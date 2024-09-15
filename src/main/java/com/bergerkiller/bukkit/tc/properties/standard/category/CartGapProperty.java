package com.bergerkiller.bukkit.tc.properties.standard.category;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import org.bukkit.command.CommandSender;

import java.util.Optional;

/**
 * Extra distance kept between carts. If omitted in the YAML the gap configured in config.yml
 * is used. Setting it to the same value as config.yml will clear the setting.
 * So this acts more as an override.
 */
public final class CartGapProperty extends FieldBackedStandardTrainProperty.StandardDouble {

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        // This is planned to be configurable in the attachment editor only
        return Permission.COMMAND_GIVE_EDITOR.has(sender);
    }

    @Override
    public double getDoubleDefault() {
        return TCConfig.cartDistanceGap;
    }

    @Override
    public double getDoubleData(TrainInternalData data) {
        return data.cartGap;
    }

    @Override
    public void setDoubleData(TrainInternalData data, double value) {
        data.cartGap = value;
    }

    @Override
    public Optional<Double> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "cart_gap", double.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
        Util.setConfigOptional(config, "cart_gap", value);
    }
}
