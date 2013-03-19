package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementRandom extends Statement {

	@Override
	public boolean match(String text) {
		return text.startsWith("rand");
	}

	@Override
	public boolean matchArray(String text) {
		return text.startsWith("rand");
	}

	private boolean handle(String... text) {
		double chance = 0.5;
		if (text.length > 0) {
			chance = ParseUtil.parseDouble(text[0], chance);
			if (text[0].endsWith("%")) {
				chance /= 100;
			}
			chance = MathUtil.clamp(chance, 0.0, 1.0);
		}
		return Math.random() <= chance;
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		return handle();
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		return handle();
	}

	@Override
	public boolean handleArray(MinecartGroup group, String[] text, SignActionEvent event) {
		return handle(text);
	}

	@Override
	public boolean handleArray(MinecartMember<?> member, String[] text, SignActionEvent event) {
		return handle(text);
	}
}
