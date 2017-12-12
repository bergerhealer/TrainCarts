package com.bergerkiller.bukkit.tc.attachments.config;

import org.bukkit.inventory.EquipmentSlot;

/**
 * Transformation mode for displaying an Item.
 * For now only includes armor-stand based transforms.
 */
public enum ItemTransformType {
    HEAD("head", EquipmentSlot.HEAD),
    CHEST("chest", EquipmentSlot.CHEST),
    LEFT_HAND("left hand", EquipmentSlot.OFF_HAND),
    RIGHT_HAND("right hand", EquipmentSlot.HAND),
    LEGS("legs", EquipmentSlot.LEGS),
    FEET("feet", EquipmentSlot.FEET);

    private final String name;
    private final EquipmentSlot slot;

    private ItemTransformType(String name, EquipmentSlot slot) {
        this.name = name;
        this.slot = slot;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public EquipmentSlot getSlot() {
        return this.slot;
    }

}
