package com.bergerkiller.bukkit.tc.rails;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Retrieves and caches rails and information about rails, mapped to
 * the minecart and rail positions. The cache eliminates frequent lookups
 * using more expensive discovery methods.<br>
 * <br>
 * Per section of rail tied to rail block and type, the following information is cached:
 * <ul>
 * <li>Rail block and rail type
 * <li>Signs and their detected sign actions activated by them
 * <li>Minecart Members occupying the section of rails
 * </ul>
 * The cache stores information in memory for so long it is accessed, and the
 * cached information is automatically wiped when no longer used. The data is
 * validated every tick, such as by checking signs exist and the rail type still
 * detects the rail block as a valid rail.<br>
 * <br>
 * This lookup is not multi-thread safe and all access must be done from the main
 * Bukkit thread.
 */
public final class RailLookup {
    /** This is incremented every tick to force cached information to re-verify itself */
    private static int lifeTimer = 1;
    /** Stores the (every tick incrementing) future tick when cached information expires */
    private static int verifyTimer = 1;

    private static final Bucket[] NO_RAILS_AT_POSITION = new Bucket[0];
    private static final TrackedSign[] NO_SIGNS = new TrackedSign[0];
    private static final TrackedSign[] MISSING_RAILS_NO_SIGNS = new TrackedSign[0];
    private static final List<MinecartMember<?>> DEFAULT_MEMBER_LIST = Collections.emptyList();
    private static final Material WALL_SIGN_TYPE = getMaterial("LEGACY_WALL_SIGN");
    private static final Material SIGN_POST_TYPE = getMaterial("LEGACY_SIGN_POST");
    private static BlockFace[] SIGN_FACES_ORDERED = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};
    private static final List<Block> SIGN_LIST_CACHE = new ArrayList<Block>();
    private static final HashMap<OfflineBlock, Bucket> cache = new HashMap<>();

    /**
     * Discovers the RailPieces that are active for controlling the movement of a Minecart
     * at the RailState position information specified. The caller should further filter which of
     * these rail pieces truly control the movement, but the returned array is efficiently
     * cached.
     *
     * @param state RailState with position information
     * @return Array of rail pieces active here, or an empty array if there are no rails nearby
     */
    public static RailPiece[] findAtStatePosition(RailState state) {
        return findAtBlockPosition(state.positionOfflineBlock());
    }

    /**
     * Discovers the RailPieces that are active for controlling the movement of a Minecart
     * moving within the Block position specified. The caller should further filter which of
     * these rail pieces truly control the movement, but the returned array is efficiently
     * cached.
     *
     * @param positionBlock Block position
     * @return Array of rail pieces active here, or an empty array if there are no rails nearby
     */
    public static RailPiece[] findAtBlockPosition(OfflineBlock positionBlock) {
        // If already in the cache, compute/return it right-away
        // During computation the original bucket may get deleted (if rail type was NONE)
        Bucket inCache = cache.get(positionBlock);
        if (inCache != null) {
            return inCache.getRailsAtPosition();
        }

        // We need to create a new bucket at this position. While we could initialize one
        // with rail type NONE and proceed from there, it results in a bucket to be created
        // that is then just thrown away again. It's better to do an at-position search first,
        // and if any of the found rails match with the position block, we use that one.
        return Bucket.discoverAtPositionBlock(positionBlock);
    }

    /**
     * API Note: you should never have to call this function. It's used internally by RailPiece.<br>
     * <br>
     * Looks up cached rail information about the provided rail block. If needed a new entry
     * is created in the cache to store the cached information, so that members or signs at this
     * block can be found. This will also succeed when the rail type doesn't exist at this block.
     *
     * @param railOfflineBlock Rail offline block, key
     * @param railBlock Bukkit Block version of railOfflineBlock
     * @param railType Rail type
     * @return Rail piece information backed by this lookup cache. The information is verified.
     */
    public static CachedRailPiece lookupCachedRailPiece(final OfflineBlock railOfflineBlock,
                                                        final Block railBlock,
                                                        final RailType railType
    ) {
        return lookupRailBucket(railOfflineBlock, railBlock, railType);
    }

    private static Bucket lookupRailBucket(final OfflineBlock railOfflineBlock,
                                           final Block railBlock,
                                           final RailType railType
    ) {
        // First try to find it in the cache, and if none exists, initialize a new one.
        Bucket inCache = cache.computeIfAbsent(railOfflineBlock, b -> new Bucket(b, railBlock, railType));
        RailType inCacheType = inCache.type();

        // If the rail type of the one in cache is 'NONE', it most likely was initialized before
        // the rail was found as a valid rail type. For performance reasons it's better to
        // yeet this bucket out, as it's unlikely to be used again.
        // Because of this logic the inCache one will also never store a 'next' chain.
        //
        // In other cases, entries are added to the 'next' chain to represent them, including
        // NONE if this is required.
        if (inCacheType == railType) {
            return inCache;
        } else if (inCacheType == RailType.NONE) {
            return inCache.swapOutNoneType(railType);
        } else {
            return inCache.findOrAppendToChain(railType);
        }
    }

    /**
     * Gets a List of members that are driving on a particular rail block. If there are no
     * members driving on the Block, an empty list is returned. The returned list is
     * unmodifiable.<br>
     * <br>
     * <b>Important note: </b>This is not very reliable, because the same rail block could be
     * controlling multiple rail types. It is more reliable to use {@link RailPiece#members()}
     *
     * @param railOfflineBlock
     * @return List of members on this block
     */
    public static List<MinecartMember<?>> findMembersOnRail(OfflineBlock railOfflineBlock) {
        Bucket bucket = cache.get(railOfflineBlock);
        return (bucket == null) ? Collections.emptyList() : bucket.members;
    }

    /**
     * Completely clears the cache. Be very aware that this will cause some information to go
     * out of sync, if information is still stored, such as the Minecart Members that are on
     * particular rails. If that's a problem, use {@link #forceRecalculation()} instead.
     */
    public static void clear() {
        for (Bucket bucket : cache.values()) {
            for (Bucket next = bucket; next != null; next = next.next) {
                next.rail_life = 0;
            }
        }
        cache.clear();
    }

    /**
     * Forces all cached information to be invalidated so that it is recalculated the next time
     * information is accessed. This should be called when registering and un-registering a rail
     * type, or when a rail type significantly alters behavior/reloads.
     */
    public static void forceRecalculation() {
        // Force all positions to re-discover the rails that are there
        // Delete buckets from memory that have no members on it
        refreshBuckets(bucket -> {
            // Delete this at all times
            bucket.rails_at_position_life = 0;
            bucket.rails_at_position = NO_RAILS_AT_POSITION;
            // Remove bucket if there's no members on the rails
            return !bucket.members.isEmpty();
        });

        // Increment life timer so that all rail access is re-validated
        verifyTimer = ++lifeTimer + TCConfig.cacheVerificationTicks;
    }

    /**
     * Removes a particular member from all member lists of cached rail positions
     *
     * @param member
     */
    public static void removeMemberFromAll(MinecartMember<?> member) {
        for (Bucket bucket : cache.values()) {
            for (Bucket next = bucket; next != null; next = next.next) {
                List<MinecartMember<?>> members = next.members;
                if (!members.isEmpty()) {
                    members.remove(member);
                }
            }
        }
    }

    /**
     * Called every tick in the background to delete cached entries that haven't been accessed
     * in a while, so they can be properly regenerated and memory doesn't infinitely go up.
     */
    public static void update() {
        final int deadTimeout = lifeTimer - TCConfig.cacheExpireTicks - TCConfig.cacheVerificationTicks;
        refreshBuckets(b -> b.checkStillValid(deadTimeout));
        verifyTimer = ++lifeTimer + TCConfig.cacheVerificationTicks;
    }

    private static void refreshBuckets(Predicate<Bucket> validChecker) {
        Iterator<Map.Entry<OfflineBlock, Bucket>> iter = cache.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<OfflineBlock, Bucket> e = iter.next();
            Bucket bucket = e.getValue();
            if (validChecker.test(bucket)) {
                // Only remove invalid buckets from the next chain
                bucket.removeInvalidBucketsFromChain(validChecker);
            } else {
                // If bucket has a next value, put that one in instead. Remove if all dead.
                while (true) {
                    bucket.rail_life = 0;
                    bucket = bucket.next;
                    if (bucket == null) {
                        iter.remove(); // No more buckets, remove entirely
                        break;
                    } else if (validChecker.test(bucket)) {
                        // Set this one, instead. Do remove further next entries that aren't valid
                        bucket.removeInvalidBucketsFromChain(validChecker);
                        e.setValue(bucket);
                        break;
                    }
                }
            }
        }
    }

    /**
     * A single bucket mapped to a block on the server. Stores both information
     * about the block as a rail block, and the block as a position block. This makes it
     * more efficient when both are the same.
     */
    private static final class Bucket extends CachedRailPiece {
        /**
         * In the case of multiple rail types existing at the same rail block, stores the
         * next rail type bucket for the same (offline) block position. This next bucket
         * and the buckets that come after will not be used for finding rails at positions.
         */
        public Bucket next = null;

        /**
         * Tick counter set to the lifeTimer every time this bucket's rail info is accessed.
         * On first access, revalidates the information. If set to 0, the bucket
         * was removed from the cache.
         */
        public int rail_life;

        /**
         * Tick counter set to the lifeTimer every time this bucket's rail-at-position info
         * is accessed. On first access, checks all it's cached rail pieces are still valid.
         * Is not set to 0 when the bucket is removed, since getting this bucket requires
         * retrieving from the cache in the first place.
         */
        public int rails_at_position_life;

        /**
         * Stores the rail pieces accessed when this bucket is treated as the Block
         * position a Minecart is at. Each rail piece will internally refer to a
         * Bucket of it's own with the rail piece information, such as signs and
         * members on the rail.
         */
        public Bucket[] rails_at_position;

        // Initializes a new Bucket for a non-rail use, with RailType NONE
        // This is used when using a block position to find rails that have minecarts near it
        // If at a later time a rail block is found anyway, then this bucket is discarded and
        // replaced with one initialized with the new Rail type.
        public Bucket(OfflineBlock offlineBlock, Block block) {
            super(offlineBlock, block, RailType.NONE);
            this.signs = MISSING_RAILS_NO_SIGNS;
            this.rail_life = lifeTimer;
            this.rails_at_position_life = 0; // Needs to be calculated
            this.rails_at_position = NO_RAILS_AT_POSITION;
        }

        // Initializes a new Bucket for a rail block. Computes signs right away.
        public Bucket(OfflineBlock offlineBlock, Block block, RailType type) {
            super(offlineBlock, block, type);
            this.signs = discoverSignsAtRailPiece(this);
            this.rail_life = lifeTimer;
            this.rails_at_position_life = 0; // Needs to be calculated
            this.rails_at_position = NO_RAILS_AT_POSITION;
        }

        /**
         * Gets whether this Bucket is dead, that is, hasn't been accessed in a while.
         * Checks whether this bucket is used as a Rail or as a Position lookup anytime
         * before the timeout. If this bucket stores members, it is never deleted.
         *
         * @param timeoutTicks
         * @return True if dead.
         */
        public boolean checkStillValid(int timeoutTicks) {
            // If accessed recently, then it can be kept. Even if members are around that should be
            // unloaded, presumably, such an unloaded member wouldn't keep accessing it.
            if (this.rail_life >= timeoutTicks || this.rails_at_position_life >= timeoutTicks) {
                return true;
            }

            // Check members
            boolean valid;
            List<MinecartMember<?>> members = this.members;
            if (members.isEmpty()) {
                valid = false; // Not accessed and no members, remove from cache
                return false; // Not accessed and no members, remove from cache
            } else {
                // Purge unloaded members, if any
                valid = true;
                Iterator<MinecartMember<?>> iter = members.iterator();
                while (iter.hasNext()) {
                    MinecartMember<?> member = iter.next();
                    if (member.isUnloaded()) {
                        iter.remove();
                        valid = !members.isEmpty();
                        TrainCarts.plugin.log(Level.WARNING, "Purged unloaded minecart from rail cache at " +
                                    offlineBlock().getPosition());
                    }
                }
            }
            return valid;
        }

        /**
         * Replaces this bucket with a new bucket of the specified Rail Type.
         * Is used when this bucket's Rail Type is NONE.
         *
         * @param railType
         * @return Newly added Bucket
         */
        public Bucket swapOutNoneType(RailType railType) {
            Bucket newBucket = new Bucket(this.offlineBlock(), this.block(), railType);
            newBucket.rails_at_position = this.rails_at_position;
            if (this.members.isEmpty()) {
                // Delete the previous bucket, it's unlikely to be used again.
                this.rail_life = 0;
            } else {
                // We can't do this if there are members stored, as those would get out of sync if
                // we remove the bucket. Put the NONE one as the second bucket to avoid problems.
                newBucket.next = this;
            }
            cache.put(this.offlineBlock(), newBucket);
            return newBucket;
        }

        /**
         * Tries to find a Bucket in this bucket's {@link #next} chain that has the
         * specified rail type. If not found, appends a new entry at the end for the
         * rail type requested.<br>
         * <br>
         * Important: this bucket's own type is NOT checked!
         *
         * @param railType
         * @return Newly added Bucket
         */
        public Bucket findOrAppendToChain(RailType railType) {
            // Traverse the 'next' chain to find it. If not found, stick a new entry at the end.
            Bucket current = this;
            while (true) {
                Bucket next = current.next;
                if (next == null) {
                    Bucket newBucket = new Bucket(this.offlineBlock(), this.block(), railType);
                    current.next = newBucket;
                    return newBucket;
                } else if (next.type() == railType) {
                    return next;
                } else {
                    current = next;
                }
            }
        }

        /**
         * Iterates down the chain of {@link #next} entries and removes buckets that
         * aren't valid anymore according to a valid checker.
         *
         * @param validChecker
         */
        public void removeInvalidBucketsFromChain(Predicate<Bucket> validChecker) {
            Bucket curr = this;
            Bucket next;
            while ((next = curr.next) != null) {
                if (validChecker.test(next)) {
                    curr = next;
                } else {
                    next.rail_life = 0;
                    curr.next = next.next;
                }
            }
        }

        /**
         * Gets, verifies and/or computes the rails that control this block position.
         *
         * @return rails at this block position
         */
        public Bucket[] getRailsAtPosition() {
            if (this.rails_at_position_life >= lifeTimer) {
                return this.rails_at_position;
            }
            this.rails_at_position_life = verifyTimer;

            // Verify still valid, if still valid, return as-is
            Bucket[] currAtPosition = this.rails_at_position;
            if (currAtPosition.length == 0) {
                return computeRailsAtPosition();
            } else {
                for (Bucket b : currAtPosition) {
                    if (!b.verify()) {
                        return computeRailsAtPosition();
                    }
                }
                return currAtPosition;
            }
        }

        private Bucket[] computeRailsAtPosition() {
            // Query the registered Rail Types for whether they exist at this position
            OfflineWorld offlineWorld = this.offlineWorld();
            Block positionBlock = this.block();
            try (Timings tim = TCTimings.RAILTYPE_FINDRAILINFO.start()) {
                for (RailType type : RailType.values()) {
                    try {
                        List<Block> rails = type.findRails(positionBlock);
                        if (!rails.isEmpty()) {
                            // During this we might end up deleting 'ourselves' if the rail type of this bucket is NONE,
                            // and a rail is found with the same block position as ourselves.
                            Bucket bucketInCache = this;
                            RailType bucketInCacheType = bucketInCache.type();

                            // Fill this array with the found buckets
                            Bucket[] newRailsAtPosition = new Bucket[rails.size()];
                            int index = 0;

                            for (Block railsBlock : rails) {
                                if (railsBlock.getX() == positionBlock.getX() &&
                                    railsBlock.getY() == positionBlock.getY() &&
                                    railsBlock.getZ() == positionBlock.getZ())
                                {
                                    // Rail can be found in the same bucket as we're already in
                                    if (bucketInCacheType == type) {
                                        // Self
                                        newRailsAtPosition[index++] = bucketInCache;
                                    } else if (bucketInCacheType == RailType.NONE) {
                                        // Swap it out
                                        bucketInCache = bucketInCache.swapOutNoneType(type);
                                        bucketInCacheType = type;
                                        newRailsAtPosition[index++] = bucketInCache;
                                    } else {
                                        // Append to chain, bucket in cache isn't changed
                                        newRailsAtPosition[index++] = bucketInCache.findOrAppendToChain(type);
                                    }
                                }
                                else
                                {
                                    // Need to look it up in the cache. This bucket won't get replaced.
                                    OfflineBlock railsOfflineBlock = offlineWorld.getBlockAt(railsBlock.getX(), railsBlock.getY(), railsBlock.getZ());
                                    newRailsAtPosition[index++] = lookupRailBucket(railsOfflineBlock, railsBlock, type);
                                }
                            }

                            return bucketInCache.rails_at_position = newRailsAtPosition;
                        }
                    } catch (Throwable t) {
                        RailType.handleCriticalError(type, t);
                    }
                }
            }

            // When no rails are found, the array is the NO_RAILS_AT_POSITION array. This will trigger another
            // lookup for rails the next tick.
            return this.rails_at_position = NO_RAILS_AT_POSITION;
        }

        @Override
        public boolean verify() {
            int currLife = this.rail_life;
            if (currLife >= lifeTimer) {
                return true; // Accessed multiple times within the cache period
            }
            if (currLife == 0) {
                return false; // Removed from cache, another lookup required
            }

            // Reset life timer on every access
            this.rail_life = verifyTimer;

            // Check that the rails type still exists at the position
            // Will always fail for RailType NONE, if that ever happens
            if (this.type().isRail(this.block())) {
                // Verify all signs we computed previously are still there
                // This is MISSING_RAILS_NO_SIGNS if previously the rails didn't exist
                TrackedSign[] signs = this.signs;
                if (signs == MISSING_RAILS_NO_SIGNS) {
                    this.signs = discoverSignsAtRailPiece(this);
                } else {
                    for (TrackedSign sign : signs) {
                        if (!sign.verify()) {
                            this.signs = discoverSignsAtRailPiece(this);
                            break;
                        }
                    }
                }

                // All good!
                return true;
            } else {
                // Clear all signs with a special array that indicates signs couldn't be calculated
                // If the rail type exists in the future, recalculates the signs properly
                this.signs = MISSING_RAILS_NO_SIGNS;

                // This sadly will result in another cache lookup, but as it only occurs when rails
                // go missing, it's not a big problem. We must return false so that during at-position
                // lookup it will actually recompute.
                return false;
            }
        }

        /**
         * Called to initialize a new Bucket with the rail pieces that exist at a given
         * Block position. Is optimized to refer to itself if the rails happen to coincide
         * with the block position.
         *
         * @param positionOfflineBlock
         * @return List of buckets of rails at this block position
         */
        public static Bucket[] discoverAtPositionBlock(OfflineBlock positionOfflineBlock) {
            // Query the registered Rail Types for whether they exist at this position
            OfflineWorld offlineWorld = positionOfflineBlock.getWorld();
            Block positionBlock = positionOfflineBlock.getLoadedBlock();
            try (Timings tim = TCTimings.RAILTYPE_FINDRAILINFO.start()) {
                for (RailType type : RailType.values()) {
                    try {
                        List<Block> rails = type.findRails(positionBlock);
                        if (!rails.isEmpty()) {
                            // During this we might end up deleting 'ourselves' if the rail type of this bucket is NONE,
                            // and a rail is found with the same block position as ourselves.
                            Bucket bucketInCache = null;

                            // Fill this array with the found buckets
                            Bucket[] newRailsAtPosition = new Bucket[rails.size()];
                            int index = 0;

                            for (Block railsBlock : rails) {
                                if (railsBlock.getX() == positionBlock.getX() &&
                                    railsBlock.getY() == positionBlock.getY() &&
                                    railsBlock.getZ() == positionBlock.getZ())
                                {
                                    // As the bucket for this type is being calculated, it's never going to find more
                                    // than one type here, so this is safe.
                                    bucketInCache = new Bucket(positionOfflineBlock, positionBlock, type);
                                    newRailsAtPosition[index++] = bucketInCache;
                                }
                                else
                                {
                                    // Need to look it up in the cache. This bucket won't get replaced.
                                    OfflineBlock railsOfflineBlock = offlineWorld.getBlockAt(railsBlock.getX(), railsBlock.getY(), railsBlock.getZ());
                                    newRailsAtPosition[index++] = lookupRailBucket(railsOfflineBlock, railsBlock, type);
                                }
                            }

                            // If block itself isn't a rail then we must initialize it as NONE initially
                            if (bucketInCache == null) {
                                bucketInCache = new Bucket(positionOfflineBlock, positionBlock);
                            }

                            // Put it in the cache
                            cache.put(positionOfflineBlock, bucketInCache);

                            return bucketInCache.rails_at_position = newRailsAtPosition;
                        }
                    } catch (Throwable t) {
                        RailType.handleCriticalError(type, t);
                    }
                }
            }

            // When no rails are found, the array is the NO_RAILS_AT_POSITION array. This will trigger another
            // lookup for rails the next tick.
            // Just put a NONE bucket to represent this
            cache.put(positionOfflineBlock, new Bucket(positionOfflineBlock, positionBlock));
            return NO_RAILS_AT_POSITION;
        }
    }

    /**
     * A RailPiece backed by this RailLookup's cache. Stores, besides the rail piece
     * information itself, the members and signs coupled with the rails.
     */
    public static abstract class CachedRailPiece extends RailPiece {
        /**
         * List of members occupying this bucket's block, treating the block as
         * a rail block. Is made immutable with an ArrayList when a member first
         * uses the rails, is the DEFAULT_MEMBER_LIST otherwise.
         */
        protected List<MinecartMember<?>> members;
        /**
         * Array of signs activated by this bucket's block, treating the block as
         * a rail block.
         */
        protected TrackedSign[] signs;

        /**
         * A cached rail piece with no valid information at all. {@link #verify()} will always
         * return false, so the caller will change this one out with the right one on first use.
         */
        public static final CachedRailPiece NONE = new CachedRailPiece() {
            @Override
            public boolean verify() {
                return false;
            }
        };
 
        private CachedRailPiece() {
            super();
            this.members = Collections.unmodifiableList(Collections.emptyList());
            this.signs = NO_SIGNS;
        }

        protected CachedRailPiece(OfflineBlock offlineBlock, Block block, RailType type) {
            super(offlineBlock, block, type);
            this.cached = this;
            this.members = DEFAULT_MEMBER_LIST;
            this.signs = NO_SIGNS;
        }

        /**
         * Checks whether these cached contents are still valid and refreshes information
         * if needed. Returns false if this cached instance is no longer valid, and a new
         * lookup is required.
         *
         * @return True if this information is still valid
         */
        public abstract boolean verify();

        /**
         * Gets a list of cached Minecart Members that occupy these rails.
         * Only valid if {@link #verify()} is true.
         *
         * @return members
         */
        public final List<MinecartMember<?>> cachedMembers() {
            return this.members;
        }

        /**
         * Gets a list of cached Minecart Members that occupy these rails.
         * The returned list is guaranteed to be mutable.
         * Only valid if {@link #verify()} is true.
         *
         * @return members
         */
        public final List<MinecartMember<?>> cachedMutableMembers() {
            List<MinecartMember<?>> result = this.members;
            if (result == DEFAULT_MEMBER_LIST) {
                this.members = result = new ArrayList<MinecartMember<?>>(2);
            }
            return result;
        }

        /**
         * Gets an array of cached tracked signs that are activated when trains drive
         * over these rails.
         * Only valid if {@link #verify()} is true.
         *
         * @return signs
         */
        public final TrackedSign[] cachedSigns() {
            return this.signs;
        }

        @Override
        public boolean equals(Object o) {
            return o == this; // Used when removing a value, we don't want normal equals there
        }
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

        /**
         * Verifies that this tracked sign is really still there on the server
         *
         * @return True if the sign is there
         */
        public boolean verify() {
            return BlockUtil.ISSIGN.get(signBlock);
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

    /**
     * Searches from the position of a sign block for the RailPiece that is coupled
     * with that sign, if that sign were to be triggered (by redstone, for example).
     * Returns {@link RailPiece#NONE} if no rails could be found.
     * 
     * @param signblock Block of the sign
     * @return rails piece information, NONE if the sign has no rails (rail block is null)
     */
    public static RailPiece discoverRailPieceFromSign(Block signblock) {
        if (signblock == null) {
            return RailPiece.NONE;
        }

        BlockData signblock_data = WorldUtil.getBlockData(signblock);
        final Block mainBlock;
        if (signblock_data.isType(WALL_SIGN_TYPE)) {
            mainBlock = signblock.getRelative(signblock_data.getAttachedFace());
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
            BlockData blockData;
            hasSigns = true;
            while (true) {
                // Go to the next block
                block = block.getRelative(dir);
                blockData = WorldUtil.getBlockData(block);

                // Check for rails
                railType = RailType.getType(block, blockData);
                BlockFace columnDir = railType.getSignColumnDirection(block);
                if (dir == columnDir.getOppositeFace()) {
                    return RailPiece.create(railType, block);
                }

                // End of the loop?
                if (!hasSigns) {
                    break;
                }

                // Go to the next block
                if (MaterialUtil.ISSIGN.get(blockData)) {
                    hasSigns = blockData.getAttachedFace() == BlockFace.DOWN;
                } else {
                    hasSigns = hasAttachedSigns(block);
                }
            }
        }
        return RailPiece.NONE;
    }

    /**
     * Discovers the signs belonging to a particular rail.
     * Unlike {@link RailPiece#signs()} this method does not look
     * the information up from a cache
     * 
     * @param railType of the rail
     * @param railBlock of the rail
     * @return signs belonging to this rail
     */
    public static TrackedSign[] discoverSignsAtRailPiece(RailPiece rail) {
        Block columnStart = rail.type().getSignColumnStart(rail.block());
        if (columnStart == null) {
            return NO_SIGNS;
        }

        BlockFace direction = rail.type().getSignColumnDirection(rail.block());
        if (direction == null || direction == BlockFace.SELF) {
            return NO_SIGNS;
        }

        List<Block> cache = SIGN_LIST_CACHE;
        try {
            // Compute signs. Do check that the sign search input params are correct.
            int x = columnStart.getX() & 0xF;
            int z = columnStart.getZ() & 0xF;
            if (FaceUtil.isVertical(direction) && x >= 1 && x <= 14 && z >= 1 && z <= 14) {
                // If direction is vertical, and the block is within chunk bounds, we can check
                // super efficiently by querying the chunk directly.
                Chunk chunk = columnStart.getChunk();
                addSignsFromRailsVerticalInChunk(cache, chunk, x, columnStart.getY(), z, direction);
                if (!cache.isEmpty()) {
                    TrackedSign[] signs = new TrackedSign[cache.size()];
                    for (int i = 0; i < signs.length; i++) {
                        signs[i] = new TrackedSign(cache.get(i), rail);
                    }
                    return signs;
                }
            } else {
                // Slightly slower
                addSignsFromRails(cache, columnStart, direction);
                if (!cache.isEmpty()) {
                    TrackedSign[] signs = new TrackedSign[cache.size()];
                    for (int i = 0; i < signs.length; i++) {
                        signs[i] = new TrackedSign(cache.get(i), rail);
                    }
                    return signs;
                }
            }

            return NO_SIGNS;
        } finally {
            cache.clear();
        }
    }

    private static void addSignsFromRailsVerticalInChunk(List<Block> rval, Chunk chunk, int rx, int ry, int rz, BlockFace signDirection) {
        int offsetCtr = 0;
        while (true) {
            if (WorldUtil.getBlockData(chunk, rx, ry, rz).isType(SIGN_POST_TYPE)) {
                // Found a sign post - add it and continue
                rval.add(chunk.getBlock(rx, ry, rz));
            } else if (addAttachedSignsVerticalInChunk(rval, chunk, rx, ry, rz)) {
                // Found one or more signs attached to the current block - continue
            } else if (offsetCtr > 1) {
                // No signs found here. If this is too far down, stop.
                break;
            }

            ry += signDirection.getModY();
            offsetCtr++;
        }
    }

    private static boolean addAttachedSignsVerticalInChunk(List<Block> rval, Chunk chunk, int rx, int ry, int rz) {
        boolean found = false;
        for (BlockFace face : FaceUtil.AXIS) {
            BlockData blockData = WorldUtil.getBlockData(chunk,
                    rx + face.getModX(), ry, rz + face.getModZ());
            if (MaterialUtil.ISSIGN.get(blockData) && blockData.getAttachedFace() == face.getOppositeFace()) {
                found = true;
                rval.add(chunk.getBlock(rx + face.getModX(), ry, rz + face.getModZ()));
            }
        }
        return found;
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
            BlockData blockData = WorldUtil.getBlockData(b);
            if (MaterialUtil.ISSIGN.get(blockData) && blockData.getAttachedFace() == face.getOppositeFace()) {
                found = true;
                rval.add(b);
            }
        }
        return found;
    }

    private static boolean hasAttachedSigns(final Block middle) {
        for (BlockFace face : FaceUtil.AXIS) {
            Block b = middle.getRelative(face);
            BlockData blockData = WorldUtil.getBlockData(b);
            if (MaterialUtil.ISSIGN.get(blockData) && blockData.getAttachedFace() == face.getOppositeFace()) {
                return true;
            }
        }
        return false;
    }
}
