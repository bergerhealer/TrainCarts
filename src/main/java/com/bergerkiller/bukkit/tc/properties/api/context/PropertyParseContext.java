package com.bergerkiller.bukkit.tc.properties.api.context;

import java.util.regex.MatchResult;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.statements.Statement;

/**
 * Argument passed to {@link PropertyParser} annotated methods
 * to perform parsing. Instead of this class a String
 * class can be used as well.
 * 
 * @param <T> Type of value to parse
 */
public final class PropertyParseContext<T> extends PropertyContext {
    private final T current;
    private final String name;
    private final PropertyInputContext input;
    private final MatchResult matchResult;

    public PropertyParseContext(TrainCarts traincarts, IProperties properties, T current, String name, PropertyInputContext input, MatchResult matchResult) {
        super(traincarts, properties);
        this.current = current;
        this.name = name;
        this.input = input;
        this.matchResult = matchResult;
    }

    /**
     * Gets the full name of the property that was matched
     * using the regex pattern defined by {@link PropertyParser#value()}.
     * 
     * @return property name
     */
    public String name() {
        return this.name;
    }

    /**
     * Retrieves a {@link #name()} capture group matched from the regex pattern
     * defined by {@link PropertyParser#value()}.
     * 
     * @param index Index of the capture group to return
     * @return Value of the capture group, or an empty String if out of range
     */
    public String nameGroup(int index) {
        if (index >= 0 && index <= this.matchResult.groupCount()) {
            return this.matchResult.group(index);
        } else {
            return "";
        }
    }

    /**
     * Gets the input value that is being parsed
     * 
     * @return value String
     */
    public String input() {
        return this.input.input();
    }

    /**
     * Gets the input value and all contextual information around it
     *
     * @return Input context
     */
    public PropertyInputContext inputContext() {
        return this.input;
    }

    /**
     * Parses the input text as a numeric float value.
     * 
     * @return parsed float value
     * @throws PropertyInvalidInputException if the input is not a number
     */
    public float inputFloat() {
        float result = ParseUtil.parseFloat(input(), Float.NaN);
        if (Float.isNaN(result)) {
            throw new PropertyInvalidInputException("Not a number");
        }
        return result;
    }

    /**
     * Parses the input text as a numeric float value. Non-numeric
     * or "none" makes this method return NaN instead.
     * 
     * @return parsed float value, or NaN
     */
    public float inputFloatOrNaN() {
        if (input().equalsIgnoreCase("none")) {
            return Float.NaN;
        }
        return ParseUtil.parseFloat(input(), Float.NaN);
    }

    /**
     * Parses the input text as a numeric double value.
     * 
     * @return parsed double value
     * @throws PropertyInvalidInputException if the input is not a number
     */
    public double inputDouble() {
        double result = ParseUtil.parseDouble(input(), Double.NaN);
        if (Double.isNaN(result)) {
            throw new PropertyInvalidInputException("Not a number");
        }
        return result;
    }

    /**
     * Parses the input text as a numeric integer value.
     * 
     * @return parsed integer value
     * @throws PropertyInvalidInputException if the input is not an integer number
     */
    public int inputInteger() {
        int result = ParseUtil.parseInt(input(), Integer.MAX_VALUE);
        if (result == Integer.MAX_VALUE && ParseUtil.parseInt(input(), 0) == 0) {
            throw new PropertyInvalidInputException("Not a number");
        }
        return result;
    }

    /**
     * Parses the input text as a boolean expression.
     * 
     * @return parsed boolean value
     * @throws PropertyInvalidInputException if the input is not a boolean
     */
    public boolean inputBoolean() {
        if (!ParseUtil.isBool(input())) {
            // Try to match the value using statements
            Statement.MatchResult match = Statement.Matcher.of(input())
                    .withSignEvent(this.input.signEvent())
                    .withGroup(isTrainProperties() ? trainProperties().getHolder() : null)
                    .withMember(isCartProperties() ? cartProperties().getHolder() : null)
                    .match();

            // We do want a failure result if no real statement gets matched.
            // It always matches the tag statement as a fall-back, so suppress that one.
            if (!match.isExactMatch()) {
                throw new PropertyInvalidInputException("Not a boolean (true/false) or Statement expression");
            }

            boolean result = match.has();
            this.input.setHasParsedStatements(true);
            return result;
        }
        return ParseUtil.parseBool(input());
    }

    /**
     * Gets the current value of the property being parsed.
     * If none is known, it is set to {@link IProperty#getDefault()}.
     * 
     * @return current value
     */
    public T current() {
        return this.current;
    }
}
