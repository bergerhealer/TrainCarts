package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutUpdateAttributesHandle;

/**
 * Synchronizes the seat to the player sitting in the seat
 */
public class FirstPersonDefault {
    private final CartAttachmentSeat seat;
    private Player _player;
    private boolean _useSmoothCoasters = false;
    private boolean _useVirtualCamera = false;
    private VirtualEntity _fakeCameraMount = null;

    public FirstPersonDefault(CartAttachmentSeat seat) {
        this.seat = seat;
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

        if (this.useVirtualCamera()) {
            if (this._fakeCameraMount == null) {
                this._fakeCameraMount = new VirtualEntity(seat.getManager());

                this._fakeCameraMount.setEntityType(EntityType.CHICKEN);
                this._fakeCameraMount.setPosition(new Vector(0.0, 1.4, 0.0));
                this._fakeCameraMount.setRelativeOffset(0.0, VirtualEntity.PLAYER_SIT_CHICKEN_OFFSET, 0.0);
                this._fakeCameraMount.setSyncMode(SyncMode.SEAT);

                // When synchronizing passenger to himself, we put him on a fake mount to alter where the camera is at
                this._fakeCameraMount.updatePosition(seat.getTransform());
                this._fakeCameraMount.syncPosition(true);
                this._fakeCameraMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
                this._fakeCameraMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
                this._fakeCameraMount.spawn(viewer, seat.calcMotion());
                this._fakeCameraMount.syncPosition(true);

                // Also send zero-max-health
                PacketUtil.sendPacket(viewer, PacketPlayOutUpdateAttributesHandle.createZeroMaxHealth(this._fakeCameraMount.getEntityId()));
            }

            PlayerUtil.getVehicleMountController(viewer).mount(this._fakeCameraMount.getEntityId(), viewer.getEntityId());
        }

        seat.seated.makeVisible(seat, viewer, this.isFakePlayerUsed());
    }

    public void makeHidden(Player viewer) {
        if (TrainCarts.plugin.isEnabled()) {
            // Cannot send plugin messages while the plugin is being disabled
            TrainCarts.plugin.getSmoothCoastersAPI().resetRotation(viewer);
        }

        if (this.useVirtualCamera() && this._fakeCameraMount != null) {
            PlayerUtil.getVehicleMountController(viewer).unmount(this._fakeCameraMount.getEntityId(), viewer.getEntityId());
            this._fakeCameraMount.destroy(viewer);
            this._fakeCameraMount = null;
        }

        seat.seated.makeHidden(viewer, this.isFakePlayerUsed());
    }

    public boolean isFakePlayerUsed() {
        return this.useVirtualCamera();
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

        if (this._fakeCameraMount != null && this.useVirtualCamera()) {
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
     * Gets whether the viewer uses a virtual camera instead of the view from the Player entity itself
     * Only has effect if the entity is a Player.
     * When upside-down, a virtual camera is automatically used.
     * 
     * @return true if a virtual camera is used
     */
    public boolean useVirtualCamera() {
        return this._useVirtualCamera;
    }

    /**
     * Sets whether the viewer uses a virtual camera instead of the view from the Player entity itself.
     * Only has effect if the entity is a Player.
     * When upside-down, a virtual camera is automatically used.
     * 
     * @param use option
     */
    public void setUseVirtualCamera(boolean use) {
        this._useVirtualCamera = use;
    }
}
