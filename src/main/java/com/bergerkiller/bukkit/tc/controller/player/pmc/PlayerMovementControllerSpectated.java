package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.PlayerAbilities;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer.ViewRotationTracker;
import com.bergerkiller.bukkit.tc.controller.player.SpectatedFakePlayer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerInputPacketHandle;
import com.bergerkiller.generated.net.minecraft.server.level.ServerPlayerHandle;
import org.bukkit.util.Vector;

/**
 * Movement controller that keeps the player spectating a fake player entity.
 * The fake player is moved using requested movement API updates, while look
 * orientation is synchronized using the attachment viewer's rotation tracker.
 */
class PlayerMovementControllerSpectated extends PlayerMovementController {
    private static final boolean HAS_CLIENT_TICK_END_PACKET = Common.evaluateMCVersion(">=", "1.21.2");
    private static final int DOUBLE_TAP_FORWARD_THRESHOLD_TICKS = 7;
    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;

    private final Object stateLock = new Object();
    private final SpectatedFakePlayer fakePlayer;
    private final boolean useClientTickEndPacket;
    private AttachmentViewer.Input input = AttachmentViewer.Input.ofPlayer(player);
    private ViewRotationTracker viewRotationTracker = null;
    /** Whether double-tap W sprint was toggled on */
    private boolean hasSprintDoubleTapAction = false;
    private int clientTicksElapsed = 0;
    private int lastForwardTapTick = Integer.MIN_VALUE;
    private boolean wasForwardPressed;
    private ViewRotationTracker.Rotation viewRotation = null;
    private Vector requestedPosition = null;
    private Quaternion requestedOrientation = null;
    private boolean started = false;

    protected PlayerMovementControllerSpectated(ControllerType type, AttachmentViewer viewer) {
        super(type, viewer);
        this.fakePlayer = viewer.createSpectatedFakePlayer();
        this.useClientTickEndPacket = HAS_CLIENT_TICK_END_PACKET && viewer.evaluateGameVersion(">=", "1.21.2");
        this.fakePlayer.setForceAbsoluteSync(true);

        wasForwardPressed = input.forwards();
        if (viewer.getPlayer().isSprinting()) {
            input = input.withSprinting(true);
            hasSprintDoubleTapAction = true;
        }
    }

    @Override
    public AttachmentViewer.Input getInput() {
        synchronized (stateLock) {
            return input;
        }
    }

    @Override
    protected void syncPosition(Vector position, Quaternion orientation, Quaternion orientationDelta) {
        synchronized (stateLock) {
            requestedPosition = position.clone();
            if (orientationDelta != null) {
                applyOrientationDelta(orientationDelta);
            }
            updateOrientationFromRotationTracker();

            if (viewRotation == null) {
                setViewRotation(Quaternion.fromLookDirection(player.getEyeLocation().getDirection(), new Vector(0.0, 1.0, 0.0)));
            }

            ensureStarted();

            fakePlayer.updatePosition(createFakePlayerTransform());
            fakePlayer.syncPosition(true);

            ServerPlayerHandle sp = ServerPlayerHandle.fromBukkit(player);
            sp.setPositionRotation(requestedPosition.getX(), requestedPosition.getY(), requestedPosition.getZ(), viewRotation.yaw, viewRotation.pitch);
        }
    }

    private void ensureStarted() {
        if (started) {
            return;
        }
        ViewRotationTracker.Rotation initialRotation = viewRotation;
        if (initialRotation == null) {
            initialRotation = ViewRotationTracker.Rotation.fromPlayerEye(player.getEyeLocation());
            viewRotation = initialRotation;
            requestedOrientation = Quaternion.fromYawPitchRoll(initialRotation.pitch, initialRotation.yaw, 0.0);
        }

        viewRotationTracker = viewer.startViewRotationTracking();
        viewRotationTracker.setRotation(initialRotation);
        viewRotationTracker.setUsePitchAdjustment(true);
        viewRotationTracker.enable();

        fakePlayer.start(createFakePlayerTransform(), new Vector());

        started = true;
    }

