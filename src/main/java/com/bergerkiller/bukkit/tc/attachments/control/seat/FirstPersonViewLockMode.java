package com.bergerkiller.bukkit.tc.attachments.control.seat;

/**
 * Whether, and how, the first player view camera is locked. When
 * locked, the camera rotates when the seat itself rotates.
 */
public enum FirstPersonViewLockMode {
    /** Player can freely look around and camera does not rotate along with seat */
    OFF("attachments/view_lock_off.png", "No view locking"),
    /** Relative movement updates are sent so the player camera rotates when the seat rotates */
    MOVE("attachments/view_lock_move.png", "Move view when\nseat moves (Choppy!)");

    private final String _icon;
    private final String _tooltip;

    private FirstPersonViewLockMode(String icon, String tooltip) {
        this._icon = icon;
        this._tooltip = tooltip;
    }

    public String getIconPath() {
        return _icon;
    }

    public String getTooltip() {
        return _tooltip;
    }
}
