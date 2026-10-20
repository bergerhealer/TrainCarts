package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer.ViewRotationTracker.Rotation;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerRotationPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundMovePlayerPacketHandle;
import org.bukkit.Location;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks changes in player yaw/pitch by listening for incoming look packets.
 * Can optionally perform spectator pitch adjustment logic to keep vertical look
 * smooth while in spectate-based camera modes.
 */
class ViewRotationTrackerImpl {
    /** Adjusts player look pitch when the angle is beyond this point for infinite vertical look */
    private static final float PITCH_ADJ_THRESHOLD = 15.0f;

    private final AttachmentViewer viewer;
    private final Object stateLock = new Object();
    private Rotation currentRotation = null;
    private RotationSession rotationSession = null;
    private boolean isAdjustingClientView = false;
    private Rotation yawPitchDuringAdjustment = null;
    private boolean hasInflightAbsoluteSync = false;
    private float inFlightPitchCorrection = 0.0f;
    private int inFlightPitchCorrectionsCurrTick = -1;
    private boolean usePitchAdjustment = true;
    private final TrackerControl control;
    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);

    ViewRotationTrackerImpl(AttachmentViewer viewer, TrackerControl control) {
        this.viewer = viewer;
        this.control = control;
    }

    void setSession(RotationSession rotationSession) {
        synchronized (stateLock) {
            this.rotationSession = rotationSession;
        }
    }

    void setRotation(Rotation rotation) {
        synchronized (stateLock) {
            pushRotation(rotation, false);
        }
    }

    /**
     * Enables or disables pitch adjustment logic while tracking.
     *
     * @param usePitchAdjustment True to enable pitch adjustment, False to disable
     */
    public void setUsePitchAdjustment(boolean usePitchAdjustment) {
        this.usePitchAdjustment = usePitchAdjustment;
    }

    /**
     * Enables packet tracking for this tracker session.
     */
    public void enable() {
        if (state.get() == State.STOPPED) {
            return;
        }
        control.enable(this);
    }

    /**
     * Stops packet tracking and terminates the underlying listener after client sync.
     * If this tracker is no longer active, this call is a no-op.
     */
    public void stop() {
        if (!state.compareAndSet(State.RUNNING, State.STOPPING)) {
            return;
        }

        final Location playerRealEye = Util.getRealEyeLocation(viewer.getPlayer());
        makeAbsoluteStopAdjustment(new Rotation(playerRealEye.getYaw(), playerRealEye.getPitch()));
    }

    void markStoppedByReplacement() {
        if (state.getAndSet(State.STOPPED) != State.STOPPED) {
            control.stop(this);
        }
    }

    boolean requestUse() {
        for (;;) {
            State curr = state.get();
            if (curr == State.RUNNING) {
                return true;
            } else if (curr == State.STOPPING) {
                if (state.compareAndSet(State.STOPPING, State.RUNNING)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    boolean isStopping() {
        return state.get() == State.STOPPING;
    }

    boolean isStopped() {
        return state.get() == State.STOPPED;
    }

    void onPacketReceive(PacketReceiveEvent event) {
        ServerboundMovePlayerPacketHandle p = ServerboundMovePlayerPacketHandle.createHandle(event.getPacket().getHandle());
        if (event.getType() != PacketType.IN_POSITION) {
            detectLookChanges(new Rotation(p.getYaw(), p.getPitch()), true);
        }

        event.setCancelled(true);
    }

    private void detectLookChanges(Rotation newRotation, boolean fromPacket) {
        Float pitchAdjustment = null;

        synchronized (stateLock) {
            if (state.get() == State.STOPPED) {
                return;
            }

            if (isAdjustingClientView) {
                yawPitchDuringAdjustment = newRotation;
                return;
            }

            pushRotation(newRotation, true);

            if (usePitchAdjustment && !hasInflightAbsoluteSync) {
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

    private void ackClientViewAdjustStart() {
        synchronized (stateLock) {
            isAdjustingClientView = true;
            yawPitchDuringAdjustment = null;
        }
    }

    private void ackAbsoluteStopAdjustDone(Rotation newRotation) {
        boolean stopped;

        synchronized (stateLock) {
            if (!isAdjustingClientView) {
                return;
            }

            pushRotation(newRotation, false);

            afterAdjustment();

            stopped = state.compareAndSet(State.STOPPING, State.STOPPED);
            hasInflightAbsoluteSync = false;
        }

        if (stopped) {
            control.stop(this);
        }
    }

    private void ackRelativePitchAdjustDone(float pitchChange) {
        synchronized (stateLock) {
            if (!isAdjustingClientView) {
                return;
            }

            inFlightPitchCorrection -= pitchChange;
            if (currentRotation != null) {
                pushRotation(new Rotation(currentRotation.yaw, currentRotation.pitch + pitchChange), false);
            }

            afterAdjustment();
        }
    }

    private void ackAbsoluteRotationPitchAdjust(float absoluteYaw, float pitchChange) {
        synchronized (stateLock) {
            if (!isAdjustingClientView) {
                return;
            }

            inFlightPitchCorrection -= pitchChange;
            pushRotation(new Rotation(absoluteYaw, 0.0f), false);

            afterAdjustment();
        }
    }

    private void afterAdjustment() {
        Rotation duringAdjustment = this.yawPitchDuringAdjustment;
        this.yawPitchDuringAdjustment = null;
        this.isAdjustingClientView = false;
        if (duringAdjustment != null) {
            detectLookChanges(duringAdjustment, false);
        }
    }

    private void makeAbsoluteStopAdjustment(Rotation newRotation) {
        hasInflightAbsoluteSync = true;
        viewer.getClientSynchronizer().synchronizeBundle(
                Collections.singletonList(
                        ClientboundPlayerRotationPacketHandle.createAbsolute(newRotation.yaw, newRotation.pitch)
                ),
                this::ackClientViewAdjustStart,
                () -> ackAbsoluteStopAdjustDone(newRotation));
    }

    private void makePitchAdjustment(Rotation newRotation, float pitchAdjustment) {
        if (viewer.supportsRelativeRotationUpdate()) {
            viewer.getClientSynchronizer().synchronizeBundle(
                    Collections.singletonList(
                            ClientboundPlayerRotationPacketHandle.createRelative(0.0f, pitchAdjustment)
                    ),
                    this::ackClientViewAdjustStart,
                    () -> ackRelativePitchAdjustDone(pitchAdjustment));
        } else {
            viewer.getClientSynchronizer().synchronizeBundle(
                    Collections.singletonList(
                            ClientboundPlayerRotationPacketHandle.createAbsolute(newRotation.yaw, 0.0f)
                    ),
                    this::ackClientViewAdjustStart,
                    () -> ackAbsoluteRotationPitchAdjust(newRotation.yaw, pitchAdjustment));
        }
    }

    interface TrackerControl {
        void enable(ViewRotationTrackerImpl tracker);
        void stop(ViewRotationTrackerImpl tracker);
    }

    enum State {
        STOPPING,
        STOPPED,
        RUNNING
    }

    interface RotationSession {
        void pushRotation(Rotation newRotation, Rotation changes);
    }

    private void pushRotation(Rotation newRotation, boolean detectChanges) {
        Rotation currentRotation = this.currentRotation;
        Rotation changes = Rotation.ZERO;
        if (currentRotation != null && detectChanges) {
            changes = Rotation.subtract(newRotation, currentRotation);
        }
        this.currentRotation = newRotation;
        if (rotationSession != null) {
            rotationSession.pushRotation(newRotation, changes);
        }
    }
}
