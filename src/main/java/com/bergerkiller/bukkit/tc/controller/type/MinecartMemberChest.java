package com.bergerkiller.bukkit.tc.controller.type;

import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartChest;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberInventory;

public class MinecartMemberChest extends MinecartMember<CommonMinecartChest> {

	@Override
	public void onAttached() {
		super.onAttached();
		entity.setInventoryController(new MinecartMemberInventory());
	}

	public boolean hasItem(ItemParser item) {
		if (item == null)
			return false;
		if (item.hasData()) {
			return this.hasItem(item.getType(), item.getData());
		} else {
			return this.hasItem(item.getType());
		}
	}

	public boolean hasItem(Material type, int data) {
		for (ItemStack stack : this.entity.getInventory()) {
			if (!LogicUtil.nullOrEmpty(stack)) {
				if (stack.getType() == type && stack.getDurability() == data) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasItem(Material type) {
		for (ItemStack stack : this.entity.getInventory()) {
			if (!LogicUtil.nullOrEmpty(stack)) {
				if (stack.getType() == type) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasItems() {
		for (ItemStack stack : this.entity.getInventory()) {
			if (stack != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
		super.onPhysicsPostMove(speedFactor);
		if (this.getProperties().canPickup()) {
			Inventory inv = entity.getInventory();
			double distance;
			for (Entity e : entity.getNearbyEntities(2.0)) {
				if (!(e instanceof Item) || EntityUtil.isIgnored(e)) {
					continue;
				}
				ItemStack stack = ((Item) e).getItemStack();
				distance = entity.loc.distanceSquared(e);
				if (ItemUtil.testTransfer(stack, inv) == stack.getAmount()) {
					if (distance < 0.7) {
						ItemUtil.transfer(stack, inv, Integer.MAX_VALUE);
						// This.world.playNote
						entity.getWorld().playEffect(entity.getLocation(), Effect.CLICK1, 0);
						if (stack.getAmount() == 0) {
							e.remove();
							continue;
						}
					} else {
						final double factor;
						if (distance > 1) {
							factor = 0.8;
						} else if (distance > 0.75) {
							factor = 0.5;
						} else {
							factor = 0.1;
						}
						this.push(e, -factor / distance);
					}
				}
			}
		}
	}
}
