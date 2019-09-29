package com.bergerkiller.bukkit.tc.attachments.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.StringUtil;

public class AttachmentModel {
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
        this.config = config;
        this._isDefault = false;
        this.onConfigChanged();
    }

    public ConfigurationNode getConfig() {
        return this.config;
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
        config.set("type", CartAttachmentType.ENTITY);
        config.set("entityType", entityType);
        if (entityType == EntityType.MINECART) {
            ConfigurationNode seatNode = new ConfigurationNode();
            seatNode.set("type", CartAttachmentType.SEAT);
            config.setNodeList("attachments", Arrays.asList(seatNode));
        }
        this.update(config);
        this._isDefault = true;
    }

    /**
     * Sets this model to a named model from the model store
     * 
     * @param modelName to link to
     */
    public void resetToName(String modelName) {
        ConfigurationNode config = new ConfigurationNode();
        config.set("type", CartAttachmentType.MODEL);
        config.set("model", modelName);
        this.update(config);
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
        this.config = newConfig;
        this.onConfigChanged();

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
            changedNode.set(key, newConfig.get(key));
        }
        this.onConfigNodeChanged(targetPath, changedNode);
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

    private void onConfigChanged() {
        this.seatCount = 0;
        this.loadSeats(this.config);

        ConfigurationNode physical = this.config.getNode("physical");
        this.cartLength = physical.get("cartLength", 0.98f);
        this.wheelCenter = physical.get("wheelCenter", 0.0);
        this.wheelDistance = physical.get("wheelDistance", 0.0);
        this._isDefault = false; // Was changed; no longer default!

        for (AttachmentModelOwner owner : new ArrayList<AttachmentModelOwner>(this.owners)) {
            owner.onModelChanged(this);
        }
    }

    private void onConfigNodeChanged(int[] targetPath, ConfigurationNode config) {
        this._isDefault = false; // Was changed; no longer default!

        for (AttachmentModelOwner owner : new ArrayList<AttachmentModelOwner>(this.owners)) {
            owner.onModelNodeChanged(this, targetPath, config);
        }
    }

    private void loadSeats(ConfigurationNode config) {
        if (config.get("type", CartAttachmentType.EMPTY) == CartAttachmentType.SEAT) {
            this.seatCount++;
        }
        for (ConfigurationNode node : config.getNodeList("attachments")) {
            this.loadSeats(node);
        }
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
