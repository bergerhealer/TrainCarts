package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

/**
 * A transfer function that always returns the same constant value
 */
public final class TransferFunctionConstant implements TransferFunction {
    public static final Serializer<TransferFunctionConstant> SERIALIZER = new Serializer<TransferFunctionConstant>() {
        @Override
        public String typeId() {
            return "CONSTANT";
        }

        @Override
        public String title() {
            return "Constant";
        }

        @Override
        public TransferFunctionConstant createNew(TransferFunctionHost host) {
            return new TransferFunctionConstant();
        }

        @Override
        public TransferFunctionConstant load(TransferFunctionHost host, ConfigurationNode config) {
            double output = config.getOrDefault("output", 0.0);
            return new TransferFunctionConstant(output);
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionConstant function) {
            config.set("output", function.output);
        }
    };

    private double output;

    public TransferFunctionConstant() {
        this(0.0);
    }

    public TransferFunctionConstant(double output) {
        this.output = output;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
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
    public boolean isPure() {
        return true;
    }

    @Override
    public TransferFunction clone() {
        return new TransferFunctionConstant(output);
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 3, MapColorPalette.COLOR_GREEN,
                output + " [constant]");
    }

    @Override
    public void openDialog(Dialog dialog) {
        dialog.addWidget(new MapWidgetNumberBox() {
            @Override
            public void onAttached() {
                this.setInitialValue(output);
                this.setIncrement(0.001);
                super.onAttached();
            }

            @Override
            public void onValueChanged() {
                output = getValue();
                dialog.markChanged();
            }
        }).setBounds(8, 1, dialog.getWidth() - 16, dialog.getHeight() - 2);
    }

    @Override
    public DialogMode openDialogMode() {
        return DialogMode.INLINE;
    }
}
