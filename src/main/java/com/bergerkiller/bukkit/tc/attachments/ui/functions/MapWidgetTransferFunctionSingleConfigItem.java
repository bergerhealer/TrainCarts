package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.util.function.BooleanSupplier;

/**
 * Reads and writes a single transfer function from a config at a particular key
 */
public abstract class MapWidgetTransferFunctionSingleConfigItem extends MapWidgetTransferFunctionSingleItem {
    private final ConfigurationNode config;
    private final String configKey;

    public MapWidgetTransferFunctionSingleConfigItem(
            final TransferFunctionHost host,
            final ConfigurationNode config, String configKey,
            final BooleanSupplier isBooleanInput
    ) {
        super(host, config.getNodeIfExists(configKey), isBooleanInput);
        this.config = config;
        this.configKey = configKey;
    }

    @Override
    public void onChanged(TransferFunction.Holder<TransferFunction> function) {
        if (function.isDefault()) {
            config.remove(configKey);
        } else {
            config.set(configKey, host.saveFunction(function.getFunction()));
        }
    }
}
