package com.bergerkiller.bukkit.tc.properties.standard.type;

/**
 * When a cart orientation was locked during saving, this orientation
 * is set to non-NONE and indicates the "flipped" property written to
 * saved yaml. During future saves this will then be used to correct
 * for trains moving in the opposite direction.
 */
public enum CartLockOrientation {
    NONE(false), LOCKED_NOT_FLIPPED(false), LOCKED_FLIPPED(true);

    private final boolean flipped;

    private CartLockOrientation(boolean flipped) {
        this.flipped = flipped;
    }

    public boolean isFlipped() {
        return this.flipped;
    }

    public static CartLockOrientation locked(boolean flipped) {
        return flipped ? LOCKED_FLIPPED : LOCKED_NOT_FLIPPED;
    }
}
