package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.statements.Statement;

public class DirectionStatement {
	public Direction direction;
	public String text;
	public Integer number;

	public DirectionStatement(String text) {
		this(text, Direction.NONE);
	}

	public DirectionStatement(String text, Direction alternative) {
		int idx = text.indexOf(':');
		if (idx == -1) {
			this.direction = alternative;
			this.text = text;
		} else {
			this.direction = Direction.parse(text.substring(0, idx));
			this.text = text.substring(idx + 1);
		}
		try {
			this.number = Integer.parseInt(this.text);
		} catch (NumberFormatException ex) {
			this.number = null;
		}
	}

	public boolean has(SignActionEvent event, MinecartMember member) {
		return Statement.has(member, this.text, event);
	}

	public boolean has(SignActionEvent event, MinecartGroup group) {
		return Statement.has(group, this.text, event);
	}

	public boolean hasNumber() {
		return this.number != null;
	}
}
