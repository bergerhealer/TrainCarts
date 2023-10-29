package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

import java.util.List;

/**
 * Reads an input from an external source
 */
public class TransferFunctionInput implements TransferFunction {
    public static final Serializer<TransferFunctionInput> SERIALIZER = new Serializer<TransferFunctionInput>() {
        @Override
        public String typeId() {
            return "INPUT";
        }

        @Override
        public String title() {
            return "Input";
        }

        @Override
        public TransferFunctionInput createNew(TransferFunctionHost host) {
            List<Input> inputs = host.getInputs();
            if (inputs.isEmpty()) {
                return new TransferFunctionInput(new InvalidInput("No Input Set"));
            } else {
                return new TransferFunctionInput(inputs.get(0));
            }
        }

        @Override
        public TransferFunctionInput load(TransferFunctionHost host, ConfigurationNode config) {
            String name = config.getOrDefault("name", "");
            TransferFunction.Input input = host.findInput(name);
            if (input == null) {
                input = new InvalidInput(name);
            }
            return new TransferFunctionInput(input);
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInput function) {
            config.set("name", function.input.name());
        }
    };

    private TransferFunction.Input input;

    public TransferFunctionInput(TransferFunction.Input input) {
        this.input = input;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public double map(double unusedInput) {
        return input.value();
    }

    @Override
    public TransferFunction clone() {
        return new TransferFunctionInput(input);
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        byte color = input.valid() ? MapColorPalette.COLOR_GREEN : MapColorPalette.COLOR_RED;
        view.draw(MapFont.MINECRAFT, 2, 3, color, "=" + input.name() + " [input]");
    }

    @Override
    public void openDialog(MapWidgetTransferFunctionDialog dialog) {

    }

    private static class InvalidInput implements Input {
        private final String name;

        public InvalidInput(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean valid() {
            return false;
        }

        @Override
        public double value() {
            return 0.0;
        }
    }
}
