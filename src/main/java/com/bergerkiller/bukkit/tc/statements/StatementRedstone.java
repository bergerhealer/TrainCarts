package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementRedstone extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("redstone");
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("rs") || text.equals("redstone");
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		return this.handle(text, event);
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		return this.handle(text, event);
	}

	@Override
	public boolean handleArray(MinecartMember<?> member, String[] names, SignActionEvent event) {
		return this.handle(names, event);
	}

	@Override
	public boolean handleArray(MinecartGroup group, String[] names, SignActionEvent event) {
		return this.handle(names, event);
	}

	public boolean handle(String text, SignActionEvent event) {
		return event.isPoweredRaw(false);
	}

	public boolean handle(String[] names, SignActionEvent event) {
		for (String name : names) {
			BlockFace direction = Direction.parse(name).getDirection(event.getFacing());
			if (event.getPower(direction).hasPower()) {
				return true;
			}
		}
		return false;
	}
}
