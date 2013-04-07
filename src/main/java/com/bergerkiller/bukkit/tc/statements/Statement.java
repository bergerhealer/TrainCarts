package com.bergerkiller.bukkit.tc.statements;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

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

	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		for (MinecartMember<?> member : group) {
			if (this.handle(member, text, event)) {
				return true;
			}
		}
		return false;
	}

	public boolean handleArray(MinecartGroup group, String[] text, SignActionEvent event) {
		for (MinecartMember<?> member : group) {
			if (this.handleArray(member, text, event)) {
				return true;
			}
		}
		return false;
	}

	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		return false;
	}

	public boolean handleArray(MinecartMember<?> member, String[] text, SignActionEvent event) {
		return false;
	}

	public static String[] parseArray(String text) {
		return text.split(";", -1);
	}

	private static List<Statement> statements = new ArrayList<Statement>();

	public static void init() {
		register(new StatementDestination());
		register(new StatementBoolean());
		register(new StatementRandom());
		register(new StatementProperty());
		register(new StatementName());
		register(new StatementEmpty());
		register(new StatementPassenger());
		register(new StatementOwners());
		register(new StatementItems());
		register(new StatementFuel());
		register(new StatementType());
		register(new StatementVelocity());
		register(new StatementPlayerItems());
		register(new StatementPlayerHand());
		register(new StatementMob());
		register(new StatementRedstone());
		register(new StatementPermission());
		register(new StatementDirection());
		register(new StatementTag()); //register lastly!
	}

	public static void deinit() {
		statements.clear();
	}

	public static <T extends Statement> T register(T statement) {
		statements.add(statement);
		return statement;
	}

	public static boolean has(MinecartMember<?> member, String text, SignActionEvent event) {
		return has(member, null, text, event);
	}

	public static boolean has(MinecartGroup group, String text, SignActionEvent event) {
		return has(null, group, text, event);
	}

	/**
	 * Gets if the member or group has the statement specified
	 * 
	 * @param member to use, or null to use group
	 * @param group to use, or null to use member
	 * @param text to evaluate
	 * @param event to parse
	 * @return True if successful, False if not
	 */
	public static boolean has(MinecartMember<?> member, MinecartGroup group, String text, SignActionEvent event) {
		boolean inv = false;
		text = TrainCarts.statementShortcuts.replace(text);
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
					return statement.handle(member, text, event) != inv;
				} else if (group != null) {
					return statement.handle(group, text, event) != inv;
				}
			} else if (arrayText != null && statement.matchArray(arrayText)) {
				if (member != null) {
					return statement.handleArray(member, array, event) != inv;
				} else if (group != null) {
					return statement.handleArray(group, array, event) != inv;
				}
			}
		}
		return inv;
	}
}
