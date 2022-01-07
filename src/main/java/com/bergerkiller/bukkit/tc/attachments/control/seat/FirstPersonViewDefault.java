package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutUpdateAttributesHandle;
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

    public FirstPersonViewDefault(CartAttachmentSeat seat) {
        super(seat);
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

        // Based on the first person view mode
        switch (this.getLiveMode()) {
        case SMOOTHCOASTERS_FIX:
        case THIRD_P:
            return true;
        case DEFAULT:
            // The elytra has a 'weird' mount position to make it work in third-person
            // This causes the default camera, mounted for the same entity, to no longer work
            // To fix this, make use of the virtual camera mount
            if (this.seat.seated instanceof SeatedEntityElytra) {
                return true;
            }
            return false;
        default:
            return false;
        }
    }

    @Override
    public void makeVisible(Player viewer) {
        VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
        boolean useFakeCamera = this.isFakeCameraUsed();
        if (useFakeCamera || this.useSmoothCoasters()) {
            // In these two cases special initialization needs to be done
            Matrix4x4 eyeTransform = getEyeTransform();

            if (this.useSmoothCoasters()) {
                Quaternion rotation = eyeTransform.getRotation();
                TrainCarts.plugin.getSmoothCoastersAPI().setRotationMode(
                        null,
                        viewer,
                        TCConfig.smoothCoastersRotationMode
                );
                TrainCarts.plugin.getSmoothCoastersAPI().setRotation(
                        null,
                        viewer,
                        (float) rotation.getX(),
                        (float) rotation.getY(),
                        (float) rotation.getZ(),
                        (float) rotation.getW(),
                        (byte) 0 // Set instantly
                );
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

        if (!useFakeCamera) {
            // If mode is INVISIBLE, then also make the player itself invisible using a metadata update
            if (this.getLiveMode() == FirstPersonViewMode.INVISIBLE) {
                setPlayerVisible(viewer, false);
            }

            // If no fake camera mount is used, make sure to mount the player in the vehicle mount
            vmc.mount(seat.seated.spawnVehicleMount(viewer), viewer.getEntityId());
        } else if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            // Hide the actual player to himself
            setPlayerVisible(viewer, false);

            // Also spawn the seated entity
            seat.seated.makeVisibleFirstPerson(viewer);
        }
    }

    @Override
    public void makeHidden(Player viewer) {
        VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);

        if (TrainCarts.plugin.isEnabled()) {
            // Cannot send plugin messages while the plugin is being disabled
            TrainCarts.plugin.getSmoothCoastersAPI().resetRotation(null, viewer);
        }

        if (this._fakeCameraMount != null) {
            vmc.unmount(this._fakeCameraMount.getEntityId(), viewer.getEntityId());
            this._fakeCameraMount.destroy(viewer);
            this._fakeCameraMount = null;
        }

        if (!this.isFakeCameraUsed()) {
            // Unmount the viewer from the seat
            vmc.unmount(seat.seated.parentMountId, viewer.getEntityId());

            // If mode is not INVISIBLE, then also make the player itself invisible using a metadata update
            if (this.getLiveMode() == FirstPersonViewMode.INVISIBLE) {
                setPlayerVisible(viewer, true);
            }
        } else if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            // Despawn a fake seated entity
            seat.seated.makeHiddenFirstPerson(viewer);

            // Make real player visible again
            setPlayerVisible(viewer, true);
        }
    }

    @Override
    public void onTick() {
        // Move player view relatively
        if (this.getLockMode() == FirstPersonViewLockMode.MOVE && this.seat.seated.isPlayer() && !this.useSmoothCoasters()) {
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
    }

    @Override
    public void onMove(boolean absolute) {
        // In these two cases we need to do some work here. Otherwise, the player
        // is mounted in a vehicle managed elsewhere, and there's nothing to do
        if (this._fakeCameraMount != null || this.useSmoothCoasters()) {
            Matrix4x4 eyeTransform = getEyeTransform();

            if (this.useSmoothCoasters()) {
                Quaternion rotation = eyeTransform.getRotation();
                TrainCarts.plugin.getSmoothCoastersAPI().setRotation(
                        null,
                        player,
                        (float) rotation.getX(),
                        (float) rotation.getY(),
                        (float) rotation.getZ(),
                        (float) rotation.getW(),
                        (byte) 3 // TODO 5 for minecarts
                );
            }
            if (this._fakeCameraMount != null) {
                this._fakeCameraMount.updatePosition(eyeTransform);
                this._fakeCameraMount.syncPosition(absolute);
            }
        }
    }
}
