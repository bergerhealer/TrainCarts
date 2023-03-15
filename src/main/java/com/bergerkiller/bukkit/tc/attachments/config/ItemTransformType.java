package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.wrappers.ItemDisplayMode;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityEquipmentHandle;

/**
 * Transformation mode for displaying an Item.
 * For now only includes armor-stand based transforms.
 */
public enum ItemTransformType {
    HEAD("head", "HEAD", ItemDisplayMode.HEAD),
    LEFT_HAND("left hand", "OFF_HAND", ItemDisplayMode.THIRD_PERSON_LEFT_HAND),
    RIGHT_HAND("right hand", "HAND", ItemDisplayMode.THIRD_PERSON_RIGHT_HAND),
    CHEST("chest", "CHEST", ItemDisplayMode.NONE),
    LEGS("legs", "LEGS", ItemDisplayMode.NONE),
    FEET("feet", "FEET", ItemDisplayMode.NONE);

    private final String name;
    private final EquipmentSlot slot;
    private final ItemDisplayMode displayMode;

    private ItemTransformType(String name, String slotName, ItemDisplayMode displayMode) {
        this.name = name;
        this.displayMode = displayMode;
        EquipmentSlot slot = ParseUtil.parseEnum(EquipmentSlot.class, slotName, null);
        if (slot == null && slotName.equals("OFF_HAND")) {
            slot = ParseUtil.parseEnum(EquipmentSlot.class, "HAND", null);
        }
        if (slot != null) {
            this.slot = slot;
        } else {
            this.slot = EquipmentSlot.HEAD;
        }
    }

    @Override
    public String toString() {
        return this.name;
    }

    public EquipmentSlot getSlot() {
        return this.slot;
    }

    /**
     * Gets the Item Display Mode used when this item transform type is used
     * to display items using an Item Display entity. Returns {@link ItemDisplayMode#NONE}
     * if a display entity can't be used.
     *
     * @return Item display mode
     */
    public ItemDisplayMode getDisplayMode() {
        return displayMode;
    }

    public boolean isHead() {
        return this == HEAD;
    }

    public boolean isLeftHand() {
        return this == LEFT_HAND;
    }

    public boolean isRightHand() {
        return this == RIGHT_HAND;
    }

    public boolean isLeg() {
        return this == LEGS || this == FEET;
    }

    public double getArmorStandHorizontalOffset(boolean small) {
        switch (this) {
        case LEFT_HAND:
            return small ? 0.12 : 0.3125;
        case RIGHT_HAND:
            return small ? -0.12 : -0.3125;
        default:
            return 0.0;
        }
    }

    public double getArmorStandVerticalOffset(boolean small) {
        switch (this) {
        case LEFT_HAND:
        case RIGHT_HAND:
            return small ? 0.492 : 1.38;
        case HEAD:
            return small ? 0.73 : 1.44;
        default:
            return 1.44;
        }
    }

    public PacketPlayOutEntityEquipmentHandle createEquipmentPacket(int entityId, ItemStack item) {
        return PacketPlayOutEntityEquipmentHandle.createNew(entityId, this.getSlot(), item);
    }

    public static ItemTransformType get(String name) {
        for (ItemTransformType type : values()) {
            if (type.toString().equals(name)) {
                return type;
            }
        }
        return HEAD;
    }
}
