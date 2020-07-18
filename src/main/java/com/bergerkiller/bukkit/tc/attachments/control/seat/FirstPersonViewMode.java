package com.bergerkiller.bukkit.tc.attachments.control.seat;

/**
 * The manner of which the player can see himself sit inside the seat
 */
public enum FirstPersonViewMode {
    /**
     * Uses default mode when the seat is level, but switches to third-person
     * mode when the seat is vertical or upside-down. This is default legacy
     * behavior.
     */
    DYNAMIC(Double.NaN, false),
    /**
     * Default mode: player sits in the seat, and sees himself
     * sitting there always upright. The player head is always
     * above the position of the seat. Anchors the butt of the player
     * where the seat position is.
     */
    DEFAULT(Double.NaN, false),
    /**
     * Player floats an offset away from the seat, making sure the player head
     * is exactly where it would be if perfectly rotated. Anchors the eyes of
     * the player a fixed distance of 1 from the seat position.
     */
    FLOATING(1.0, false),
    /**
     * The player can not see himself sitting, but the camera is positioned
     * where the player head would be in floating mode.
     */
    INVISIBLE(1.0, true),
    /**
     * The player can see himself sit in third-person, the camera hovering
     * slightly above where the head is located. Similar to floating,
     * but the player is visible as a separate entity.
     */
    THIRD_P(1.4, true);

    private final double _cameraOffset;
    private final boolean _fakePlayer;

    private FirstPersonViewMode(double cameraOffset, boolean fakePlayer) {
        this._cameraOffset = cameraOffset;
        this._fakePlayer = fakePlayer;
    }

    /**
     * Gets whether for this mode a virtual entity is used to move
     * the player around
     * 
     * @return True if virtual
     */
    public boolean isVirtual() {
        return !Double.isNaN(this._cameraOffset);
    }

    /**
     * Gets the Y-offset of the camera
     * 
     * @return virtual camera offset
     */
    public double getVirtualOffset() {
        return this._cameraOffset;
    }

    /**
     * Gets whether a different Player entity is spawned to represent
     * the player in first-person
     * 
     * @return True if a fake player is used
     */
    public boolean hasFakePlayer() {
        return this._fakePlayer;
    }
}
