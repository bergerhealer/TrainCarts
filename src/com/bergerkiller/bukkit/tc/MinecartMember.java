package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.PoweredMinecart;
import org.bukkit.entity.Player;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

public class MinecartMember {
	public MinecartMember(Minecart m) {
		this.m = m;
	}
	private Minecart m;
	public double forceremainder = 0;
	public double inputForceFactor = 1; //used for breaks
	private double forceFactor = 1;
	private float yaw = 0;

	public Minecart getMinecart() {
		return this.m;
	}
	
	public void stop() {
		forceremainder = 0;
		m.setVelocity(new Vector());
	}
	public static boolean isDerailed(Minecart m) {
		return getRails(m) == null;
	}
	
	public static Block getRailsBlock(Minecart m) {
		Block b = m.getLocation().getBlock();
		if (b.getState().getData() instanceof Rails) {
			return b;
		} else {
			b = b.getRelative(BlockFace.DOWN);
			if (b.getState().getData() instanceof Rails) {
				return b;
			} else {
				return null;
			}
		}
	}
	public static Rails getRails(Minecart m) {
		Block b = getRailsBlock(m);
		if (b == null) return null;
		return (Rails) b.getState().getData();
	}

	public static boolean isSharingRails(Minecart m1, Minecart m2) {
		int dx = Math.abs(m1.getLocation().getBlockX() - m2.getLocation().getBlockX());
		int dz = Math.abs(m1.getLocation().getBlockZ() - m2.getLocation().getBlockZ());
		int stepcount = dx + dz;
		Block bm1 = getRailsBlock(m1);
		Block bm2 = getRailsBlock(m2);	
		for (Block b : getAdjacentRails(bm1, stepcount)) {
			if (b == bm2) return true;
		}
		return false;
	}
	private static boolean addAdjacentRails(Block r, Collection<Block> array) {
		if (r == null || r.getType() == Material.AIR) return false;
		if (!(r.getState().getData() instanceof Rails)) return false;
		if (!array.add(r)) return false;
		final BlockFace[] stardirect = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
		for (BlockFace dir : stardirect) {
			Block b = r.getRelative(dir);
			if (b.getState().getData() instanceof Rails) {
				BlockFace direction = ((Rails) b.getState().getData()).getDirection();
				if (direction == BlockFace.NORTH || direction == BlockFace.NORTH_EAST || direction == BlockFace.NORTH_WEST) {
					if (dir == BlockFace.NORTH || dir == BlockFace.SOUTH) array.add(b);
				}
				if (direction == BlockFace.EAST || direction == BlockFace.NORTH_EAST || direction == BlockFace.SOUTH_EAST) {
					if (dir == BlockFace.EAST || dir == BlockFace.WEST) array.add(b);
				} 
				if (direction == BlockFace.SOUTH || direction == BlockFace.SOUTH_EAST || direction == BlockFace.SOUTH_WEST) {
					if (dir == BlockFace.NORTH || dir == BlockFace.SOUTH) array.add(b);
				}
				if (direction == BlockFace.WEST || direction == BlockFace.NORTH_WEST || direction == BlockFace.SOUTH_WEST) {
					if (dir == BlockFace.EAST || dir == BlockFace.WEST) array.add(b);
				}
			}
		}
		return true;
	}
	public static Block[] getAdjacentRails(Block r, int stepcount) {
		if (r == null || stepcount <= 0) return new Block[0];
		HashSet<Block> rval = new HashSet<Block>();
		boolean added = false;
		if (addAdjacentRails(r, rval)) added = true;
		if (addAdjacentRails(r.getRelative(BlockFace.DOWN), rval)) added = true;
		if (addAdjacentRails(r.getRelative(BlockFace.UP), rval)) added = true;		
		if (added) {
			for (Block b : rval.toArray(new Block[0])) {
				if (rval.add(b)) {
					for (Block b2 : getAdjacentRails(b, stepcount - 1)) {
						rval.add(b2);
					}
				}
			}
		}
		return rval.toArray(new Block[0]);	
	}
		
