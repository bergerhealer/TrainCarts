package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.inventory.Inventory;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class StatementPlayerItems extends StatementItems {

	@Override
	public boolean match(String text) {
		return text.startsWith("playeritems");
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("pi");
	}
	
	@Override
	public Inventory getInventory(MinecartMember<?> member) {
		return member.getPlayerInventory();
	}
	
	@Override
	public Inventory getInventory(MinecartGroup group) {
		return group.getPlayerInventory();
	}
}
