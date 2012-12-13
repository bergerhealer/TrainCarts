package com.bergerkiller.bukkit.tc.statements;

import java.util.ArrayList;

import org.bukkit.inventory.Inventory;

import net.minecraft.server.v1_4_5.ItemStack;

import com.bergerkiller.bukkit.common.natives.IInventoryBaseImpl;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class StatementPlayerHand extends StatementItems {

	@Override
	public boolean match(String text) {
		return text.startsWith("playerhand");
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("ph");
	}

	@Override
	public Inventory getInventory(MinecartMember member) {
		org.bukkit.inventory.ItemStack item = null;
		if (member.hasPlayerPassenger()) {
			item = member.getPlayerInventory().getItemInHand();
		}
		if (LogicUtil.nullOrEmpty(item)) {
			return new IInventoryBaseImpl(new ItemStack[0]).getInventory();
		} else {
			return new IInventoryBaseImpl(item).getInventory();
		}
	}

	@Override
	public Inventory getInventory(MinecartGroup group) {
		ArrayList<org.bukkit.inventory.ItemStack> items = new ArrayList<org.bukkit.inventory.ItemStack>();
		for (MinecartMember member : group) {
			if (member.hasPlayerPassenger()) {
				org.bukkit.inventory.ItemStack item = member.getPlayerInventory().getItemInHand();
				if (LogicUtil.nullOrEmpty(item)) {
					items.add(item);
				}
			}
		}
		return new IInventoryBaseImpl(items).getInventory();
	}
}
