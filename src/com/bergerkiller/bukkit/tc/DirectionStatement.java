package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.statements.Statement;

public class DirectionStatement {

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

	public Direction direction;
	public String text;
	public Integer number;

	public boolean has(MinecartMember member) {
		return Statement.has(member, this.text);
	}

	public boolean has(MinecartGroup group) {
		return Statement.has(group, this.text);
	}

	public boolean hasNumber() {
		return this.number != null;
	}
}