	public boolean isDerailed() {
		return isDerailed(this.m);
	}
	public Rails getRails() {
		return getRails(this.m);
	}
	
	public static float getLookAtYaw(MinecartMember loc, MinecartMember lookat) {
		return getLookAtYaw(loc.m, lookat.m);
	}
	public static float getLookAtYaw(Entity loc, Entity lookat) {
		return getLookAtYaw(loc.getLocation(), lookat.getLocation());
	}
	public static float getLookAtYaw(Location loc, Location lookat) {
        // Values of change in distance (make it relative)
        double dx = lookat.getX() - loc.getX();
        double dz = lookat.getZ() - loc.getZ();

        double yaw = 0;
        // Set yaw
        if (dx != 0) {
            // Set yaw start value based on dx
            if (dx < 0) {
            	yaw = 1.5 * Math.PI;
            } else {
                yaw = 0.5 * Math.PI;
            }
            yaw -= Math.atan(dz / dx);
        } else if (dz < 0) {
        	yaw = Math.PI;
        }
        return (float) (-yaw * 180 / Math.PI);
	}
	public static double getForce(Vector velocity, float yaw, float pitch) {
		double ryaw = -yaw / 180 * Math.PI;
		double xzforce = Math.cos(ryaw) * velocity.getZ() + Math.sin(ryaw) * velocity.getX();
		//TODO: calculate force using PITCH
		return xzforce;
	}
	public static Vector getVelocity(double force, float yaw, float pitch, double velY) {
		double ryaw = -yaw / 180 * Math.PI;
		Vector v = new Vector(0, velY, 0);
		//TODO: Y velocity from pitch
		v.setX(Math.sin(ryaw) * force);
		v.setZ(Math.cos(ryaw) * force);
		return v;
	}
	
	public double getFullForwardForce() {
		double force = getForce(m.getVelocity(), getYaw(), 0);
        force /= forceFactor;
        force *= inputForceFactor;
        force += forceremainder;
        return force;
	}
	public double getForwardForce() {
        double force = getFullForwardForce();
        if (force < 0) force = 0;
        //set limit and store the part that is removed (force remainder)
        double maxspeed = TrainCarts.maxCartSpeed;
    	forceremainder = force - maxspeed;
    	if (forceremainder < 0) forceremainder = 0;
    	if (force > maxspeed) force = maxspeed;
       	return force;
	}
	public void setForwardForce(double force) {
		force *= forceFactor;
		double ryaw = -getYaw() / 180 * Math.PI;
		Vector v = new Vector(0, m.getVelocity().getY(), 0);
		v.setX(Math.sin(ryaw) * force);
		v.setZ(Math.cos(ryaw) * force);
		m.setVelocity(v);
	}
	
	public void addForceFactor(double forcer, double factor) {
		this.forceFactor = 1 + (forcer * factor);
	}
	public boolean hasForceFactor() {
		return Math.abs(this.forceFactor - 1) > 0.1;
	}
	
	public Location getLocation() {
		Location loc = m.getLocation();
		loc.setYaw(getYaw());
		return loc;
	}
	public double getX() {
		return m.getLocation().getX();
	}
	public double getY() {
		return m.getLocation().getY();
	}
	public double getZ() {
		return m.getLocation().getZ();
	}
	public double getSubX() {
		double x = getX() + 0.5;
		return x - (int) x;
	}	
	public double getSubZ() {
		double z = getZ() + 0.5;
		return z - (int) z;
	}
	
	public double distanceXZ(MinecartMember m) {
		double d = Math.sqrt(Math.pow(this.getX() - m.getX(), 2) + Math.pow(this.getZ() - m.getZ(), 2));
		return d;
	}
	public double distance(MinecartMember m) {
		return m.getLocation().distance(m.m.getLocation());
	}
	
