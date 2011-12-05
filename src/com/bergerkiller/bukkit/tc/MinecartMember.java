package com.bergerkiller.bukkit.tc;

import java.util.HashSet;

import net.minecraft.server.EntityMinecart;
import net.minecraft.server.MathHelper;
import net.minecraft.server.World;

import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.API.CoalUsedEvent;
import com.bergerkiller.bukkit.tc.API.MemberBlockChangeEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent.ActionType;
import com.bergerkiller.bukkit.tc.Listeners.CustomEvents;
import com.bergerkiller.bukkit.tc.Utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;
import com.bergerkiller.bukkit.tc.Utils.FaceUtil;

public class MinecartMember extends NativeMinecartMember {
	
	private float customYaw = 0;
	MinecartGroup group;
	private Block activesign = null;
	private int blockx, blocky, blockz;
	private boolean railsloped = false;
	private boolean isDerailed = false;
	private boolean isFlying = false;
	
	private MinecartMember(World world, double x, double y, double z, int type) {
		super(world, x, y, z, type);
		this.customYaw = this.yaw + 90;
		try {
			this.updateBlock(true);
		} catch (GroupUnloadedException ex) {}
	}
	
	/*
	 * Overridden Minecart functions
	 */
	@Override
	public void w_() {
		MinecartGroup g = this.getGroup();
		if (g == null) return;
		if (this.dead) {
			//remove self
			g.remove(this);
		} else if (g.size() == 0) {
			g.remove();
			super.w_();
		} else if (g.tail() == this) {
			g.doPhysics();
		}
	}	
	
	public void postUpdate(double speedFactor) throws GroupUnloadedException {
		super.postUpdate(speedFactor);
		this.updateBlock(false);
	}
	
	@Override
	public boolean onCoalUsed() {
		CoalUsedEvent event = CoalUsedEvent.call(this);
		if (event.useCoal()) {
			for (MinecartMember mm : this.getNeightbours()) {
				//Is it a storage minecart?
				if (mm.type == 1) {
					//has coal?
					for (int i = 0; i < mm.getSize(); i++) {
						if (mm.getItem(i) != null && mm.getItem(i).id == Material.COAL.getId()) {
							 mm.getItem(i).count--;
							 return true;
						}
					}
				}
			}
		}
		return event.refill();
	}
	
	private void updateBlock(boolean forced) throws GroupUnloadedException {
		if (this.dead) return;
		int x = this.getBlockX();
		int y = this.getBlockY();
		int z = this.getBlockZ();
		if (forced || x != this.blockx || z != this.blockz || y != (this.railsloped ? this.blocky : this.blocky + 1)) {
			this.blockx = x;
			this.blocky = y;
			this.blockz = z;
			//is it in loaded chunks?
			if (!this.world.areChunksLoaded(this.blockx, this.blocky, this.blockz, 32)) {
			    if (this.getGroup().canUnload()) {
			    	//this train has to be unloaded
			    	GroupManager.hideGroup(this.getGroup());
			    	throw new GroupUnloadedException();
			    } else {
			    	//load nearby chunks
					int xmid = this.blockx >> 4;
					int zmid = this.blockz >> 4;
					for (int cx = xmid - 2; cx <= xmid + 2; cx++) {
						for (int cz = zmid - 2; cz <= zmid + 2; cz++) {
							this.getWorld().getChunkAt(cx, cz);
						}
					}
			    }
			}
			
			this.railsloped = false;
			this.isDerailed = false;
			this.isFlying = false;
			int r = this.world.getTypeId(this.blockx, this.blocky - 1, this.blockz);
			if (BlockUtil.isRails(r)) {
				--this.blocky;
			} else {		
				r = this.world.getTypeId(this.blockx, this.blocky, this.blockz);
				if (BlockUtil.isRails(r)) {
					this.railsloped = true;
				} else {
					this.isDerailed = true;
					if (r == 0) this.isFlying = true;
				}
			}
			//update active sign
			this.setActiveSign(this.getBlock(0, -2, 0));
			//event
			MemberBlockChangeEvent.call(this);
		} else if (this.activesign != null) {
			//move
			SignActionEvent info = new SignActionEvent(this.activesign, this);
			CustomEvents.onSign(info, ActionType.MEMBER_MOVE);
		}
	}
	
