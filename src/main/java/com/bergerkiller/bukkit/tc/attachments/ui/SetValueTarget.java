package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.bergerkiller.bukkit.common.utils.ParseUtil;

/**
 * For widgets that support setting the value using the command /train menu set [value]
 */
public interface SetValueTarget {

    /**
     * Gets the name of the property changed when a text value is accepted
     * 
     * @return accepted property name
     */
    String getAcceptedPropertyName();

    /**
     * Accepts a text value updating a property of this target. Should do the same
     * as {@link #acceptTextValue(Operation, String)} with {@link Operation#SET}.
     * 
     * @param value Value to accept
     * @return True if the value was actually updated, False if invalid or not accepted
     */
    boolean acceptTextValue(String value);

    /**
     * Accepts a text value updating a property of this target, performing a particular
     * operation when doing so. By default calls {@link #acceptTextValue(String)} when
     * the operation is a set operation.
     *
     * @param operation Operation to perform with the value
     * @param value Value to accept
     * @return True if the value was actually updated, False if the operation or value is
     *         invalid or not accepted
     */
    default boolean acceptTextValue(Operation operation, String value) {
        return (operation == Operation.SET) && acceptTextValue(value);
    }

    /**
     * Type of operation to perform when setting the value
     */
    public static enum Operation {
        /** Sets the value, discarding the previous value */
        SET,
        /** Adds the numerical representation of the value to the previous value */
        ADD,
        /** Subtracts the numerical representation of the value from the previous value */
        SUBTRACT;

        /**
         * Parses the input Text value as an Integer, and if successful, sets/adds/subtracts the
         * value by making use of the setter and getter methods specified.
         *
         * @param getter Getter function
         * @param setter Setter function
         * @param value Input value to parse
         * @return True if successful, False if this failed
         */
        public boolean perform(IntSupplier getter, IntConsumer setter, String value) {
            int parsed = ParseUtil.parseInt(value, Integer.MAX_VALUE);
            if (parsed == Integer.MAX_VALUE && (double) parsed != ParseUtil.parseDouble(value, Double.NaN)) {
                // Null doesn't work :(
                return false;
            }

            switch (this) {
            case SET:
                setter.accept(parsed);
                return true;
            case ADD:
                setter.accept(getter.getAsInt() + parsed);
                return true;
            case SUBTRACT:
                setter.accept(getter.getAsInt() - parsed);
                return true;
            default:
                return false;
            }
        }

        /**
         * Parses the input Text value as a Double, and if successful, sets/adds/subtracts the
         * value by making use of the setter and getter methods specified.
         *
         * @param getter Getter function
         * @param setter Setter function
         * @param value Input value to parse
         * @return True if successful, False if this failed
         */
        public boolean perform(DoubleSupplier getter, DoubleConsumer setter, String value) {
            double parsed = ParseUtil.parseDouble(value, Double.NaN);
            if (Double.isNaN(parsed)) {
                return false;
            }

            switch (this) {
            case SET:
                setter.accept(parsed);
                return true;
            case ADD:
                setter.accept(getter.getAsDouble() + parsed);
                return true;
            case SUBTRACT:
                setter.accept(getter.getAsDouble() - parsed);
                return true;
            default:
                return false;
            }
        }
    }
}
