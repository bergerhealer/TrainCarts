package com.bergerkiller.bukkit.tc.attachments.particle;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.tc.controller.components.WheelTrackerMember;

/**
 * Displays wheel items at the expected wheel positions
 * and cart orientation. Makes use of four {@link VirtualArrowItem}
 * entities to do this.
 */
public class VirtualMemberWheelPreview {
    private static final ItemStack WHEEL_ITEM = new ItemStack(Material.ENDER_PEARL);
    private int leftEntityId = -1, rightEntityId = -1;

    public void spawn(Player viewer, double width, WheelTrackerMember.Wheel wheel) {
        ComputedPositions computed = new ComputedPositions(width, wheel);

        this.leftEntityId = VirtualArrowItem.create(this.leftEntityId)
                .position(computed.pLeft, computed.orientation)
                .item(WHEEL_ITEM)
                .glowing(true)
                .spawn(viewer);
        this.rightEntityId = VirtualArrowItem.create(this.rightEntityId)
                .position(computed.pRight, computed.orientation)
                .item(WHEEL_ITEM)
                .glowing(true)
                .spawn(viewer);
    }

    public void update(Iterable<Player> viewers, double width, WheelTrackerMember.Wheel wheel) {
        ComputedPositions computed = new ComputedPositions(width, wheel);

        VirtualArrowItem.create(this.leftEntityId)
                .position(computed.pLeft, computed.orientation)
                .move(viewers);
        VirtualArrowItem.create(this.rightEntityId)
                .position(computed.pRight, computed.orientation)
                .move(viewers);
    }

    public void destroy(Player viewer) {
        VirtualArrowItem.create(this.leftEntityId).destroy(viewer);
        VirtualArrowItem.create(this.rightEntityId).destroy(viewer);
    }

    private static class ComputedPositions {
        public final Quaternion orientation;
        public final Vector pLeft;
        public final Vector pRight;

        public ComputedPositions(double width, WheelTrackerMember.Wheel wheel) {
            this.orientation = Quaternion.fromLookDirection(wheel.getForward(), wheel.getUp());
            Vector position = wheel.getAbsolutePosition();
            position.add(this.orientation.forwardVector().multiply(-0.03));

            this.pLeft = position.clone().add(orientation.rightVector().multiply(-0.5 * width));
            this.pRight = position.clone().add(orientation.rightVector().multiply(0.5 * width));
            this.orientation.rotateY(90.0);
        }
    }
}
