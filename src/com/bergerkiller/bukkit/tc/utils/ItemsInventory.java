package com.bergerkiller.bukkit.tc.utils;

import java.util.Collection;

import net.minecraft.server.EntityItem;
import net.minecraft.server.ItemStack;

import com.bergerkiller.bukkit.common.SimpleInventory;

/**
 * Allows you to use a collection of items as an Inventory
 */
public class ItemsInventory extends SimpleInventory {
	private final EntityItem[] items;

	public ItemsInventory(Collection<EntityItem> items) {
		super(getItems(items));
		this.items = items.toArray(new EntityItem[0]);
	}

	private static ItemStack[] getItems(Collection<EntityItem> items) {
		ItemStack[] rval = new ItemStack[items.size()];
		int i = 0;
		for (EntityItem item : items) {
			rval[i] = item.itemStack;
			i++;
		}
		return rval;
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		super.setItem(index, stack);
		EntityItem item = this.items[index];
		item.dead = (item.itemStack = stack) == null;
	}
}
