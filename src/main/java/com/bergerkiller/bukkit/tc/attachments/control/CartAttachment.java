package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.Collection;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentInternalState;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;

public abstract class CartAttachment implements Attachment {
    private final AttachmentInternalState state = new AttachmentInternalState();

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
        return this.getController().getViewers();
    }

    /**
     * Gets the network controller that owns and manages this attachment
     * 
     * @return controller
     */
    public MinecartMemberNetwork getController() {
        return (MinecartMemberNetwork) this.getManager();
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

}
