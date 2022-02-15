package com.bergerkiller.bukkit.tc.cache;

import java.util.Collection;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.RailLookup;

/**
 * Cache that tracks what rail blocks trains are occupying, allowing for quick
 * retrieval of train information that are on a particular rails block. This is used
 * for wait distance functionality and when spawning trains. It is also used when
 * a sign is activated by redstone, and the train that is on the rail coupled by it
 * needs to be found.
 *
 * @deprecated Consolidated into {@link RailLookup} and {@link RailPiece#members()}
 */
@Deprecated
public class RailMemberCache {

    /**
     * Finds the minecart that is on a particular rail block
     * 
     * @param railBlock
     * @return member on this rail, null if none is on it
     */
    public static MinecartMember<?> find(OfflineBlock railBlock) {
        Collection<MinecartMember<?>> members = RailLookup.findMembersOnRail(railBlock);
        if (members.isEmpty()) {
            return null;
        } else {
            return members.iterator().next();
        }
    }

    /**
     * @deprecated Use {@link #find(OfflineBlock)} instead
     */
    @Deprecated
    public static MinecartMember<?> find(Block railBlock) {
        return find(OfflineBlock.of(railBlock));
    }

    /**
     * Finds all minecarts traveling on a particular rail block
     * 
     * @param railBlock
     * @return members on this rail
     */
    public static Collection<MinecartMember<?>> findAll(OfflineBlock railBlock) {
        return RailLookup.findMembersOnRail(railBlock);
    }

    /**
     * @deprecated Use {@link #findAll(OfflineBlock)} instead
     */
    @Deprecated
    public static Collection<MinecartMember<?>> findAll(Block railBlock) {
        return findAll(OfflineBlock.of(railBlock));
    }

    /**
     * Removes all existant entries to a particular minecart
     * 
     * @param member value to remove
     */
    public static void remove(MinecartMember<?> member) {
        RailLookup.removeMemberFromAll(member);
    }
}
