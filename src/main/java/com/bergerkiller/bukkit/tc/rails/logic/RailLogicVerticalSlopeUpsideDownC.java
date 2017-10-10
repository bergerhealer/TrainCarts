package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

/**
 * Handles rail logic of an upside-down slope with a vertical rail below it.<br>
 * <br>
 * <img src="./doc-files/sloped_vertical_rail_logics.png" />
 */
public class RailLogicVerticalSlopeUpsideDownC extends RailLogicVerticalSlopeUpsideDownA {
    private static final RailLogicVerticalSlopeUpsideDownC[] values = new RailLogicVerticalSlopeUpsideDownC[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVerticalSlopeUpsideDownC(FaceUtil.notchToFace(i << 1));
        }
    }

    private RailLogicVerticalSlopeUpsideDownC(BlockFace direction) {
        super(direction);
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
    public boolean isVerticalHalf(double y, IntVector3 blockPos) {
        return super.isVerticalHalf(y - 1.0, blockPos);
    }

    @Override
    protected double getYOffset() {
        return 1.0;
    }
}
