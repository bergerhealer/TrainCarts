package com.bergerkiller.bukkit.tc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import net.minecraft.server.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MinecartMemberTrackerEntry extends EntityTrackerEntry {

	private static Field trackerSet = null;
	private static Field trackerMap = null;
	public static void failFields(Throwable t) {
		trackerMap = null;
		trackerSet = null;
		Util.log(Level.SEVERE, "Failed to initialize the entity tracker replacement:");
		t.printStackTrace();
		Util.log(Level.INFO, "Train movement will not be smoothed!");
	}
	public static void initFields() {
		try {
			trackerMap = EntityTracker.class.getDeclaredField("trackedEntities");
			trackerSet = EntityTracker.class.getDeclaredField("a");
			trackerMap.setAccessible(true);
			trackerSet.setAccessible(true);
		} catch (Throwable t) {
			failFields(t);
		}
	}
	public static MinecartMemberTrackerEntry get(MinecartMember member) {
		EntityTracker tracker = ((WorldServer) member.world).tracker;
		if (trackerMap != null) {
			try {
				IntHashMap map = (IntHashMap) trackerMap.get(tracker);
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
					Set set = (Set) trackerSet.get(tracker);
					set.remove(entry);
					set.add(entry);
					return (MinecartMemberTrackerEntry) entry;
				}
			} catch (Throwable t) {
				failFields(t);
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
    }
    public MinecartMemberTrackerEntry(MinecartMember member) {
    	super(member, 80, 3, true);
        this.prevX = this.tracker.locX + 16;
        this.prevY = this.tracker.locY + 16;
        this.prevZ = this.tracker.locZ + 16;
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
    
    public void syncLocation(boolean teleport) {
    	int i = MathHelper.floor(this.tracker.locX * 32);
    	int j = MathHelper.floor(this.tracker.locY * 32);
    	int k = MathHelper.floor(this.tracker.locZ * 32);
    	int l = MathHelper.d(this.tracker.yaw * 256 / 360);
    	int i1 = MathHelper.d(this.tracker.pitch * 256 / 360);
    	int j1 = i - this.d;
    	int k1 = j - this.e;
    	int l1 = k - this.f;
    	
    	boolean moved = Math.abs(j1) >= 4 || Math.abs(k1) >= 4 || Math.abs(l1) >= 4; 
    	boolean looked = Math.abs(l - this.g) >= 4 || Math.abs(i1 - this.h) >= 4;
    	
    	//movement and looked packets
    	if (!teleport && Math.abs(j1) < 128 && Math.abs(k1) < 128 && Math.abs(l1) < 128) {
    		if (moved && looked) {
    			this.broadcast(new Packet33RelEntityMoveLook(this.tracker.id, (byte) j1, (byte) k1, (byte) l1, (byte) l, (byte) i1));
    		} else if (moved) {
    			this.broadcast(new Packet31RelEntityMove(this.tracker.id, (byte) j1, (byte) k1, (byte) l1));
    		} else if (looked) {
    			this.broadcast(new Packet32EntityLook(this.tracker.id, (byte) l, (byte) i1));
    		}
    	} else {
    		this.tracker.locX = (double) i / 32;
    		this.tracker.locY = (double) j / 32;
    		this.tracker.locZ = (double) k / 32;
    		this.broadcast(new Packet34EntityTeleport(this.tracker.id, i, j, k, (byte) l, (byte) i1));
    		this.ticksNoTeleport = 0;
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
    	this.tracker.ce = false;
    	
    	//motion packets
    	double motDiff = Util.distanceSquared(this.tracker.motX, this.tracker.motY, this.tracker.motZ, this.i, this.k, this.k);
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
    	this.a(entityplayer);
    	entityplayer.netServerHandler.sendPacket(new Packet29DestroyEntity(this.tracker.id));
    }   
	public void b(EntityPlayer entityplayer) {
		if (this.tracker.dead) return;
        if (entityplayer != this.tracker) {
            double d0 = entityplayer.locX - (double) (this.d / 32);
            double d1 = entityplayer.locZ - (double) (this.f / 32);

            if (d0 >= (double) (-this.b) && d0 <= (double) this.b && d1 >= (double) (-this.b) && d1 <= (double) this.b) {
                if (this.trackedPlayers.add(entityplayer)) {
                	//send spawn packet
                    int type = ((MinecartMember) this.tracker).type;
                    if (type < 0 || type > 2) {
                    	type = 0;
                    }
                    entityplayer.netServerHandler.sendPacket(new Packet23VehicleSpawn(this.tracker, 10 + type));
                    entityplayer.netServerHandler.sendPacket(new Packet28EntityVelocity(this.tracker));
                }
            } else {
            	this.c(entityplayer);
            }
        }
    }
    
}
