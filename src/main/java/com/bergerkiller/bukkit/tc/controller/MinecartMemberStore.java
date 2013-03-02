package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.EntityTracker;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.MemberSpawnEvent;
import com.bergerkiller.bukkit.tc.events.MemberConvertEvent;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;

import net.minecraft.server.v1_4_R1.EntityMinecart;

public abstract class MinecartMemberStore extends NativeMinecartMember {

	public MinecartMemberStore(org.bukkit.World world, double d0, double d1, double d2, Material type) {
		super(world, d0, d1, d2, type);
	}

	/**
	 * Converts all Minecarts on all enabled worlds into Minecart Members
	 */
	public static void convertAll() {
		List<Minecart> minecarts = new ArrayList<Minecart>();
		for (org.bukkit.World world : WorldUtil.getWorlds()) {
			if (TrainCarts.isWorldDisabled(world)) {
				continue;
			}
			for (org.bukkit.entity.Entity entity : WorldUtil.getEntities(world)) {
				if (canConvert(entity)) {
					minecarts.add((Minecart) entity);
				}
			}
		}
		// Convert
		for (Minecart minecart : minecarts) {
			convert(minecart);
		}
		minecarts.clear();
	}

	/**
	 * Checks if a given minecart can be converted to a minecart member<br>
	 * - Returns false if the minecart null or already a minecart member<br>
	 * - Returns true if the class equals the Entity Minecart<br>
	 * - Returns false if the class is another extended version of the Entity Minecart<br>
	 * - Returns true if the class name equals the minecart member name (a forgotten minecart)<br>
	 * - Returns false if the world the entity is in is not enabled in TrainCarts
	 * 
	 * @param minecart to check
	 * @return True if the minecart can be converted, False if not
	 */
	public static boolean canConvert(org.bukkit.entity.Entity minecart) {
		if (minecart == null) {
			return false;
		}
		Class<?> handleClass = Conversion.toEntityHandle.convert(minecart).getClass();
		if (MinecartMember.class.isAssignableFrom(handleClass)) {
			return false;
		}
		// Check if it is a minecart, or if it is a de-linked Minecart Member
		if (!handleClass.equals(EntityMinecart.class) && !handleClass.getName().equals(MinecartMember.class.getName())) {
			return false;
		}
		return !TrainCarts.isWorldDisabled(minecart.getWorld());
	}

	/**
	 * Creates a Minecart Member from the source minecart specified<br>
	 * Returns null if no member could be created for this Source
	 * 
	 * @param source minecart to convert
	 * @return Minecart Member conversion
	 */
	public static MinecartMember convert(Minecart source) {
		if (source.isDead()) {
			return null;
		}
		Object sourceHandle = Conversion.toEntityHandle.convert(source);
		if (sourceHandle instanceof MinecartMember) {
			return (MinecartMember) sourceHandle;
		}
		if (!canConvert(source)) {
			return null;
		}
		// Create a new Minecart member
		MinecartMember with = new MinecartMember(source);
		//unloaded?
		with.unloaded = OfflineGroupManager.containsMinecart(with.uniqueId);

		// Call the conversion event
		MemberConvertEvent event = MemberConvertEvent.call(source, with);
		if (event.isCancelled()) {
			return null;
		}
		with = event.getMember();

		// Replace entity
		EntityTracker tracker = WorldUtil.getTracker(source.getWorld());
		synchronized (tracker.getHandle()) {
			// swap the tracker
			Object entry = tracker.getEntry(source);
			// Create MM tracker using old as base
			if (entry == null) {
				entry = new MinecartMemberTrackerEntry(with);
			} else if (!(entry instanceof MinecartMemberTrackerEntry)) {
				entry = new MinecartMemberTrackerEntry(entry);
			}
			with.tracker = (MinecartMemberTrackerEntry) entry;
			// And set the entity
			EntityUtil.setEntity(source, with.getEntity(), entry);
		}
		return with;
	}

	/**
	 * Tries to find a minecart member by UUID
	 * 
	 * @param uuid of the minecart
	 * @return Minecart Member, or null if not found
	 */
	public static MinecartMember getFromUID(UUID uuid) {
		for (org.bukkit.World world : WorldUtil.getWorlds()) {
			MinecartMember member = MemberConverter.toMember.convert(EntityUtil.getEntity(world, uuid));
			if (member != null && !member.isUnloaded()) {
				return member;
			}
		}
		return null;
	}

	public static MinecartMember get(Object o) {
		return MemberConverter.toMember.convert(o);
	}

	/**
	 * Spawns a minecart as being placed by a player
	 * 
	 * @param at location to spawn
	 * @param player that placed something
	 * @return the spawned Minecart Member, or null if it failed
	 */
	public static MinecartMember spawnBy(Location at, Player player) {
		ItemStack item = player.getItemInHand();
		if (item == null) {
			return null;
		}

		// subtract held item
		if (player.getGameMode() != GameMode.CREATIVE) {
			item.setAmount(item.getAmount() - 1);
			if (item.getAmount() == 0) {
				player.getPlayer().setItemInHand(null);
			}
		}

		// spawn and fire event
		return MinecartMember.spawn(at, item.getType());
	}

	public static MinecartMember spawn(Location at, Material type) {
		MinecartMember mm = new MinecartMember(at.getWorld(), at.getX(), at.getY(), at.getZ(), type);
		mm.yaw = at.getYaw();
		mm.pitch = at.getPitch();
		mm = MemberSpawnEvent.call(mm).getMember();
		mm.tracker = new MinecartMemberTrackerEntry(mm);
		WorldUtil.setTrackerEntry(mm.getEntity(), mm.tracker);
		mm.world.addEntity(mm);
		CommonUtil.callEvent(new VehicleCreateEvent(mm.getMinecart()));
		return mm;
	}

	public static MinecartMember getAt(Block block) {
		return getAt(block.getWorld(), new IntVector3(block));
	}

	public static MinecartMember getAt(org.bukkit.World world, IntVector3 coord) {
		org.bukkit.Chunk chunk = WorldUtil.getChunk(world, coord.x >> 4, coord.z >> 4);
		if (chunk != null) {
			MinecartMember mm;
			MinecartMember result = null;
			// find member in chunk
			for (org.bukkit.entity.Entity entity : WorldUtil.getEntities(chunk)) {
				if ((mm = get(entity)) != null) {
					if (mm.getLiveBlockX() != coord.x) continue;
					if (mm.getLiveBlockY() != coord.y) continue;
					if (mm.getLiveBlockZ() != coord.z) continue;
					result = mm;
					if (result.isHeadingTo(coord)) return result;
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
		if (at == null) {
			return null;
		}
		MinecartMember result = null;
		for (org.bukkit.entity.Entity e : WorldUtil.getNearbyEntities(at, searchRadius, searchRadius, searchRadius)) {
			if (e instanceof Minecart) {
				MinecartMember mm = get(e);
				if (mm == null) {
					continue;
				}
				if (in != null && mm.getGroup() != in) {
					continue;
				}
				if (mm.distanceSquared(at) > searchRadius) {
					continue;
				}
				result = mm;
				// If heading (moving) towards the point, instantly return it
				if (mm.isHeadingTo(at)) {
					return result;
				}
			}
		}
		return result;
	}
}
