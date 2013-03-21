package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.controller.DefaultEntityController;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartChest;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartFurnace;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartHopper;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartRideable;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartTNT;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberChest;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberFurnace;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberHopper;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberRideable;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberTNT;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;

public abstract class MinecartMemberStore {

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
		if (!(minecart instanceof Minecart) || TrainCarts.isWorldDisabled(minecart.getWorld())) {
			return false;
		}
		CommonEntity<Entity> common = CommonEntity.get(minecart);
		return common.hasControllerSupport() && common.getController() instanceof DefaultEntityController;
	}

	/**
	 * Creates a Minecart Member from the source minecart specified<br>
	 * Returns null if no member could be created for this Source
	 * 
	 * @param source minecart to convert
	 * @return Minecart Member conversion
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static MinecartMember<?> convert(Minecart source) {
		if (source.isDead() || !canConvert(source)) {
			return null;
		}
		CommonEntity<?> entity = CommonEntity.get(source);

		// Already assigned a controller?
		if (entity.getController() instanceof MinecartMember) {
			return (MinecartMember<?>) entity.getController();
		}

		// Create a new Minecart controller for this type
		MinecartMember newController;
		if (entity instanceof CommonMinecartRideable) {
			newController = new MinecartMemberRideable();
		} else if (entity instanceof CommonMinecartFurnace) {
			newController = new MinecartMemberFurnace();
		} else if (entity instanceof CommonMinecartChest) {
			newController = new MinecartMemberChest();
		} else if (entity instanceof CommonMinecartHopper) {
			newController = new MinecartMemberHopper();
		} else if (entity instanceof CommonMinecartTNT) {
			newController = new MinecartMemberTNT();
		} else {
			// Unsupported
			return null;
		}

		// Unloaded?
		newController.unloaded = OfflineGroupManager.containsMinecart(entity.getUniqueId());

		// Set controllers and done
		entity.setController(newController);
		entity.setNetworkController(new MinecartMemberNetwork());
		return newController;
	}

	/*
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
	*/

	/**
	 * Tries to find a minecart member by UUID
	 * 
	 * @param uuid of the minecart
	 * @return Minecart Member, or null if not found
	 */
	public static MinecartMember<?> getFromUID(UUID uuid) {
		for (org.bukkit.World world : WorldUtil.getWorlds()) {
			MinecartMember<?> member = MemberConverter.toMember.convert(EntityUtil.getEntity(world, uuid));
			if (member != null && !member.isUnloaded()) {
				return member;
			}
		}
		return null;
	}

	public static MinecartMember<?> get(Object o) {
		return MemberConverter.toMember.convert(o);
	}

	/**
	 * Spawns a minecart as being placed by a player
	 * 
	 * @param at location to spawn
	 * @param player that placed something
	 * @return the spawned Minecart Member, or null if it failed
	 */
	public static MinecartMember<?> spawnBy(Location at, Player player) {
		ItemStack item = player.getItemInHand();
		if (LogicUtil.nullOrEmpty(item)) {
			return null;
		}

		// subtract held item
		if (player.getGameMode() != GameMode.CREATIVE) {
			ItemUtil.subtractAmount(item, 1);
			if (LogicUtil.nullOrEmpty(item)) {
				player.setItemInHand(null);
			} else {
				player.setItemInHand(item);
			}
		}

		// spawn and fire event
		return spawn(at, item.getType());
	}

	public static MinecartMember<?> spawn(Location at, Material type) {
//		MinecartMember<?> mm = new MinecartMember<?>(at.getWorld(), at.getX(), at.getY(), at.getZ(), type);
//		mm.yaw = at.getYaw();
//		mm.pitch = at.getPitch();
//		mm = MemberSpawnEvent.call(mm).getMember();
//		mm.tracker = new MinecartMemberTrackerEntry(mm);
//		WorldUtil.setTrackerEntry(mm.getEntity(), mm.tracker);
//		mm.world.addEntity(mm);
//		CommonUtil.callEvent(new VehicleCreateEvent(mm.getMinecart()));
//		return mm;
		return null;
	}

	public static MinecartMember<?> getAt(Block block) {
		return getAt(block.getWorld(), new IntVector3(block));
	}

	public static MinecartMember<?> getAt(org.bukkit.World world, IntVector3 coord) {
		org.bukkit.Chunk chunk = WorldUtil.getChunk(world, coord.x >> 4, coord.z >> 4);
		if (chunk != null) {
			MinecartMember<?> mm;
			MinecartMember<?> result = null;
			// find member in chunk
			for (org.bukkit.entity.Entity entity : WorldUtil.getEntities(chunk)) {
				if ((mm = get(entity)) != null) {
					if (mm.getEntity().getLocChunkX() != coord.x) continue;
					if (mm.getEntity().getLocChunkY() != coord.y) continue;
					if (mm.getEntity().getLocChunkZ() != coord.z) continue;
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

	public static MinecartMember<?> getAt(Location at) {
		return getAt(at, null);
	}
	public static MinecartMember<?> getAt(Location at, MinecartGroup in) {
		return getAt(at, in, 1);
	}
	public static MinecartMember<?> getAt(Location at, MinecartGroup in, double searchRadius) {
		if (at == null) {
			return null;
		}
		MinecartMember<?> result = null;
		final double distSquared = searchRadius * searchRadius;
		for (org.bukkit.entity.Entity e : WorldUtil.getNearbyEntities(at, searchRadius, searchRadius, searchRadius)) {
			if (e instanceof Minecart) {
				MinecartMember<?> mm = get(e);
				if (mm == null) {
					continue;
				}
				if (in != null && mm.getGroup() != in) {
					continue;
				}
				if (mm.getEntity().distanceSquaredTo(at) > distSquared) {
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
