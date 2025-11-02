package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.utils.BoundingRange;
import com.bergerkiller.bukkit.tc.utils.QuoteEscapedString;

/**
 * The key-value condition component of a selector query. Supports Minecraft
 * selector expressions such as ranges, as well as text wildcard matching
 * as used for tag and name matching.
 */
public class SelectorCondition {
    private final Key key;
    private final String value;

    @Deprecated
    protected SelectorCondition(String key, String value) {
        this(Key.parse(key), value);
    }

    protected SelectorCondition(Key key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Gets the key to which this selector value was bound
     *
     * @return key
     */
    public String getKey() {
        return this.key.name();
    }

    /**
     * Gets an additional path argument set on the key. If someone specifies
     * <code>selectorname.path.to.item</code>, this returns <code>path.to.item</code>
     *
     * @return Key path argument, or "" if not specified (omitted)
     */
    public String getKeyPath() {
        return this.key.path();
    }

    /**
     * Gets whether a {@link #getKeyPath() key path} was set for this selector.
     * If true, an extra argument is available to configure the selector with.
     *
     * @return True if a key path was set
     */
    public boolean hasKeyPath() {
        return !this.key.path().isEmpty();
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
     * Checks whether any of the given String values matches this selector
     * value expression. If this is an inversion expression, it inverts the entire
     * match, so !name means that 'name' is not contained in the collection.
     *
     * @param values Text values
     * @return True if any of the values match
     * @throws SelectorException If something is wrong with the expression
     */
    public boolean matchesAnyText(Collection<String> values) throws SelectorException {
        return values.contains(this.value);
    }

    /**
     * Checks whether any of the String values in the stream matches this selector
     * value expression. If this is an inversion expression, it inverts the entire
     * match, so !name means that 'name' is not contained in the stream.
     *
     * @param values
     * @return True if any of the values match
     * @throws SelectorException If something is wrong with the expression
     */
    public boolean matchesAnyText(Stream<String> values) throws SelectorException {
        return values.anyMatch(Predicate.isEqual(this.value));
    }

    /**
     * Checks whether the given String value matches this selector
     * value expression
     *
     * @param value Text value
     * @return True if the value matches
     * @throws SelectorException If something is wrong with the expression
     */
    public boolean matchesText(String value) throws SelectorException {
        return this.value.equals(value);
    }

    /**
     * Gets the bounding range of values specified. Throws a
     * SelectorException if this value expression does not denote a number.
     *
     * @return bounding range
     * @throws SelectorException
     */
    public BoundingRange getBoundingRange() throws SelectorException {
        throw new SelectorException(key + " value is not a number");
    }

    /**
     * Gets the argument value specified as a floating point value.
     * Throws a SelectorException if this value expression does not denote
     * a number, or specifies a range of numbers instead of a single number.
     *
     * @return value
     * @throws SelectorException
     */
    public double getDouble() throws SelectorException {
        BoundingRange range = this.getBoundingRange();
        if (range.isZeroLength()) {
            return range.getMin();
        } else {
            throw new SelectorException(key + " value is a range, expected a single number");
        }
    }

    /**
     * Gets the argument value specified as a boolean.
     * Throws a SelectorException if this value expression does not denote
     * a boolean, or specifies a range of numbers instead of a single number.
     *
     * @return boolean
     * @throws SelectorException
     */
    public boolean getBoolean() throws SelectorException {
        throw new SelectorException(key + " value is not a boolean");
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
     * Checks whether the given boolean matches this selector value
     * expression. This depends on how the selector was specified. It
     * supports number notation (condition=1 or condition=0), boolean
     * notation (condition=yes, condition=true, etc.) and no value
     * expression at all (condition or !condition).
     *
     * @param value Value to compare
     * @return True if the boolean matches
     */
    public boolean matchesBoolean(boolean value) throws SelectorException {
        throw new SelectorException(key + " value is not a boolean flag");
    }

    /**
     * Whether this selector condition represents a number or a range of
     * numbers.
     *
     * @return True if a number was specified
     */
    public boolean isNumber() {
        return false;
    }

    /**
     * Whether this selector condition represents a boolean. This can be true/false/yes/no,
     * or a number "1" or "0" only.
     *
     * @return True if a boolean was specified
     */
    public boolean isBoolean() {
        return false;
    }

    /**
     * Parses the value expression into its components so it can be used to
     * compare text and numbers with it.
     *
     * @param key The original key to which the value was bound
     * @param value Value to parse
     * @return Selector value representation of the input text value
     * @deprecated Use {@link Key} for key instead
     */
    public static SelectorCondition parse(String key, String value) {
        return parse(Key.parse(key), value);
    }

    /**
     * Parses the value expression into its components so it can be used to
     * compare text and numbers with it.
     *
     * @param key The original key to which the value was bound
     * @param value Value to parse
     * @return Selector value representation of the input text value
     */
    public static SelectorCondition parse(Key key, String value) {
        // Handle ! inversion, which only operates on the entire condition
        // This is so that expressions such as !1..5 works (checking outside of range)
        // The ! cannot be used for individual text expressions
        if (value.startsWith("!")) {
            SelectorCondition base = parse(key, value.substring(1));
            return new SelectorConditionInverted(key, value, base);
        }

        // Check for a range (multiple) of values, acts as an OR for text
        int rangeStart = QuoteEscapedString.unquotedIndexOf(value, "..", 0);
        if (rangeStart != -1) {
            // Check for more beyond rangeStart
            int rangeCurrent = QuoteEscapedString.unquotedIndexOf(value, "..", rangeStart + 2);
            if (rangeCurrent == -1) {
                // Check for a numeric range, which also handles text if needed
                String first = (rangeStart > 0) ? value.substring(0, rangeStart).trim() : null;
                String second = ((rangeStart + 2) < value.length()) ? value.substring(rangeStart + 2).trim() : null;
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
                    selectorValues.add(parsePart(key, value.substring(0, rangeStart).trim()));
                }
                if (rangeCurrent > (rangeStart + 2)) {
                    selectorValues.add(parsePart(key, value.substring(rangeStart + 2, rangeCurrent).trim()));
                }

                int rangeNext;
                while ((rangeNext = QuoteEscapedString.unquotedIndexOf(value, "..", rangeCurrent + 2)) != -1) {
                    if (rangeNext > (rangeCurrent + 2)) {
                        selectorValues.add(parsePart(key, value.substring(rangeCurrent, rangeNext).trim()));
                    }
                    rangeCurrent = rangeNext;
                }
                if ((rangeCurrent + 2) < value.length()) {
                    selectorValues.add(parsePart(key, value.substring(rangeCurrent + 2).trim()));
                }
                return new SelectorConditionAnyOfText(key, value, selectorValues.toArray(new SelectorCondition[0]));
            }
        }

        // Not a range of values, standard single-value parsing
        return parsePart(key, value.trim());
    }

    /**
     * Parses a text selector value. If wildcards are used, returns
     * a special type of selector value that can match multiple values.
     * Checks for valid numbers and returns a number-compatible condition
     * if one can be parsed.
     *
     * @param key Key (left of =)
     * @param value Value (right of =)
     * @return selector value
     */
    private static SelectorCondition parsePart(Key key, String value) {
        // Try to unescape a string value
        // This also makes it ineligible to be parsed as number / truthy
        QuoteEscapedString unescapedValue = QuoteEscapedString.tryParseQuoted(value);
        value = unescapedValue.getUnescaped();

        // Check for numbers
        if (!unescapedValue.isQuoteEscaped() && ParseUtil.isNumeric(value)) {
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

        // Truthy value
        if (!unescapedValue.isQuoteEscaped()) {
            SelectorConditionBoolean truthy = SelectorConditionBoolean.tryParse(key, value);
            if (truthy != null) {
                return truthy;
            }
        }

        // Normal text value, nothing special
        return new SelectorCondition(key, value);
    }

    /**
     * Tries to parse all conditions specified within a conditions string.
     * If parsing fails, returns null.
     *
     * @param conditionsString String of conditions to parse
     * @return Parsed conditions, or null if parsing failed (invalid syntax)
     */
    public static List<SelectorCondition> parseAll(String conditionsString) {
        int separator = QuoteEscapedString.unquotedIndexOf(conditionsString, ",", 0);
        final int length = conditionsString.length();
        if (separator == -1) {
            // A single condition provided
            // Parse as a singleton list, with an expected key=value syntax
            // Reject invalid matches such as value, =value and value=
            int equals = QuoteEscapedString.unquotedIndexOf(conditionsString, "=", 0);
            if (equals == -1 || equals == 0 || equals == (length-1)) {
                return null;
            }
            Key condKey = Key.parse(conditionsString.substring(0, equals));
            String condValue = conditionsString.substring(equals + 1);
            return Collections.singletonList(parse(condKey, condValue));
        } else {
            // Multiple conditions provided, build a hashmap with them
            List<SelectorCondition> conditions = new ArrayList<SelectorCondition>(10);
            int argStart = 0;
            int argEnd = separator;
            boolean valid = true;
            while (true) {
                int equals = QuoteEscapedString.unquotedIndexOf(conditionsString, "=", argStart);
                if (equals == -1 || equals == argStart || equals >= (argEnd-1)) {
                    valid = false;
                    break;
                }

                Key condKey = Key.parse(conditionsString.substring(argStart, equals));
                String condValue = conditionsString.substring(equals+1, argEnd);
                conditions.add(parse(condKey, condValue));

                // End of String
                if (argEnd == length) {
                    break;
                }

                // Find next separator. If none found, condition is until end of String.
                argStart = argEnd + 1;
                argEnd = QuoteEscapedString.unquotedIndexOf(conditionsString,",", argEnd + 1);
                if (argEnd == -1) {
                    argEnd = length;
                }
            }
            if (!valid) {
                return null;
            }
            return conditions;
        }
    }

    /**
     * Inverts the remainder of the selector value
     */
    public static class SelectorConditionInverted extends SelectorCondition {
        private final SelectorCondition base;

        @Deprecated
        public SelectorConditionInverted(String key, String value, SelectorCondition base) {
            this(Key.parse(key), value, base);
        }

        public SelectorConditionInverted(Key key, String value, SelectorCondition base) {
            super(key, value);
            this.base = base;
        }

        @Override
        public boolean matchesAnyText(Collection<String> values) throws SelectorException {
            return !base.matchesAnyText(values);
        }

        @Override
        public boolean matchesAnyText(Stream<String> values) throws SelectorException {
            return !base.matchesAnyText(values);
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            return !base.matchesText(value);
        }

        @Override
        public BoundingRange getBoundingRange() throws SelectorException {
            return base.getBoundingRange().invert();
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return !base.matchesNumber(value);
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return !base.matchesNumber(value);
        }

        @Override
        public boolean matchesBoolean(boolean value) throws SelectorException {
            return !base.matchesBoolean(value);
        }

        @Override
        public boolean isNumber() {
            return base.isNumber();
        }
    }

    /**
     * Selector for a singular numeric value
     */
    private static class SelectorConditionNumeric extends SelectorCondition {
        public static final SelectorConditionNumeric RANGE_MIN = new SelectorConditionNumeric(Key.of("NONE"), "", Double.NEGATIVE_INFINITY, Long.MIN_VALUE);
        public static final SelectorConditionNumeric RANGE_MAX = new SelectorConditionNumeric(Key.of("NONE"), "", Double.POSITIVE_INFINITY, Long.MAX_VALUE);
        public final double valueDouble;
        public final long valueLong;

        @Deprecated
        public SelectorConditionNumeric(String key, String value, double valueDouble, long valueLong) {
            this(Key.parse(key), value, valueDouble, valueLong);
        }

        public SelectorConditionNumeric(Key key, String value, double valueDouble, long valueLong) {
            super(key, value);
            this.valueDouble = valueDouble;
            this.valueLong = valueLong;
        }

        @Override
        public BoundingRange getBoundingRange() throws SelectorException {
            return BoundingRange.create(valueDouble, valueDouble);
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return value == valueDouble;
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return value == valueLong;
        }

        @Override
        public boolean matchesBoolean(boolean value) throws SelectorException {
            return getBoolean() == value;
        }

        @Override
        public boolean getBoolean() throws SelectorException {
            if (valueDouble == 0.0)
                return false;
            else if (valueDouble == 1.0)
                return true;
            else
                throw new SelectorException(getKey() + " value is not a boolean (0, 1, true, etc.)");
        }

        @Override
        public boolean isNumber() {
            return true;
        }

        @Override
        public boolean isBoolean() {
            // Must be exact, if someone specifies "0.1" it's not a boolean anymore
            return valueDouble == 0.0 || valueDouble == 1.0;
        }

        @Deprecated
        public static SelectorConditionNumeric tryParse(String key, String value) {
            return tryParse(Key.parse(key), value);
        }

        public static SelectorConditionNumeric tryParse(Key key, String value) {
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

        @Deprecated
        public SelectorConditionNumericRange(String key, String value, SelectorConditionNumeric min, SelectorConditionNumeric max) {
            this(Key.parse(key), value, min, max);
        }

        public SelectorConditionNumericRange(Key key, String value, SelectorConditionNumeric min, SelectorConditionNumeric max) {
            super(key, value);
            if (min.valueDouble > max.valueDouble) {
                this.min = max;
                this.max = min;
            } else {
                this.min = min;
                this.max = max;
            }
        }

        @Override
        public boolean matchesAnyText(Collection<String> values) throws SelectorException {
            return min.matchesAnyText(values) || max.matchesAnyText(values);
        }

        @Override
        public boolean matchesAnyText(Stream<String> values) throws SelectorException {
            Collection<String> tmp = values.collect(Collectors.toList());
            return min.matchesAnyText(tmp) || max.matchesAnyText(tmp);
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            return min.matchesText(value) || max.matchesText(value);
        }

        @Override
        public BoundingRange getBoundingRange() throws SelectorException {
            return BoundingRange.create(min.valueDouble, max.valueDouble);
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return value >= min.valueDouble && value <= max.valueDouble;
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return value >= min.valueLong && value <= max.valueLong;
        }

        @Override
        public boolean isNumber() {
            return true;
        }
    }

    /**
     * Selector for a text value that includes wildcards (*)
     */
    public static class SelectorConditionWildcardText extends SelectorCondition {
        private final String[] elements;
        private final boolean firstAny;
        private final boolean lastAny;

        @Deprecated
        public SelectorConditionWildcardText(String key, String value, String[] elements, boolean firstAny, boolean lastAny) {
            this(Key.parse(key), value, elements, firstAny, lastAny);
        }

        public SelectorConditionWildcardText(Key key, String value, String[] elements, boolean firstAny, boolean lastAny) {
            super(key, value);
            this.elements = elements;
            this.firstAny = firstAny;
            this.lastAny = lastAny;
        }

        @Override
        public boolean matchesAnyText(Collection<String> values) throws SelectorException {
            for (String value : values) {
                if (matchesText(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean matchesAnyText(Stream<String> values) throws SelectorException {
            return values.anyMatch(this::matchesText);
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

        @Deprecated
        public SelectorConditionAnyOfText(String key, String value, SelectorCondition... selectorValues) {
            this(Key.parse(key), value, selectorValues);
        }

        public SelectorConditionAnyOfText(Key key, String value, SelectorCondition... selectorValues) {
            super(key, value);
            this.selectorValues = selectorValues;
        }

        @Override
        public boolean matchesAnyText(Collection<String> values) throws SelectorException {
            for (SelectorCondition selectorValue : selectorValues) {
                if (selectorValue.matchesAnyText(values)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean matchesAnyText(Stream<String> values) throws SelectorException {
            //TODO: Could alternatively collect to a List and use matchesAnyText on that instead
            return values.anyMatch(s -> {
                for (SelectorCondition selectorValue : selectorValues) {
                    if (selectorValue.matchesText(s)) {
                        return true;
                    }
                }
                return false;
            });
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
        public BoundingRange getBoundingRange() throws SelectorException {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (SelectorCondition selectorValue : selectorValues) {
                double value = selectorValue.getBoundingRange().getMin(); // always 1 value
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }
            return BoundingRange.create(min, max);
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

    /**
     * A text expression that can indicate text, or a true/false condition
     */
    private static class SelectorConditionBoolean extends SelectorCondition {
        private static final Map<String, Boolean> booleanConstants = new HashMap<>();
        static {
            register("yes", Boolean.TRUE);
            register("true", Boolean.TRUE);
            register("no", Boolean.FALSE);
            register("false", Boolean.FALSE);
        }

        private static void register(String key, Boolean value) {
            booleanConstants.put(key, value); // true
            booleanConstants.put(key.toLowerCase(Locale.ENGLISH), value); // TRUE
            booleanConstants.put(key.substring(0, 1).toUpperCase(Locale.ENGLISH) +
                    key.substring(1), value); // True
        }

        private final boolean booleanValue;

        protected SelectorConditionBoolean(Key key, String value, boolean booleanValue) {
            super(key, value);
            this.booleanValue = booleanValue;
        }

        @Override
        public boolean isBoolean() {
            return true;
        }

        @Override
        public boolean matchesBoolean(boolean value) throws SelectorException {
            return value == booleanValue;
        }

        @Override
        public boolean getBoolean() throws SelectorException {
            return booleanValue;
        }

        @Deprecated
        public static SelectorConditionBoolean tryParse(String key, String value) {
            return tryParse(Key.parse(key), value);
        }

        public static SelectorConditionBoolean tryParse(Key key, String value) {
            Boolean truthy = booleanConstants.get(value);
            return (truthy == null) ? null : new SelectorConditionBoolean(key, value, truthy.booleanValue());
        }
    }

    /**
     * Key of a selector condition. Consists of a String key name,
     * and an optional path parameter (after the first dot).
     */
    public static final class Key {
        private final String name;
        private final String path;

        /**
         * Parses the key String format into key + path (if path is specified)
         *
         * @param keyStr Full key String
         * @return Parsed Key
         */
        public static Key parse(String keyStr) {
            // Trim whitespace left/right. Keep it within quotes if any.
            keyStr = keyStr.trim();

            // Try to unescape a string key
            QuoteEscapedString unescapedKey = QuoteEscapedString.tryParseQuoted(keyStr);
            keyStr = unescapedKey.getUnescaped();

            // Try to identify a path argument in the key (key.path)
            String keyPathStr = "";
            {
                int keyPathStart = QuoteEscapedString.unquotedIndexOf(keyStr, ".", 0);
                if (keyPathStart != -1) {
                    keyPathStr = keyStr.substring(keyPathStart + 1);
                    keyStr = keyStr.substring(0, keyPathStart);
                    if (!unescapedKey.isQuoteEscaped()) {
                        keyStr = QuoteEscapedString.tryParseQuoted(keyStr.trim()).getUnescaped();
                    }
                    if (!unescapedKey.isQuoteEscaped()) {
                        keyPathStr = QuoteEscapedString.tryParseQuoted(keyPathStr.trim()).getUnescaped();
                    }
                }
            }

            return of(keyStr, keyPathStr);
        }

        public static Key of(String name) {
            return new Key(name);
        }

        public static Key of(String name, String path) {
            return new Key(name, path);
        }

        private Key(String name) {
            this(name, "");
        }

        private Key(String name, String path) {
            this.name = name;
            this.path = path;
        }

        public String name() {
            return name;
        }

        public String path() {
            return path;
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + path.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Key) {
                Key other = (Key) o;
                return this.name.equals(other.name) && this.path.equals(other.path);
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            if (path.isEmpty()) {
                return "Key{name=" + name + "}";
            } else {
                return "Key{name=" + name + ", path=" + path + "}";
            }
        }
    }
}
