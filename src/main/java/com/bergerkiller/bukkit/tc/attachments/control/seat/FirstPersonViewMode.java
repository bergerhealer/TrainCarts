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
    DYNAMIC(false),
    /**
     * Default mode: player sits in the seat, and sees himself
     * sitting there always upright. The player head is always
     * above the position of the seat. Anchors the butt of the player
     * where the seat position is.
     */
    DEFAULT(false),
    /**
     * Player floats an offset away from the seat, making sure the player head
     * is exactly where it would be if perfectly rotated. The same can be achieved
     * with DEFAULT and an eye-position offset of y=1.
     *
     * This is used when the player uses smooth coasters, because mount switching
     * doesn't work very well there.
     */
    SMOOTHCOASTERS_FIX(false, false),
    /**
     * The player can not see himself sitting, but the camera is positioned
     * where the player head would be in floating mode.
     */
    INVISIBLE(true),
    /**
     * The player can see himself sit in third-person, the camera hovering
     * slightly above where the head is located. Similar to floating,
     * but the player is visible as a separate entity.
     */
    THIRD_P(true);

    private final boolean _fakePlayer;
    private final boolean _selectable;

    private FirstPersonViewMode(boolean fakePlayer) {
        this(fakePlayer, true);
    }

    private FirstPersonViewMode(boolean fakePlayer, boolean selectable) {
        this._fakePlayer = fakePlayer;
        this._selectable = selectable;
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

    /**
     * Whether this view mode is selectable in the editor
     *
     * @return True if selectable
     */
    public boolean isSelectable() {
        return _selectable;
    }
}
