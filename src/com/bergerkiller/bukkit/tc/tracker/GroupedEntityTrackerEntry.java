package com.bergerkiller.bukkit.tc.tracker;

import java.util.List;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

import net.minecraft.server.*;

public class GroupedEntityTrackerEntry extends EntityTrackerEntry {

	public GroupedEntityTrackerEntry(MinecartMember member) {
		super(member, 80, 3, true);
		//set old locations far away to enforce an update
        this.o = this.tracker.locX + 16;
        this.p = this.tracker.locY + 16;
        this.q = this.tracker.locZ + 16;
	}

    private int t = 0;
    private double o;
    private double p;
    private double q;

    public void a() {
    	super.a();
    	//this member is no longer tracked
    	
    }
    
    public boolean needsSync() {
    	return this.tracker.ce || this.l % this.c == 0;
    }
    
    
    public void sync(boolean forced) {
        if (forced || this.needsSync()) {
            int i = MathHelper.floor(this.tracker.locX * 32);
            int j = MathHelper.floor(this.tracker.locY * 32);
            int k = MathHelper.floor(this.tracker.locZ * 32);
            int l = MathHelper.d(this.tracker.yaw * 256 / 360);
            int i1 = MathHelper.d(this.tracker.pitch * 256 / 360);
            int j1 = i - this.d;
            int k1 = j - this.e;
            int l1 = k - this.f;
            
            boolean moved = forced || Math.abs(j1) >= 4 || Math.abs(k1) >= 4 || Math.abs(l1) >= 4;
            boolean looked = Math.abs(l - this.g) >= 4 || Math.abs(i1 - this.h) >= 4;

            Packet packet = null;
            
            //movement and look-at packets
            if (Math.abs(j1) < 128 && Math.abs(k1) < 128 && Math.abs(l1) < 128 && this.t <= 400) {
                if (moved && looked) {
                    packet = new Packet33RelEntityMoveLook(this.tracker.id, (byte) j1, (byte) k1, (byte) l1, (byte) l, (byte) i1);
                } else if (moved) {
                    packet = new Packet31RelEntityMove(this.tracker.id, (byte) j1, (byte) k1, (byte) l1);
                } else if (looked) {
                    packet = new Packet32EntityLook(this.tracker.id, (byte) l, (byte) i1);
                }
            } else {
                this.t = 0;
                this.tracker.locX = (double) i / 32;
                this.tracker.locY = (double) j / 32;
                this.tracker.locZ = (double) k / 32;
                packet = new Packet34EntityTeleport(this.tracker.id, i, j, k, (byte) l, (byte) i1);
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

            //motion packets
            double motDiff = Util.distanceSquared(this.tracker.motX, this.tracker.motY, this.tracker.motZ, this.i, this.k, this.k);
            if (motDiff > 0.0004 || (motDiff > 0 && this.tracker.motX == 0 && this.tracker.motY == 0.0D && this.tracker.motZ == 0)) {
                this.i = this.tracker.motX;
                this.j = this.tracker.motY;
                this.k = this.tracker.motZ;
                this.broadcast(new Packet28EntityVelocity(this.tracker.id, this.i, this.j, this.k));
            }

            //sending the packet
            if (packet != null) {
                this.broadcast(packet);
            }

            //meta data packets (used for effects like smoke toggling)
            DataWatcher datawatcher = this.tracker.getDataWatcher();
            if (datawatcher.a()) {
                this.broadcast(new Packet40EntityMetadata(this.tracker.id, datawatcher));
            }
        }

        //velocity changed
        if (this.tracker.velocityChanged) {
        	this.broadcast(new Packet28EntityVelocity(this.tracker));
            this.tracker.velocityChanged = false;
        }
    	
    }
    
    
	@SuppressWarnings("rawtypes")
	public void track(List list) {
		//is used to update player information
		this.m = this.tracker.e(this.o, this.p, this.q) > 16.0D;
        if (this.m) {
            this.o = this.tracker.locX;
            this.p = this.tracker.locY;
            this.q = this.tracker.locZ;
            this.scanPlayers(list);
        }
        MinecartGroup group = this.getMember().getGroup();
        ++this.t;
        ++this.l;
        if (group == null || group.size() == 1) {
        	this.sync(false);
        }
        this.tracker.ce = false;
    }
    
    
    @SuppressWarnings("unchecked")
	public void b(EntityPlayer entityplayer) {
        double d0 = entityplayer.locX - (double) (this.d / 32);
        double d1 = entityplayer.locZ - (double) (this.f / 32);

        if (d0 >= (double) (-this.b) && d0 <= (double) this.b && d1 >= (double) (-this.b) && d1 <= (double) this.b) {
            if (!this.trackedPlayers.contains(entityplayer)) {
                this.trackedPlayers.add(entityplayer);
                if (!this.tracker.dead) {
                	int type = this.getMember().type;
                	if (type < 0 || type > 2) return;
                	entityplayer.netServerHandler.sendPacket(new Packet23VehicleSpawn(this.tracker, 10 + type));
                }
                entityplayer.netServerHandler.sendPacket(new Packet28EntityVelocity(this.tracker.id, this.tracker.motX, this.tracker.motY, this.tracker.motZ));
            }
        } else if (this.trackedPlayers.contains(entityplayer)) {
            this.trackedPlayers.remove(entityplayer);
            entityplayer.netServerHandler.sendPacket(new Packet29DestroyEntity(this.tracker.id));
        }
    }
    
    public MinecartMember getMember() {
    	return (MinecartMember) this.tracker;
    }

}
