package com.bergerkiller.bukkit.tc.controller;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.protocol.PacketFields;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> {
	public static final double ROTATION_K = 0.5;
	public static final int ABSOLUTE_UPDATE_INTERVAL = 200;
	public static final double VELOCITY_SOUND_RADIUS = 16;
	public static final double VELOCITY_SOUND_RADIUS_SQUARED = VELOCITY_SOUND_RADIUS * VELOCITY_SOUND_RADIUS;
	private static final Vector ZERO_VELOCITY = new Vector(0.0, 0.0, 0.0);
	private final Set<Player> velocityUpdateReceivers = new HashSet<Player>();

	private boolean isSoundEnabled() {
		MinecartMember<?> member = (MinecartMember<?>) entity.getController();
		return (member == null || member.isUnloaded()) ? false : member.getGroup().getProperties().isSoundEnabled();
	}

	private void updateVelocity(Player player) {
		final boolean inRange = isSoundEnabled() && getEntity().loc.distanceSquared(player) <= VELOCITY_SOUND_RADIUS_SQUARED;
		if ((inRange ? velocityUpdateReceivers.add(player) : velocityUpdateReceivers.remove(player))) {
			Vector velocity;
			if (inRange) {
				// Send the current velocity
				velocity = this.getProtocolVelocitySynched();
			} else {
				// Clear velocity
				velocity = ZERO_VELOCITY;
			}
			// Send
			PacketUtil.sendPacket(player, PacketFields.ENTITY_VELOCITY.newInstance(getEntity().getEntityId(), velocity));
		}
	}

	@Override
	public void makeHidden(Player player, boolean instant) {
		super.makeHidden(player, instant);
		this.velocityUpdateReceivers.remove(player);
		PacketUtil.sendPacket(player, PacketFields.ENTITY_VELOCITY.newInstance(getEntity().getEntityId(), ZERO_VELOCITY));
	}

	@Override
	public void makeVisible(Player player) {
		super.makeVisible(player);
		this.velocityUpdateReceivers.add(player);
		this.updateVelocity(player);
	}

	@Override
	public void onSync() {
		try {
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
			int i;
			MinecartGroup group = member.getGroup();
			final int count = group.size();
			MinecartMemberNetwork[] networkControllers = new MinecartMemberNetwork[count];
			for (i = 0; i < count; i++) {
				EntityNetworkController<?> controller = group.get(i).getEntity().getNetworkController();
				if (!(controller instanceof MinecartMemberNetwork)) {
					// Assign a new one - probably a bug?
					controller = new MinecartMemberNetwork();
					group.get(i).getEntity().setNetworkController(controller);
				}
				networkControllers[i] = (MinecartMemberNetwork) controller;
			}

			// Synchronize to the clients
			if (this.getTicksSinceLocationSync() > ABSOLUTE_UPDATE_INTERVAL) {
				// Perform absolute updates
				for (MinecartMemberNetwork controller : networkControllers) {
					controller.syncLocationAbsolute();
					controller.syncVelocity();
					controller.syncMeta();
					controller.getEntity().setPositionChanged(false);
				}
			} else {
				// Perform relative updates
				boolean needsSync = this.isUpdateTick();
				if (!needsSync) {
					for (i = 0; i < count; i++) {
						MinecartMemberNetwork controller = networkControllers[i];
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
					for (i = 0; i < count; i++) {
						MinecartMemberNetwork controller = networkControllers[i];
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
					for (i = 0; i < count; i++) {
						MinecartMemberNetwork controller = networkControllers[i];

						// Synchronize location
						if (rotated) {
							// Update rotation with control system function
							// This ensures that the Client animation doesn't glitch the rotation
							liveRot[i] = liveRot[i].add(liveRot[i].subtract(synchedRot[i]).multiply(ROTATION_K));
						}
						controller.syncLocation(moved ? livePos[i] : null, rotated ? liveRot[i] : null);
						controller.getEntity().setPositionChanged(false);

						// Synchronize velocity
						if (velocity) {
							controller.setProtocolVelocitySynched(liveVel[i]);
							controller.getEntity().setVelocityChanged(false);
							// Send packets to recipients
							for (Player player : controller.velocityUpdateReceivers) {
								PacketUtil.sendPacket(player, PacketFields.ENTITY_VELOCITY.newInstance(controller.getEntity().getEntityId(), liveVel[i]));
							}
						}
						// Update the velocity update receivers
						if (isSoundEnabled()) {
							for (Player player : controller.getViewers()) {
								controller.updateVelocity(player);
							}
						}

						// Synchronize meta data
						controller.syncMeta();
					}
				}
			}
		} catch (Throwable t) {
			TrainCarts.plugin.log(Level.SEVERE, "Failed to synchronize a network controller:");
			TrainCarts.plugin.handle(t);
		}
	}

	@Override
	public Vector getProtocolVelocity() {
		if (isSoundEnabled()) {
			final double maxSpeed = getEntity().getMaxSpeed();
			Vector vel = super.getProtocolVelocity();
			vel.setX(MathUtil.clamp(vel.getX(), maxSpeed));
			vel.setY(MathUtil.clamp(vel.getY(), maxSpeed));
			vel.setZ(MathUtil.clamp(vel.getZ(), maxSpeed));
			return vel;
		} else {
			return ZERO_VELOCITY;
		}
	}
}
