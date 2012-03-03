package com.bergerkiller.bukkit.tc;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import com.bergerkiller.bukkit.common.SafeField;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

import net.minecraft.server.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MinecartMemberTrackerEntry extends EntityTrackerEntry {

	private static SafeField<Set> trackerSet;
	private static SafeField<IntHashMap> trackerMap;
	public static void failFields() {
		trackerMap = null;
		trackerSet = null;
		TrainCarts.plugin.log(Level.WARNING, "Failed to initialize the entity tracker replacement:");
		TrainCarts.plugin.log(Level.WARNING, "Train movement will not be smoothed!");
	}
	public static void initFields() {
		trackerMap = new SafeField<IntHashMap>(EntityTracker.class, "trackedEntities");
		trackerSet = new SafeField<Set>(EntityTracker.class, "a");
		if (!trackerMap.isValid() || !trackerSet.isValid()) {
			failFields();
		}
	}
	public static MinecartMemberTrackerEntry get(MinecartMember member) {
		EntityTracker tracker = WorldUtil.getTracker(member.world);
		if (trackerMap != null) {
			try {
				IntHashMap map = trackerMap.get(tracker);
				synchronized (tracker) {
					Object entry = map.get(member.id);
					if (entry == null) {
						entry = new MinecartMemberTrackerEntry(member);
					} else if (!(entry instanceof MinecartMemberTrackerEntry)) {
						entry = new MinecartMemberTrackerEntry((EntityTrackerEntry) entry);
					} else {
						return (MinecartMemberTrackerEntry) entry;
					}
					map.a(member.id, entry);
					Set set = trackerSet.get(tracker);
					set.remove(entry);
					set.add(entry);
					return (MinecartMemberTrackerEntry) entry;
				}
			} catch (Throwable t) {
				failFields();
			}
		}
		return null;
	}

	private int ticksNoTeleport = 0;
	public boolean isRemoved;
	private double prevX, prevY, prevZ;

	public MinecartMemberTrackerEntry(EntityTrackerEntry source) {
		super(source.tracker, 80, 3, true);
		//copy important information over
		this.xLoc = source.xLoc;
		this.yLoc = source.yLoc;
		this.zLoc = source.zLoc;
		this.xRot = source.xRot;
		this.yRot = source.yRot;
		this.m = source.m;
		this.trackedPlayers.addAll(source.trackedPlayers);
		this.isRemoved = false;
		this.doTeleport();
	}
	public MinecartMemberTrackerEntry(MinecartMember member) {
		super(member, 80, 3, true);
		this.prevX = this.tracker.locX + 32;
		this.prevY = this.tracker.locY + 32;
		this.prevZ = this.tracker.locZ + 32;
		this.isRemoved = false;
	}

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
	}

	public void sync() {
		if (this.tracker.dead) return;
		if (this.needsLocationSync() || this.tracker.ce) {
			this.syncLocation(this.needsTeleport());
		}
		if (this.tracker.velocityChanged) {
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

	public void doTeleport() {
		this.broadcast(this.createTeleportPacket());
	}
	public Packet34EntityTeleport createTeleportPacket() {
		int x = MathHelper.floor(this.tracker.locX * 32);
		int y = MathHelper.floor(this.tracker.locY * 32);
		int z = MathHelper.floor(this.tracker.locZ * 32);
		int yaw = MathHelper.d(this.tracker.yaw * 256 / 360);
		int pitch = MathHelper.d(this.tracker.pitch * 256 / 360);
		this.xLoc = x;
		this.yLoc = y;
		this.zLoc = z;
		this.xRot = yaw;
		this.yRot = pitch;
		this.ticksNoTeleport = 0;
		return new Packet34EntityTeleport(this.tracker.id, x, y, z, (byte) yaw, (byte) pitch);
	}

	public void syncLocation(boolean teleport) {
		if (!teleport) {
			int newXLoc = MathHelper.floor(this.tracker.locX * 32);
			int newYLoc = MathHelper.floor(this.tracker.locY * 32);
			int newZLoc = MathHelper.floor(this.tracker.locZ * 32);
			int xDiff = newXLoc - this.xLoc;
			int yDiff = newYLoc - this.yLoc;
			int zDiff = newZLoc - this.zLoc;
			while (this.xRot < 0) this.xRot += 256;
			while (this.yRot < 0) this.yRot += 256;
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
						this.xRot = newXRot;
						this.yRot = newYRot;
					}
				}
			}
		}
		if (teleport) this.doTeleport();
		this.tracker.ce = false;

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

	public void a() {
		super.a();
		this.isRemoved = true;
	}   
	public void a(EntityPlayer entityplayer) {
		this.trackedPlayers.remove(entityplayer);
	}
	public void clear(EntityPlayer entityplayer) {
		if (this.trackedPlayers.remove(entityplayer)) {
			entityplayer.netServerHandler.sendPacket(new Packet29DestroyEntity(this.tracker.id));
		}
	}
	public void updatePlayer(EntityPlayer entityplayer) {
		if (this.tracker.dead) return;
		if (entityplayer != this.tracker) {
			double d0 = entityplayer.locX - (double) (this.xLoc / 32);
			double d1 = entityplayer.locZ - (double) (this.zLoc / 32);
			if (d0 >= (double) (-this.b) && d0 <= (double) this.b && d1 >= (double) (-this.b) && d1 <= (double) this.b) {
				if (this.trackedPlayers.add(entityplayer)) {
					//send spawn packet
					int type = MathUtil.limit(((MinecartMember) this.tracker).type, 0, 2);
					entityplayer.netServerHandler.sendPacket(new Packet23VehicleSpawn(this.tracker, 10 + type));
					entityplayer.netServerHandler.sendPacket(new Packet28EntityVelocity(this.tracker));
					entityplayer.netServerHandler.sendPacket(this.createTeleportPacket());
				}
			} else {
				this.clear(entityplayer);
			}
		}
	}

}
