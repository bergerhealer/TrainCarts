package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutUpdateAttributesHandle;

import me.m56738.smoothcoasters.api.RotationMode;

/**
 * Default view mode where the player can freely look around. Views either the entity
 * itself, or a third-person fake camera is used.
 */
public class FirstPersonViewDefault extends FirstPersonView {
    // If used, a mount the player is riding to adjust the player camera/view/position
    private VirtualEntity _fakeCameraMount = null;
    // Remainder yaw and pitch when moving player view orientation along with the seat
    // This remainder is here because Minecraft has only limited yaw/pitch granularity
    private double _playerYawRemainder = 0.0;
    private double _playerPitchRemainder = 0.0;

    public FirstPersonViewDefault(CartAttachmentSeat seat, Player player) {
        super(seat, player);
    }

    /**
     * Whether the player is mounted on a fake entity as part of this first-person view.
     * If this is the case, then the player shouldn't be mounted to the actual vehicle.
     *
     * @return True if a fake mount is used in this first-person view
     */
    public boolean isFakeCameraUsed() {
        // If an eye position is set, then we must use a fake camera for the player to make this work
        if (!this._eyePosition.isDefault()) {
            return true;
        }

        // Must always mount to a separate vehicle with smooth coasters
        if (seat.useSmoothCoasters()) {
            return true;
        }

        // Based on the first person view mode
        switch (this.getLiveMode()) {
        case THIRD_P:
            return true;
        default:
            // Only do it when the seat (butt) to camera (eye) offset is exactly vanilla
            // If not, we must use a fake mount to position it properly
            return this.seat.seated.getFirstPersonCameraOffset().getY() != VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT;
        }
    }

