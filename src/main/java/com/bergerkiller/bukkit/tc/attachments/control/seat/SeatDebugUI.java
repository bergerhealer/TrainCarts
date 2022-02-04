package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.spectator.FirstPersonEyePreview;

/**
 * All components showing ingame what the seat is like, when working with the
 * attachment editor.
 */
public class SeatDebugUI {
    // Owning seat
    private final CartAttachmentSeat seat;
    // Uses spectator mode to display exactly how a player would view from inside the seat
    private final Map<Player, FirstPersonEyePreview> _eyePreviews;
    // Displays a floating arrow pointing where the eyes are at
    private final FirstPersonEyePositionArrow _eyeArrow;

    public SeatDebugUI(CartAttachmentSeat seat) {
        this.seat = seat;
        this._eyePreviews = new HashMap<>();
        this._eyeArrow = new FirstPersonEyePositionArrow(seat);
    }

    /**
     * Previews the exact position of the eye for a Player by using spectator mode.
     * The preview is displayed for the number of ticks specified. 0 ticks disables
     * the preview.
     *
     * @param player Player to make preview
     * @param numTicks Number of ticks to preview
     * @return True if the preview was started
     */
    public void previewEye(Player player, int numTicks) {
        // Don't allow for this, that's messy
        if (!seat.isAttached() || seat.firstPerson.player == player || !player.isOnline()) {
            return;
        }

        if (numTicks <= 0) {
            FirstPersonEyePreview preview = this._eyePreviews.remove(player);
            if (preview != null) {
                preview.stop();
                onEyePreviewStopped(preview.player);
            }
        } else if (this._eyePreviews.computeIfAbsent(player, p -> new FirstPersonEyePreview(seat, p))
                    .start(numTicks, seat.firstPerson.getEyeTransform())
        ) {
            onEyePreviewStarted(player);
        }
    }

    /**
     * Shows an eye arrow where the eyes are for a Player.
     * Does nothing if the player is in first-person already or is not online,
     * or is previewing the eye.
     *
     * @param player Player to show the arrow to
     * @param numTicks Number of ticks to display
     */
    public void showEyeArrow(Player player, int numTicks) {
        // Don't allow for this, that's messy
        if (!seat.isAttached() || seat.firstPerson.player == player || !player.isOnline() || this._eyePreviews.containsKey(player)) {
            return;
        }

        if (numTicks <= 0) {
            this._eyeArrow.stop(player);
        } else {
            this._eyeArrow.start(player, numTicks);
        }
    }

    /**
     * Updates the eye preview, if a preview is active
     */
    public void updateEyePreview() {
        if (!this._eyePreviews.isEmpty()) {
            Matrix4x4 eyeTransform = seat.firstPerson.getEyeTransform();
            Iterator<FirstPersonEyePreview> iter = this._eyePreviews.values().iterator();
            do {
                FirstPersonEyePreview preview = iter.next();
                if (!preview.updateRemaining()) {
                    // Stopped
                    iter.remove();
                    onEyePreviewStopped(preview.player);
                } else if (!preview.player.isOnline()) {
                    // Just remove
                    iter.remove();
                } else {
                    // Update
                    preview.updatePosition(eyeTransform);
                }
            } while (iter.hasNext());
        }

        this._eyeArrow.updatePosition();
    }

    /**
     * Synchronizes new positions to the players
     *
     * @param absolute
     */
    public void syncEyePreviews(boolean absolute) {
        if (!this._eyePreviews.isEmpty()) {
            for (FirstPersonEyePreview preview : this._eyePreviews.values()) {
                preview.syncPosition(absolute);
            }
        }
        this._eyeArrow.syncPosition(absolute);
    }

    /**
     * Aborts all ongoing eye previews
     */
    public void stopEyePreviews() {
        if (!this._eyePreviews.isEmpty()) {
            for (FirstPersonEyePreview preview : this._eyePreviews.values()) {
                preview.stop();
            }
            this._eyePreviews.clear();
        }
        this._eyeArrow.stop();
    }

    /**
     * Gets whether the seated entity is hidden (made invisible) because of an
     * active eye preview.
     *
     * @param player
     * @return True if active
     */
    public boolean isSeatedEntityHiddenBecauseOfPreview(Player player) {
        return this._eyePreviews.containsKey(player) &&
                seat.seated.isDisplayed() && seat.firstPerson.getLiveMode() != FirstPersonViewMode.THIRD_P;
    }

    private void onEyePreviewStarted(Player player) {
        // If player is also viewing the entity, make that entity invisible
        // This prevents things looking all glitched
        // Only needed when not viewed in third-p mode
        if (seat.seated.isDisplayed() && seat.firstPerson.getLiveMode() != FirstPersonViewMode.THIRD_P) {
            seat.seated.makeHidden(player);
        }

        // Disable the preview arrow - gets in the way
        _eyeArrow.stop(player);
    }

    private void onEyePreviewStopped(Player player) {
        // Stopped the preview, can re-spawn any third person view
        if (seat.seated.isDisplayed() && seat.firstPerson.getLiveMode() != FirstPersonViewMode.THIRD_P) {
            seat.seated.makeVisible(player);
        }
    }
}
