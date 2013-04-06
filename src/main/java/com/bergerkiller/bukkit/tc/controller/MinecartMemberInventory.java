package com.bergerkiller.bukkit.tc.controller;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.controller.EntityInventoryController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartInventory;

public class MinecartMemberInventory extends EntityInventoryController<CommonMinecartInventory<?>> {

	@Override
	public void onItemSet(int index, ItemStack item) {
		super.onItemSet(index, item);
		// Mark the Entity as changed
		((MinecartMember<?>) entity.getController()).onPropertiesChanged();
	}
}
