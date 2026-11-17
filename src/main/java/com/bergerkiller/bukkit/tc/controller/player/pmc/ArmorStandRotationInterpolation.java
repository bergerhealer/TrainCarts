package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer.ViewRotationTracker.Rotation;

/**
 * Interpolates client yaw and pitch in the same way as armor stand movement.
 */
final class ArmorStandRotationInterpolation {
    private static final double YAW_INTERPOLATION_FACTOR = 3.0;
    private static final double PITCH_INTERPOLATION_FACTOR = 4.0 / 3.0;

    private boolean enabled = true;
    private boolean initialized = false;
    private double lastYaw;
    private double lastPitch;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            initialized = false;
        }
    }

    public Rotation update(Rotation target) {
        double targetPitch = MathUtil.clamp((double) target.pitch, -90.0, 90.0);
        if (!enabled) {
            return new Rotation(target.yaw, (float) targetPitch);
        }
        double yaw = target.yaw;
        double pitch = targetPitch;
        if (!initialized) {
            initialized = true;
        } else {
            yaw = lastYaw + MathUtil.wrapAngle(yaw - lastYaw) * YAW_INTERPOLATION_FACTOR;
            pitch = lastPitch + (targetPitch - lastPitch) * PITCH_INTERPOLATION_FACTOR;
        }
        lastYaw = target.yaw;
        lastPitch = targetPitch;
        return new Rotation((float) yaw, (float) MathUtil.clamp(pitch, -90.0, 90.0));
    }
}
