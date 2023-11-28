package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;

/**
 * Takes in double input values and maps (transforms) the input into a new output value.
 * Can be configured using map display widgets, and has preview drawing for in list views.<br>
 * <br>
 * This class is not multithread-safe.
 * 
 * @see #map(double)
 */
public interface TransferFunction extends DoubleUnaryOperator, Cloneable {

    /**
     * Gets the serializer that is responsible for loading/saving this transfer function.
     * Should be the same instance that was registered in the {@link TransferFunctionRegistry}
     *
     * @return Serializer
     */
    Serializer<? extends TransferFunction> getSerializer();

    /**
     * Gets the transfer function registry that stores all supported transfer function types.
     * Use this to register a new transfer function type.
     *
     * @return Transfer Function Registry
     */
    static TransferFunctionRegistry getRegistry() {
        return TransferFunctionRegistry.INSTANCE;
    }

    /**
     * Gets an identity transfer function singleton instance. This transfer function is
     * a no-op that simply returns the input.
     *
     * @return Identity transfer function
     */
    static TransferFunction identity() {
        return TransferFunctionIdentity.INSTANCE;
    }

    /**
     * Maps the input value to an output value based on this transfer function.
     * For time-based transfer functions it assumes this method is called every tick.
     *
     * @param input Input value
     * @return Transfer Function Output value
     */
    double map(double input);

    /**
     * Gets whether the output of this transfer function is a boolean. That is,
     * {@link #map(double)} return 1.0 for true and 0.0 for false.
     *
     * @return True if map() returns a boolean output
     */
    default boolean isBooleanOutput() {
        return false;
    }

    /**
     * Gets whether the output of this transfer function is a constant value for the same
     * inputs. If so, {@link #map(double)} can be called and is guaranteed to return the
     * same output value every time for the same inputs.
     *
     * @return True if the output of this transfer function is always the same for the
     *         same inputs.
     */
    default boolean isPure() {
        return false;
    }

    @Override
    default double applyAsDouble(double v) {
        return map(v);
    }

    TransferFunction clone();

    /**
     * Draws a non-interactive preview that shows up behind the navigation menu.
     * Should show a small summary of what this transfer function does
     *
     * @param widget Item Widget that is being drawn. Can be used to see if the item
     *               is currently focused
     * @param view View canvas to draw on top of
     */
    void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view);

    /**
     * Fills a menu dialog with contents for configuring this transfer function
     *
     * @param dialog Dialog widget to which other widgets should be added to configure it.
     *               Is empty initially.
     */
    void openDialog(MapWidgetTransferFunctionDialog dialog);

    /**
     * Holds a single TransferFunction. Class can be used to swap an instance of a transfer
     * function.
     *
     * @param <T> Transfer Function Type
     */
    class Holder<T extends TransferFunction> {
        protected T function;

        protected Holder(T function) {
            this.function = function;
        }

        public T getFunction() {
            return function;
        }

        public void setFunction(T function) {
            this.function = function;
        }

        public boolean isIdentity() {
            return getFunction() == TransferFunction.identity();
        }

        /**
         * Returns a new Holder that calls the change listener specified whenever
         * {@link #setFunction(TransferFunction)} is called.
         *
         * @param onChanged Callback for after the function is changed
         * @return new Holder that calls the callback when the function is changed
         */
        public Holder<T> withChangeListener(Consumer<T> onChanged) {
            final Holder<T> orig = this;
            return new Holder<T>(function) {
                @Override
                public void setFunction(T function) {
                    super.setFunction(function);
                    orig.setFunction(function);
                    onChanged.accept(function);
                }
            };
        }

        /**
         * Wraps a TransferFunction as a Holder
         *
         * @param function Function
         * @return Holder
         * @param <T> Transfer Function type
         */
        public static <T extends TransferFunction> Holder<T> of(T function) {
            return new Holder<T>(function);
        }
    }

    /**
     * Loads or creates transfer functions of a certain type
     *
     * @param <T> Transfer Function Type
     */
    interface Serializer<T extends TransferFunction> {
        /** Field in the Configuration that stores the {@link #typeId()} */
        String TYPE_FIELD = "type";

        /**
         * Gets a unique type id that identifies this type of transfer function.
         * Is important for persistent serialization/de-serialization.
         *
         * @return Type Id
         */
        String typeId();

        /**
         * Gets the title displayed for this serializer when it is listed
         * in the new transfer function selection menu
         *
         * @return Title
         */
        String title();

        /**
         * Gets whether this transfer function is listed in the 'create new transfer function'
         * dialog.
         *
         * @return True if listed (default)
         */
        default boolean isListed() {
            return true;
        }

        /**
         * Gets whether this transfer function is a type of input
         *
         * @return True if it is an input
         */
        default boolean isInput() {
            return false;
        }

        /**
         * Creates a new blank slate instance of this Transfer Function
         *
         * @param host TransferFunctionHost that will contain this transfer function
         * @return New Transfer Function instance
         */
        T createNew(TransferFunctionHost host);

        /**
         * Creates a new instance of this Transfer Function by loading it from
         * a YAML configuration
         *
         * @param host TransferFunctionHost that contains this transfer function
         * @param config Configuration to load from
         * @return New Transfer Function instance
         */
        T load(TransferFunctionHost host, ConfigurationNode config);

        /**
         * Saves a transfer function instance to a YAML configuration.
         * The {@link #typeId()} has already been saved.
         *
         * @param host TransferFunctionHost that contains this transfer function
         * @param config Configuration to save to
         * @param function Transfer Function to save
         */
        void save(TransferFunctionHost host, ConfigurationNode config, T function);
    }
}
