package com.bergerkiller.bukkit.tc.properties.api.context;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;

/**
 * Input parameters for a property set operation. Must include the String value to set,
 * but can optionally contain additional context for the operation. This includes the
 * sign action event for property signs, and a callback to execute before setting the
 * property.
 */
public class PropertyInputContext {
    private final String input;
    private UnaryOperator<PropertyParseResult<?>> beforeSet;
    private SignActionEvent signEvent;
    private boolean hasParsedStatements;

    protected PropertyInputContext(String input) {
        this.input = input;
        this.beforeSet = UnaryOperator.identity();
        this.signEvent = null;
        this.hasParsedStatements = false;
    }

    /**
     * Gets the String input to be parsed and set for the property
     *
     * @return Input String value to set
     */
    public String input() {
        return this.input;
    }

    /**
     * Sets a callback to call with the result before the property is actually set.
     * In the callback an exception can be thrown if the setting should be
     * disallowed/cancelled for some reason.
     *
     * @param callback Callback method to call before setting the property
     * @return this
     */
    public PropertyInputContext beforeSet(final Consumer<PropertyParseResult<?>> callback) {
        return this.beforeSet(input -> {
            callback.accept(input);
            return input;
        });
    }

    /**
     * Sets a callback to call with the result before the property is actually set.
     * In the callback an exception can be thrown if the setting should be
     * disallowed/cancelled for some reason. The function callback can also return a
     * new or different parse result.
     *
     * @param function Callback method to call before setting the property. The method
     *                 can return a new parse result or the original one.
     * @return this
     */
    public <T> PropertyInputContext beforeSet(UnaryOperator<PropertyParseResult<T>> function) {
        this.beforeSet = CommonUtil.unsafeCast(function);
        return this;
    }

    /**
     * Handles the before-set callback if one was set on the result specified. Internal use.
     *
     * @param Initial result.
     * @return Result after handling the before-set callback
     * @see #beforeSet(Consumer)
     */
    public <T> PropertyParseResult<T> handleBeforeSet(PropertyParseResult<T> result) {
        return result.isSuccessful() ? CommonUtil.unsafeCast(this.beforeSet.apply(result)) : result;
    }

    /**
     * Sets the SignActionEvent involved in this current property setting operation.
     * Only used when the property is set using the property sign.
     * Provides additional context while parsing the property value.
     *
     * @param event SignActionEvent to set
     * @return this
     */
    public PropertyInputContext signEvent(SignActionEvent event) {
        this.signEvent = event;
        return this;
    }

    /**
     * Gets the SignActionEvent involved in this current property setting operation.
     * Only used when the property is set using the property sign.
     *
     * @return Sign Action Event
     */
    public SignActionEvent signEvent() {
        return this.signEvent;
    }

    /**
     * After parsing a property will be set to True if a statement was matched against
     * the value to find the end-result. This indicates that the true value can change
     * due to outside circumstances.
     *
     * @return True if a statement was matched against this input value during parsing
     */
    public boolean hasParsedStatements() {
        return this.hasParsedStatements;
    }

    void setHasParsedStatements(boolean state) {
        this.hasParsedStatements = state;
    }

    /**
     * Creates a new PropertyInput instance
     *
     * @param input String input value
     * @return PropertyInput
     */
    public static PropertyInputContext of(String input) {
        return new PropertyInputContext(input);
    }
}
