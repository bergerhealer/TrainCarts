package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

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

    /**
     * Input for a transfer function to use. Used by {@link TransferFunctionInput}
     */
    interface Input {
        /** Name of this input */
        String name();

        /**
         * Gets whether this input is a boolean input. A boolean input returns 1.0 for true and
         * 0.0 for false. For conditional transfer functions this method signals whether to show
         * the comparator controls.
         *
         * @return True if this input is a boolean input, False if not
         */
        boolean isBool();

        /**
         * Gets whether this input is still valid, that is, registered internally.
         * If the input was removed it is no longer valid.
         *
         * @return True if valid
         */
        boolean valid();

        /** Current value of this input. Multi-thread safe */
        double value();
    }
}
