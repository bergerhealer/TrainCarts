package com.bergerkiller.bukkit.tc.cache;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Cache that tracks what rail blocks trains are occupying, allowing for quick
 * retrieval of train information that are on a particular rails block. This is used
 * for wait distance functionality and when spawning trains. It is also used when
 * a sign is activated by redstone, and the train that is on the rail coupled by it
 * needs to be found.
 */
public class RailMemberCache {
    private static final ListMultimap<Block, MinecartMember<?>> cache = LinkedListMultimap.create(10000);

    /**
     * Wipes all members stored in the cache
     */
    public static void reset() {
        cache.clear();
    }

    /**
     * Finds the minecart that is on a particular rail block
     * 
     * @param railBlock
     * @return member on this rail, null if none is on it
     */
    public static MinecartMember<?> find(Block railBlock) {
        Collection<MinecartMember<?>> members = cache.get(railBlock);
        if (members.isEmpty()) {
            return null;
        }
        MinecartMember<?> result = members.iterator().next();
        if (result.isUnloaded()) {
            TrainCarts.plugin.log(Level.WARNING, "Purged unloaded minecart from rail cache at " + new IntVector3(railBlock));
            remove(result);
            result = null;
        }
        return result;
    }

    /**
     * Removes all existant entries to a particular minecart
     * 
     * @param member value to remove
     */
    public static void remove(MinecartMember<?> member) {
        Iterator<MinecartMember<?>> iter = cache.values().iterator();
        while (iter.hasNext()) {
            if (iter.next() == member) {
                iter.remove();
            }
        }
    }

    /**
     * Removes a minecart from being bound to a particular rail block
     * 
     * @param railsBlock
     * @param member
     */
    public static void removeBlock(Block railBlock, MinecartMember<?> member) {
        cache.remove(railBlock, member);
    }

    /**
     * Adds a minecart, binding it to a particular rail block
     * 
     * @param railsBlock
     * @param member
     */
    public static void addBlock(Block railBlock, MinecartMember<?> member) {
        cache.put(railBlock, member);
    }

    /**
     * Changes the minecart member that is on a particular rails block.
     * If old and new member are the same, all this does is verify that the member is added.
     * 
     * @param railBlock
     * @param oldMember
     * @param newMember
     */
    public static void changeMember(Block railBlock, MinecartMember<?> oldMember, MinecartMember<?> newMember) {
        List<MinecartMember<?>> members = cache.get(railBlock);
        ListIterator<MinecartMember<?>> iter = members.listIterator();
        while (iter.hasNext()) {
            if (iter.next() == oldMember) {
                if (oldMember != newMember) {
                    iter.set(newMember);
                }
                return;
            }
        }

        // Not yet in it. Add it.
        members.add(newMember);
    }
}
