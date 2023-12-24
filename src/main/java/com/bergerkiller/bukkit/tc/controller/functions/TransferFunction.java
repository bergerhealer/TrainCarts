package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;

import java.util.function.BooleanSupplier;
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
     * The color code used to show a function preview when it is the default ('unset')
     */
    byte DEFAULT_FUNCTION_COLOR = MapColorPalette.getColor(100, 100, 100);

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
     * @param isBooleanInput Whether the input to this function is a boolean, too.
     *                       Functions that can return the input can infer from that.
     *                       If not needed to come to an answer, don't call it to
     *                       save on computation.
     * @return True if map() returns a boolean output
     */
    default boolean isBooleanOutput(BooleanSupplier isBooleanInput) {
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
     * @param dialog Dialog context information. Is {@link MapWidgetTransferFunctionDialog}
     *               if {@link #openDialogMode()} is WINDOW.
     */
    void openDialog(Dialog dialog);

    /**
     * Gets whether {@link #openDialog(Dialog)} opens a dialog
     * inline of where the item is edited. If INLINE, no dialog window is opened.
     * Default is WINDOW, opening the configuration in a dialog window.
     * To now allow for any configuration, return NONE.
     *
     * @return Whether the dialog to configure this transfer function is opened inline
     */
    default DialogMode openDialogMode() {
        return DialogMode.WINDOW;
    }

    /**
     * Dialog context information for {@link #openDialog(Dialog)}
     */
    interface Dialog {
        /**
         * Gets the dialog widget to which other widgets should be added to configure it.
         * Is empty initially. If {@link #openDialogMode()} is <i>WINDOW</i> then this
         * dialog widget is a {@link MapWidgetTransferFunctionDialog}
         *
         * @return Parent widget
         */
        MapWidget getWidget();

        /**
         * Gets the TransferFunctionHost that provides context information
         * @return TransferFunctionHost
         */
        TransferFunctionHost getHost();

        /**
         * Changes the instance of the transfer function. Can be used to swap out the
         * transfer function with something new when configuration radically changes.
         *
         * @param function New transfer function to set
         */
        void setFunction(TransferFunction function);

        /**
         * Gets whether the input to the function being configured is a boolean
         *
         * @return True if the input is a boolean
         */
        boolean isBooleanInput();

        /**
         * Gets whether the previous function being configured in this dialog was
         * the function specified. Can be used to re-focus the right widgets
         * when the player goes 'back'.
         *
         * @param functionHolder Function Holder to test
         * @return True if the previous function being configured was for this holder
         */
        boolean isPreviousFunction(Holder<?> functionHolder);

        /**
         * Sends a signal that the existing transfer function instance has changed.
         * Same as calling {@link #setFunction(TransferFunction)} with the current
         * instance.
         */
        void markChanged();

        /**
         * Closes the dialog, finishing the editing of the function
         */
        void finish();

        /**
         * Same as {@link #getWidget()}.addWidget()
         *
         * @param widget Widget to add
         * @return Added widget
         * @param <T> Widget type
         */
        default <T extends MapWidget> T addWidget(T widget) {
            return getWidget().addWidget(widget);
        }

        /**
         * Same as {@link #getWidget()}.getWidth()
         *
         * @return dialog width
         */
        default int getWidth() {
            return getWidget().getWidth();
        }

        /**
         * Same as {@link #getWidget()}.getHeight()
         *
         * @return dialog height
         */
        default int getHeight() {
            return getWidget().getHeight();
        }

        /**
         * Adds a tiny-font label somewhere on the dialog
         *
         * @param x X-position
         * @param y Y-position
         * @param color Text color of the label
         * @param text Text of the label
         */
        default void addLabel(int x, int y, byte color, String text) {
            MapWidgetText label = new MapWidgetText();
            label.setFont(MapFont.TINY);
            label.setText(text);
            label.setPosition(x, y);
            label.setColor(color);
            this.addWidget(label);
        }

        /**
         * Creates a new Dialog that wraps a different widget. All other methods are proxied
         * from this original dialog. This can be used when a part of the original dialog's
         * space is used to show a smaller (part of a-) dialog.
         *
         * @param widget Widget to wrap. Width/height/etc. will be of this widget.
         * @return Updated Dialog
         */
        default Dialog wrapWidget(final MapWidget widget) {
            final Dialog original = this;
            return new Dialog() {

                @Override
                public MapWidget getWidget() {
                    return widget;
                }

                @Override
                public TransferFunctionHost getHost() {
                    return original.getHost();
                }

                @Override
                public void setFunction(TransferFunction function) {
                    original.setFunction(function);
                }

                @Override
                public boolean isBooleanInput() {
                    return original.isBooleanInput();
                }

                @Override
                public boolean isPreviousFunction(Holder<?> functionHolder) {
                    return original.isPreviousFunction(functionHolder);
                }

                @Override
                public void markChanged() {
                    original.markChanged();
                }

                @Override
                public void finish() {
                    original.finish();
                }
            };
        }
    }

    /**
     * Holds a single TransferFunction. Class can be used to swap an instance of a transfer
     * function, or to be notified of the function being changed.
     *
     * @param <T> Transfer Function Type
     */
    class Holder<T extends TransferFunction> {
        protected T function;
        protected boolean isDefault = false;

        protected Holder(T function, boolean isDefault) {
            this.function = function;
            this.isDefault = isDefault;
        }

        public T getFunction() {
            return function;
        }

        public final void setFunction(T function) {
            setFunction(function, false);
        }

        public void setFunction(T function, boolean isDefault) {
            this.function = function;
            this.isDefault = isDefault;
        }

        public boolean isIdentity() {
            return getFunction() == TransferFunction.identity();
        }

        /**
         * Gets whether this function is a default function. A default function is not
         * written to configuration, and is inferred from there being no (null) function
         * stored in the configuration.
         *
         * @return True if this function is the default function
         */
        public boolean isDefault() {
            return isDefault;
        }

        /**
         * Returns a new Holder that calls the change listener specified whenever
         * {@link #setFunction(TransferFunction)} is called.
         *
         * @param onChanged Callback for after the function is changed
         * @return new Holder that calls the callback when the function is changed
         */
        public Holder<T> withChangeListener(Consumer<Holder<T>> onChanged) {
            final Holder<T> orig = this;
            return new Holder<T>(function, isDefault) {
                @Override
                public void setFunction(T function, boolean isDefault) {
                    super.setFunction(function, isDefault);
                    orig.setFunction(function, isDefault);
                    onChanged.accept(this);
                }

                @Override
                protected Holder<?> rootHolder() {
                    return orig.rootHolder();
                }
            };
        }

        /**
         * Gets whether this holder is ultimately the same as another one.
         * This survives wrapping with {@link #withChangeListener(Consumer)}
         *
         * @param other Other Holder
         * @return True if the same
         */
        public final boolean isSame(Holder<?> other) {
            return this.rootHolder() == other.rootHolder();
        }

        protected Holder<?> rootHolder() {
            return this;
        }

        /**
         * Wraps a TransferFunction as a Holder
         *
         * @param function Function
         * @return Holder
         * @param <T> Transfer Function type
         */
        public static <T extends TransferFunction> Holder<T> of(T function) {
            return new Holder<T>(function, false);
        }

        /**
         * Wraps a TransferFunction as a Holder, potentailly a default value
         *
         * @param function Function
         * @param isDefault Whether this function is the default value (no config)
         * @return Holder
         * @param <T> Transfer Function type
         */
        public static <T extends TransferFunction> Holder<T> of(T function, boolean isDefault) {
            return new Holder<T>(function, isDefault);
        }
    }

    /**
     * Mode of opening a dialog configuring this transfer function
     */
    enum DialogMode {
        /** No window is opened at all. This transfer function has no configuration. */
        NONE,
        /** Configuration is opened inline, in the transfer function item (preview area) */
        INLINE,
        /** The window is navigated to show this function, or a window is popped open to configure it */
        WINDOW
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
         * dialog. The input host can be queried to test compatibility.
         *
         * @param host TransferFunctionHost that will contain this transfer function
         * @return True if listed (default)
         */
        default boolean isListed(TransferFunctionHost host) {
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
