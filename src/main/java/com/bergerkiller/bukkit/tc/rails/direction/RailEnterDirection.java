package com.bergerkiller.bukkit.tc.rails.direction;

import java.util.ArrayList;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;

/**
 * A direction a train can travel towards a particular rail. For vanilla rails this is
 * covered by {@link com.bergerkiller.bukkit.tc.Direction}, but other directions
 * can be implemented to support other types of rails. Like named junction-based
 * directions for switched rails.
 */
public interface RailEnterDirection {
    /**
     * All rail enter directions. Always triggers the sign.
     */
    public static final RailEnterDirection[] ALL = RailEnterDirectionImpl.ALL;

    /**
     * Gets the name that identifies this RailEnterDirection. If put on a direction
     * list/header, it should uniquely absolutely identify this enter direction.
     *
     * @return Rail enter direction name
     */
    String name();

    /**
     * Computes the dot product between the motion vector specified and this rail
     * enter direction. If this returns a positive value, then the input motion
     * is in the same general direction as this enter direction.
     *
     * @param motion Motion direction vector
     * @return Dot product of motion with this rail enter direction
     */
    double motionDot(Vector motion);

    /**
     * Gets whether a train at a particular position on the rails, moving a certain
     * direction, activates according to this RailEnterDirection
     *
     * @param state RailState of the train
     * @return True if this enter direction matches positively
     */
    boolean match(RailState state);

    /**
     * Gets a RailEnterDirection used for matching trains moving towards certain rail
     * block face. This is the opposite face of the block face first hit/entered
     * by the train. Or more generally, the face towards which the train is moving.
     *
     * @param face Face towards which is moved / opposite face entered. Must be one of
     *             NORTH/EAST/SOUTH?WEST/UP/DOWN.
     * @return RailEnterDirection for moving towards this face
     */
    public static RailEnterDirection toFace(BlockFace face) {
        return RailEnterDirectionToFace.fromFace(face);
    }

    /**
     * Gets a RailEnterDirection used for matching trains moving into the specified
     * Direction. The forward direction is used to transform sign-relative
     * left/right/etc. into the appropriate face.<br>
     * <br>
     * Beware that left/right refer to what direction the train comes FROM, and absolute
     * north/east/etc. refer to the direction of movement. I know, confusing, but we're
     * stuck with this now.
     *
     * @param direction Input direction
     * @param forwardDirection Absolute forward direction
     * @return RailEnterDirection for moving into this direction (Face)
     */
    public static RailEnterDirection intoDirection(Direction direction, BlockFace forwardDirection) {
        return toFace(direction.getDirection(forwardDirection));
    }

    /**
     * Gets a RailEnterDirection used for matching trains coming from a certain rail
     * junction relative to the rail block.
     *
     * @param junction Rail Junction to come from
     * @return RailEnterDirection for coming from this junction
     */
    public static RailEnterDirection fromJunction(RailJunction junction) {
        return new RailEnterDirectionFromJunction(junction);
    }

    /**
     * Parses the input text into an array of rail enter directions it represents
     *
     * @param rail Rail Piece to decode Junctions of
     * @param forwardDirection Forward direction of the sign relative to which "left" and such are solved.
     *                         Supports all cardinal directions including sub-cardinal ones like north-west.
     * @param text Text to parse
     * @return Array of rail enter directions represented in the order they are declared
     */
    public static RailEnterDirection[] parseAll(RailPiece rail, BlockFace forwardDirection, String text) {
        return RailEnterDirectionImpl.parseAll(rail, forwardDirection, text);
    }

    /**
     * Converts an array of RailEnterDirection values to just the ones which represent
     * block faces.
     *
     * @param directions RailEnterDirection values
     * @return values which are faces, converted to it's BlockFace
     */
    public static BlockFace[] toFacesOnly(RailEnterDirection[] directions) {
        if (directions == null) {
            return null;
        }
        int len = directions.length;
        if (len == 0) {
            return new BlockFace[0];
        } else if (len == 1) {
            RailEnterDirection dir = directions[0];
            if (dir instanceof RailEnterDirectionToFace) {
                return new BlockFace[] { ((RailEnterDirectionToFace) dir).getFace() };
            } else {
                return new BlockFace[0];
            }
        } else {
            ArrayList<BlockFace> faces = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                RailEnterDirection dir = directions[i];
                if (dir instanceof RailEnterDirectionToFace) {
                    faces.add(((RailEnterDirectionToFace) dir).getFace());
                }
            }
            return faces.toArray(new BlockFace[faces.size()]);
        }
    }
}
