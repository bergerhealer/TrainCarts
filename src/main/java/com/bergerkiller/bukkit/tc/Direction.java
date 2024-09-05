package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.utils.FaceUtil;

import org.bukkit.block.BlockFace;

import java.util.LinkedHashSet;

public enum Direction {
    NORTH(true, "n", "north"),
    EAST(true, "e", "east"),
    SOUTH(true, "s", "south"),
    WEST(true, "w", "west"),
    LEFT(false, "l", "left"),
    RIGHT(false, "r", "right"),
    /** {@link #LEFT} selected when switcher sign does not define a direction */
    IMPLICIT_LEFT(false, "implicit_left"),
    /** {@link #RIGHT} selected when switcher sign does not define a direction */
    IMPLICIT_RIGHT(false, "implicit_right"),
    FORWARD(false, "f", "front", "forward", "forwards"),
    BACKWARD(false, "b", "back", "backward", "backwards"),
    UP(true, "u", "up", "upwards", "above"),
    DOWN(true, "d", "down", "downwards", "below"),
    CONTINUE(false, "continue"),
    REVERSE(false, "reverse"),
    NONE(true, "", "n", "none");

    private final boolean absolute;
    private final String[] aliases;

    Direction(boolean absolute, String... aliases) {
        this.absolute = absolute;
        this.aliases = aliases;
    }

    /**
     * Gets whether this Direction is absolute. If it is,
     * {@link #getDirection(BlockFace)} will always output the same
     * BlockFace.
     *
     * @return True if absolute
     */
    public boolean isAbsolute() {
        return this.absolute;
    }

    public String[] aliases() {
        return this.aliases;
    }

    public BlockFace getDirection(BlockFace signfacing) {
        return getDirectionLegacy(signfacing, signfacing.getOppositeFace());
    }

    public BlockFace getDirection(BlockFace signfacing, BlockFace cartdirection) {
        switch (this) {
        case NORTH:
            return BlockFace.NORTH;
        case EAST:
            return BlockFace.EAST;
        case SOUTH:
            return BlockFace.SOUTH;
        case WEST:
            return BlockFace.WEST;
        case DOWN:
            return BlockFace.DOWN;
        case UP:
            return BlockFace.UP;
        case LEFT:
        case IMPLICIT_LEFT:
            return FaceUtil.rotate(signfacing, 2);
        case RIGHT:
        case IMPLICIT_RIGHT:
            return FaceUtil.rotate(signfacing, -2);
        case FORWARD:
            return signfacing.getOppositeFace();
        case BACKWARD:
            return signfacing;
        case CONTINUE:
            return cartdirection;
        case REVERSE:
            return cartdirection.getOppositeFace();
        default:
            return cartdirection;
        }
    }

    // Was changed so that FORWARD/BACKWARD is distinct from CONTINUE/REVERSE
    // For this reason to remain backwards-supported with existing signs, this one is kept
    public BlockFace getDirectionLegacy(BlockFace signfacing, BlockFace cartdirection) {
        switch (this) {
            case NORTH:
                return BlockFace.NORTH;
            case EAST:
                return BlockFace.EAST;
            case SOUTH:
                return BlockFace.SOUTH;
            case WEST:
                return BlockFace.WEST;
            case DOWN:
                return BlockFace.DOWN;
            case UP:
                return BlockFace.UP;
            case LEFT:
            case IMPLICIT_LEFT:
                return FaceUtil.rotate(signfacing, 2);
            case RIGHT:
            case IMPLICIT_RIGHT:
                return FaceUtil.rotate(signfacing, -2);
            case CONTINUE:
            case FORWARD:
                return cartdirection;
            case REVERSE:
            case BACKWARD:
                return cartdirection.getOppositeFace();
            default:
                return cartdirection;
        }
    }

    public boolean match(char character) {
        for (String alias : this.aliases) {
            if (alias.length() == 1 && alias.charAt(0) == character) {
                return true;
            }
        }
        return false;
    }

    public boolean match(String text) {
        for (String alias : this.aliases) {
            if (alias.equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    public static Direction parse(char character) {
        for (Direction dir : values()) {
            if (dir.match(character)) return dir;
        }
        return NONE;
    }

    public static Direction parse(String text) {
        for (Direction dir : values()) {
            if (dir.match(text)) return dir;
        }
        return NONE;
    }

    public static Direction fromFace(BlockFace face) {
        switch (face) {
            case NORTH:
                return NORTH;
            case EAST:
                return EAST;
            case SOUTH:
                return SOUTH;
            case WEST:
                return WEST;
            case UP:
                return UP;
            case DOWN:
                return DOWN;
            case SELF:
                return CONTINUE;
            default:
                return NONE;
        }
    }

    public static Direction[] parseAll(String text) {
        if (text.equalsIgnoreCase("all") || text.equals("*")) {
            Direction[] dirs = new Direction[FaceUtil.BLOCK_SIDES.length];
            for (int i = 0; i < dirs.length; i++) {
                dirs[i] = fromFace(FaceUtil.BLOCK_SIDES[i]);
            }
            return dirs;
        } else {
            LinkedHashSet<Direction> faces = new LinkedHashSet<>();
            Direction dir = Direction.parse(text);
            if (dir == Direction.NONE) {
                for (char c : text.toCharArray()) {
                    dir = Direction.parse(c);
                    if (dir == Direction.NONE) {
                        return new Direction[0];
                    } else {
                        faces.add(dir);
                    }
                }
            } else {
                faces.add(dir);
            }
            return faces.toArray(new Direction[0]);
        }
    }

    public static BlockFace[] parseAll(String text, BlockFace absoluteDirection) {
        Direction[] dirs = parseAll(text);
        BlockFace[] faces = new BlockFace[dirs.length];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = dirs[i].getDirection(absoluteDirection);
        }
        return faces;
    }

}
