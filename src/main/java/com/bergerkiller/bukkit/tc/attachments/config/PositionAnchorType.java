package com.bergerkiller.bukkit.tc.attachments.config;

/**
 * The anchor position of the attachment. By default it is anchored to its parent or the middle
 * of the minecart. By setting it to front/back wheel, position can be made relative to the wheel's
 * position and rotation.
 */
public enum PositionAnchorType {
    DEFAULT("default"), FRONT_WHEEL("front wheel"), BACK_WHEEL("back wheel");

    private final String name;

    private PositionAnchorType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static PositionAnchorType get(String name) {
        for (PositionAnchorType type : values()) {
            if (type.toString().equals(name)) {
                return type;
            }
        }
        return DEFAULT;
    }    
}
