package com.bergerkiller.bukkit.tc.controller;

import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.EntityTrackerEntryBase;
import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.protocol.PacketFields;
import com.bergerkiller.bukkit.common.reflection.classes.EntityTrackerEntryRef;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TrainCarts;

public class MinecartMemberTrackerEntry extends EntityTrackerEntryBase {
	private int ticksNoTeleport = 0;
	private Vector synchedVelocity = new Vector();
	public boolean isRemoved = false;
	protected boolean tracked = false;
	public static final long MIN_SYNC_INTERVAL = 10;

	public MinecartMemberTrackerEntry(Object source) {
		super(EntityTrackerEntryRef.tracker.get(source), 80, 3, true);
		//copy important information over
		org.bukkit.entity.Entity oldTracker = getTracker();
		EntityTrackerEntryRef.TEMPLATE.transfer(source, this);
		this.setTracker(oldTracker);
	}

	public MinecartMemberTrackerEntry(MinecartMember member) {
		super(member.getEntity(), 80, 3, true);
	}

	public MinecartMember getMember() {
		return Conversion.convert(getTracker(), MinecartMember.class);
	}

	@Override
	public void onTick() {
		try {
			this.tracked = true;
			final MinecartMember tracker = getMember();
			if (tracker.isUnloaded()) {
				this.sync();
				return;
			}
			MinecartGroup group = tracker.getGroup();
			for (MinecartMember member : group) {
				if (!member.getTracker().tracked) {
					return;
				}
			}

			// Allowed?
			long time = System.currentTimeMillis();
			if (group.lastSync == Long.MIN_VALUE || (time - group.lastSync) > MIN_SYNC_INTERVAL) {
				group.lastSync = time;

				// All trackers updated, time to sync
				syncAll();
			}
		} catch (Throwable t) {
			TrainCarts.plugin.log(Level.SEVERE, "An exception occurred while tracking a minecart:");
			TrainCarts.plugin.handle(t);
		}
	}

	/**
	 * Synchronizes all members' entity trackers, 
	 * Makes them move nicely in-sync<br><br>
	 * 
	 * Called from within the last tracked minecart in the group
	 */
	private void syncAll() {
		MinecartGroup group = MemberConverter.toMember.convert(getTracker()).getGroup();
		MinecartMemberTrackerEntry headtracker = group.head().getTracker();
		if (headtracker == null) {
			return;
		}
		if (group.size() == 1) {
			headtracker.sync();
		} else {
			boolean location = headtracker.needsLocationSync();
			boolean teleport = headtracker.needsTeleport();
			boolean velocity = false;
			for (MinecartMember mm : group) {
				MinecartMemberTrackerEntry tracker = mm.getTracker();
				if (tracker != null) {
					location |= this.isTrackerPositionChanged();
					velocity |= this.isTrackerVelocityChanged();
				}
			}
			for (MinecartMember mm : group) {
				MinecartMemberTrackerEntry tracker = mm.getTracker();
				if (tracker != null) {
					tracker.tracked = false;
					if (location) {
						tracker.syncLocation(teleport);
					}
					if (velocity) {
						tracker.syncVelocity();
					}
					tracker.syncMeta();
				}
			}
		}
	}
	
	public void sync() {
		if (this.getTracker().isDead()) {
			return;
		}
		if (this.needsLocationSync() || this.isTrackerPositionChanged()) {
			this.syncLocation(this.needsTeleport());
		}
		if (this.isTrackerVelocityChanged()) {
			this.syncVelocity();
		}
		this.syncMeta();
	}

	public boolean needsTeleport() {
		return ++this.ticksNoTeleport > 400;
	}

	public boolean needsLocationSync() {
		return ++this.m % this.c == 0;
	}

	private void setRotation(int xRot, int yRot) {
		this.xRot = xRot;
		this.yRot = yRot;
		while (this.xRot < 0) this.xRot += 256;
		while (this.yRot < 0) this.yRot += 256;
	}

	public void doTeleport() {
		int x = this.getTrackerProtocolX();
		int y = this.getTrackerProtocolY();
		int z = this.getTrackerProtocolZ();
		int yaw = this.getTrackerProtocolYaw();
		int pitch = this.getTrackerProtocolPitch();
		this.xLoc = x;
		this.yLoc = y;
		this.zLoc = z;
		setRotation(yaw, pitch);
		this.ticksNoTeleport = 0;
		this.broadcastPacket(getTeleportPacket());
	}

	private Object getTeleportPacket() {
		return PacketFields.ENTITY_TELEPORT.newInstance(getTrackerId(), this.xLoc, this.yLoc, this.zLoc, (byte) this.xRot, (byte) this.yRot);
	}

