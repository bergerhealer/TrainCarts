package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

import org.bukkit.block.BlockFace;

/**
 * Handles the rail logic of a sloped rail with a vertical rail above it.<br>
 * <br>
 * <img src="./doc-files/sloped_vertical_rail_logics.png" />
 */
public class RailLogicVerticalSlopeNormalA extends RailLogicVerticalSlopeBase {
    private static final RailLogicVerticalSlopeNormalA[] values = new RailLogicVerticalSlopeNormalA[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVerticalSlopeNormalA(FaceUtil.notchToFace(i << 1));
        }
    }

    private RailLogicVerticalSlopeNormalA(BlockFace direction) {
        super(direction, false);
    }

    /**
     * Gets the sloped-vertical rail logic for the the sloped track leading upwards facing the direction specified
     *
     * @param direction of the sloped rail
     * @return Rail Logic
     */
    public static RailLogicVerticalSlopeNormalA get(BlockFace direction) {
        return values[FaceUtil.faceToNotch(direction) >> 1];
    }

    @Override
    public final boolean isVerticalHalf(double y, IntVector3 blockPos) {
        return y > (blockPos.midY() + Y_POS_OFFSET);
    }

}
