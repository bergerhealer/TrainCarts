package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementPermission extends Statement {

	@Override
	public boolean match(String text) {
		return false;
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("pm") || text.equals("perm");
	}

	@Override
	public boolean handleArray(MinecartGroup group, String[] text, SignActionEvent event) {
		for (MinecartMember<?> member : group) {
			if (!handleArray(member, text, event)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean handleArray(MinecartMember<?> member, String[] text, SignActionEvent event) {
		if (member.getEntity().hasPlayerPassenger()) {
			Player passenger = member.getEntity().getPlayerPassenger();
			for (String perm : text) {
				if (!passenger.hasPermission(perm)) {
					return false;
				}
			}
		}
		return true;
	}
}