	/*
	 * MinecartMember <> EntityMinecart replacement functions
	 */
	private static HashSet<MinecartMember> replacedCarts = new HashSet<MinecartMember>();
	private static boolean denyConversion = false;
	
	public static MinecartMember get(Object o) {
		if (o == null) return null;
		if (o instanceof Minecart) {
			o = EntityUtil.getNative((Minecart) o);
		}
		if (o instanceof MinecartMember) {
			return (MinecartMember) o;
		} else {
			return null;
		}
	}
	public static MinecartMember[] getAll(Object... objects) {
		MinecartMember[] rval = new MinecartMember[objects.length];
		for (int i = 0; i < rval.length; i++) {
			rval[i] = get(objects[i]);
		}
		return rval;
	}
	public static MinecartMember convert(Object o) {
		if (o == null) return null;
		EntityMinecart em;
		if (o instanceof EntityMinecart) {
			em = (EntityMinecart) o;
		} else if (o instanceof Minecart) {
			em = EntityUtil.getNative((Minecart) o);
		} else {
			return null;
		}
		if (em instanceof MinecartMember) return (MinecartMember) em;
		if (em.dead) return null; //prevent conversion of dead entities 
		//not found, conversion allowed?
		if (denyConversion) return null;
		//convert
		MinecartMember mm = new MinecartMember(em.world, em.lastX, em.lastY, em.lastZ, em.type);
		EntityUtil.replaceMinecarts(em, mm);
		replacedCarts.add(mm);
		return mm;
	}
	public static MinecartMember[] convertAll(Entity... entities) {
		MinecartMember[] rval = new MinecartMember[entities.length];
		for (int i = 0; i < rval.length; i++) {
			rval[i] = convert(entities[i]);
		}
		return rval;
	}
	
	public static MinecartMember spawn(Location at, int type, double forwardforce) {
		MinecartMember mm = new MinecartMember(EntityUtil.getNative(at.getWorld()), at.getX(), at.getY(), at.getZ(), type);
		mm.yaw = at.getYaw();
		mm.pitch = at.getPitch();
		mm.setForce(forwardforce, mm.yaw);
		mm.world.addEntity(mm);
		return mm;
	}
	
	public static MinecartMember getAt(Location at) {
		return getAt(at, null);
	}
	public static MinecartMember getAt(Location at, MinecartGroup in) {
		return getAt(at, in, 1);
	}
	public static MinecartMember getAt(Location at, MinecartGroup in, double searchRadius) {
		searchRadius *= searchRadius;
		for (Entity e : at.getBlock().getChunk().getEntities()) {
			if (e instanceof Minecart) {
				MinecartMember mm = get(e);
				if (mm == null) continue;
				if (in != null && mm.getGroup() != in) continue;
				if (mm.distanceSquared(at) > searchRadius) continue;
				return mm;
			}
		}
		return null;
	}
	public static EntityMinecart undoReplacement(MinecartMember mm) {
		replacedCarts.remove(mm);
		if (!mm.dead) {
			denyConversion = true;
			EntityMinecart em = new EntityMinecart(mm.world, mm.lastX, mm.lastY, mm.lastZ, mm.type);
			EntityUtil.replaceMinecarts(mm, em);
			denyConversion = false;
			return em;
		}
		return null;
	}
	public static void undoReplacement() {
		for (MinecartMember m : replacedCarts.toArray(new MinecartMember[0])) {
			undoReplacement(m);
		}
	}
	
	/*
	 * General getters and setters
	 */
 	public Minecart getMinecart() {
		return (Minecart) this.getBukkitEntity();
	}
 	public MinecartGroup getGroup() {
 		if (this.group == null) {
 			this.group = MinecartGroup.create(this);
 		}
 		return this.group;
 	}
 	public MinecartMember[] getNeightbours() {
		if (this.getGroup() == null) return new MinecartMember[0];
		int index = this.getGroup().indexOf(this);
		if (index == -1) return new MinecartMember[0];
		if (index > 0) {
			if (index < this.getGroup().size() - 1) {
				return new MinecartMember[] {
						this.getGroup().get(index - 1), 
						this.getGroup().get(index + 1)};
			} else {
				return new MinecartMember[] {this.getGroup().get(index - 1)};
			}
		} else if (index < this.getGroup().size() - 1) {
			return new MinecartMember[] {this.getGroup().get(index + 1)};
		} else {
			return new MinecartMember[0];
		}
	}
	
