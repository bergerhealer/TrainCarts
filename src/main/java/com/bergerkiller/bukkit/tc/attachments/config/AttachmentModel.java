package com.bergerkiller.bukkit.tc.attachments.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.StringUtil;

public class AttachmentModel {
    public static boolean test_model_mode = false;
    public static AttachmentModel MODEL = new AttachmentModel(); // For debug only!

    static {
        MODEL.config.set("type", CartAttachmentType.ENTITY);
        MODEL.config.set("entityType", EntityType.MINECART);
        
        ConfigurationNode testNode = new ConfigurationNode();
        testNode.set("type", CartAttachmentType.SEAT);
        
        /*
        ConfigurationNode posNode = testNode.getNode("position");
        posNode.set("x", -1.5);
        posNode.set("y", 0.0);
        posNode.set("z", 0.0);
        */
        
        MODEL.config.setNodeList("attachments", Arrays.asList(testNode));

        
        /*
        MODEL.config.set("type", CartAttachmentType.ENTITY);
        MODEL.config.set("entityType", EntityType.MINECART);
        
        ConfigurationNode testNode = new ConfigurationNode();
        testNode.set("type", CartAttachmentType.ENTITY);
        testNode.set("entityType", EntityType.MINECART);
        ConfigurationNode posNode = testNode.getNode("position");
        posNode.set("x", -1.5);
        posNode.set("y", 0.0);
        posNode.set("z", 0.0);
        
        ConfigurationNode testNode2 = testNode.clone();
        testNode2.getNode("position").set("x", 1.5);
        
        MODEL.config.setNodeList("attachments", Arrays.asList(testNode, testNode2));
        //ConfigurationNode seatNode = new ConfigurationNode();
        //seatNode.set("type", CartAttachmentType.SEAT);
        
        //MODEL.config.setNodeList("attachments", Arrays.asList(seatNode));
        */
    }

    /**
     * Gets the default, unmodified model for a Vanilla Minecart
     * 
     * @param minecartType
     * @return default minecart model
     */
    public static AttachmentModel getDefaultModel(EntityType minecartType) {
        if (test_model_mode) {
            return MODEL; //debug!
        }

        AttachmentModel result = new AttachmentModel();
        result.config.set("type", CartAttachmentType.ENTITY);
        result.config.set("entityType", minecartType);
        if (minecartType == EntityType.MINECART) {
            ConfigurationNode seatNode = new ConfigurationNode();
            seatNode.set("type", CartAttachmentType.SEAT);
            result.config.setNodeList("attachments", Arrays.asList(seatNode));
        }
        return result;
    }

    private ConfigurationNode config;
    private List<AttachmentModelOwner> owners = new ArrayList<AttachmentModelOwner>();

    public AttachmentModel() {
        this.config = new ConfigurationNode();
    }

    public ConfigurationNode getConfig() {
        return this.config;
    }

    public void addOwner(AttachmentModelOwner owner) {
        this.owners.add(owner);
    }
    
    public void removeOwner(AttachmentModelOwner owner) {
        this.owners.remove(owner);
    }
    
    public void update(ConfigurationNode newConfig) {
        this.config = newConfig;

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
}
