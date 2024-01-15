package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.Collection;
import java.util.UUID;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentInternalState;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;
import com.bergerkiller.generated.net.minecraft.world.entity.monster.EntityShulkerHandle;

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
        return this.getManager().getViewers();
    }

    @Override
    public Collection<AttachmentViewer> getAttachmentViewers() {
        return this.getManager().getAttachmentViewers();
    }

    public boolean hasController() {
        return this.getManager() instanceof AttachmentControllerMember;
    }

    /**
     * Gets the network controller that owns and manages this attachment
     * 
     * @return controller
     */
    public AttachmentControllerMember getController() {
        return (AttachmentControllerMember) this.getManager();
    }

    /**
     * Gets the MinecartMember this attachment belongs to
     *
     * @return member
     */
    public MinecartMember<?> getMember() {
        return getController().getMember();
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
     * @deprecated Use {@link AttachmentViewer#updateGlowColor(UUID, ChatColor)} instead
     */
    @Deprecated
    protected void updateGlowColorFor(UUID entityUUID, ChatColor color, Player viewer) {
        updateGlowColorFor(entityUUID, color, getManager().asAttachmentViewer(viewer));
    }

    /**
     * @deprecated Use {@link AttachmentViewer#updateGlowColor(UUID, ChatColor)} instead
     */
    @Deprecated
    protected void updateGlowColorFor(UUID entityUUID, ChatColor color, AttachmentViewer viewer) {
        viewer.updateGlowColor(entityUUID, color);
    }

    protected void updateGlowColor(UUID entityUUID, ChatColor color) {
        for (AttachmentViewer viewer : this.getAttachmentViewers()) {
            viewer.updateGlowColor(entityUUID, color);
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
        AttachmentTypeRegistry.instance().register(CartAttachmentHitBox.TYPE);
        AttachmentTypeRegistry.instance().register(CartAttachmentSound.TYPE);
        AttachmentTypeRegistry.instance().register(CartAttachmentSequencer.TYPE);

        if (EntityShulkerHandle.T.isAvailable()) {
            AttachmentTypeRegistry.instance().register(CartAttachmentPlatform.TYPE);
        }

        if (CommonCapabilities.HAS_DISPLAY_ENTITY) {
            AttachmentTypeRegistry.instance().register(CartAttachmentBlock.TYPE);

            // Requires WorldEdit to be loaded (not yet enabled, that's fine)
            // We assume that WorldEdit will also load up properly
            if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null) {
                AttachmentTypeRegistry.instance().register(CartAttachmentSchematic.TYPE);
            }
        }
    }
}
