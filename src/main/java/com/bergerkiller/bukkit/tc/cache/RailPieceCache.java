package com.bergerkiller.bukkit.tc.cache;

import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.RailLookup;

/**
 * Caches {@link com.bergerkiller.bukkit.tc.controller.components.RailPiece RailPiece} information for
 * positions in the world.
 *
 * @deprecated Consolidated into {@link RailLookup}
 */
@Deprecated
public class RailPieceCache {

    /**
     * Gets all the cached rail pieces available at the position in a rail state
     * 
     * @param state
     * @return list of valid rails at the state's block position
     */
    public static RailPiece[] find(RailState state) {
        return RailLookup.findAtBlockPosition(state.positionOfflineBlock());
    }

    /**
     * Gets all the cached rail pieces available at a particular block position
     * 
     * @param posBlock
     * @return list of valid rails at a block position
     */
    public static RailPiece[] find(OfflineBlock block) {
        return RailLookup.findAtBlockPosition(block);
    }

    // removes all cached rails, forcing a global recalculation
    public static void reset() {
        RailLookup.forceRecalculation();
    }
}
