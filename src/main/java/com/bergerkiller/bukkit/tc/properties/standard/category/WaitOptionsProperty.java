package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import com.bergerkiller.bukkit.tc.Permission;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.commands.parsers.AccelerationParser;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.WaitOptions;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Default;

/**
 * Stores the wait options for a train. This controls whether trains wait
 * for obstacles ahead on the track.
 */
public final class WaitOptionsProperty extends FieldBackedStandardTrainProperty<WaitOptions> {

    @CommandTargetTrain
    @PropertyCheckPermission("waitdistance")
    @Command("train wait distance <blocks>")
    @CommandDescription("Sets the distance to keep to other trains")
    private void setDistanceProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="blocks", description="Number of blocks distance") double distance
    ) {
        properties.update(this, options -> WaitOptions.create(
                distance,
                options.delay(),
                options.acceleration(),
                options.deceleration(),
                options.predict()));

        getDistanceProperty(sender, properties);
    }

    @Command("train wait distance")
    @CommandDescription("Displays the distance to keep to other trains")
    private void getDistanceProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Distance to keep to other trains: "
                + ChatColor.GREEN + properties.getWaitDistance() + " blocks");
    }

    @CommandTargetTrain
    @PropertyCheckPermission("waitdelay")
    @Command("train wait delay <time>")
    @CommandDescription("Sets the time a train waits when fully stopped to wait")
    private void setDelayProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="time", description="Time to wait in seconds") double delay
    ) {
        properties.update(this, options -> WaitOptions.create(
                options.distance(),
                delay,
                options.acceleration(),
                options.deceleration(),
                options.predict()));

        getDelayProperty(sender, properties);
    }

    @Command("train wait delay")
    @CommandDescription("Displays the time a train waits when fully stopped to wait")
    private void getDelayProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Wait delay when stopped: "
                + ChatColor.GREEN + properties.getWaitDelay() + " seconds");
    }

    @CommandTargetTrain
    @PropertyCheckPermission("waitacceleration")
    @Command("train wait acceleration <acceleration> [deceleration]")
    @CommandDescription("Sets the rate of acceleration (and deceleration) of the train")
    private void setAccelerationProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="acceleration", parserName=AccelerationParser.NAME,
                            description="Acceleration in blocks/tick\u00B2") double acceleration,
            final @Argument(value="deceleration", parserName=AccelerationParser.NAME,
                            description="De-acceleration in blocks/tick\u00B2") @Default("NaN") double deceleration
    ) {
        properties.update(this, options -> WaitOptions.create(
                options.distance(),
                options.delay(),
                acceleration,
                Double.isNaN(deceleration) ? acceleration : deceleration,
                options.predict()));

        getAccelerationProperty(sender, properties);
    }

    @Command("train wait acceleration")
    @CommandDescription("Displays the rate of acceleration (and deceleration) of the train")
    private void getAccelerationProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.getWaitAcceleration() == properties.getWaitDeceleration()) {
            sender.sendMessage(ChatColor.YELLOW + "Speeds up and slows down to wait at: "
                    + ChatColor.GREEN + properties.getWaitAcceleration() + " blocks/tick\u00B2");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Slows down to wait at: "
                    + ChatColor.GREEN + properties.getWaitDeceleration() + " blocks/tick\u00B2");
            sender.sendMessage(ChatColor.YELLOW + "Speeds up after waiting at: "
                    + ChatColor.GREEN + properties.getWaitAcceleration() + " blocks/tick\u00B2");
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("waitprediction")
    @Command("train wait predict <predict>")
    @CommandDescription("Sets whether the train will predict routing up ahead")
    private void setPredictProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument(value="predict", description="Whether to predict") boolean predict
    ) {
        properties.update(this, options -> WaitOptions.create(
                options.distance(),
                options.delay(),
                options.acceleration(),
                options.deceleration(),
                predict));

        getPredictProperty(sender, properties);
    }

    @Command("train wait predict")
    @CommandDescription("Displays whether the train will predict routing up ahead")
    private void getPredictProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Predict path up ahead: "
                + Localization.boolStr(properties.isWaitPredicted()));
    }

    @PropertyParser("waitdistance|wait distance")
    public WaitOptions parseWaitDistance(PropertyParseContext<WaitOptions> context) {
        return WaitOptions.create(context.inputDouble(),
                context.current().delay(),
                context.current().acceleration(),
                context.current().deceleration(),
                context.current().predict());
    }

    @PropertyParser("waitdelay|wait delay")
    public WaitOptions parseWaitDelay(PropertyParseContext<WaitOptions> context) {
        return WaitOptions.create(context.current().distance(),
                context.inputDouble(),
                context.current().acceleration(),
                context.current().deceleration(),
                context.current().predict());
    }

    @PropertyParser("waitacceleration|wait acceleration")
    public WaitOptions parseWaitAcceleration(PropertyParseContext<WaitOptions> context) {
        String[] args = context.input().trim().split(" ");
        double newAcceleration;
        double newDeceleration;
        if (args.length >= 2) {
            newAcceleration = Util.parseAcceleration(args[0], Double.NaN);
            newDeceleration = Util.parseAcceleration(args[1], Double.NaN);
        } else {
            newAcceleration = newDeceleration = Util.parseAcceleration(context.input(), Double.NaN);
        }
        if (Double.isNaN(newAcceleration)) {
            throw new PropertyInvalidInputException("Acceleration is not a number or acceleration expression");
        }
        if (Double.isNaN(newDeceleration)) {
            throw new PropertyInvalidInputException("Deceleration is not a number or acceleration expression");
        }
        return WaitOptions.create(context.current().distance(),
                context.current().delay(),
                newAcceleration,
                newDeceleration,
                context.current().predict());
    }

    @PropertyParser("waitpredicted|waitprediction|wait predicted|wait predict")
    public WaitOptions parseWaitPrediction(PropertyParseContext<WaitOptions> context) {
        return WaitOptions.create(context.current().distance(),
                context.current().delay(),
                context.current().acceleration(),
                context.current().deceleration(),
                context.inputBoolean());
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_WAIT.has(sender);
    }

    @Override
    public WaitOptions getDefault() {
        return WaitOptions.DEFAULT;
    }

    @Override
    public WaitOptions getData(TrainInternalData data) {
        return data.waitOptionsData;
    }

    @Override
    public void setData(TrainInternalData data, WaitOptions value) {
        data.waitOptionsData = value;
    }

    @Override
    public Optional<WaitOptions> readFromConfig(ConfigurationNode config) {
        if (!config.isNode("wait")) {
            if (config.contains("waitDistance")) {
                return Optional.of(WaitOptions.create(config.get("waitDistance", 0.0)));
            } else {
                return Optional.empty();
            }
        }

        ConfigurationNode waitConfig = config.getNode("wait");
        double distance = waitConfig.get("distance", 0.0);
        double delay = waitConfig.get("delay", 0.0);
        double accel = waitConfig.get("acceleration", 0.0);
        double decel = waitConfig.get("deceleration", 0.0);
        boolean predict = waitConfig.get("predict", true);
        return Optional.of(WaitOptions.create(distance, delay, accel, decel, predict));
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<WaitOptions> value) {
        config.remove("waitDistance");
        if (value.isPresent()) {
            WaitOptions data = value.get();
            ConfigurationNode node = config.getNode("wait");
            node.set("distance", data.distance());
            node.set("delay", data.delay());
            node.set("acceleration", data.acceleration());
            node.set("deceleration", data.deceleration());
            node.set("predict", data.predict());
        } else {
            config.remove("wait");
        }
    }
}
