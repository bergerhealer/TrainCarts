package com.bergerkiller.bukkit.tc.cache;

import java.util.HashMap;
import java.util.Iterator;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.RailInfo;

/**
 * Caches rail types found at Minecart positions to improve performance when walking tracks
 */
public class RailTypeCache {
    private static final RailInfo[] EMPTY_INFO = new RailInfo[0];
    private static final HashMap<Block, CachedRailType> cachedRailTypes = new HashMap<Block, CachedRailType>();

    public static void removeInfo(Block posBlock) {
        cachedRailTypes.remove(posBlock);
    }

    /**
     * Gets all the cached rail informations available at a particular block position
     * 
     * @param posBlock
     * @return list of valid rails at a block position
     */
    public static RailInfo[] getInfo(Block posBlock) {
        CachedRailType cached = cachedRailTypes.get(posBlock);
        if (cached == null) {
            return EMPTY_INFO; // No rails
        }

        // Verify if needed
        if (cached.life > 0) {
            // Verify that all stored rails types are actually still valid (the rails exists)
            // It is incredibly rare that the rails stops existing, so make this as fast as possible!
            // Hence we use an array instead of a list because why not?
            // If we detect a single rail being missing, invalidate the entire cache for that block
            for (int i = 0; i < cached.info.length; i++) {
                RailInfo info = cached.info[i];
                try {
                    if (!info.railType.isRail(info.railBlock)) {
                        removeInfo(posBlock);
                        return EMPTY_INFO; // Invalid
                    }
                } catch (Throwable t) {
                    removeInfo(posBlock);
                    RailType.handleCriticalError(info.railType, t);
                    return EMPTY_INFO; // Error
                }
            }

            // Still good. Reset life to 0 so that we don't do this check upon the next invocation.
            cached.life = 0;
        }

        return cached.info;
    }

    @Deprecated
    public static RailInfo getInfoOld(Block posBlock) {
        RailInfo[] info = getInfo(posBlock);
        return (info.length > 0) ? info[0] : null;
    }

    public static void storeInfo(Block block, RailInfo[] info) {
        cachedRailTypes.put(block, new CachedRailType(info));
    }

    // removes all cached rails, forcing a global recalculation
    public static void reset() {
        cachedRailTypes.clear();
    }

    // cleans up cached rail types that haven't been accessed in quite a while
    public static void cleanup() {
        Iterator<CachedRailType> iter = cachedRailTypes.values().iterator();
        while (iter.hasNext()) {
            if (++iter.next().life > 20) {
                iter.remove();
            }
        }
    }

    private static final class CachedRailType {
        public final RailInfo[] info;
        public int life; // for automatic purging

        public CachedRailType(RailInfo[] info) {
            this.info = info;
            this.life = 0;
        }
    }
}
