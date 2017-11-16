package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;

public abstract class CartAttachment {
    public List<CartAttachment> children = new ArrayList<CartAttachment>(0);
    protected MinecartMemberNetwork controller = null;
    protected CartAttachment parent = null;
    protected ConfigurationNode config = null;
    public Matrix4x4 last_transform;
    public Matrix4x4 transform;
    public Vector3 position;

    public void onAttached() {
        this.position = new Vector3(0.0, 0.0, 0.0);
        if (this.config.isNode("position")) {
            ConfigurationNode positionNode = this.config.getNode("position");
            this.position.x = positionNode.get("x", 0.0);
            this.position.y = positionNode.get("y", 0.0);
            this.position.z = positionNode.get("z", 0.0);
        }
    }

    public void onDetached() {
    }

    /**
     * Gets whether a particular Entity Id is in use by this attachment.
     * This is called when the player interacts to find out which entity was interacted with.
     * 
     * @param entityId to check
     * @return True if the entity id is part of this attachment
     */
    public boolean containsEntityId(int entityId) {
        return false;
    }

    /**
     * Gets an Entity Id of an Entity other entities can mount to mount this attachment.
     * Returns -1 if no mounting is possible.
     * 
     * @return Mountable entity Id, or -1 if not mountable
     */
    public int getMountEntityId() {
        return -1;
    }
    
    public abstract void makeVisible(Player viewer);

    public abstract void makeHidden(Player viewer);

    /**
     * Called right after the position transformation matrix is updated.
     * Relative positioning of the attachment should happen here.
     */
    public void onPositionUpdate() {
        this.transform.translate(this.position);
    }

    public abstract void onTick();
    
    public abstract void onMove(boolean absolute);

    public static CartAttachment findAttachment(CartAttachment root, int entityId) {
        if (root.containsEntityId(entityId)) {
            return root;
        } else {
            for (CartAttachment child : root.children) {
                CartAttachment att = findAttachment(child, entityId);
                if (att != null) {
                    return att;
                }
            }
            return null;
        }
    }

    public static void performTick(CartAttachment attachment) {
        attachment.onTick();
        for (CartAttachment child : attachment.children) {
            performTick(child);
        }
    }

    public static void performMovement(CartAttachment attachment, boolean absolute) {
        attachment.onMove(absolute);
        for (CartAttachment child : attachment.children) {
            performMovement(child, absolute);
        }
    }

    public static void updatePositions(CartAttachment attachment, Matrix4x4 transform) {
        attachment.last_transform = attachment.transform;
        attachment.transform = transform.clone();
        attachment.onPositionUpdate();
        if (attachment.last_transform == null) {
            attachment.last_transform = attachment.transform.clone();
        }
        for (CartAttachment child : attachment.children) {
            updatePositions(child, attachment.transform);
        }
    }

    /**
     * De-initializes cart attachments after no viewers see the attachments anymore.
     * Done before attachments are radically changed.
     * 
     * @param attachment
     */
    public static void deinitialize(CartAttachment attachment) {
        for (CartAttachment child : attachment.children) {
            deinitialize(child);
        }
        attachment.onDetached();
    }

    /**
     * Loads a full cart attachment tree from configuration
     * 
     * @param controller
     * @param config
     * @return cart attachment root node
     */
    public static CartAttachment initialize(MinecartMemberNetwork controller, ConfigurationNode config) {
        CartAttachment attachment = loadAttachments(controller, config);
        attachment.attachAttachments();
        updatePositions(attachment, controller.getTransform(false));
        return attachment;
    }

    private static CartAttachment loadAttachments(MinecartMemberNetwork controller, ConfigurationNode config) {
        CartAttachment attachment = config.get("type", CartAttachmentType.EMPTY).createAttachment();
        attachment.controller = controller;
        attachment.config = config;
        for (ConfigurationNode childNode : config.getNodeList("attachments")) {
            CartAttachment child = loadAttachments(controller, childNode);
            child.parent = attachment;
            attachment.children.add(child);
        }
        return attachment;
    }

    private void attachAttachments() {
        this.onAttached();
        for (CartAttachment child : this.children) {
            child.attachAttachments();
        }
    }

    protected Vector calcMotion() {
        Vector pos_old = this.last_transform.toVector();
        Vector pos_new = this.transform.toVector();
        return pos_new.subtract(pos_old);
    }
}
