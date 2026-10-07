package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;

/**
 * Variant of {@link SpectatedFakePlayer} that synchronizes head position from
 * a body transform using a local head offset.
 */
public class SpectatedFakePlayerHead extends SpectatedFakePlayer {
    private double headOffsetX = 0.0;
    private double headOffsetY = 0.0;
    private double headOffsetZ = 0.0;

    public SpectatedFakePlayerHead(TrainCartsAttachmentViewer player) {
        this(player, null);
    }

    public SpectatedFakePlayerHead(TrainCartsAttachmentViewer player, AttachmentManager manager) {
        super(player, manager);
    }

    public void setHeadOffset(double x, double y, double z) {
        this.headOffsetX = x;
        this.headOffsetY = y;
        this.headOffsetZ = z;
    }

    public void setHeadOffsetToStandingDefault() {
        setHeadOffset(0.0, VirtualEntity.PLAYER_STANDING_EYE_HEIGHT, 0.0);
    }

    public void setHeadOffsetToSittingDefault() {
        setHeadOffset(0.0, VirtualEntity.PLAYER_SIT_BUTT_EYE_HEIGHT, 0.0);
    }

    @Override
    protected Matrix4x4 transformForSync(Matrix4x4 transform) {
        Matrix4x4 headTransform = transform.clone();
        headTransform.translate(headOffsetX, headOffsetY, headOffsetZ);
        return headTransform;
    }

    @Override
    protected boolean isHeadOnly() {
        return true;
    }
}
