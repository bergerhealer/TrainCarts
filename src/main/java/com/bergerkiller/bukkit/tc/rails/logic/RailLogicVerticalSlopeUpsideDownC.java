package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Handles rail logic of an upside-down slope with a vertical rail below it.<br>
 * <br>
 * <img src="./doc-files/sloped_vertical_rail_logics.png" />
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
    public RailPath getPath() {
        if (this.railPath == RailPath.EMPTY) {
            // Initialize the rail path, making use of getFixedPosition for each node
            // This type of logic has a path consisting of two line segments
            // One segment is vertical, and leads to somewhere in the middle
            // The other segment is sloped from the middle to the other end
            // The x/z coordinates are asserted from the y-coordinate
            Vector p1 = new Vector(0.5, 0.0, 0.5);
            Vector p2 = new Vector(0.5, this.getHalfOffset(), 0.5);
            Vector p3 = new Vector(0.5, this.getHalfOffset() + 0.5, 0.5);

            if (this.alongZ) {
                p3.setZ(0.5 + 0.5 * (double) this.getDirection().getModZ());
            } else if (this.alongX) {
                p3.setX(0.5 + 0.5 * (double) this.getDirection().getModX());
            }

            this.railPath = RailPath.create(p1, p2, p3);
        }
        return this.railPath;
    }

    @Override
    protected double getYOffset() {
        return 1.0;
    }

    @Override
    public boolean isVerticalHalf(double y, IntVector3 blockPos) {
        return (y + 0.0001) < (blockPos.y + this.getHalfOffset());
    }

    @Override
    protected double getHalfOffset() {
        return 0.5 + Y_POS_OFFSET_UPSIDEDOWN + Y_POS_OFFSET_UPSIDEDOWN_SLOPE;
    }

}
