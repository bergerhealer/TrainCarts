package com.bergerkiller.bukkit.tc.properties.api;

import java.util.regex.MatchResult;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Argument passed to {@link PropertyParser} annotated methods
 * to perform parsing. Instead of this class a String
 * class can be used as well.
 * 
 * @param <T> Type of value to parse
 */
public final class PropertyParseContext<T> {
    private final IProperties properties;
    private final T current;
    private final String name;
    private final String input;
    private final MatchResult matchResult;

    public PropertyParseContext(IProperties properties, T current, String name, String input, MatchResult matchResult) {
        this.properties = properties;
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

    /**
     * Retrieves the cart properties that are being parsed if
     * {@link #isCartProperties()} is true, otherwise returns
     * <i>null</i>
     * 
     * @return Cart properties, null if the parse operation is on an entire train
     */
    public CartProperties cartProperties() {
        return isCartProperties() ? ((CartProperties) this.properties) : null;
    }

    /**
     * Retrieves the train properties that are being parsed.
     * If {@link #isTrainProperties()} is true, then these
     * properties are returned. If {@link #isCartProperties()} is
     * true instead, then the train properties of the cart properties
     * are returned. If neither returns true, then the caller did
     * not specify properties being parsed, and null is returned.
     * 
     * @return Train Properties, null if no properties were specified
     */
    public TrainProperties trainProperties() {
        if (isTrainProperties()) {
            return (TrainProperties) this.properties;
        } else if (isCartProperties()) {
            return ((CartProperties) this.properties).getTrainProperties();
        } else {
            return null;
        }
    }

    /**
     * Gets whether currently cart properties are being parsed
     * 
     * @return True if cart properties are being parsed
     */
    public boolean isCartProperties() {
        return this.properties instanceof CartProperties;
    }

    /**
     * Gets whether currently train properties are being parsed
     * 
     * @return True if train properties are being parsed
     */
    public boolean isTrainProperties() {
        return this.properties instanceof TrainProperties;
    }
}
