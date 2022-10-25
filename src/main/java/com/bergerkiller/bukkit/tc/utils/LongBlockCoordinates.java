package com.bergerkiller.bukkit.tc.utils;

import java.util.function.LongUnaryOperator;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;

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
     * @param block
     * @return Mapped long key
     */
    public static long map(Block block) {
        return map(block.getX(), block.getY(), block.getZ());
    }

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
     * Iterates all 6 sides of a block, passing the BlockFace side and the block key
     * for that side to the consumer. The input key is not accepted.
     * There is no guaranteed order of block sides accepted.
     *
     * @param key Center block coordinate key
     * @param consumer Consumer to notify of all 6 sides
     */
    public static void forAllBlockSidesAndSelf(long key, BlockSideConsumer consumer) {
        consumer.accept(BlockFace.SELF, key);
        consumer.accept(BlockFace.NORTH, shiftNorth(key));
        consumer.accept(BlockFace.EAST, shiftEast(key));
        consumer.accept(BlockFace.SOUTH, shiftSouth(key));
        consumer.accept(BlockFace.WEST, shiftWest(key));
        consumer.accept(BlockFace.UP, shiftUp(key));
        consumer.accept(BlockFace.DOWN, shiftDown(key));
    }

    @FunctionalInterface
    public static interface BlockSideConsumer {
        /**
         * Accepts a single block side
         *
         * @param face Face side of the block
         * @param key Block coordinate key
         */
        void accept(BlockFace face, long key);
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

    // Computes the number of blocks between the block itself and the nearest
    // edge block of the chunk. If 0, it is on the edge. If 1, 1 away from the edge, etc.
    public static int getChunkEdgeDistance(long key) {
        int relx = (int) (key >> X_OFFSET) & 0xF;
        int relz = (int) (key >> Z_OFFSET) & 0xF;
        if ((relx & 0x8) != 0) relx = 0xF - relx;
        if ((relz & 0x8) != 0) relz = 0xF - relz;
        return Math.min(relx, relz);
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

    // Avoid using these if you can
    public static IntVector3 get(long i) {
        return new IntVector3(getX(i), getY(i), getZ(i));
    }
}
