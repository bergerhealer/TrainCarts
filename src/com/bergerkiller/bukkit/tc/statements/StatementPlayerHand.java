package com.bergerkiller.bukkit.tc.statements;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.ItemStack;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class StatementPlayerHand extends Statement {

	@Override
	public boolean match(String text) {
		return false;
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("ph");
	}
	
	public boolean handleItem(MinecartMember member, ItemParser[] parsers) {
		if (member.hasPlayerPassenger()) {
			ItemStack item = ((EntityPlayer) member.passenger).inventory.getItemInHand();
			if (item != null) {
				for (ItemParser parser : parsers) {
					if (parser.hasType()) {
						if (item == null || item.id != parser.getTypeId()) continue;
						if (parser.hasData() && item.getData() != parser.getData()) continue;
						if (parser.hasAmount() && item.count < parser.getAmount()) continue;
						return true;
					} else {
						if (item != null) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	@Override
	public boolean handleArray(MinecartMember member, String[] text) {
		return handleItem(member, Util.getParsers(text));
	}
	
	@Override
	public boolean handleArray(MinecartGroup group, String[] text) {
		ItemParser[] parsers = Util.getParsers(text);
		for (MinecartMember member : group) {
			if (handleItem(member, parsers)) {
				return true;
			}
		}
		return false;
	}
}
