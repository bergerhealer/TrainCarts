package com.bergerkiller.bukkit.tc.utils;

import java.util.function.LongUnaryOperator;

import org.bukkit.block.BlockFace;

/**
 * Maps Block coordinates to longs, similar to how Minecraft does that now.
 * Does not work for Y-ranges beyond 4096. Keys should not be decoded, store such information
 * in the value itself so that this wrapping, if it causes problems, causes less harm.
 */
public class LongBlockCoordinates {
    // From Minecraft MathHelper / BlockPosition
    //private static final int PACKED_X_LENGTH = 1 + MathHelper.log2(MathHelper.smallestEncompassingPowerOfTwo(30000000));
    public static final int PACKED_X_LENGTH = 26;
    public static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
    public static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
    public static final long PACKED_X_MASK = (1L << PACKED_X_LENGTH) - 1L;
    public static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
    public static final long PACKED_Z_MASK = (1L << PACKED_Z_LENGTH) - 1L;
    public static final int Y_OFFSET = 0;
    public static final int Z_OFFSET = PACKED_Y_LENGTH;
    public static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

    /**
     * Creates a long key to refer to the specified x/y/z coordinates
     *
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param z Z-coordinate
     * @return Mapped long key
     */
    public static long map(int x, int y, int z) {
        long l = 0L;

        l |= ((long) x & PACKED_X_MASK) << X_OFFSET;
        l |= ((long) y & PACKED_Y_MASK) << Y_OFFSET;
        l |= ((long) z & PACKED_Z_MASK) << Z_OFFSET;
        return l;
    }

    /**
     * Shifts a long key coordinate one block east (x+1)
     *
     * @param key Key to shift
     * @return Shifted key
     */
    public static long shiftEast(long key) {
        return (key & ~(PACKED_X_MASK << X_OFFSET)) | ((key + (1L << X_OFFSET)) & (PACKED_X_MASK << X_OFFSET));
    }

    /**
     * Shifts a long key coordinate one block west (x-1)
     *
     * @param key Key to shift
     * @return Shifted key
     */
    public static long shiftWest(long key) {
        return (key & ~(PACKED_X_MASK << X_OFFSET)) | ((key - (1L << X_OFFSET)) & (PACKED_X_MASK << X_OFFSET));
    }

    /**
     * Shifts a long key coordinate one block up (y+1)
     *
     * @param key Key to shift
     * @return Shifted key
     */
    public static long shiftUp(long key) {
        return (key & ~(PACKED_Y_MASK << Y_OFFSET)) | ((key + (1L << Y_OFFSET)) & (PACKED_Y_MASK << Y_OFFSET));
    }

    /**
     * Shifts a long key coordinate one block down (y-1)
     *
     * @param key Key to shift
     * @return Shifted key
     */
    public static long shiftDown(long key) {
        return (key & ~(PACKED_Y_MASK << Y_OFFSET)) | ((key - (1L << Y_OFFSET)) & (PACKED_Y_MASK << Y_OFFSET));
    }

    /**
     * Shifts a long key coordinate one block south (z+1)
     *
     * @param key Key to shift
     * @return Shifted key
     */
    public static long shiftSouth(long key) {
        return (key & ~(PACKED_Z_MASK << Z_OFFSET)) | ((key + (1L << Z_OFFSET)) & (PACKED_Z_MASK << Z_OFFSET));
    }

    /**
     * Shifts a long key coordinate one block north (z-1)
     *
     * @param key Key to shift
     * @return Shifted key
     */
    public static long shiftNorth(long key) {
        return (key & ~(PACKED_Z_MASK << Z_OFFSET)) | ((key - (1L << Z_OFFSET)) & (PACKED_Z_MASK << Z_OFFSET));
    }

    /**
     * Retrieves the suitable long key shift operator to use for shifting in a particular
     * BlockFace direction
     *
     * @param face
     * @return operator
     */
    public static LongUnaryOperator shiftOperator(BlockFace face) {
        switch (face) {
        case DOWN:   return LongBlockCoordinates::shiftDown;
        case UP:     return LongBlockCoordinates::shiftUp;
        case NORTH:  return LongBlockCoordinates::shiftNorth;
        case EAST:   return LongBlockCoordinates::shiftEast;
        case SOUTH:  return LongBlockCoordinates::shiftSouth;
        case WEST:   return LongBlockCoordinates::shiftWest;
        case SELF:   return LongUnaryOperator.identity();
        default:
            // Fallback
            final BlockFace f = face;
            return key -> map(getX(key) + f.getModX(), getY(key) + f.getModY(), getZ(key) + f.getModZ());
        }
    }

