package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Statement {
    private static List<Statement> statements = new ArrayList<>();

    public static String[] parseArray(String text) {
        return text.split(";", -1);
    }

    public static void init() {
        // Note: For same priority(), evaluated from bottom to top
        //       Late-registered statements evaluate before early ones.
        register(new StatementDestination());
        register(new StatementBoolean());
        register(new StatementRandom());
        register(new StatementProperty());
        register(new StatementName());
        register(new StatementEmpty());
        register(new StatementPassenger());
        register(new StatementOwners());
        register(new StatementTrainItems());
        register(new StatementFuel());
        register(new StatementType());
        register(new StatementVelocity());
        register(new StatementPlayerItems());
        register(new StatementPlayerHand());
        register(new StatementMob());
        register(new StatementRedstone());
        register(new StatementPermission());
        register(new StatementDirection());
        register(new StatementTag());
    }

    public static void deinit() {
        statements.clear();
    }

    public static <T extends Statement> T register(T statement) {
        int index = Collections.binarySearch(statements, statement,
                (a, b) -> Integer.compare(b.priority(), a.priority()));
        if (index < 0) index = ~index;

        // Make sure that if priority is the same, an item is inserted at the beginning
        // This allows third parties to register new statements without specifying priority,
        // overriding existing statements that start with the same name.
        {
            int itemPriority = statement.priority();
            while (index > 0 && statements.get(index - 1).priority() == itemPriority) {
                index--;
            }
        }

        statements.add(index, statement);
        return statement;
    }

    public static boolean has(MinecartMember<?> member, String text, SignActionEvent event) {
        return has(member, null, text, event);
    }

    public static boolean has(MinecartGroup group, String text, SignActionEvent event) {
        return has(null, group, text, event);
    }

    /**
     * Gets if the member or group has the statement specified.
     * If both member and group are null, then only statements that require no train
     * will function. Statements that do will return false.
     *
     * @param member to use, or null to use group
     * @param group  to use, or null to use member
     * @param text   to evaluate
     * @param event  to parse
     * @return True if successful, False if not
     */
    public static boolean has(MinecartMember<?> member, MinecartGroup group, String text, SignActionEvent event) {
        boolean inv = false;
        text = TCConfig.statementShortcuts.replace(text);
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
            if (event == null && statement.requiredEvent()) {
                continue;
            }
            if (arrayText != null && statement.matchArray(arrayText)) {
                if (member != null) {
                    return statement.handleArray(member, array, event) != inv;
                } else if (group != null) {
                    return statement.handleArray(group, array, event) != inv;
                } else if (!statement.requiresTrain()) {
                    return statement.handleArray((MinecartMember<?>) null, array, event) != inv;
                }
            } else if (statement.match(lowerText)) {
                if (member != null) {
                    return statement.handle(member, text, event) != inv;
                } else if (group != null) {
                    return statement.handle(group, text, event) != inv;
                } else if (!statement.requiresTrain()) {
                    return statement.handle((MinecartMember<?>) null, text, event) != inv;
                }
            }
        }
        return inv;
    }

    public static boolean hasMultiple(MinecartMember<?> member, Iterable<String> statementTexts, SignActionEvent event) {
        return hasMultiple(member, null, statementTexts, event);
    }

    public static boolean hasMultiple(MinecartGroup group, Iterable<String> statementTexts, SignActionEvent event) {
        return hasMultiple(null, group, statementTexts, event);
    }

    /**
     * Gets if the member or group has multiple statements as specified.
     * If both member and group are null, then only statements that require no train
     * will function. Statements that do will return false.<br>
     * <br>
     * Empty statements are ignored. Statements preceeding with & use AND-logic
     * with all the statements prior, and statements preceeding with | use OR-logic.
     * Others default to AND.
     *
     * @param member to use, or null to use group
     * @param group  to use, or null to use member
     * @param text   to evaluate
     * @param event  to parse
     * @return True if successful, False if not
     */
    public static boolean hasMultiple(MinecartMember<?> member, MinecartGroup group, Iterable<String> statementTexts, SignActionEvent event) {
        boolean match = true;
        for (String statementText : statementTexts) {
            if (!statementText.isEmpty()) {
                boolean isLogicAnd = true;
                if (statementText.startsWith("&")) {
                    isLogicAnd = true;
                    statementText = statementText.substring(1);
                } else if (statementText.startsWith("|")) {
                    isLogicAnd = false;
                    statementText = statementText.substring(1);
                }
                boolean result = Statement.has(member, group, statementText, event);
                if (isLogicAnd) {
                    match &= result;
                } else {
                    match |= result;
                }
            }
        }
        return match;
    }

    /**
     * Checks if this statement matches the given text
     * The given text is lower cased.
     *
     * @param text to use
     * @return if it matches and can handle it
     */
    public abstract boolean match(String text);

    /**
     * Checks if this statement matches the given text
     * The given text is lower cased.
     * The text is the pre-text of '@'
     *
     * @param text to use
     * @return if it matches and can handle an array
     */
    public abstract boolean matchArray(String text);

    /**
     * Whether a MinecartMember or MinecartGroup is required for this statement to operate.
     * Some statements also work without using the train, and can therefore return false here.
     * If this method returns false, then <i>handle</i> can be called with null as group/member argument.<br>
     * <br>
     * <b>Default: true</b>
     * 
     * @return True if a train is required
     */
    public boolean requiresTrain() {
        return true;
    }

    /**
     * Whether a SignActionEvent is required for this statement to operate.
     * Some statements also work without using the event, and can therefore return false here.
     * If this method returns false, then <i>handle</i> can be called with null as event argument.<br>
     * <br>
     * <b>Default: false</b>
     * 
     * @return True if an event is required
     */
    public boolean requiredEvent() {
        return false;
    }

    /**
     * Defines the priority of this statement. Use this to have statements
     * match before or after other statements. Default is 0. A negative value
     * will make it match last, a positive value will have it match first.
     * 
     * @return priority
     */
    public int priority() {
        return 0;
    }

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
}
