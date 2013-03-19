package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberFurnace;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementFuel extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("coal") || text.equals("fuel") || text.equals("fueled");
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		return member instanceof MinecartMemberFurnace && ((MinecartMemberFurnace) member).getEntity().hasFuel();
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
