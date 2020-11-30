package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.collision.CollisionConfig;

/**
 * All standard TrainCarts built-in train and cart properties
 */
public class StandardProperties {

    public static final ITrainProperty<String> displayName = new ITrainProperty<String>() {
        @Override
        public List<String> getNames() {
            return Arrays.asList("displayname", "dname", "setdisplayname", "setdname");
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "displayName", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "displayName", value);
        }
    };

    public static final FieldBackedStandardTrainProperty<CollisionConfig> collision = new FieldBackedStandardTrainProperty<CollisionConfig>() {

        @Override
        public List<String> getNames() {
            return Arrays.asList(
                    "playercollision",
                    "misccollision",
                    "traincollision",
                    "blockcollision",
                    "collision", "collide",
                    "linking", "link",
                    "pushplayers",
                    "pushmisc",
                    "push", "pushing"
            );
        }

        @Override
        public CollisionConfig getDefault() {
            return CollisionConfig.DEFAULT;
        }

        @Override
        public CollisionConfig getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.collision;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, CollisionConfig value) {
            holder.collision = value;
        }

        @Override
        public Optional<CollisionConfig> readFromConfig(ConfigurationNode config) {
            if (config.contains("trainCollision") && !config.get("trainCollision", true)) {
                // Collision is completely disabled, except for blocks
                // This is a legacy property, we no longer save it.
                CollisionConfig collision = CollisionConfig.CANCEL;
                if (config.contains("collision.block")) {
                    collision = collision.setBlockMode(config.get("collision.block", CollisionMode.DEFAULT));
                }
                return Optional.of(collision);
            } else if (config.isNode("collision")) {
                return Optional.of(CollisionConfig.fromConfig(config.getNode("collision")));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<CollisionConfig> value) {
            // Get rid of legacy trainCollision property (legacy)
            config.remove("trainCollision");

            if (value.isPresent()) {
                config.set("collision", value.get().toConfig());
            } else {
                config.remove("collision");
            }
        }
    };

    public static final FieldBackedStandardTrainProperty.StandardDouble speedLimit = new FieldBackedStandardTrainProperty.StandardDouble() {
        @Override
        public List<String> getNames() {
            return Arrays.asList("maxspeed", "speedlimit");
        }

        @Override
        public double getDoubleDefault() {
            return 0.4;
        }

        @Override
        public double getHolderDoubleValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.speedLimit;
        }

        @Override
        public void setHolderDoubleValue(FieldBackedStandardTrainPropertiesHolder holder, double value) {
            holder.speedLimit = value;
        }

        @Override
        public Optional<Double> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "speedLimit", Double.class);
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
            StandardDouble.super.set(properties, value);
        }
    };
}
