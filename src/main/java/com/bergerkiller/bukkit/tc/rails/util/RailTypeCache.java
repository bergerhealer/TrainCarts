package com.bergerkiller.bukkit.tc.rails.util;

import java.util.HashMap;
import java.util.Iterator;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.RailInfo;

/**
 * Caches rail types found at Minecart positions to improve performance when walking tracks
 */
public class RailTypeCache {
    private static final HashMap<Block, CachedRailType> cachedRailTypes = new HashMap<Block, CachedRailType>();

    public static void removeInfo(Block posBlock) {
        cachedRailTypes.remove(posBlock);
    }

    public static RailInfo getInfo(Block posBlock) {
        CachedRailType cached = cachedRailTypes.get(posBlock);
        if (cached != null) {
            // Verify if needed
            if (cached.life > 0) {
                try {
                    if (cached.info.railType.isRail(cached.info.railBlock)) {
                        cached.life = 0;
                    } else {
                        removeInfo(posBlock);
                        return null; // Invalid!
                    }
                } catch (Throwable t) {
                    removeInfo(posBlock);
                    RailType.handleCriticalError(cached.info.railType, t);
                    return null; // Error!
                }
            }
            return cached.info;
        }
        return null;
    }

    public static void storeInfo(Block block, RailInfo info) {
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
        public final RailInfo info;
        public int life; // for automatic purging

        public CachedRailType(RailInfo info) {
            this.info = info;
            this.life = 0;
        }
    }
}
