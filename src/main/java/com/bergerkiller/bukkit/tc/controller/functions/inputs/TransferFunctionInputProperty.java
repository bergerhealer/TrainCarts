package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.api.IDoubleProperty;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

public class TransferFunctionInputProperty extends TransferFunctionInput {
    public static final TransferFunction.Serializer<TransferFunctionInput> SERIALIZER = new TransferFunction.Serializer<TransferFunctionInput>() {
        @Override
        public String typeId() {
            return "INPUT-PROPERTY";
        }

        @Override
        public String title() {
            return "In: Property";
        }

        @Override
        public boolean isInput() {
            return true;
        }

        @Override
        public TransferFunctionInput createNew(TransferFunctionHost host) {
            TransferFunctionInputProperty property = new TransferFunctionInputProperty();
            property.updateSource(host);
            return property;
        }

        @Override
        public TransferFunctionInput load(TransferFunctionHost host, ConfigurationNode config) {
            TransferFunctionInputProperty property = new TransferFunctionInputProperty();
            property.updateSource(host);
            return property;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInput function) {
        }
    };

    @Override
    public TransferFunction.Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ReferencedSource createSource(TransferFunctionHost host) {
        CartProperties properties = host.getCartProperties();
        if (properties == null) {
            return ReferencedSource.NONE;
        }

        IProperty<?> property = StandardProperties.SPEEDLIMIT;

        if (property instanceof IDoubleProperty) {
            return new PropertySourceDouble(properties, (IDoubleProperty) property);
        } else if (property.getDefault() instanceof Double) {
            return new PropertySourceDoubleBoxed(properties, (IProperty<Double>) property);
        } else if (property.getDefault() instanceof Boolean) {
            return new PropertySourceBool(properties, (IProperty<Boolean>) property);
        } else {
            // Unknown
            return ReferencedSource.NONE;
        }
    }

    @Override
    protected TransferFunctionInput cloneInput() {
        return new TransferFunctionInputProperty();
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 3, MapColorPalette.COLOR_GREEN, "Property [input]");
    }

    @Override
    public void openDialog(Dialog dialog) {
        super.openDialog(dialog);
    }

    // For boolean properties. 1 is true, 0 is false.
    private static class PropertySourceBool extends TransferFunctionInput.ReferencedSource {
        public final CartProperties properties;
        public final IProperty<Boolean> property;

        public PropertySourceBool(CartProperties properties, IProperty<Boolean> property) {
            this.properties = properties;
            this.property = property;
        }

        @Override
        public void onTick() {
            this.value = property.get(properties) ? 1.0 : 0.0;
        }

        @Override
        public boolean isBool() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PropertySourceBool && ((PropertySourceBool) o).property == property;
        }
    }

    // For boxed double. Probably reads from configuration.
    private static class PropertySourceDoubleBoxed extends TransferFunctionInput.ReferencedSource {
        public final CartProperties properties;
        public final IProperty<Double> property;

        public PropertySourceDoubleBoxed(CartProperties properties, IProperty<Double> property) {
            this.properties = properties;
            this.property = property;
        }

        @Override
        public void onTick() {
            this.value = property.get(properties);
        }

        @Override
        public boolean isBool() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PropertySourceDoubleBoxed && ((PropertySourceDoubleBoxed) o).property == property;
        }
    }

    // For double that avoids boxing overhead
    private static class PropertySourceDouble extends TransferFunctionInput.ReferencedSource {
        public final CartProperties properties;
        public final IDoubleProperty property;

        public PropertySourceDouble(CartProperties properties, IDoubleProperty property) {
            this.properties = properties;
            this.property = property;
        }

        @Override
        public void onTick() {
            this.value = property.getDouble(properties);
        }

        @Override
        public boolean isBool() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PropertySourceDouble && ((PropertySourceDouble) o).property == property;
        }
    }
}