package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentInternalState;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;
import com.bergerkiller.generated.net.minecraft.server.EntityShulkerHandle;

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
    public void onLoad(ConfigurationNode config) {
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
    public AttachmentControllerMember getController() {
        return (AttachmentControllerMember) this.getManager();
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

    protected void updateGlowColorFor(UUID entityUUID, ChatColor color, Player viewer) {
        if (TrainCarts.plugin != null && TrainCarts.plugin.getGlowColorTeamProvider() != null) {
            if (color == null) {
                TrainCarts.plugin.getGlowColorTeamProvider().reset(viewer, entityUUID);
            } else {
                TrainCarts.plugin.getGlowColorTeamProvider().update(viewer, entityUUID, color);
            }
        }
    }

    protected void updateGlowColor(UUID entityUUID, ChatColor color) {
        for (Player viewer : this.getViewers()) {
            updateGlowColorFor(entityUUID, color, viewer);
        }
    }

    /**
     * Register all default TrainCarts attachment types
     */
    public static void registerDefaultAttachments() {
        AttachmentTypeRegistry.instance().register(CartAttachmentEmpty.TYPE);
        AttachmentTypeRegistry.instance().register(CartAttachmentEntity.TYPE);
        AttachmentTypeRegistry.instance().register(CartAttachmentItem.TYPE);
        AttachmentTypeRegistry.instance().register(CartAttachmentModel.TYPE);
        AttachmentTypeRegistry.instance().register(CartAttachmentSeat.TYPE);
        AttachmentTypeRegistry.instance().register(CartAttachmentText.TYPE);

        if (EntityShulkerHandle.T.isAvailable()) {
            AttachmentTypeRegistry.instance().register(CartAttachmentPlatformOriginal.TYPE);
        }
    }
}
