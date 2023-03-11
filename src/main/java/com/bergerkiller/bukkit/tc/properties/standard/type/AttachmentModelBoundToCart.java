package com.bergerkiller.bukkit.tc.properties.standard.type;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.yaml.YamlChangeListener;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentEntity;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * An AttachmentModel that is tied with the properties of a cart. Handles the logic
 * of switching between a vanilla default model and a user-configured model, updating
 * the model field in the configuration automatically.
 */
public class AttachmentModelBoundToCart extends AttachmentModel {
    private final ModelConfigSupplier configSupplier;

    public AttachmentModelBoundToCart(CartProperties properties) {
        this(new ModelConfigSupplier(properties));
    }

    private AttachmentModelBoundToCart(ModelConfigSupplier configSupplier) {
        super(configSupplier);
        this.configSupplier = configSupplier;
    }

    @Override
    public boolean isDefault() {
        return configSupplier.isDefault();
    }

    @Override
    public void resetToDefaults() {
        configSupplier.makeDefaults();
        sync();
    }

    private static class ModelConfigSupplier implements Supplier<ConfigurationNode> {
        private final CartProperties properties;
        private EntityType cartEntityType;
        private EntityType defaultConfigEntityType;
        private ConfigurationNode defaultConfig;

        public ModelConfigSupplier(CartProperties properties) {
            this.properties = properties;
            this.cartEntityType = null;
            this.defaultConfigEntityType = null;
            this.defaultConfig = null;
        }

        public boolean isDefault() {
            return !properties.getConfig().isNode("model");
        }

        @Override
        public ConfigurationNode get() {
            ConfigurationNode config = properties.getConfig();
            if (config.isNode("model")) {
                // If it was a default configuration before, clean that up again
                defaultConfig = null;
                defaultConfigEntityType = null;

                return config.getNode("model");
            } else {
                // Create a default configuration based on the entity type of the minecart
                // Entity type might not be available, in which case this must be corrected
                // Stop doing this once the Minecart entity type is found (minecart is loaded)
                if (cartEntityType == null) {
                    MinecartMember<?> member = properties.getHolder();
                    if (member != null && member.getEntity() != null) {
                        cartEntityType = member.getEntity().getType();
                    }
                }

                // If different, re-create. Also does initial initialization.
                EntityType entityType = (cartEntityType == null) ? EntityType.MINECART : cartEntityType;
                if (entityType != defaultConfigEntityType) {
                    defaultConfigEntityType = entityType;
                    defaultConfig = createDefaults(AttachmentTypeRegistry.instance(), entityType);

                    // If changes happen to this configuration, then store it in the parent model configuration
                    // Don't store if meanwhile a different configuration is already stored
                    final ConfigurationNode currConfig = defaultConfig;
                    currConfig.addChangeListener(new YamlChangeListener() {
                        @Override
                        public void onNodeChanged(YamlPath yamlPath) {
                            currConfig.removeChangeListener(this);
                            if (defaultConfig != currConfig) {
                                return;
                            }

                            // Store the (changed) configuration node in the model field
                            // From now on, the properties are no longer default
                            ConfigurationNode currCartConfig = properties.getConfig();
                            if (!currCartConfig.isNode("model")) {
                                currCartConfig.set("model", currConfig);
                                defaultConfig = null;
                                defaultConfigEntityType = null;
                            }
                        }
                    });
                }

                return defaultConfig;
            }
        }

        public void makeDefaults() {
            ConfigurationNode config = properties.getConfig();
            config.remove("model");
        }
    }

    /**
     * Creates the default cart model configuration for a particular vanilla Minecart type.
     *
     * @param typeRegistry Attachment type registry
     * @param entityType EntityType of the Minecart Entity
     * @return Default configuration for this type of Minecart
     */
    public static ConfigurationNode createDefaults(AttachmentTypeRegistry typeRegistry, EntityType entityType) {
        ConfigurationNode config = new ConfigurationNode();
        typeRegistry.toConfig(config, CartAttachmentEntity.TYPE);
        config.set("entityType", entityType);
        if (entityType == EntityType.MINECART) {
            ConfigurationNode seatNode = new ConfigurationNode();
            typeRegistry.toConfig(seatNode, CartAttachmentSeat.TYPE);
            config.setNodeList("attachments", Arrays.asList(seatNode));
        }
        return config;
    }
}
