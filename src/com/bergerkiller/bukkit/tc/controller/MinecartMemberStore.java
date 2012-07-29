package com.bergerkiller.bukkit.tc.controller;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

import net.minecraft.server.ChunkCoordinates;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.World;

public class MinecartMemberStore extends NativeMinecartMember {

	public MinecartMemberStore(World world, double d0, double d1, double d2, int i) {
		super(world, d0, d1, d2, i);
	}

	private static Set<MinecartMember> replacedCarts = new HashSet<MinecartMember>();
	private static boolean denyConversion = false;
	public static boolean canConvert(Entity entity) {
		return !denyConversion && get(entity) == null;
	}

	private static EntityMinecart findByID(UUID uuid) {
		EntityMinecart e;
		for (World world : WorldUtil.getWorlds()) {
			for (Object o : world.entityList) {
				if (o instanceof EntityMinecart) {
					e = (EntityMinecart) o;
					if (e.uniqueId.equals(uuid)) {
						return e;
					}
				}
			}
		}
		return null;
	}
	public static MinecartMember get(Object o) {
		if (o == null) return null;
		if (o instanceof UUID) {
			o = findByID((UUID) o);
			if (o == null) return null;
		}
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
		if (o instanceof UUID) {
			o = findByID((UUID) o);
			if (o == null) return null;
		}
		if (o instanceof Minecart) {
			o = EntityUtil.getNative((Minecart) o);
		}
		if (o instanceof MinecartMember) {
			return (MinecartMember) o;
		}
		if (!o.getClass().equals(EntityMinecart.class)) {
			return null;
		}
		EntityMinecart em = (EntityMinecart) o;
		if (em.dead) return null; //prevent conversion of dead entities 
		//not found, conversion allowed?
		if (denyConversion) return null;
		//convert
		MinecartMember mm = new MinecartMember(em.world, em.lastX, em.lastY, em.lastZ, em.type);
		Util.replaceMinecarts(em, mm);
		return mm;
	}
	public static MinecartMember[] convertAll(Entity... entities) {
		MinecartMember[] rval = new MinecartMember[entities.length];
		for (int i = 0; i < rval.length; i++) {
			rval[i] = convert(entities[i]);
		}
		return rval;
	}

	public static MinecartMember getEditing(Player player) {
		return getEditing(player.getName());
	}
	public static MinecartMember getEditing(String playername) {
		CartProperties cp = CartProperties.getEditing(playername);
		return cp == null ? null : cp.getMember();
	}
	
	public static MinecartMember spawn(Location at, int type) {
		MinecartMember mm = new MinecartMember(WorldUtil.getNative(at.getWorld()), at.getX(), at.getY(), at.getZ(), type);
		mm.yaw = at.getYaw();
		mm.pitch = at.getPitch();
		mm.world.addEntity(mm);
		return mm;
	}

	public static MinecartMember getAt(Block railblock, boolean checkmoving) {
		if (railblock == null) return null;
		return getAt(WorldUtil.getNative(railblock.getWorld()), BlockUtil.getCoordinates(railblock), checkmoving);
	}

	private static boolean isHeadingTo(MinecartMember mm, ChunkCoordinates to) {
		if (mm == null) {
			return false;
		} else {
			BlockFace dir = mm.getDirection();
			if (dir.getModX() + mm.getBlockX() != to.x) return false;
			if (dir.getModZ() + mm.getBlockZ() != to.z) return false;
			return true;
		}
	}

	public static MinecartMember getAt(org.bukkit.World world, ChunkCoordinates coord, boolean checkmoving) {
		return getAt(WorldUtil.getNative(world), coord, checkmoving);
	}

