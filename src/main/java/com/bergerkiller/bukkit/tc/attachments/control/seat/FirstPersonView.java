package com.bergerkiller.bukkit.tc.attachments.control.seat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.seat.spectator.FirstPersonEyePreview;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Synchronizes the seat to the player sitting in the seat
 */
public abstract class FirstPersonView {
    protected final CartAttachmentSeat seat;
    public Player player;
    private FirstPersonViewMode _liveMode = FirstPersonViewMode.DEFAULT;
    private FirstPersonViewMode _mode = FirstPersonViewMode.DYNAMIC;
    private FirstPersonViewLockMode _lock = FirstPersonViewLockMode.MOVE;
    private boolean _useSmoothCoasters = false;
    protected ObjectPosition _eyePosition = new ObjectPosition();
    private final Map<Player, FirstPersonEyePreview> _eyePreviews = new HashMap<>();

    public FirstPersonView(CartAttachmentSeat seat) {
        this.seat = seat;
    }

    public ObjectPosition getEyePosition() {
        return this._eyePosition;
    }

    /**
     * Gets whether because of a view mode change, this first person view will have to be
     * reset or not. If reset, the view is de-initialized (hidden) and re-initialized (visible).
     *
     * @param newViewMode
     * @return True if view should be reset when switching to this new live view mode
     */
    public boolean doesViewModeChangeRequireReset(FirstPersonViewMode newViewMode) {
        return newViewMode != getLiveMode() || newViewMode.hasFakePlayer();
    }

    /**
     * Gets the base transform for use with eye/view logic. If an eye position is set, returns the
     * seat transform transformed with this eye. Otherwise, returns the seat transform with a
     * third-person view offset. It is up to the caller to perform further appropriate adjustments.
     *
     * @return First-person base eye/view transform
     */
    protected Matrix4x4 getEyeTransform() {
        if (!this._eyePosition.isDefault()) {
            // Eye configured
            return Matrix4x4.multiply(seat.getTransform(), this._eyePosition.transform);
        } else if (this.getLiveMode() == FirstPersonViewMode.THIRD_P) {
            // Seat, with a third-person view offset from it based on the seated entity
            Matrix4x4 transform = seat.getTransform().clone();
            transform.translate(seat.seated.getThirdPersonCameraOffset());
            return transform;
        } else {
            // Return seat transform with a player butt-to-eye offset included
            // This offset is not rotated when the seat rotates, it is always y + 1
            Matrix4x4 eye = new Matrix4x4();
            eye.translate(seat.seated.getFirstPersonCameraOffset());
            eye.multiply(seat.getTransform());
            return eye;
        }
    }

    /**
     * Called when a Player entered the seat. Should make the first-person view mode
     * active for this viewer.
     *
     * @param viewer Player that entered the seat
     */
    public abstract void makeVisible(Player viewer);

    /**
     * Called when a Player was inside the seat, but now exited it, disabling the
     * first-person view.
     *
     * @param viewer Player viewer that left the seat
     */
    public abstract void makeHidden(Player viewer);

    /**
     * Called every tick to perform any logic required
     */
    public abstract void onTick();

    /**
     * Called every tick to update positions of structures used to move the
     * first-person view camera, if any
     *
     * @param absolute Whether this is an absolute position update
     */
    public abstract void onMove(boolean absolute);

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

    /**
     * Gets the way the first person view camera is locked
     *
     * @return lock mode
     */
    public FirstPersonViewLockMode getLockMode() {
        return this._lock;
    }

    /**
     * Sets the view lock mode to use
     *
     * @param lock
     */
    public void setLockMode(FirstPersonViewLockMode lock) {
        this._lock = lock;
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
        if (this.player == player || !player.isOnline()) {
            return;
        }

        if (numTicks <= 0) {
            FirstPersonEyePreview preview = this._eyePreviews.remove(player);
            if (preview != null) {
                preview.stop();
                onEyePreviewStopped(preview.player);
            }
        } else if (this._eyePreviews.computeIfAbsent(player, p -> new FirstPersonEyePreview(seat, p))
                    .start(numTicks, getEyeTransform())
        ) {
            onEyePreviewStarted(player);
        }
    }

    /**
     * Updates the eye preview, if a preview is active
     */
    public void updateEyePreview() {
        if (!this._eyePreviews.isEmpty()) {
            Matrix4x4 eyeTransform = this.getEyeTransform();
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
    }

    private void onEyePreviewStarted(Player player) {
        // If player is also viewing the entity, make that entity invisible
        // This prevents things looking all glitched
        // Only needed when not viewed in third-p mode
        if (seat.seated.isDisplayed() && getLiveMode() != FirstPersonViewMode.THIRD_P) {
            seat.seated.makeHidden(player);
        }
    }

    private void onEyePreviewStopped(Player player) {
        // Stopped the preview, can re-spawn any third person view
        if (seat.seated.isDisplayed() && getLiveMode() != FirstPersonViewMode.THIRD_P) {
            seat.seated.makeVisible(player);
        }
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
                seat.seated.isDisplayed() && getLiveMode() != FirstPersonViewMode.THIRD_P;
    }

    protected static void setPlayerVisible(Player player, boolean visible) {
        DataWatcher metaTmp = new DataWatcher();
        metaTmp.set(EntityHandle.DATA_FLAGS, EntityUtil.getDataWatcher(player).get(EntityHandle.DATA_FLAGS));
        metaTmp.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, !visible);
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(player.getEntityId(), metaTmp, true);
        PacketUtil.sendPacket(player, metaPacket);
    }
}
