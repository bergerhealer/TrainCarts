package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.api.IDoubleProperty;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TransferFunctionInputProperty extends TransferFunctionInput {
    public static final TransferFunction.Serializer<TransferFunctionInputProperty> SERIALIZER = new TransferFunction.Serializer<TransferFunctionInputProperty>() {
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
        public TransferFunctionInputProperty createNew(TransferFunctionHost host) {
            TransferFunctionInputProperty propertyInput = new TransferFunctionInputProperty(StandardProperties.SPEEDLIMIT);
            propertyInput.updateSource(host);
            return propertyInput;
        }

        @Override
        public TransferFunctionInputProperty load(TransferFunctionHost host, ConfigurationNode config) {
            IProperty<?> property = host.getTrainCarts().getPropertyRegistry().byListedName()
                    .get(config.getOrDefault("property", ""));

            TransferFunctionInputProperty propertyInput = new TransferFunctionInputProperty(property);
            propertyInput.updateSource(host);
            return propertyInput;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInputProperty function) {
            config.set("property", function.getProperty().getListedName());
        }
    };

    private IProperty<?> property;

    public TransferFunctionInputProperty(IProperty<?> property) {
        this.property = property;
    }

    public IProperty<?> getProperty() {
        return property;
    }

    public void setProperty(IProperty<?> property) {
        this.property = property;
    }

    @Override
    public TransferFunction.Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ReferencedSource createSource(TransferFunctionHost host) {
        if (property != null) {
            CartProperties properties = host.getCartProperties();
            if (properties != null) {
                Function<CartProperties, ReferencedSource> creator = getPropertySourceCreator(property);
                if (creator != null) {
                    return creator.apply(properties);
                }
            }
        }

        // Unknown / not supported
        return ReferencedSource.NONE;
    }

    @Override
    protected TransferFunctionInput cloneInput() {
        return new TransferFunctionInputProperty(property);
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 3, MapColorPalette.COLOR_GREEN, "Property [input]");
    }

    @Override
    public void openDialog(Dialog dialog) {
        super.openDialog(dialog);

        dialog.addWidget(new MapWidgetSelectionBox() {
            private List<ListedProperty> properties = Collections.emptyList();
            private boolean loading = false;

            @Override
            public void onAttached() {
                properties = TrainCarts.plugin.getPropertyRegistry().byListedName().entrySet().stream()
                        .map(e -> new ListedProperty(e.getKey(), e.getValue()))
                        .filter(p -> getPropertySourceCreator(p.property) != null)
                        .sorted()
                        .collect(Collectors.toList());

                loading = true;
                for (ListedProperty listedProperty : properties) {
                    addItem(listedProperty.name);
                    if (listedProperty.property == property) {
                        setSelectedIndex(getItemCount() - 1);
                    }
                }
                super.onAttached();
                loading = false;
            }

            @Override
            public void onSelectedItemChanged() {
                if (!loading && getSelectedIndex() >= 0 && getSelectedIndex() < properties.size()) {
                    TransferFunctionInputProperty.this.setProperty(properties.get(getSelectedIndex()).property);
                    updateSource(dialog.getHost());
                    dialog.markChanged();
                }
            }
        }).setBounds(4, 18, dialog.getWidth() - 8, 11);
    }

    private static Function<CartProperties, ReferencedSource> getPropertySourceCreator(IProperty<?> property) {
        if (!property.isListed()) {
            // Cannot be used
            return null;
        } else if (property instanceof IDoubleProperty) {
            return properties -> new PropertySourceDouble(properties, (IDoubleProperty) property);
        } else if (property.getDefault() instanceof Double) {
            return properties -> new PropertySourceDoubleBoxed(properties, (IProperty<Double>) property);
        } else if (property.getDefault() instanceof Boolean) {
            return properties -> new PropertySourceBool(properties, (IProperty<Boolean>) property);
        } else {
            // Unknown
            return null;
        }
    }

    private static class ListedProperty implements Comparable<ListedProperty> {
        public final String name;
        public final IProperty<?> property;

        public ListedProperty(String listedName, IProperty<?> property) {
            this.name = listedName;
            this.property = property;
        }

        @Override
        public int compareTo(ListedProperty listedProperty) {
            return this.name.compareTo(listedProperty.name);
        }
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