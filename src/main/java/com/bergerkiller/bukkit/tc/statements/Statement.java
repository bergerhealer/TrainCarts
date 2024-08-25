package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Statement {
    private static final List<Statement> statements = new ArrayList<>();

    public static String[] parseArray(String text) {
        return text.split(";", -1);
    }

    public static void init() {
        // Note: For same priority(), evaluated from bottom to top
        //       Late-registered statements evaluate before early ones.
        register(new StatementDestination());
        register(StatementBoolean.INSTANCE);
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
        return Matcher.of(text).withMember(member).withGroup(group).withSignEvent(event).match().has();
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
     * Empty statements are ignored. Statements preceeding with &amp; use AND-logic
     * with all the statements prior, and statements preceeding with | use OR-logic.
     * Others default to AND.
     *
     * @param member MinecartMember to use for context, or null to use group
     * @param group MinecartGroup to use for context, or null to use member
     * @param statementTexts Statement rules to evaluate
     * @param event Event defining the sign involved in this check
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
     * Whether this statement evaluates in a constant fashion. If true, then this statement does
     * not rely on world, cart, train or changing random information to evaluate it.
     * Constant statements behave differently with the path finding algorithm, where it will
     * use it as a path route instead of registering the switcher sign as a node.<br>
     * <br>
     * Enter direction is considered a constant, too.
     *
     * @return True if this statement evaluates as a constant. Default false.
     */
    public boolean isConstant() {
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

    /**
     * Called before handle() is called to check all required context is available.
     * By default, checks {@link #requiredEvent()} and {@link #requiresTrain()},
     * but can be tweaked for more specific checks.
     *
     * @param member Member, null if not available
     * @param group Group, null if not available
     * @param event Sign Event information, null if not available
     * @return True if all required context is available
     */
    public boolean hasRequiredContext(MinecartMember<?> member, MinecartGroup group, SignActionEvent event) {
        if (member == null && group == null && requiresTrain()) {
            return false;
        }
        if (event == null && requiredEvent()) {
            return false;
        }
        return true;
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

    /**
     * Matches input text to find the statement and evaluate it against a group, member and/or
     * with sign context information.
     */
    public static class Matcher {
        private final String text;
        private MinecartGroup group;
        private MinecartMember<?> member;
        private SignActionEvent signEvent;

        private Matcher(String text) {
            this.text = text;
        }

        public static Matcher of(String text) {
            return new Matcher(text);
        }

        public Matcher withGroup(MinecartGroup group) {
            this.group = group;
            return this;
        }

        public Matcher withMember(MinecartMember<?> member) {
            this.member = member;
            return this;
        }

        public Matcher withSignEvent(SignActionEvent event) {
            this.signEvent = event;
            return this;
        }

        /**
         * Matches the input text against a compatible statement and returns
         * the result of whether the condition is True.
         *
         * @return Match result, or Empty if no statement matched
         */
        public MatchResult match() {
            boolean inv = false;
            String text = TCConfig.statementShortcuts.replace(this.text);
            while (!text.isEmpty() && text.charAt(0) == '!') {
                text = text.substring(1);
                inv = !inv;
            }
            if (text.isEmpty()) {
                return MatchResult.create(StatementBoolean.EMPTY, false, inv);
            }

            String lowerText = text.toLowerCase();
            int idx = lowerText.indexOf('@');
            String arrayText = idx == -1 ? null : lowerText.substring(0, idx);
            String[] array = idx == -1 ? null : parseArray(text.substring(idx + 1));
            for (Statement statement : statements) {
                if (arrayText != null && statement.matchArray(arrayText)) {
                    if (!statement.hasRequiredContext(member, group, signEvent)) {
                        return MatchResult.createWithMissingContext(statement, true, inv);
                    } else if (member != null) {
                        return MatchResult.create(statement, true, statement.handleArray(member, array, signEvent) != inv);
                    } else if (group != null) {
                        return MatchResult.create(statement, true, statement.handleArray(group, array, signEvent) != inv);
                    } else {
                        return MatchResult.create(statement, true, statement.handleArray((MinecartMember<?>) null, array, signEvent) != inv);
                    }
                } else if (statement.match(lowerText)) {
                    if (!statement.hasRequiredContext(member, group, signEvent)) {
                        return MatchResult.createWithMissingContext(statement, false, inv);
                    } else if (member != null) {
                        return MatchResult.create(statement, false, statement.handle(member, text, signEvent) != inv);
                    } else if (group != null) {
                        return MatchResult.create(statement, false, statement.handle(group, text, signEvent) != inv);
                    } else {
                        return MatchResult.create(statement, false, statement.handle((MinecartMember<?>) null, text, signEvent) != inv);
                    }
                }
            }

            // Note: this never gets reached in practise because StatementTag (at the end) match() always evaluates true.
            // Something is put here just to avoid unexpected behavior
            return MatchResult.createWithMissingContext(StatementBoolean.EMPTY, false, inv);
        }
    }

    /**
     * The result of looking up a statement and matching it against the cart, train or sign
     */
    public static class MatchResult {
        private final Statement statement;
        private final boolean isArray;
        private final boolean isMissingContext;
        private final boolean has;

        public static MatchResult create(Statement statement, boolean isArray, boolean has) {
            return new MatchResult(statement, isArray, false, has);
        }

        public static MatchResult createWithMissingContext(Statement statement, boolean isArray, boolean inv) {
            return new MatchResult(statement, isArray, true, inv);
        }

        private MatchResult(Statement statement, boolean isArray, boolean isMissingContext, boolean has) {
            this.statement = statement;
            this.isArray = isArray;
            this.isMissingContext = isMissingContext;
            this.has = has;
        }

        /**
         * Gets the statement instance that was matched and this result is for
         *
         * @return Statement, never null
         */
        public Statement statement() {
            return statement;
        }

        /**
         * Gets whether the statement evaluated true
         *
         * @return True if the statement evaluated
         */
        public boolean has() {
            return has;
        }

        /**
         * Whether context such as a sign, train info or cart info was missing when evaluating
         * the statement.
         *
         * @return True if context was missing that was required, and false was assumed
         */
        public boolean isMissingContext() {
            return isMissingContext;
        }

        /**
         * Gets whether the statement matched using the array @ syntax
         *
         * @return True if array
         */
        public boolean isArray() {
            return isArray;
        }

        /**
         * Whether the statement is considered constant
         *
         * @return True if constant
         * @see Statement#isConstant()
         */
        public boolean isConstant() {
            return statement.isConstant();
        }

        /**
         * Gets whether this result actually matched a statement,
         * or that a fallback result was produced using the Tag fallback,
         * or false assumed because of missing context.
         *
         * @return True if a result was actually matched. For tag statements this requires
         *         use of t@.
         */
        public boolean isExactMatch() {
            // Empty text encountered
            if (statement == StatementBoolean.EMPTY) {
                return false;
            }

            // Is not a statement tag, or it is but the tag was done with t@
            return !(this.statement instanceof StatementTag) || this.isArray;
        }
    }
}
