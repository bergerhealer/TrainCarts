package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

/**
 * A special identity transfer function, where map() simply returns the input.
 * Not listed, internal use only.
 */
class TransferFunctionIdentity implements TransferFunction {
    public static final TransferFunctionIdentity INSTANCE = new TransferFunctionIdentity();
    public static final Serializer<TransferFunctionIdentity> SERIALIZER = new Serializer<TransferFunctionIdentity>() {
        @Override
        public String typeId() {
            return "IDENTITY";
        }

        @Override
        public String title() {
            return "Identity";
        }

        @Override
        public boolean isListed() {
            return false;
        }

        @Override
        public TransferFunctionIdentity createNew(TransferFunctionHost host) {
            return INSTANCE;
        }

        @Override
        public TransferFunctionIdentity load(TransferFunctionHost host, ConfigurationNode config) {
            return INSTANCE;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionIdentity function) {
        }
    };

    private TransferFunctionIdentity() {
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public double map(double input) {
        return input;
    }

    @Override
    public boolean isPure() {
        return true;
    }

    @Override
    public TransferFunction clone() {
        return INSTANCE;
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 2, 3, MapColorPalette.COLOR_YELLOW, "[Input]");
    }

    @Override
    public void openDialog(MapWidgetTransferFunctionDialog dialog) {
        /* Adds nothing. Also doesn't open anything as a result */
    }
}
