package com.bergerkiller.bukkit.tc.attachments.control.seat;

/**
 * The manner of which the player can see himself sit inside the seat
 */
public enum FirstPersonViewMode {
    /**
     * Default mode: player sits in the seat, and sees himself
     * sitting there always upright
     */
    DEFAULT(Double.NaN, false),
    /**
     * Uses default mode when the seat is level, but switches to third-person
     * mode when the seat is vertical or upside-down
     */
    DYNAMIC(Double.NaN, false),
    /**
     * The player can see himself sit in third-person, the camera hovering
     * slightly above where the head is located
     */
    THIRD_P(1.4, true),
    /**
     * The player can not see himself sitting, but the camera is positioned
     * where the player head would be in third-person
     */
    INVISIBLE(0.0, false);

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
