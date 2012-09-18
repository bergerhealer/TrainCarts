package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

import net.minecraft.server.EntityItem;
import net.minecraft.server.ItemStack;
import net.minecraft.server.World;

import com.bergerkiller.bukkit.common.items.IInventoryBase;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

/**
 * Allows you to use items on the ground as an Inventory<br>
 * Is of a dynamic size; the last item is always null<br>
 * If this item is set, a new item is spawned
 */
public class GroundItemsInventory extends IInventoryBase {
	private final List<EntityItem> items = new ArrayList<EntityItem>();
	private final Location location;
	private final World world;

	public GroundItemsInventory(Location location, double range) {
		this.location = location;
		this.world = WorldUtil.getNative(location.getWorld());
		double rangeSquared = range * range;
		for (Object o : this.world.entityList) {
			if (!(o instanceof EntityItem)) {
				continue;
			}
			EntityItem em = (EntityItem) o;
			if (MathUtil.distanceSquared(em.locX, em.locY, em.locZ, location.getX(), location.getY(), location.getZ()) > rangeSquared) {
				continue;
			}
			this.items.add(em);
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
	public EntityItem getEntity(int index) {
		return this.items.get(index);
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		if (index == this.items.size()) {
			// Spawn new item for this item stack
			float rfact = 0.7F;
			float offset = (1.0f - rfact) * 0.5f;
			double dX = (double) (world.random.nextFloat() * rfact + offset);
			double dY = (double) (world.random.nextFloat() * rfact + offset);
			double dZ = (double) (world.random.nextFloat() * rfact + offset);
			EntityItem entityitem = new EntityItem(world, location.getX() + dX, location.getY() + dY, location.getZ() + dZ, stack);
			entityitem.pickupDelay = 5;
			world.addEntity(entityitem);
			this.items.add(entityitem);
		} else {
			// Set item stack, if null, kill the item
			EntityItem item = this.items.get(index);
			if (!(item.dead = (stack == null))) {
				item.itemStack = stack;
				this.items.set(index, ItemUtil.respawnItem(item));
			}
		}
	}

	@Override
	public ItemStack getItem(int index) {
		if (index == this.items.size()) {
			return null;
		} else {
			EntityItem item = this.items.get(index);
			if (item.dead) {
				return null;
			} else {
				return item.itemStack;
			}
		}
	}

	@Override
	public ItemStack[] getContents() {
		ItemStack[] items = new ItemStack[this.getSize()];
		for (int i = 0; i < items.length; i++) {
			items[i] = this.getItem(i);
		}
		return items;
	}

	@Override
	public String getName() {
		return "Ground Items";
	}
}
