package com.bergerkiller.bukkit.tc.controller;

import java.util.List;
import java.util.Set;

import com.bergerkiller.bukkit.common.reflection.classes.EntityTrackerEntryRef;
import com.bergerkiller.bukkit.common.utils.MathUtil;

import net.minecraft.server.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MinecartMemberTrackerEntry extends EntityTrackerEntry {
	private int ticksNoTeleport = 0;
	public boolean isRemoved;
	private double prevX, prevY, prevZ;
	protected boolean tracked = false;

	public MinecartMemberTrackerEntry(EntityTrackerEntry source) {
		super(source.tracker, 80, 3, true);
		//copy important information over
		EntityTrackerEntryRef.TEMPLATE.transfer(source, this);
		this.prevX = EntityTrackerEntryRef.prevX.get(source);
		this.prevY = EntityTrackerEntryRef.prevY.get(source);
		this.prevZ = EntityTrackerEntryRef.prevZ.get(source);
		if (!EntityTrackerEntryRef.synched.get(source)) {
			this.prevX += 32.0;
			this.prevY += 32.0;
			this.prevZ += 32.0;
		}
		this.isRemoved = false;
	}

	public MinecartMemberTrackerEntry(MinecartMember member) {
		super(member, 80, 3, true);
		this.prevX = this.tracker.locX + 32;
		this.prevY = this.tracker.locY + 32;
		this.prevZ = this.tracker.locZ + 32;
		this.isRemoved = false;
	}

	/**
	 * Reverts this entry to the default internal minecart entry
	 */
	public EntityTrackerEntry revert() {
		EntityTrackerEntry entry = new EntityTrackerEntry(this.tracker, 80, 3, true);
		EntityTrackerEntryRef.TEMPLATE.transfer(this, entry);
		return entry;
	}

	@Override
	public void track(List list) { 
		if (this.tracker.dead) {
			super.track(list);
			return;
		}

		//update players
		if (this.tracker.e(this.prevX, this.prevY, this.prevZ) > 16.0) {
			this.prevX = this.tracker.locX;
			this.prevY = this.tracker.locY;
			this.prevZ = this.tracker.locZ;
			this.scanPlayers(list);
		}

		this.tracked = true;
		MinecartGroup group = ((MinecartMember) this.tracker).getGroup();
		for (MinecartMember member : group) {
			if (!member.getTracker().tracked) {
				return;
			}
		}

		// All trackers updated, time to sync
		syncAll();
	}

	/**
	 * Synchronizes all members' entity trackers, 
	 * Makes them move nicely in-sync<br><br>
	 * 
	 * Called from within the last tracked minecart in the group
	 */
	private void syncAll() {
		MinecartGroup group = ((MinecartMember) this.tracker).getGroup();
		MinecartMemberTrackerEntry headtracker = group.head().getTracker();
		if (headtracker == null) return;
		if (group.size() == 1) {
			headtracker.sync();
		} else {
			boolean location = headtracker.needsLocationSync();
			boolean teleport = headtracker.needsTeleport();
			boolean velocity = false;
			for (MinecartMember mm : group) {
				MinecartMemberTrackerEntry tracker = mm.getTracker();
				if (tracker == null) continue;
				if (!location && tracker.tracker.al) {
					location = true;
				}
				if (!velocity && tracker.tracker.velocityChanged) {
					velocity = true;
				}
			}
			for (MinecartMember mm : group) {
				MinecartMemberTrackerEntry tracker = mm.getTracker();
				if (tracker == null) continue;
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
	
	public void sync() {
		if (this.tracker.dead) return;
		if (this.needsLocationSync() || this.hasTrackerChanged()) {
			this.syncLocation(this.needsTeleport());
		}
		if (this.tracker.velocityChanged) {
			this.syncVelocity();
		}
		this.syncMeta();
	}

	public boolean needsTeleport() {
		return ++this.ticksNoTeleport > 100; // was 400, but to reduce de-synching times, it is now 5 seconds
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
		int x = tracker.am.a(this.tracker.locX);
		int y = MathHelper.floor(this.tracker.locY * 32);
		int z = tracker.am.a(this.tracker.locZ);
		int yaw = MathHelper.d(this.tracker.yaw * 256 / 360);
		int pitch = MathHelper.d(this.tracker.pitch * 256 / 360);
		this.xLoc = x;
		this.yLoc = y;
		this.zLoc = z;
		setRotation(yaw, pitch);
		this.ticksNoTeleport = 0;
		this.broadcast(getTeleportPacket());
	}

	private Packet getTeleportPacket() {
		return new Packet34EntityTeleport(this.tracker.id, this.xLoc, this.yLoc, this.zLoc, (byte) this.xRot, (byte) this.yRot);
	}

	public boolean hasTrackerChanged() {
		return this.tracker.al;
	}

	public void setTrackerChanged(boolean changed) {
		this.tracker.al = changed;
	}

	public void syncLocation(boolean teleport) {
		if (!teleport) {
			int newXLoc = this.tracker.am.a(this.tracker.locX);
			int newYLoc = MathHelper.floor(this.tracker.locY * 32);
			int newZLoc = this.tracker.am.a(this.tracker.locZ);
			int xDiff = newXLoc - this.xLoc;
			int yDiff = newYLoc - this.yLoc;
			int zDiff = newZLoc - this.zLoc;
			teleport = Math.abs(xDiff) > 128 || Math.abs(yDiff) > 128 || Math.abs(zDiff) > 128;
			if (!teleport) {
				int newXRot = MathHelper.d(this.tracker.yaw * 256 / 360);
				int newYRot = MathHelper.d(this.tracker.pitch * 256 / 360);
				boolean looked = Math.abs(newXRot - this.xRot) >= 4 || Math.abs(newYRot - this.yRot) >= 4;
				boolean moved = Math.abs(xDiff) >= 4 || Math.abs(yDiff) >= 4 || Math.abs(zDiff) >= 4;
				if (moved) {
					//moving to derailed track?
					MinecartMember mm = (MinecartMember) this.tracker;
					if (mm.wasOnMinecartTrack && !mm.isOnMinecartTrack) {
						teleport = true;
					}
				}
				if (!teleport) {
					if (moved && looked) {
						this.broadcast(new Packet33RelEntityMoveLook(this.tracker.id, (byte) xDiff, (byte) yDiff, (byte) zDiff, (byte) newXRot, (byte) newYRot));
					} else if (moved) {
						this.broadcast(new Packet31RelEntityMove(this.tracker.id, (byte) xDiff, (byte) yDiff, (byte) zDiff));
					} else if (looked) {
						this.broadcast(new Packet32EntityLook(this.tracker.id, (byte) newXRot, (byte) newYRot));
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
		}
		if (teleport) this.doTeleport();
		this.setTrackerChanged(false);

		//motion packets
		double motDiff = MathUtil.distanceSquared(this.tracker.motX, this.tracker.motY, this.tracker.motZ, this.j, this.k, this.l);
		if (motDiff > 0.0004 || (motDiff > 0 && this.tracker.motX == 0 && this.tracker.motY == 0 && this.tracker.motZ == 0)) {
			this.j = this.tracker.motX;
			this.k = this.tracker.motY;
			this.l = this.tracker.motZ;
			this.broadcast(new Packet28EntityVelocity(this.tracker.id, this.j, this.k, this.l));
		}
	}

	public void syncVelocity() {
		if (this.tracker.dead) return;
		this.broadcast(new Packet28EntityVelocity(this.tracker));
		this.tracker.velocityChanged = false;
	}

	public void syncMeta() {
		if (this.tracker.dead) return;
		//meta data packets (used for effects like smoke toggling)
		DataWatcher datawatcher = this.tracker.getDataWatcher();
		if (datawatcher.a()) {
			this.broadcastIncludingSelf(new Packet40EntityMetadata(this.tracker.id, datawatcher));
		}
	}

	public void doRespawn() {
		for (EntityPlayer ep : (Set<EntityPlayer>) this.trackedPlayers) {
			this.doRespawn(ep);
		}
	}

	public void doRespawn(EntityPlayer entityplayer) {
		this.doDestroy(entityplayer);
		this.doSpawn(entityplayer);
	}

	public void doDestroy(EntityPlayer entityplayer) {
		entityplayer.netServerHandler.sendPacket(new Packet29DestroyEntity(this.tracker.id));
	}

	public void doSpawn(EntityPlayer entityplayer) {
		//send spawn packet
		int type = MathUtil.clamp(((MinecartMember) this.tracker).type, 0, 2);
		entityplayer.netServerHandler.sendPacket(new Packet23VehicleSpawn(this.tracker, 10 + type));
		entityplayer.netServerHandler.sendPacket(new Packet28EntityVelocity(this.tracker));
		entityplayer.netServerHandler.sendPacket(getTeleportPacket());
		if (this.tracker.passenger != null) {
			entityplayer.netServerHandler.sendPacket(new Packet39AttachEntity(this.tracker.passenger, this.tracker));
		}
	}

	@Override
	public void a() {
		super.a();
		this.isRemoved = true;
	}

	@Override
	public void a(EntityPlayer entityplayer) {
		this.trackedPlayers.remove(entityplayer);
	}

	@Override
	public void clear(EntityPlayer entityplayer) {
		if (this.trackedPlayers.remove(entityplayer)) {
			this.doDestroy(entityplayer);
		}
	}

	@Override
	public void updatePlayer(EntityPlayer entityplayer) {
		if (this.tracker.dead) return;
		if (entityplayer != this.tracker) {
			double d0 = entityplayer.locX - (double) (this.xLoc / 32);
			double d1 = entityplayer.locZ - (double) (this.zLoc / 32);
			if (d0 >= (double) (-this.b) && d0 <= (double) this.b && d1 >= (double) (-this.b) && d1 <= (double) this.b) {
				if (this.trackedPlayers.add(entityplayer)) {
					//send spawn packet
					this.doSpawn(entityplayer);
				}
			} else {
				this.clear(entityplayer);
			}
		}
	}
}
