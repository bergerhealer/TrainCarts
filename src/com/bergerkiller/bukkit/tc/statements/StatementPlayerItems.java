package com.bergerkiller.bukkit.tc.statements;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.ItemStack;
import net.minecraft.server.PlayerInventory;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class StatementPlayerItems extends Statement {

	@Override
	public boolean match(String text) {
		return false;
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("pi");
	}
	
	public boolean hasItem(MinecartMember member, ItemParser[] parsers) {
		if (member.hasPlayerPassenger()) {
			PlayerInventory inventory = ((EntityPlayer) member.passenger).inventory;
			for (ItemParser parser : parsers) {
				if (parser.hasType()) {
    				Integer data = parser.hasData() ? (int) parser.getData() : null;
    				ItemStack item = ItemUtil.findItem(inventory, parser.getTypeId(), data);
    				if (item == null) continue;
    				if (parser.hasAmount()) {
    					if (item.count >= parser.getAmount()) {
    						return true;
    					}
    				} else {
    					return true;
    				}
				} else {
					for (ItemStack item : inventory.getContents()) {
						if (item != null) {
							return true;
						}
					}
					return false;
				}
			}
		}
		return false;
	}
	
	@Override
	public boolean handleArray(MinecartMember member, String[] text) {
		return hasItem(member, Util.getParsers(text));
	}
	
	@Override
	public boolean handleArray(MinecartGroup group, String[] text) {
		ItemParser[] parsers = Util.getParsers(text);
		for (MinecartMember member : group) {
			if (hasItem(member, parsers)) {
				return true;
			}
		}
		return false;
	}
}