    @Override
    protected void onStopped() {
        final ViewRotationTracker trackerToStop;
        synchronized (stateLock) {
            if (started) {
                started = false;
                fakePlayer.stop();
            }
            trackerToStop = viewRotationTracker;
            viewRotationTracker = null;
            requestedPosition = null;
            requestedOrientation = null;
            viewRotation = null;
        }

        if (trackerToStop != null) {
            trackerToStop.stop();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        final PacketType type = event.getType();
        if (type == PacketType.IN_STEER_VEHICLE) {
            ServerboundPlayerInputPacketHandle packet = ServerboundPlayerInputPacketHandle.createHandle(event.getPacket().getHandle());
            synchronized (stateLock) {
                boolean isForwardPressed = packet.isForward();
                if (!wasForwardPressed && isForwardPressed) {
                    int currTick = currentInputTick();
                    hasSprintDoubleTapAction = lastForwardTapTick != Integer.MIN_VALUE &&
                            (currTick - lastForwardTapTick) <= DOUBLE_TAP_FORWARD_THRESHOLD_TICKS;
                    lastForwardTapTick = currTick;
                } else if (!isForwardPressed) {
                    hasSprintDoubleTapAction = false;
                }
                wasForwardPressed = isForwardPressed;

                AttachmentViewer.Input currInput = AttachmentViewer.Input.fromVehicleSteer(packet);
                if (hasSprintDoubleTapAction) {
                    currInput = currInput.withSprinting(true);
                }
                input = currInput;
                if (translateVehicleSteer) {
                    event.setPacket(input.createSteerPacket());
                }
            }
        } else if (type == PacketType.IN_CLIENT_TICK_END) {
            if (useClientTickEndPacket) {
                synchronized (stateLock) {
                    clientTicksElapsed++;
                }
            }
        } else if (type == PacketType.IN_ABILITIES) {
            ServerboundPlayerAbilitiesPacketHandle p = ServerboundPlayerAbilitiesPacketHandle.createHandle(event.getPacket().getHandle());
            if (!p.isFlying()) {
                event.setCancelled(true);
                PlayerAbilities pa = ServerPlayerHandle.fromBukkit(event.getPlayer()).getAbilities();
                ClientboundPlayerAbilitiesPacketHandle pp = ClientboundPlayerAbilitiesPacketHandle.createNew(pa);
                PacketUtil.queuePacket(event.getPlayer(), pp);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }

    private void updateOrientationFromRotationTracker() {
        ViewRotationTracker tracker = viewRotationTracker;
        if (tracker != null) {
            ViewRotationTracker.Rotation rotation = tracker.readAndResetRotationChange();
            if (rotation != ViewRotationTracker.Rotation.ZERO) {
                applyRotationDelta(rotation.yaw, rotation.pitch);
            }
        }
    }

    private void setViewRotation(Quaternion orientation) {
        Vector yawPitchRoll = orientation.getYawPitchRoll();
        viewRotation = new ViewRotationTracker.Rotation(
                (float) yawPitchRoll.getY(),
                MathUtil.clamp((float) yawPitchRoll.getX(), MIN_PITCH, MAX_PITCH));
        requestedOrientation = Quaternion.fromYawPitchRoll(viewRotation.pitch, viewRotation.yaw, 0.0);
    }

    private void applyOrientationDelta(Quaternion delta) {
        Vector yawPitchRoll = delta.getYawPitchRoll();
        applyRotationDelta((float) yawPitchRoll.getY(), (float) yawPitchRoll.getX());
    }

    private void applyRotationDelta(float yawDelta, float pitchDelta) {
        if (viewRotation == null) {
            setViewRotation(Quaternion.fromLookDirection(player.getEyeLocation().getDirection(), new Vector(0.0, 1.0, 0.0)));
        }

        viewRotation = new ViewRotationTracker.Rotation(
                viewRotation.yaw + yawDelta,
                MathUtil.clamp(viewRotation.pitch + pitchDelta, MIN_PITCH, MAX_PITCH));
        requestedOrientation = Quaternion.fromYawPitchRoll(viewRotation.pitch, viewRotation.yaw, 0.0);
    }

    private Matrix4x4 createFakePlayerTransform() {
        Matrix4x4 transform = new Matrix4x4();
        transform.translate(requestedPosition);
        transform.rotate(requestedOrientation);
        return transform;
    }

    private int currentInputTick() {
        return useClientTickEndPacket ? clientTicksElapsed : CommonUtil.getServerTicks();
    }
}
