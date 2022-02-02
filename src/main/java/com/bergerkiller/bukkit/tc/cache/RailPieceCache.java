package com.bergerkiller.bukkit.tc.cache;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Caches {@link com.bergerkiller.bukkit.tc.controller.components.RailPiece RailPiece} information for
 * positions in the world.
 */
public class RailPieceCache {
    private static final RailPiece[] EMPTY_INFO = new RailPiece[0];
    private static final Value NO_VALUE = new Value(EMPTY_INFO, 0);
    private static final Map<OfflineBlock, Value> cache = new HashMap<OfflineBlock, Value>();
    private static int lifeTimer = 0;

    /**
     * Removes rail pieces cached at a block position in the world
     * 
     * @param block
     */
    public static void removeAtPosition(OfflineBlock block) {
        cache.remove(block);
    }

    /**
     * Gets all the cached rail pieces available at the position in a rail state
     * 
     * @param state
     * @return list of valid rails at the state's block position
     */
    public static RailPiece[] find(RailState state) {
        return find(state.positionOfflineBlock());
    }

    /**
     * Gets all the cached rail pieces available at a particular block position
     * 
     * @param posBlock
     * @return list of valid rails at a block position
     */
    public static RailPiece[] find(OfflineBlock block) {
        Value cached = cache.getOrDefault(block, NO_VALUE);

        // Verify if needed
        int currLifeTimer = lifeTimer;
        if (cached.life < currLifeTimer) {
            // Verify that all stored rails types are actually still valid (the rails exists)
            // It is incredibly rare that the rails stops existing, so make this as fast as possible!
            // Hence we use an array instead of a list because why not?
            // If we detect a single rail being missing, invalidate the entire cache for that block
            for (RailPiece info : cached.info) {
                try {
                    // Verify rail exists
                    if (!info.type().isRail(info.block())) {
                        cache.remove(block);
                        return EMPTY_INFO; // Invalid
                    }

                    // Verify signs exist
                    info.verifySigns();
                } catch (Throwable t) {
                    cache.remove(block);
                    RailType.handleCriticalError(info.type(), t);
                    return EMPTY_INFO; // Error
                }
            }

            // Reset life so that we don't do check upon the next invocation.
            cached.life = currLifeTimer;
        }

        return cached.info;
    }

    public static void storeInfo(OfflineBlock block, RailPiece[] info) {
        cache.put(block, new Value(info, lifeTimer));
    }

    // removes all cached rails, forcing a global recalculation
    public static void reset() {
        cache.clear();
    }

    /**
     * Erases cached sign information for all cached rails.
     * Called from {@link RailSignCache#reset()}
     */
    protected static void resetSigns() {
        for (Value value : cache.values()) {
            for (RailPiece info : value.info) {
                info.refreshSigns();
            }
        }
    }

    // cleans up cached rail types that haven't been accessed in quite a while
    public static void update() {
        int dead = lifeTimer - 20;
        Iterator<Value> iter = cache.values().iterator();
        while (iter.hasNext()) {
            if (iter.next().life < dead) {
                iter.remove();
            }
        }

        lifeTimer++;
        NO_VALUE.life = lifeTimer;
    }

    // Value object used in the hashmap, mapped by block, storing all rail information
    private static final class Value {
        public final RailPiece[] info;
        public int life; // for automatic purging

        public Value(RailPiece[] info, int life) {
            this.info = info;
            this.life = life;
        }
    }
}
