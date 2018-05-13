package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Handles rail logic of an upside-down slope with a vertical rail below it.
 * This logic will most likely rarely be used because it teleports the minecart down onto different logic.
 * It is here in case the sloped rail is hit anyway, and the Minecart needs to be repositioned.<br>
 * <br>
 * <img src="./doc-files/sloped_vertical_rail_logics.png" />
 */
public class RailLogicVerticalSlopeUpsideDownA extends RailLogicVerticalSlopeBase {
    private static final RailLogicVerticalSlopeUpsideDownA[] values = new RailLogicVerticalSlopeUpsideDownA[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVerticalSlopeUpsideDownA(FaceUtil.notchToFace(i << 1));
        }
    }

    protected RailLogicVerticalSlopeUpsideDownA(BlockFace direction) {
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
    protected RailPath createPath() {
        double base_y = Y_POS_OFFSET_UPSIDEDOWN + Y_POS_OFFSET_UPSIDEDOWN_SLOPE;
        Vector p1, p2;
        switch (this.getDirection()) {
        case NORTH:
            p1 = new Vector(0.5, base_y+1.0, 0.0);
            p2 = new Vector(0.5, base_y, 1.0);
            break;
        case EAST:
            p1 = new Vector(1.0, base_y+1.0, 0.5);
            p2 = new Vector(0.0, base_y, 0.5);
            break;
        case SOUTH:
            p1 = new Vector(0.5, base_y+1.0, 1.0);
            p2 = new Vector(0.5, base_y, 0.0);
            break;
        case WEST:
        default:
            p1 = new Vector(0.0, base_y+1.0, 0.5);
            p2 = new Vector(1.0, base_y, 0.5);
            break;
        }

        // Aligns p2 to the bottom floor of the block this logic is for
        // This makes sure RailLogicVerticalUpsideDownC continues working
        if (p2.getY() < 0.0) {
            Vector d = p2.clone().subtract(p1).normalize();
            d.multiply(p2.getY() / d.getY());
            p2.subtract(d);
        }

        // Aligns to the x/z border instead
        /*
        {
            Vector d = p2.clone().subtract(p1).normalize();
            if (this.getDirection().getModX() != 0) {
                double dx = 0.5 - RailLogicVertical.XZ_POS_OFFSET * this.getDirection().getModX();
                d.multiply((p2.getX() - dx) / d.getX());
            } else {
                double dz = 0.5 - RailLogicVertical.XZ_POS_OFFSET * this.getDirection().getModZ();
                d.multiply((p2.getZ() - dz) / d.getZ());
            }
            p2.subtract(d);
        }
        */

        return new RailPath.Builder()
                .up(BlockFace.DOWN)
                .add(p1).add(p2).build();
    }

}
