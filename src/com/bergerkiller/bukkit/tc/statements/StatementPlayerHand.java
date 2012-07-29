package com.bergerkiller.bukkit.tc.statements;

import java.util.ArrayList;

import org.bukkit.inventory.Inventory;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.ItemStack;

import com.bergerkiller.bukkit.common.SimpleInventory;
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
		ItemStack item = null;
		if (member.hasPlayerPassenger()) {
			item = ((EntityPlayer) member.passenger).inventory.getItemInHand();
		}
		if (item == null) {
			return new SimpleInventory(new ItemStack[0]).getInventory();
		} else {
			return new SimpleInventory(item).getInventory();
		}
	}
	
	@Override
	public Inventory getInventory(MinecartGroup group) {
		ArrayList<ItemStack> items = new ArrayList<ItemStack>();
		for (MinecartMember member : group) {
			if (member.hasPlayerPassenger()) {
				ItemStack item = ((EntityPlayer) member.passenger).inventory.getItemInHand();
				if (item != null) {
					items.add(item);
				}
			}
		}
		return new SimpleInventory(items).getInventory();
	}
}
