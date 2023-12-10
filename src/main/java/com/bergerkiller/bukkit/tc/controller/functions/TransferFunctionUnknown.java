package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;

import java.util.Collections;

/**
 * Placeholder for transfer functions whose type id isn't registered.
 * In case third parties make their own functions, this avoids problems when
 * those don't exist anymore. Not public.
 */
class TransferFunctionUnknown implements TransferFunction {
    private final String typeId;
    private final ConfigurationNode config;
    private final boolean error;

    public TransferFunctionUnknown(String typeId, ConfigurationNode config, boolean error) {
        this.typeId = typeId;
        this.config = config.clone();
        this.error = error;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return new Serializer<TransferFunctionUnknown>() {
            @Override
            public String typeId() {
                return typeId;
            }

            @Override
            public String title() {
                return (error ? "LOAD ERROR [" : "UNKNOWN [") + typeId + "]";
            }

            @Override
            public TransferFunctionUnknown createNew(TransferFunctionHost host) {
                return TransferFunctionUnknown.this.clone();
            }

            @Override
            public TransferFunctionUnknown load(TransferFunctionHost host, ConfigurationNode config) {
                return new TransferFunctionUnknown(typeId(), config, error);
            }

            @Override
            public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionUnknown function) {
                config.setToExcept(function.config.clone(), Collections.singleton(TYPE_FIELD));
            }
        };
    }

    @Override
    public double map(double input) {
        return input; // Fallback
    }

    @Override
    public boolean isPure() {
        return true;
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 2, 2, MapColorPalette.COLOR_RED, "Unknown [" + typeId + "]");
    }

    @Override
    public void openDialog(Dialog dialog) {
    }

    @Override
    public DialogMode openDialogMode() {
        return DialogMode.NONE;
    }

    @Override
    public TransferFunctionUnknown clone() {
        return new TransferFunctionUnknown(typeId, config, error);
    }
}
