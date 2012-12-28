package com.bergerkiller.bukkit.tc.controller;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.reflection.classes.EntityRef;
import com.bergerkiller.bukkit.common.reflection.classes.EntityTrackerEntryRef;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

import net.minecraft.server.v1_4_5.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MinecartMemberTrackerEntry extends EntityTrackerEntry {
	private int ticksNoTeleport = 0;
	public boolean isRemoved;
	private double prevX, prevY, prevZ;
	protected boolean tracked = false;
	public static final long MIN_SYNC_INTERVAL = 10;

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
		try {
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
			if (((MinecartMember) this.tracker).isUnloaded()) {
				this.sync();
				return;
			}
			MinecartGroup group = ((MinecartMember) this.tracker).getGroup();
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
				if (tracker != null) {
					location |= tracker.hasTrackerChanged();
					velocity |= tracker.tracker.velocityChanged;
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
		int x = tracker.ar.a(this.tracker.locX);
		int y = MathHelper.floor(this.tracker.locY * 32);
		int z = tracker.ar.a(this.tracker.locZ);
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
		return EntityRef.positionChanged.get(this.tracker);
	}

	public void setTrackerChanged(boolean changed) {
		EntityRef.positionChanged.set(this.tracker, changed);
	}

	public void syncLocation(boolean teleport) {
		if (!teleport) {
			int newXLoc = this.tracker.ar.a(this.tracker.locX);
			int newYLoc = MathHelper.floor(this.tracker.locY * 32);
			int newZLoc = this.tracker.ar.a(this.tracker.locZ);
			int xDiff = newXLoc - this.xLoc;
			int yDiff = newYLoc - this.yLoc;
			int zDiff = newZLoc - this.zLoc;
			teleport = Math.abs(xDiff) > 128 || Math.abs(yDiff) > 128 || Math.abs(zDiff) > 128;
			if (!teleport) {
				int newXRot = MathHelper.d(this.tracker.yaw * 256 / 360);
				int newYRot = MathHelper.d(this.tracker.pitch * 256 / 360);
				boolean looked = Math.abs(newXRot - this.xRot) >= 4 || Math.abs(newYRot - this.yRot) >= 4;
				boolean moved = Math.abs(xDiff) >= 4 || Math.abs(yDiff) >= 4 || Math.abs(zDiff) >= 4;
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
		if (teleport) this.doTeleport();
		this.setTrackerChanged(false);

		//motion packets
		Vector velocity = ((MinecartMember) this.tracker).getLimitedVelocity();
		double motDiff = MathUtil.distanceSquared(velocity.getX(), velocity.getY(), velocity.getZ(), this.j, this.k, this.l);
		if (motDiff > 0.0004 || (motDiff > 0 && velocity.lengthSquared() == 0.0)) {
			this.j = velocity.getX();
			this.k = velocity.getY();
			this.l = velocity.getZ();
			this.broadcast(new Packet28EntityVelocity(this.tracker.id, this.j, this.k, this.l));
		}
	}

	public void syncVelocity() {
		if (this.tracker.dead) return;
		Vector velocity = ((MinecartMember) this.tracker).getLimitedVelocity();
		this.broadcast(new Packet28EntityVelocity(this.tracker.id, velocity.getX(), velocity.getY(), velocity.getZ()));
		this.tracker.velocityChanged = false;
	}

	public void syncMeta() {
		if (!this.tracker.dead) {
			//meta data packets (used for effects like smoke toggling)
			DataWatcher datawatcher = this.tracker.getDataWatcher();
			if (datawatcher.a()) {
				this.broadcastIncludingSelf(new Packet40EntityMetadata(this.tracker.id, datawatcher, false));
			}
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
		entityplayer.netServerHandler.sendPacket(((MinecartMember) this.tracker).getSpawnPacket());

		// Meta data
		entityplayer.netServerHandler.sendPacket(new Packet40EntityMetadata(this.tracker.id, this.tracker.getDataWatcher(), true));

		// Velocity, positioning and passenger
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
