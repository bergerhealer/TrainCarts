package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.inventory.InventoryBase;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

/**
 * Allows you to use items on the ground as an Inventory<br>
 * Is of a dynamic size; the last item is always null<br>
 * If this item is set, a new item is spawned
 */
public class GroundItemsInventory extends InventoryBase {
	private final List<Item> items = new ArrayList<Item>();
	private final Location location;

	public GroundItemsInventory(Block block, double range) {
		this(block.getLocation().add(0.5, 0.5, 0.5), range);
	}

	public GroundItemsInventory(Location location, double range) {
		this.location = location;
		double rangeSquared = range * range;
		for (org.bukkit.entity.Entity e : WorldUtil.getEntities(location.getWorld())) {
			if (e instanceof Item && e.getLocation().distanceSquared(location) <= rangeSquared) {
				this.items.add((Item) e);
			}
		}
	}

	@Override
	public int getSize() {
		return this.items.size() + 1;
	}

	/**
	 * Gets the Location of the center of this ground items cluster
	 * 
	 * @return ground center location
	 */
	public Location getLocation() {
		return this.location;
	}

	/**
	 * Gets the item at the index specified<br>
	 * Note that the last element has no item!
	 * 
	 * @param index to get the item at
	 * @return the item
	 */
	public Item getEntity(int index) {
		return this.items.get(index);
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		if (index == this.items.size()) {
			if (!LogicUtil.nullOrEmpty(stack)) {
				// Spawn new item for this item stack
				Random random = WorldUtil.getRandom(this.location.getWorld());
				Location spawnLoc = this.location.clone().add(-0.45, -0.45, -0.45);
				spawnLoc = spawnLoc.add(0.9f * random.nextFloat(), 0.9f * random.nextFloat(), 0.9f * random.nextFloat());
				Item item = location.getWorld().dropItem(spawnLoc, stack);
				item.setVelocity(new Vector(0, 0, 0));
				this.items.add(item);
			}
		} else {
			// Set item stack, if null, kill the item
			Item item = this.items.get(index);
			EntityUtil.setDead(item, LogicUtil.nullOrEmpty(stack));
			if (!item.isDead()) {
				item.setItemStack(stack);
				this.items.set(index, ItemUtil.respawnItem(item));
			}
		}
	}

	@Override
	public ItemStack getItem(int index) {
		if (index == this.items.size()) {
			return null;
		} else {
			Item item = this.items.get(index);
			if (item.isDead()) {
				return null;
			} else {
				return item.getItemStack();
			}
		}
	}

	@Override
	public String getName() {
		return "Ground Items";
	}
}
