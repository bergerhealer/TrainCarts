package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public enum SignActionMode {
	TRAIN, CART, RCTRAIN, NONE;

	public static SignActionMode fromString(String name) {
		if (name.endsWith("]") && name.startsWith("[")) {
			name = name.substring(1, name.length() - 1);
			if (name.startsWith("!") || name.startsWith("+")) {
				name = name.substring(1);
			}
			name = name.toLowerCase();
			// further parsing
			if (name.startsWith("train ") && name.length() > 6) {
				return RCTRAIN;
			} else if (name.startsWith("t ") && name.length() > 2) {
				return RCTRAIN;
			} else if (name.startsWith("train")) {
				return TRAIN;
			} else if (name.startsWith("cart")) {
				return CART;
			}
		}
		return NONE;
	}

	public static SignActionMode fromSign(Sign sign) {
		return sign == null ? NONE : fromString(sign.getLine(0));
	}

	public static SignActionMode fromEvent(SignActionEvent event) {
		return fromSign(event.getSign());
	}

	public static SignActionMode fromEvent(SignChangeEvent event) {
		return fromString(event.getLine(0));
	}
}