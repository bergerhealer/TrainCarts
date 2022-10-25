package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Horizontal rail logic that does not operate on the vertical motion and position
 */
public class RailLogicHorizontal extends RailLogic {
    private static final RailLogicHorizontal[] values = new RailLogicHorizontal[8];
    private static final RailLogicHorizontal[] values_upsidedown = new RailLogicHorizontal[8];

    static {
        for (int i = 0; i < 8; i++) {
            values[i] = new RailLogicHorizontal(FaceUtil.notchToFace(i), false);
            values_upsidedown[i] = new RailLogicHorizontal(FaceUtil.notchToFace(i), true);
        }
    }

    private final boolean upside_down;
    protected final double dx, dz;
    protected final double startX, startZ;
    private final BlockFace horizontalCartDir;
    private final BlockFace[] cartFaces;
    private final BlockFace[] faces;
    private final BlockFace[] ends;
    public static final double Y_POS_OFFSET = 0.0625;
    public static final double Y_POS_OFFSET_UPSIDEDOWN = -Y_POS_OFFSET;
    public static final double Y_POS_OFFSET_UPSIDEDOWN_SLOPE = -0.2;

    protected RailLogicHorizontal(BlockFace direction) {
        this(direction, false);
    }

    protected RailLogicHorizontal(BlockFace direction, boolean upsideDown) {
        super(direction);
        this.horizontalCartDir = FaceUtil.getRailsCartDirection(direction);
        // Train drives on this horizontal rails upside-down
        this.upside_down = upsideDown;
        // Motion faces for the rails cart direction
        this.cartFaces = FaceUtil.getFaces(this.getCartDirection());
        // The ends of the rail, where the rail can be connected to other rails
        this.ends = FaceUtil.getFaces(direction.getOppositeFace());
        // Fix north/west, they are non-existent
        direction = FaceUtil.toRailsDirection(direction);
        // Faces and direction
        if (this.curved) {
            this.dx = 0.5 * direction.getModX();
            this.dz = -0.5 * direction.getModZ();
            // Invert direction, because it is wrong otherwise
            direction = direction.getOppositeFace();
        } else {
            this.dx = direction.getModX();
            this.dz = direction.getModZ();
        }
        // Start offset and direction faces
        this.faces = FaceUtil.getFaces(direction);
        final double startFactor = MathUtil.invert(0.5, !this.curved);
        this.startX = startFactor * faces[0].getModX();
        this.startZ = startFactor * faces[0].getModZ();
        // Invert all north and south (is for some reason needed)
        for (int i = 0; i < this.faces.length; i++) {
            if (this.faces[i] == BlockFace.NORTH || this.faces[i] == BlockFace.SOUTH) {
                this.faces[i] = this.faces[i].getOppositeFace();
            }
        }
    }

    /**
     * Gets the motion vector along which minecarts move according to this RailLogic
     * 
     * @return motion vector
     */
    public BlockFace getCartDirection() {
        return this.horizontalCartDir;
    }

    /**
     * Gets the horizontal rail logic to go into the direction specified
     *
     * @param direction to go to
     * @return Horizontal rail logic for that direction
     */
    public static RailLogicHorizontal get(BlockFace direction) {
        return values[FaceUtil.faceToNotch(direction)];
    }

    /**
     * Gets the horizontal rail logic to go into the direction specified
     *
     * @param direction to go to
     * @param upsideDown whether the Minecart drives on the rail upside-down
     * @return Horizontal rail logic for that direction
     */
    public static RailLogicHorizontal get(BlockFace direction, boolean upsideDown) {
        if (upsideDown) {
            return values_upsidedown[FaceUtil.faceToNotch(direction)];
        } else {
            return values[FaceUtil.faceToNotch(direction)];
        }
    }

    @Override
    protected RailPath createPath() {
        double base_y = isUpsideDown() ? Y_POS_OFFSET_UPSIDEDOWN : Y_POS_OFFSET;
        Vector p1 = new Vector(this.startX + 0.5, base_y, this.startZ + 0.5);
        Vector p2 = p1.clone();
        if (this.alongZ) {
            p2.setZ(p2.getZ() + this.dz);
        } else if (this.alongX) {
            p2.setX(p2.getX() + this.dx);
        } else {
            p2.setX(p2.getX() - this.dx);
            p2.setZ(p2.getZ() - this.dz);
        }
        getFixedPosition(p1, IntVector3.ZERO);
        getFixedPosition(p2, IntVector3.ZERO);
        return new RailPath.Builder()
                .up(this.isUpsideDown() ? BlockFace.DOWN : BlockFace.UP)
                .add(p1).add(p2).build();
    }

    /**
     * Gets whether this Rail Logic drives the train horizontally upside-down
     * 
     * @return True if upside-down
     */
    public boolean isUpsideDown() {
        return upside_down;
    }

    /**
     * Gets the position of the Minecart when snapped to the rails. The input
     * position vector is adjusted, with the result written into the same vector.
     * This is only used once when creating the path for this rail logic.
     *
     * @param position input and result output
     * @param railPos of the rails using this logic
     * @deprecated Was used before the introduction of Rail Paths. This is here
     *             for backwards compatibility with plugins like TC Hangrail
     */
    @Deprecated
    public void getFixedPosition(Vector position, IntVector3 railPos) {
        //nop
    }

    @Override
    public void onPathAdjust(RailState state) {
        // When coming in from the side, set motion to move down the slope
        if (this.isSloped()) {
            BlockFace enterFaceRot = state.enterFace();
            if (enterFaceRot == FaceUtil.rotate(this.horizontalCartDir, 2) ||
                enterFaceRot == FaceUtil.rotate(this.horizontalCartDir, -2))
            {
                state.position().setMotion(this.horizontalCartDir.getOppositeFace());
            }
        }
    }

    @Override
    public BlockFace getMovementDirection(BlockFace endDirection) {
        final BlockFace raildirection = this.getDirection();
        BlockFace direction;
        if (this.isSloped()) {
            // Sloped rail logic
            // When moving in the direction of a slope, or up, go up the slope
            // In all other cases, go down the slope
            if (endDirection == raildirection || endDirection == BlockFace.UP) {
                direction = raildirection; // up the slope
            } else {
                direction = raildirection.getOppositeFace(); // down the slope
            }
        } else if (this.curved) {
            // Curved rail logic
            // When moving in the same direction as an end, go to that end
            // When moving in the opposite direction of an end, pick the other end
            BlockFace targetFace;
            if (endDirection == this.ends[0] || endDirection == this.ends[1].getOppositeFace()) {
                targetFace = this.ends[0];
            } else {
                targetFace = this.ends[1];
            }

            direction = this.getCartDirection();
            if (!LogicUtil.contains(targetFace, this.cartFaces)) {
                direction = direction.getOppositeFace();
            }
        } else {
            // Straight rail logic
            // Go in the direction of the rail, unless the opposite direction is chosen
            // This logic fulfills the 'south-east' rule
            if (endDirection == raildirection.getOppositeFace()) {
                direction = raildirection.getOppositeFace();
            } else {
                direction = raildirection;
            }
        }
        return direction;
    }
}
