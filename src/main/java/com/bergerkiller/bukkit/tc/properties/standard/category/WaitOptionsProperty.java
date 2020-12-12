package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.WaitOptions;

/**
 * Stores the wait options for a train. This controls whether trains wait
 * for obstacles ahead on the track.
 */
public final class WaitOptionsProperty extends FieldBackedStandardTrainProperty<WaitOptions> {

    @PropertyParser("waitdistance")
    public WaitOptions parseWaitDistance(PropertyParseContext<WaitOptions> context) {
        return WaitOptions.create(context.inputDouble(),
                context.current().delay(),
                context.current().acceleration(),
                context.current().deceleration());
    }

    @PropertyParser("waitdelay")
    public WaitOptions parseWaitDelay(PropertyParseContext<WaitOptions> context) {
        return WaitOptions.create(context.current().distance(),
                context.inputDouble(),
                context.current().acceleration(),
                context.current().deceleration());
    }

    @PropertyParser("waitacceleration")
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
                context.inputDouble(),
                newAcceleration,
                newDeceleration);
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
        return Optional.of(WaitOptions.create(distance, delay, accel, decel));
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
        } else {
            config.remove("wait");
        }
    }
}
