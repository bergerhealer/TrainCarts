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
    DYNAMIC(false, false),
    /**
     * Default mode: player sits in the seat, and sees himself
     * sitting there always upright. The player head is always
     * above the position of the seat. Anchors the butt of the player
     * where the seat position is.
     */
    DEFAULT(false, false),
    /**
     * The player can not see himself sitting, but the camera is positioned
     * where the player head would be in floating mode.
     */
    INVISIBLE(true, true),
    /**
     * Head mode displays a floating head, spectated by the player.
     * As such, when this option is used spectator mode is always
     * active.
     */
    HEAD(false, true),
    /**
     * Standing mode makes the player stand upright, without being seated
     * in a mount. The player is moved around with velocity packets, or if
     * using spectator mode, by moving the spectated player.
     */
    STANDING(false, false),
    /**
     * The player can see himself sit in third-person, the camera hovering
     * slightly above where the head is located. Similar to floating,
     * but the player is visible as a separate entity.
     */
    THIRD_P(true, true);

    private final boolean _fakePlayer;
    private final boolean _realPlayerInvisible;
    private final boolean _selectable;

    private FirstPersonViewMode(boolean fakePlayer, boolean realPlayerInvisible) {
        this(fakePlayer, realPlayerInvisible, true);
    }

    private FirstPersonViewMode(boolean fakePlayer, boolean realPlayerInvisible, boolean selectable) {
        this._fakePlayer = fakePlayer;
        this._realPlayerInvisible = realPlayerInvisible;
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
     * Gets whether the real player is made invisible when in this mode
     *
     * @return True if the real player is invisible
     */
    public boolean isRealPlayerInvisible() {
        return this._realPlayerInvisible;
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
