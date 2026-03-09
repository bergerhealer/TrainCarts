package com.bergerkiller.bukkit.tc.attachments.animation;

/**
 * Mode at which the movement of the attachment (train) controls the rate of
 * animation playback.
 */
public enum AnimationMovementControl {
    /** Not active */
    OFF,
    /** Animation reverses when movement reverses */
    REVERSIBLE,
    /** Animation plays at speed of movement, regardless of direction */
    FORWARD_ONLY,
}
