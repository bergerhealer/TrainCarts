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
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Caches and searches for signs below rails blocks in the order in which they should be executed.
 */
public class RailSignCache {
    private static final Material WALL_SIGN_TYPE = getMaterial("LEGACY_WALL_SIGN");
    private static final Material SIGN_POST_TYPE = getMaterial("LEGACY_SIGN_POST");
    private static BlockFace[] SIGN_FACES_ORDERED = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};
    private static final TrackedSign[] EMPTY_SIGNS = new TrackedSign[0];
    private static final HashMap<CachedRailKey, CachedRailSignList> cachedRailSigns = new HashMap<CachedRailKey, CachedRailSignList>();
    private static final List<Block> signListCache = new ArrayList<Block>();

    /**
     * Gets all the cached rail signs available at a particular block position
     * 
     * @param railType
     * @param railBlock
     * @return array of valid rails at a rails block position
     */
    public static TrackedSign[] getSigns(RailType railType, Block railBlock) {
        CachedRailKey key = new CachedRailKey(railType, railBlock);
        CachedRailSignList cached = cachedRailSigns.get(key);
        if (cached != null && cached.life > 0) {
            // Verify all the signs mentioned are still there
            // If still valid, reset life, otherwise regenerate
            if (verifySigns(cached.signs)) {
                cached.life = 0;
            } else {
                cached = null;
            }
        }

        // Regenerate if required
        if (cached == null) {
            cached = new CachedRailSignList(discoverSigns(key.railType, key.railBlock));
            cachedRailSigns.put(key, cached);
        }

        return cached.signs;
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
        Block columnStart = railType.getSignColumnStart(railBlock);
        BlockFace direction = railType.getSignColumnDirection(railBlock);

        // Compute signs. Do check that the sign search input params are correct.
        TrackedSign[] signs = EMPTY_SIGNS;
        if (columnStart != null && direction != BlockFace.SELF && direction != null) {
            try {
                addSignsFromRails(signListCache, columnStart, direction);
                if (!signListCache.isEmpty()) {
                    signs = new TrackedSign[signListCache.size()];
                    for (int i = 0; i < signs.length; i++) {
                        signs[i] = new TrackedSign(signListCache.get(i), railType, railBlock);
                    }
                }
            } finally {
                signListCache.clear();
            }
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

    public static Block getRailsFromSign(Block signblock) {
        if (signblock == null) {
            return null;
        }

        BlockData signblock_data = WorldUtil.getBlockData(signblock);
        final Block mainBlock;
        if (signblock_data.isType(WALL_SIGN_TYPE)) {
            mainBlock = BlockUtil.getAttachedBlock(signblock);
        } else if (signblock_data.isType(SIGN_POST_TYPE)) {
            mainBlock = signblock;
        } else {
            return null;
        }

        // Check main block IS rails itself
        if (RailType.getType(mainBlock) != RailType.NONE) {
            return mainBlock;
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
                BlockFace columnDir = RailType.getType(block).getSignColumnDirection(block);
                if (dir == columnDir.getOppositeFace()) {
                    return block;
                }

                // End of the loop?
                if (!hasSigns) {
                    break;
                }

                // Go to the next block
                hasSigns = Util.hasAttachedSigns(block);
            }
        }
        return null;
    }

    // removes all cached signs, forcing a global recalculation
    public static void reset() {
        cachedRailSigns.clear();
        RailTypeCache.resetSigns();
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
        public final RailType railType;
        public final Block railBlock;

        public TrackedSign(Block signBlock, RailType railType, Block railBlock) {
            this.sign = BlockUtil.getSign(signBlock);
            this.signBlock = signBlock;
            this.railType = railType;
            this.railBlock = railBlock;
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

    private static final class CachedRailKey {
        public final RailType railType;
        public final Block railBlock;

        public CachedRailKey(RailType railType, Block railBlock) {
            this.railType = railType;
            this.railBlock = railBlock;
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = hash * 31 + System.identityHashCode(this.railBlock.getWorld());
            hash = hash * 31 + this.railBlock.getX();
            hash = hash * 31 + this.railBlock.getY();
            hash = hash * 31 + this.railBlock.getZ();
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            CachedRailKey other = (CachedRailKey) o;
            return this.railBlock.getWorld() == other.railBlock.getWorld() &&
                   this.railBlock.getX() == other.railBlock.getX() &&
                   this.railBlock.getY() == other.railBlock.getY() &&
                   this.railBlock.getZ() == other.railBlock.getZ() &&
                   this.railType == other.railType;
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
