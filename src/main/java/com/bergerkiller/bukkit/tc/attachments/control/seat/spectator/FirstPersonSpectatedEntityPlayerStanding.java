package com.bergerkiller.bukkit.tc.attachments.control.seat.spectator;

import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity.SyncMode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewMode;
import com.bergerkiller.bukkit.tc.attachments.control.seat.FirstPersonViewSpectator;
import com.bergerkiller.bukkit.tc.controller.player.SpectatedFakePlayer;
import com.bergerkiller.bukkit.tc.controller.player.SpectatedFakePlayerHead;

/**
 * Spawns a duplicate of the player and spectates that player. The player is left standing
 * up and is moved around without a vehicle mount.
 */
class FirstPersonSpectatedEntityPlayerStanding extends FirstPersonSpectatedEntity {
    private final SpectatedFakePlayer fakePlayer;

    public FirstPersonSpectatedEntityPlayerStanding(CartAttachmentSeat seat, FirstPersonViewSpectator view, AttachmentViewer player) {
        super(seat, view, player);
        if (view.getLiveMode() == FirstPersonViewMode.HEAD) {
            SpectatedFakePlayerHead headPlayer = new SpectatedFakePlayerHead(player, seat.getManager());
            headPlayer.setHeadOffsetToStandingDefault();
            this.fakePlayer = headPlayer;
        } else {
            this.fakePlayer = new SpectatedFakePlayer(player, seat.getManager());
        }
        this.fakePlayer.setUseMinecartInterpolation(seat.isMinecartInterpolation());
        this.fakePlayer.setForceAbsoluteSync(true);
    }

    private Matrix4x4 toBodyTransform(Matrix4x4 eyeTransform) {
        Vector pos = eyeTransform.toVector();
        pos.setY(pos.getY() - VirtualEntity.PLAYER_STANDING_EYE_HEIGHT);

        Matrix4x4 bodyTransform = new Matrix4x4();
        bodyTransform.translate(pos);
        bodyTransform.rotate(eyeTransform.getRotation());
        return bodyTransform;
    }

    @Override
    public void start(Matrix4x4 eyeTransform) {
        fakePlayer.start(toBodyTransform(eyeTransform), seat.calcMotion());
    }

    @Override
    public void stop() {
        this.fakePlayer.stop();
    }

    @Override
    public void updatePosition(Matrix4x4 eyeTransform) {
        this.fakePlayer.updatePosition(toBodyTransform(eyeTransform));
        // Keep cadence equal to regular standing seat display logic: sync every tick.
        this.fakePlayer.syncPosition(true);
    }

    @Override
    public void syncPosition(boolean absolute) {
        // Already synchronized in updatePosition() every tick.
    }

    @Override
    public VirtualEntity getCurrentEntity() {
        return fakePlayer.getCurrentEntity();
    }
}
