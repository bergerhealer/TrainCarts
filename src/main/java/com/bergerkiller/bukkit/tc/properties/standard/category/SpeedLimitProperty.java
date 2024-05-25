package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.PropertySelectorCondition;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;

/**
 * Changes the maximum speed a train can move at
 */
public final class SpeedLimitProperty extends FieldBackedStandardTrainProperty.StandardDouble {

    @CommandTargetTrain
    @PropertyCheckPermission("maxspeed")
    @Command("train maxspeed|speedlimit <speed>")
    @CommandDescription("Sets a new  speed limit for the train")
    private void trainSetSpeedLimit(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("speed") FormattedSpeed speed
    ) {
        properties.setSpeedLimit(speed.getValue());
        trainGetSpeedLimit(sender, properties);
    }

    @Command("train maxspeed|speedlimit")
    @CommandDescription("Reads the current speed limit set for the train")
    private void trainGetSpeedLimit(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        double currSpeed = properties.hasHolder() ? properties.getHolder().head().getRealSpeedLimited() : 0.0;

        sender.sendMessage(ChatColor.YELLOW + "Maximum speed: " +
                formatSpeed(properties.getSpeedLimit(), ChatColor.WHITE));
        sender.sendMessage(ChatColor.YELLOW + "Current speed: " +
                formatSpeed(currSpeed, (currSpeed == properties.getSpeedLimit())
                        ? ChatColor.RED : ChatColor.WHITE));
    }

    private static String formatSpeed(double speed, ChatColor baseColor) {
        double speedKMH = MathUtil.round(speed * (20.0 * 3600.0) / 1000.0, 2);
        double speedMPH = MathUtil.round(speed * (20.0 * 3600.0) / 1609.344, 2);
        return baseColor.toString() + MathUtil.round(speed, 4) + " blocks/tick (" +
               ChatColor.BLUE + speedKMH + " km/h" + baseColor + " / " +
               ChatColor.BLUE + speedMPH + " mph" +
                baseColor + ")";
    }

    @PropertyParser("maxspeed|speedlimit")
    public double parse(String input) {
        double result = Util.parseVelocity(input, Double.NaN);
        if (Double.isNaN(result)) {
            throw new PropertyInvalidInputException("Not a valid number or speed expression");
        }

        return result;
    }

    @PropertySelectorCondition("speedlimit")
    public double getSelectorDoubleValue(TrainProperties properties) {
        return getDouble(properties);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_MAXSPEED.has(sender);
    }

    @Override
    public double getDoubleDefault() {
        return 0.4;
    }

    @Override
    public double getDoubleData(TrainInternalData data) {
        return data.speedLimit;
    }

    @Override
    public void setDoubleData(TrainInternalData data, double value) {
        data.speedLimit = value;
    }

    @Override
    public Optional<Double> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "speedLimit", double.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
        Util.setConfigOptional(config, "speedLimit", value);
    }

    @Override
    public void set(TrainProperties properties, Double value) {
        // Limit the value between 0.0 and the maximum allowed speed
        double valuePrim = value.doubleValue();
        if (valuePrim < 0.0) {
            value = Double.valueOf(0.0);
        } else if (valuePrim > TCConfig.maxVelocity) {
            value = Double.valueOf(TCConfig.maxVelocity);
        }

        // Standard set
        super.set(properties, value);
    }
}
