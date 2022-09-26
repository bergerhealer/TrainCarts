package com.bergerkiller.bukkit.tc.rails;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
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
final class WorldRailLookupImpl implements WorldRailLookup {
    // Internally-used constant arrays
    private static final Bucket[] NO_RAILS_AT_POSITION = new Bucket[0];

    // Used when calculating the signs at a rail
    private static final TrackedSignList SIGN_LIST_CACHE = new TrackedSignList();

    private static final Material WALL_SIGN_TYPE = getMaterial("LEGACY_WALL_SIGN");
    private static final Material SIGN_POST_TYPE = getMaterial("LEGACY_SIGN_POST");
    private static BlockFace[] SIGN_FACES_ORDERED = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};

    // Per-world data
    private final TrainCarts traincarts;
    private World world;
    private OfflineWorld offlineWorld;
    private Map<IntVector3, Bucket> cache;
    private List<Bucket> cacheValues;
    private MutexZoneCacheWorld mutexZones;
    private SignControllerWorld signController;
    private int ticksWithEmptyCache;

    WorldRailLookupImpl(TrainCarts traincarts, World world) {
        this.traincarts = traincarts;
        this.offlineWorld = OfflineWorld.of(world);
        this.world = world;
        this.cache = new HashMap<>();
        this.cacheValues = new ArrayList<>();
        this.mutexZones = MutexZoneCache.forWorld(this.offlineWorld);
        this.signController = traincarts.getSignController().forWorldSkipInitialization(this.world);
        this.ticksWithEmptyCache = 0;
    }

    /**
     * Orders initialization of this lookup cache. Information mapped to blocks should be
     * stored into the cache at this point.
     */
    void initialize() {
        // NOW we can start activating signs (sign action loaded events are fired)
        this.signController.initialize();

        DetectorRegion.fillRailLookup(this);
    }

    @Override
    public World getWorld() {
        World w = this.world;
        return (w == null) ? this.offlineWorld.getLoadedWorld() : w;
    }

    @Override
    public OfflineWorld getOfflineWorld() {
        return this.offlineWorld;
    }

    @Override
    public MutexZoneCacheWorld getMutexZones() {
        return this.mutexZones;
    }

    @Override
    public SignControllerWorld getSignController() {
        return this.signController;
    }

    @Override
    public boolean isValid() {
        return this.world != null;
    }

    @Override
    public boolean isValidForWorld(World world) {
        return this.world == world;
    }

    /**
     * Checks whether this world rail lookup can be removed. This is the case when the world has
     * unloaded, or the cache is empty.
     *
     * @return True if this by-world rail lookup can be removed safely
     */
    boolean checkCanBeRemoved() {
        if (this.offlineWorld.getLoadedWorld() != this.world) {
            return true; // World unloaded, delete cache right away!
        }
        if (!this.cache.isEmpty()) {
            ticksWithEmptyCache = 0;
            return false; // Not empty, keep it around!
        }

        // Empty for 10 minutes? Remove it, free memory
        return ++ticksWithEmptyCache > 12000;
    }

    /**
     * Completely clears the cache. Be very aware that this will cause some information to go
     * out of sync, if information is still stored, such as the Minecart Members that are on
     * particular rails. If that's a problem, use {@link #forceRecalculation()} instead.<Br>
     * <br>
     * Should be called when this by-world lookup is deleted and shouldn't be used, anymore.
     */
    void close() {
        if (!cache.isEmpty()) {
            forAllBuckets(b -> b.rail_life = RailLookup.LIFE_TIMER_DELETED);
            cache.clear();
            cacheValues.clear();
        }
        cache = Collections.emptyMap(); // Free memory
        cacheValues = Collections.emptyList(); // Free memory
        world = null; // Forces errors / checking
    }

    @Override
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

    @Override
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

    @Override
    public RailLookup.CachedRailPiece lookupCachedRailPieceIfCached(final OfflineBlock railOfflineBlock,
                                                                    final RailType railType
    ) {
        IntVector3 cacheKey = createCacheKey(railOfflineBlock);
        Bucket inCache = cache.get(cacheKey);
        if (inCache != null) {
            RailType inCacheType = inCache.type();
            if (inCacheType == railType) {
                return inCache;
            } else if (inCacheType != RailType.NONE) {
                while ((inCache = inCache.next) != null) {
                    if (inCache.type() == railType) {
                        return inCache;
                    }
                }
            }
        }

        // Not cached, return NONE
        return RailLookup.CachedRailPiece.NONE;
    }

    @Override
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
            if (!railType.isRegistered()) {
                throw new RailLookup.RailTypeNotRegisteredException(railType);
            }
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

    @Override
    public List<MinecartMember<?>> findMembersOnRail(IntVector3 railCoordinates) {
        Bucket bucket = cache.get(createCacheKey(railCoordinates));
        return (bucket == null) ? Collections.emptyList() : bucket.members;
    }

    @Override
    public List<MinecartMember<?>> findMembersOnRail(OfflineBlock railOfflineBlock) {
        Bucket bucket = cache.get(createCacheKey(railOfflineBlock));
        return (bucket == null) ? Collections.emptyList() : bucket.members;
    }

    @Override
    public void removeMemberFromAll(MinecartMember<?> member) {
        forAllBuckets(b -> {
            List<MinecartMember<?>> members = b.members;
            if (!members.isEmpty()) {
                members.remove(member);
            }
        });
    }

    @Override
    public TrackedSign[] discoverSignsAtRailPiece(RailPiece rail) {
        try (TrackedSignList cache = SIGN_LIST_CACHE.start(rail)) {
            try {
                RailType type = rail.type();
                if (!type.isRegistered()) {
                    throw new RailLookup.RailTypeNotRegisteredException(type);
                }
                type.discoverSigns(rail, this.signController, cache.signs);
                return cache.build();
            } catch (Throwable t) {
                traincarts.getLogger().log(Level.SEVERE, "Failed discover signs for " + rail, t);
                return RailLookup.NO_SIGNS;
            }
        }
    }

    @Override
    public RailPiece discoverRailPieceFromSign(Block signblock) {
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
        for (BlockFace dir : SIGN_FACES_ORDERED) {
            Block block = mainBlock;
            BlockData blockData;
            boolean hasSigns = true;
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
                if (blockData.isType(SIGN_POST_TYPE)) {
                    hasSigns = true;
                } else {
                    hasSigns = this.signController.hasSignsAroundColumn(block, dir.getOppositeFace());
                }
            }
        }
        return RailPiece.NONE;
    }

    private void forAllBuckets(Consumer<Bucket> callback) {
        for (Bucket bucket : cacheValues) {
            for (Bucket next = bucket; next != null; next = next.next) {
                callback.accept(next);
            }
        }
    }

    /**
     * Forcefully unloads all information stored about a particular rail type
     *
     * @param type
     */
    void unloadRailType(RailType type) {
        refreshBuckets(bucket -> bucket.type() != type, true);
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
        }, false);
    }

    void update(int deadTimeout) {
        refreshBuckets(b -> b.checkStillValid(deadTimeout), false);
    }

    private void refreshBuckets(Predicate<Bucket> validChecker, boolean ignoreCanBePurged) {
        for (ListIterator<Bucket> iter = cacheValues.listIterator(); iter.hasNext();) {
            Bucket bucket = iter.next();
            if (validChecker.test(bucket) || (!ignoreCanBePurged && !bucket.canBePurged(bucket.next == null))) {
                // Only remove invalid buckets from the next chain
                bucket.removeInvalidBucketsFromChain(validChecker, ignoreCanBePurged);
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
                    } else if (validChecker.test(bucket) || (!ignoreCanBePurged && !bucket.canBePurged(true))) {
                        // Set this one, instead. Do remove further next entries that aren't valid
                        bucket.removeInvalidBucketsFromChain(validChecker, ignoreCanBePurged);
                        iter.set(bucket);
                        cache.put(cacheKey, bucket);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void storeDetectorRegions(IntVector3 coordinates, DetectorRegion[] regions) {
        for (Bucket b = getOrCreateAtCoordinates(coordinates); b != null; b = b.next) {
            b.detectorRegions = (regions == null || regions.length == 0) ? RailLookup.NO_DETECTOR_REGIONS : regions;
        }
    }

    @Override
    public DetectorRegion[] getDetectorRegions(IntVector3 coordinates) {
        Bucket bucket = this.cache.get(createCacheKey(coordinates));
        return (bucket == null) ? RailLookup.NO_DETECTOR_REGIONS : bucket.detectorRegions;
    }

    @Override
    public Collection<IntVector3> getBlockIndex() {
        return this.cache.keySet();
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
        if (positionBlock == null) {
            if (!this.isValid()) {
                throw new ClosedException();
            }
        }

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
            super(WorldRailLookupImpl.this, offlineBlock, block, type);
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
                    if (member.isUnloaded() || member.getEntity().isRemoved()) {
                        iter.remove();
                        traincarts.log(Level.WARNING, "Purged unloaded minecart from rail cache at " +
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
         * @throws RailLookup.RailTypeNotRegisteredException If the specified rail type is not registered
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
         * @param ignoreCanBePurged Whether to ignore immutable buckets (members, metadata)
         */
        public void removeInvalidBucketsFromChain(Predicate<Bucket> validChecker, boolean ignoreCanBePurged) {
            Bucket curr = this;
            Bucket next;
            while ((next = curr.next) != null) {
                if (validChecker.test(next) || (!ignoreCanBePurged && !next.canBePurged(false))) {
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
            int lifeTimerAtPosition = RailLookup.lifeTimerAtPosition;
            if (this.rails_at_position_life >= lifeTimerAtPosition) {
                return this.rails_at_position;
            }
            this.rails_at_position_life = lifeTimerAtPosition;

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
         * @throws RailLookup.RailTypeNotRegisteredException If the specified rail type is not registered
         */
        private Bucket cloneAsType(RailType railType) {
            if (!railType.isRegistered()) {
                throw new RailLookup.RailTypeNotRegisteredException(railType);
            }
            Bucket newBucket = new Bucket(this.offlineBlock(), this.block(), railType);
            newBucket.detectorRegions = this.detectorRegions;
            return newBucket;
        }

        private Bucket[] computeRailsAtPosition() {
            // Query the registered Rail Types for whether they exist at this position
            OfflineWorld offlineWorld = this.offlineWorld();
            Block positionBlock = this.block();

            // When no rails are found, the array is the NO_RAILS_AT_POSITION array. This will trigger another
            // lookup for rails the next tick.
            Bucket[] newRailsAtPosition = NO_RAILS_AT_POSITION;
            Bucket bucketInCache = this;

            try (Timings tim = TCTimings.RAILTYPE_FINDRAILINFO.start()) {
                for (RailType type : RailType.values()) {
                    try {
                        List<Block> rails = type.findRails(positionBlock);
                        if (!rails.isEmpty()) {
                            // During this we might end up deleting 'ourselves' if the rail type of this bucket is NONE,
                            // and a rail is found with the same block position as ourselves.
                            RailType bucketInCacheType = bucketInCache.type();

                            // Fill this array with the found buckets
                            int index = newRailsAtPosition.length;
                            newRailsAtPosition = Arrays.copyOf(newRailsAtPosition, index + rails.size());

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
                        }
                    } catch (Throwable t) {
                        RailType.handleCriticalError(type, t);
                    }
                }
            }

            return bucketInCache.rails_at_position = newRailsAtPosition;
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

            // Check that the rails type still exists at the position
            // Will always fail for RailType NONE, if that ever happens
            if (!this.type().isRail(this.block())) {
                // Clear all signs with a special array that indicates signs couldn't be calculated
                // If the rail type exists in the future, recalculates the signs properly
                this.signs = RailLookup.MISSING_RAILS_NO_SIGNS;

                // This sadly will result in another cache lookup, but as it only occurs when rails
                // go missing, it's not a big problem. We must return false so that during at-position
                // lookup it will actually recompute.
                this.rail_life = RailLookup.LIFE_TIMER_START;
                return false;
            }

            // Reset life timer on every access
            this.rail_life = RailLookup.verifyTimer;

            // Verify all signs we computed previously are still there
            // This is MISSING_RAILS_NO_SIGNS if previously the rails didn't exist
            TrackedSign[] signs = this.signs;
            if (signs == RailLookup.MISSING_RAILS_NO_SIGNS) {
                this.signs = RailLookup.discoverSignsAtRailPiece(this);
            } else {
                // Check all tracked signs to see if any of them have been removed or changed
                for (TrackedSign sign : signs) {
                    if (!sign.verify()) {
                        // Regenerate, the entire sign is gone, so there's likely more changes
                        this.signs = RailLookup.discoverSignsAtRailPiece(this);
                        break;
                    }
                }
            }

            // All good!
            return true;
        }

        @Override
        public boolean verifyExists() {
            return this.rail_life != RailLookup.LIFE_TIMER_DELETED;
        }

        @Override
        public void forceCacheVerification() {
            this.rail_life = RailLookup.LIFE_TIMER_START;
        }
    }

    /**
     * List cache used when generating the array of tracked signs
     */
    private static final class TrackedSignList implements AutoCloseable {
        private final List<TrackedSign> signs = new ArrayList<>();
        private RailPiece rail = null;

        public TrackedSignList start(RailPiece rail) {
            if (this.rail == null) {
                this.rail = rail;
                return this;
            } else {
                // Already opened, create a new one to prevent corruption
                TrackedSignList copy = new TrackedSignList();
                copy.rail = rail;
                return copy;
            }
        }

        @Override
        public void close() {
            this.signs.clear();
            this.rail = null;
        }

        public TrackedSign[] build() {
            List<TrackedSign> signs = this.signs;
            return signs.isEmpty() ? RailLookup.NO_SIGNS : signs.toArray(new TrackedSign[signs.size()]);
        }
    }
}
