package com.bergerkiller.bukkit.tc.controller;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.tc.TrainCarts;

public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> {
	public static final double ROTATION_K = 0.5;
	public static final int ABSOLUTE_UPDATE_INTERVAL = 200;

	@Override
	public void onSync() {
		if (entity.isDead()) {
			return;
		}
		MinecartMember<?> member = (MinecartMember<?>) entity.getController();
		if (member.isUnloaded()) {
			// Unloaded: Synchronize just this Minecart
			super.onSync();
			return;
		} else if (member.getIndex() != 0) {
			// Ignore
			return;
		}

		// Update the entire group
		MinecartGroup group = member.getGroup();
		final int count = group.size();
		EntityNetworkController<?>[] networkControllers = new EntityNetworkController<?>[count];
		for (int i = 0; i < count; i++) {
			networkControllers[i] = group.get(i).getEntity().getNetworkController();
			if (networkControllers[i] == null) {
				// Assign a new one - probably a bug?
				networkControllers[i] = new MinecartMemberNetwork();
				group.get(i).getEntity().setNetworkController(networkControllers[i]);
			}
		}

		// Synchronize to the clients
		if (this.getTicksSinceLocationSync() > ABSOLUTE_UPDATE_INTERVAL) {
			// Perform absolute updates
			for (EntityNetworkController<?> controller : networkControllers) {
				controller.syncLocationAbsolute();
				controller.syncVelocity();
				controller.syncMeta();
				controller.getEntity().setPositionChanged(false);
			}
		} else {
			// Perform relative updates
			boolean needsSync = this.isUpdateTick();
			if (!needsSync) {
				for (EntityNetworkController<?> controller : networkControllers) {
					if (controller.getEntity().isPositionChanged()) {
						needsSync = true;
						break;
					}
				}
			}
			if (needsSync) {
				final IntVector3[] synchedPos = new IntVector3[count];
				final IntVector2[] synchedRot = new IntVector2[count];
				final IntVector3[] livePos = new IntVector3[count];
				final IntVector2[] liveRot = new IntVector2[count];
				final Vector[] liveVel = new Vector[count];
				boolean moved = false;
				boolean rotated = false;
				boolean velocity = false;
				// Check whether changes are needed
				for (int i = 0; i < count; i++) {
					EntityNetworkController<?> controller = networkControllers[i];
					// Position
					synchedPos[i] = controller.getProtocolPositionSynched();
					livePos[i] = controller.getProtocolPosition();
					moved |= livePos[i].subtract(synchedPos[i]).abs().greaterEqualThan(MIN_RELATIVE_CHANGE);
					// Rotation
					synchedRot[i] = controller.getProtocolRotationSynched();
					liveRot[i] = controller.getProtocolRotation();
					rotated |= liveRot[i].subtract(synchedRot[i]).abs().greaterEqualThan(MIN_RELATIVE_CHANGE);
					// Velocity
					liveVel[i] = controller.getProtocolVelocity();
					velocity |= controller.getEntity().isVelocityChanged();
					velocity |= liveVel[i].distanceSquared(controller.getProtocolVelocitySynched()) > MIN_RELATIVE_VELOCITY_SQUARED;
				}
				// Perform actual updates
				for (int i = 0; i < count; i++) {
					EntityNetworkController<?> controller = networkControllers[i];
					if (rotated) {
						// Update rotation with control system function
						// This ensures that the Client animation doesn't glitch the rotation
						liveRot[i] = liveRot[i].add(liveRot[i].subtract(synchedRot[i]).multiply(ROTATION_K));
					}
					// Synchronize location
					controller.syncLocation(moved ? livePos[i] : null, rotated ? liveRot[i] : null);
					controller.getEntity().setPositionChanged(false);
					// Synchronize velocity
					if (velocity) {
						controller.syncVelocity(liveVel[i]);
						controller.getEntity().setVelocityChanged(false);
					}
					// Synchronize meta data
					controller.syncMeta();
				}
			}
		}
	}

	@Override
	public Vector getProtocolVelocity() {
		if (TrainCarts.minecartSoundEnabled) {
			return new Vector(0.0, 0.0, 0.0);
		}
		return super.getProtocolVelocity();
	}
}
