package com.bergerkiller.bukkit.tc.properties.api;

import java.util.Iterator;
import java.util.function.Consumer;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Represents an initialized {@link PropertyParser} method
 * 
 * @param <T> Property value type
 */
public interface IPropertyParser<T> {

    /**
     * Gets the property instance this parser is for
     * 
     * @return property
     */
    IProperty<T> getProperty();

    /**
     * Gets the name by which this property parser was found
     * 
     * @return property parser name
     */
    String getName();

    /**
     * Gets whether the input to this parser is
     * pre-processed to eliminate surrounding whitespace
     * and converted to lower-case.
     * 
     * @return True if input is pre-processed before parsing
     * @see PropertyParser#preProcess()
     */
    boolean isInputPreProcessed();

    /**
     * Gets whether input should be processed for each cart
     * individually when parsing and applying properties for
     * a train. When false, the value should be parsed once
     * and then applied to all carts of the train.
     * 
     * @return True if properties are parsed and applied per cart,
     *         False if they should be parsed for the train and
     *         then applied to all carts.
     * @see PropertyParser#processPerCart()
     */
    boolean isProcessedPerCart();

    /**
     * Attempts to parse the input value to a value for this parser's owning property.
     * To check whether parsing succeeded, use {@link PropertyParseResult#isSuccessful()}.
     * If parsing failed, the reason for failing can be found using
     * {@link PropertyParseResult#getReason()}.
     * 
     * @param <T> Type of property value
     * @param properties The properties the value is parsed for, can be train or cart
     *        properties. The previous (current) value is read from these properties
     *        before parsing. This is used in case a parser makes use of the current
     *        value to parse a value, for example, when adding elements to a list.
     *        If <i>null</i> is provided, the default value of the property is used.
     * @param input Input value to parse
     * @return result of parsing the property by this name using the value
     */
    PropertyParseResult<T> parse(IProperties properties, String input);

    /**
     * Parses the input using this parser and, if successful, applies the parsed value
     * to the properties.<br>
     * <br>
     * If {@link #isProcessedPerCart()} is <i>true</i>, then
     * the property is parsed and applied to each cart individually. In that case
     * the returned value is the output of {@link IProperty#get(TrainProperties)}.
     * 
     * @param properties The properties the value is parsed for and applied to
     * @param input Input value to parse
     * @return result of parsing the property by this name using the value
     * @see #parse(IProperties, String)
     */
    default PropertyParseResult<T> parseAndSet(IProperties properties, String input) {
        return parseAndSet(properties, input, LogicUtil.noopConsumer());
    }

    /**
     * Parses the input using this parser and, if successful, applies the parsed value
     * to the properties.<br>
     * <br>
     * If {@link #isProcessedPerCart()} is <i>true</i>, then
     * the property is parsed and applied to each cart individually. In that case
     * the returned value is the output of {@link IProperty#get(TrainProperties)}.<br>
     * <br>
     * A consumer can be specified to process the parse result before it is applied to the
     * train or cart. This can be used to cancel the operation by throwing exceptions.
     * 
     * @param properties The properties the value is parsed for and applied to
     * @param input Input value to parse
     * @param beforeSet Consumer of the parse result before the property is updated.
     *                  Can throw exceptions to cancel the operation.
     * @return result of parsing the property by this name using the value
     * @see #parse(IProperties, String)
     */
    default PropertyParseResult<T> parseAndSet(IProperties properties, String input, Consumer<PropertyParseResult<T>> beforeSet) {
        // Parsed once and then applied to all carts / the cart
        if (!this.isProcessedPerCart() || !(properties instanceof TrainProperties)) {
            PropertyParseResult<T> result = this.parse(properties, input);
            if (result.isSuccessful()) {
                properties.set(this.getProperty(), result.getValue());
            }
            return result;
        }

        // Try parsing each cart individual, if train has no carts, only parse
        TrainProperties trainProperties = (TrainProperties) properties;
        if (trainProperties.isEmpty()) {
            return parse(properties, input);
        }

        // Go by each cart and try parsing, if any succeeds, return a success with the
        // train property value that is now set. This allows properties to do any
        // merging of individual properties in the train properties getter.
        boolean successful = false;
        String name = input;
        Iterator<CartProperties> cartIter = trainProperties.iterator();
        CartProperties cartProp = cartIter.next();
        PropertyParseResult<T> result = this.parse(cartProp, input);
        if (result.isSuccessful()) {
            cartProp.set(this.getProperty(), result.getValue());
            name = result.getName();
            successful = true;
        }
        while (cartIter.hasNext()) {
            cartProp = cartIter.next();
            PropertyParseResult<T> cartResult = parse(cartProp, input);
            if (cartResult.isSuccessful()) {
                cartProp.set(this.getProperty(), cartResult.getValue());
                name = result.getName();
                successful = true;
            }
        }
        if (successful) {
            result = PropertyParseResult.success(this.getProperty(), name,
                    properties.get(this.getProperty()));
        }
        return result;
    }
}
