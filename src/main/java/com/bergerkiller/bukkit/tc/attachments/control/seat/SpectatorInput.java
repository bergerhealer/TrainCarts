package com.bergerkiller.bukkit.tc.attachments.control.seat;

import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerRotationPacketHandle;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;

/**
 * Initializes and tracks the player input, which is used to move the spectator
 * camera around.
 */
class SpectatorInput {
    private AttachmentViewer player;
    private int blindTicks = 0;
    private float yawLimit = 360.0f;
    private boolean enabled = false;

    // Tracks changes in yaw and pitch as the player looks around
    private final Object deltaRotationLock = new Object();
    private YawPitch deltaRotation = YawPitch.ZERO;

    // Tracks accumulated yaw and pitch, updated relative to the eye orientation
    private final Quaternion absOrientation = new Quaternion();

    /**
     * Resets the player orientation to look at 0/0 and starts
     * reading yaw/pitch orientation details for this player.
     *
     * @param player
     */
    public void start(AttachmentViewer player, float yawLimit) {
        this.player = player;
        this.blindTicks = CommonUtil.getServerTicks() + 5; // no input for 5 ticks
        this.yawLimit = yawLimit;
        this.deltaRotation = YawPitch.ZERO;
        this.absOrientation.setIdentity();
        this.enabled = true;
        sendRotation(0.0f, 0.0f); // reset
    }

    /**
     * Does not track player orientation for view rotation.
     * It will be locked to what the input transform gives.
     */
    public void startLocked() {
        enabled = false;
    }

    public void addInputRotation(YawPitch rotation) {
        synchronized (deltaRotationLock) {
            deltaRotation = YawPitch.add(deltaRotation, rotation);
        }
    }

    /**
     * Stops intercepting input. Resets the look orientation to
     * what the eye last looked at.
     */
    public void stop(Matrix4x4 currentEyeTransform) {
        if (this.player != null) {
            FirstPersonView.HeadRotation headRot = FirstPersonView.HeadRotation.compute(currentEyeTransform);
            headRot = headRot.ensureLevel();
            sendRotation(headRot.pitch, headRot.yaw);
        }
        this.player = null;
        this.blindTicks = 0;
        this.deltaRotation = YawPitch.ZERO;
        this.enabled = false;
    }

    public boolean isStarted() {
        return this.player != null;
    }

    public void applyTo(Matrix4x4 eyeTransform) {
        Vector pos = eyeTransform.toVector();
        Quaternion rot = eyeTransform.getRotation();
        applyTo(rot);
        eyeTransform.setIdentity();
        eyeTransform.translate(pos);
        eyeTransform.rotate(rot);
    }

    public void applyTo(Quaternion eyeRotation) {
        if (enabled) {
            // Apply delta rotations
            YawPitch accumulatedRotation;
            synchronized (deltaRotationLock) {
                accumulatedRotation = deltaRotation;
                deltaRotation = YawPitch.ZERO;
            }

            // When player doesn't look around, just efficiently update the eye
            if (accumulatedRotation.yaw != 0.0f || accumulatedRotation.pitch != 0.0f) {
                // All actual calculations happen elsewhere, because it's complex
                RelativeOrientationCalc calc = new RelativeOrientationCalc(this, accumulatedRotation, eyeRotation);
                this.absOrientation.setTo(calc.calculate());

                // Ensure the absolute orientation never flips upside-down. This causes weird input.
                // When detected, flip roll to make it level again
                if (isUpsideDown(this.absOrientation)) {
                    this.absOrientation.rotateZFlip();
                }
            }
        }

        eyeRotation.multiply(this.absOrientation);
    }

    /**
     * Updates the view orientation of the Player
     */
    public void update() {
        if (player == null) {
            return; // No player inside - no input
        }
        if (blindTicks != 0) {
            if (CommonUtil.getServerTicks() >= blindTicks) {
                blindTicks = 0;
            } else {
                return; // Identity - input disabled until client sync'd up
            }
        }
    }

    private void sendRotation(float pitch, float yaw) {
        player.send(ClientboundPlayerRotationPacketHandle.createAbsolute(yaw, pitch));
    }

    private static boolean isUpsideDown(Quaternion q) {
        // return q.upVector().getY() < 0.0
        return (1.0 + 2.0 * (-q.getX()*q.getX()-q.getZ()*q.getZ())) < 0.0;
    }

    /**
     * Calculates the result of rotation the current input orientation using player input.
     * Handles the logic of keeping input to within the yaw/pitch limits.
     */
    private static class RelativeOrientationCalc {
        /**
         * How many times to 'zoom in' on a best-fit interpolation result.
         * Higher numbers result in higher CPU overhead.
         */
        private static final int MAX_INTERPOLATION_ROUNDS = 20;

        public final Quaternion base;
        public final double basePitch;
        public final double baseYaw;
        public final double deltaPitch;
        public final double deltaYaw;
        public final double maxForwardZ;

