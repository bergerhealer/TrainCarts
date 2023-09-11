package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Handles rail logic of an upside-down slope with a vertical rail below it.<br>
 * <br>
 * <img alt="vertical sloped rail logic" src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/sloped_vertical_rail_logics.png" />
 */
public class RailLogicVerticalSlopeUpsideDownC extends RailLogicVerticalSlopeBase {
    private static final RailLogicVerticalSlopeUpsideDownC[] values = new RailLogicVerticalSlopeUpsideDownC[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVerticalSlopeUpsideDownC(FaceUtil.notchToFace(i << 1));
        }
    }

    private RailLogicVerticalSlopeUpsideDownC(BlockFace direction) {
        super(direction, true);
    }

    /**
     * Gets the sloped-vertical rail logic for the the sloped track leading upwards facing the direction specified
     *
     * @param direction of the sloped rail
     * @return Rail Logic
     */
    public static RailLogicVerticalSlopeUpsideDownC get(BlockFace direction) {
        return values[FaceUtil.faceToNotch(direction) >> 1];
    }

    @Override
    protected RailPath createPath() {
        // Initialize the rail path, making use of getFixedPosition for each node
        // This type of logic has a path consisting of two line segments
        // One segment is vertical, and leads to somewhere in the middle
        // The other segment is sloped from the middle to the other end
        // The x/z coordinates are asserted from the y-coordinate
        double dx = 0.5 - RailLogicVertical.XZ_POS_OFFSET * this.getDirection().getModX();
        double dz = 0.5 - RailLogicVertical.XZ_POS_OFFSET * this.getDirection().getModZ();
        Vector p1 = new Vector(dx, 0.0, dz);
        Vector p2 = new Vector(dx, 0.81, dz);
        Vector p3 = new Vector(dx, 2.0 + Y_POS_OFFSET_UPSIDEDOWN + Y_POS_OFFSET_UPSIDEDOWN_SLOPE, dz);

        if (this.alongZ) {
            p3.setZ(0.5 + 0.5 * (double) this.getDirection().getModZ());
        } else if (this.alongX) {
            p3.setX(0.5 + 0.5 * (double) this.getDirection().getModX());
        }

        // Guarantee that the top of the slope cuts off where UpsideDownA begins
        // This prevents trains splitting up when crossing the rails
        if (p3.getY() > 1.0) {
            Vector d = p3.clone().subtract(p2).normalize();
            d.multiply((p3.getY() - 1.0) / d.getY());
            p3.subtract(d);
        }

        return new RailPath.Builder()
                .add(p1, this.getDirection())
                .add(p2, this.getDirection())
                .add(p3, BlockFace.DOWN).build();
    }

}
