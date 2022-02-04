package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.attachments.VirtualArmorStandItemEntity;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Displays an updated floating arrow where the eye position and look direction
 */
public final class FirstPersonEyePositionArrow {
    private final CartAttachmentSeat seat;
    private VirtualArmorStandItemEntity arrow;
    private int timeout;

    public FirstPersonEyePositionArrow(CartAttachmentSeat seat) {
        this.seat = seat;
        this.arrow = null;
        this.timeout = 0;
    }

    public void start(Player viewer, int tickDuration) {
        if (this.arrow == null) {
            this.arrow = new VirtualArmorStandItemEntity(seat.getManager());
            this.arrow.setItem(ItemTransformType.HEAD, new ItemStack(MaterialUtil.getFirst(
                    "ARROW", "LEGACY_ARROW")));
            this.arrow.getMetaData().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, true);
            this.arrow.getMetaData().setFlag(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_SET_MARKER, true);
            this.arrow.updatePosition(adjust(seat.firstPerson.getEyeTransform()));
            this.arrow.syncPosition(true);
        }

        if (!this.arrow.isViewer(viewer)) {
            this.arrow.spawn(viewer, new Vector());
        }

        this.timeout = Math.max(this.timeout, tickDuration);
    }

    public void stop(Player viewer) {
        if (this.arrow != null && this.arrow.isViewer(viewer)) {
            this.arrow.destroy(viewer);
            if (!this.arrow.hasViewers()) {
                this.arrow = null;
            }
        }
    }

    public void stop() {
        if (this.arrow != null) {
            this.arrow.destroyForAll();
            this.arrow = null;
        }
        this.timeout = 0;
    }

    public void updatePosition() {
        // If timeout > 0 and is now 0 after ticking, disable the arrow
        if (this.timeout > 0) {
            if (this.timeout == 1) {
                this.stop();
            } else {
                this.timeout--;
            }
        }

        if (this.arrow != null) {
            this.arrow.updatePosition(adjust(seat.firstPerson.getEyeTransform()));
        }
    }

    public void syncPosition(boolean absolute) {
        if (this.arrow != null) {
            this.arrow.syncPosition(true);
        }
    }

    private static Matrix4x4 adjust(Matrix4x4 eyeTransform) {
        Vector pos = eyeTransform.toVector();
        Quaternion rot = eyeTransform.getRotation();

        // Transformation position adjustment for the arrow
        Vector v = new Vector(-0.27, -0.5, -0.2);
        rot.transformPoint(v);
        pos.add(v);

        // Rotate arrow to face the way it should
        rot.rotateY(-90.0);
        rot.rotateZ(-45.0);

        // Produce final result matrix
        Matrix4x4 result = new Matrix4x4();
        result.translate(pos);
        result.rotate(rot);
        return result;
    }
}
