package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatedEntity.DisplayMode;
import com.bergerkiller.bukkit.tc.controller.player.SpectatedFakePlayer;
import com.bergerkiller.bukkit.tc.controller.player.SpectatedFakePlayerHead;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundSetPassengersPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacketHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.LivingEntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.ArmorStandHandle;

/**
 * Spawns a duplicate of the player and spectates that player. The player is seated into
 * the seat like any other player is as viewed in third-person.
 */
class FirstPersonSpectatedEntityPlayerSitting extends FirstPersonSpectatedEntity {
    private static final VirtualEntity[] NO_FAKE_MOUNTS = new VirtualEntity[0];

    // A fake invisible mount that positions the player correctly and moves it around
    // One mount on newer versions of Minecraft that supports multiple passengers per mount
    // Two mounts for older versions.
    // 0 mounts if mounted directly in the parent vehicle.
    private VirtualEntity[] fakeMounts = NO_FAKE_MOUNTS;
    private final SpectatedFakePlayer fakePlayer;
    private final boolean isHeadMode;

    public FirstPersonSpectatedEntityPlayerSitting(CartAttachmentSeat seat, FirstPersonViewSpectator view, AttachmentViewer player) {
        super(seat, view, player);
        this.isHeadMode = (view.getLiveMode() == FirstPersonViewMode.HEAD);
        if (isHeadMode) {
            SpectatedFakePlayerHead headPlayer = new SpectatedFakePlayerHead(player, seat.getManager());
            this.fakePlayer = headPlayer;
        } else {
            this.fakePlayer = new SpectatedFakePlayer(player, seat.getManager());
        }
        this.fakePlayer.setUseMinecartInterpolation(seat.isMinecartInterpolation());
    }

    private Matrix4x4 getSyncTransform(Matrix4x4 eyeTransform) {
        // Keep sitting spectator sync in eye space for both default and head mode.
        // The fake mount applies the seated vertical offsets already.
        return eyeTransform;
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        Matrix4x4 syncTransform = getSyncTransform(eyeTransform);
        fakePlayer.start(syncTransform, seat.calcMotion());

        // Spawn an invisible holder entity inside which the fake player sits
        // Or, depending on configuration, just mount it in the vehicle directly
        // Or, on older versions of Minecraft, where multiple passengers per mount doesn't work
        if (!seat.firstPerson.getEyePosition().isDefault() ||
            seat.seated.getDisplayMode() == DisplayMode.HEAD ||
            seat.seated.getDisplayMode() == DisplayMode.INVISIBLE ||
            !ClientboundSetPassengersPacketHandle.T.isAvailable()
        ) {
            // Player must be put into the seat so the eye position is at the baseTransform
            prepareFakeMounts(syncTransform);
        } else {
            // Player is put into a vehicle, we don't really care
            mountInVehicle();
        }

    }

    private void mountInVehicle() {
        int vehicleId = view.prepareVehicleEntityId();
        this.fakePlayer.mountSingle(vehicleId);
    }

    private void prepareFakeMounts(Matrix4x4 baseTransform) {
        VehicleMountController vmc = player.getVehicleMountController();
        if (ClientboundSetPassengersPacketHandle.T.isAvailable()) {
            // Only one fake mount needed
            VirtualEntity fakeMount = createFakeMount(baseTransform);

            // Mount both players inside
            this.fakePlayer.mountSingle(fakeMount.getEntityId());

            this.fakeMounts = new VirtualEntity[] { fakeMount };
        } else {
            // Spawn two mounts, put players in each
            VirtualEntity[] fakeMounts = new VirtualEntity[] {
                    createFakeMount(baseTransform),
                    createFakeMount(baseTransform),
                    createFakeMount(baseTransform)
            };

            // Mount both players inside
            this.fakePlayer.mountMultiple(fakeMounts[0].getEntityId(),
                    fakeMounts[1].getEntityId(),
                    fakeMounts[2].getEntityId());

            this.fakeMounts = fakeMounts;
        }
    }

    private VirtualEntity createFakeMount(Matrix4x4 baseTransform) {
        VirtualEntity fakeMount = new VirtualEntity(seat.getManager());
        fakeMount.setEntityType(EntityType.ARMOR_STAND);
        fakeMount.setSyncMode(SyncMode.SEAT);
        fakeMount.setUseMinecartInterpolation(seat.isMinecartInterpolation());

        // Put the entity on a fake mount that we move around at an offset
        fakeMount.setByViewerPositionAdjustment((viewer, pos) -> {
            pos.setY(pos.getY() - viewer.getArmorStandButtOffset() - VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT);
        });
        fakeMount.updatePosition(baseTransform);
        fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
        fakeMount.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        fakeMount.getMetaData().set(LivingEntityHandle.DATA_HEALTH, 10.0F);
        fakeMount.getMetaData().set(ArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                ArmorStandHandle.DATA_FLAG_SET_MARKER |
                ArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                ArmorStandHandle.DATA_FLAG_IS_SMALL));
        fakeMount.syncPosition(true);
        fakeMount.spawn(player, seat.calcMotion());

        // Hide health bar
        player.send(ClientboundUpdateAttributesPacketHandle.createZeroMaxHealth(fakeMount.getEntityId()));

        return fakeMount;
    }

    @Override
    public void stop() {
        this.fakePlayer.unmount();

        for (VirtualEntity fakeMount : fakeMounts) {
            fakeMount.destroy(player);
        }
        fakeMounts = NO_FAKE_MOUNTS;

        this.fakePlayer.stop();
    }

    @Override
    public void updatePosition(Matrix4x4 eyeTransform) {
        Matrix4x4 syncTransform = getSyncTransform(eyeTransform);
        this.fakePlayer.updatePosition(syncTransform);

        // Move the vehicle itself, which moves the fake player around
        for (VirtualEntity fakeMount : fakeMounts) {
            fakeMount.updatePosition(syncTransform);
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        for (VirtualEntity fakeMount : fakeMounts) {
            fakeMount.syncPosition(absolute);
        }

        this.fakePlayer.syncPosition(absolute);
    }

    @Override
    public VirtualEntity getCurrentEntity() {
        return fakePlayer.getCurrentEntity();
    }
}
