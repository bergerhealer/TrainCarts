package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerRotationPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundMovePlayerPacketHandle;

import java.util.Collections;

/**
 * Tracks changes in player yaw/pitch by listening for incoming look packets.
 * Can optionally perform spectator pitch adjustment logic to keep vertical look
 * smooth while in spectate-based camera modes.
 */
public class ViewRotationTracker {
    /** Adjusts player look pitch when the angle is beyond this point for infinite vertical look */
    private static final float PITCH_ADJ_THRESHOLD = 15.0f;

    private final AttachmentViewer viewer;
    private final Object stateLock = new Object();
    private Rotation pendingRotation = Rotation.ZERO;
    private Rotation lastRotation = null;
    private boolean isAdjustingPitch = false;
    private Rotation yawPitchDuringAdjustment = null;
    private float inFlightPitchCorrection = 0.0f;
    private int inFlightPitchCorrectionsCurrTick = -1;
    private double positionYCorrection = 0.0;
    private boolean usePitchAdjustment = true;
    private final TrackerControl control;
    private boolean stopped = false;

    ViewRotationTracker(AttachmentViewer viewer, TrackerControl control) {
        this.viewer = viewer;
        this.control = control;
    }

    /**
     * Sets the Y-value offset that should be removed from incoming movement packets.
     * Useful when the player is mounted at a fake offset while spectating.
     *
     * @param positionYCorrection Y correction to subtract from incoming packet Y values
     */
    public void setPositionYCorrection(double positionYCorrection) {
        this.positionYCorrection = positionYCorrection;
    }

    /**
     * Enables or disables pitch adjustment logic while tracking.
     *
     * @param usePitchAdjustment True to enable pitch adjustment, False to disable
     */
    public void setUsePitchAdjustment(boolean usePitchAdjustment) {
        this.usePitchAdjustment = usePitchAdjustment;
    }

    void reset() {
        synchronized (stateLock) {
            pendingRotation = Rotation.ZERO;
            lastRotation = null;
            isAdjustingPitch = false;
            yawPitchDuringAdjustment = null;
            inFlightPitchCorrection = 0.0f;
            inFlightPitchCorrectionsCurrTick = -1;
        }
    }

    /**
     * Enables packet tracking for this tracker session.
     */
    public void enable() {
        synchronized (this) {
            if (stopped) {
                return;
            }
        }
        control.enable(this);
    }

    /**
     * Stops packet tracking and terminates the underlying listener after client sync.
     * If this tracker is no longer active, this call is a no-op.
     */
    public void stop() {
        boolean wasStopped;
        synchronized (this) {
            wasStopped = stopped;
            stopped = true;
        }
        if (!wasStopped) {
            control.stop(this);
        }
    }

    void markStoppedByReplacement() {
        synchronized (this) {
            stopped = true;
        }
    }

    /**
     * Gets and resets the accumulated relative rotation since the last call.
     *
     * @return Accumulated relative yaw/pitch change
     */
    public Rotation readAndResetRotationChange() {
        synchronized (stateLock) {
            Rotation result = pendingRotation;
            pendingRotation = Rotation.ZERO;
            return result;
        }
    }

    /**
     * Gets the last absolute yaw/pitch observed in incoming look packets.
     *
     * @return Last observed absolute rotation, or null if none observed yet
     */
    public Rotation getLastRotation() {
        synchronized (stateLock) {
            return lastRotation;
        }
    }

    void onPacketReceive(PacketReceiveEvent event) {
        synchronized (this) {
            if (stopped) {
                return;
            }
        }
        ServerboundMovePlayerPacketHandle p = ServerboundMovePlayerPacketHandle.createHandle(event.getPacket().getHandle());
        if (positionYCorrection != 0.0 && event.getType() != PacketType.IN_LOOK) {
            p.setY(p.getY() - positionYCorrection);
        }
        if (event.getType() != PacketType.IN_POSITION) {
            detectLookChanges(new Rotation(p.getYaw(), p.getPitch()));
        }
    }