	public void syncLocation(boolean teleport) {
		if (!teleport) {
			int newXLoc = this.getTrackerProtocolX();
			int newYLoc = this.getTrackerProtocolY();
			int newZLoc = this.getTrackerProtocolZ();
			int xDiff = newXLoc - this.xLoc;
			int yDiff = newYLoc - this.yLoc;
			int zDiff = newZLoc - this.zLoc;
			teleport = Math.abs(xDiff) > 128 || Math.abs(yDiff) > 128 || Math.abs(zDiff) > 128;
			if (!teleport) {
				int newXRot = this.getTrackerProtocolYaw();
				int newYRot = this.getTrackerProtocolPitch();
				boolean looked = Math.abs(newXRot - this.xRot) >= 4 || Math.abs(newYRot - this.yRot) >= 4;
				boolean moved = Math.abs(xDiff) >= 4 || Math.abs(yDiff) >= 4 || Math.abs(zDiff) >= 4;
				if (moved && looked) {
					this.broadcastPacket(PacketFields.REL_ENTITY_MOVE_LOOK.newInstance(getTrackerId(), (byte) xDiff, (byte) yDiff, (byte) zDiff, (byte) newXRot, (byte) newYRot));
				} else if (moved) {
					this.broadcastPacket(PacketFields.REL_ENTITY_MOVE.newInstance(getTrackerId(), (byte) xDiff, (byte) yDiff, (byte) zDiff));
				} else if (looked) {
					this.broadcastPacket(PacketFields.ENTITY_LOOK.newInstance(this.getTrackerId(), (byte) newXRot, (byte) newYRot));
				}
				if (moved) {
					this.xLoc = newXLoc;
					this.yLoc = newYLoc;
					this.zLoc = newZLoc;
				}
				if (looked) {
					setRotation(newXRot, newYRot);
				}
			}
		}
		if (teleport) {
			this.doTeleport();
		}
		this.setTrackerPositionChanged(false);

		//motion packets
		Vector velocity = getMember().getLimitedVelocity();
		double motDiff = velocity.distance(synchedVelocity);
		if (motDiff > 0.0004 || (motDiff > 0 && velocity.lengthSquared() == 0.0)) {
			synchedVelocity = velocity;
			this.broadcastPacket(PacketFields.ENTITY_VELOCITY.newInstance(getTrackerId(), velocity));
		}
	}

	public void syncVelocity() {
		if (this.getTracker().isDead()) {
			return;
		}
		Vector velocity = getMember().getLimitedVelocity();
		this.broadcastPacket(PacketFields.ENTITY_VELOCITY.newInstance(getTrackerId(), velocity));
		this.setTrackerVelocityChanged(false);
	}

	public void syncMeta() {
		if (!this.getTracker().isDead()) {
			//meta data packets (used for effects like smoke toggling)
			DataWatcher datawatcher = this.getTrackerMetaData();
			if (datawatcher.isChanged()) {
				this.broadcastPacket(PacketFields.ENTITY_METADATA.newInstance(getTrackerId(), datawatcher, false));
			}
		}
	}

	public void doRespawn() {
		for (Player player : this.getViewers()) {
			doRespawn(player);
		}
	}

	public void doRespawn(Player player) {
		this.doDestroy(player);
		this.doSpawn(player);
	}

	public void doSpawn(Player player) {
		PacketUtil.sendCommonPacket(player, getSpawnPacket());

		// Meta data
		PacketUtil.sendPacket(player, PacketFields.ENTITY_METADATA.newInstance(getTrackerId(), getTrackerMetaData(), true));

		// Velocity, positioning and passenger
		PacketUtil.sendPacket(player, PacketFields.ENTITY_VELOCITY.newInstance(getTracker()));
		PacketUtil.sendPacket(player, getTeleportPacket());
	}

	@Override
	public void broadcastDestroyPackets() {
		super.broadcastDestroyPackets();
		this.isRemoved = true;
	}

	@Override
	public void removeViewer(Player player) {
		if (this.getViewers().remove(player)) {
			this.doDestroy(player);
		}
	}

	@Override
	public void updatePlayer(Player player) {
		if (this.getTracker().isDead()) {
			return;
		}
		if (player != getTracker()) {
			double d0 = EntityUtil.getLocX(player) - (double) (this.xLoc / 32);
			double d1 = EntityUtil.getLocZ(player) - (double) (this.zLoc / 32);
			if (d0 >= (double) (-this.getViewDistance()) && d0 <= (double) this.getViewDistance() 
					&& d1 >= (double) (-this.getViewDistance()) && d1 <= (double) this.getViewDistance()) {

				if (this.getViewers().add(player)) {
					//send spawn packet
					this.doSpawn(player);
				}
			} else {
				this.removeViewer(player);
			}
		}
	}
}
