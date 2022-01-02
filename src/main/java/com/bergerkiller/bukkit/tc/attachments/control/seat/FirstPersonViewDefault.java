package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
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
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutUpdateAttributesHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Default view mode where the player can freely look around. Views either the entity
 * itself, or a third-person fake camera is used.
 */
public class FirstPersonViewDefault extends FirstPersonView {
    // Player that's used
    private Player _player;
    // If used, a mount the player is riding to adjust the player camera/view/position
    private VirtualEntity _fakeCameraMount = null;
    // Remainder yaw and pitch when moving player view orientation along with the seat
    // This remainder is here because Minecraft has only limited yaw/pitch granularity
    private double _playerYawRemainder = 0.0;
    private double _playerPitchRemainder = 0.0;

    public FirstPersonViewDefault(CartAttachmentSeat seat) {
        super(seat);
    }

    @Override
    public void makeVisible(Player viewer) {
        _player = viewer;

        boolean useFakeCamera = this.isFakeCameraUsed();
        if (useFakeCamera || this.useSmoothCoasters()) {
            // In these two cases special initialization needs to be done
            Matrix4x4 baseTransform = getBaseTransform();

            if (this.useSmoothCoasters()) {
                Quaternion rotation = baseTransform.getRotation();
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
                    this._fakeCameraMount = new VirtualEntity(seat.getManager());
                    this._fakeCameraMount.setEntityType(EntityType.ARMOR_STAND);
                    this._fakeCameraMount.setSyncMode(SyncMode.SEAT);

                    if (this._eyePosition.isDefault()) {
                        // Compute automatically using the view modes used
                        double y_offset = -VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET;
                        if (this.getLiveMode().isVirtual()) {
                            y_offset -= VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT;
                            this._fakeCameraMount.setPosition(new Vector(0.0, this.getLiveMode().getVirtualOffset(), 0.0));
                        }
                        this._fakeCameraMount.setRelativeOffset(0.0, y_offset, 0.0);
                    } else {
                        // Position exactly at the seat transform x the eye position
                        // Add a relative offset so that this position is where the eyes are
                        double y_offset = VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET + VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT;
                        this._fakeCameraMount.setRelativeOffset(0.0, -y_offset, 0.0);
                    }

                    // When synchronizing passenger to himself, we put him on a fake mount to alter where the camera is at
                    this._fakeCameraMount.updatePosition(baseTransform);
                    this._fakeCameraMount.syncPosition(true);
                    this._fakeCameraMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                    this._fakeCameraMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
                    this._fakeCameraMount.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                            EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                            EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                            EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
                    this._fakeCameraMount.spawn(viewer, seat.calcMotion());
                    this._fakeCameraMount.syncPosition(true);

                    // Also send zero-max-health
                    PacketUtil.sendPacket(viewer, PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(this._fakeCameraMount.getEntityId()));
                }

                VehicleMountController vmc = PlayerUtil.getVehicleMountController(viewer);
                vmc.mount(this._fakeCameraMount.getEntityId(), viewer.getEntityId());
            }
        }

        seat.seated.makeVisible(viewer, this.getLiveMode().hasFakePlayer());
    }

    @Override
    public void makeHidden(Player viewer) {
        if (TrainCarts.plugin.isEnabled()) {
            // Cannot send plugin messages while the plugin is being disabled
            TrainCarts.plugin.getSmoothCoastersAPI().resetRotation(null, viewer);
        }

        if (this._fakeCameraMount != null) {
            PlayerUtil.getVehicleMountController(viewer).unmount(this._fakeCameraMount.getEntityId(), viewer.getEntityId());
            this._fakeCameraMount.destroy(viewer);
            this._fakeCameraMount = null;
        }

        seat.seated.makeHidden(viewer, this.getLiveMode().hasFakePlayer());

        _player = null;
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
            Matrix4x4 baseTransform = getBaseTransform();

            if (this.useSmoothCoasters()) {
                Quaternion rotation = baseTransform.getRotation();
                TrainCarts.plugin.getSmoothCoastersAPI().setRotation(
                        null,
                        _player,
                        (float) rotation.getX(),
                        (float) rotation.getY(),
                        (float) rotation.getZ(),
                        (float) rotation.getW(),
                        (byte) 3 // TODO 5 for minecarts
                );
            }
            if (this._fakeCameraMount != null) {
                this._fakeCameraMount.updatePosition(baseTransform);
                this._fakeCameraMount.syncPosition(absolute);
            }
        }
    }
}
