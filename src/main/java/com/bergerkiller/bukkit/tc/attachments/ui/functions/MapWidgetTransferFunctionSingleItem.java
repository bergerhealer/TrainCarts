package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

/**
 * Configures a transfer function as a single item, that is expected to be stored as a single
 * entry in the configuration. Includes a mechanism to reset it to the defaults (=remove)
 * and to change it to a new one.
 */
public abstract class MapWidgetTransferFunctionSingleItem extends MapWidgetTransferFunctionItem {
    private boolean functionWasDefault = false;

    public MapWidgetTransferFunctionSingleItem(TransferFunctionHost host, TransferFunction.Holder<TransferFunction> function) {
        super(host, function);
        updateButtons();
    }

    /**
     * Called when the transfer function is changed using user input.
     * External changes (not coming from this widget) are not detected.
     *
     * @param function New function it was changed into
     */
    public abstract void onChanged(TransferFunction function);

    /**
     * Creates a new instance of the default transfer function for this menu item.
     * This resets the function to the defaults, allowing a new one to be configured.
     *
     * @return Default transfer function
     */
    public abstract TransferFunction createDefault();

    @Override
    protected void onChangedInternal(TransferFunction function) {
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
