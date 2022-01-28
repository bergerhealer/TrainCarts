package com.bergerkiller.bukkit.tc.attachments.control.seat;

/**
 * Whether, and how, the first player view camera is locked. When
 * locked, the camera rotates when the seat itself rotates.
 */
public enum FirstPersonViewLockMode {
    /** Player can freely look around and camera does not rotate along with seat */
    OFF("attachments/view_lock_off.png", "No view locking", false),
    /** Relative movement updates are sent so the player camera rotates when the seat rotates */
    MOVE("attachments/view_lock_move.png", "Move view when\nseat moves (Choppy!)", false),
    /** Spectator mode with the Player being able to look around */
    SPECTATOR_FREE("attachments/view_lock_spectator_free.png", "Free look in\nSpectator mode", true),
    /** Spectator mode with the Player unable to look freely */
    SPECTATOR_LOCKED("attachments/view_lock_spectator.png", "View locked in\nSpectator mode", true);

    private final String _icon;
    private final String _tooltip;
    private final boolean _spectator;

    private FirstPersonViewLockMode(String icon, String tooltip, boolean spectator) {
        this._icon = icon;
        this._tooltip = tooltip;
        this._spectator = spectator;
    }

    public String getIconPath() {
        return _icon;
    }

    public String getTooltip() {
        return _tooltip;
    }

    public boolean isSpectator() {
        return _spectator;
    }
}
