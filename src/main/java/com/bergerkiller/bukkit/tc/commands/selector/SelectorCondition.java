package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Util;

/**
 * The key-value condition component of a selector query. Supports Minecraft
 * selector expressions such as ranges, as well as text wildcard matching
 * as used for tag and name matching.
 */
public class SelectorCondition {
    private final String key;
    private final String value;

    protected SelectorCondition(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Gets the key to which this selector value was bound
     *
     * @return key
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Gets the full value expression as provided by the user
     *
     * @return value
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Checks whether the given String value matches this selector
     * value expression
     *
     * @param value Text value
     * @return True if the value matches
     */
    public boolean matchesText(String value) throws SelectorException {
        return this.value.equals(value);
    }

    /**
     * Checks whether the given number value matches this selector
     * value expression. Throws a SelectorException if this value expression
     * does not denote a number.
     *
     * @param value Value to compare
     * @return True if the value matches the number
     */
    public boolean matchesNumber(double value) throws SelectorException {
        throw new SelectorException(key + " value is not a number");
    }

    /**
     * Checks whether the given number value matches this selector
     * value expression. Throws a SelectorException if this value expression
     * does not denote a number.
     *
     * @param value Value to compare
     * @return True if the value matches the number
     */
    public boolean matchesNumber(long value) throws SelectorException {
        throw new SelectorException(key + " value is not a number");
    }

    /**
     * Parses the value expression into its components so it can be used to
     * compare text and numbers with it.
     *
     * @param key The original key to which the value was bound
     * @param value Value to parse
     * @return Selector value representation of the input text value
     */
    public static SelectorCondition parse(String key, String value) {
        // Handle ! inversion, which only operates on the entire condition
        // This is so that expressions such as !1..5 works (checking outside of range)
        // The ! cannot be used for individual text expressions
        if (value.startsWith("!")) {
            return new SelectorConditionInverted(key, value, parse(key, value.substring(1)));
        }

        // Check for a range (multiple) of values, acts as an OR for text
        int rangeStart = value.indexOf("..");
        if (rangeStart != -1) {
            // Check for more beyond rangeStart
            int rangeCurrent = value.indexOf("..", rangeStart + 2);
            if (rangeCurrent == -1) {
                // Check for a numeric range, which also handles text if needed
                String first = (rangeStart > 0) ? value.substring(0, rangeStart) : null;
                String second = ((rangeStart + 2) < value.length()) ? value.substring(rangeStart + 2) : null;
                SelectorConditionNumeric min = (first != null)
                        ? SelectorConditionNumeric.tryParse(key, first)
                        : SelectorConditionNumeric.RANGE_MIN;
                SelectorConditionNumeric max = (second != null)
                        ? SelectorConditionNumeric.tryParse(key, second)
                        : SelectorConditionNumeric.RANGE_MAX;
                if (min != null && max != null) {
                    return new SelectorConditionNumericRange(key, value, min, max);
                }

                // List of 2 text values
                return new SelectorConditionAnyOfText(key, value, parsePart(key, first), parsePart(key, second));
            } else {
                // Collect a list of values
                List<SelectorCondition> selectorValues = new ArrayList<SelectorCondition>(5);
                if (rangeStart > 0) {
                    selectorValues.add(parsePart(key, value.substring(0, rangeStart)));
                }
                if (rangeCurrent > (rangeStart + 2)) {
                    selectorValues.add(parsePart(key, value.substring(rangeStart + 2, rangeCurrent)));
                }

                int rangeNext;
                while ((rangeNext = value.indexOf("..", rangeCurrent + 2)) != -1) {
                    if (rangeNext > (rangeCurrent + 2)) {
                        selectorValues.add(parsePart(key, value.substring(rangeCurrent, rangeNext)));
                    }
                    rangeCurrent = rangeNext;
                }
                if ((rangeCurrent + 2) < value.length()) {
                    selectorValues.add(parsePart(key, value.substring(rangeCurrent + 2)));
                }
                return new SelectorConditionAnyOfText(key, value, selectorValues.toArray(new SelectorCondition[selectorValues.size()]));
            }
        }

        // Not a range of values, standard single-value parsing
        return parsePart(key, value);
    }

    /**
     * Parses a text selector value. If wildcards are used, returns
     * a special type of selector value that can match multiple values.
     * Checks for valid numbers and returns a number-compatible condition
     * if one can be parsed.
     *
     * @param key
     * @param value
     * @return selector value
     */
    private static SelectorCondition parsePart(String key, String value) {
        // Check for numbers
        if (ParseUtil.isNumeric(value)) {
            SelectorConditionNumeric numeric = SelectorConditionNumeric.tryParse(key, value);
            if (numeric != null) {
                return numeric;
            }
        }

        // Check for text with wildcards (*)
        final String[] elements = value.split("\\*", -1);
        if (elements.length > 1) {
            boolean firstAny = value.startsWith("*");
            boolean lastAny = value.endsWith("*");
            return new SelectorConditionWildcardText(key, value, elements, firstAny, lastAny);
        }

        // Normal text value, nothing special
        return new SelectorCondition(key, value);
    }

    /**
     * Inverts the remainder of the selector value
     */
    public static class SelectorConditionInverted extends SelectorCondition {
        private final SelectorCondition base;

        public SelectorConditionInverted(String key, String value, SelectorCondition base) {
            super(key, value);
            this.base = base;
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            return !base.matchesText(value);
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return !base.matchesNumber(value);
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return !base.matchesNumber(value);
        }
    }

    /**
     * Selector for a singular numeric value
     */
    private static class SelectorConditionNumeric extends SelectorCondition {
        public static final SelectorConditionNumeric RANGE_MIN = new SelectorConditionNumeric("NONE", "", Double.NEGATIVE_INFINITY, Long.MIN_VALUE);
        public static final SelectorConditionNumeric RANGE_MAX = new SelectorConditionNumeric("NONE", "", Double.POSITIVE_INFINITY, Long.MAX_VALUE);
        public final double valueDouble;
        public final long valueLong;

        public SelectorConditionNumeric(String key, String value, double valueDouble, long valueLong) {
            super(key, value);
            this.valueDouble = valueDouble;
            this.valueLong = valueLong;
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return value == valueDouble;
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return value == valueLong;
        }

        public static SelectorConditionNumeric tryParse(String key, String value) {
            double valueDouble = ParseUtil.parseDouble(value, Double.NaN);
            if (!Double.isNaN(valueDouble)) {
                long valueLong = ParseUtil.parseLong(value, 0L);
                return new SelectorConditionNumeric(key, value, valueDouble, valueLong);
            }
            return null;
        }
    }

    /**
     * Selector for a range min..max of numeric values
     */
    private static class SelectorConditionNumericRange extends SelectorCondition {
        private final SelectorConditionNumeric min, max;

        public SelectorConditionNumericRange(String key, String value, SelectorConditionNumeric min, SelectorConditionNumeric max) {
            super(key, value);
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            return min.matchesText(value) || max.matchesText(value);
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return value >= min.valueDouble && value <= max.valueDouble;
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return value >= min.valueLong && value <= max.valueLong;
        }
    }

    /**
     * Selector for a text value that includes wildcards (*)
     */
    public static class SelectorConditionWildcardText extends SelectorCondition {
        private final String[] elements;
        private final boolean firstAny;
        private final boolean lastAny;

        public SelectorConditionWildcardText(String key, String value, String[] elements, boolean firstAny, boolean lastAny) {
            super(key, value);
            this.elements = elements;
            this.firstAny = firstAny;
            this.lastAny = lastAny;
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            return Util.matchText(value, this.elements, this.firstAny, this.lastAny);
        }
    }

    /**
     * Simple any-of for a list of selector values to check against
     */
    private static class SelectorConditionAnyOfText extends SelectorCondition {
        private final SelectorCondition[] selectorValues;

        public SelectorConditionAnyOfText(String key, String value, SelectorCondition... selectorValues) {
            super(key, value);
            this.selectorValues = selectorValues;
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            for (SelectorCondition selectorValue : selectorValues) {
                if (selectorValue.matchesText(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            for (SelectorCondition selectorValue : selectorValues) {
                if (selectorValue.matchesNumber(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            for (SelectorCondition selectorValue : selectorValues) {
                if (selectorValue.matchesNumber(value)) {
                    return true;
                }
            }
            return false;
        }
    }
}
