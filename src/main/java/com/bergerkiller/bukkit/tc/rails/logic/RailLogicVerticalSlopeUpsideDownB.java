package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Handles rail logic of an upside-down slope with a vertical rail above it<br>
 * <br>
 * <img src="./doc-files/sloped_vertical_rail_logics.png" />
 */
public class RailLogicVerticalSlopeUpsideDownB extends RailLogicVerticalSlopeBase {
    private static final RailLogicVerticalSlopeUpsideDownB[] values = new RailLogicVerticalSlopeUpsideDownB[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVerticalSlopeUpsideDownB(FaceUtil.notchToFace(i << 1));
        }
    }

    private RailLogicVerticalSlopeUpsideDownB(BlockFace direction) {
        super(direction, true);
    }

    /**
     * Gets the sloped-vertical rail logic for the the sloped track leading upwards facing the direction specified
     *
     * @param direction of the sloped rail
     * @return Rail Logic
     */
    public static RailLogicVerticalSlopeUpsideDownB get(BlockFace direction) {
        return values[FaceUtil.faceToNotch(direction) >> 1];
    }

    @Override
    protected RailPath createPath() {
        // Initialize the rail path, making use of getFixedPosition for each node
        // This type of logic has a path consisting of two line segments
        // One segment is vertical, and leads to somewhere in the middle
        // The other segment is sloped from the middle to the other end
        // The x/z coordinates are asserted from the y-coordinate
        final double half_offset = -0.5 + Y_POS_OFFSET_UPSIDEDOWN + Y_POS_OFFSET_UPSIDEDOWN_SLOPE;
        Vector p1 = new Vector(0.5, 1.0, 0.5);
        Vector p2 = new Vector(0.5, Y_POS_OFFSET + half_offset, 0.5);
        Vector p3 = new Vector(0.5, Y_POS_OFFSET + half_offset - 0.5, 0.5);

        if (this.alongZ) {
            p3.setZ(0.5 - 0.5 * (double) this.getDirection().getModZ());
        } else if (this.alongX) {
            p3.setX(0.5 - 0.5 * (double) this.getDirection().getModX());
        }

        return new RailPath.Builder()
                .add(p1, this.getDirection())
                .add(p2, this.getDirection())
                .add(p3, BlockFace.DOWN).build();
    }

}
