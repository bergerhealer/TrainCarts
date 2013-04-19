package com.bergerkiller.bukkit.tc.statements;

import java.util.Locale;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementType extends Statement {

	private boolean isSize(String text) {
		return LogicUtil.contains(text, "cartcount", "trainsize", "length", "count", "size");
	}

	@Override
	public boolean match(String text) {
		return isSize(text) || Conversion.toMinecartType.convert(text) != null;
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		if (isSize(text.toLowerCase(Locale.ENGLISH))) {
			return true;
		}
		return member.getEntity().getType() == Conversion.toMinecartType.convert(text);
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		if (isSize(text.toLowerCase(Locale.ENGLISH))) {
			return Util.evaluate(group.size(), text);
		}
		final EntityType type = Conversion.toMinecartType.convert(text);
		if (type == null) {
			return false;
		} else {
			return Util.evaluate(group.size(type), text);
		}
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
