package com.bergerkiller.bukkit.tc.controller;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.ClassTemplate;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.events.MinecartSwapEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

import net.minecraft.server.ChunkCoordinates;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.World;

public class MinecartMemberStore extends NativeMinecartMember {

	public MinecartMemberStore(World world, double d0, double d1, double d2, int i) {
		super(world, d0, d1, d2, i);
	}

	protected static Set<MinecartMember> replacedCarts = new HashSet<MinecartMember>();
	private static boolean denyConversion = false;
	private static final ClassTemplate<EntityMinecart> MINECARTTEMPLATE = ClassTemplate.create(EntityMinecart.class);

	public static boolean canConvert(Entity entity) {
		return !denyConversion && get(entity) == null;
	}

	/**
	 * Checks if the minecart is added to the world. If not, returns false and removes the minecart
	 * 
	 * @param minecart to check (only checks if it is an EntityMinecart)
	 * @return True if valid, False if not
	 */
	public static boolean validateMinecart(net.minecraft.server.Entity minecart) {
		if (minecart instanceof EntityMinecart) {
			//could be a bugged minecart.
			//verify that it is not bugged
			if (!minecart.world.entityList.contains(minecart)) {
				//bugged minecart - kill it!
				minecart.dead = true;
				minecart.world.removeEntity(minecart);
				minecart.ah = MathUtil.locToChunk(minecart.locX);
				minecart.ai = MathUtil.locToChunk(minecart.locY);
				minecart.aj = MathUtil.locToChunk(minecart.locZ);
				if (minecart.world.chunkProvider.isChunkLoaded(minecart.ah, minecart.aj)) {
					minecart.world.chunkProvider.getChunkAt(minecart.ah, minecart.aj).b(minecart);
				}
				return false;
			}
		}
		return true;
	}

	public static void replaceMinecarts(EntityMinecart toreplace, EntityMinecart with) {
		//transfer variables, excluding the id. (it has to be a new ID)
		int id = with.id;
		MINECARTTEMPLATE.transfer(toreplace, with);
		with.id = id;

		// preserve the Bukkit entity, simply swap the contents
		((CraftEntity) with.getBukkitEntity()).setHandle(with);

		// swap a possible passenger
		if (toreplace.passenger != null) {
			toreplace.passenger.setPassengerOf(with);
		}

		//force removal in chunk
		with.dead = false;
		toreplace.dead = true;
		toreplace.ag = true;

		// swap the entity
		MinecartSwapEvent.call(toreplace, with);
		toreplace.world.removeEntity(toreplace);

		// swap the tracker
		if (with instanceof MinecartMember) {
			// Create MM tracker
			((MinecartMember) with).getTracker();
		} else {
			// Create default tracker
			WorldUtil.getTracker(with.world).trackedEntities.d(with.id);
		}
		with.world.addEntity(with);
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
		replaceMinecarts(em, mm);
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
		mm.getTracker();
		mm.world.addEntity(mm);
		return mm;
	}

	public static MinecartMember getAt(Block railblock) {
		if (railblock == null) return null;
		return getAt(WorldUtil.getNative(railblock.getWorld()), BlockUtil.getCoordinates(railblock));
	}

	public static MinecartMember getAt(org.bukkit.World world, ChunkCoordinates coord) {
		return getAt(WorldUtil.getNative(world), coord);
	}

	@SuppressWarnings("rawtypes")
	public static MinecartMember getAt(World world, ChunkCoordinates coord) {
		net.minecraft.server.Chunk chunk = WorldUtil.getChunk(world, coord.x >> 4, coord.z >> 4);
		if (chunk != null) {
			MinecartMember mm;
			MinecartMember result = null;
			// find member in chunk
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
			if (result == null) {
				// find member in all groups
				for (MinecartGroup group : MinecartGroupStore.getGroupsUnsafe()) {
					mm = group.getAt(coord);
					if (mm == null) continue;
					result = mm;
					if (result.isHeadingTo(coord)) return result;
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
			replaceMinecarts(mm, em);
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