    @Override
    public void makeVisible(Player viewer, boolean isReload) {
        VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
        boolean useFakeCamera = this.isFakeCameraUsed();
        if (useFakeCamera || seat.useSmoothCoasters() || seat.isRotationLocked()) {
            // In these three cases the eye transform is used for some logic here
            Matrix4x4 eyeTransform = getEyeTransform();

            if (!isReload && this.seat.useSmoothCoasters()) {
                this.seat.getPlugin().getSmoothCoastersAPI().setRotationMode(
                        null,
                        viewer,
                        RotationMode.CAMERA // TCConfig.smoothCoastersRotationMode
                );
                this.syncSmoothCoastersRotations(eyeTransform, true);
            }

            if (isReload) {
                // Don't do any bizar logic here...
            } else if (this.seat.useSmoothCoasters()) {
                if (seat.isRotationLocked()) {
                    // Body is locked, make the player face forwards according to the eye transform
                    // As smoothcoasters uses a quaternion base, this is simply 0/0
                    PacketPlayOutPositionHandle p = PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, 0.0f, 0.0f);
                    p.setRotationRelative(false);
                    PacketUtil.sendPacket(viewer, p);
                } else {
                    // Send current player absolute rotation. SmoothCoasters has a bug in it
                    // that the player looks into a random direction otherwise. This rotation must be
                    // adjusted for the base quaternion orientation that's already applied.

                    // Absolute orientation of player
                    Quaternion lookOrientation;
                    {
                        Location loc = viewer.getEyeLocation();
                        lookOrientation = Quaternion.fromYawPitchRoll(loc.getPitch(), loc.getYaw(), 0.0);
                    }

                    // Subtract the seat orientation we're sending
                    Quaternion result = Quaternion.diff(eyeTransform.getRotation(), lookOrientation);

                    // To head yaw/pitch - ensure always level as the client cannot comprehend extreme pitch
                    HeadRotation rot = HeadRotation.compute(result).ensureLevel();

                    // Send it
                    PacketPlayOutPositionHandle p = PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, rot.yaw, rot.pitch);
                    p.setRotationRelative(false);
                    PacketUtil.sendPacket(viewer, p);
                }
            } else if (seat.isRotationLocked()) {
                // Body is locked, make the player face forwards according to the eye transform
                HeadRotation rot = HeadRotation.compute(eyeTransform).ensureLevel();
                PacketPlayOutPositionHandle p = PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, rot.yaw, rot.pitch);
                p.setRotationRelative(false);
                PacketUtil.sendPacket(viewer, p);
            }

            if (useFakeCamera) {
                if (this._fakeCameraMount == null) {
                    this._fakeCameraMount = this.seat.seated.createPassengerVehicle();
                    this._fakeCameraMount.addRelativeOffset(0.0, -VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT, 0.0);
                    this._fakeCameraMount.updatePosition(eyeTransform);
                    this._fakeCameraMount.syncPosition(true);
                    this._fakeCameraMount.spawn(viewer, seat.calcMotion());

                    // Also send zero-max-health
                    PacketUtil.sendPacket(viewer, PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(this._fakeCameraMount.getEntityId()));
                }

                vmc.mount(this._fakeCameraMount.getEntityId(), viewer.getEntityId());
            }
        }

        // If mode is INVISIBLE or HEAD, then also make the player itself invisible using a metadata update
        // This also removes all player equipment so they aren't displayed hovering in the air
        if (this.getLiveMode().isRealPlayerInvisible()) {
            setPlayerVisible(viewer, false);
        }

        // If mode is HEAD, then assign a player head skull as equipment
        if (this.getLiveMode() == FirstPersonViewMode.HEAD) {
            sendEquipment(viewer, EquipmentSlot.HEAD, SeatedEntityHead.createSkullItem(viewer));
        }

        if (!useFakeCamera) {
            // If no fake camera mount is used, make sure to mount the player in the vehicle mount
            vmc.mount(seat.seated.spawnVehicleMount(viewer), viewer.getEntityId());
        } else if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            // Also spawn the seated entity
            seat.seated.makeVisibleFirstPerson(viewer);
        }
    }

    @Override
    public void makeHidden(Player viewer, boolean isReload) {
        VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);

        // Reset everything smooth coasters has changed when the player exits the seat
        if (!isReload && seat.useSmoothCoasters()) {
            seat.getPlugin().getSmoothCoastersAPI().setEntityRotation(null, viewer, viewer.getEntityId(),
                    0.0f, 0.0f, 0.0f, 1.0f, (byte) 0);
            seat.getPlugin().getSmoothCoastersAPI().resetRotation(null, viewer);
            seat.getPlugin().getSmoothCoastersAPI().setRotationMode(null, viewer, RotationMode.NONE);

            // Make the player look where the player was looking before
            {
                Quaternion headRotQuat = seat.seated.getCurrentHeadRotationQuat(seat.getTransform());
                HeadRotation headRot = HeadRotation.compute(headRotQuat).ensureLevel();
                PacketPlayOutPositionHandle p = PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, headRot.yaw, headRot.pitch);
                p.setRotationRelative(false);
                PacketUtil.sendPacket(viewer, p);
            }
        }

        if (this._fakeCameraMount != null) {
            vmc.unmount(this._fakeCameraMount.getEntityId(), viewer.getEntityId());
            this._fakeCameraMount.destroy(viewer);
            this._fakeCameraMount = null;
        }

        if (!this.isFakeCameraUsed()) {
            // Unmount the viewer from the seat
            vmc.unmount(seat.seated.parentMountId, viewer.getEntityId());
        } else if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            // Despawn a fake seated entity
            seat.seated.makeHiddenFirstPerson(viewer);
        }

        // Make the player visible again if the mode made it invisible
        if (this.getLiveMode().isRealPlayerInvisible()) {
            setPlayerVisible(viewer, true);
        }
    }

    @Override
    public void onTick() {
        // Move player view relatively
        if (this.getLockMode() == FirstPersonViewLockMode.MOVE && this.seat.seated.isPlayer() && !this.seat.useSmoothCoasters()) {
            // Every now and then, rotate the player view by the amount the seat itself rotated
            Vector player_pyr;
            {
                Location eye_loc = ((Player) this.seat.seated.getEntity()).getEyeLocation();
                player_pyr = new Vector(eye_loc.getPitch(),
                                     eye_loc.getYaw(),
                                     0.0);
                player_pyr.setX(-player_pyr.getX());
            }

            // Find the rotation transformation to go from the previous transformation to pyr
            // Multiplying the previous transform with this rotation should result in player_pyr exactly
            Quaternion diff = Quaternion.diff(this.seat.getPreviousTransform().getRotation(), Quaternion.fromYawPitchRoll(player_pyr));

            // Calculate what player pyr would be with the rotation changes that have since occurred
            Quaternion new_rotation = this.seat.getTransform().getRotation();
            new_rotation.multiply(diff);

            // Compute difference, also include a remainder we haven't synchronized yet
            Vector new_pyr = new_rotation.getYawPitchRoll();
            Vector pyr = new_pyr.clone().subtract(player_pyr);
            pyr.setX(pyr.getX() + this._playerPitchRemainder);
            pyr.setY(pyr.getY() + this._playerYawRemainder);

            // Refresh this change in pitch/yaw/roll to the player
            if (Math.abs(pyr.getX()) > 1e-5 || Math.abs(pyr.getY()) > 1e-5) {
                PacketPlayOutPositionHandle p = PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, (float) pyr.getY(), (float) pyr.getX());
                this._playerPitchRemainder = (pyr.getX() - p.getPitch());
                this._playerYawRemainder = (pyr.getY() - p.getYaw());
                PacketUtil.sendPacket((Player) this.seat.seated.getEntity(), p);
            } else {
                this._playerPitchRemainder = pyr.getX();
                this._playerYawRemainder = pyr.getY();
            }
        }

        // If body rotation is locked, restrict rotation within a yaw diff of 70 degrees
        // TODO: I disabled this stuff because it's way too jittery and annoying
        /*
        if (player != null && seat.isRotationLocked()) {
            if (seat.useSmoothCoasters()) {
                // Easy, keep yaw between the limits. The yaw is forwards when 0.
                float currYaw = MathUtil.wrapAngle(player.getEyeLocation().getYaw());
                float corr;
                if (currYaw > BODY_LOCK_FOV_LIMIT) {
                    corr = BODY_LOCK_FOV_LIMIT - currYaw;
                } else if (currYaw < -BODY_LOCK_FOV_LIMIT) {
                    corr = -BODY_LOCK_FOV_LIMIT - currYaw;
                } else {
                    corr = 0.0f;
                }
                if (corr != 0.0f) {
                    // When the correction is less than 15 degrees, perform a bouncy rejection
                    // Anything beyond that is rejected hard.
                    final float HARD_LIMIT = 15.0f;
                    final float SMOOTH_FACTOR = 0.3f;
                    if (corr > HARD_LIMIT) {
                        corr = (corr - HARD_LIMIT) + HARD_LIMIT * SMOOTH_FACTOR;
                    } else if (corr < -HARD_LIMIT) {
                        corr = (corr + HARD_LIMIT) - HARD_LIMIT * SMOOTH_FACTOR;
                    } else {
                        corr *= SMOOTH_FACTOR;
                    }

                    PacketUtil.sendPacket(player, PacketPlayOutPositionHandle.createRelative(0.0, 0.0, 0.0, corr, 0.0f));
                }
            } else {
                // Harder, compute the yaw the player can have and restrict that
            }
        }
        */
    }

    @Override
    public void onMove(boolean absolute) {
        // In these two cases we need to do some work here. Otherwise, the player
        // is mounted in a vehicle managed elsewhere, and there's nothing to do
        if (this._fakeCameraMount != null || this.seat.useSmoothCoasters()) {
            Matrix4x4 eyeTransform = getEyeTransform();

            if (this.seat.useSmoothCoasters()) {
                this.syncSmoothCoastersRotations(eyeTransform, false);
            }

            if (this._fakeCameraMount != null) {
                this._fakeCameraMount.updatePosition(eyeTransform);
                this._fakeCameraMount.syncPosition(absolute);
            }
        }
    }

    private void syncSmoothCoastersRotations(Matrix4x4 eyeTransform, boolean instant) {
        // This rotates the head view
        this.seat.sendSmoothCoastersRelativeRotation(eyeTransform.getRotation(), instant);

        // This rotates the body and not the camera
        // Not used when the true player is made invisible - waste of packets
        if (!this.getLiveMode().isRealPlayerInvisible()) {
            Quaternion bodyRot = seat.getTransform().getRotation();
            seat.getPlugin().getSmoothCoastersAPI().setEntityRotation(null, player, player.getEntityId(),
                    (float) bodyRot.getX(),
                    (float) bodyRot.getY(),
                    (float) bodyRot.getZ(),
                    (float) bodyRot.getW(),
                    instant ? (byte) 0 : (seat.isMinecartInterpolation() ? (byte) 5 : (byte) 3));
        }
    }
}
