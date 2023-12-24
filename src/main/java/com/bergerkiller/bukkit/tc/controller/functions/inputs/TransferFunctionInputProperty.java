package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.api.IDoubleProperty;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
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
            propertyInput.property.load(config);
            propertyInput.updateSource(host);
            return propertyInput;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInputProperty function) {
            config.set("property", function.property.name);
            if (function.property.exists()) {
                function.property.save(config);
            }
        }
    };

    private ListedProperty property;

    public TransferFunctionInputProperty(IProperty<?> property) {
        this(ListedProperty.of(property));
    }

    private TransferFunctionInputProperty(ListedProperty property) {
        if (property == null) {
            throw new IllegalArgumentException("Listed Property cannot be null");
        }
        this.property = property;
    }

    public IProperty<?> getProperty() {
        return property.property;
    }

    public void setProperty(IProperty<?> property) {
        this.property = ListedProperty.of(property);
    }

    @Override
    public TransferFunction.Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ReferencedSource createSource(TransferFunctionHost host) {
        if (property.canCreateSource()) {
            CartProperties properties = host.getCartProperties();
            if (properties != null) {
                return property.createSource(properties);
            }
        }

        // Unknown / not supported
        return ReferencedSource.NONE;
    }

    @Override
    public boolean isBooleanOutput() {
        return property.isBooleanOutput();
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
                        .map(e -> ListedProperty.of(e.getKey(), e.getValue()))
                        .filter(ListedProperty::canCreateSource)
                        .sorted()
                        .collect(Collectors.toList());

                loading = true;
                for (ListedProperty listedProperty : properties) {
                    addItem(listedProperty.name);
                    if (listedProperty.property == property.property) {
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

    private static class ListedProperty implements Comparable<ListedProperty>, Cloneable {
        public final String name;
        public final IProperty<?> property;
        private final BiFunction<CartProperties, IProperty<?>, ReferencedSource> sourceCreator;

        @SuppressWarnings("unchecked")
        public <T extends IProperty<?>> ListedProperty(
                final String listedName,
                final T property,
                final BiFunction<CartProperties, T, ReferencedSource> sourceCreator
        ) {
            this.name = listedName;
            this.property = property;
            this.sourceCreator = (BiFunction<CartProperties, IProperty<?>, ReferencedSource>) sourceCreator;
        }

        public boolean exists() {
            return property != null;
        }

        public boolean canCreateSource() {
            return sourceCreator != null;
        }

        public ReferencedSource createSource(CartProperties properties) {
            return sourceCreator.apply(properties, this.property);
        }

        public boolean isBooleanOutput() {
            return exists() && property.getDefault() instanceof Boolean;
        }

        public void load(ConfigurationNode config) {
        }

        public void save(ConfigurationNode config) {
        }

        @Override
        public int compareTo(ListedProperty listedProperty) {
            return this.name.compareTo(listedProperty.name);
        }

        @Override
        public ListedProperty clone() {
            return this; // By default no mutable data is stored
        }

        public static ListedProperty of(IProperty<?> property) {
            return of(property == null ? "" : property.getListedName(), property);
        }

        @SuppressWarnings("unchecked")
        public static ListedProperty of(String name, IProperty<?> property) {
            if (property == null) {
                // Property not found / invalid config / not set
                return new ListedProperty(name, null, null);
            } else if (!property.isListed()) {
                // Cannot be used
                return new ListedProperty(name, property, null);
            } else if (property instanceof IDoubleProperty) {
                return new ListedProperty(name, (IDoubleProperty) property, PropertySourceDouble::new);
            } else if (property.getDefault() instanceof Double) {
                return new ListedProperty(name, (IProperty<Double>) property, PropertySourceDoubleBoxed::new);
            } else if (property.getDefault() instanceof Boolean) {
                return new ListedProperty(name, (IProperty<Boolean>) property, PropertySourceBool::new);
            } else {
                // Unknown
                return new ListedProperty(name, property, null);
            }
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
        public boolean equals(Object o) {
            return o instanceof PropertySourceDouble && ((PropertySourceDouble) o).property == property;
        }
    }
}