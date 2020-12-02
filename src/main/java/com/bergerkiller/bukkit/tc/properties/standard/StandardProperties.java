package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.BankingOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.WaitOptions;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

/**
 * All standard TrainCarts built-in train and cart properties
 */
public class StandardProperties {

    /**
     * Configures how trains roll inwards when turning
     */
    public static final FieldBackedStandardTrainProperty<BankingOptions> BANKING = new FieldBackedStandardTrainProperty<BankingOptions>() {

        @Override
        public List<String> getNames() {
            return Arrays.asList("banking");
        }

        @Override
        public BankingOptions getDefault() {
            return BankingOptions.DEFAULT;
        }

        @Override
        public BankingOptions getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.bankingOptionsData;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, BankingOptions value) {
            holder.bankingOptionsData = value;
        }

        @Override
        public Optional<BankingOptions> readFromConfig(ConfigurationNode config) {
            if (!config.isNode("banking")) {
                return Optional.empty();
            }

            ConfigurationNode banking = config.getNode("banking");
            return Optional.of(BankingOptions.create(
                    banking.get("strength", 0.0),
                    banking.get("smoothness", 0.0)
            ));
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<BankingOptions> value) {
            if (value.isPresent()) {
                BankingOptions data = value.get();
                ConfigurationNode banking = config.getNode("banking");
                banking.set("strength", data.strength());
                banking.set("smoothness", data.smoothness());
            } else {
                config.remove("banking");
            }
        }
    };

    /**
     * Configures train behavior for waiting on obstacles on the track ahead
     */
    public static final FieldBackedStandardTrainProperty<WaitOptions> WAIT = new FieldBackedStandardTrainProperty<WaitOptions>() {

        @Override
        public List<String> getNames() {
            return Arrays.asList("waitdistance", "waitdelay", "waitacceleration", "waitdeceleration");
        }

        @Override
        public WaitOptions getDefault() {
            return WaitOptions.DEFAULT;
        }

        @Override
        public WaitOptions getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.waitOptionsData;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, WaitOptions value) {
            holder.waitOptionsData = value;
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
            double distance = waitConfig.contains("distance")     ? waitConfig.get("distance",     0.0) : 0.0;
            double delay =    waitConfig.contains("delay")        ? waitConfig.get("delay",        0.0) : 0.0;
            double accel =    waitConfig.contains("acceleration") ? waitConfig.get("acceleration", 0.0) : 0.0;
            double decel =    waitConfig.contains("deceleration") ? waitConfig.get("deceleration", 0.0) : 0.0;
            return Optional.of(WaitOptions.create(distance, delay, accel, decel));
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<WaitOptions> value) {
            config.remove("waitDistance");
            if (value.isPresent()) {
                WaitOptions data = value.get();
                ConfigurationNode node = config.getNode("wait");
                node.set("distance",         (data.distance() == 0.0) ? null : data.distance());
                node.set("delay",               (data.delay() == 0.0) ? null : data.delay());
                node.set("acceleration", (data.acceleration() == 0.0) ? null : data.acceleration());
                node.set("deceleration", (data.deceleration() == 0.0) ? null : data.deceleration());
            } else {
                config.remove("wait");
            }
        }
    };

    /**
     * The persistent data stored for the sign skip feature of carts and trains.
     * This property is used internally by the SignSkipOptions class.
     */
    public static final IProperty<SignSkipOptions> SIGN_SKIP = new IProperty<SignSkipOptions>() {

        @Override
        public List<String> getNames() {
            return Collections.emptyList();
        }

        @Override
        public SignSkipOptions getDefault() {
            return SignSkipOptions.NONE;
        }

        @Override
        public Optional<SignSkipOptions> readFromConfig(ConfigurationNode config) {
            if (!config.isNode("skipOptions")) {
                return Optional.empty();
            }

            ConfigurationNode skipOptions = config.getNode("skipOptions");
            int ignoreCtr = skipOptions.get("ignoreCtr", 0);
            int skipCtr = skipOptions.get("skipCtr", 0);
            String filter = skipOptions.get("filter", "");
            Set<BlockLocation> signs = Collections.emptySet();
            if (skipOptions.contains("signs")) {
                List<String> signLocationNames = skipOptions.getList("signs", String.class);
                if (!signLocationNames.isEmpty()) {
                    signs = new LinkedHashSet<BlockLocation>(signLocationNames.size());
                    for (String signLocationName : signLocationNames) {
                        BlockLocation loc = BlockLocation.parseLocation(signLocationName);
                        if (loc != null) {
                            signs.add(loc);
                        }
                    }
                    signs = Collections.unmodifiableSet(signs);
                }
            }

            return Optional.of(SignSkipOptions.create(ignoreCtr, skipCtr, filter, signs));
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<SignSkipOptions> value) {
            if (value.isPresent()) {
                SignSkipOptions data = value.get();
                ConfigurationNode skipOptions = config.getNode("skipOptions");
                skipOptions.set("ignoreCtr", data.ignoreCounter());
                skipOptions.set("skipCtr", data.skipCounter());
                skipOptions.set("filter", data.filter());

                // Save signs in an offline friendly format
                if (data.hasSkippedSigns()) {
                    List<String> signs = skipOptions.getList("signs", String.class);
                    Iterator<BlockLocation> signBlockIter = data.skippedSigns().iterator();

                    int num_signs = 0;
                    while (signBlockIter.hasNext()) {
                        if (num_signs >= signs.size()) {
                            signs.add(signBlockIter.next().toString());
                        } else {
                            signs.set(num_signs, signBlockIter.next().toString());
                        }
                        ++num_signs;
                    }
                    while (signs.size() > num_signs) {
                        signs.remove(signs.size()-1);
                    }
                } else {
                    skipOptions.remove("signs");
                }
            } else {
                config.remove("skipOptions");
            }
        }

        @Override
        public SignSkipOptions get(CartProperties properties) {
            return properties.getStandardPropertiesHolder().signSkipOptionsData;
        }

        @Override
        public void set(CartProperties properties, SignSkipOptions value) {
            if (value.equals(SignSkipOptions.NONE)) {
                properties.getStandardPropertiesHolder().signSkipOptionsData = SignSkipOptions.NONE;
                this.writeToConfig(properties.getConfig(), Optional.empty());
            } else {
                properties.getStandardPropertiesHolder().signSkipOptionsData = value;
                this.writeToConfig(properties.getConfig(), Optional.of(value));
            }
        }

        @Override
        public SignSkipOptions get(TrainProperties properties) {
            return properties.getStandardPropertiesHolder().signSkipOptionsData;
        }

        @Override
        public void set(TrainProperties properties, SignSkipOptions value) {
            if (value.equals(SignSkipOptions.NONE)) {
                properties.getStandardPropertiesHolder().signSkipOptionsData = SignSkipOptions.NONE;
                this.writeToConfig(properties.getConfig(), Optional.empty());
            } else {
                properties.getStandardPropertiesHolder().signSkipOptionsData = value;
                this.writeToConfig(properties.getConfig(), Optional.of(value));
            }
        }

        @Override
        public void onConfigurationChanged(CartProperties properties) {
            Optional<SignSkipOptions> opt = this.readFromConfig(properties.getConfig());
            properties.getStandardPropertiesHolder().signSkipOptionsData = opt.isPresent() ? opt.get() : SignSkipOptions.NONE;
        }

        @Override
        public void onConfigurationChanged(TrainProperties properties) {
            Optional<SignSkipOptions> opt = this.readFromConfig(properties.getConfig());
            properties.getStandardPropertiesHolder().signSkipOptionsData = opt.isPresent() ? opt.get() : SignSkipOptions.NONE;
        }
    };

    public static final FieldBackedStandardTrainProperty<Set<SlowdownMode>> SLOWDOWN = new FieldBackedStandardTrainProperty<Set<SlowdownMode>>() {
        private final Set<SlowdownMode> ALL = Collections.unmodifiableSet(EnumSet.allOf(SlowdownMode.class));
        private final Set<SlowdownMode> NONE = Collections.unmodifiableSet(EnumSet.noneOf(SlowdownMode.class));

        @Override
        public List<String> getNames() {
            return Arrays.asList("slowdown", "slowfriction", "slowgravity");
        }

        @Override
        public Set<SlowdownMode> getDefault() {
            return ALL;
        }

        @Override
        public Set<SlowdownMode> getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return Collections.unmodifiableSet(holder.slowdown);
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, Set<SlowdownMode> value) {
            holder.slowdown.clear();
            holder.slowdown.addAll(value);
        }

        @Override
        public Optional<Set<SlowdownMode>> readFromConfig(ConfigurationNode config) {
            if (!config.contains("slowDown")) {
                // Not set
                return Optional.empty();
            } else if (!config.isNode("slowDown")) {
                // Boolean all defaults / none
                return Optional.of(config.get("slowDown", true) ? ALL : NONE);
            } else {
                // Node with [name]: true/false options
                final EnumSet<SlowdownMode> modes = EnumSet.noneOf(SlowdownMode.class);
                ConfigurationNode slowDownNode = config.getNode("slowDown");
                for (SlowdownMode mode : SlowdownMode.values()) {
                    if (slowDownNode.contains(mode.getKey()) &&
                        slowDownNode.get(mode.getKey(), true))
                    {
                        modes.add(mode);
                    }
                }
                return Optional.of(Collections.unmodifiableSet(modes));
            }
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Set<SlowdownMode>> value) {
            if (value.isPresent()) {
                Set<SlowdownMode> modes = value.get();
                if (modes.isEmpty()) {
                    config.set("slowDown", false);
                } else if (modes.equals(ALL)) {
                    config.set("slowDown", true);
                } else {
                    ConfigurationNode slowDownNode = config.getNode("slowDown");
                    slowDownNode.clear();
                    for (SlowdownMode mode : SlowdownMode.values()) {
                        slowDownNode.set(mode.getKey(), modes.contains(mode));
                    }
                }
            } else {
                config.remove("slowDown");
            }
        }
    };

    public static final ITrainProperty<String> DISPLAYNAME = new ITrainProperty<String>() {
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

    public static final FieldBackedStandardTrainProperty<CollisionOptions> COLLISION = new FieldBackedStandardTrainProperty<CollisionOptions>() {

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
        public CollisionOptions getDefault() {
            return CollisionOptions.DEFAULT;
        }

        @Override
        public CollisionOptions getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.collision;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, CollisionOptions value) {
            holder.collision = value;
        }

        @Override
        public Optional<CollisionOptions> readFromConfig(ConfigurationNode config) {
            if (config.contains("trainCollision") && !config.get("trainCollision", true)) {
                // Collision is completely disabled, except for blocks
                // This is a legacy property, we no longer save it.
                CollisionOptions collision = CollisionOptions.CANCEL;
                if (config.contains("collision.block")) {
                    collision = collision.setBlockMode(config.get("collision.block", CollisionMode.DEFAULT));
                }
                return Optional.of(collision);
            } else if (config.isNode("collision")) {
                return Optional.of(CollisionOptions.fromConfig(config.getNode("collision")));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<CollisionOptions> value) {
            // Get rid of legacy trainCollision property (legacy)
            config.remove("trainCollision");

            if (value.isPresent()) {
                config.set("collision", value.get().toConfig());
            } else {
                config.remove("collision");
            }
        }
    };

    public static final FieldBackedStandardTrainProperty.StandardDouble SPEEDLIMIT = new FieldBackedStandardTrainProperty.StandardDouble() {
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