    /**
     * Finds the BlockFace offset to translate from one long block coordinate to another.
     * The two coordinates must be neighbouring or the same value, otherwise an exception
     * is thrown.
     *
     * @param from Long block coordinates
     * @param to Long block coordinates
     * @return Direction, null if from and to are not neighbours
     */
    public static BlockFace findDirection(long from, long to) {
        // Subtracts the masked portions, and encodes it as a single long value
        long diff = (to - (from & (PACKED_X_MASK << X_OFFSET)) & (PACKED_X_MASK << X_OFFSET)) |
                    (to - (from & (PACKED_Y_MASK << Y_OFFSET)) & (PACKED_Y_MASK << Y_OFFSET)) |
                    (to - (from & (PACKED_Z_MASK << Z_OFFSET)) & (PACKED_Z_MASK << Z_OFFSET));

        // Note: This only looks so awful because Java doesn't allow a switch on a long
        //       It's really just a long row of if-statements, turned into a switch case.
        //       Care was taken that the int downcast value is unique across all 7 choices
        //       https://stackoverflow.com/questions/2676210/why-cant-your-switch-statement-data-type-be-long-java
        switch ((int) (diff ^ (diff >> 32))) {
        case 0:
            if (diff == 0)
                return BlockFace.SELF;
        case (int) ((PACKED_Z_MASK << Z_OFFSET) ^ ((PACKED_Z_MASK << Z_OFFSET) >> 32)):
            if (diff == (PACKED_Z_MASK << Z_OFFSET))
                return BlockFace.NORTH;
        case (int) ((1L << X_OFFSET) ^ ((1L << X_OFFSET) >> 32)):
            if  (diff == (1L << X_OFFSET))
                return BlockFace.EAST;
        case (int) ((1L << Z_OFFSET) ^ ((1L << Z_OFFSET) >> 32)):
            if (diff == (1L << Z_OFFSET))
                return BlockFace.SOUTH;
        case (int) ((PACKED_X_MASK << X_OFFSET) ^ ((PACKED_X_MASK << X_OFFSET) >> 32)):
            if (diff == (PACKED_X_MASK << X_OFFSET))
                return BlockFace.WEST;
        case (int) ((1L << Y_OFFSET) ^ ((1L << Y_OFFSET) >> 32)):
            if (diff == (1L << Y_OFFSET))
                return BlockFace.UP;
        case (int) ((PACKED_Y_MASK << Y_OFFSET) ^ ((PACKED_Y_MASK << Y_OFFSET) >> 32)):
            if (diff == (PACKED_Y_MASK << Y_OFFSET))
                return BlockFace.DOWN;
        default:
            return null;
            //throw new IllegalArgumentException("Blocks not neighbours: " +
            //        "{x=" + getX(from) + ", y=" + getY(from) + ", z=" + getZ(from) + "} and " +
            //        "{x=" + getX(to) + ", y=" + getY(to) + ", z=" + getZ(to) + "}");
        }
    }

    // Efficiently checks whether a long coordinate refers to a block within a chunk,
    // and not one at the edge of the chunk. Blocks that are not at the edge receive
    // some optimizations to skip certain logic on chunk load/unload.
    public static boolean isWithinChunk(long key) {
        long relx = key & (0xFL << X_OFFSET);
        long relz = key & (0xFL << Z_OFFSET);
        return relx != 0L && relx != (0xFL << X_OFFSET) && relz != 0L && relz != (0xFL << Z_OFFSET);
    }

    // Avoid using these if you can
    public static int getX(long i) {
        return (int) (i << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH);
    }

    // Avoid using these if you can
    public static int getY(long i) {
        return (int) (i << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH);
    }

    // Avoid using these if you can
    public static int getZ(long i) {
        return (int) (i << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH);
    }
}
