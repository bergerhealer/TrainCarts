package com.bergerkiller.bukkit.tc.attachments.control.seat;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerPacketListener;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInFlyingHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityPlayerHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.spectator.FirstPersonSpectatedEntity;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

import java.util.Collections;

/**
 * Makes the player spectate an Entity and then moves that Entity to
 * move the camera around.
 */
public class FirstPersonViewSpectator extends FirstPersonView {
    /** The real (invisible) player is floating this high above the entity being spectated */
    private static final double GHOST_Y_OFFSET = 64;
    /** Adjusts player look pitch when the angle is beyond this point for infinite vertical look */
    private static final float PITCH_ADJ_THRESHOLD = 15.0f;
    // Vehicle entity id, -1 if not used
    private int vehicleEntityId = -1;
    // Controls all the spectating logic itself, depending on the type of view mode used
    private FirstPersonSpectatedEntity _spectatedEntity = null;
    // Holds the player nearby, off-screen, while spectating. Out of the way of the
    // spectated entity to prevent self-interaction-caused player d/c.
    private VirtualEntity _playerMount = null;
    // Tracks player input while inside this FPV mode
    private final SpectatorInput _input = new SpectatorInput();
    // This alters player position so that it is not where the fake mount is, to avoid issues
    private PlayerPacketListener<?> _spectatorPacketListener = null;

    public FirstPersonViewSpectator(CartAttachmentSeat seat, AttachmentViewer player) {
        super(seat, player);
    }

    /**
     * If not already spawned, spawns a fake vehicle, or otherwise returns the Entity ID
     * to which passengers can be mounted directly into the vehicle.
     *
     * @return
     */
    public int prepareVehicleEntityId() {
        if (vehicleEntityId == -1) {
            vehicleEntityId = seat.seated.spawnVehicleMount(player);
        }
        return vehicleEntityId;
    }

    @Override
    public boolean doesViewModeChangeRequireReset(FirstPersonViewMode newViewMode) {
        // Respawns the seated entity in third-person, so a reset is needed
        return newViewMode == FirstPersonViewMode.THIRD_P ||
               this.getLiveMode() == FirstPersonViewMode.THIRD_P;
    }

    @Override
    protected Matrix4x4 getEyeTransform() {
        Matrix4x4 base = super.getEyeTransform();
        _input.applyTo(base);
        return base;
    }

    /**
     * Gets the current exact head rotation of the Player inside this seat.
     * This differs from the player entity's rotation, because of the relative
     * transformation that is applied.
     *
     * @param transform Current body (butt) transformation of this seated entity
     * @return current head rotation
     */
    protected Quaternion getCurrentHeadRotation(Matrix4x4 transform) {
        transform = transform.clone();

        // Adjust for eye position changes, if set
        if (!this._eyePosition.isDefault()) {
            transform.multiply(this._eyePosition.transform);
        }

        // Adjust for the relative rotation input by the player
        _input.applyTo(transform);

        return transform.getRotation();
    }

    @Override
    public void makeVisible(AttachmentViewer viewer, boolean isReload) {
        // Make the player invisible - we don't want it to get in view
        setPlayerVisible(viewer, false);
        vehicleEntityId = -1;

        // Start tracking player input
        if (this.getLockMode() == FirstPersonViewLockMode.SPECTATOR_FREE) {
            _input.start(viewer, seat.isRotationLocked() ? BODY_LOCK_FOV_LIMIT : 360.0f);
        } else {
            _input.startLocked();
        }

        // Position used to compute where the eye/camera view is at
        Matrix4x4 eyeTransform = this.getEyeTransform();

        // Start spectator mode
        this._spectatedEntity = FirstPersonSpectatedEntity.create(seat, this, viewer);
        this._spectatedEntity.start(eyeTransform);

        // Create the packet listener for modifying state
        if (this._spectatorPacketListener != null) {
            this._spectatorPacketListener.terminate();
            this._spectatorPacketListener = null;
        }
        this._spectatorPacketListener = viewer.createPacketListener(new ViewControlPacketListener(),
                PacketType.IN_POSITION_LOOK, PacketType.IN_POSITION, PacketType.IN_LOOK);

        // Mount the player itself off-screen on a mount somewhere
        // We want it to stay out of clickable range to prevent player d/c
        if (this._playerMount == null) {
            this._playerMount = new VirtualEntity(seat.getManager());
            this._playerMount.setEntityType(EntityType.ARMOR_STAND);
            this._playerMount.setSyncMode(SyncMode.SEAT);

            // Put the Player somewhere high up there in the sky
            this._playerMount.setRelativeOffset(0.0, GHOST_Y_OFFSET, 0.0);
            this._playerMount.updatePosition(eyeTransform);
            this._playerMount.syncPosition(true);
            this._playerMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            this._playerMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
            this._playerMount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                    EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                    EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                    EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
            this._playerMount.spawn(viewer, new Vector());

            // Sync the player to be high up in the sky, and then begin intercepting player inputs
            // This also gets rid of the GHOST_Y_OFFSET that the server receives from the player
            Vector pos = this._playerMount.getSyncPos();
            viewer.getClientSynchronizer().synchronize(teleportId -> PacketPlayOutPositionHandle.createNew(
                    pos.getX(), pos.getY(), pos.getZ(),
                    this._playerMount.getSyncYaw(), this._playerMount.getSyncPitch(),
                    0.0, 0.0, 0.0,
                    RelativeFlags.ABSOLUTE_POSITION,
                    teleportId), p -> _spectatorPacketListener.enable());

            // Mount the player. Happens after the position sync.
            viewer.getVehicleMountController().mount(this._playerMount.getEntityId(), viewer.getEntityId());
        }

        // If third-person mode is used, also spawn the real seated entity for this viewer
        if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            seat.seated.makeVisibleFirstPerson(viewer);
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer, boolean isReload) {
        // If third-person mode is used, also despawn the real seated entity for this viewer
        if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            seat.seated.makeHiddenFirstPerson(viewer);
        }