 	public Block getBlock(int dx, int dy, int dz) {
 		return this.world.getWorld().getBlockAt(this.blockx + dx, this.blocky + dy, this.blockz + dz);
 	}
	public Block getBlock() {
		return this.getBlock(0, 0, 0);
	}
 	public Block getRailsBlock() {
		if (this.isDerailed) return null;
		Block b = this.getBlock();
		if (BlockUtil.isRails(b.getTypeId())) {
			return b;
		} else {
			this.isDerailed = true;
			return null;
		}
	}
	public BlockFace getRailDirection() {
		Rails r = BlockUtil.getRails(this.getRailsBlock());
		if (r == null) return BlockFace.NORTH;
		return r.getDirection();
	}
	public Block getGroundBlock() {
		return this.getBlock(0, -1, 0);
	}
	
	public void setActiveSign(Block activesign) {
		//update active sign
		if (this.activesign == activesign) return;
		//set inactive
		if (this.activesign != null) {
			SignActionEvent info = new SignActionEvent(this.activesign, this);
			CustomEvents.onSign(info, ActionType.MEMBER_LEAVE);
			if (this.dead) return; 
			MinecartGroup g = this.getGroup();
			if (g.size() == 1 || g.tail() == this) {
				this.getGroup().setActiveSign(info, false);
			}
			this.activesign = null;
		}
		//set active
		this.activesign = activesign;
		if (BlockUtil.isSign(this.activesign)) {
			if (this.dead) return;
			SignActionEvent info = new SignActionEvent(this.activesign, this);
			CustomEvents.onSign(info, ActionType.MEMBER_ENTER);
			if (this.dead) return;
			MinecartGroup g = this.getGroup();
			if (g.size() == 1 || g.tail() != this) {
				this.getGroup().setActiveSign(info, true);
			}
			if (this.dead) return; 
		} else {
			this.activesign = null;
		}
	}
	public Block getSignBlock() {
		return this.activesign;
	}
	public Sign getSign() {
		return BlockUtil.getSign(this.activesign);
	}
	public boolean hasSign() {
		return this.activesign != null;
	}
	
	public VelocityTarget addTarget(Location to, double toVelocity, long delayMS) {
		return this.getGroup().addTarget(this, to, toVelocity, delayMS);
	}
	public VelocityTarget setTarget(Location to, double toVelocity, long delayMS) {
		this.getGroup().clearTargets();
		return this.getGroup().addTarget(this, to, toVelocity, delayMS);
	}
	public VelocityTarget addTarget(Vector offset, double toVelocity, long delayMS) {
		return this.addTarget(this.getLocation().add(offset), toVelocity, delayMS);
	}
	public VelocityTarget setTarget(Vector offset, double toVelocity, long delayMS) {
		return this.setTarget(this.getLocation().add(offset), toVelocity, delayMS);
	}
	public VelocityTarget addTarget(BlockFace direction, double distance, double toVelocity, long delayMS) {
		return this.addTarget(FaceUtil.faceToVector(direction, distance), toVelocity, delayMS);
	}
	public VelocityTarget setTarget(BlockFace direction, double distance, double toVelocity, long delayMS) {
		return this.setTarget(FaceUtil.faceToVector(direction, distance), toVelocity, delayMS);
	}
		
