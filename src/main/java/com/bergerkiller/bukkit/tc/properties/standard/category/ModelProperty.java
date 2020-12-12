package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.AttachmentModelBoundToCart;

/**
 * Controls the Attachments Model used by a particular cart. This configures the outside
 * appearance of carts.
 */
public final class ModelProperty extends FieldBackedProperty<AttachmentModel> {

    @Override
    public AttachmentModel getDefault() {
        return null;
    }

    @Override
    public void onConfigurationChanged(CartProperties properties) {
        CartInternalData data = CartInternalData.get(properties);
        if (data.model != null) {
            if (properties.getConfig().isNode("model")) {
                ConfigurationNode modelConfig = properties.getConfig().getNode("model");
                if (data.model.isDefault()) {
                    // Model property added, load from new configuration
                    data.model.update(modelConfig);
                } else if (modelConfig != data.model.getConfig()) {
                    // Node was completely swapped out, reload
                    // Configuration has no equals() check we can use
                    data.model.update(modelConfig);
                }
            } else if (!data.model.isDefault()) {
                // Model property removed, reset to vanilla defaults
                resetToVanillaDefaults(properties);
            }
        }
    }

    @Override
    public AttachmentModel get(CartProperties properties) {
        CartInternalData data = CartInternalData.get(properties);
        if (data.model == null) {
            if (properties.getConfig().isNode("model")) {
                // Decode model and initialize
                data.model = new AttachmentModelBoundToCart(properties, properties.getConfig().getNode("model"));
            } else {
                // No model was set. Create a Vanilla model based on the Minecart information
                data.model = new AttachmentModelBoundToCart(properties, new ConfigurationNode());
                resetToVanillaDefaults(properties);
                data.model.setBoundToOwner(false);
            }
        }
        return data.model;
    }

    @Override
    public void set(CartProperties properties, AttachmentModel value) {
        CartInternalData data = CartInternalData.get(properties);
        if (value == null || value.isDefault()) {
            // Reset model to vanilla defaults and wipe configuration
            properties.getConfig().remove("model");
            if (data.model != null) {
                data.model.setBoundToOwner(false);
            }
            if (data.model != null && !data.model.isDefault()) {
                resetToVanillaDefaults(properties);
            }
        } else {
            // Clone configuration and update/assign model if one was initialized
            // If the model was vanilla defaults, it will set the model during update()
            if (data.model != null) {
                data.model.update(properties.getConfig().getNode("model"));
            } else {
                properties.getConfig().set("model", value.getConfig().clone());
            }
        }
    }

    @Override
    public AttachmentModel get(TrainProperties properties) {
        return properties.isEmpty() ? getDefault() : get(properties.get(0));
    }

    @Override
    public void set(TrainProperties properties, AttachmentModel value) {
        for (CartProperties cProp : properties) {
            set(cProp, value);
        }
    }

    @Override
    public Optional<AttachmentModel> readFromConfig(ConfigurationNode config) {
        if (config.isNode("model")) {
            return Optional.of(new AttachmentModel(config.getNode("model").clone()));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<AttachmentModel> value) {
        if (value.isPresent()) {
            config.set("model", value.get().getConfig().clone());
        } else {
            config.remove("model");
        }
    }

    private void resetToVanillaDefaults(CartProperties properties) {
        MinecartMember<?> member = properties.getHolder();
        EntityType entityType = (member == null) ? EntityType.MINECART : member.getEntity().getType();
        CartInternalData.get(properties).model.resetToDefaults(entityType);
    }
}
