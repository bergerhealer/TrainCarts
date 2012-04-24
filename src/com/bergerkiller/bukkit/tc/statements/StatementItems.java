package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class StatementItems extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("items");
	}
	
	@Override
	public boolean handle(MinecartMember member, String text) {
		return member.hasItems();
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("i");
	}
	
	@Override
	public boolean handleArray(MinecartMember member, String[] items) {
		for (ItemParser parser : Util.getParsers(items)) {
			if (member.hasItem(parser)) {
				return true;
			}
		}
        return false;
	}
	
	@Override
	public boolean handleArray(MinecartGroup group, String[] items) {
		for (ItemParser parser : Util.getParsers(items)) {
			for (MinecartMember member : group) {
				if (member.hasItem(parser)) {
					return true;
				}
			}
		}
		return false;
	}
}
