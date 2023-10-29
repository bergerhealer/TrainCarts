package com.bergerkiller.bukkit.tc.controller.functions;

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
     * Maps the input value to an output value based on this transfer function.
     * For time-based transfer functions it assumes this method is called every tick.
     *
     * @param input Input value
     * @return Transfer Function Output value
     */
    double map(double input);

    @Override
    default double applyAsDouble(double v) {
        return map(v);
    }

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

    TransferFunction clone();
}
