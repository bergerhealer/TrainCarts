package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

/**
 * Handles rail logic from a vertical rail to an upside-down slope 2.0
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
    public final double getSlopeRatio(double y, IntVector3 blockPos) {
        return (y - (double) blockPos.y + 0.65);
    }

    @Override
    public boolean isReversedSlope() {
        return false;
    }
}
