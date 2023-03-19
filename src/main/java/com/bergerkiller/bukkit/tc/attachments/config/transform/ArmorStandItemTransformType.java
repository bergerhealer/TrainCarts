package com.bergerkiller.bukkit.tc.attachments.config.transform;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityEquipmentHandle;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Armorstand item transformation options. These control the scale of the armorstand as well
 * as what equipment slot of the armorstand is used. Only works for armorstand
 * entities.
 */
public enum ArmorStandItemTransformType {
    HEAD("head", "HEAD", false),
    SMALL_HEAD("head \u24AE", "HEAD", true),
    LEFT_HAND("left hand", "OFF_HAND", false),
    SMALL_LEFT_HAND("left hand \u24AE", "OFF_HAND", true),
    RIGHT_HAND("right hand", "HAND", false),
    SMALL_RIGHT_HAND("right hand \u24AE", "HAND", true),
    CHEST("chest", "CHEST", false),
    SMALL_CHEST("chest \u24AE", "CHEST", true),
    LEGS("legs", "LEGS", false),
    SMALL_LEGS("legs \u24AE", "LEGS", true),
    FEET("feet", "FEET", false),
    SMALL_FEET("feet \u24AE", "FEET", true);

    private final String name;
    private final EquipmentSlot slot;
    private final boolean small;

    ArmorStandItemTransformType(String name, String slotName, boolean small) {
        this.name = name;
        this.small = small;
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

    public boolean isHead() {
        return this == HEAD || this == SMALL_HEAD;
    }

    /**
     * Gets whether this mode requires the use of a small (baby) scale armorstand
     *
     * @return True if this is a small armorstand
     */
    public boolean isSmallArmorStand() {
        return small;
    }

    public boolean isLeftHand() {
        return this == LEFT_HAND || this == SMALL_LEFT_HAND;
    }

    public boolean isRightHand() {
        return this == RIGHT_HAND || this == SMALL_RIGHT_HAND;
    }

    public boolean isLeg() {
        return this == LEGS || this == SMALL_LEGS || this == FEET || this == SMALL_FEET;
    }

    public double getArmorStandHorizontalOffset() {
        switch (this) {
        case LEFT_HAND:
            return 0.3125;
        case SMALL_LEFT_HAND:
            return 0.12;
        case RIGHT_HAND:
            return -0.3125;
        case SMALL_RIGHT_HAND:
            return -0.12;
        default:
            return 0.0;
        }
    }

    public double getArmorStandVerticalOffset() {
        switch (this) {
        case LEFT_HAND:
        case RIGHT_HAND:
            return 1.375;
        case SMALL_LEFT_HAND:
        case SMALL_RIGHT_HAND:
            return 0.492;
        case HEAD:
            return 1.44;
        case SMALL_HEAD:
            return 0.73;
        default:
            return 1.44;
        }
    }

    public PacketPlayOutEntityEquipmentHandle createEquipmentPacket(int entityId, ItemStack item) {
        return PacketPlayOutEntityEquipmentHandle.createNew(entityId, this.getSlot(), item);
    }

    public static ArmorStandItemTransformType get(String name) {
        for (ArmorStandItemTransformType type : values()) {
            if (type.toString().equals(name)) {
                return type;
            }
        }
        return HEAD;
    }
}
