package com.bergerkiller.bukkit.tc.cache;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.block.Block;

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
    private static final LookupKey LOOKUP_KEY = new LookupKey();
    private static final RailPiece[] EMPTY_INFO = new RailPiece[0];
    private static final Map<Key, Value> cache = new HashMap<Key, Value>();
    private static int lifeTimer = 0;

    /**
     * Removes rail pieces cached at a block position in the world
     * 
     * @param blockPosition
     */
    public static void removeAtPosition(Block blockPosition) {
        try (LookupKey key = LOOKUP_KEY.lock(blockPosition)) {
            cache.remove(key);
        }
    }

    /**
     * Gets all the cached rail pieces available at the position in a rail state
     * 
     * @param state
     * @return list of valid rails at the state's block position
     */
    public static RailPiece[] find(RailState state) {
        try (LookupKey posBlockKey = LOOKUP_KEY.lock(state)) {
            return lookupInfo(posBlockKey);
        }
    }

    /**
     * Gets all the cached rail pieces available at a particular block position
     * 
     * @param posBlock
     * @return list of valid rails at a block position
     */
    public static RailPiece[] find(Block blockPosition) {
        try (LookupKey posBlockKey = LOOKUP_KEY.lock(blockPosition)) {
            return lookupInfo(posBlockKey);
        }
    }

    private static RailPiece[] lookupInfo(LookupKey key) {
        Value cached = cache.get(key);
        if (cached == null) {
            return EMPTY_INFO; // No rails
        }

        // Verify if needed
        if (cached.life < lifeTimer) {
            // Verify that all stored rails types are actually still valid (the rails exists)
            // It is incredibly rare that the rails stops existing, so make this as fast as possible!
            // Hence we use an array instead of a list because why not?
            // If we detect a single rail being missing, invalidate the entire cache for that block
            for (int i = 0; i < cached.info.length; i++) {
                RailPiece info = cached.info[i];
                try {
                    // Verify rail exists
                    if (!info.type().isRail(info.block())) {
                        cache.remove(key);
                        return EMPTY_INFO; // Invalid
                    }

                    // Verify signs exist
                    info.verifySigns();
                } catch (Throwable t) {
                    cache.remove(key);
                    RailType.handleCriticalError(info.type(), t);
                    return EMPTY_INFO; // Error
                }
            }

            // Still good. Reset life so that we don't do this check upon the next invocation.
            cached.life = lifeTimer;
        }

        return cached.info;
    }

    public static void storeInfo(Block block, RailPiece[] info) {
        cache.put(new Key(block), new Value(info, lifeTimer));
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

    // Used for get/remove operations, to avoid creating a new key
    private static final class LookupKey extends Key implements AutoCloseable {
        private boolean locked;

        public LookupKey() {
            super(null, 0, 0, 0);
            this.locked = false;
        }

        /**
         * Locks the lookup key with a new lookup value.
         * The returned key can be used for lookup.
         * If this key was already locked, another key is used instead.
         * 
         * @param state
         * @return key
         */
        public LookupKey lock(RailState state) {
            RailPath.Position railPosition = state.position();
            if (railPosition.relative) {
                Block railBlock = state.railBlock();
                return lock(railBlock.getWorld(),
                        railBlock.getX() + MathUtil.floor(railPosition.posX),
                        railBlock.getY() + MathUtil.floor(railPosition.posY),
                        railBlock.getZ() + MathUtil.floor(railPosition.posZ));
            } else {
                return lock(state.railWorld(),
                        MathUtil.floor(railPosition.posX),
                        MathUtil.floor(railPosition.posY),
                        MathUtil.floor(railPosition.posZ));
            }
        }

        /**
         * Locks the lookup key with a new lookup value.
         * The returned key can be used for lookup.
         * If this key was already locked, another key is used instead.
         * 
         * @param block
         * @return key
         */
        public LookupKey lock(Block block) {
            return lock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }

        /**
         * Locks the lookup key with a new lookup value.
         * The returned key can be used for lookup.
         * If this key was already locked, another key is used instead.
         * 
         * @param world of the block
         * @param x - coordinate of the block
         * @param y - coordinate of the block
         * @param z - coordinate of the block
         * @return key
         */
        public LookupKey lock(World world, int x, int y, int z) {
            if (this.locked) {
                LookupKey key = new LookupKey();
                key.locked = true;
                key.load(world, x, y, z);
                return key;
            } else {
                this.locked = true;
                this.load(world, x, y, z);
                return this;
            }
        }

        @Override
        public void close() {
            this.locked = false;
        }
    }

    // Key object used in the hashmap for mapping by Block
    private static class Key implements Cloneable {
        public World world;
        public int x, y, z;

        public Key(Block block) {
            this(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }

        public Key(World world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public final void load(World world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public final int hashCode() {
            int hash = 17;
            hash = hash * 31 + System.identityHashCode(world);
            hash = hash * 31 + x;
            hash = hash * 31 + y;
            hash = hash * 31 + z;
            return hash;
        }

        @Override
        public final boolean equals(Object o) {
            Key other = (Key) o;
            return this.x == other.x &&
                   this.y == other.y &&
                   this.z == other.z &&
                   this.world == other.world;
        }

        @Override
        public final Key clone() {
            return new Key(this.world, this.x, this.y, this.z);
        }
    }
}
