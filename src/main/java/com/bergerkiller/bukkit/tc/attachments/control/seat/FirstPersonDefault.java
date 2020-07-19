package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutUpdateAttributesHandle;

/**
 * Synchronizes the seat to the player sitting in the seat
 */
public class FirstPersonDefault {
    private final CartAttachmentSeat seat;
    private Player _player;
    private FirstPersonViewMode _liveMode = FirstPersonViewMode.DEFAULT;
    private FirstPersonViewMode _mode = FirstPersonViewMode.DYNAMIC;
    private boolean _useSmoothCoasters = false;
    private VirtualEntity _fakeCameraMount = null;

    public FirstPersonDefault(CartAttachmentSeat seat) {
        this.seat = seat;
    }

    public boolean isFakeCameraUsed() {
        if (this._liveMode.isVirtual()) {
            return true;
        }

        // The elytra has a 'weird' mount position to make it work in third-person
        // This causes the default camera, mounted for the same entity, to no longer work
        // To fix this, make use of the virtual camera mount
        if (this._liveMode == FirstPersonViewMode.DEFAULT && this.seat.seated instanceof SeatedEntityElytra) {
            return true;
        }

        return false;
    }

    public void makeVisible(Player viewer) {
        _player = viewer;

        if (this.useSmoothCoasters()) {
            Quaternion rotation = seat.getTransform().getRotation();
            TrainCarts.plugin.getSmoothCoastersAPI().setRotation(
                    viewer,
                    (float) rotation.getX(),
                    (float) rotation.getY(),
                    (float) rotation.getZ(),
                    (float) rotation.getW(),
                    (byte) 0 // Set instantly
            );
        }

        if (isFakeCameraUsed()) {
            if (this._fakeCameraMount == null) {
                this._fakeCameraMount = new VirtualEntity(seat.getManager());
                this._fakeCameraMount.setEntityType(EntityType.ARMOR_STAND);
                this._fakeCameraMount.setSyncMode(SyncMode.SEAT);

                double y_offset = VirtualEntity.PLAYER_SIT_ARMORSTAND_BUTT_OFFSET;
                if (this._liveMode.isVirtual()) {
                    y_offset -= VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT;
                    this._fakeCameraMount.setPosition(new Vector(0.0, this._liveMode.getVirtualOffset(), 0.0));
                }
                this._fakeCameraMount.setRelativeOffset(0.0, y_offset, 0.0);

                // When synchronizing passenger to himself, we put him on a fake mount to alter where the camera is at
                this._fakeCameraMount.updatePosition(seat.getTransform());
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

        seat.seated.makeVisible(viewer, this._liveMode.hasFakePlayer());
    }

    public void makeHidden(Player viewer) {
        if (TrainCarts.plugin.isEnabled()) {
            // Cannot send plugin messages while the plugin is being disabled
            TrainCarts.plugin.getSmoothCoastersAPI().resetRotation(viewer);
        }

        if (this._fakeCameraMount != null) {
            PlayerUtil.getVehicleMountController(viewer).unmount(this._fakeCameraMount.getEntityId(), viewer.getEntityId());
            this._fakeCameraMount.destroy(viewer);
            this._fakeCameraMount = null;
        }

        seat.seated.makeHidden(viewer, this._liveMode.hasFakePlayer());
    }

    public void onMove(boolean absolute) {
        if (this.useSmoothCoasters()) {
            Quaternion rotation = seat.getTransform().getRotation();
            TrainCarts.plugin.getSmoothCoastersAPI().setRotation(
                    _player,
                    (float) rotation.getX(),
                    (float) rotation.getY(),
                    (float) rotation.getZ(),
                    (float) rotation.getW(),
                    (byte) 3 // TODO 5 for minecarts
            );
        }

        if (this._fakeCameraMount != null) {
            this._fakeCameraMount.updatePosition(seat.getTransform());
            this._fakeCameraMount.syncPosition(absolute);
        }
    }

    public boolean useSmoothCoasters() {
        return _useSmoothCoasters;
    }

    public void setUseSmoothCoasters(boolean use) {
        this._useSmoothCoasters = use;
    }

    /**
     * Gets the view mode used in first-person currently. This mode alters how the player perceives
     * himself in the seat. This one differs from the {@link #getMode()} if DYNAMIC is used.
     * 
     * @return live mode
     */
    public FirstPersonViewMode getLiveMode() {
        return this._liveMode;
    }

    /**
     * Sets the view mode currently used in first-person. See: {@link #getLiveMode()}
     * 
     * @param liveMode
     */
    public void setLiveMode(FirstPersonViewMode liveMode) {
        this._liveMode = liveMode;
    }

    /**
     * Gets the view mode that should be used. This is set using seat configuration, and
     * alters what mode is picked for {@link #getLiveMode()}. If set to DYNAMIC, the DYNAMIC
     * view mode conditions are checked to switch to the appropriate mode.
     * 
     * @return mode
     */
    public FirstPersonViewMode getMode() {
        return this._mode;
    }

    /**
     * Sets the view mode that should be used. Is configuration, the live view mode
     * is updated elsewhere. See: {@link #getMode()}.
     * 
     * @param mode
     */
    public void setMode(FirstPersonViewMode mode) {
        this._mode = mode;
    }
}
