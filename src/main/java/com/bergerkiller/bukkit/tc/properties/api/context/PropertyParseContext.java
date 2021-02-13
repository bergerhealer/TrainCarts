package com.bergerkiller.bukkit.tc.properties.api.context;

import java.util.regex.MatchResult;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;

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
    private final String input;
    private final MatchResult matchResult;

    public PropertyParseContext(IProperties properties, T current, String name, String input, MatchResult matchResult) {
        super(properties);
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
     * Parses the input text as a boolean expression.
     * 
     * @return parsed boolean value
     * @throws PropertyInvalidInputException if the input is not a boolean
     */
    public boolean inputBoolean() {
        if (!ParseUtil.isBool(input())) {
            throw new PropertyInvalidInputException("Not a boolean (true/false expression)");
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
