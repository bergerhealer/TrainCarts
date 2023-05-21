package com.bergerkiller.bukkit.tc.attachments.config;

import java.util.function.Supplier;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.properties.standard.type.AttachmentModelBoundToCart;
import com.bergerkiller.bukkit.tc.utils.SetCallbackCollector;
import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;

/**
 * The configuration and properties of a single Cart attachment model.
 * Everything configurable in the attachment editor is described here.
 */
public class AttachmentModel implements SavedAttachmentModelStore.ModelUsing {
    private final AttachmentTypeRegistry registry;
    private final AttachmentConfigTracker tracker;
    private final SavedAttachmentModelStore modelStore;
    private AttachmentModelMeta _meta;

    public AttachmentModel(ConfigurationNode config) {
        this(LogicUtil.constantSupplier(config));
    }

    public AttachmentModel(Supplier<ConfigurationNode> configSupplier) {
        this.registry = AttachmentTypeRegistry.instance();
        this.tracker = new AttachmentConfigTracker(configSupplier, TrainCarts.plugin);
        this.modelStore = TrainCarts.plugin.getSavedAttachmentModels();
        this._meta = new AttachmentModelMeta();
    }

    /**
     * Gets the configuration of this model. The configuration
     * returned is for the root node, and stores child attachments recursively.
     * The configuration does not include (recursive) model attachment
     * configurations.
     *
     * @return root configuration
     */
    public ConfigurationNode getConfig() {
        return tracker.getConfig();
    }

    /**
     * Gets the configuration for a single node in the model.
     * If no node is found at this path, null is returned.
     * 
     * @param targetPath of the node
     * @return node configuration
     */
    public ConfigurationNode getNodeConfig(int[] targetPath) {
        AttachmentConfig child = tracker.getRoot().get().child(targetPath);
        return (child == null) ? null : child.config();
    }

    /**
     * Gets the root attachment configuration of this attachment model. From this root
     * the child attachments can be found, recursively. Modifying the configurations
     * of these attachments will update this attachment model.<br>
     * <br>
     * This attachment configuration tree does not include the (hidden) model attachments,
     * and their configurations.<br>
     * <br>
     * If you are storing this root reference for longer, please read the information
     * about {@link AttachmentConfig.RootReference RootReference validity}.
     *
     * @return Root attachment
     */
    public AttachmentConfig.RootReference getRoot() {
        return tracker.getRoot();
    }

    /**
     * Gets the attachment configuration tracker of this attachment model. This tracker
     * only tracks changes that happen to {@link #getConfig()}, not the changes that
     * happen in recursive model attachments.<br>
     * <br>
     * The tracker can be used to discover the attachment configurations that exist,
     * as well as register a listener for receiving notifications about changes to
     * these attachment configurations.
     *
     * @return config tracker
     */
    public AttachmentConfigTracker getConfigTracker() {
        return tracker;
    }

    public int getSeatCount() {
        return calcMeta().seatCount;
    }

    public float getCartLength() {
        return calcMeta().cartLength;
    }

    public double getWheelDistance() {
        return calcMeta().wheelDistance;
    }

    public double getWheelCenter() {
        return calcMeta().wheelCenter;
    }

    /**
     * Gets whether this is a default model. This will be a default vanilla minecart model,
     * for which no configuration is stored in the train's properties. The moment this
     * configuration is modified, its configuration is saved and no longer default.
     *
     * @return True if this is the default minecart model configuration
     * @see #resetToDefaults()
     */
    public boolean isDefault() {
        return false;
    }

    /**
     * Sets this model to the default model for a Minecart. Has special logic for cart-bound
     * attachment models.
     */
    public void resetToDefaults() {
        update(AttachmentModelBoundToCart.createDefaults(registry, EntityType.MINECART));
    }

    /**
     * Sets this model to a named model from the model store
     * 
     * @param modelName to link to
     */
    public void resetToName(String modelName) {
        ConfigurationNode config = new ConfigurationNode();
        registry.toConfig(config, CartAttachmentModel.TYPE);
        config.set("model", modelName);
        this.update(config, false);
    }

    /**
     * Updates the full configuration of this attachment model. All users of this model
     * will be notified. This will result in a complete re-spawning of the model. If
     * smaller changes are desired, modify {@link #getConfig()} directly and call
     * {@link #sync()} instead.
     * 
     * @param newConfig
     */
    public void update(ConfigurationNode newConfig) {
        if (getConfig() != newConfig) {
            getConfig().clear(); // Prevents attachments being re-used
            getConfig().setTo(newConfig);
        }
        sync();
    }

