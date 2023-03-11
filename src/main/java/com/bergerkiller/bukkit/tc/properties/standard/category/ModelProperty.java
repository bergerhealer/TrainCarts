package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
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
            data.model.sync();
        }
    }

    @Override
    public AttachmentModel get(CartProperties properties) {
        CartInternalData data = CartInternalData.get(properties);
        if (data.model == null) {
            data.model = new AttachmentModelBoundToCart(properties);
        }
        return data.model;
    }

    @Override
    public void set(CartProperties properties, AttachmentModel value) {
        CartInternalData data = CartInternalData.get(properties);
        if (value == null || value.isDefault()) {
            // Reset model to vanilla defaults and wipe configuration
            if (data.model != null) {
                data.model.resetToDefaults();
            } else {
                // Best we can do
                properties.getConfig().remove("model");
            }
        } else {
            // Clone configuration and update/assign model if one was initialized
            // If the model was vanilla defaults, it will set the model during update()
            if (data.model == null) {
                properties.getConfig().set("model", value.getConfig().clone());
            } else if (data.model != value) {
                data.model.update(value.getConfig());
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
}