	/*
	 * Velocity functions
	 */
	public double getForce() {
		return Util.length(motX, motZ);
	}
	public double getForwardForce() {
		float yaw = this.getYaw() * Util.DEGTORAD;
        return -MathHelper.sin(yaw) * this.motZ - MathHelper.cos(yaw) * this.motX;
	}
	public void setForceFactor(final double factor) {
		this.motX *= factor;
		this.motY *= factor;
		this.motZ *= factor;
	}
	public void setForce(double force, float yaw) {
		 if (isTurned()) {
		     double l = this.getForce();
		 	   if (l > 0.001) {
		 	       double factor = force / l;
		 	 	   this.motX *= factor;
		 	 	   this.motZ *= factor;
		 	 	   return;
		     }
		 }
		yaw *= Util.DEGTORAD;
		this.motX = -MathHelper.cos(yaw) * force;
		this.motZ = -MathHelper.sin(yaw) * force;
		if (this.railsloped) {
			//calculate upwards or downwards force
			BlockFace raildir = this.getRailDirection();
			BlockFace dir = this.getDirection();
			final float factor = 0.7071F;
			if (dir == raildir) {
				this.motY = factor * force;
			} else if (dir == raildir.getOppositeFace()) {
				this.motY = -factor * force;
			}
		}
	}
	public void setForce(double force, Location to) {
		setForce(force, Util.getLookAtYaw(this.getLocation(), to));
	}
	public void setForce(double force, BlockFace direction) {
		setForce(force, FaceUtil.faceToYaw(direction));
	}
	public void setForwardForce(double force) {
		setForce(force, this.getYaw());
	}
	public void limitSpeed() {
		//Limits the velocity to the maximum
		double currvel = getForce();
		if (currvel > this.maxSpeed && currvel > 0.01) {
			double factor = this.maxSpeed / currvel;
			this.motX *= factor;
			this.motZ *= factor;
		}
	}
	public void setVelocity(Vector velocity) {
		this.motX = velocity.getX();
		this.motZ = velocity.getZ();
		this.motY = velocity.getY();
	}
	public Vector getVelocity() {
		return new Vector(this.motX, this.motY, this.motZ);
	}
	public BlockFace getDirection() {
		float yaw;
		if (this.isMoving()) {
			yaw = Util.getLookAtYaw(this.getVelocity());
		} else {
			yaw = this.getYaw();
		}
		return FaceUtil.yawToFace(yaw, false);
	}
	
	public TrackMap makeTrackMap(int size) {
		return new TrackMap(BlockUtil.getRailsBlock(this.getLocation()), this.getDirection(), size);
	}
	
	/*
	 * Location functions
	 */
	public void teleport(Location to) {
		getMinecart().teleport(to);
	}
	public double getSubX() {
		double x = getX() + 0.5;
		return x - (int) x;
	}	
	public double getSubZ() {
		double z = getZ() + 0.5;
		return z - (int) z;
	}	
	public double distance(MinecartMember m) {
		return Util.distance(this.getX(), this.getY(), this.getZ(), m.getX(), m.getY(), m.getZ());
	}
	public double distance(Location l) {
		return Util.distance(this.getX(), this.getY(), this.getZ(), l.getX(), l.getY(), l.getZ());
	}
	public double distanceXZ(MinecartMember m) {
		return Util.distance(this.getX(), this.getZ(), m.getX(), m.getZ());
	}
	public double distanceXZ(Location l) {
		return Util.distance(this.getX(), this.getZ(), l.getX(), l.getZ());
	}
	public double distanceSquared(MinecartMember m) {
		return Util.distanceSquared(this.getX(), this.getY(), this.getZ(), m.getX(), m.getY(), m.getZ());
	}
	public double distanceSquared(Location l) {
		return Util.distanceSquared(this.getX(), this.getY(), this.getZ(), l.getX(), l.getY(), l.getZ());
	}
	public double distanceXZSquared(MinecartMember m) {
		return Util.distanceSquared(this.getX(), this.getZ(), m.getX(), m.getZ());
	}
	public double distanceXZSquared(Location l) {
		return Util.distanceSquared(this.getX(), this.getZ(), l.getX(), l.getZ());
	}
	public boolean isNearOf(MinecartMember member) {
		double max = TrainCarts.maxCartDistance * TrainCarts.maxCartDistance;
		if (this.distanceXZSquared(member) > max) return false;
		if (this.isDerailed() || member.isDerailed()) {
			return Math.abs(this.getY() - member.getY()) <= max;
		}
		return true;
	}
	
	/*
	 * Pitch functions
	 */
	public float getPitch() {
		return this.pitch;
	}
	public float getPitchDifference(MinecartMember comparer) {
		return getPitchDifference(comparer.getPitch());
	}
	public float getPitchDifference(float pitchcomparer) {
		return Util.getAngleDifference(getPitch(), pitchcomparer);
	}
		
