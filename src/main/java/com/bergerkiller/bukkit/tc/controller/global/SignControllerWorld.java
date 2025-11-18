package com.bergerkiller.bukkit.tc.controller.global;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.logging.Level;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkNeighbourList;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkStateListener;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkStateTracker;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.ChunkUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;
import com.bergerkiller.bukkit.tc.controller.global.SignController.Entry;
import com.bergerkiller.bukkit.tc.utils.LongBlockCoordinates;

/**
 * Tracks the locations of signs and handles the redstone activation of them. Provides
 * an efficient lookup for use by the rail cache. A controller instance exists per
 * world and is tightly coupled with the rail lookup cache.
 */
public class SignControllerWorld {
    private static final Material WALL_SIGN_TYPE = getMaterial("LEGACY_WALL_SIGN");
    private static final Material SIGN_POST_TYPE = getMaterial("LEGACY_SIGN_POST");
    private final SignController controller;
    private final World world;
    private final OfflineWorld offlineWorld;
    private final LongHashMap<SignControllerChunk> signChunks = new LongHashMap<>();
    private final LongHashMap<SignController.EntryList> signsByNeighbouringBlock = new LongHashMap<>();
    private final ChunkFutureProvider chunkFutureProvider;
    private boolean needsInitialization;

    SignControllerWorld(SignController controller) {
        this.controller = controller;
        this.world = null;
        this.offlineWorld = OfflineWorld.NONE;
        this.chunkFutureProvider = null;
        this.needsInitialization = true;
    }

    SignControllerWorld(SignController controller, World world) {
        this.controller = controller;
        this.world = world;
        this.offlineWorld = OfflineWorld.of(world);
        this.chunkFutureProvider = ChunkFutureProvider.of(controller.getPlugin());
        this.needsInitialization = true;
    }

    public World getWorld() {
        return this.world;
    }

    public SignController getGlobalController() {
        return controller;
    }

    public TrainCarts getPlugin() {
        return controller.getPlugin();
    }

    public boolean isValid() {
        return this.offlineWorld.getLoadedWorld() == this.world;
    }

    /**
     * Gets whether this sign controller is enabled. If the World is disabled in Traincarts config,
     * this will return false to indicate no processing should occur.
     *
     * @return True if enabled, False if disabled
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * If this World Sign Controller has not yet been initialized, because of using
     * {@link SignController#forWorldSkipInitialization(World)}, performs that initialization now.
     * If this controller was already initialized once, this method does nothing.
     */
    public void initialize() {
        if (this.needsInitialization) {
            this.needsInitialization = false;
            if (this.isEnabled()) {
                for (Chunk chunk : this.world.getLoadedChunks()) {
                    this.loadChunk(chunk);
                }
            }
        }
    }

