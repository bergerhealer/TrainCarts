package com.bergerkiller.bukkit.tc.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Caches and searches for signs below rails blocks in the order in which they should be executed.
 */
public class RailSignCache {
    private static final Material WALL_SIGN_TYPE = getMaterial("LEGACY_WALL_SIGN");
    private static final Material SIGN_POST_TYPE = getMaterial("LEGACY_SIGN_POST");
    private static BlockFace[] SIGN_FACES_ORDERED = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};
    private static final TrackedSign[] EMPTY_SIGNS = new TrackedSign[0];
    private static final HashMap<RailPiece, CachedRailSignList> cachedRailSigns = new HashMap<RailPiece, CachedRailSignList>();
    private static final List<Block> signListCache = new ArrayList<Block>();

    /**
     * Gets all the cached rail signs available at a particular block position
     * 
     * @param railType
     * @param railBlock
     * @return array of valid rails at a rails block position
     */
    public static TrackedSign[] getSigns(RailType railType, Block railBlock) {
        return getSigns(RailPiece.create(railType, railBlock));
    }

    /**
     * Gets all the cached rail signs available at a particular block position
     * 
     * @param rail The rail to find signs at
     * @return array of valid rails at a rails block position
     */
    public static TrackedSign[] getSigns(RailPiece rail) {
        CachedRailSignList list = cachedRailSigns.computeIfAbsent(rail,
                key -> new CachedRailSignList(discoverSigns(key)));

        // If more than a tick old, verify the signs, and re-discover if incorrect
        if (list.life > 0) {
            if (verifySigns(list.signs)) {
                list.life = 0;
            } else {
                list = new CachedRailSignList(discoverSigns(rail));
                cachedRailSigns.put(rail, list);
            }
        }

        return list.signs;
    }

    /**
     * Discovers the signs belonging to a particular rail.
     * Unlike {@link #getSigns(railType, railBlock)} this method does not look
     * the information up from a cache
     * 
     * @param railType of the rail
     * @param railBlock of the rail
     * @return signs belonging to this rail
     */
    public static TrackedSign[] discoverSigns(RailType railType, Block railBlock) {
        return discoverSigns(RailPiece.create(railType, railBlock));
    }

    /**
     * Discovers the signs belonging to a particular rail.
     * Unlike {@link #getSigns(rail)} this method does not look
     * the information up from a cache
     * 
     * @param railType of the rail
     * @param railBlock of the rail
     * @return signs belonging to this rail
     */
    public static TrackedSign[] discoverSigns(RailPiece rail) {
        Block columnStart = rail.type().getSignColumnStart(rail.block());
        if (columnStart == null) {
            return EMPTY_SIGNS;
        }

        BlockFace direction = rail.type().getSignColumnDirection(rail.block());
        if (direction == null || direction == BlockFace.SELF) {
            return EMPTY_SIGNS;
        }

        // Compute signs. Do check that the sign search input params are correct.
        TrackedSign[] signs = EMPTY_SIGNS;
        try {
            addSignsFromRails(signListCache, columnStart, direction);
            if (!signListCache.isEmpty()) {
                signs = new TrackedSign[signListCache.size()];
                for (int i = 0; i < signs.length; i++) {
                    signs[i] = new TrackedSign(signListCache.get(i), rail);
                }
            }
        } finally {
            signListCache.clear();
        }

        return signs;
    }

    public static boolean verifySigns(TrackedSign[] signs) {
        for (TrackedSign sign : signs) {
            if (!BlockUtil.ISSIGN.get(sign.signBlock)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the rails type and rails block that are linked with a given sign block
     * 
     * @param signblock
     * @return rails piece information, NONE if the sign has no rails (rail block is null)
     */
    public static RailPiece getRailsFromSign(Block signblock) {
        if (signblock == null) {
            return RailPiece.NONE;
        }

        BlockData signblock_data = WorldUtil.getBlockData(signblock);
        final Block mainBlock;
        if (signblock_data.isType(WALL_SIGN_TYPE)) {
            mainBlock = BlockUtil.getAttachedBlock(signblock);
        } else if (signblock_data.isType(SIGN_POST_TYPE)) {
            mainBlock = signblock;
        } else {
            return RailPiece.NONE;
        }

        // Check main block IS rails itself
        RailType railType = RailType.getType(mainBlock);
        if (railType != RailType.NONE) {
            return RailPiece.create(railType, mainBlock);
        }

        // Look further in all 6 possible directions
        boolean hasSigns;
        for (BlockFace dir : SIGN_FACES_ORDERED) {
            Block block = mainBlock;
            hasSigns = true;
            while (true) {
                // Go to the next block
                block = block.getRelative(dir);

                // Check for rails
                railType = RailType.getType(block);
                BlockFace columnDir = railType.getSignColumnDirection(block);
                if (dir == columnDir.getOppositeFace()) {
                    return RailPiece.create(railType, block);
                }

                // End of the loop?
                if (!hasSigns) {
                    break;
                }

                // Go to the next block
                hasSigns = Util.hasAttachedSigns(block);
            }
        }
        return RailPiece.NONE;
    }

    // removes all cached signs, forcing a global recalculation
    public static void reset() {
        cachedRailSigns.clear();
        RailPieceCache.resetSigns();
    }

    // cleans up cached rail sign lists that haven't been accessed in quite a while
    public static void cleanup() {
        Iterator<CachedRailSignList> iter = cachedRailSigns.values().iterator();
        while (iter.hasNext()) {
            if (++iter.next().life > 20) {
                iter.remove();
            }
        }
    }

    private static void addSignsFromRails(List<Block> rval, Block startBlock, BlockFace signDirection) {
        final boolean hasSignPost = FaceUtil.isVertical(signDirection);
        Block currentBlock = startBlock;
        int offsetCtr = 0;
        while (true) {
            if (hasSignPost && MaterialUtil.isType(currentBlock, SIGN_POST_TYPE)) {
                // Found a sign post - add it and continue
                rval.add(currentBlock);
            } else if (addAttachedSigns(currentBlock, rval)) {
                // Found one or more signs attached to the current block - continue
            } else if (offsetCtr > 1) {
                // No signs found here. If this is too far down, stop.
                break;
            }

            currentBlock = currentBlock.getRelative(signDirection);
            offsetCtr++;
        }
    }

    private static boolean addAttachedSigns(final Block middle, final Collection<Block> rval) {
        boolean found = false;
        for (BlockFace face : FaceUtil.AXIS) {
            Block b = middle.getRelative(face);
            if (MaterialUtil.ISSIGN.get(b) && BlockUtil.getAttachedFace(b) == face.getOppositeFace()) {
                found = true;
                rval.add(b);
            }
        }
        return found;
    }

    /**
     * A single sign that is tracked
     */
    public static class TrackedSign {
        public final Sign sign;
        public final Block signBlock;
        public final RailPiece rail;
        public final RailType railType;
        public final Block railBlock;

        public TrackedSign(Block signBlock, RailPiece rail) {
            this.sign = BlockUtil.getSign(signBlock);
            this.signBlock = signBlock;
            this.rail = rail;
            this.railType = rail.type();
            this.railBlock = rail.block();
        }

        @Override
        public int hashCode() {
            return this.signBlock.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return ((TrackedSign) o).signBlock.equals(this.signBlock);
        }
    }

    private static final class CachedRailSignList {
        public final TrackedSign[] signs;
        public int life; // for automatic purging

        public CachedRailSignList(TrackedSign[] signs) {
            this.signs = signs;
            this.life = 0;
        }
    }
}
