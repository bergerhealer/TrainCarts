package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.Collection;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentInternalState;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;

public abstract class CartAttachment implements Attachment {
    private final AttachmentInternalState state = new AttachmentInternalState();
    private MinecartMemberNetwork controller = null;

    @Override
    public AttachmentInternalState getInternalState() {
        return this.state;
    }

    @Override
    public void onAttached() {
    }

    @Override
    public void onDetached() {
    }

    @Override
    public Collection<Player> getViewers() {
        return this.controller.getViewers();
    }

    /**
     * Gets the network controller that owns and manages this attachment
     * 
     * @return controller
     */
    public MinecartMemberNetwork getController() {
        return this.controller;
    }

    @Override
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

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
    }

    /**
     * Loads a full cart attachment tree from configuration
     * 
     * @param controller
     * @param config
     * @return cart attachment root node
     */
    public static Attachment initialize(MinecartMemberNetwork controller, ConfigurationNode config) {
        Attachment attachment = HelperMethods.createController(config);
        setControllerRecursive(attachment, controller);
        HelperMethods.perform_onAttached(attachment);
        HelperMethods.updatePositions(attachment, controller.getLiveTransform());
        return attachment;
    }

    private static void setControllerRecursive(Attachment attachment, MinecartMemberNetwork controller) {
        ((CartAttachment) attachment).controller = controller;
        for (Attachment child : attachment.getChildren()) {
            setControllerRecursive(child, controller);
        }
    }
}
