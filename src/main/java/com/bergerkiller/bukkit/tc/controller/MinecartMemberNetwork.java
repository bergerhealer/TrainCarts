package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;

public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> {
	public static final long MIN_SYNC_INTERVAL = 10;

	@Override
	public synchronized void onSync() {
		if (entity.isDead()) {
			return;
		}
		MinecartMember<?> member = (MinecartMember<?>) entity.getController();
		if (member.isUnloaded() || member.isSingle()) {
			// Unloaded or only one minecart: Synchronize just this Minecart
			super.onSync();
			return;
		} else if (member.getIndex() != 0) {
			// Ignore
			return;
		}

		// Update for the entire group
		MinecartGroup group = member.getGroup();
		List<EntityNetworkController<?>> networkControllers = new ArrayList<EntityNetworkController<?>>(group.size());
		for (MinecartMember<?> mm : group) {
			networkControllers.add(mm.getEntity().getNetworkController());
		}

		// Synchronize to the clients
		if (this.getTicksSinceLocationSync() > 200) {
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
				boolean moved = false;
				boolean rotated = false;
				boolean velocity = false;
				for (EntityNetworkController<?> controller : networkControllers) {
					// Position changed?
					if (!moved) {
						IntVector3 oldPos = controller.getProtocolPositionSynched();
						IntVector3 newPos = controller.getProtocolPosition();
						moved = newPos.subtract(oldPos).abs().greaterEqualThan(MIN_RELATIVE_CHANGE);
					}
					// Rotation changed?
					if (!rotated) {
						IntVector2 oldRot = controller.getProtocolRotationSynched();
						IntVector2 newRot = controller.getProtocolRotation();
						rotated = newRot.subtract(oldRot).abs().greaterEqualThan(MIN_RELATIVE_CHANGE);
					}
					// Velocity changed?
					if (!velocity) {
						Vector oldVel = controller.getProtocolVelocitySynched();
						Vector newVel = controller.getProtocolVelocity();
						velocity |= controller.getEntity().isVelocityChanged();
						velocity |= newVel.distanceSquared(oldVel) > MIN_RELATIVE_VELOCITY_SQUARED;
					}
				}
				// Update
				for (EntityNetworkController<?> controller : networkControllers) {
					// Location update
					controller.syncLocation(moved, rotated);
					controller.getEntity().setPositionChanged(false);
					// Velocity
					if (velocity) {
						controller.syncVelocity(controller.getProtocolVelocity());
						controller.getEntity().setVelocityChanged(false);
					}
					// Meta
					controller.syncMeta();
				}
			}
		}
	}
}
