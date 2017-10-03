package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

/**
 * Handles rail logic from a vertical rail to an upside-down slope
 */
public class RailLogicVerticalSlopeUpsideDownA extends RailLogicVerticalSlopeBase {
    private static final RailLogicVerticalSlopeUpsideDownA[] values = new RailLogicVerticalSlopeUpsideDownA[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVerticalSlopeUpsideDownA(FaceUtil.notchToFace(i << 1));
        }
    }

    private RailLogicVerticalSlopeUpsideDownA(BlockFace direction) {
        super(direction, true);
    }

    /**
     * Gets the sloped-vertical rail logic for the the sloped track leading upwards facing the direction specified
     *
     * @param direction of the sloped rail
     * @return Rail Logic
     */
    public static RailLogicVerticalSlopeUpsideDownA get(BlockFace direction) {
        return values[FaceUtil.faceToNotch(direction) >> 1];
    }

    @Override
    public final double getSlopeRatio(double y, IntVector3 blockPos) {
        return ((double) blockPos.y - y - 0.65);
    }

    @Override
    public boolean isReversedSlope() {
        return true;
    }
}