    /**
     * Looks up the signs that exist at, or neighbouring, the specified block.
     *
     * @param block Block middle
     * @param mustHaveSignActions Whether the signs to look for must have sign actions, such as
     *                            redstone change handlers or train activation.
     * @return Entries nearby
     */
    public SignController.Entry[] findNearby(Block block, boolean mustHaveSignActions) {
        if (!initializeNearbySigns(block.getX(), block.getY(), block.getZ(), 1, mustHaveSignActions)) {
            return SignController.EntryList.NONE.values();
        }

        return getNearbySignsUnsafe(LongBlockCoordinates.map(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * Looks up the signs that exist at, or neighbouring, the specified block.
     * <b>Does not initialize the by-block mapping for chunks nearby.</b>
     * The {@link #initializeNearbySigns(int, int, int, int, boolean)} method must
     * be called prior to initialize it as needed.
     *
     * @param blockCoordinatesKey Key created using {@link LongBlockCoordinates#map(int, int, int)}
     * @return Entries nearby
     */
    SignController.Entry[] getNearbySignsUnsafe(long blockCoordinatesKey) {
        return signsByNeighbouringBlock.getOrDefault(blockCoordinatesKey, SignController.EntryList.NONE).values();
    }

    /**
     * Looks up the entry for a specific sign
     *
     * @param signBlock Sign Block
     * @param mustHaveSignActions Whether the sign to look for must have sign actions, such as
     *                            redstone change handlers or train activation.
     * @return Entry if found, null otherwise
     */
    public SignController.Entry findForSign(Block signBlock, boolean mustHaveSignActions) {
        for (Entry entry : findNearby(signBlock, mustHaveSignActions)) {
            if (entry.sign.getBlock().equals(signBlock)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Calls a function on all signs at or neighbouring a specified block.
     * Before it calls the handler, verifies the sign still truly exists.
     *
     * @param block Near Block
     * @param mustHaveSignActions Whether the sign to look for must have sign actions, such as
     *                            redstone change handlers or train activation.
     * @param handler
     */
    public void forEachNearbyVerify(Block block, boolean mustHaveSignActions, Consumer<SignController.Entry> handler) {
        // Note: findNearby already does verification every tick as part of the chunk refreshing
        // This must happen there because whether entries can be found near blocks or not can also change
        // when the sign goes from being a TrainCarts sign to not.
        for (SignController.Entry entry : this.findNearby(block, mustHaveSignActions)) {
            if (mustHaveSignActions && !entry.hasSignActionEvents()) {
                continue;
            }
            handler.accept(entry);
        }
    }

    /**
     * Queries a sign column of signs starting at a Block, into the direction
     * specified. This is used to find the signs below/at a rail block.
     * Before it calls the handler, verifies the sign still truly exists.
     * Passes the tracked sign details, which has been verified to exist.
     *
     * @param block Column start block
     * @param direction Column direction
     * @param mustHaveSignActions Whether the signs to look for must have sign actions, such as
     *                            redstone change handlers or train activation.
     * @param handler Handler accepting the sign
     */
    public void forEachSignInColumn(Block block, BlockFace direction, boolean mustHaveSignActions, Consumer<SignChangeTracker> handler) {
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        int checkBorder = FaceUtil.isVertical(direction) ? 0 : 1;
        if (!initializeNearbySigns(bx, by, bz, checkBorder, mustHaveSignActions)) {
            return;
        }

        long key = LongBlockCoordinates.map(block.getX(), block.getY(), block.getZ());
        LongUnaryOperator shift = LongBlockCoordinates.shiftOperator(direction);
        int steps = 0;
        while (true) {
            boolean foundSigns = false;
            for (SignController.Entry entry : this.getNearbySignsUnsafe(key)) {
                if (verifySignColumnSlice(key, direction, steps == 0, entry)) {
                    foundSigns = true;

                    if (!mustHaveSignActions|| entry.hasSignActionEvents()) {
                        handler.accept(entry.sign);
                    }
                }
            }

            // If no signs found this step, and we've moved too far, stop
            if (!foundSigns && steps > 1) {
                break;
            }

            // Next block
            key = shift.applyAsLong(key);
            steps++;

            // Also increment bx/bz and load chunks when searching sideways (rare)
            // TODO: Optimize this? Not worth the hassle as it's not really used.
            if (!FaceUtil.isVertical(direction)) {
                bx += direction.getModX();
                bz += direction.getModZ();
                if (!initializeNearbySigns(bx, by, bz, checkBorder, mustHaveSignActions)) {
                    break;
                }
            }
        }
    }

    /**
     * Checks for a single block of a rail sign column, whether there are wall signs attached
     * to the column. Uses this cache, and verifies the signs truly do exist. For directions
     * other than up and down, also verifies the sign isn't attached to the block in the same
     * direction as the column.
     *
     * @param block Column start block
     * @param direction Column direction
     * @param mustHaveSignActions Whether the signs to look for must have sign actions, such as
     *                            redstone change handlers or train activation.
     * @return True if there are wall signs attached to this block, False if not
     */
    public boolean hasSignsAroundColumn(Block block, BlockFace direction, boolean mustHaveSignActions) {
        int checkBorder = FaceUtil.isVertical(direction) ? 0 : 1;
        if (!initializeNearbySigns(block.getX(), block.getY(), block.getZ(), checkBorder, mustHaveSignActions)) {
            return false;
        }

        long key = LongBlockCoordinates.map(block.getX(), block.getY(), block.getZ());
        for (SignController.Entry entry : this.getNearbySignsUnsafe(key)) {
            if (verifySignColumnSlice(key, direction, true, entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether at a block position, signs might be nearby.
     * Chunks that need to be checked are loaded (sync) as needed.
     * Every tick the sign entries in the chunk(s) are refreshed and checked for removal.
     *
     * @param x Search X-position
     * @param y Search Y-position
     * @param z Search Z-position
     * @param border X/Z chunk border check distance
     * @param mustHaveSignActions Whether the signs to look for must have sign actions, such as
     *                            redstone change handlers or train activation.
     * @return True if signs might be nearby. False if there definitely are no signs.
     */
    private boolean initializeNearbySigns(int x, int y, int z, int border, boolean mustHaveSignActions) {
        int serverTick = CommonUtil.getServerTicks();

        boolean result;
        int cx = x >> 4;
        int cz = z >> 4;
        result = getSignChunk(cx, cz).checkMayHaveSigns(x, y, z, mustHaveSignActions, serverTick);

        int bx = x & 0xF;
        if (bx <= border) {
            result |= getSignChunk(cx - 1, cz).checkMayHaveSigns(x, y, z, mustHaveSignActions, serverTick);
        } else if (bx >= (15-border)) {
            result |= getSignChunk(cx + 1, cz).checkMayHaveSigns(x, y, z, mustHaveSignActions, serverTick);
        }

        int bz = z & 0xF;
        if (bz <= border) {
            result |= getSignChunk(cx, cz - 1).checkMayHaveSigns(x, y, z, mustHaveSignActions, serverTick);
        } else if (bz >= (15-border)) {
            result |= getSignChunk(cx, cz + 1).checkMayHaveSigns(x, y, z, mustHaveSignActions, serverTick);
        }

        return result;
    }

    /**
     * Verifies whether a particular sign is a part of a rail sign column, or not.
     *
     * @param key Block coordinate key
     * @param direction Sign column direction
     * @param firstLayer Whether this is the first layer being checked. Includes special logic
     *                   to allow sign posts to function without duplicate activation.
     * @param entry Current sign entry
     * @return True if the sign is a part of the column slice, False if not
     */
    private boolean verifySignColumnSlice(long key, BlockFace direction, boolean firstLayer, SignController.Entry entry) {
        // Find relative direction the sign is at
        BlockFace offset = LongBlockCoordinates.findDirection(entry.blockKey, key);
        if (offset == null) {
            return false;
        }

        // Retrieve BlockData. Check sign is part of the column of signs being checked.
        BlockData blockData = entry.sign.getBlockData();
        if (blockData.isType(SIGN_POST_TYPE)) {
            // When checking downwards, it normally would encounter the sign post before encountering
            // the block it is attached to. This is legacy behavior that must stay functioning.
            // For this reason, we 'trigger' this sign when we encounter it, rather than when encountering
            // the block it is attached to.
            //
            // However, if we start checking (first layer) we have not yet encountered sign posts above the
            // rail block. So still allow those to be activated the traditional way.
            if (direction == BlockFace.DOWN) {
                return (offset == BlockFace.SELF) ||
                       (firstLayer && offset == BlockFace.DOWN);
            }

            // When going up or sideways, activate sign posts when they stand on top of the block currently being checked
            // For the first layer, also allow the sign post put at the current rail block to be activated
            return (offset == BlockFace.DOWN) ||
                   (firstLayer && offset == BlockFace.SELF);
        } else if (blockData.isType(WALL_SIGN_TYPE)) {
            // Skip if positioned in the path we are checking
            if (offset == direction || offset == direction.getOppositeFace()) {
                return false;
            }

            BlockFace facing = blockData.getAttachedFace();
            return facing == offset || facing == direction.getOppositeFace();
        } else {
            // Doesn't map to either legacy wall or sign post type
            // Assume it's not a sign at all and remove it
            entry.removeInvalidEntry();
            return false;
        }
    }

    /**
     * Checks the 6 blocks neighbouring a particular block for the placement of a new sign
     * not yet known to this controller through the usual events.
     *
     * @param around
     */
    public void detectNewSigns(Block around) {
        long blockKey = LongBlockCoordinates.map(around);

        final Entry[] nearby = initializeNearbySigns(around.getX(), around.getY(), around.getZ(), 1, false)
                ? getNearbySignsUnsafe(blockKey) : SignController.EntryList.NONE.unsortedValues();

        LongBlockCoordinates.forAllBlockSidesAndSelf(blockKey, (face, key) -> {
            // Check not already in the nearby mapping
            for (Entry e : nearby) {
                if (e.blockKey == key) {
                    return;
                }
            }

            // If chunk is actually loaded, check if there is an actual sign here
            int bx = around.getX() + face.getModX();
            int by = around.getY() + face.getModY();
            int bz = around.getZ() + face.getModZ();
            Chunk chunk = WorldUtil.getChunk(world, bx >> 4, bz >> 4);
            if (chunk == null) {
                return;
            }
            if (!MaterialUtil.ISSIGN.get(ChunkUtil.getBlockData(chunk, bx, by, bz))) {
                return;
            }

            // Missing entry! Add one now, but do so next tick in case text isn't loaded onto it yet.
            final Block potentialSign = around.getRelative(face);
            new Task(controller.getPlugin()) {
                @Override
                public void run() {
                    if (MaterialUtil.ISSIGN.get(potentialSign)) {
                        addSign(potentialSign, false, true);
                    }
                }
            }.start();
        });
    }

    /**
     * Refreshes whether the signs on this world have particular sign actions. Called after a sign action
     * is registered or un-registered.
     */
    public void redetectSignActions() {
        for (SignControllerChunk chunk : signChunks.values()) {
            for (Entry entry : chunk.getEntries()) {
                entry.redetectSignActions();
            }
        }
    }

    /**
     * Starts tracking a newly placed sign. Initializes power state, but does not fire any events.
     * If the sign was already tracked, returns the existing entry instead. If the sign could not
     * be found at this block, returns null.
     *
     * @param signBlock Block of the sign that was placed
     * @param isSignChange Whether the sign was added as part of a sign change event.
     *                     If this is the case, pre-existing signs are destroyed first.
     *                     This is to support the use of sign-edit plugins.
     * @param frontText If isSignChange is true, specifies whether the front changed (true)
     *                  or the back (false).
     * @return entry for the sign, null if not a sign
     */
    public SignController.Entry addSign(Block signBlock, boolean isSignChange, boolean frontText) {
        // Find/activate an existing sign
        SignController.Entry existing = this.findForSign(signBlock, false);
        if (existing != null) {
            if (isSignChange) {
                if (existing.verifyBeforeSignChange(frontText)) {
                    return existing;
                } else {
                    existing.removeInvalidEntry();
                    existing = null;
                }
            }

            if (existing != null) {
                if (existing.verify()) {
                    controller.activateEntry(existing, true, true);
                    return existing;
                } else {
                    existing.removeInvalidEntry();
                    existing = null;
                }
            }
        }

        // Add a new one. Lines of text might be wiped initially.
        Sign sign = BlockUtil.getSign(signBlock);
        if (sign == null) {
            return null;
        } else {
            return createNewSign(sign, isSignChange);
        }
    }

    /**
     * Initializes a new sign that was just placed in the world. Not called when signs are
     * loaded in as new chunks/worlds load in.
     *
     * @param sign Sign
     * @param isSignChange Whether the sign is created as part of a sign change event.
     *                     If this is the case, no sign load events are fired, as those
     *                     are part of the sign change building events already.
     * @return Entry created for this new sign
     */
    private SignController.Entry createNewSign(Sign sign, boolean isSignChange) {
        Block signBlock = sign.getBlock();

        SignControllerChunk signChunk = this.getSignChunk(
                MathUtil.toChunk(signBlock.getX()),
                MathUtil.toChunk(signBlock.getZ()));

        // Create entry
        SignController.Entry entry = this.controller.createEntry(sign,
                this,
                signChunk,
                LongBlockCoordinates.map(signBlock.getX(), signBlock.getY(), signBlock.getZ()));

        // Add it to the chunk.
        // If the chunk initialized the by-neighbour-mapping before, adds it there too
        signChunk.addEntry(entry);

        this.controller.activateEntry(entry, true, !isSignChange);

        return entry;
    }

    /**
     * Refreshes the signs that exist in a particular chunk. This is used for the debug
     * command, and for use by external plugins that modify/place signs in weird ways.
     *
     * @param chunk Chunk
     * @return Information about the number of signs added/removed thanks to refreshing
     */
    public RefreshResult refreshInChunk(Chunk chunk) {
        long chunkKey = SignControllerChunk.getKeyOf(chunk);

        // Verify existence of signs we already had. Remove if missing.
        int numRemoved = 0;
        {
            SignControllerChunk signChunk = this.signChunks.get(chunkKey);

            if (signChunk != null && signChunk.hasSigns()) {
                for (SignController.Entry entry : signChunk.getEntries()) {
                    if (entry.verify()) {
                        continue;
                    }

                    // Remove loaded sign information
                    signChunk.removeEntry(entry);

                    // Remove from the offline signs cache as well
                    controller.getPlugin().getOfflineSigns().removeAll(entry.sign.getBlock());
                    numRemoved++;

                    // Event handling
                    entry.onRemoved();
                }
            }
        }

        // Try to add signs we didn't already have
        int numAdded = 0;
        for (BlockState blockState : this.getBlockStatesSafe(chunk)) {
            if (blockState instanceof Sign) {
                Block signBlock = blockState.getBlock();
                SignController.Entry existing = this.findForSign(signBlock, false);
                if (existing != null) {
                    controller.activateEntry(existing);
                    continue;
                }

                this.createNewSign((Sign) blockState, false);
                numAdded++;
            }
        }

        return new RefreshResult(numAdded, numRemoved);
    }

    /**
     * Removes all data cached/stored in this World
     */
    void clear() {
        for (SignControllerChunk chunk : this.signChunks.values()) {
            for (SignController.Entry e : chunk.getEntries()) {
                e.onRemoved();
            }
        }
        this.signChunks.clear();
        this.signsByNeighbouringBlock.clear();
    }

    /**
     * Called once a chunk and all it's 8 neighbouring chunks are loaded.
     * This should activate any previously added signs in this chunk.
     *
     * @param chunk
     */
    private void activateSignsInChunk(Chunk chunk) {
        // Use verifyEntry which updates the sign state, important when activating
        changeActiveForEntriesInChunk(chunk, true, SignController.Entry::verify, this.controller::activateEntry);
    }

    /**
     * Called once a chunk, or one of it's 8 neighbouring chunks, unloads.
     * This should de-activate any previously activated signs.
     * It does not yet remove the signs (but this may have already happened).
     *
     * @param chunk
     */
    private void deactivateSignsInChunk(Chunk chunk) {
        // Use isRemoved() because we just want to know whether the sign is there to avoid NPE
        changeActiveForEntriesInChunk(chunk, false, e -> !e.sign.isRemoved(), Entry::deactivate);
    }

    private void changeActiveForEntriesInChunk(
            Chunk chunk,
            boolean activating,
            Predicate<SignController.Entry> verify,
            Consumer<SignController.Entry> handler
    ) {
        SignControllerChunk signChunk = this.signChunks.get(chunk.getX(), chunk.getZ());
        if (signChunk == null || !signChunk.hasSigns()) {
            return;
        }

        int retryLimit = 100;
        while (true) {
            Entry[] entries = signChunk.getEntries();

            // Check that any of the entries need activating/de-activating at all
            {
                boolean hasEntriesToHandle = false;
                for (SignController.Entry entry : entries) {
                    if (entry.front.activated != activating || entry.back.activated != activating) {
                        hasEntriesToHandle = true;
                        break;
                    }
                }
                if (!hasEntriesToHandle) {
                    break;
                }
            }

            // Prevent a crash if anything goes wrong here
            if (--retryLimit == 0) {
                controller.getPlugin().log(Level.SEVERE, "Infinite loop " +
                        (activating ? "activating" : "de-activating") +
                        " signs in chunk [" + chunk.getX() + "/" + chunk.getZ() + "]. Signs:");
                for (SignController.Entry entry : entries) {
                    controller.getPlugin().log(Level.SEVERE, "- at " + entry.sign.getBlock());
                }
                break;
            }

            // De-activate or activate all entries
            // We might find some signs become invalid - remove from original list.
            // Risks concurrent modification if SignAction loadedChanged modifies this,
            // so iterate a copy.
            for (SignController.Entry entry : entries) {
                if (entry.front.activated != activating || entry.back.activated != activating) {
                    if (verify.test(entry)) {
                        // Callbacks
                        handler.accept(entry);
                    } else {
                        // Sign is gone. Remove it.
                        // Won't modify the immutable entries array.
                        entry.removeInvalidEntry();
                    }
                }
            }
        }
    }

    private SignControllerChunk getSignChunk(int cx, int cz) {
        long key = MathUtil.longHashToLong(cx, cz);
        SignControllerChunk signChunk;
        if ((signChunk = this.signChunks.get(key)) == null) {
            // Note: On Paper, getChunkAt() will already fire ChunkLoad, and thus loadChunk
            //       Because of this, the extra loadChunk will do nothing (already loaded, ignore)
            //       On Spigot however, this does not happen, and the extra loadChunk() is required.
            signChunk = this.loadChunk(world.getChunkAt(cx, cz));
        }

        return signChunk;
    }

    /**
     * Adds data about signs stored in a particular chunk
     *
     * @param chunk
     * @return SignControllerChunk with loaded sign details
     */
    SignControllerChunk loadChunk(Chunk chunk) {
        long chunkKey = SignControllerChunk.getKeyOf(chunk);

        // If this sign cache hasn't been initialized with all loaded chunks yet, don't do anything here
        if (this.needsInitialization) {
            return new SignControllerChunk(chunkKey);
        }

        // The loadChunk() always fires twice: once when requested from a lookup function of this
        // controller, again because of the ChunkLoadEvent. The behavior and timing of these
        // things differ between Paper and Spigot. The below check ensures initialization
        // of the chunk-sign metadata only occurs once.
        {
            SignControllerChunk existingSignChunk = this.signChunks.get(chunkKey);
            if (existingSignChunk != null) {
                return existingSignChunk;
            }
        }

        SignControllerChunk newSignChunk = new SignControllerChunk(chunkKey);
        List<SignController.Entry> newEntriesAtChunk = Collections.emptyList();
        for (BlockState blockState : getBlockStatesSafe(chunk)) {
            if (blockState instanceof Sign) {
                SignController.Entry entry = this.controller.createEntry((Sign) blockState,
                        this,
                        newSignChunk,
                        LongBlockCoordinates.map(blockState.getX(), blockState.getY(), blockState.getZ()));
                if (newEntriesAtChunk.isEmpty()) {
                    newEntriesAtChunk = new ArrayList<>();
                }
                newEntriesAtChunk.add(entry);
            }
        }

        newSignChunk.initialize(newEntriesAtChunk);

        this.signChunks.put(chunkKey, newSignChunk);

        // Once all this chunk's neighbours are loaded as well, initialize the initial power state of the sign
        this.chunkFutureProvider.trackNeighboursLoaded(chunk, ChunkNeighbourList.neighboursOf(chunk, 1), new ChunkStateListener() {
            @Override
            public void onRegistered(ChunkStateTracker tracker) {
                if (tracker.isLoaded()) {
                    onLoaded(tracker);
                }
            }

            @Override
            public void onCancelled(ChunkStateTracker tracker) {
            }

            @Override
            public void onLoaded(ChunkStateTracker tracker) {
                //System.out.println("All neighbours loaded: " + tracker.getChunk());
                activateSignsInChunk(tracker.getChunk());
            }

            @Override
            public void onUnloaded(ChunkStateTracker tracker) {
                deactivateSignsInChunk(tracker.getChunk());
            }
        });

        return newSignChunk;
    }

    private Collection<BlockState> getBlockStatesSafe(Chunk chunk) {
        try {
            return WorldUtil.getBlockStates(chunk);
        } catch (Throwable t) {
            this.controller.getPlugin().getLogger().log(Level.SEVERE, "Error reading sign block states in chunk " + chunk.getWorld().getName() +
                    " [" + chunk.getX() + "/" + chunk.getZ() + "]", t);
            return Collections.emptyList();
        }
    }

    void addChunkByBlockEntry(final SignController.Entry entry, long key) {
        this.signsByNeighbouringBlock.merge(key, entry.singletonList, (a, b) -> a.add(entry));
    }

    /**
     * Orders to delete cached sign information about a particular chunk
     *
     * @param chunk
     */
    void unloadChunk(Chunk chunk) {
        // If this sign cache hasn't been initialized with all loaded chunks yet, don't do anything here
        if (this.needsInitialization) {
            return;
        }

        SignControllerChunk signChunk = this.signChunks.remove(chunk.getX(), chunk.getZ());
        if (signChunk != null && signChunk.hasSigns()) {
            // Remove all entries from the by-neighbour-block mapping
            for (SignController.Entry entry : signChunk.getEntries()) {
                // De-activate first, if it was activated still
                if (!entry.sign.isRemoved()) {
                    entry.deactivate();
                }

                entry.unregisterInNeighbouringBlocks(true);
            }
        }
    }

    protected void removeChunkByBlockEntry(SignController.Entry entry, long key) {
        removeChunkByBlockEntry(entry, key, false);
    }

    protected void removeChunkByBlockEntry(SignController.Entry entry, long key, boolean purgeAllInSameChunk) {
        SignController.EntryList oldEntryList = this.signsByNeighbouringBlock.remove(key);
        if (oldEntryList == null || (purgeAllInSameChunk && LongBlockCoordinates.getChunkEdgeDistance(key) >= 2)) {
            return;
        }

        // Slow method - we have to remove entries at chunk boundaries carefully, so we don't touch
        // entries that refer to signs in neighbouring (still loaded!) chunks.
        SignController.EntryList newEntryList = oldEntryList.filter(
                e -> e != entry && (!purgeAllInSameChunk || e.chunk != entry.chunk));

        // Put if there are entries to keep
        if (newEntryList.count() > 0) {
            this.signsByNeighbouringBlock.put(key, newEntryList);
        }
    }

    static class SignControllerWorldDisabled extends SignControllerWorld {

        SignControllerWorldDisabled(SignController controller, World world) {
            super(controller, world);
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public SignController.Entry[] findNearby(Block block, boolean mustHaveSignActions) {
            return SignController.EntryList.NONE.values();
        }

        @Override
        public SignController.Entry addSign(Block signBlock, boolean handleLoadChange, boolean frontText) {
            return null;
        }

        @Override
        public RefreshResult refreshInChunk(Chunk chunk) {
            return RefreshResult.NONE;
        }

        @Override
        SignControllerChunk loadChunk(Chunk chunk) {
            return new SignControllerChunk(SignControllerChunk.getKeyOf(chunk));
        }

        @Override
        void unloadChunk(Chunk chunk) {}
    }

    public static class RefreshResult {
        public static final RefreshResult NONE = new RefreshResult(0, 0);
        public final int numAdded, numRemoved;

        public RefreshResult(int numAdded, int numRemoved) {
            this.numAdded = numAdded;
            this.numRemoved = numRemoved;
        }

        public RefreshResult add(RefreshResult other) {
            return new RefreshResult(this.numAdded + other.numAdded,
                                     this.numRemoved + other.numRemoved);
        }
    }
}