	/*
	 * Pitch functions
	 */
	public float getPitch() {
		return m.getLocation().getPitch();
	}
	public float getPitchDifference(MinecartMember comparer) {
		return getPitchDifference(comparer.getPitch());
	}
	public float getPitchDifference(float pitchcomparer) {
		return getAngleDifference(getPitch(), pitchcomparer);
	}
	
	
	/*
	 * Yaw functions
	 */
	public float getYaw() {
		return this.yaw;
	}
	public static float getAngleDifference(float angle1, float angle2) {
		float difference = angle1 - angle2;
        while (difference < -180) difference += 360;
        while (difference > 180) difference -= 360;
        return Math.abs(difference);
	}
	public float getYawDifference(float yawcomparer) {
		return getAngleDifference(this.yaw, yawcomparer);
	}	
	public float setYaw(float yawcomparer) {
		yaw = 0;
		Vector v = m.getVelocity();
		double x = getSubX();
		double z = getSubZ();
		if (x == 0 && Math.abs(v.getX()) < 0.001) {
			//cart is driving along the x-axis
			yaw = 0;
		} else if (z == 0 && Math.abs(v.getZ()) < 0.001) {
			//cart is driving along the z-axis
			yaw = 90;
		} else if (x == 0) {
			//cart is driving along the x-axis (standing still)
			yaw = 0;
		} else if (z == 0) {
			//cart is driving along the z-axis (standing still)
			yaw = 90;
		} else {
			//cart is driving in a corner
			Rails rails = getRails();
			if (rails == null) {
				yaw = 0;
			} else {
				BlockFace d = rails.getDirection();
				if (d == BlockFace.WEST) {
					yaw = 0;
				} else if (d == BlockFace.SOUTH) {
					yaw = 90;
				} else if (d == BlockFace.SOUTH_WEST) {
					yaw = 45;
				} else if (d == BlockFace.NORTH_WEST) {
					yaw = 135;
				} else if (d == BlockFace.NORTH_EAST) {
					yaw = 45;	
				} else if (d == BlockFace.SOUTH_EAST) {
					yaw = 135;
				}
			}
		}
		if (getYawDifference(yawcomparer) > 90) yaw += 180;
		return yaw;
	}
	public float setYawTo(MinecartMember head) {
		return setYaw(getLookAtYaw(this, head));
	}
	public float setYawFrom(MinecartMember tail) {
		return setYaw(getLookAtYaw(tail, this));
	}

	public boolean isNear() {
		for (Player p : Bukkit.getServer().getOnlinePlayers()) {
			if (p.getLocation().distance(m.getLocation()) <= 3) return true;
		}
		return false;
	}
	public boolean isMoving() {
		Vector v = m.getVelocity();
		if (v.getX() > 0.001) return true;
		if (v.getX() < -0.001) return true;
		if (v.getZ() > 0.001) return true;
		if (v.getZ() < -0.001) return true;
		return false;
	}
	public boolean isTurned() {
		float yaw = getYaw();
		if (yaw == 0) return false;
		if (yaw == 90) return false;
		if (yaw == 180) return false;
		if (yaw == 270) return false;
		return true;
	}

	public double getHeadingToDistance(MinecartMember to) {
	      Vector vel = this.m.getVelocity();
	      double veldistance = Math.sqrt(Math.pow(vel.getX(), 2) + Math.pow(vel.getZ(), 2));
	      double cartdistance = this.distanceXZ(to);
	      vel = vel.multiply(cartdistance / veldistance);
	      Location loc = this.m.getLocation().add(vel.getX(), 0, vel.getZ());
	      double distance = Math.sqrt(Math.pow(loc.getX() - to.getX(), 2) + Math.pow(loc.getZ() - to.getZ(), 2));
	      return distance;
    }
	public boolean isHeadingTo(MinecartMember to) {
		double d1 = this.getHeadingToDistance(to);
		double d2 = to.getHeadingToDistance(this);
		return d1 < d2;
	}
}