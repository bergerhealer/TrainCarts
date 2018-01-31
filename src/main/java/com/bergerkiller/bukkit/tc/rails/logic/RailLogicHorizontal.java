package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

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
    public static final double Y_POS_OFFSET_UPSIDEDOWN = 0.25;
    public static final double Y_POS_OFFSET_UPSIDEDOWN_SLOPE = -0.4;

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
    public RailPath getPath() {
        if (this.railPath == RailPath.EMPTY) {
            // Initialize the rail path, making use of getFixedPosition for each node
            Vector p1 = new Vector(this.startX + 0.5, Y_POS_OFFSET, this.startZ + 0.5);
            Vector p2 = p1.clone();
            if (this.alongZ) {
                p2.setZ(p2.getZ() + this.dz);
            } else if (this.alongX) {
                p2.setX(p2.getX() + this.dx);
            } else {
                p2.setX(p2.getX() - this.dx);
                p2.setZ(p2.getZ() - this.dz);
            }
            this.getFixedPosition(p1, IntVector3.ZERO);
            this.getFixedPosition(p2, IntVector3.ZERO);
            this.railPath = new RailPath.Builder()
                    .up(this.upside_down ? BlockFace.DOWN : BlockFace.UP)
                    .add(p1).add(p2).build();
        }
        return this.railPath;
    }

    /**
     * Gets whether this Rail Logic drives the train horizontally upside-down
     * 
     * @return True if upside-down
     */
    public boolean isUpsideDown() {
        return upside_down;
    }

    @Override
    public void getFixedPosition(Vector position, IntVector3 railPos) {
        double newLocX = railPos.midX() + this.startX;
        double newLocZ = railPos.midZ() + this.startZ;
        if (this.alongZ) {
            // Moving along the X-axis
            newLocZ += this.dz * (position.getZ() - railPos.z);
        } else if (this.alongX) {
            // Moving along the Z-axis
            newLocX += this.dx * (position.getX() - railPos.x);
        } else {
            // Curve
            double factor = 2.0 * (this.dx * (position.getX() - newLocX) + this.dz * (position.getZ() - newLocZ));
            if (factor >= -0.001) {
                factor = -0.001;
            } else if (factor <= -0.999) {
                factor = -0.999;
            }
            newLocX += factor * this.dx;
            newLocZ += factor * this.dz;
        }
        position.setX(newLocX);
        position.setZ(newLocZ);

        if (isUpsideDown()) {
            position.setY((double) railPos.y - 1.0 + Y_POS_OFFSET_UPSIDEDOWN);
        } else {
            position.setY((double) railPos.y + Y_POS_OFFSET);
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

    @Override
    public void onPostMove(MinecartMember<?> member) {
        final CommonMinecart<?> entity = member.getEntity();

        // Correct the Y-coordinate for the newly moved position
        // This also makes sure we don't clip through the floor moving down a slope
        Vector tmp = entity.loc.vector();
        getFixedPosition(tmp, member.getBlockPos());
        entity.setPosition(entity.loc.getX(), tmp.getY(), entity.loc.getZ());
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        super.onPreMove(member);

        // Adjust position of Entity on rail
        CommonMinecart<?> entity = member.getEntity();
        IntVector3 railPos = member.getBlockPos();
        Vector position = entity.loc.vector();
        getFixedPosition(position, railPos);
        entity.loc.set(position);
    }
}
