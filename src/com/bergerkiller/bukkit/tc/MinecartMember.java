package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.HashSet;

import net.minecraft.server.EntityMinecart;
import net.minecraft.server.World;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.tc.API.CoalUsedEvent;
import com.bergerkiller.bukkit.tc.API.GroupUpdateEvent;
import com.bergerkiller.bukkit.tc.API.UpdateStage;

public class MinecartMember extends NativeMinecartMember {
	
	private double forceFactor = 1;
	private float customYaw = 0;
	private MinecartGroup group;
	private VelocityTarget target = null;
	
	public MinecartMember(World world, double d0, double d1, double d2, int i) {
		super(world, d0, d1, d2, i);
		this.customYaw = this.yaw - 90;
	}
	
	/*
	 * Overridden Minecart functions
	 */
	@Override
	public void m_() {
		MinecartGroup g = this.getGroup();
		if (g != null) {
			if (g.tail() == this) {
				if (GroupUpdateEvent.call(g, UpdateStage.FIRST)) {
					for (MinecartMember m : g.getMembers()) {
						//Update targets
						if (m.target != null && m.target.update(m.getBukkitEntity())) {
							if (m.target.goalVelocity < 0.01) {
								g.stop();
							}
							m.target = null;
						}
						//General velocity update
						m.preUpdate();
					}
					if (GroupUpdateEvent.call(g, UpdateStage.BEFORE_GROUP)) {
						g.update();
						if (GroupUpdateEvent.call(g, UpdateStage.AFTER_GROUP)) {
							for (MinecartMember m : g.getMembers()) {
								m.postUpdate(m.forceFactor);
							}
							GroupUpdateEvent.call(g, UpdateStage.LAST);
						}
					}
				}
			}
		} else {
			super.m_();
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
	public static MinecartMember[] getAll(Entity... entities) {
		MinecartMember[] rval = new MinecartMember[entities.length];
		for (int i = 0; i < rval.length; i++) {
			rval[i] = get(entities[i]);
		}
		return rval;
	}
	public static MinecartMember get(Entity e) {
		if (!(e instanceof Minecart)) return null;
		EntityMinecart em = Util.getNative((Minecart) e);
		if (em instanceof MinecartMember) return (MinecartMember) em;
		return null;
	}
	public static MinecartMember get(Location loc, int type, MinecartGroup group) {
		MinecartMember mm = new MinecartMember(Util.getNative(loc.getWorld()), loc.getX(), loc.getY(), loc.getZ(), type);
		mm.yaw = loc.getYaw();
		mm.pitch = loc.getPitch();
		mm.group = group;
		mm.world.addEntity(mm);
		return mm;
	}
	
	public static MinecartMember getAt(Location at) {
		return getAt(at, 1);
	}
	public static MinecartMember getAt(Location at, double searchRadius) {
		for (Entity e : at.getBlock().getChunk().getEntities()) {
			if (e instanceof Minecart) {
				MinecartMember mm = get(e);
				if (mm != null) {
					if (mm.getLocation().distance(at) <= searchRadius) {
						return mm;
					}
				}
			}
		}
		return null;
	}
	public static MinecartMember get(Minecart m, MinecartGroup group) {
		//to prevent unneeded cart conversions
		EntityMinecart em = Util.getNative(m);
		if (em instanceof MinecartMember) {
			MinecartMember mm = (MinecartMember) em;
			mm.group = group;
			return mm;
		}
				
		//declare a new MinecartMember with the same characteristics as the previous EntityMinecart
		MinecartMember f = new MinecartMember(em.world, em.lastX, em.lastY, em.lastZ, em.type);
		f.group = group;
		Util.replaceMinecarts(em, f);
		
		replacedCarts.add(f);
		return f;
	}
	public static void undoReplacement(MinecartMember mm) {
		if (!mm.dead) {
			EntityMinecart em = new EntityMinecart(mm.world, mm.lastX, mm.lastY, mm.lastZ, mm.type);
			Util.replaceMinecarts(mm, em);
		}
		replacedCarts.remove(mm);
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
		this.world.removeEntity(this);
	}
	
	/*
	 * General getters and setters
	 */
 	public Minecart getMinecart() {
		return (Minecart) this.getBukkitEntity();
	}
 	public boolean grouped() {
 		return this.group != null;
 	}
 	public MinecartGroup getGroup() {
 		return this.group;
 	}
 	public void setGroup(MinecartGroup group) {
 		this.group = group;
 	}
	public MinecartMember[] getNeightbours() {
		if (this.getGroup() == null) return new MinecartMember[0];
		int index = this.getGroup().indexOf(this);
		if (index == -1) return new MinecartMember[0];
		if (index > 0) {
			if (index < this.getGroup().size() - 1) {
				return new MinecartMember[] {
						this.getGroup().getMember(index - 1), 
						this.getGroup().getMember(index + 1)};
			} else {
				return new MinecartMember[] {this.getGroup().getMember(index - 1)};
			}
		} else if (index < this.getGroup().size() - 1) {
			return new MinecartMember[] {this.getGroup().getMember(index + 1)};
		} else {
			return new MinecartMember[0];
		}
	}
	public Block getRailsBlock() {
		if (this.group != null) {
			if (super.isDerailed()) return null;
		}
		return Util.getRailsBlock(this.getMinecart());
	}
	public Rails getRails() {
		return Util.getRails(this.getRailsBlock());
	}
	public Block getSignBlock() {
		Block b = this.getRailsBlock();
		if (b == null) return null;
		b = b.getRelative(0, -2, 0);
		if (Util.isSign(b)) return b;
		return null;
	}
	public Sign getSign() {
		return Util.getSign(getSignBlock());
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
		double force = Math.sqrt(motX * motX + motZ * motZ);
		if (getForwardForce() < 0) force = -force;
		return force;
	}
	public double getForwardForce() {
		double ryaw = -this.getYaw() / 180 * Math.PI;
        return Math.cos(ryaw) * this.motZ + Math.sin(ryaw) * this.motX;
	}
	public void setForwardForce(double force) {
		if (isTurned()) {
			double l = Math.sqrt(motX * motX + motZ * motZ);
			if (l > 0.001) {
				double factor = force / l;
				this.motX *= factor;
				this.motZ *= factor;
				return;
			}
		}
		double ryaw = -getYaw() / 180 * Math.PI;
		this.motX = Math.sin(ryaw) * force;
		this.motZ = Math.cos(ryaw) * force;
	}
	public void addForceFactor(double forcer, double factor) {
		this.forceFactor = 1 + (forcer * factor);
	}
		
	/*
	 * Velocity targeting
	 */
	public boolean hasTarget() {
		return this.target != null;
	}
	public void setTarget(Location target, double targetVelocity) {
		if (target == null) {
			this.target = null;
		} else {
			this.target = new VelocityTarget(target, targetVelocity, this.getBukkitEntity());
		}
	}
	public void setTarget(Location target, double fromVelocity, double toVelocity) {
		if (target == null) {
			this.target = null;
		} else {
			this.target = new VelocityTarget(target, fromVelocity, toVelocity, this.getBukkitEntity());
		}
	}
	public TrackMap makeTrackMap(int size) {
		return new TrackMap(Util.getRailsBlock(this.getLocation()), this.getDirection(), size);
	}
	
	/*
	 * Location functions
	 */
	public double getSubX() {
		double x = getX() + 0.5;
		return x - (int) x;
	}	
	public double getSubZ() {
		double z = getZ() + 0.5;
		return z - (int) z;
	}	
	public double distanceXZ(MinecartMember m) {
		return Util.distance(this.getX(), this.getZ(), m.getX(), m.getZ());
	}
	public double distance(MinecartMember m) {
		return Util.distance(this.getX(), this.getY(), this.getZ(), m.getX(), m.getY(), m.getZ());
	}
	public void addNearChunks(ArrayList<SimpleChunk> rval, boolean addloaded, boolean addunloaded) {
		int chunkX = Util.toChunk(this.getX());
		int chunkZ = Util.toChunk(this.getZ());
		Util.addNearChunks(rval, this.getWorld(), chunkX, chunkZ, 2, addloaded, addunloaded);
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
		customYaw = 0;
		double x = getSubX();
		double z = getSubZ();
		if (x == 0 && Math.abs(motX) < 0.001) {
			//cart is driving along the x-axis
			customYaw = 0;
		} else if (z == 0 && Math.abs(motZ) < 0.001) {
			//cart is driving along the z-axis
			customYaw = 90;
		} else {
			//try to get the yaw from the rails
			customYaw = Util.getRailsYaw(getRails());
		}
		//Fine tuning
		if (getYawDifference(yawcomparer) > 90) customYaw -= 180;
		return customYaw;
	}
	public float setYawTo(MinecartMember head) {
		return setYaw(Util.getLookAtYaw(this, head));
	}
	public float setYawFrom(MinecartMember tail) {
		return setYaw(Util.getLookAtYaw(tail, this));
	}
	public BlockFace getDirection() {
		int yaw = (int) this.customYaw;
		while (yaw < 0) yaw += 360;
		while (yaw >= 360) yaw -= 360;
		switch (yaw) {
		case 0 : return BlockFace.WEST;
		case 45: return BlockFace.NORTH_WEST;
		case 90 : return BlockFace.NORTH;
		case 135 : return BlockFace.NORTH_EAST;
		case 180 : return BlockFace.EAST;
		case 225 : return BlockFace.SOUTH_EAST;
		case 270 : return BlockFace.SOUTH;
		case 315 : return BlockFace.SOUTH_WEST;
		}
		return BlockFace.NORTH;
	}
	public boolean isMovingTo(Location to) {
		BlockFace dir = getDirection();
		Location next = getLocation().add(dir.getModX(), 0, dir.getModZ());
		double d1 = to.distance(getLocation());
		double d2 = next.distance(getLocation());
		return d2 < d1;
	}

	/*
	 * States
	 */
	public static boolean isMember(Minecart m) {
		return Util.getNative(m) instanceof MinecartMember;
	}
 	public static boolean validate(Minecart m) {
 		return !(m.isDead() || (TrainCarts.removeDerailedCarts && isDerailed(m)));
 	}
 	public static boolean validate(MinecartMember mm) {
 		return !(mm.dead || (TrainCarts.removeDerailedCarts && mm.isDerailed()));
 	}		
	public boolean isMoving() {
		if (motX > 0.001) return true;
		if (motX < -0.001) return true;
		if (motZ > 0.001) return true;
		if (motZ < -0.001) return true;
		return false;
	}
	public boolean isTurned() {
		return getSubX() != 0 && getSubZ() != 0;
	}	
	public static boolean isDerailed(Minecart m) {
		if (m == null) return true;
		MinecartMember mm = get(m);
		if (mm == null) {
			return Util.getRailsBlock(m) == null;
		} else {
			return isDerailed(mm);
		}
	}
	public static boolean isDerailed(MinecartMember mm) {
		if (mm == null) return true;
		return mm.getRailsBlock() == null;
	}	
	public boolean isDerailed() {
		return isDerailed(this);
	}
}