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
					Object entry = map.a(member.id);
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
		this.d = source.d;
		this.e = source.e;
		this.f = source.f;
		this.g = source.g;
		this.h = source.h;
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
		if (this.m = this.tracker.e(this.prevX, this.prevY, this.prevZ) > 16) {
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
		return ++this.l % this.c == 0;
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
		this.d = x;
		this.e = y;
		this.f = z;
		this.g = yaw;
		this.h = pitch;
		this.ticksNoTeleport = 0;
		return new Packet34EntityTeleport(this.tracker.id, x, y, z, (byte) yaw, (byte) pitch);
	}

	public boolean dd = false;
	public void syncLocation(boolean teleport) {
		if (dd) return;
		if (!teleport) {
			int i = MathHelper.floor(this.tracker.locX * 32);
			int j = MathHelper.floor(this.tracker.locY * 32);
			int k = MathHelper.floor(this.tracker.locZ * 32);
			int j1 = i - this.d;
			int k1 = j - this.e;
			int l1 = k - this.f;
			while (this.g < 0) this.g += 256;
			while (this.h < 0) this.h += 256;
			teleport = Math.abs(j1) > 128 || Math.abs(k1) > 128 || Math.abs(l1) > 128;
			if (!teleport) {
				int l = MathHelper.d(this.tracker.yaw * 256 / 360);
				int i1 = MathHelper.d(this.tracker.pitch * 256 / 360);
				boolean looked = Math.abs(l - this.g) >= 4 || Math.abs(i1 - this.h) >= 4;
				boolean moved = Math.abs(j1) >= 4 || Math.abs(k1) >= 4 || Math.abs(l1) >= 4;
				if (moved) {
					//moving to derailed track?
					MinecartMember mm = (MinecartMember) this.tracker;
					if (mm.wasOnMinecartTrack && !mm.isOnMinecartTrack) {
						this.doTeleport();
						return;
					}
				}
				if (!teleport) {
					if (moved && looked) {
						this.broadcast(new Packet33RelEntityMoveLook(this.tracker.id, (byte) j1, (byte) k1, (byte) l1, (byte) l, (byte) i1));
					} else if (moved) {
						this.broadcast(new Packet31RelEntityMove(this.tracker.id, (byte) j1, (byte) k1, (byte) l1));
					} else if (looked) {
						this.broadcast(new Packet32EntityLook(this.tracker.id, (byte) l, (byte) i1));
					}
					if (moved) {
						this.d = i;
						this.e = j;
						this.f = k;
					}
					if (looked) {
						this.g = l;
						this.h = i1;
					}
				}
			}
		}
		if (teleport) this.doTeleport();
		this.tracker.ce = false;

		//motion packets
		double motDiff = MathUtil.distanceSquared(this.tracker.motX, this.tracker.motY, this.tracker.motZ, this.i, this.k, this.k);
		if (motDiff > 0.0004 || (motDiff > 0 && this.tracker.motX == 0 && this.tracker.motY == 0 && this.tracker.motZ == 0)) {
			this.i = this.tracker.motX;
			this.j = this.tracker.motY;
			this.k = this.tracker.motZ;
			this.broadcast(new Packet28EntityVelocity(this.tracker.id, this.i, this.j, this.k));
		}
	}
	public void syncVelocity() {
		if (this.tracker.dead) return;
		this.broadcastIncludingSelf(new Packet28EntityVelocity(this.tracker));
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
	public void c(EntityPlayer entityplayer) {
		if (this.trackedPlayers.remove(entityplayer)) {
			entityplayer.netServerHandler.sendPacket(new Packet29DestroyEntity(this.tracker.id));
		}
	}
	public void b(EntityPlayer entityplayer) {
		if (this.tracker.dead) return;
		if (entityplayer != this.tracker) {
			double d0 = entityplayer.locX - (double) (this.d / 32);
			double d1 = entityplayer.locZ - (double) (this.f / 32);
			if (d0 >= (double) (-this.b) && d0 <= (double) this.b && d1 >= (double) (-this.b) && d1 <= (double) this.b) {
				if (this.trackedPlayers.add(entityplayer)) {
					//send spawn packet
					int type = MathUtil.limit(((MinecartMember) this.tracker).type, 0, 2);
					entityplayer.netServerHandler.sendPacket(new Packet23VehicleSpawn(this.tracker, 10 + type));
					entityplayer.netServerHandler.sendPacket(new Packet28EntityVelocity(this.tracker));
					entityplayer.netServerHandler.sendPacket(this.createTeleportPacket());
				}
			} else {
				this.c(entityplayer);
			}
		}
	}

}
