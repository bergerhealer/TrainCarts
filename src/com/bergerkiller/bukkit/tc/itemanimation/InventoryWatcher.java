package com.bergerkiller.bukkit.tc.itemanimation;

import java.util.List;

import net.minecraft.server.EntityHuman;
import net.minecraft.server.IInventory;
import net.minecraft.server.ItemStack;

import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class InventoryWatcher implements IInventory {

	public InventoryWatcher(Object from, Object to, Inventory inventory) {
		this(from, to, ((CraftInventory) inventory).getInventory());
	}
	
	private Object from, to;
	private final IInventory source;
	private final ItemStack[] original;
	public InventoryWatcher(Object from, Object to, final IInventory inventory) {
		this.from = from;
		this.to = to;
		this.source = inventory;
		this.original = new ItemStack[this.source.getSize()];
		for (int i = 0; i < this.original.length; i++) {
			ItemStack item = inventory.getItem(i);
			this.original[i] = item == null ? null : item.cloneItemStack();
		}
	}
	
	public static Inventory convert(Object from, Object to, Inventory inventory) {
		return convert(from, to, ((CraftInventory) inventory).getInventory());
	}
	public static Inventory convert(Object from, Object to, IInventory inventory) {
		return new InventoryWatcher(from, to, inventory).getInventory();
	}
	
	public Inventory getInventory() {
		return new CraftInventory(this);
	}
		
	@Override
	public void setItem(int index, ItemStack newitem) {
		ItemStack olditem = this.original[index];
		this.source.setItem(index, newitem);
		this.original[index] = newitem == null ? null : newitem.cloneItemStack();
		if (olditem == null) {
			if (newitem != null) {
				ItemAnimation.start(from, to, newitem);
			}
		} else {
			if (newitem == null) {
				ItemAnimation.start(to, from, olditem);
			} else {
				//same type?
				if (newitem.id == olditem.id && newitem.getData() == olditem.getData()) {
					ItemStack trans = newitem.cloneItemStack();
					trans.count -= olditem.count;
					if (trans.count > 0) {
						ItemAnimation.start(from, to, trans);
					} else if (trans.count < 0) {
						trans.count = -trans.count;
						ItemAnimation.start(to, from, trans);
					}
				} else {
					//swap
					ItemAnimation.start(to, from, olditem);
					ItemAnimation.start(from, to, newitem);
				}
			}
		}
	}
	
	public boolean a(EntityHuman arg0) {
		return this.source.a(arg0);
	}
	public void f() {
		this.source.f();
	}
	public ItemStack[] getContents() {
		return this.source.getContents();
	}
	public ItemStack getItem(int arg0) {
		return this.source.getItem(arg0);
	}
	public int getMaxStackSize() {
		return this.source.getMaxStackSize();
	}
	public String getName() {
		return this.source.getName();
	}
	public int getSize() {
		return this.source.getSize();
	}
	public ItemStack splitStack(int arg0, int arg1) {
		return this.source.splitStack(arg0, arg1);
	}
	public void update() {
		this.source.update();
	}

	@Override
	public InventoryHolder getOwner() {
		return this.source.getOwner();
	}

	@Override
	public List<HumanEntity> getViewers() {
		return this.source.getViewers();
	}

	@Override
	public void onClose(CraftHumanEntity arg0) {
		this.source.onClose(arg0);
	}

	@Override
	public void onOpen(CraftHumanEntity arg0) {
		this.source.onOpen(arg0);
	}

	@Override
	public ItemStack splitWithoutUpdate(int arg0) {
		return this.source.splitWithoutUpdate(arg0);
	}

	@Override
	public void setMaxStackSize(int arg0) {
		this.source.setMaxStackSize(arg0);
	}

	@Override
	public void startOpen() {
		this.source.startOpen();
	}
}
