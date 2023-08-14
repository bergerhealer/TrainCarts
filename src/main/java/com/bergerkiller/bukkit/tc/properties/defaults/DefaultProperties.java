package com.bergerkiller.bukkit.tc.properties.defaults;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Caches the information of default property values. These can be applied to a train
 * in an efficient manner, or differences between the defaults and those of a train/cart
 * configuration can be detected.
 */
public class DefaultProperties {
    private final ConfigurationNode config;
    private final List<DefaultProperty<?>> properties;
    private final List<DefaultProperty<?>> propertiesWithValues;

    /**
     * Generates the default properties using the configuration specified
     *
     * @param defaultConfig Default Configuration to parse
     * @return new DefaultProperties representation
     */
    public static DefaultProperties of(ConfigurationNode defaultConfig) {
        return new DefaultProperties(defaultConfig);
    }

    private DefaultProperties(ConfigurationNode config) {
        Collection<IProperty<Object>> registeredProperties = IPropertyRegistry.instance().all();

        this.config = config;
        this.properties = new ArrayList<>(registeredProperties.size());
        this.propertiesWithValues = new ArrayList<>(registeredProperties.size());
        for (IProperty<?> property : registeredProperties) {
            if (property.isAppliedAsDefault()) {
                DefaultProperty<?> defaultProperty;
                if (property instanceof ICartProperty) {
                    defaultProperty = new DefaultCartProperty<>(property, config);
                } else {
                    defaultProperty = new DefaultTrainProperty<>(property, config);
                }
                this.properties.add(defaultProperty);
                if (defaultProperty.set) {
                    this.propertiesWithValues.add(defaultProperty);
                }
            }
        }
    }

    /**
     * Gets the underlying YAML configuration of these default properties
     *
     * @return default YAML configuration
     */
    public ConfigurationNode getConfig() {
        return config;
    }

    /**
     * Applies these default properties to a train and all its carts.
     *
     * @param properties Train Properties to apply these default properties to
     */
    public void applyTo(TrainProperties properties) {
        // Read all properties TrainCarts knows about from the configuration
        // This will read and apply both train and cart properties
        for (DefaultProperty<?> defaultProperty : propertiesWithValues) {
            defaultProperty.applyTo(properties);
        }

        // Fire onPropertiesChanged, if possible
        properties.tryUpdate();
        for (CartProperties prop : properties) {
            prop.tryUpdate();
        }
    }

    /**
     * Applies these default properties to a cart. Note that if the default includes
     * train properties, the train is updated too.
     *
     * @param properties Cart Properties to apply these default properties to
     */
    public void applyTo(CartProperties properties) {
        // Read all properties TrainCarts knows about from the configuration
        // This will read and apply both train and cart properties
        for (DefaultProperty<?> defaultProperty : propertiesWithValues) {
            defaultProperty.applyTo(properties);
        }

        // Fire onPropertiesChanged, if possible
        properties.tryUpdate();
    }

    /**
     * Gets whether a Player has permission to use a particular train configuration.
     * If a property or feature is set in the (saved) train configuration that a
     * Player cannot use, then a message is sent to the player explaining this property
     * cannot be used and false is returned.
     *
     * @param sender Command sender or Player that is trying to use a particular train configuration
     * @param spawnableGroup Spawnable train configuration requested
     * @return True if the train configuration can be used, False if permission is denied
     */
    public boolean checkSavedTrainPermissions(CommandSender sender, SpawnableGroup spawnableGroup) {
        List<ConfigurationNode> cartConfigs = new ArrayList<>(spawnableGroup.getMembers().size());
        for (SpawnableMember member : spawnableGroup.getMembers()) {
             cartConfigs.add(member.getConfig());
        }

        return checkSavedTrainPermissions(sender, spawnableGroup.getConfig(), cartConfigs);
    }

    /**
     * Gets whether a Player has permission to use a particular train configuration.
     * If a property or feature is set in the (saved) train configuration that a
     * Player cannot use, then a message is sent to the player explaining this property
     * cannot be used and false is returned.
     *
     * @param player Command sender or Player that is trying to use a particular train configuration
     * @param trainConfig Train configuration requested
     * @return True if the train configuration can be used, False if permission is denied
     */
    public boolean checkSavedTrainPermissions(CommandSender player, ConfigurationNode trainConfig) {
        List<ConfigurationNode> cartConfigs = trainConfig.getNodeList("carts");

        return checkSavedTrainPermissions(player, trainConfig, cartConfigs);
    }

    private boolean checkSavedTrainPermissions(CommandSender sender, ConfigurationNode trainConfig, List<ConfigurationNode> cartConfigs) {
        boolean canChangeProperties = Permission.COMMAND_PROPERTIES.has(sender) ||
                                      Permission.COMMAND_GLOBALPROPERTIES.has(sender);

        // Check any of the properties were changed from the defaults for this train
        for (DefaultProperty<?> property : properties) {
            if (property.isEqual(trainConfig, cartConfigs)) {
                continue;
            }

            if (!canChangeProperties) {
                Localization.PROPERTY_NOPERM.message(sender, property.permissionName);
                Localization.PROPERTY_NOPERM_ANY.message(sender);
                return false;
            }

            if (property.property.hasPermission(sender, property.permissionName)) {
                continue;
            }

            Localization.PROPERTY_NOPERM.message(sender, property.permissionName);
            return false;
        }

        return true;
    }

    private static abstract class DefaultProperty<T> {
        public final IProperty<T> property;
        public final String permissionName;
        public final boolean set;
        public final T value;

        public DefaultProperty(IProperty<T> property, ConfigurationNode config) {
            this.property = property;
            this.permissionName = property.getPermissionName();

            Optional<T> valueOpt = property.readFromConfig(config);
            this.set = valueOpt.isPresent();
            this.value = valueOpt.orElse(property.getDefault());
        }

        public void applyTo(IProperties properties) {
            properties.set(property, value);
        }

        public abstract boolean isEqual(ConfigurationNode trainConfig, List<ConfigurationNode> cartConfigs);
    }

    private static class DefaultTrainProperty<T> extends DefaultProperty<T> {

        public DefaultTrainProperty(IProperty<T> property, ConfigurationNode config) {
            super(property, config);
        }

        @Override
        public boolean isEqual(ConfigurationNode trainConfig, List<ConfigurationNode> cartConfigs) {
            return LogicUtil.bothNullOrEqual(
                    property.readFromConfig(trainConfig).orElse(property.getDefault()),
                    value);
        }
    }

    private static class DefaultCartProperty<T> extends DefaultProperty<T> {

        public DefaultCartProperty(IProperty<T> property, ConfigurationNode config) {
            super(property, config);
        }

        @Override
        public boolean isEqual(ConfigurationNode trainConfig, List<ConfigurationNode> cartConfigs) {
            for (ConfigurationNode cartConfig : cartConfigs) {
                if (!LogicUtil.bothNullOrEqual(
                        property.readFromConfig(cartConfig).orElse(property.getDefault()),
                        value)
                ) {
                    return false;
                }
            }
            return true;
        }
    }
}