        public RelativeOrientationCalc(SpectatorInput input, YawPitch inputRotation, Quaternion eyeRotation) {
            Quaternion current = Quaternion.multiply(eyeRotation, input.absOrientation);
            Vector eyePYR = current.getYawPitchRoll();
            this.basePitch = eyePYR.getX();
            this.baseYaw = -eyePYR.getY();

            // Upside-down movement has yaw inverted
            // Note: The rotateY function is -yaw because Minecraft is kind of poop like that
            this.deltaPitch = inputRotation.pitch;
            this.deltaYaw = isUpsideDown(current) ? inputRotation.yaw : -inputRotation.yaw;

            // Base is without the base yaw/pitch, so that the math works out right
            this.base = Quaternion.divide(input.absOrientation, current);
            this.maxForwardZ = (input.yawLimit >= 180.0f) ? 1.0 : Math.cos(Math.toRadians(input.yawLimit));
        }

        public Quaternion calculate() {
            // First try out a full rotation. If that is valid, then we're already done
            Quaternion fullRotation = createRotation(deltaPitch, deltaYaw);
            if (isValidRotation(fullRotation)) {
                return fullRotation;
            }

            // Used as a throw-away temporary quaternion during below's calculations
            Quaternion tmp = new Quaternion();

            // Try to see if applying only one of the rotations is valid
            // If so, we can rotate fully with one and only partially with the other
            boolean canDoFullPitch = testRotation(tmp, deltaPitch, 0.0);
            boolean canDoFullYaw = testRotation(tmp, 0.0, deltaYaw);
            if (canDoFullPitch == canDoFullYaw) {
                // Neither or both can be rotated. Best we can do is slerp our way there.
                return calcUsingSlerp(fullRotation);
            }

            // Adjust only one of two rotation axis
            double t0 = 0.0;
            double t1 = 1.0;
            Quaternion result;
            if (canDoFullYaw) {
                // Rotate yaw fully and adjust pitch until we find the sweet spot
                result = createRotation(0.0, deltaYaw);
                for (int n = 0; n < MAX_INTERPOLATION_ROUNDS; n++) {
                    double th = 0.5 * (t0 + t1);
                    if (testRotation(tmp, th * deltaPitch, deltaYaw)) {
                        // Valid values between th and t1
                        t0 = th;
                        result.setTo(tmp);
                    } else {
                        // Valid values between t0 and th
                        t1 = th;
                    }
                }
            } else {
                // Rotate pitch fully and adjust yaw until we find the sweet spot
                result = createRotation(deltaPitch, 0.0);
                for (int n = 0; n < MAX_INTERPOLATION_ROUNDS; n++) {
                    double th = 0.5 * (t0 + t1);
                    if (testRotation(tmp, deltaPitch, th * deltaYaw)) {
                        // Valid values between th and t1
                        t0 = th;
                        result.setTo(tmp);
                    } else {
                        // Valid values between t0 and th
                        t1 = th;
                    }
                }
            }
            return result;
        }

        /**
         * Uses Quaternion slerp to approximate the most it can rotate towards,
         * without violating valid rotation limits. This tends to cause rotation in
         * otherwise 'free' axis to be blocked, so it isn't most ideal.
         *
         * @param fullRotation Full rotation that is desired
         * @return Most it can be rotated towards without violating limits
         */
        private Quaternion calcUsingSlerp(Quaternion fullRotation) {
            Quaternion startRotation = createRotation(0.0, 0.0);

            double t0 = 0.0;
            double t1 = 1.0;
            Quaternion result = startRotation;
            for (int n = 0; n < MAX_INTERPOLATION_ROUNDS; n++) {
                double th = 0.5 * (t0 + t1);
                Quaternion qh = Quaternion.slerp(startRotation, fullRotation, th);
                if (isValidRotation(qh)) {
                    // Valid values between th and t1
                    t0 = th;
                    result = qh;
                } else {
                    // Valid values between t0 and th
                    t1 = th;
                }
            }
            return result;
        }

        private boolean testRotation(Quaternion tmp, double deltaPitch, double deltaYaw) {
            tmp.setTo(this.base);
            tmp.rotateY(this.baseYaw + deltaYaw);
            tmp.rotateX(this.basePitch + deltaPitch);
            return isValidRotation(tmp);
        }

        private Quaternion createRotation(double deltaPitch, double deltaYaw) {
            Quaternion result = new Quaternion();
            testRotation(result, deltaPitch, deltaYaw);
            return result;
        }

        private boolean isValidRotation(Quaternion rotation) {
            // Pitch must stay within -90 and 90
            if (isUpsideDown(rotation)) {
                return false;
            }

            // If set, forward vector angle must not exceed limits
            if (maxForwardZ != 1.0) {
                // Create a cylinder by setting y to 0 and re-normalizing
                Vector forward = rotation.forwardVector().setY(0.0).normalize();
                if (forward.getZ() < maxForwardZ) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Immutable container for (relative) yaw/pitch values
     */
    public static final class YawPitch {
        public static final YawPitch ZERO = new YawPitch(0.0f, 0.0f);

        public final float yaw;
        public final float pitch;

        public YawPitch(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public static YawPitch add(YawPitch a, YawPitch b) {
            return new YawPitch(a.yaw + b.yaw, a.pitch + b.pitch);
        }

        public static YawPitch subtract(YawPitch a, YawPitch b) {
            return new YawPitch(a.yaw - b.yaw, a.pitch - b.pitch);
        }
    }
}
