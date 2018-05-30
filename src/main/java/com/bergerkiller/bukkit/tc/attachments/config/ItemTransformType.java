package com.bergerkiller.bukkit.tc.attachments.config;

import org.bukkit.inventory.EquipmentSlot;

import com.bergerkiller.bukkit.common.utils.ParseUtil;

/**
 * Transformation mode for displaying an Item.
 * For now only includes armor-stand based transforms.
 */
public enum ItemTransformType {
    HEAD("head", "HEAD", false),
    LEFT_HAND("left hand", "OFF_HAND", false),
    RIGHT_HAND("right hand", "HAND", false),
    CHEST("chest", "CHEST", false),
    LEGS("legs", "LEGS", false),
    FEET("feet", "FEET", false),
    SMALL_HEAD("S head", "HEAD", true),
    SMALL_LEFT_HAND("S left hand", "OFF_HAND", true),
    SMALL_RIGHT_HAND("S right hand", "HAND", true),
    SMALL_CHEST("S chest", "CHEST", true),
    SMALL_LEGS("S legs", "LEGS", true),
    SMALL_FEET("S feet", "FEET", true);

    private final String name;
    private final EquipmentSlot slot;
    private final boolean small;

    private ItemTransformType(String name, String slotName, boolean small) {
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

    public boolean isSmall() {
        return this.small;
    }

    public boolean isHead() {
        return this == HEAD || this == SMALL_HEAD;
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

    public double getHorizontalOffset() {
        switch (this) {
        case LEFT_HAND:
            return 0.3125;
        case RIGHT_HAND:
            return -0.3125;
        case SMALL_LEFT_HAND:
            return 0.12;
        case SMALL_RIGHT_HAND:
            return -0.12;
        default:
            return 0.0;
        }
    }

    public double getVerticalOffset() {
        switch (this) {
        case LEFT_HAND:
        case RIGHT_HAND:
            return 1.38;
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

    public static ItemTransformType get(String name) {
        for (ItemTransformType type : values()) {
            if (type.toString().equals(name)) {
                return type;
            }
        }
        return HEAD;
    }
}
