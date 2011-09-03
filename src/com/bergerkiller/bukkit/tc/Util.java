package com.bergerkiller.bukkit.tc;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public class Util {
	private static Logger logger = Logger.getLogger("Minecraft");
	public static void log(Level level, String message) {
		logger.log(level, "[TrainCarts] " + message);
	}
	public static void broadcast(Vector vec) {
		broadcast("VECTOR: [X=" + round(vec.getX(), 3) + " | Y=" + round(vec.getY(), 3) + " | Z=" + round(vec.getZ(), 3) + "]");
	}
	public static void broadcast(String msg) {
		Bukkit.getServer().broadcastMessage(msg);
	}
	public static void heartbeat() {
		broadcast("HEARTBEAT: " + System.currentTimeMillis());
	}

	public static double length(double... values) {
		double rval = 0;
		for (double value : values) {
			rval += value * value;
		}
		return Math.sqrt(rval);
	}
	public static double distance(double x1, double y1, double x2, double y2) {
		return length(x1 - x2, y1 - y2);
	}
	public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
		return length(x1 - x2, y1 - y2, z1 - z2);
	}
	public static float getAngleDifference(float angle1, float angle2) {
        return Math.abs(normalAngle(angle1 - angle2));
	}
	public static float normalAngle(float angle) {
        while (angle <= -180) angle += 360;
        while (angle > 180) angle -= 360;
        return angle;
	}
	public static float getLookAtYaw(MinecartMember loc, MinecartMember lookat) {
		return getLookAtYaw(loc.getMinecart(), lookat.getMinecart());
	}
	public static float getLookAtYaw(Entity loc, Entity lookat) {
		return getLookAtYaw(loc.getLocation(), lookat.getLocation());
	}
	public static float getLookAtYaw(Block loc, Block lookat) {
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
        return (float) (-yaw * 180 / Math.PI - 90);
	}
	public static Location move(Location loc, Vector offset) {
        // Convert rotation to radians
        float ryaw = -loc.getYaw() / 180f * (float) Math.PI;
        float rpitch = loc.getPitch() / 180f * (float) Math.PI;

        //Conversions found by (a lot of) testing
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        z -= offset.getX() * Math.sin(ryaw);
        z += offset.getY() * Math.cos(ryaw) * Math.sin(rpitch);
        z += offset.getZ() * Math.cos(ryaw) * Math.cos(rpitch);
        x += offset.getX() * Math.cos(ryaw);
        x += offset.getY() * Math.sin(rpitch) * Math.sin(ryaw);
        x += offset.getZ() * Math.sin(ryaw) * Math.cos(rpitch);
        y += offset.getY() * Math.cos(rpitch);
        y -= offset.getZ() * Math.sin(rpitch);
        return new Location(loc.getWorld(), x, y, z, loc.getYaw(), loc.getPitch());
    }
    public static double round(double Rval, int Rpl) {
  	  double p = Math.pow(10, Rpl);
  	  return Math.round(Rval * p) / p;
    }
    public static double fixNaN(double value) {
    	if (Double.isNaN(value)) return 0;
    	return value;
    }

    /*
     * Stages the value between the two points using a stage from 0 to 1
     */
    public static double stage(double d1, double d2, double stage) {
    	if (Double.isNaN(stage)) return d2;
    	if (stage < 0) stage = 0;
    	if (stage > 1) stage = 1;
    	return d1 * (1 - stage) + d2 * stage;
    }
    public static Vector stage(Vector vec1, Vector vec2, double stage) {
    	Vector newvec = new Vector();
    	newvec.setX(stage(vec1.getX(), vec2.getX(), stage));
    	newvec.setY(stage(vec1.getY(), vec2.getY(), stage));
    	newvec.setZ(stage(vec1.getZ(), vec2.getZ(), stage));
    	return newvec;
    }
    public static Location stage(Location loc1, Location loc2, double stage) {
    	Location newloc = new Location(loc1.getWorld(), 0, 0, 0);
    	newloc.setX(stage(loc1.getX(), loc2.getX(), stage));
    	newloc.setY(stage(loc1.getY(), loc2.getY(), stage));
    	newloc.setZ(stage(loc1.getZ(), loc2.getZ(), stage));
    	newloc.setYaw((float) stage(loc1.getYaw(), loc2.getYaw(), stage));
    	newloc.setPitch((float) stage(loc1.getPitch(), loc2.getPitch(), stage));
    	return newloc;
    }
    	
	public static boolean isInverted(double value1, double value2) {
		return (value1 > 0 && value2 < 0) || (value1 < 0 && value2 > 0);
	}

	public static Vector getDirection(float yaw, float pitch) {
		return new Location(null, 0, 0, 0, yaw, pitch).getDirection();
	}
				
	public static boolean isHeadingTo(Location from, Location to, Vector velocity) {
		//standing still
		if (velocity.length() < 0.01) return false;
		if (from.distanceSquared(to) < 0.01) return true;
		//distance check
		double dbefore = from.distance(to);
		from = from.add(velocity.getX() * 0.000001, velocity.getY() * 0.000001, velocity.getZ() * 0.000001);
		double dafter = from.distance(to);
		return dafter < dbefore;
	}

}
