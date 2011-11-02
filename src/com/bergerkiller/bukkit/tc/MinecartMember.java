package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.HashSet;

import net.minecraft.server.EntityMinecart;
import net.minecraft.server.World;

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
import com.bergerkiller.bukkit.tc.API.GroupUpdateEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.API.UpdateStage;
import com.bergerkiller.bukkit.tc.API.SignActionEvent.ActionType;
import com.bergerkiller.bukkit.tc.Listeners.CustomEvents;
import com.bergerkiller.bukkit.tc.Utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Utils.ChunkUtil;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;
import com.bergerkiller.bukkit.tc.Utils.FaceUtil;

public class MinecartMember extends NativeMinecartMember {
	
	private double forceFactor = 1;
	private float customYaw = 0;
	private MinecartGroup group;
	private Location activeSign = null;
	
	public MinecartMember(World world, double d0, double d1, double d2, int i) {
		super(world, d0, d1, d2, i);
		this.customYaw = this.yaw + 90;
	}
	
	/*
	 * Overridden Minecart functions
	 */
	@Override
	public void s_() {
		motX = Util.fixNaN(motX);
		motY = Util.fixNaN(motY);
		motZ = Util.fixNaN(motZ);
		MinecartGroup g = this.getGroup();
		if (g.size() == 0) {
			this.group = null;
			g.remove();
			super.s_();
		} else {
			if (g.tail() == this) {
				if (GroupUpdateEvent.call(g, UpdateStage.FIRST)) {
					g.updateTarget();
					for (MinecartMember m : g) {
						m.maxSpeed = getGroup().getProperties().speedLimit;
						motX = Util.fixNaN(motX);
						motY = Util.fixNaN(motY);
						motZ = Util.fixNaN(motZ);
						//General velocity update
						m.preUpdate();
					}
					if (GroupUpdateEvent.call(g, UpdateStage.BEFORE_GROUP)) {
						g.update();
						if (GroupUpdateEvent.call(g, UpdateStage.AFTER_GROUP)) {
							for (MinecartMember m : g.toArray()) {
								m.postUpdate(m.forceFactor);
							}
							GroupUpdateEvent.call(g, UpdateStage.LAST);
						}
					}
				}
			}
		}
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
	
	/*
	 * MinecartMember <> EntityMinecart replacement functions
	 */
	private static HashSet<MinecartMember> replacedCarts = new HashSet<MinecartMember>();
	private static boolean denyConversion = false;
	
	public static MinecartMember get(Object o) {
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
	public static MinecartMember convert(Entity e) {
		if (!(e instanceof Minecart)) return null;
		EntityMinecart em = EntityUtil.getNative((Minecart) e);
		if (em instanceof MinecartMember) return (MinecartMember) em;
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
	
	public static MinecartMember get(Location loc, int type) {
		MinecartMember mm = new MinecartMember(EntityUtil.getNative(loc.getWorld()), loc.getX(), loc.getY(), loc.getZ(), type);
		mm.yaw = loc.getYaw();
		mm.pitch = loc.getPitch();
		mm.world.addEntity(mm);
		replacedCarts.add(mm);
		return mm;
	}
	
	public static MinecartMember getAt(Location at) {
		return getAt(at, null);
	}
	public static MinecartMember getAt(Location at, MinecartGroup in) {
		return getAt(at, in, 1);
	}
	public static MinecartMember getAt(Location at, MinecartGroup in, double searchRadius) {
		for (Entity e : at.getBlock().getChunk().getEntities()) {
			if (e instanceof Minecart) {
				MinecartMember mm = get(e);
				if (mm != null) {
					if (in == null || mm.getGroup() == in) {
					  if (mm.getLocation().getWorld() != at.getWorld()){continue;}//cannot measure between worlds
						if (mm.getLocation().distance(at) <= searchRadius) {
							return mm;
						}
					}
				}
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
	 * Other
	 */
	public void destroy() {
		if (this.passenger != null) this.passenger.setPassengerOf(null);
		replacedCarts.remove(this);
		this.die();
		this.remove();
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
 	void setGroup(MinecartGroup group) {
 		this.group = group;
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
	public Block getRailsBlock() {
		if (super.isDerailed()) return null;
		return BlockUtil.getRailsBlock(this.getMinecart());
	}
	public Rails getRails() {
		return BlockUtil.getRails(this.getRailsBlock());
	}
	public Block getSignBlock() {
		Block b = this.getRailsBlock();
		if (b == null) return null;
		b = b.getRelative(0, -2, 0);
		if (BlockUtil.isSign(b)) return b;
		return null;
	}
	public Sign getSign() {
		return BlockUtil.getSign(getSignBlock());
	}
	public VelocityTarget addTarget(Location to, double toVelocity, long delayMS) {
		return this.getGroup().addTarget(this, to, toVelocity, delayMS);
	}
	public VelocityTarget setTarget(Location to, double toVelocity, long delayMS) {
		this.getGroup().clearTargets();
		return this.getGroup().addTarget(this, to, toVelocity, delayMS);
	}
	
	/*
	 * Velocity functions
	 */
	public void stop() {
		this.motX = 0;
		this.motY = 0;
		this.motZ = 0;
	}
	public double getForce() {
		return Util.length(motX, motZ);
	}
	public double getForwardForce() {
		double ryaw = this.getYaw() / 180 * Math.PI;
        return -Math.sin(ryaw) * this.motZ - Math.cos(ryaw) * this.motX;
	}
	public void setForceFactor(double factor) {
		this.motX *= factor;
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
		double ryaw = yaw / 180 * Math.PI;
		this.motX = -Math.cos(ryaw) * force;
		this.motZ = -Math.sin(ryaw) * force;
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
	public void addForceFactor(double forcer, double factor) {
		this.forceFactor = 1 + (forcer * factor);
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
		return new Vector(motX, motY, motZ);
	}
	
	public TrackMap makeTrackMap(int size) {
		return new TrackMap(BlockUtil.getRailsBlock(this.getLocation()), this.getDirection(), size);
	}
	public void addNearChunks(ArrayList<SimpleChunk> rval, boolean addloaded, boolean addunloaded) {
		int chunkX = ChunkUtil.toChunk(this.getX());
		int chunkZ = ChunkUtil.toChunk(this.getZ());
		ChunkUtil.addNearChunks(rval, this.getWorld(), chunkX, chunkZ, 2, addloaded, addunloaded);
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
	public boolean isMiddleOf(MinecartMember m1, MinecartMember m2) {
		double d1 = this.distanceXZSquared(m1);
		double d2 = m2.distanceXZSquared(m1);
		if (d1 < d2) {
			double max = TrainCarts.maxCartDistance * TrainCarts.maxCartDistance;
			//is the XZ height allowed?
			if (d1 <= max && (d2 <= max)) {
				if (m1.isDerailed() || m2.isDerailed()) {
					//is the Y value allowed?
					if (Math.abs(m1.getY() - this.getY()) <= TrainCarts.maxCartDistance) {
						if (Math.abs(m2.getY() - this.getY()) <= TrainCarts.maxCartDistance) {
							return true;
						}
					}
				} else {
					return true;
				}
			}
		}
		return false;
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
	public float setYaw(float yawcomparer) {
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
			customYaw = BlockUtil.getRailsYaw(getRails());
		}
		//Fine tuning
		if (getYawDifference(yawcomparer) > 90) customYaw += 180;
		
		return customYaw;
	}
	public float setYawTo(MinecartMember head) {
		return setYaw(Util.getLookAtYaw(this, head));
	}
	public float setYawFrom(MinecartMember tail) {
		return setYaw(Util.getLookAtYaw(tail, this));
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
		return isDerailed(this);
	}
	public boolean isHeadingTo(Location target) {
		return Util.isHeadingTo(this.getLocation(), target, this.getVelocity());
	}
	public static boolean isDerailed(Minecart m) {
		if (m == null) return true;
		MinecartMember mm = get(m);
		if (mm == null) {
			return BlockUtil.getRailsBlock(m) == null;
		} else {
			return isDerailed(mm);
		}
	}
	public static boolean isDerailed(MinecartMember mm) {
		if (mm == null) return true;
		return mm.getRailsBlock() == null;
	}

	/*
	 * Active sign (sign underneath tracks)
	 */
 	public boolean hasActiveSign() {
 		return this.activeSign != null;
 	}
 	public Block getActiveSign() {
 		if (this.hasActiveSign()) {
 	 		return this.activeSign.getBlock();
 		} else {
 			return null;
 		}
 	}
 	public void setActiveSign(Block signblock) {
 		if (signblock == null) {
 			this.activeSign = null;
 		} else {
 	 		this.activeSign = signblock.getLocation();
 		}
 	}
	public boolean isActiveSign(Block signblock) {
		if (this.hasActiveSign()) {
			if (signblock == null) {
				return false;
			} else {
				return signblock.getLocation().equals(this.activeSign);
			}
		} else {
			return false;
		}
	}
	public void updateActiveSign() {
		Block signblock = this.getSignBlock();
		if (signblock != null) {
			SignActionEvent info = new SignActionEvent(signblock, this);
			CustomEvents.onSign(info, ActionType.MEMBER_MOVE);
			if (!this.isActiveSign(signblock)) {
				this.setActiveSign(signblock);
				CustomEvents.onSign(info, ActionType.MEMBER_ENTER);
			}
			if (!this.getGroup().getSignActive(signblock)) {
				this.getGroup().setSignActive(signblock, true);
				CustomEvents.onSign(info, ActionType.GROUP_ENTER);
			}
		} else if (this.hasActiveSign()) {
			signblock = this.getActiveSign();
			CustomEvents.onSign(new SignActionEvent(ActionType.MEMBER_LEAVE, signblock, this));
			if (this == this.getGroup().tail()) {
				this.getGroup().setSignActive(signblock, false);
				CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_LEAVE, signblock, this));
			}
			this.setActiveSign(null);
		}
	}
		
	/*
	 * Actions
	 */
	public void push(Entity entity) {
		float yaw = this.getYaw();
		while (yaw < 0) yaw += 360;
		while (yaw >= 360) yaw -= 360;
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
}