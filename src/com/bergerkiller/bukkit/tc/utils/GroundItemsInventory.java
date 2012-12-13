package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Item;

import net.minecraft.server.v1_4_5.EntityItem;
import net.minecraft.server.v1_4_5.ItemStack;
import net.minecraft.server.v1_4_5.World;

import com.bergerkiller.bukkit.common.natives.IInventoryBase;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.NativeUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

/**
 * Allows you to use items on the ground as an Inventory<br>
 * Is of a dynamic size; the last item is always null<br>
 * If this item is set, a new item is spawned
 */
public class GroundItemsInventory extends IInventoryBase {
	private final List<Item> items = new ArrayList<Item>();
	private final Location location;
	private final World world;

	public GroundItemsInventory(Location location, double range) {
		this.location = location;
		this.world = NativeUtil.getNative(location.getWorld());
		double rangeSquared = range * range;
		for (org.bukkit.entity.Entity e : WorldUtil.getEntities(location.getWorld())) {
			if (!(e instanceof Item)) {
				continue;
			}
			EntityItem em = NativeUtil.getNative((Item) e);
			if (MathUtil.distanceSquared(em.locX, em.locY, em.locZ, location.getX(), location.getY(), location.getZ()) <= rangeSquared) {
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
			// Spawn new item for this item stack
			float rfact = 0.7F;
			float offset = (1.0f - rfact) * 0.5f;
			double dX = (double) (world.random.nextFloat() * rfact + offset);
			double dY = (double) (world.random.nextFloat() * rfact + offset);
			double dZ = (double) (world.random.nextFloat() * rfact + offset);
			EntityItem entityitem = new EntityItem(world, location.getX() + dX, location.getY() + dY, location.getZ() + dZ, stack);
			entityitem.pickupDelay = 5;
			EntityUtil.addEntity(entityitem.getBukkitEntity());
			this.items.add(NativeUtil.getItem(entityitem));
		} else {
			// Set item stack, if null, kill the item
			EntityItem item = NativeUtil.getNative(this.items.get(index));
			if (!(item.dead = (stack == null))) {
				item.itemStack = stack;
				this.items.set(index, ItemUtil.respawnItem((Item) item.getBukkitEntity()));
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
				return NativeUtil.getNative(item).itemStack;
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
