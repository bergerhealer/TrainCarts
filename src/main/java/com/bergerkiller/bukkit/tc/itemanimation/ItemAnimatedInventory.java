package com.bergerkiller.bukkit.tc.itemanimation;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.inventory.InventoryBase;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.utils.GroundItemsInventory;

/**
 * Redirects calls to a base inventory, while showing item animations during item transfers
 */
public class ItemAnimatedInventory extends InventoryBase {
	private Object other, self;
	private final Inventory source;
	private final ItemStack[] original;

	public ItemAnimatedInventory(Inventory inventory, Object self, Object other) {
		this.other = other;
		this.self = self;
		this.source = inventory;
		this.original = ItemUtil.getClonedContents(inventory);
	}

	public static Inventory convert(Inventory inventory, Object self, Object other) {
		return new ItemAnimatedInventory(inventory, self, other);
	}

	@Override
	public void setItem(int index, ItemStack newitem) {
		ItemStack olditem = this.original[index];
		this.source.setItem(index, newitem);
		Object self = this.getSelfAt(index);
		this.original[index] = ItemUtil.cloneItem(newitem);
		if (olditem == null) {
			if (newitem != null) {
				ItemAnimation.start(other, self, newitem);
			}
		} else {
			if (newitem == null) {
				ItemAnimation.start(self, other, olditem);
			} else {
				//same type?
				if (ItemUtil.equalsIgnoreAmount(olditem, newitem)) {
					// Obtain an item stack (trans) to do an animation with
					// Switch between self and other based on changed amount
					ItemStack trans = ItemUtil.cloneItem(newitem);
					int newAmount = trans.getAmount() - olditem.getAmount();
					if (newAmount > 0) {
						trans.setAmount(newAmount);
						ItemAnimation.start(other, self, trans);
					} else if (newAmount < 0) {
						trans.setAmount(-newAmount);
						ItemAnimation.start(self, other, trans);
					}
				} else {
					//swap
					ItemAnimation.start(self, other, olditem);
					ItemAnimation.start(other, self, newitem);
				}
			}
		}
	}

	public Object getSelfAt(int index) {
		if (this.source instanceof GroundItemsInventory) {
			return ((GroundItemsInventory) this.source).getEntity(index);
		}
		return self;
	}

	@Override
	public ItemStack getItem(int index) {
		return this.source.getItem(index);
	}

	@Override
	public int getSize() {
		return this.source.getSize();
	}
}
