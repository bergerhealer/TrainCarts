package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import com.bergerkiller.bukkit.tc.controller.functions.ui.inputs.MapWidgetInputFilterExpression;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.api.IDoubleProperty;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IStringSetProperty;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;

import java.util.Collections;
import java.util.List;
import java.util.Set;
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

    private ListedProperty<?> property;

    public TransferFunctionInputProperty(IProperty<?> property) {
        this(ListedProperty.of(property));
    }

    private TransferFunctionInputProperty(ListedProperty<?> property) {
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
            private List<ListedProperty<?>> properties = Collections.emptyList();
            private boolean loading = false;

            @Override
            public void onAttached() {
                properties = TrainCarts.plugin.getPropertyRegistry().byListedName().entrySet().stream()
                        .map(e -> ListedProperty.of(e.getKey(), e.getValue()))
                        .filter(ListedProperty::canCreateSource)
                        .sorted()
                        .collect(Collectors.toList());

                loading = true;
                for (ListedProperty<?> listedProperty : properties) {
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
                    for (MapWidget w : dialog.getWidget().getWidgets()) {
                        if (w instanceof PropertyOptionsWidget) {
                            ((PropertyOptionsWidget) w).update();
                            break;
                        }
                    }
                    updateSource(dialog.getHost());
                    dialog.markChanged();
                }
            }
        }).setBounds(4, 18, dialog.getWidth() - 8, 11);

        dialog.addWidget(new PropertyOptionsWidget(dialog)
                .setBounds(0, 31, dialog.getWidth(), dialog.getHeight() - 31));
    }

    private class PropertyOptionsWidget extends MapWidget {
        public final Dialog dialog;

        public PropertyOptionsWidget(Dialog dialog) {
            this.dialog = dialog.wrapWidget(this);
        }

        @Override
        public void onAttached() {
            update();
        }

        public void update() {
            if (display != null) {
                clearWidgets();
                property.addWidgets(dialog, TransferFunctionInputProperty.this);
            }
        }
    }

    private static class ListedProperty<P extends IProperty<?>> implements Comparable<ListedProperty<?>>, Cloneable {
        public final String name;
        public final P property;
        private final BiFunction<CartProperties, ListedProperty<P>, ReferencedSource> sourceCreator;

        @SuppressWarnings("unchecked")
        public <LP extends ListedProperty<P>> ListedProperty(
                final String listedName,
                final P property,
                final BiFunction<CartProperties, LP, ReferencedSource> sourceCreator
        ) {
            this.name = listedName;
            this.property = property;
            this.sourceCreator = (BiFunction<CartProperties, ListedProperty<P>, ReferencedSource>) sourceCreator;
        }

        public boolean exists() {
            return property != null;
        }

        public boolean canCreateSource() {
            return sourceCreator != null;
        }

        public ReferencedSource createSource(CartProperties properties) {
            return sourceCreator.apply(properties, this);
        }

        public boolean isBooleanOutput() {
            return exists() && property.getDefault() instanceof Boolean;
        }

        public void load(ConfigurationNode config) {
        }

        public void save(ConfigurationNode config) {
        }

        public void addWidgets(Dialog dialog, TransferFunctionInputProperty function) {
        }

        @Override
        public int compareTo(ListedProperty<?> listedProperty) {
            return this.name.compareTo(listedProperty.name);
        }

        @Override
        public ListedProperty<P> clone() {
            return this; // By default no mutable data is stored
        }

        public static ListedProperty<?> of(IProperty<?> property) {
            return of(property == null ? "" : property.getListedName(), property);
        }

        @SuppressWarnings("unchecked")
        public static ListedProperty<?> of(String name, IProperty<?> property) {
            if (property == null) {
                // Property not found / invalid config / not set
                return new ListedProperty<>(name, null, null);
            } else if (!property.isListed()) {
                // Cannot be used
                return new ListedProperty<>(name, property, null);
            } else if (property instanceof IDoubleProperty) {
                return new ListedProperty<>(name, (IDoubleProperty) property, PropertySourceDouble::new);
            } else if (property.getDefault() instanceof Double) {
                return new ListedProperty<>(name, (IProperty<Double>) property, PropertySourceDoubleBoxed::new);
            } else if (property.getDefault() instanceof Boolean) {
                return new ListedProperty<>(name, (IProperty<Boolean>) property, PropertySourceBool::new);
            } else if (property instanceof IStringSetProperty) {
                return new ListedPropertyStringSet(name, (IStringSetProperty) property);
            } else {
                // Unknown
                return new ListedProperty<>(name, property, null);
            }
        }
    }

    private static class ListedPropertyStringSet extends ListedProperty<IStringSetProperty> {
        public boolean train;
        public String expression;

        public ListedPropertyStringSet(String listedName, IStringSetProperty property) {
            super(listedName, property, PropertyStringSet::new);
            this.expression = "";
            this.train = false;
        }

        @Override
        public boolean isBooleanOutput() {
            return true;
        }

        @Override
        public void load(ConfigurationNode config) {
            train = config.getOrDefault("ofTrain", false);
            expression = config.getOrDefault("expression", "");
        }

        @Override
        public void save(ConfigurationNode config) {
            config.set("ofTrain", train);
            config.set("expression", expression);
        }

        @Override
        public void addWidgets(Dialog dialog, TransferFunctionInputProperty function) {
            dialog.addLabel(11, 3, MapColorPalette.COLOR_RED, "Check " + property.getListedName() + " of:");

            dialog.addWidget(new MapWidgetButton() {
                @Override
                public void onAttached() {
                    updateText();
                    super.onAttached();
                }

                @Override
                public void onActivate() {
                    train = !train;
                    function.updateSource(dialog.getHost());
                    dialog.markChanged();
                    updateText();
                }

                private void updateText() {
                    setText(train ? "TRAIN" : "CART");
                }
            }).setBounds(11, 10, dialog.getWidth() - 22, 12);

            dialog.addLabel(11, 26, MapColorPalette.COLOR_RED, "Filter Expression:");
            dialog.addWidget(new MapWidgetInputFilterExpression() {
                @Override
                public void onChanged(String expression) {
                    ListedPropertyStringSet.this.expression = expression;
                    function.updateSource(dialog.getHost());
                    dialog.markChanged();
                }
            }).setExpression(expression)
              .setBounds(11, 33, dialog.getWidth() - 22, 12);
        }

        @Override
        public ListedPropertyStringSet clone() {
            ListedPropertyStringSet clone = new ListedPropertyStringSet(name, property);
            clone.train = train;
            clone.expression = expression;
            return clone;
        }
    }

    // For cart or train properties that are sets of strings. Matches an expression against these sets.
    private static class PropertyStringSet extends TransferFunctionInput.ReferencedSource {
        public final CartProperties properties;
        public final IStringSetProperty property;
        public final boolean train;
        public final String expression;
        private Set<String> previousResult = null;

        public PropertyStringSet(CartProperties properties, ListedPropertyStringSet property) {
            this.properties = properties;
            this.property = property.property;
            this.train = property.train;
            this.expression = property.expression;
        }

        @Override
        public void onTick() {
            IProperties props = properties;
            if (train) {
                props = properties.getTrainProperties();

                // Unloaded?
                if (props == null) {
                    this.value = 0.0;
                    return;
                }
            }

            Set<String> allValues = props.get(property);
            if (previousResult != allValues) {
                previousResult = allValues;
                value = Util.matchText(allValues, expression) ? 1.0 : 0.0;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof PropertyStringSet) {
                PropertyStringSet other = (PropertyStringSet) o;
                return property == other.property &&
                        expression.equals(other.expression) &&
                        train == other.train;
            }
            return false;
        }
    }

    // For boolean properties. 1 is true, 0 is false.
    private static class PropertySourceBool extends TransferFunctionInput.ReferencedSource {
        public final CartProperties properties;
        public final IProperty<Boolean> property;

        public PropertySourceBool(CartProperties properties, ListedProperty<IProperty<Boolean>> property) {
            this.properties = properties;
            this.property = property.property;
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

        public PropertySourceDoubleBoxed(CartProperties properties, ListedProperty<IProperty<Double>> property) {
            this.properties = properties;
            this.property = property.property;
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

        public PropertySourceDouble(CartProperties properties, ListedProperty<IDoubleProperty> property) {
            this.properties = properties;
            this.property = property.property;
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