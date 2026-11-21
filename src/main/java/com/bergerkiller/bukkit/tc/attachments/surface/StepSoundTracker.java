package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import org.bukkit.util.Vector;

/**
 * Keeps track of a player's traveled distance and when a full-step distance is walked,
 * emits a sound. Also plays the first time a player walks on a surface again, after not
 * having done so before.
 */
class StepSoundTracker {
    private static final double DISTANCE_INTERVAL = 1.5;
    private Vector lastPosition = null;
    private double accumulatedDistance = 0.0;

    /**
     * Moves the last-position along with a transformation. This is called to update the step
     * position when the surface the player is walking on moves itself, keeping step-counting
     * relative to the surface.
     *
     * @param transform Transformation change of the surface
     */
    public void move(Matrix4x4 transform) {
        if (lastPosition != null) {
            transform.transformPoint(lastPosition);
        }
    }

    /**
     * Informs no step occurred (player is in the air or not on a surface). Resets any tracking.
     */
    public void noStep() {
        lastPosition = null;
    }

    /**
     * Handles a single step. Returns whether a sound should be emitted. Returns true for
     * the first step, and every distance interval traveled since then.
     *
     * @param position Position of the player's foot
     * @return True if a step sound should play, False if not
     */
    public boolean step(Vector position) {
        if (lastPosition == null) {
            lastPosition = position.clone();
            accumulatedDistance = 0.0;
            return true;
        } else {
            accumulatedDistance += position.distance(lastPosition);
            MathUtil.setVector(lastPosition, position);
            if (accumulatedDistance >= DISTANCE_INTERVAL) {
                accumulatedDistance -= DISTANCE_INTERVAL;
                return true;
            } else {
                return false;
            }
        }
    }
}