	/*
	 * Yaw functions
	 */
	public float getYaw() {
		return this.customYaw;
	}
	public float getYawDifference(float yawcomparer) {
		return Util.getAngleDifference(customYaw, yawcomparer);
	}	
	public float updateYaw(float yawcomparer) {
		double x = this.getSubX();
		double z = this.getSubZ();
		if (x == 0 && z != 0 && Math.abs(motX) < 0.001) {
			//cart is driving along the x-axis
			customYaw = -90;
		} else if (z == 0 && x != 0 && Math.abs(motZ) < 0.001) {
			//cart is driving along the z-axis
			customYaw = -180;
		} else {
			//try to get the yaw from the rails
			customYaw = FaceUtil.getRailsYaw(this.getRailDirection());
		}
		//Fine tuning
		if (getYawDifference(yawcomparer) > 90) customYaw += 180;
		
		return customYaw;
	}
	public float updateYaw(BlockFace yawdirection) {
		return this.updateYaw(FaceUtil.faceToYaw(yawdirection));
	}
	public float updateYawTo(MinecartMember head) {
		return this.updateYaw(Util.getLookAtYaw(this, head));
	}
	public float updateYawFrom(MinecartMember tail) {
		return this.updateYaw(Util.getLookAtYaw(tail, this));
	}
	
	/*
	 * States
	 */
 	public boolean isMoving() {
		if (motX > 0.001) return true;
		if (motX < -0.001) return true;
		if (motZ > 0.001) return true;
		if (motZ < -0.001) return true;
		return false;
	}
	public boolean isTurned() {
		if (getYaw() == 45 || getYaw() == -45) return true;
		if (getYaw() == 135 || getYaw() == -135) return true;
		if (getYaw() == 225 || getYaw() == -225) return true;
		if (getYaw() == 315 || getYaw() == -315) return true;
		return getSubX() != 0 && getSubZ() != 0;
	}	
	public boolean isDerailed() {
		return this.isDerailed;
	}
	public boolean isFlying() {
		return this.isFlying;
	}
	public boolean isHeadingTo(Location target) {
		return Util.isHeadingTo(this.getLocation(), target, this.getVelocity());
	}
	public boolean isOnSlope() {
		return this.railsloped;
	}
	public boolean isValidMember() {
		return !this.dead || !TrainCarts.removeDerailedCarts || !this.isDerailed;
	}
	public boolean isInChunk(Chunk chunk) {
		return this.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
	}
	public boolean isInChunk(org.bukkit.World world, int cx, int cz) {
		if (world != this.getWorld()) return false;
		if (Math.abs(cx - this.getChunkX()) > 2) return false;
		if (Math.abs(cz - this.getChunkZ()) > 2) return false;
	    return true;
	}
		
	/*
	 * Actions
	 */
	public void push(Entity entity) {
		float yaw = this.getYaw();
		float lookat = Util.getLookAtYaw(this.getBukkitEntity(), entity) - yaw;
		lookat = Util.normalAngle(lookat);
		if (lookat > 0) {
			yaw -= 180;
		}
		//push the obstacle awaayyy :d
		Vector vel = Util.getDirection(yaw, 0).multiply(TrainCarts.pushAwayForce);
		entity.setVelocity(vel);
	}
	public void playLinkEffect() {
		Location loc = this.getLocation();
		loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
		loc.getWorld().playEffect(loc, Effect.EXTINGUISH, 0);
	}
	public void destroy() {
		this.destroy(true);
	}
	public void destroy(boolean remove) {
		if (this.passenger != null) this.passenger.setPassengerOf(null);
		replacedCarts.remove(this);
		this.die();
		if (remove) this.remove();
	}
	public boolean remove() {
		return this.getGroup().remove(this);
	}
	public void eject() {
		this.getMinecart().eject();
	}
	public void eject(Vector offset) {
		if (this.passenger != null) {
			Entity passenger = this.passenger.getBukkitEntity();
			this.passenger.setPassengerOf(null);
			Task t = new Task(TrainCarts.plugin, passenger, offset) {
				public void run() {
					Entity e = (Entity) getArg(0);
					Vector offset = (Vector) getArg(1);
					e.teleport(e.getLocation().add(offset.getX(), offset.getY(), offset.getZ()));
				}
			};
			t.startDelayed(0);
		}
	}
	public boolean connect(MinecartMember with) {
		return this.getGroup().connect(this, with);
	}
	public void stop() {
		this.motX = 0;
		this.motY = 0;
		this.motZ = 0;
	}
	public void reverse() {
		this.motX *= -1;
		this.motY *= -1;
		this.motZ *= -1;
		this.customYaw = Util.normalAngle(this.customYaw + 180);
	}

}