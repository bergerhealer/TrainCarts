package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Synchronizes the seat to the player sitting in the seat
 */
public abstract class FirstPersonView {
    protected final CartAttachmentSeat seat;
    protected final AttachmentViewer player;
    private FirstPersonViewMode _liveMode = FirstPersonViewMode.DEFAULT;
    private FirstPersonViewMode _mode = FirstPersonViewMode.DYNAMIC;
    private FirstPersonViewLockMode _lock = FirstPersonViewLockMode.MOVE;
    protected ObjectPosition _eyePosition = new ObjectPosition();

    /**
     * How far away from looking forwards the player can yaw left/right when the body is locked
     */
    public static final float BODY_LOCK_FOV_LIMIT = 70.0f;

    public FirstPersonView(CartAttachmentSeat seat, AttachmentViewer player) {
        this.seat = seat;
        this.player = player;
    }

    public ObjectPosition getEyePosition() {
        return this._eyePosition;
    }

    public AttachmentViewer getViewer() {
        return this.player;
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
        } else if (seat.useSmoothCoasters()) {
            // Smooth coasters places the player in such a way that it 'hangs' below the head
            // As such, return the head position exactly
            Matrix4x4 transform = seat.getTransform().clone();
            transform.translate(seat.seated.getFirstPersonCameraOffset());
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
     * Gets the first-person viewed eye position of the Player that is in first-person view
     *
     * @return FPV Eye Location
     */
    public Location getPlayerEyeLocation() {
        if (player != null) {
            Vector pos = getEyeTransform().toVector();
            Location eye = player.getPlayer().getEyeLocation();
            eye.setX(pos.getX());
            eye.setY(pos.getY());
            eye.setZ(pos.getZ());
            return eye;
        }
        return null;
    }

    /**
     * Called when a Player entered the seat. Should make the first-person view mode
     * active for this viewer.
     *
     * @param viewer AttachmentViewer of the Player that entered the seat
     * @param isReload Whether this is a reload. If true, makeHidden with isReload true was called before
     */
    public abstract void makeVisible(AttachmentViewer viewer, boolean isReload);

    /**
     * Called when a Player was inside the seat, but now exited it, disabling the
     * first-person view.
     *
     * @param viewer AttachmentViewer of the Player viewer that left the seat
     * @param isReload Whether this is a reload. If true, makeVisible will be called later.
     */
    public abstract void makeHidden(AttachmentViewer viewer, boolean isReload);

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

    protected static void setPlayerVisible(AttachmentViewer player, boolean visible) {
        // Send metadata packet to make the actual player entity visible or invisible
        {
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_FLAGS, EntityUtil.getDataWatcher(player.getPlayer()).get(EntityHandle.DATA_FLAGS));
            metaTmp.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_INVISIBLE, !visible);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(player.getEntityId(), metaTmp, true);
            player.send(metaPacket);
        }

        if (visible) {
            // Resend all original equipment information of the Player
            PlayerInventory inv = player.getPlayer().getInventory();
            sendEquipment(player, EquipmentSlot.HEAD, inv.getHelmet());
            sendEquipment(player, EquipmentSlot.CHEST, inv.getChestplate());
            sendEquipment(player, EquipmentSlot.FEET, inv.getBoots());
            sendEquipment(player, EquipmentSlot.LEGS, inv.getLeggings());
        } else {
            // Wipe all equipment information of the Player
            sendEquipment(player, EquipmentSlot.HEAD, null);
            sendEquipment(player, EquipmentSlot.CHEST, null);
            sendEquipment(player, EquipmentSlot.FEET, null);
            sendEquipment(player, EquipmentSlot.LEGS, null);
        }
    }

    protected static void sendEquipment(AttachmentViewer player, EquipmentSlot slot, ItemStack item) {
        if (HAS_EQUIPMENT_SEND_METHOD) {
            sendEquipmentUsingBukkit(player.getPlayer(), slot, item);
        } else {
            player.sendSilent(PacketPlayOutEntityEquipmentHandle.createNew(
                    player.getEntityId(), slot, item));
        }
    }

    private static final boolean HAS_EQUIPMENT_SEND_METHOD = Common.evaluateMCVersion(">=", "1.18");
    private static void sendEquipmentUsingBukkit(Player player, EquipmentSlot slot, ItemStack item) {
        if (item == null) {
            item = ItemUtil.emptyItem(); // Boo bukkit.
        }
        player.sendEquipmentChange(player, slot, item);
    }

    /**
     * The pitch and yaw of a (player's) head. Includes helpful utilities
     * for calculating an appropriate head rotation given an eye transform.
     */
    public static final class HeadRotation {
        public final float pitch;
        public final float yaw;
        public final float roll;
        public final Vector pyr;

        private HeadRotation(float pitch, float yaw, float roll) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.pyr = new Vector(pitch, yaw, roll);
        }

        public HeadRotation flipVertical() {
            return new HeadRotation(180.0f - pitch, 180.0f + yaw, roll);
        }

        /**
         * If the current pitch is beyond the normal human limits, alters the pitch
         * and yaw to be level with the horizon instead.
         *
         * @return Head rotation that's for sure level
         */
        public HeadRotation ensureLevel() {
            if (Math.abs(this.pitch) > 90.0f) {
                return flipVertical();
            }
            return this;
        }

        /**
         * Computes the most appropriate head rotation for the given eye transform.
         * A guarantee is made that the player will look in the same direction as
         * the forward vector, while handling an appropriate vertical flip.
         *
         * @param eyeTransform
         * @return head rotation
         */
        public static HeadRotation compute(Matrix4x4 eyeTransform) {
            return compute(eyeTransform.getRotation());
        }

        /**
         * Computes the most appropriate head rotation for the given eye orientation.
         * A guarantee is made that the player will look in the same direction as
         * the forward vector, while handling an appropriate vertical flip.
         *
         * @param eyeOrientation
         * @return head rotation
         */
        public static HeadRotation compute(Quaternion eyeOrientation) {
            Vector forward = eyeOrientation.forwardVector();
            Vector up = eyeOrientation.upVector();

            if (Math.abs(forward.getY()) < 0.999) {
                // Look into the direction
                HeadRotation rot = new HeadRotation(
                        MathUtil.getLookAtPitch(forward.getX(), forward.getY(), forward.getZ()),
                        MathUtil.getLookAtYaw(forward) + 90.0f,
                        (float) eyeOrientation.getRoll());

                // Upside-down modifier
                if (up.getY() < 0.0) {
                    rot = rot.flipVertical();
                }

                return rot;
            } else {
                float pitch, yaw, roll;
                if (forward.getY() > 0.0) {
                    // Looking upwards and spinning
                    pitch = -90.0f;
                    yaw = MathUtil.getLookAtYaw(up) - 90.0f;
                    roll = (float) eyeOrientation.getRoll();
                } else {
                    // Looking downwards and spinning
                    pitch = 90.0f;
                    yaw = MathUtil.getLookAtYaw(up) + 90.0f;
                    roll = (float) eyeOrientation.getRoll();
                }
                return new HeadRotation(pitch, yaw, roll);
            }
        }

        public static HeadRotation of(float pitch, float yaw) {
            return new HeadRotation(pitch, yaw, 0.0f);
        }
    }
}