        // Even when we hide it now, it can take some time before the inputs from the client
        // stop coming. So we got to keep intercepting until this is done.
        // We do another sync for that.
        if (_spectatorPacketListener != null) {
            viewer.getClientSynchronizer().synchronize(_spectatorPacketListener::terminate);
            _spectatorPacketListener = null;
        }

        // Remove player from the temporary mount
        if (_playerMount != null) {
            VehicleMountController vmc = viewer.getVehicleMountController();
            vmc.unmount(this._playerMount.getEntityId(), viewer.getEntityId());

            _playerMount.destroy(viewer);
            _playerMount = null;

            // The player will be really high up there after ejecting, put them back at a sane position
            if (_spectatedEntity != null) {
                VirtualEntity entity = _spectatedEntity.getCurrentEntity();
                Vector pos = entity.getSyncPos();

                // Ensure that the player position is sync with where it should be
                // We already do this with the player position modifier, but to be safe...
                EntityPlayerHandle playerHandle = EntityPlayerHandle.fromBukkit(viewer.getPlayer());
                playerHandle.setPositionRotation(pos.getX(), pos.getY(), pos.getZ(),
                        entity.getSyncYaw(), entity.getSyncPitch());
                playerHandle.setFallDistance(0.0f);

                viewer.send(PacketPlayOutPositionHandle.createAbsolute(pos.getX(), pos.getY(), pos.getZ(),
                        entity.getSyncYaw(), entity.getSyncPitch()));
            }
        }

        // Release camera of spectated entity & destroy it
        if (_spectatedEntity != null) {
            _spectatedEntity.stop();
            _spectatedEntity = null;
        }

        // Hide any fake mount previously used
        if (vehicleEntityId != -1) {
            seat.seated.despawnVehicleMount(viewer);
            vehicleEntityId = -1;
        }

        // Make viewer visible to himself again (restore)
        if (this.getLiveMode() != FirstPersonViewMode.THIRD_P) {
            setPlayerVisible(viewer, true);
        }

