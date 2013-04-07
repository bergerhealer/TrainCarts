package com.bergerkiller.bukkit.tc;

import java.util.Locale;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.statements.Statement;

public class DirectionStatement {
	public Direction direction;
	public String text;
	public Integer number;

	public DirectionStatement(String text, BlockFace cartDirection) {
		this(text, BlockFace.SELF, Direction.NONE);
	}

	public DirectionStatement(String text, BlockFace cartDirection, Direction alternative) {
		int idx = text.indexOf(':');
		if (idx == -1) {
			this.text = text;
			this.direction = alternative;
		} else {
			this.text = text.substring(idx + 1);
			// Parse Direction from String text
			final String dirText = text.substring(0, idx).toLowerCase(Locale.ENGLISH);
			if (LogicUtil.contains(dirText, "c", "continue")) {
				this.direction = Direction.fromFace(cartDirection);
			} else if (LogicUtil.contains(dirText, "r", "reverse")) {
				this.direction = Direction.fromFace(cartDirection.getOppositeFace());
			} else {
				this.direction = Direction.parse(dirText);
			}
		}
		try {
			this.number = Integer.parseInt(this.text);
		} catch (NumberFormatException ex) {
			this.number = null;
		}
	}

	public boolean has(SignActionEvent event, MinecartMember<?> member) {
		return Statement.has(member, this.text, event);
	}

	public boolean has(SignActionEvent event, MinecartGroup group) {
		return Statement.has(group, this.text, event);
	}

	public boolean hasNumber() {
		return this.number != null;
	}
}
