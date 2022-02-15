package com.bergerkiller.bukkit.tc.cache;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Caches and searches for signs below rails blocks in the order in which they should be executed.
 * 
 * @deprecated Consolidated into {@link RailLookup} and {@link RailPiece#signs()}
 */
@Deprecated
public class RailSignCache {

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
        return rail.signs();
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
        return RailLookup.discoverSignsAtRailPiece(rail);
    }

    public static boolean verifySigns(TrackedSign[] signs) {
        for (TrackedSign sign : signs) {
            if (!sign.verify()) {
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
        return RailLookup.discoverRailPieceFromSign(signblock);
    }
}
