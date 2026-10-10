package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.PlayerAbilities;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.controller.player.SpectatedFakePlayer;
import com.bergerkiller.bukkit.tc.controller.player.ViewRotationTracker;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerRotationPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundMovePlayerPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerInputPacketHandle;
import com.bergerkiller.generated.net.minecraft.server.level.ServerPlayerHandle;
import org.bukkit.util.Vector;

import java.util.Collections;

/**
 * Movement controller that keeps the player spectating a fake player entity.
 * The fake player is moved using requested movement API updates, while look
 * orientation is synchronized from incoming look packets.
 */
class PlayerMovementControllerSpectated extends PlayerMovementController {
    private static final boolean HAS_CLIENT_TICK_END_PACKET = Common.evaluateMCVersion(">=", "1.21.2");
    private static final int DOUBLE_TAP_FORWARD_THRESHOLD_TICKS = 7;

    private final Object stateLock = new Object();
    private final SpectatedFakePlayer fakePlayer;
    private final boolean useClientTickEndPacket;
    private AttachmentViewer.Input input = AttachmentViewer.Input.ofPlayer(player);
    /** Whether double-tap W sprint was toggled on */
    private boolean hasSprintDoubleTapAction = false;
    private int clientTicksElapsed = 0;
    private int lastForwardTapTick = Integer.MIN_VALUE;
    private boolean wasForwardPressed = false;
    private boolean acceptLookPackets = false;
    private ViewRotationTracker.Rotation lastLookRotation = null;
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
    protected void syncPosition(Vector position, Quaternion orientation) {
        synchronized (stateLock) {
            requestedPosition = position.clone();
            if (orientation != null) {
                requestedOrientation = orientation.clone();
            } else {
                updateOrientationFromLookPacket();
            }

            if (requestedOrientation == null) {
                requestedOrientation = Quaternion.fromLookDirection(player.getEyeLocation().getDirection(), new Vector(0.0, 1.0, 0.0));
            }

            ensureStarted();

            fakePlayer.updatePosition(createFakePlayerTransform());
            fakePlayer.syncPosition(true);

            ServerPlayerHandle sp = ServerPlayerHandle.fromBukkit(player);
            sp.setPosition(requestedPosition.getX(), requestedPosition.getY(), requestedPosition.getZ());
        }
    }

    @Override
    protected void onStopped() {
        synchronized (stateLock) {
            fakePlayer.setOnRealPlayerPositionSynchronized(null);
            if (started) {
                started = false;
                fakePlayer.stop();
            }
            acceptLookPackets = false;
            requestedPosition = null;
            requestedOrientation = null;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketType type = event.getType();
        if (type == PacketType.IN_POSITION_LOOK || type == PacketType.IN_LOOK) {
            ServerboundMovePlayerPacketHandle packet = ServerboundMovePlayerPacketHandle.createHandle(event.getPacket().getHandle());
            synchronized (stateLock) {
                if (acceptLookPackets) {
                    lastLookRotation = new ViewRotationTracker.Rotation(packet.getYaw(), packet.getPitch());
                }
            }
        } else if (type == PacketType.IN_STEER_VEHICLE) {
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

    private void ensureStarted() {
        if (started) {
            return;
        }
        final ViewRotationTracker.Rotation initialRotation = new ViewRotationTracker.Rotation(
                player.getEyeLocation().getYaw(),
                player.getEyeLocation().getPitch());
        lastLookRotation = initialRotation;
        requestedOrientation = Quaternion.fromYawPitchRoll(initialRotation.pitch, initialRotation.yaw, 0.0);
        acceptLookPackets = false;

        fakePlayer.start(createFakePlayerTransform(), new Vector());
        viewer.getClientSynchronizer().synchronizeBundle(
                Collections.singletonList(
                        ClientboundPlayerRotationPacketHandle.createAbsolute(initialRotation.yaw, initialRotation.pitch)
                ),
                () -> {},
                () -> {
                    synchronized (stateLock) {
                        acceptLookPackets = true;
                    }
                });

        started = true;
    }

    private void updateOrientationFromLookPacket() {
        ViewRotationTracker.Rotation rotation = lastLookRotation;
        if (rotation == null) {
            return;
        }
        requestedOrientation = Quaternion.fromYawPitchRoll(rotation.pitch, rotation.yaw, 0.0);
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
