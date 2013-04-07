package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementDirection extends Statement {

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		BlockFace[] directions = Direction.parseAll(text, event.getFacing().getOppositeFace());
		return directions.length > 0 && LogicUtil.contains(member.getDirectionFrom(), directions);
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		return handle(group.head(), text, event);
	}

	@Override
	public boolean match(String text) {
		return Direction.parseAll(text).length > 0;
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}
}