        // Cleanup, moves player view so it faces where it looked in spectator mode
        _input.stop(this.getEyeTransform());
    }

    @Override
    public void onTick() {
        // Update spectated entity
        if (_spectatedEntity != null) {
            Matrix4x4 baseTransform = getEyeTransform();
            _playerMount.updatePosition(baseTransform);
            _spectatedEntity.updatePosition(baseTransform);
        }

        // Update input
        _input.update();
    }

    @Override
    public void onMove(boolean absolute) {
        // Move the spectated entity
        if (_spectatedEntity != null) {
            _playerMount.syncPosition(absolute);
            _spectatedEntity.syncPosition(absolute);
        }
    }

    /**
     * Active while the player is in this seats spectator mode to track the yaw/pitch changes
     * of the player. Occasionally forces an adjustment of pitch to allow for infinite vertical
     * panning.
     */
    private class ViewControlPacketListener implements PacketListener {
        /** While we process exclusively on the netty thread, it can't hurt to be safe */
        private final Object stateLock = new Object();
        /** Previous yaw/pitch value to use to detect look changes in the packet flow */
        private SpectatorInput.YawPitch lastYawPitch = null;
        /** Is set to true at the start of a pitch adjustment cycle */
        private boolean isAdjustingPitch = false;
        /** Keeps track of yaw/pitch received during the adjustment cycle */
        private SpectatorInput.YawPitch yawPitchDuringAdjustment = null;
        /** Total pitch adjustment offset that still need to be acknowledged by the client */
        private float inFlightPitchCorrection = 0.0f;
        /** Avoids sending too many adjustments per tick */
        private int inFlightPitchCorrectionsCurrTick = -1;

        private void ackPitchAdjustStart() {
            synchronized (stateLock) {
                isAdjustingPitch = true;
                yawPitchDuringAdjustment = null;
            }
        }

        private void ackPitchAdjustDone(float pitchChange) {
            synchronized (stateLock) {
                if (!isAdjustingPitch) {
                    return; // Desync???
                }

                // Apply the pitch change to the tracked client state
                inFlightPitchCorrection -= pitchChange;
                if (lastYawPitch != null) {
                    lastYawPitch = new SpectatorInput.YawPitch(lastYawPitch.yaw, lastYawPitch.pitch + pitchChange);
                }

                SpectatorInput.YawPitch yawPitchDuringAdjustment = this.yawPitchDuringAdjustment;
                this.yawPitchDuringAdjustment = null;
                this.isAdjustingPitch = false;

                // If the adjustment included look yaw/pitch updates, process them now
                // The last look update received will make use of the corrected amounts
                if (yawPitchDuringAdjustment != null) {
                    detectLookChanges(yawPitchDuringAdjustment);
                }
            }
        }

        private void detectLookChanges(SpectatorInput.YawPitch newYawPitch) {
            SpectatorInput.YawPitch lookChange = null;
            Float pitchAdjustment = null;

            synchronized (stateLock) {
                // If presently adjusting pitch, move it aside in the pitch adjustment update, don't apply it right away
                // We don't know yet if this is the proper yaw/pitch after adjustment, as sometimes
                // two or more movement updates occur mid-adjustment.
                if (isAdjustingPitch) {
                    yawPitchDuringAdjustment = newYawPitch;
                    return;
                }

                // Handle changes
                if (lastYawPitch != null) {
                    lookChange = SpectatorInput.YawPitch.subtract(newYawPitch, lastYawPitch);
                }
                lastYawPitch = newYawPitch;

                // If absolute value pitch is too far from 0.0, synchronize an adjustment back to 0.0 with a
                // relative update.
                // Take into account if we've already sent pitch corrections before.
                float pitchErrorFromZero = MathUtil.wrapAngle(-newYawPitch.pitch - inFlightPitchCorrection);
                if (Math.abs(pitchErrorFromZero) > PITCH_ADJ_THRESHOLD) {

                    // Don't send too many per tick, it will cause player disconnects
                    // ViaVersion for example has a limit of 800/s (40/tick)
                    // But realistically we should only send one pitch correction per tick...
                    int currTick = CommonUtil.getServerTicks();
                    if (currTick != inFlightPitchCorrectionsCurrTick) {
                        inFlightPitchCorrectionsCurrTick = currTick;
                        inFlightPitchCorrection += pitchErrorFromZero;
                        pitchAdjustment = pitchErrorFromZero;
                    }
                }
            }

            // Outside lock, notify the pitch updates
            if (lookChange != null) {
                _input.addInputRotation(lookChange);
            }

            // Outside lock, start pitch adjustment cycles
            if (pitchAdjustment != null) {
                final float pitchAdjustmentFinal = pitchAdjustment;
                player.getClientSynchronizer().synchronizeBundle(
                        Collections.singletonList(
                                Util.createRelativeRotationPacket(0.0f, pitchAdjustmentFinal)
                        ),
                        this::ackPitchAdjustStart,
                        () -> ackPitchAdjustDone(pitchAdjustmentFinal));
            }
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            PacketPlayInFlyingHandle p = PacketPlayInFlyingHandle.createHandle(event.getPacket().getHandle());

            // Ensure Y value is corrected
            if (event.getType() != PacketType.IN_LOOK) {
                p.setY(p.getY() - GHOST_Y_OFFSET);
            }

            // Keep track of the changes in player yaw/pitch
            if (event.getType() != PacketType.IN_POSITION) {
                detectLookChanges(new SpectatorInput.YawPitch(p.getYaw(), p.getPitch()));
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
        }
    }
}
