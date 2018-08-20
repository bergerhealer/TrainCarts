package com.bergerkiller.bukkit.tc.attachments.config;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.control.*;
import com.bergerkiller.mountiplex.MountiplexUtil;

public enum CartAttachmentType {
    /** Shows nothing, placeholder node */
    EMPTY(CartAttachmentEmpty.class),
    /** Shows the model of an entity */
    ENTITY(CartAttachmentEntity.class),
    /** Shows the model of an item in an armor stand */
    ITEM(CartAttachmentItem.class),
    /** A seat a player can sit in */
    SEAT(CartAttachmentSeat.class),
    /** Attaches the full model tree of another model to this one */
    MODEL(CartAttachmentEmpty.class);

    private final Class<? extends CartAttachment> attachmentClass;
    
    private CartAttachmentType(Class<? extends CartAttachment> attachmentClass) {
        this.attachmentClass = attachmentClass;
    }

    public MapTexture getIcon(ConfigurationNode config) {
        switch (this) {
        case ITEM:
            ItemStack item = config.get("item", new ItemStack(Material.MINECART));
            return TCConfig.resourcePack.getItemTexture(item, 16, 16);

        case SEAT:
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/seat.png");

        case ENTITY:
            EntityType type = config.get("entityType", EntityType.MINECART);
            if (type == EntityType.MINECART) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_CHEST) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_STORAGE_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_COMMAND) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_COMMAND_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_FURNACE) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_POWERED_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_HOPPER) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_HOPPER_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_MOB_SPAWNER) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_MOB_SPAWNER")), 16, 16);
            } else if (type == EntityType.MINECART_TNT) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_EXPLOSIVE_MINECART")), 16, 16);
            } else {
                return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/mob.png");
            }

        default:
            return MapTexture.createEmpty(16, 16);
        }
    }

    public CartAttachment createAttachment() {
        try {
            return this.attachmentClass.newInstance();
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }
}
