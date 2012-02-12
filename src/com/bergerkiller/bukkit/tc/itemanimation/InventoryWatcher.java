package com.bergerkiller.bukkit.tc.itemanimation;

import net.minecraft.server.IInventory;

import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryWatcher extends CraftInventory {

	public InventoryWatcher(Object from, Object to, Inventory inventory) {
		this(from, to, ((CraftInventory) inventory).getInventory());
	}
	
	private Object from, to;
	public InventoryWatcher(Object from, Object to, IInventory inventory) {
		super(inventory);
		this.from = from;
		this.to = to;
	}
	
	public void setItem(int index, ItemStack item) {
		super.setItem(index, item);
		ItemAnimation.start(from, to, item);
	}
	
}