    /**
     * Updates the full configuration of this attachment model. All users of this model
     * will be notified if notify is true.
     *
     * @param newConfig
     * @param notify True to not notify the changes, False for a silent update
     * @deprecated Use {@link #update(ConfigurationNode)} and/or {@link #sync()} instead
     */
    @Deprecated
    public void update(ConfigurationNode newConfig, boolean notify) {
        update(newConfig);
    }

    /**
     * Forces all configuration changes that have occurred so far to be applied right now.
     * Is done automatically in the background every tick.
     */
    public void sync() {
        tracker.sync();
    }

    /**
     * Updates only a single leaf of the attachment model tree. All users of this model
     * will be notified. It is also fine to update the child in {@link #getConfig()}
     * and call {@link #sync()} afterwards.
     * 
     * @param targetPath to the leaf that changed
     * @param newConfig for the leaf
     */
    public void updateNode(int[] targetPath, ConfigurationNode newConfig) {
        AttachmentConfig child = tracker.getRoot().get().child(targetPath);
        if (child != null) {
            child.setConfig(newConfig);
            sync();
        }
    }

    /**
     * Removes a single leaf of the attachment model tree. Removes all children of the
     * attachment too.
     *
     * @param targetPath to the leaf to remove
     */
    public void removeNode(int[] targetPath) {
        AttachmentConfig child = tracker.getRoot().get().child(targetPath);
        if (child != null) {
            child.config().remove();
            sync();
        }
    }

    /**
     * Updates only a single leaf of the attachment model tree. All users of this model
     * will be notified if notify is true.
     * 
     * @param targetPath to the leaf that changed
     * @param newConfig for the leaf
     * @param notify True to notify the changes, False for a silent update
     * @deprecated Use {@link #updateNode(int[], ConfigurationNode)} instead
     */
    @Deprecated
    public void updateNode(int[] targetPath, ConfigurationNode newConfig, boolean notify) {
        updateNode(targetPath, newConfig);
    }

    @Override
    public void getUsedModels(SetCallbackCollector<SavedAttachmentModel> collector) {
        modelStore.findModelsUsedInConfiguration(getRoot().get(), collector);
    }

    private AttachmentModelMeta calcMeta() {
        AttachmentModelMeta meta = this._meta;
        if (meta.valid()) {
            return meta;
        } else {
            // Recompute
            return meta = this._meta = new AttachmentModelMeta(registry, tracker.getRoot());
        }
    }

    /**
     * Gets a default attachment model for a vanilla minecart
     *
     * @param minecartType EntityType of the Minecart
     * @return Attachment Model
     */
    public static AttachmentModel getDefaultModel(EntityType minecartType) {
        return new AttachmentModel(AttachmentModelBoundToCart.createDefaults(
                AttachmentTypeRegistry.instance(), minecartType));
    }

    /**
     * Metadata that is cached about an attachment model. Is automatically
     * re-generated when the configuration changes.
     */
    private static class AttachmentModelMeta {
        public final AttachmentConfig.RootReference root;
        public final int seatCount;
        public final float cartLength;
        public final double wheelCenter;
        public final double wheelDistance;

        public AttachmentModelMeta() {
            this.root = AttachmentConfig.RootReference.NONE;
            this.seatCount = 0;
            this.cartLength = 0.98f;
            this.wheelCenter = 0.0;
            this.wheelDistance = 0.0;
        }

        public AttachmentModelMeta(AttachmentTypeRegistry registry, AttachmentConfig.RootReference root) {
            this.root = root;

            AttachmentConfig rootAtt = root.get();
            this.seatCount = calcSeatCount(registry, rootAtt);
            if (rootAtt.config().isNode("physical")) {
                ConfigurationNode physical = rootAtt.config().getNode("physical");
                this.cartLength = physical.get("cartLength", 0.98f);
                this.wheelCenter = physical.get("wheelCenter", 0.0);
                this.wheelDistance = physical.get("wheelDistance", 0.0);
            } else {
                this.cartLength = 0.98f;
                this.wheelCenter = 0.0;
                this.wheelDistance = 0.0;
            }
        }

        public boolean valid() {
            return root.valid();
        }

        private static int calcSeatCount(AttachmentTypeRegistry registry, AttachmentConfig attachment) {
            int count = 0;
            if (registry.find(attachment.typeId()) == CartAttachmentSeat.TYPE) {
                count = 1;
            }
            for (AttachmentConfig child : attachment.children()) {
                count += calcSeatCount(registry, child);
            }
            return count;
        }
    }
}
