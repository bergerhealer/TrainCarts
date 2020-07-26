package com.bergerkiller.bukkit.tc.attachments.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentEntity;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

public class AttachmentModel {
    private final AttachmentTypeRegistry registry;
    private ConfigurationNode config;
    private List<AttachmentModelOwner> owners = new ArrayList<AttachmentModelOwner>();
    private int seatCount;
    private float cartLength;
    private double wheelCenter;
    private double wheelDistance;
    private boolean _isDefault;

    public AttachmentModel() {
        this(new ConfigurationNode());
    }

    public AttachmentModel(ConfigurationNode config) {
        this.registry = AttachmentTypeRegistry.instance();
        this.config = config;
        this._isDefault = false;
        this.computeProperties();
    }

    /**
     * Gets the full configuration of this entire model. The configuration
     * returned is for the root node, and stores child attachments recursively.
     * 
     * @return root configuration
     */
    public ConfigurationNode getConfig() {
        return this.config;
    }

    /**
     * Gets the configuration for a single node in the model.
     * If node node is found at this path, null is returned.
     * 
     * @param targetPath of the node
     * @return node configuration
     */
    public ConfigurationNode getNodeConfig(int[] targetPath) {
        ConfigurationNode config = this.config;
        for (int index : targetPath) {
            List<ConfigurationNode> attachments = config.getNodeList("attachments");
            if (attachments == null || index < 0 || index >= attachments.size()) {
                return null;
            }
            config = attachments.get(index);
        }
        return config;
    }

    public int getSeatCount() {
        return this.seatCount;
    }

    public float getCartLength() {
        return this.cartLength;
    }

    public double getWheelDistance() {
        return this.wheelDistance;
    }

    public double getWheelCenter() {
        return this.wheelCenter;
    }

    public boolean isDefault() {
        return this._isDefault;
    }

    /**
     * Sets this model to a default model for a Minecart
     * 
     * @param entityType of the Minecart
     */
    public void resetToDefaults(EntityType entityType) {
        ConfigurationNode config = new ConfigurationNode();
        registry.toConfig(config, CartAttachmentEntity.TYPE);
        config.set("entityType", entityType);
        if (entityType == EntityType.MINECART) {
            ConfigurationNode seatNode = new ConfigurationNode();
            registry.toConfig(seatNode, CartAttachmentSeat.TYPE);
            config.setNodeList("attachments", Arrays.asList(seatNode));
        }
        this.update(config, false);
        this._isDefault = true;
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
     * Adds an owner of this model. The owner will receive updates
     * when this model changes. onModelChanged will be called right after the owner
     * is added.
     * 
     * @param owner
     */
    public void addOwner(AttachmentModelOwner owner) {
        this.owners.add(owner);
        owner.onModelChanged(this);
    }

    /**
     * Removes an owner of this model. The owner will no longer receive updates
     * when the model changes.
     * 
     * @param owner
     */
    public void removeOwner(AttachmentModelOwner owner) {
        this.owners.remove(owner);
    }

    /**
     * Updates the full configuration of this attachment model. All users of this model
     * will be notified.
     * 
     * @param newConfig
     */
    public void update(ConfigurationNode newConfig) {
        this.update(newConfig, true);
    }

    /**
     * Updates the full configuration of this attachment model. All users of this model
     * will be notified if notify is true.
     * 
     * @param newConfig
     * @param notify True to not notify the changes, False for a silent update
     */
    public void update(ConfigurationNode newConfig, boolean notify) {
        this.config = newConfig;
        this.onConfigChanged(notify);

        //TODO: Tell save scheduler we can re-save models.yml

        //TODO: Tell everyone that uses this model to refresh
    }

    /**
     * Updates only a single leaf of the attachment model tree. All users of this model
     * will be notified.
     * 
     * @param targetPath to the leaf that changed
     * @param newConfig for the leaf
     */
    public void updateNode(int[] targetPath, ConfigurationNode newConfig) {
        this.updateNode(targetPath, newConfig);
    }

    /**
     * Updates only a single leaf of the attachment model tree. All users of this model
     * will be notified if notify is true.
     * 
     * @param targetPath to the leaf that changed
     * @param newConfig for the leaf
     * @param notify True to not notify the changes, False for a silent update
     */
    public void updateNode(int[] targetPath, ConfigurationNode newConfig, boolean notify) {
        ConfigurationNode changedNode = this.config;
        for (int index : targetPath) {
            List<ConfigurationNode> attachments = changedNode.getNodeList("attachments");
            if (index >= 0 && index < attachments.size()) {
                changedNode = attachments.get(index);
            } else {
                return; // invalid path
            }
        }
        for (String key : newConfig.getKeys()) {
            if (!key.equals("attachments")) {
                changedNode.set(key, newConfig.get(key));
            }
        }
        for (String oldKey : new ArrayList<String>(changedNode.getKeys())) {
            if (!newConfig.contains(oldKey) && !oldKey.equals("attachments")) {
                changedNode.remove(oldKey);
            }
        }
        this.onConfigNodeChanged(targetPath, changedNode, notify);
    }

    public void log() {
        log(this.config, 0);
    }

    private void log(ConfigurationNode node, int indent) {
        for (Map.Entry<String, Object> entry : node.getValues().entrySet()) {
            if (entry.getKey().equals("attachments")) {
                continue;
            }
            System.out.println(StringUtil.getFilledString("  ", indent) + entry.getKey() + ": " + entry.getValue());
        }

        System.out.println(StringUtil.getFilledString("  ", indent) + "attachments:");
        for (ConfigurationNode subNode : node.getNodeList("attachments")) {
            log(subNode, indent + 1);
        }
    }

    private void computeProperties() {
        this.seatCount = 0;
        this.loadSeats(this.config);

        ConfigurationNode physical = this.config.getNode("physical");
        this.cartLength = physical.get("cartLength", 0.98f);
        this.wheelCenter = physical.get("wheelCenter", 0.0);
        this.wheelDistance = physical.get("wheelDistance", 0.0);
    }

    private void onConfigChanged(boolean notify) {
        this._isDefault = false; // Was changed; no longer default!
        this.computeProperties();
        TrainPropertiesStore.markForAutosave(); // hack!
        if (notify) {
            for (AttachmentModelOwner owner : new ArrayList<AttachmentModelOwner>(this.owners)) {
                owner.onModelChanged(this);
            }
        }
    }

    private void onConfigNodeChanged(int[] targetPath, ConfigurationNode config, boolean notify) {
        this._isDefault = false; // Was changed; no longer default!
        TrainPropertiesStore.markForAutosave(); // hack!
        if (notify) {
            for (AttachmentModelOwner owner : new ArrayList<AttachmentModelOwner>(this.owners)) {
                owner.onModelNodeChanged(this, targetPath, config);
            }
        }
    }

    private void loadSeats(ConfigurationNode config) {
        if (registry.fromConfig(config) == CartAttachmentSeat.TYPE) {
            this.seatCount++;
        }
        for (ConfigurationNode node : config.getNodeList("attachments")) {
            this.loadSeats(node);
        }
    }

    @Override
    public AttachmentModel clone() {
        return new AttachmentModel(this.getConfig().clone());
    }

    /**
     * Creates the default, unmodified model for a Vanilla Minecart
     * 
     * @param entityType
     * @return default minecart model
     */
    public static AttachmentModel getDefaultModel(EntityType entityType) {
        AttachmentModel result = new AttachmentModel();
        result.resetToDefaults(entityType);
        return result;
    }
}
