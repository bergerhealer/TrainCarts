package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;

/**
 * Information for a seated entity that is left standing/unseated. The entity
 * is moved around and will look as if walking through the air.
 */
class SeatedEntityStanding extends SeatedEntityNormal {

    public SeatedEntityStanding(CartAttachmentSeat seat) {
        super(seat);
        this._fake = true;
    }

    @Override
    public Vector getThirdPersonCameraOffset() {
        return new Vector(0.0, 2.2, 0.0);
    }

    @Override
    public Vector getFirstPersonCameraOffset() {
        return new Vector(0.0, VirtualEntity.PLAYER_STANDING_EYE_HEIGHT, 0.0);
    }

    @Override
    public boolean isFirstPersonCameraFake() {
        return false;
    }

    @Override
    protected boolean detectFake(boolean new_isUpsideDown, FirstPersonViewMode new_firstPersonMode) {
        return true;
    }

    private void makeFakePlayerVisible(AttachmentViewer viewer) {
        // Generate an entity id if needed for the first time
        if (this._fakeEntityId == -1) {
            this._fakeEntityId = EntityUtil.getUniqueEntityId();
        }

        // Position of the fake player
        Vector fpp_pos = seat.getTransform().toVector();
        FakePlayerSpawner.FakePlayerPosition fpp = FakePlayerSpawner.FakePlayerPosition.create(
                fpp_pos.getX(), fpp_pos.getY(), fpp_pos.getZ(),
                this.orientation.getPassengerYaw(),
                this.orientation.getPassengerPitch(),
                this.orientation.getPassengerHeadYaw());

        if (this._upsideDown) {
            // Spawn player as upside-down. For dummy players, entity is null, so spawns a dummy.
            FakePlayerSpawner.UPSIDEDOWN.spawnPlayer(viewer, (Player) this.entity, this._fakeEntityId, fpp, this::applyFakePlayerMetadata);
        } else {
            // Spawn a normal no-nametag player. For dummy players, entity is null, so spawns a dummy.
            FakePlayerSpawner.NO_NAMETAG.spawnPlayer(viewer, (Player) this.entity, this._fakeEntityId, fpp, this::applyFakePlayerMetadata);
        }
    }

    private void makeFakePlayerInvisible(AttachmentViewer viewer) {
        // De-spawn the fake player itself
        VehicleMountController vmc = viewer.getVehicleMountController();
        viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(this._fakeEntityId));
        vmc.remove(this._fakeEntityId);
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        if (isDummyPlayer() && isEmpty()) {
            // For dummy players, spawn a fake version of the Player and seat it
            // The original player is also displayed, so that might be weird. Oh well.
            makeFakePlayerVisible(viewer);
            return;
        }

        if (isPlayer()) {
            if (this.entity != viewer.getPlayer()) {
                hideRealPlayer(viewer);
            }
            makeFakePlayerVisible(viewer);
            return;
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        if (isDummyPlayer() && isEmpty()) {
            // Hide fake player again
            makeFakePlayerInvisible(viewer);
            return;
        }

        if (isPlayer()) {
            makeFakePlayerInvisible(viewer);
            if (this.entity != viewer.getPlayer()) {
                showRealPlayer(viewer);
            }
            return;
        }
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        // Player head orientation
        super.updatePosition(transform);

        Vector pos = transform.toVector();
        if (this.isUpsideDown()) {
            pos.setY(pos.getY() - 1.95);
        }
        for (AttachmentViewer viewer : seat.getAttachmentViewers()) {
            if (viewer.getPlayer() == entity && !seat.firstPerson.getLiveMode().hasFakePlayer()) {
                continue;
            }

            PacketPlayOutEntityTeleportHandle p = PacketPlayOutEntityTeleportHandle.createNew(this._fakeEntityId,
                    pos.getX(), pos.getY(), pos.getZ(),
                    orientation.getPassengerYaw(), orientation.getPassengerPitch(), false);
            viewer.send(p);
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
    }

    @Override
    public void updateFocus(boolean focused) {
    }
}
