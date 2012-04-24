package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.MinecartMember;

/**
 * This tag is a default and always matches, ask it lastly!
 */
public class StatementTag extends Statement {

	@Override
	public boolean match(String text) {
		return true;
	}
	
	@Override
	public boolean matchArray(String text) {
		return true;
	}
	
	@Override
	public boolean handle(MinecartMember member, String tag) {
		return this.handleArray(member, parseArray(tag));
	}
	
	@Override
	public boolean handleArray(MinecartMember member, String[] tags) {
		for (String tag : tags) {
			if (member.getProperties().hasTag(tag)) {
				return true;
			}
		}
		return false;
	}
	
}
