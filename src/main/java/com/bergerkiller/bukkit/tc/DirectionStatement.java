package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.statements.Statement;

import java.util.Locale;

public class DirectionStatement {
    public final String directionFrom;
    public String direction, text;
    public Counter counter;

    public DirectionStatement(String text, String defaultDirection) {
        int idx = text.indexOf(':');
        if (idx == -1) {
            this.text = text;
            this.direction = defaultDirection;
        } else {
            this.text = text.substring(idx + 1);
            this.direction = text.substring(0, idx);
        }
        if (this.text.isEmpty()) this.text = "default";

        // Using - between two directions to denote from and to directions
        // When not used, it switches from the direction the minecart came ('self')
        idx = this.direction.indexOf('-');
        if (idx == -1) this.directionFrom = "self";
        else {
            this.directionFrom = this.direction.substring(0, idx);
            this.direction = this.direction.substring(idx + 1);
        }

        // Number (counter) statements
        if (this.text.endsWith("%"))
            try {
                this.counter = new CounterPercentage(Double.parseDouble(this.text.substring(0, this.text.length() - 1)));
            } catch (NumberFormatException ex) {
                this.counter = null;
            }
        else
            try {
                this.counter = new CounterAbsolute(Integer.parseInt(this.text));
            } catch (NumberFormatException ex) {
                this.counter = null;
            }
    }

    public boolean has(SignActionEvent event, MinecartMember<?> member) {
        return Statement.has(member, this.text, event);
    }

    public boolean has(SignActionEvent event, MinecartGroup group) {
        return Statement.has(group, this.text, event);
    }

    /**
     * Whether this switcher sign switches from the 'self' direction, that is,
     * the direction from which the train entered the switcher sign.
     *
     * @return True if switched from the train's direction
     */
    public boolean isSwitchedFromSelf() {
        return this.directionFrom.equals("self");
    }

    public boolean hasCounter() {
        return this.counter != null;
    }

    /**
     * Gets whether this statement is a default rule.
     * This means this direction should be chosen if no other statements match.
     *
     * @return True if this is a default rule
     */
    public boolean isDefault() {
        final String str = this.text.toLowerCase(Locale.ENGLISH);
        return str.equals("def") || str.equals("default");
    }

    @Override
    public String toString() {
        return "{from=" + this.directionFrom + " to=" + this.direction +
                (!hasCounter() ? " when " + this.text : " every " + this.counter)
                + "}";
    }

    /**
     * Used when a counter statement is defined
     */
    public static interface Counter {
        /**
         * Gets the current counter value
         *
         * @param trainSize Total size of the train. Used when the counter
         *                  is declared as a percentage of the size.
         * @return counter value
         */
        int get(int trainSize);
    }

    private static final class CounterAbsolute implements Counter {
        private final int value;

        public CounterAbsolute(int value) {
            this.value = value;
        }

        @Override
        public int get(int trainSize) {
            return value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    private static final class CounterPercentage implements Counter {
        private final double theta;

        public CounterPercentage(double percentage) {
            this.theta = percentage / 100.0;
        }

        @Override
        public int get(int trainSize) {
            return MathUtil.ceil(this.theta * trainSize);
        }

        @Override
        public String toString() {
            return (this.theta * 100.0) + "%";
        }
    }
}
