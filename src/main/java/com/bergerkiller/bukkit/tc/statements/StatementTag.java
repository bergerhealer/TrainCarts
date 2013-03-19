package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

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
	public boolean handle(MinecartMember<?> member, String tag, SignActionEvent event) {
		return this.handleArray(member, parseArray(tag), event);
	}

	@Override
	public boolean handleArray(MinecartMember<?> member, String[] tags, SignActionEvent event) {
		for (String tag : tags) {
			if (member.getProperties().matchTag(tag)) {
				return true;
			}
		}
		return false;
	}
}