    private void detectLookChanges(Rotation newRotation) {
        Float pitchAdjustment = null;

        synchronized (stateLock) {
            if (isAdjustingPitch) {
                yawPitchDuringAdjustment = newRotation;
                return;
            }

            if (lastRotation != null) {
                pendingRotation = Rotation.add(pendingRotation, Rotation.subtract(newRotation, lastRotation));
            }
            lastRotation = newRotation;

            if (usePitchAdjustment) {
                float pitchErrorFromZero = MathUtil.wrapAngle(-newRotation.pitch - inFlightPitchCorrection);
                if (Math.abs(pitchErrorFromZero) > PITCH_ADJ_THRESHOLD) {
                    int currTick = CommonUtil.getServerTicks();
                    if (currTick != inFlightPitchCorrectionsCurrTick) {
                        inFlightPitchCorrectionsCurrTick = currTick;
                        inFlightPitchCorrection += pitchErrorFromZero;
                        pitchAdjustment = pitchErrorFromZero;
                    }
                }
            }
        }

        if (pitchAdjustment != null) {
            makePitchAdjustment(newRotation, pitchAdjustment.floatValue());
        }
    }

    private void ackPitchAdjustStart() {
        synchronized (stateLock) {
            isAdjustingPitch = true;
            yawPitchDuringAdjustment = null;
        }
    }

    private void ackRelativePitchAdjustDone(float pitchChange) {
        synchronized (stateLock) {
            if (!isAdjustingPitch) {
                return;
            }

            inFlightPitchCorrection -= pitchChange;
            if (lastRotation != null) {
                lastRotation = new Rotation(lastRotation.yaw, lastRotation.pitch + pitchChange);
            }

            Rotation duringAdjustment = this.yawPitchDuringAdjustment;
            this.yawPitchDuringAdjustment = null;
            this.isAdjustingPitch = false;
            if (duringAdjustment != null) {
                detectLookChanges(duringAdjustment);
            }
        }
    }

    private void ackAbsoluteRotationAdjust(float absoluteYaw, float pitchChange) {
        synchronized (stateLock) {
            if (!isAdjustingPitch) {
                return;
            }

            inFlightPitchCorrection -= pitchChange;
            if (lastRotation != null) {
                lastRotation = new Rotation(absoluteYaw, 0.0f);
            }

            Rotation duringAdjustment = this.yawPitchDuringAdjustment;
            this.yawPitchDuringAdjustment = null;
            this.isAdjustingPitch = false;
            if (duringAdjustment != null) {
                detectLookChanges(duringAdjustment);
            }
        }
    }

    private void makePitchAdjustment(Rotation newRotation, float pitchAdjustment) {
        if (viewer.supportRelativeRotationUpdate()) {
            viewer.getClientSynchronizer().synchronizeBundle(
                    Collections.singletonList(
                            ClientboundPlayerRotationPacketHandle.createRelative(0.0f, pitchAdjustment)
                    ),
                    this::ackPitchAdjustStart,
                    () -> ackRelativePitchAdjustDone(pitchAdjustment));
        } else {
            viewer.getClientSynchronizer().synchronizeBundle(
                    Collections.singletonList(
                            ClientboundPlayerRotationPacketHandle.createAbsolute(newRotation.yaw, 0.0f)
                    ),
                    this::ackPitchAdjustStart,
                    () -> ackAbsoluteRotationAdjust(newRotation.yaw, pitchAdjustment));
        }
    }

    interface TrackerControl {
        void enable(ViewRotationTracker tracker);
        void stop(ViewRotationTracker tracker);
    }

    /**
     * Immutable yaw/pitch rotation values.
     */
    public static final class Rotation {
        public static final Rotation ZERO = new Rotation(0.0f, 0.0f);

        public final float yaw;
        public final float pitch;

        public Rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public static Rotation add(Rotation a, Rotation b) {
            return new Rotation(a.yaw + b.yaw, a.pitch + b.pitch);
        }

        public static Rotation subtract(Rotation a, Rotation b) {
            return new Rotation(a.yaw - b.yaw, a.pitch - b.pitch);
        }
    }
}
