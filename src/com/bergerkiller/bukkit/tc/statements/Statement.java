package com.bergerkiller.bukkit.tc.statements;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;

public abstract class Statement {
	
	/**
	 * Checks if this statement matches the given text
	 * The given text is lower cased.
	 * @param text to use
	 * @return if it matches and can handle it
	 */
	public abstract boolean match(String text);
	
	/**
	 * Checks if this statement matches the given text
	 * The given text is lower cased.
	 * The text is the pre-text of '@'
	 * @param text to use
	 * @return if it matches and can handle an array
	 */
	public abstract boolean matchArray(String text);
	
	public boolean handle(MinecartGroup group, String text) {
		for (MinecartMember member : group) {
			if (this.handle(member, text)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean handleArray(MinecartGroup group, String[] text) {
		for (MinecartMember member : group) {
			if (this.handleArray(member, text)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean handle(MinecartMember member, String text) {
		return false;
	}
	
	public boolean handleArray(MinecartMember member, String[] text) {
		return false;
	}
	
	public static String[] parseArray(String text) {
		return text.split(";", -1);
	}
	
	private static List<Statement> statements = new ArrayList<Statement>();
	
	public static void init() {
		register(new StatementDestination());
		register(new StatementBoolean());
		register(new StatementEmpty());
		register(new StatementPassenger());
		register(new StatementOwners());
		register(new StatementItems());
		register(new StatementFuel());
		register(new StatementType());
		register(new StatementPlayerItems());
		register(new StatementPlayerHand());
		register(new StatementMob());
		register(new StatementTag()); //register lastly!
	}
	
	public static void deinit() {
		statements.clear();
	}
	
	public static <T extends Statement> T register(T statement) {
		statements.add(statement);
		return statement;
	}
		
	public static Direction getDirection(MinecartMember member, String text) {
		return getDirection(member, null, text);
	}
	
	public static Direction getDirection(MinecartGroup group, String text) {
		return getDirection(null, group, text);
	}
	
	private static Direction getDirection(MinecartMember member, MinecartGroup group, String text) {
		Direction dir = Direction.parse(text);
		if (dir != Direction.NONE) {
			int idx = text.indexOf(':');
			if (idx != -1) {
				text = text.substring(0, idx);
			}
			if (has(member, group, text)) {
				return dir;
			}
		}
		return Direction.NONE;
	}
	
	public static boolean has(MinecartMember member, String text) {
		return has(member, null, text);
	}
	
	public static boolean has(MinecartGroup group, String text) {
		return has(null, group, text);
	}
		
	private static boolean has(MinecartMember member, MinecartGroup group, String text) {
		boolean inv = false;
		while (text.startsWith("!")) {
			text = text.substring(1);
			inv = !inv;
		}
		if (text.isEmpty()) {
			return inv;
		}
		String lowerText = text.toLowerCase();
		int idx = lowerText.indexOf('@');
		String arrayText = idx == -1 ? null : lowerText.substring(0, idx);
		String[] array = idx == -1 ? null : parseArray(text.substring(idx + 1));
		for (Statement statement : statements) {
			if (statement.match(lowerText)) {
				if (member != null) {
					return statement.handle(member, text) != inv;
				} else if (group != null) {
					return statement.handle(group, text) != inv;
				}
			} else if (arrayText != null && statement.matchArray(arrayText)) {
				if (member != null) {
					return statement.handleArray(member, array) != inv;
				} else if (group != null) {
					return statement.handleArray(group, array) != inv;
				}
			}
		}
		return false;
	}
}