	@SuppressWarnings("rawtypes")
	public static MinecartMember getAt(World world, ChunkCoordinates coord, boolean checkmoving) {
		net.minecraft.server.Chunk chunk = WorldUtil.getChunk(world, coord.x >> 4, coord.z >> 4);
		if (chunk != null) {
			MinecartMember mm;
			MinecartMember result = null;
			for (List list : chunk.entitySlices) {
				for (Object e : list) {
					if (e instanceof MinecartMember) {
						mm = (MinecartMember) e;
						if (mm.getBlockX() != coord.x) continue;
						if (mm.getBlockY() != coord.y) continue;
						if (mm.getBlockZ() != coord.z) continue;
						result = mm;
						if (result.isHeadingTo(coord)) return result;
					}
				}
			}
			if (result == null && checkmoving) {
				Block b = world.getWorld().getBlockAt(coord.x, coord.y, coord.z);
				int id = b.getTypeId();
				
				//get the two connected rails to check
				if (BlockUtil.isRails(id)) {
					BlockFace[] possible = FaceUtil.getFaces(BlockUtil.getRails(b).getDirection());
				    MinecartMember mm1 = getAt(Util.getRailsBlock(b.getRelative(possible[0])), false);
				    MinecartMember mm2 = getAt(Util.getRailsBlock(b.getRelative(possible[1])), false);
				    if (mm1 != null && mm2 != null && mm1.group == mm2.group) {
				    	Location loc = b.getLocation();
				    	return mm1.distance(loc) < mm2.distance(loc) ? mm1 : mm2;
				    } else if (isHeadingTo(mm1, coord)) {
				    	return mm1;
				    } else if (isHeadingTo(mm2, coord)) {
				    	return mm2;
				    } else {
				    	return null;
				    }
				} else if (Util.isPressurePlate(id)) {
					//check all directions
				    MinecartMember mm1 = getAt(Util.getRailsBlock(b.getRelative(BlockFace.NORTH)), false);
				    MinecartMember mm2 = getAt(Util.getRailsBlock(b.getRelative(BlockFace.SOUTH)), false);
				    MinecartMember mm3 = getAt(Util.getRailsBlock(b.getRelative(BlockFace.EAST)), false);
				    MinecartMember mm4 = getAt(Util.getRailsBlock(b.getRelative(BlockFace.WEST)), false);
				    if (mm1 != null && mm2 != null && mm1.group == mm2.group) {
				    	Location loc = b.getLocation();
				    	return mm1.distance(loc) < mm2.distance(loc) ? mm1 : mm2;
				    } else if (mm3 != null && mm4 != null && mm3.group == mm4.group) {
				    	Location loc = b.getLocation();
				    	return mm3.distance(loc) < mm4.distance(loc) ? mm3 : mm4;
				    } else if (isHeadingTo(mm1, coord)) {
				    	return mm1;
				    } else if (isHeadingTo(mm2, coord)) {
				    	return mm2;
				    } else if (isHeadingTo(mm3, coord)) {
				    	return mm3;
				    } else if (isHeadingTo(mm4, coord)) {
				    	return mm4;
				    } else {
				    	return null;
				    }
				}
			}
			return result;
		}
		return null;
	}

	public static MinecartMember getAt(Location at) {
		return getAt(at, null);
	}
	public static MinecartMember getAt(Location at, MinecartGroup in) {
		return getAt(at, in, 1);
	}
	public static MinecartMember getAt(Location at, MinecartGroup in, double searchRadius) {
		if (at == null) return null;
		searchRadius *= searchRadius;
		MinecartMember result = null;
		for (Entity e : at.getBlock().getChunk().getEntities()) {
			if (e instanceof Minecart) {
				MinecartMember mm = get(e);
				if (mm == null) continue;
				if (in != null && mm.getGroup() != in) continue;
				if (mm.distanceSquared(at) > searchRadius) continue;
				result = mm;
				if (mm.isHeadingTo(at)) return result;
			}
		}
		return result;
	}
	public static EntityMinecart undoReplacement(MinecartMember mm) {
		replacedCarts.remove(mm);
		if (!mm.dead) {
			denyConversion = true;
			mm.died = true;
			EntityMinecart em = new EntityMinecart(mm.world, mm.lastX, mm.lastY, mm.lastZ, mm.type);
			Util.replaceMinecarts(mm, em);
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
	public static void cleanUpDeadCarts() {
		Iterator<MinecartMember> iter = replacedCarts.iterator();
		MinecartMember mm;
		while (iter.hasNext()) {
			mm = iter.next();
			if (mm.dead) {
				iter.remove();
				mm.die();
			}
		}
	}
}
