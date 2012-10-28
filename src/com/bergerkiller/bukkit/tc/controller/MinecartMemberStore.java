package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.MemberSpawnEvent;
import com.bergerkiller.bukkit.tc.events.MemberConvertEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;

import net.minecraft.server.ChunkCoordinates;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.EntityTrackerEntry;
import net.minecraft.server.World;
import net.minecraft.server.WorldServer;

public class MinecartMemberStore extends NativeMinecartMember {

	public MinecartMemberStore(World world, double d0, double d1, double d2, int i) {
		super(world, d0, d1, d2, i);
	}

	/**
	 * Converts all Minecarts on all enabled worlds into Minecart Members
	 */
	public static void convertAll() {
		List<EntityMinecart> minecarts = new ArrayList<EntityMinecart>();
		for (WorldServer world : WorldUtil.getWorlds()) {
			if (TrainCarts.isWorldDisabled(world.getWorld())) {
				continue;
			}
			for (net.minecraft.server.Entity entity : WorldUtil.getEntities(world)) {
				if (entity.getClass().equals(EntityMinecart.class)) {
					minecarts.add((EntityMinecart) entity);
				}
			}
			// Convert
			for (EntityMinecart em : minecarts) {
				convert(em);
			}
			minecarts.clear();
		}
	}

	/**
	 * Creates a Minecart Member from the source minecart specified<br>
	 * Returns null if no member could be created for this Source
	 * 
	 * @param source minecart to convert
	 * @return Minecart Member conversion
	 */
	public static MinecartMember convert(EntityMinecart source) {
		if (source.dead) {
			return null;
		}
		if (source instanceof MinecartMember) {
			return (MinecartMember) source;
		}
		if (!source.getClass().equals(EntityMinecart.class)) {
			return null;
		}
		if (TrainCarts.isWorldDisabled(source.world.getWorld())) {
			return null;
		}
		MinecartMember with = new MinecartMember(source);
		//unloaded?
		with.unloaded = OfflineGroupManager.containsMinecart(with.uniqueId);

		// Call the conversion event
		MemberConvertEvent event = MemberConvertEvent.call(source, with);
		if (event.isCancelled()) {
			return null;
		}
		with = event.getMember();
		// swap the tracker
		EntityTrackerEntry entry = WorldUtil.getTrackerEntry(source);
		// Create MM tracker using old as base
		if (entry == null) {
			entry = new MinecartMemberTrackerEntry(with);
		} else if (!(entry instanceof MinecartMemberTrackerEntry)) {
			entry = new MinecartMemberTrackerEntry(entry);
		}
		with.tracker = (MinecartMemberTrackerEntry) entry;
		// And set the entity
		EntityUtil.setEntity(source, with, entry);
		return with;
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
				if (minecart.world.chunkProvider.isChunkLoaded(minecart.ah, minecart.aj)) {
					minecart.world.chunkProvider.getChunkAt(minecart.ah, minecart.aj).b(minecart);
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * Tries to find a minecart member by UUID
	 * 
	 * @param uuid of the minecart
	 * @return Minecart Member, or null if not found
	 */
	public static MinecartMember getFromUID(UUID uuid) {
		for (World world : WorldUtil.getWorlds()) {
			MinecartMember member = CommonUtil.tryCast(EntityUtil.getEntity(world, uuid), MinecartMember.class);
			if (member != null && !member.isUnloaded()) {
				return member;
			}
		}
		return null;
	}

	public static MinecartMember get(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof UUID) {
			return getFromUID((UUID) o);
		}
		if (o instanceof Minecart) {
			o = EntityUtil.getNative((Minecart) o);
		}
		if (o instanceof MinecartMember) {
			MinecartMember member = (MinecartMember) o;
			if (!member.isUnloaded()) {
				return member;
			}
		}
		return null;
	}
	public static MinecartMember[] getAll(Object... objects) {
		MinecartMember[] rval = new MinecartMember[objects.length];
		for (int i = 0; i < rval.length; i++) {
			rval[i] = get(objects[i]);
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
		mm = MemberSpawnEvent.call(mm).getMember();
		mm.tracker = new MinecartMemberTrackerEntry(mm);
		WorldUtil.setTrackerEntry(mm, mm.tracker);
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
						if (mm.getLiveBlockX() != coord.x) continue;
						if (mm.getLiveBlockY() != coord.y) continue;
						if (mm.getLiveBlockZ() != coord.z) continue;
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
}
