package com.bergerkiller.bukkit.tc.attachments.control.seat;

import com.bergerkiller.bukkit.tc.Util;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;

/**
 * Doesn't seat the player in any mount, leaving the player standing. Velocity movement control is used
 * to move the player around.
 */
public class FirstPersonViewStanding extends FirstPersonViewDefault {
    // Used for first-person player position control
    private AttachmentViewer.MovementController _movementControl = null;

    public FirstPersonViewStanding(CartAttachmentSeat seat, AttachmentViewer player) {
        super(seat, player);
    }

    @Override
    public boolean isFakeCameraUsed() {
        return false;
    }

    @Override
    public void makeVisible(AttachmentViewer viewer, boolean isReload) {
        // If rotation is locked, make the player look the right way
        if (!isReload && seat.isRotationLocked()) {
            // Body is locked, make the player face forwards according to the eye transform
            HeadRotation rot = HeadRotation.compute(getEyeTransform()).ensureLevel();
            viewer.send(Util.createAbsoluteRotationPacket(rot.yaw, rot.pitch));
        }

        // Move the player to where the player should be
        updateVelocityControl();

        //TODO: Some smooth coasters related stuff?
    }

    @Override
    public void makeHidden(AttachmentViewer viewer, boolean isReload) {
        // Stop this
        if (_movementControl != null) {
            _movementControl.stop();
            _movementControl = null;
        }

        //TODO: Some smooth coasters related stuff?
    }

    @Override
    public void onTick() {
        // Use velocity packets to move a standing player around
        updateVelocityControl();

        super.onTick();
    }

    private void updateVelocityControl() {
        if (_movementControl == null) {
            _movementControl = getViewer().controlMovement(
                    AttachmentViewer.MovementController.Options.create()
                            .preserveInput(true));
        }

        Vector pos;
        if (this._eyePosition.isDefault()) {
            // Use seat position (simple)
            pos = seat.getTransform().toVector();
        } else {
            // Position head at eye position transform
            pos = this.getEyeTransform().toVector();
            pos.setY(pos.getY() - VirtualEntity.PLAYER_STANDING_EYE_HEIGHT);
        }
        _movementControl.update(pos);
    }
}
