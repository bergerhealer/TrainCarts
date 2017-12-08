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

    public AttachmentModel() {
        this(new ConfigurationNode());
    }

    public AttachmentModel(ConfigurationNode config) {
        this.config = config;
        this.onConfigChanged();
    }

    public ConfigurationNode getConfig() {
        return this.config;
    }

    public int getSeatCount() {
        return this.seatCount;
    }

    public void addOwner(AttachmentModelOwner owner) {
        this.owners.add(owner);
    }
    
    public void removeOwner(AttachmentModelOwner owner) {
        this.owners.remove(owner);
    }

    public void update(ConfigurationNode newConfig) {
        this.config = newConfig;
        this.onConfigChanged();

        //TODO: Tell save scheduler we can re-save models.yml

        //TODO: Tell everyone that uses this model to refresh

        for (AttachmentModelOwner owner : this.owners) {
            owner.onModelChanged(this);
        }
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
     * @param minecartType
     * @return default minecart model
     */
    public static AttachmentModel getDefaultModel(EntityType minecartType) {
        AttachmentModel result = new AttachmentModel();
        result.config.set("type", CartAttachmentType.ENTITY);
        result.config.set("entityType", minecartType);
        if (minecartType == EntityType.MINECART) {
            ConfigurationNode seatNode = new ConfigurationNode();
            seatNode.set("type", CartAttachmentType.SEAT);
            result.config.setNodeList("attachments", Arrays.asList(seatNode));
        }
        result.onConfigChanged();
        return result;
    }
}
