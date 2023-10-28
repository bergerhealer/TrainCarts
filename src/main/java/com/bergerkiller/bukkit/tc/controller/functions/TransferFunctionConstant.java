package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

/**
 * A transfer function that always returns the same constant value
 */
public final class TransferFunctionConstant implements TransferFunction {
    private double output;

    public TransferFunctionConstant() {
        this(0.0);
    }

    public TransferFunctionConstant(double output) {
        this.output = output;
    }

    public void setOutput(double output) {
        this.output = output;
    }

    public double getOutput() {
        return output;
    }

    @Override
    public double map(double input) {
        return output;
    }

    @Override
    public TransferFunction clone() {
        return new TransferFunctionConstant(output);
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 0, MapColorPalette.COLOR_RED, "Constant");
    }

    @Override
    public void makeDialog(MapWidgetTransferFunctionDialog dialog) {
    }
}
