package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.util.function.BooleanSupplier;

/**
 * Configures a transfer function as a single item, that is expected to be stored as a single
 * entry in the configuration. Includes a mechanism to reset it to the defaults (=remove)
 * and to change it to a new one.
 */
public abstract class MapWidgetTransferFunctionSingleItem extends MapWidgetTransferFunctionItem {
    private boolean functionWasDefault = false;
    private boolean ignoreChanges = false;

    public MapWidgetTransferFunctionSingleItem(
            final TransferFunctionHost host,
            final ConfigurationNode functionConfig,
            final BooleanSupplier isBooleanInput
    ) {
        super(host, TransferFunction.Holder.of(
                (functionConfig != null) ? host.loadFunction(functionConfig)
                                         : TransferFunction.identity()),
                isBooleanInput);

        // Set to default function (using callback) if no configuration exists
        // Can't do this inside the super() constructor call sadly
        if (functionConfig == null) {
            ignoreChanges = true;
            try {
                this.function.setFunction(createDefault(), true);
            } finally {
                ignoreChanges = false;
            }
        }

        updateButtons();
    }

    public MapWidgetTransferFunctionSingleItem(
            final TransferFunctionHost host,
            final TransferFunction.Holder<TransferFunction> function,
            final BooleanSupplier isBooleanInput
    ) {
        super(host, function, isBooleanInput);
        updateButtons();
    }

    /**
     * Called when the transfer function is changed using user input.
     * External changes (not coming from this widget) are not detected.
     *
     * @param function New function it was changed into
     */
    public abstract void onChanged(TransferFunction.Holder<TransferFunction> function);

    /**
     * Creates a new instance of the default transfer function for this menu item.
     * This resets the function to the defaults, allowing a new one to be configured.
     *
     * @return Default transfer function
     */
    public abstract TransferFunction createDefault();

    @Override
    protected void onChangedInternal(TransferFunction.Holder<TransferFunction> function) {
        if (ignoreChanges) {
            return;
        }
        updateButtons();
        onChanged(function);
    }

    protected void updateButtons() {
        if (!buttons.isEmpty() && functionWasDefault == function.isDefault()) {
            return; // No change
        }

        functionWasDefault = function.isDefault();
        updateButtons(item -> {
            item.addConfigureButton();
            if (function.isDefault()) {
                item.addButton(ButtonIcon.ADD, () -> {
                    // Ask in a dialog what kind of function to set here
                    this.getParent().addWidget(new MapWidgetTransferFunctionTypeSelectorDialog(host) {
                        @Override
                        public void onSelected(TransferFunction function) {
                            MapWidgetTransferFunctionSingleItem.this.function.setFunction(function);
                            MapWidgetTransferFunctionSingleItem.this.focus();
                        }
                    });
                });
            } else {
                item.addButton(ButtonIcon.REMOVE, () -> {
                    function.setFunction(createDefault(), true);
                });
            }
        });
    }
}
