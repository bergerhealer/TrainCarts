package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.server.EntityMinecart;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

public class Util {
	private static Logger logger = Logger.getLogger("Minecraft");
	public static void log(Level level, String message) {
		logger.log(level, "[TrainCarts] " + message);
	}
	
	public static net.minecraft.server.EntityMinecart getNative(Minecart m) {
		return (net.minecraft.server.EntityMinecart) getNative((Entity) m);
	}
	public static net.minecraft.server.Entity getNative(Entity e) {
		return ((CraftEntity) e).getHandle();
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
		float difference = angle1 - angle2;
        while (difference < -180) difference += 360;
        while (difference > 180) difference -= 360;
        return Math.abs(difference);
	}
	public static float getLookAtYaw(MinecartMember loc, MinecartMember lookat) {
		return getLookAtYaw(loc.getMinecart(), lookat.getMinecart());
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
    public static double round(double Rval, int Rpl) {
  	  double p = Math.pow(10,Rpl);
  	  return Math.round(Rval * p) / p;
    }
    
	public static Block getRailsBlock(Minecart m) {
		return getRailsBlock(m.getLocation());
	}
	public static Block getRailsBlock(Location from) {
		Block b = from.getBlock();
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
	public static Rails getRails(Block railsblock) {
		if (railsblock == null) return null;
		if (!(railsblock.getState().getData() instanceof Rails)) return null;
		return (Rails) railsblock.getState().getData();
	}
	public static Rails getRails(Minecart m) {	
		return getRails(m.getLocation());
	}
	public static Rails getRails(Location loc) {
		return getRails(getRailsBlock(loc));
	}
	
	
	public static boolean isInverted(double value1, double value2) {
		if (value1 > 0 && value2 < 0) return true;
		if (value1 < 0 && value2 > 0) return true;
		return false;
	}
	
	public static boolean isSharingRails(Minecart m1, Minecart m2) {
		int dx = Math.abs(m1.getLocation().getBlockX() - m2.getLocation().getBlockX());
		int dz = Math.abs(m1.getLocation().getBlockZ() - m2.getLocation().getBlockZ());
		int stepcount = dx + dz;
		Block bm1 = getRailsBlock(m1);
		Block bm2 = getRailsBlock(m2);	
		if (bm1 == null || bm2 == null) return false;
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

	public static Minecart getMinecart(World w, UUID UID) {
		Entity e = getEntity(w, UID);
		if (e != null && e instanceof Minecart) return (Minecart) e;
		return null;
	}
	public static Entity getEntity(World w, UUID UID) {
		for (Entity e : w.getEntities()) {
			if (e.getUniqueId().equals(UID)) return e;
		}
		return null;
	}

	public static void broadcast(String msg) {
		Bukkit.getServer().broadcastMessage(msg);
	}
	public static void heartbeat() {
		broadcast("HEARTBEAT: " + System.currentTimeMillis());
	}
	
	public static int toChunk(double loc) {
		return ((int) loc) >> 4;
	}
	
	public static float getRailsYaw(Rails rails) {
		if (rails != null) {
			switch (rails.getDirection()) {
			case WEST : return 0;
			case SOUTH : return 90;
			case SOUTH_WEST : return 45;
			case NORTH_WEST : return 135;
			case NORTH_EAST : return 45;
			case SOUTH_EAST : return 135;
			}
		}
		return 0;
	}

	public static Vector getDirection(float yaw, float pitch) {
		return new Location(null, 0, 0, 0, yaw, pitch).getDirection();
	}
		
	public static float getMinecartYaw(Minecart minecart) {
		return getMinecartYaw(getNative(minecart));
	}
	public static float getMinecartYaw(EntityMinecart minecart) {
		if (minecart instanceof MinecartMember) return ((MinecartMember) minecart).getYaw();
		return minecart.yaw + 90;
	}
	public static boolean pushAway(EntityMinecart vehicle, Entity topush) {
		if (topush instanceof Player) {
			if (!TrainCarts.pushAwayPlayers) return false;
		} else if (topush instanceof Creature) {
			if (!TrainCarts.pushAwayMobs) return false;
		} else {
			if (!TrainCarts.pushAwayMisc) return false;
		}
		//===============================Entity check end======================
		double speed = length(vehicle.motX, vehicle.motY, vehicle.motZ);
		if (speed > TrainCarts.pushAwayAtVelocity) {
			//push that bastard away!
			float yaw = getMinecartYaw(vehicle);
			//which do we choose, -90 or 90?
			float lookat = Util.getLookAtYaw(vehicle.getBukkitEntity(), topush);
			if (lookat < 0) {
				yaw += 90;
			} else {
				yaw -= 90;
			}
			//push the obstacle awaayyy :d
			Vector vel = Util.getDirection(yaw, 0).multiply(TrainCarts.pushAwayForce);
			topush.setVelocity(vel);
			return true;
		} else {
			return false;
		}
	}
	public static boolean pushAway(Minecart vehicle, Entity topush) {
		return pushAway(getNative(vehicle), topush);
	}
	
	
	public static boolean getChunkSafe(Entity e) {
		return getChunkSafe(e.getLocation());
	}
	public static boolean getChunkSafe(Location loc) {
		return getChunkSafe(loc.getWorld(), toChunk(loc.getX()), toChunk(loc.getZ()));
	}
	public static boolean getChunkSafe(net.minecraft.server.Entity e) {
		return getChunkSafe(e.world.getWorld(), toChunk(e.lastX), toChunk(e.lastZ));
	}
	public static boolean getChunkSafe(World w, int chunkX, int chunkZ) {
		if (!w.isChunkLoaded(chunkX, chunkZ)) return false;
		if (!w.isChunkLoaded(chunkX + 1, chunkZ)) return false;
		if (!w.isChunkLoaded(chunkX - 1, chunkZ)) return false;
		if (!w.isChunkLoaded(chunkX, chunkZ + 1)) return false;
		if (!w.isChunkLoaded(chunkX, chunkZ - 1)) return false;
		return true;
	}
	
	public static void addNearChunks(ArrayList<SimpleChunk> rval, World w, int chunkX, int chunkZ, int radius, boolean addloaded, boolean addunloaded) {
		for (int dx = -radius; dx <= radius * 2;dx++) {
			for (int dz = -radius; dz <= radius * 2;dz++) {
				SimpleChunk c = new SimpleChunk();
				c.world = w;
				c.chunkX = chunkX + dx;
				c.chunkZ = chunkZ + dz;
				if ((addloaded && c.isLoaded()) || (addunloaded && !c.isLoaded())) {
					rval.add(c);
				}
			}
		}
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


}
