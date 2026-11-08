package com.bergerkiller.bukkit.tc.controller.player.pmc;

import org.bukkit.util.Vector;

/**
 * Interpolates client positions in the same way as armor stand movement.
 */
final class ArmorStandPositionInterpolation {
    private boolean enabled = true;
    private Vector position;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            position = null;
        }
    }

    public Vector update(Vector target) {
        if (!enabled) {
            return target.clone();
        }
        if (position == null) {
            position = target.clone();
        } else {
            position.add(target.clone().subtract(position).multiply(1.0 / 3.0));
        }
        return position.clone();
    }
}
