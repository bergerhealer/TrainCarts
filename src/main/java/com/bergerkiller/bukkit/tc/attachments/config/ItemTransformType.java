package com.bergerkiller.bukkit.tc.attachments.config;

import org.bukkit.inventory.EquipmentSlot;

import com.bergerkiller.bukkit.common.utils.ParseUtil;

/**
 * Transformation mode for displaying an Item.
 * For now only includes armor-stand based transforms.
 */
public enum ItemTransformType {
    HEAD("head", "HEAD"),
    CHEST("chest", "CHEST"),
    LEFT_HAND("left hand", "OFF_HAND"),
    RIGHT_HAND("right hand", "HAND"),
    LEGS("legs", "LEGS"),
    FEET("feet", "FEET");

    private final String name;
    private final EquipmentSlot slot;

    private ItemTransformType(String name, String slotName) {
        this.name = name;
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

    public static ItemTransformType get(String name) {
        for (ItemTransformType type : values()) {
            if (type.toString().equals(name)) {
                return type;
            }
        }
        return HEAD;
    }
}
