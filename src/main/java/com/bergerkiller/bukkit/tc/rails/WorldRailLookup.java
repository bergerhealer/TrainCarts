package com.bergerkiller.bukkit.tc.rails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCacheWorld;

/**
 * Retrieves and caches rails and information about rails, mapped to
 * the minecart and rail positions. The cache eliminates frequent lookups
 * using more expensive discovery methods. This lookup is of a single Bukkit
 * World.<br>
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
public class WorldRailLookup {
    /** Signals that no world has been selected yet. Used by {@link RailPiece#NONE}. */
    public static final WorldRailLookup NONE = new WorldRailLookup();

    // Internally-used constant arrays
    private static final Bucket[] NO_RAILS_AT_POSITION = new Bucket[0];

    // Per-world data
    private World world;
    private OfflineWorld offlineWorld;
    private final Map<IntVector3, Bucket> cache;
    private final ArrayList<Bucket> cacheValues;
    private final MutexZoneCacheWorld mutexZones;
    private final SignControllerWorld signController;

    // None
    private WorldRailLookup() {
        this.offlineWorld = OfflineWorld.NONE;
        this.world = null;
        this.cache = null; // Should never be used
        this.cacheValues = null; // Should never be used
        this.mutexZones = null; // Should never be used
        this.signController = null; // Should never be used
    }

    WorldRailLookup(World world) {
        this.offlineWorld = OfflineWorld.of(world);
        this.world = world;
        this.cache = new HashMap<>();
        this.cacheValues = new ArrayList<>();
        this.mutexZones = MutexZoneCache.forWorld(this.offlineWorld);
        this.signController = TrainCarts.plugin.getSignController().forWorld(this.world);
    }

    /**
     * Gets the World this WorldRailLookup is for
     *
     * @return World
     */
    public World getWorld() {
        return this.world;
    }

    /**
     * Gets the OfflineWorld this WorldRailLookup is for
     *
     * @return OfflineWorld
     */
    public OfflineWorld getOfflineWorld() {
        return this.offlineWorld;
    }

    /**
     * Gets the Mutex Zone information of this world's rail lookup
     *
     * @return mutex zones
     * @see MutexZoneCache#forWorld(OfflineWorld)
     */
    public MutexZoneCacheWorld getMutexZones() {
        return this.mutexZones;
    }

    /**
     * Gets the Sign Controller of the world's rail lookup
     *
     * @return sign controller
     * @see SignController#forWorld(World)
     */
    public SignControllerWorld getSignController() {
        return this.signController;
    }

    /**
     * Gets whether this World Rail Lookup is still valid, and can be used. This will return false
     * when the world it represents has unloaded, or the plugin shut down.
     *
     * @return True if still valid
     */
    public boolean isValid() {
        return this.world != null;
    }

    /**
     * Checks whether this world rail lookup can be removed. This is the case when the world has
     * unloaded, or the cache is empty.
     *
     * @return True if this by-world rail lookup can be removed safely
     */
    boolean checkCanBeRemoved() {
        return this.offlineWorld.getLoadedWorld() != this.world || this.cacheValues.isEmpty();
    }

    /**
     * Completely clears the cache. Be very aware that this will cause some information to go
     * out of sync, if information is still stored, such as the Minecart Members that are on
     * particular rails. If that's a problem, use {@link #forceRecalculation()} instead.<Br>
     * <br>
     * Should be called when this by-world lookup is deleted and shouldn't be used, anymore.
     */
    void remove() {
        forAllBuckets(b -> b.rail_life = RailLookup.LIFE_TIMER_DELETED);
        cache.clear();
        cacheValues.clear();
        world = null;
        offlineWorld = OfflineWorld.NONE;
    }

    /**
     * Orders initialization of this lookup cache. Information mapped to blocks should be
     * stored into the cache at this point.
     */
    void initialize() {
        DetectorRegion.fillRailLookup(this);
    }

    /**
     * Discovers the RailPieces that are active for controlling the movement of a Minecart
     * at the RailState position information specified. The caller should further filter which of
     * these rail pieces truly control the movement, but the returned array is efficiently
     * cached.
     *
     * @param state RailState with position information
     * @return Array of rail pieces active here, or an empty array if there are no rails nearby
     */
    public RailPiece[] findAtStatePosition(RailState state) {
        IntVector3 coordinates;
        {
            RailPath.Position pos = state.position();
            if (pos.relative) {
                // This is practically not used!
                coordinates = state.railPiece().blockPosition().add(MathUtil.floor(pos.posX),
                                                                    MathUtil.floor(pos.posY),
                                                                    MathUtil.floor(pos.posZ));
            } else {
                coordinates = new IntVector3(MathUtil.floor(pos.posX),
                                             MathUtil.floor(pos.posY),
                                             MathUtil.floor(pos.posZ));
            }
        }

        // If already in the cache, compute/return it right-away
        // During computation the original bucket may get deleted (if rail type was NONE)
        IntVector3 cacheKey = createCacheKey(coordinates);
        Bucket inCache = cache.get(cacheKey);
        if (inCache != null) {
            return inCache.getRailsAtPosition();
        }

        // We need to create a new bucket at this position. While we could initialize one
        // with rail type NONE and proceed from there, it results in a bucket to be created
        // that is then just thrown away again. It's better to do an at-position search first,
        // and if any of the found rails match with the position block, we use that one.
        return discoverBucketsAtPositionBlock(cacheKey, offlineWorld.getBlockAt(coordinates));
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
    public RailPiece[] findAtBlockPosition(OfflineBlock positionBlock) {
        // If already in the cache, compute/return it right-away
        // During computation the original bucket may get deleted (if rail type was NONE)
        IntVector3 cacheKey = createCacheKey(positionBlock);
        Bucket inCache = cache.get(cacheKey);
        if (inCache != null) {
            return inCache.getRailsAtPosition();
        }

        // We need to create a new bucket at this position. While we could initialize one
        // with rail type NONE and proceed from there, it results in a bucket to be created
        // that is then just thrown away again. It's better to do an at-position search first,
        // and if any of the found rails match with the position block, we use that one.
        return discoverBucketsAtPositionBlock(cacheKey, positionBlock);
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
    public RailLookup.CachedRailPiece lookupCachedRailPiece(final OfflineBlock railOfflineBlock,
                                                            final Block railBlock,
                                                            final RailType railType
    ) {
        return lookupRailBucket(railOfflineBlock, railBlock, railType);
    }

    private Bucket lookupRailBucket(final OfflineBlock railOfflineBlock,
                                    final Block railBlock,
                                    final RailType railType
    ) {
        // First try to find it in the cache, and if none exists, initialize a new one.
        IntVector3 cacheKey = createCacheKey(railOfflineBlock);
        Bucket inCache = cache.get(cacheKey);
        if (inCache == null) {
            inCache = new Bucket(railOfflineBlock, railBlock, railType);
            addToCache(cacheKey, inCache);
            inCache.signs = RailLookup.discoverSignsAtRailPiece(inCache);
            return inCache; // We know railType matches - we just initialized it!
        }

        // If the rail type of the one in cache is 'NONE', it most likely was initialized before
        // the rail was found as a valid rail type. For performance reasons it's better to
        // yeet this bucket out, as it's unlikely to be used again.
        // Because of this logic the inCache one will also never store a 'next' chain.
        //
        // In other cases, entries are added to the 'next' chain to represent them, including
        // NONE if this is required.
        RailType inCacheType = inCache.type();
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
     * @param railCoordinates Block coordinates of the Rail Block
     * @return List of members on this block
     */
    public List<MinecartMember<?>> findMembersOnRail(IntVector3 railCoordinates) {
        Bucket bucket = cache.get(createCacheKey(railCoordinates));
        return (bucket == null) ? Collections.emptyList() : bucket.members;
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
    public List<MinecartMember<?>> findMembersOnRail(OfflineBlock railOfflineBlock) {
        Bucket bucket = cache.get(createCacheKey(railOfflineBlock));
        return (bucket == null) ? Collections.emptyList() : bucket.members;
    }

    /**
     * Removes a particular member from all member lists of cached rail positions
     *
     * @param member
     */
    public void removeMemberFromAll(MinecartMember<?> member) {
        forAllBuckets(b -> {
            List<MinecartMember<?>> members = b.members;
            if (!members.isEmpty()) {
                members.remove(member);
            }
        });
    }

    private void forAllBuckets(Consumer<Bucket> callback) {
        for (Bucket bucket : cacheValues) {
            for (Bucket next = bucket; next != null; next = next.next) {
                callback.accept(next);
            }
        }
    }

    /**
     * Refreshes all bucket information, forcing a re-calculation
     */
    void refreshAllBuckets() {
        // Force all positions to re-discover the rails that are there
        // Delete all buckets from memory that we can get away with
        refreshBuckets(bucket -> {
            // Delete this at all times
            bucket.rail_life = RailLookup.LIFE_TIMER_START;
            bucket.rails_at_position_life = RailLookup.LIFE_TIMER_DELETED;
            bucket.rails_at_position = NO_RAILS_AT_POSITION;
            bucket.signs = RailLookup.MISSING_RAILS_NO_SIGNS;
            return false;
        });
    }

    void update(int deadTimeout) {
        refreshBuckets(b -> b.checkStillValid(deadTimeout));
    }

    private void refreshBuckets(Predicate<Bucket> validChecker) {
        for (ListIterator<Bucket> iter = cacheValues.listIterator(); iter.hasNext();) {
            Bucket bucket = iter.next();
            if (validChecker.test(bucket) || !bucket.canBePurged(bucket.next == null)) {
                // Only remove invalid buckets from the next chain
                bucket.removeInvalidBucketsFromChain(validChecker);
            } else {
                // If bucket has a next value, put that one in instead. Remove if all dead.
                IntVector3 cacheKey = createCacheKey(bucket.blockPosition());
                while (true) {
                    bucket.rail_life = RailLookup.LIFE_TIMER_DELETED;
                    bucket = bucket.next;
                    if (bucket == null) {
                        // No more buckets, remove entirely
                        iter.remove();
                        cache.remove(cacheKey);
                        break;
                    } else if (validChecker.test(bucket) || !bucket.canBePurged(true)) {
                        // Set this one, instead. Do remove further next entries that aren't valid
                        bucket.removeInvalidBucketsFromChain(validChecker);
                        iter.set(bucket);
                        cache.put(cacheKey, bucket);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Sets/stores the Detector Regions that should be activated at particular rail block
     * coordinates.
     *
     * @param coordinates Rail Block coordinates
     * @param regions Detector Regions to be activated. Null stores none.
     */
    public void storeDetectorRegions(IntVector3 coordinates, DetectorRegion[] regions) {
        for (Bucket b = getOrCreateAtCoordinates(coordinates); b != null; b = b.next) {
            b.detectorRegions = (regions == null || regions.length == 0) ? RailLookup.NO_DETECTOR_REGIONS : regions;
        }
    }

    /**
     * Gets the Detector Regions that are activated at particular rail block coordinates
     *
     * @param coordinates Rail Block coordinates
     * @return Detector Regions at these coordinates
     */
    public DetectorRegion[] getDetectorRegions(IntVector3 coordinates) {
        Bucket bucket = this.cache.get(createCacheKey(coordinates));
        return (bucket == null) ? RailLookup.NO_DETECTOR_REGIONS : bucket.detectorRegions;
    }

    /**
     * Gets or creates a Bucket for storing metadata at particular Block coordinates.
     * Does not perform any Block access (finding rail type / signs / etc.) if no such bucket
     * exists yet, as it's meant to be fast.
     *
     * @param coordinates Block coordinates
     * @return Bucket
     */
    private Bucket getOrCreateAtCoordinates(IntVector3 coordinates) {
        IntVector3 cacheKey = createCacheKey(coordinates);
        Bucket bucket = this.cache.get(cacheKey);
        if (bucket == null) {
            bucket = new Bucket(this.offlineWorld.getBlockAt(coordinates),
                                 BlockUtil.getBlock(this.world, coordinates));
            this.cache.put(cacheKey, bucket);
            this.cacheValues.add(bucket);
        }
        return bucket;
    }

    /**
     * Called to initialize a new Bucket with the rail pieces that exist at a given
     * Block position. Is optimized to refer to itself if the rails happen to coincide
     * with the block position.<br>
     * <br>
     * Only ever called if no bucket exists in cache yet.
     *
     * @param cacheKey Key to address the {@link #cache}
     * @param positionOfflineBlock
     * @return List of buckets of rails at this block position
     */
    private Bucket[] discoverBucketsAtPositionBlock(IntVector3 cacheKey, OfflineBlock positionOfflineBlock) {
        // Query the registered Rail Types for whether they exist at this position
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
                        addToCache(cacheKey, bucketInCache);
                        bucketInCache.rails_at_position = newRailsAtPosition;

                        // Compute signs now that bucket is registered
                        bucketInCache.signs = RailLookup.discoverSignsAtRailPiece(bucketInCache);

                        return newRailsAtPosition;
                    }
                } catch (Throwable t) {
                    RailType.handleCriticalError(type, t);
                }
            }
        }

        // When no rails are found, the array is the NO_RAILS_AT_POSITION array. This will trigger another
        // lookup for rails the next tick.
        // Just put a NONE bucket to represent this
        addToCache(cacheKey, new Bucket(positionOfflineBlock, positionBlock));
        return NO_RAILS_AT_POSITION;
    }

    private void addToCache(IntVector3 cacheKey, Bucket bucket) {
        cache.put(cacheKey, bucket);
        cacheValues.add(bucket);
    }

    /**
     * Computes the cache lookup key that refers to a certain position or rail block
     *
     * @param block Offline Block
     * @return Cache lookup key
     */
    private static IntVector3 createCacheKey(OfflineBlock block) {
        return block.getPosition();
    }

    /**
     * Computes the cache lookup key that refers to a certain position or rail block
     *
     * @param coordinates Block Coordinates
     * @return Cache lookup key
     */
    private static IntVector3 createCacheKey(IntVector3 coordinates) {
        return coordinates;
    }

    /**
     * A single bucket mapped to a block on the server. Stores both information
     * about the block as a rail block, and the block as a position block. This makes it
     * more efficient when both are the same.
     */
    private final class Bucket extends RailLookup.CachedRailPiece {
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
            this(offlineBlock, block, RailType.NONE);
        }

        // Initializes a new Bucket for a rail block.
        // It's expected that after assigning the signs are calculated
        public Bucket(OfflineBlock offlineBlock, Block block, RailType type) {
            super(WorldRailLookup.this, offlineBlock, block, type);
            this.signs = RailLookup.MISSING_RAILS_NO_SIGNS;
            this.rail_life = RailLookup.lifeTimer;
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

            // Purge members that have unloaded, to allow for this bucket to be cleaned up properly
            // Ideally this can be removed entirely, if the member is always cleaned up...
            List<MinecartMember<?>> members = this.members;
            if (!members.isEmpty()) {
                Iterator<MinecartMember<?>> iter = members.iterator();
                while (iter.hasNext()) {
                    MinecartMember<?> member = iter.next();
                    if (member.isUnloaded()) {
                        iter.remove();
                        TrainCarts.plugin.log(Level.WARNING, "Purged unloaded minecart from rail cache at " +
                                    offlineBlock().getPosition());
                    }
                }
            }

            // Not valid
            return false;
        }

        /**
         * Checks whether it is safe to delete this Bucket without causing a loss of data/state
         *
         * @param isOnlyBucketAtBlock Whether this is the last Bucket remaining that is mapped to
         *                            particular Rail Block coordinates
         * @return True if this Bucket can be purged from the cache
         */
        private boolean canBePurged(boolean isOnlyBucketAtBlock) {
            // Members must remain cached
            if (!this.members.isEmpty()) {
                return false;
            }

            // At least one bucket at the Block must exist that preserves the detector regions
            if (isOnlyBucketAtBlock && this.detectorRegions != RailLookup.NO_DETECTOR_REGIONS) {
                return false;
            }

            return true;
        }

        /**
         * Replaces this bucket with a new bucket of the specified Rail Type.
         * Is used when this bucket's Rail Type is NONE.
         *
         * @param railType
         * @return Newly added Bucket
         */
        public Bucket swapOutNoneType(RailType railType) {
            Bucket newBucket = this.cloneAsType(railType);
            newBucket.rails_at_position = this.rails_at_position;
            if (this.members.isEmpty()) {
                // Delete the previous bucket, it's unlikely to be used again.
                this.rail_life = 0;
            } else {
                // We can't do this if there are members stored, as those would get out of sync if
                // we remove the bucket. Put the NONE one as the second bucket to avoid problems.
                newBucket.next = this;
            }

            // Replace or add to cache values mapping
            {
                boolean found = false;
                for (ListIterator<Bucket> iter = cacheValues.listIterator(); iter.hasNext();) {
                    if (iter.next() == this) {
                        iter.set(newBucket);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    cacheValues.add(newBucket);
                }
            }
            cache.put(createCacheKey(newBucket.blockPosition()), newBucket);

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
                    Bucket newBucket = current.cloneAsType(railType);
                    current.next = newBucket;
                    newBucket.signs = RailLookup.discoverSignsAtRailPiece(newBucket);
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
                if (validChecker.test(next) || !next.canBePurged(false)) {
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
            if (this.rails_at_position_life >= RailLookup.lifeTimer) {
                return this.rails_at_position;
            }
            this.rails_at_position_life = RailLookup.verifyTimer;

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

        /**
         * Clones this Bucket as a new RailType, preserving information that must be shared
         * by all buckets for a particular rail block, like detector regions.
         *
         * @param railType
         * @return New Bucket
         */
        private Bucket cloneAsType(RailType railType) {
            Bucket newBucket = new Bucket(this.offlineBlock(), this.block(), railType);
            newBucket.detectorRegions = this.detectorRegions;
            return newBucket;
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
            if (currLife >= RailLookup.lifeTimer) {
                return true; // Accessed multiple times within the cache period
            }
            if (currLife == RailLookup.LIFE_TIMER_DELETED) {
                return false; // Removed from cache, another lookup required
            }

            // Reset life timer on every access
            this.rail_life = RailLookup.verifyTimer;

            // Check that the rails type still exists at the position
            // Will always fail for RailType NONE, if that ever happens
            if (this.type().isRail(this.block())) {
                // Verify all signs we computed previously are still there
                // This is MISSING_RAILS_NO_SIGNS if previously the rails didn't exist
                TrackedSign[] signs = this.signs;
                if (signs == RailLookup.MISSING_RAILS_NO_SIGNS) {
                    this.signs = RailLookup.discoverSignsAtRailPiece(this);
                } else {
                    // Check all tracked signs to see if any of them have been removed
                    // If they change in other ways (update()), re-create the TrackedSign instance
                    for (int i = 0; i < signs.length; i++) {
                        TrackedSign sign = signs[i];
                        if (!sign.tracker.update()) {
                            continue;
                        }
                        if (sign.tracker.isRemoved()) {
                            // Regenerate, the entire sign is gone, so there's likely more changes
                            this.signs = RailLookup.discoverSignsAtRailPiece(this);
                            break;
                        }

                        // Only re-create the TrackedSign to update the Sign state
                        try {
                            this.signs[i] = new TrackedSign(sign.tracker, sign.rail);
                        } catch (Throwable t) {
                            this.signs = signs = LogicUtil.removeArrayElement(signs, i);
                            i--;
                            break;
                        }
                    }
                }

                // All good!
                return true;
            } else {
                // Clear all signs with a special array that indicates signs couldn't be calculated
                // If the rail type exists in the future, recalculates the signs properly
                this.signs = RailLookup.MISSING_RAILS_NO_SIGNS;

                // This sadly will result in another cache lookup, but as it only occurs when rails
                // go missing, it's not a big problem. We must return false so that during at-position
                // lookup it will actually recompute.
                return false;
            }
        }

        @Override
        public boolean verifyExists() {
            return this.rail_life != RailLookup.LIFE_TIMER_DELETED;
        }
    }
}
