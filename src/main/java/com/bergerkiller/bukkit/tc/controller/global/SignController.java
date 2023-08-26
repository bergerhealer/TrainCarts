package com.bergerkiller.bukkit.tc.controller.global;

import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.collections.FastTrackedUpdateSet;
import com.bergerkiller.bukkit.common.component.LibraryComponent;
import com.bergerkiller.bukkit.common.events.MultiBlockChangeEvent;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.SignActionHeader;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld.RefreshResult;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

public class SignController implements LibraryComponent, Listener {
    private final TrainCarts plugin;
    private final SignControllerWorld NONE = new SignControllerWorld(this); // Dummy
    private final IdentityHashMap<World, SignControllerWorld> byWorld = new IdentityHashMap<>();
    private final FastTrackedUpdateSet<Entry> pendingRedstoneUpdates = new FastTrackedUpdateSet<Entry>();
    private final FastTrackedUpdateSet<Entry> ignoreRedstoneUpdates = new FastTrackedUpdateSet<Entry>();
    private final boolean blockPhysicsFireForSigns;
    private boolean disabled = false;
    private SignControllerWorld byWorldLastGet = NONE;
    private final RedstoneUpdateTask updateTask;
    private boolean redstonePhysicsSuppressed = false;

    public SignController(TrainCarts plugin) {
        this.plugin = plugin;
        this.updateTask = new RedstoneUpdateTask(plugin);
        this.blockPhysicsFireForSigns = doesBlockPhysicsFireForSigns();
    }

    public TrainCarts getPlugin() {
        return plugin;
    }

    @Override
    public void enable() {
        plugin.register(this);
        updateTask.start(1, 1);
    }

    @Override
    public void disable() {
        byWorld.values().forEach(SignControllerWorld::clear);
        byWorld.clear();
        pendingRedstoneUpdates.clear();
        byWorldLastGet = NONE;
        updateTask.stop();
        disabled = true;
    }

    /**
     * Supresses all redstone-related events being handled while a runnable
     * does stuff.
     *
     * @param runnable
     */
    public void suppressRedstonePhysicsDuring(Runnable runnable) {
        if (redstonePhysicsSuppressed) {
            runnable.run();
        } else {
            try {
                redstonePhysicsSuppressed = true;
                runnable.run();
            } finally {
                redstonePhysicsSuppressed = false;
            }
        }
    }

    /**
     * Gets the SignController for a certain World
     *
     * @param world
     * @return SignController
     */
    public SignControllerWorld forWorld(World world) {
        SignControllerWorld c = byWorldLastGet;
        if (c.getWorld() == world) {
            return c;
        } else if ((c = byWorld.get(world)) != null) {
            return byWorldLastGet = c;
        } else if (disabled) {
            throw new IllegalStateException("Can't use SignController, Traincarts is disabled!");
        } else {
            if (TrainCarts.isWorldDisabled(world)) {
                c = new SignControllerWorld.SignControllerWorldDisabled(SignController.this, world);
            } else {
                c = new SignControllerWorld(SignController.this, world);
            }
            byWorld.put(world, c);
            byWorldLastGet = c;
            c.initialize();
            return c;
        }
    }

    /**
     * Gets the SignController for a certain World, and if one doesn't already exists, creates
     * a new one without initializing the sign that are already loaded. The caller should call
     * {@link SignControllerWorld#initialize()} once that can safely be done.<br>
     * <br>
     * Internal use only.
     *
     * @param world
     * @return SignController
     */
    public SignControllerWorld forWorldSkipInitialization(World world) {
        SignControllerWorld c = byWorldLastGet;
        if (c.getWorld() == world) {
            return c;
        } else if ((c = byWorld.get(world)) != null) {
            return byWorldLastGet = c;
        } else if (disabled) {
            throw new IllegalStateException("Can't use SignController, Traincarts is disabled!");
        } else {
            if (TrainCarts.isWorldDisabled(world)) {
                c = new SignControllerWorld.SignControllerWorldDisabled(SignController.this, world);
            } else {
                c = new SignControllerWorld(SignController.this, world);
            }
            byWorld.put(world, c);
            byWorldLastGet = c;
            return c;
        }
    }

    private SignControllerWorld tryGetForWorld(World world) {
        SignControllerWorld c = byWorldLastGet;
        if (c.getWorld() != world) {
            c = byWorld.get(world);
            if (c != null) {
                byWorldLastGet = c;
            }
        }
        return c;
    }

    /**
     * Calls a function on all signs at or neighbouring a specified block.
     * Before it calls the handler, verifies the sign still truly exists.
     *
     * @param block
     * @param handler
     */
    public void forEachNearbyVerify(Block block, Consumer<SignController.Entry> handler) {
        forWorld(block.getWorld()).forEachNearbyVerify(block, handler);
    }

    /**
     * Ignores signs of current-tick redstone changes caused by the lever
     *
     * @param lever to ignore
     */
    public void ignoreOutputLever(Block lever) {
        final Block att = BlockUtil.getAttachedBlock(lever);

        // Check whether there are any signs attached to the same block the lever is
        forEachNearbyVerify(att, entry -> {
            // If attached to the same block as the lever, ignore
            if (entry.sign.isAttachedTo(att)) {
                entry.ignoreRedstone();
            }
        });
    }

    /**
     * Refreshes the signs that exist in a particular chunk. This is used for the debug
     * command, and for use by external plugins that modify/place signs in weird ways.
     *
     * @param chunk Chunk
     * @return Information about the number of signs added/removed thanks to refreshing
     */
    public RefreshResult refreshInChunk(Chunk chunk) {
        return forWorld(chunk.getWorld()).refreshInChunk(chunk);
    }

    /**
     * Notifies that a sign changed. This can mean the sign was removed from the server, or that
     * the text contents have changed. Will perform an internal update, as well inform the
     * offline sign metadata store about the changes.
     *
     * @param tracker The sign change tracker that noticed a change
     */
    public void notifySignChanged(SignChangeTracker tracker) {
        SignControllerWorld worldController = forWorld(tracker.getWorld());
        Entry entry = worldController.findForSign(tracker.getBlock());
        if (entry != null) {
            if (entry.sign != tracker) {
                entry.sign.update();
            }
            if (!entry.verifyAfterUpdate(true, true)) {
                worldController.removeInvalidEntry(entry);
            }
        }
    }

    /**
     * Deletes old SignController instances from memory that are for Worlds that have unloaded.
     * Avoids potential memory leaks.
     */
    private void cleanupUnloaded() {
        for (Iterator<SignControllerWorld> iter = byWorld.values().iterator(); iter.hasNext();) {
            SignControllerWorld controller = iter.next();
            if (!controller.isValid()) {
                iter.remove();
                byWorldLastGet = NONE;
                controller.clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onWorldInit(WorldInitEvent event) {
        forWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onWorldUnload(WorldUnloadEvent event) {
        // We might be doing some sign logic during unloading, so don't remove cache right away
        final World world = event.getWorld();
        CommonUtil.nextTick(() -> {
            SignControllerWorld controller = byWorld.remove(world);
            if (controller != null) {
                controller.clear();
                byWorldLastGet = NONE;
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onChunkLoad(ChunkLoadEvent event) {
        forWorld(event.getWorld()).loadChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onChunkUnload(ChunkUnloadEvent event) {
        SignControllerWorld controller = tryGetForWorld(event.getWorld());
        if (controller != null) {
            controller.unloadChunk(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlaceSignCheck(BlockPlaceEvent event) {
        Sign sign;
        if (
            !event.canBuild() ||
            TrainCarts.isWorldDisabled(event) ||
            !MaterialUtil.ISSIGN.get(event.getBlockPlaced()) ||
            (sign = BlockUtil.getSign(event.getBlockPlaced())) == null
        ) {
            return;
        }

        // Mock a sign change event to handle building it
        SignChangeEvent change_event = new SignChangeEvent(
                event.getBlockPlaced(),
                event.getPlayer(),
                sign.getLines());
        handleSignChange(change_event);

        // If cancelled, cancel block placement too
        if (change_event.isCancelled()) {
            event.setBuild(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (TrainCarts.isWorldDisabled(event)) {
            return;
        }

        handleSignChange(event);

        // This stuff only occurs on <= 1.19.4. On 1.20 the sign change is separate
        // from sign placement, so we shouldn't break the sign at all.
        if (event.isCancelled() && !CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            // Properly give the sign back to the player that placed it
            // We do not want to place down an empty sign, that is annoying
            // If this is impossible for whatever reason, just drop it
            Material signBlockType = event.getBlock().getType();
            if (!Util.canInstantlyBuild(event.getPlayer()) && MaterialUtil.ISSIGN.get(signBlockType)) {
                // Find the type of item matching the sign type
                Material signItemType;
                if (signBlockType == MaterialUtil.getMaterial("LEGACY_SIGN_POST")
                        || signBlockType == MaterialUtil.getMaterial("LEGACY_WALL_SIGN")
                ) {
                    // Legacy (pre-1.13 support)
                    signItemType = MaterialUtil.getFirst("OAK_SIGN", "LEGACY_SIGN");
                } else if (signBlockType.name().contains("_WALL_")) {
                    // BIRCH_WALL_SIGN -> BIRCH_SIGN
                    signItemType = MaterialUtil.getMaterial(signBlockType.name().replace("_WALL_", "_"));
                    if (signItemType == null) {
                        // Fallback to at least return 'a' sign
                        signItemType = MaterialUtil.getFirst("OAK_SIGN", "LEGACY_SIGN");
                    }
                } else {
                    // Same as the sign block type
                    signItemType = signBlockType;
                }

                ItemStack item = HumanHand.getItemInMainHand(event.getPlayer());
                if (LogicUtil.nullOrEmpty(item)) {
                    HumanHand.setItemInMainHand(event.getPlayer(), new ItemStack(signItemType, 1));
                } else if (MaterialUtil.isType(item, signItemType) && item.getAmount() < ItemUtil.getMaxSize(item)) {
                    ItemUtil.addAmount(item, 1);
                    HumanHand.setItemInMainHand(event.getPlayer(), item);
                } else {
                    // Drop the item
                    Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
                    loc.getWorld().dropItemNaturally(loc, new ItemStack(signItemType, 1));
                }
            }

            // Break the block
            event.getBlock().setType(Material.AIR);
        }
    }

    private void handleSignChange(SignChangeEvent event) {
        // Reset cache to make sure all signs are recomputed later, after the sign was made
        // Doing it here, in the most generic case, so that custom addon signs are also refreshed
        // TODO: Maybe only recalculate the rails impacted, or nearby the sign? This could be costly.
        //
        // NOW DISABLED. Handled by sign-adding logic in the sign controller now.
        //RailLookup.forceRecalculation();

        // Before handling placement, create an already-activated entry for this sign
        // This makes sure that, if during the build handling the rail is requested or some
        // complex logic occurs involving it, the sign can be found.
        //
        // No loadedChanged() event is fired, as handleBuild() already handles that there.
        SignControllerWorld controller = this.forWorld(event.getBlock().getWorld());
        Entry newSignEntry = controller.addSign(event.getBlock(), true, BlockUtil.isChangingFrontLines(event));

        // Handle building the sign. Might cancel it (permissions)
        SignAction.handleBuild(event);

        // If not cancelled, update later so the true text is known
        if (newSignEntry != null && !event.isCancelled()) {
            newSignEntry.updateRedstoneLater();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        SignControllerWorld controller = forWorld(block.getWorld());
        Entry e = controller.findForSign(block);
        if (e != null) {
            // Make sure before true handling is done, we update the sign itself
            // That way we know what the text was after the sign is destroyed
            if (!e.verify()) {
                return;
            }

            // This will check it's still there later on
            // If not, a sign destroy is handled automatically
            e.updateRedstoneLater();
        }
    }

    // This is also needed to support block placement / piston / WR / etc.
    @EventHandler(priority = EventPriority.MONITOR)
    private void onBlockPhysics(BlockPhysicsEvent event) {
        if (redstonePhysicsSuppressed) {
            return;
        }

        Block block = event.getBlock();
        SignControllerWorld controller = forWorld(block.getWorld());

        if (MaterialUtil.ISSIGN.get(event.getChangedType())) {
            controller.detectNewSigns(block);
        }

        if (this.blockPhysicsFireForSigns) {
            // Check block is a sign
            Entry e = controller.findForSign(block);
            if (e != null) {
                e.updateRedstoneLater();
            }
        } else {
            // Check signs are nearby
            for (Entry e : controller.findNearby(block)) {
                e.updateRedstoneLater();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockRedstoneChange(BlockRedstoneEvent event) {
        if (redstonePhysicsSuppressed || TrainCarts.isWorldDisabled(event)) {
            return;
        }

        // Refresh nearby signs
        {
            Block block = event.getBlock();
            for (Entry e : forWorld(block.getWorld()).findNearby(block)) {
                e.updateRedstoneLater();
            }
        }

        // If lever, suppress changes on signs nearby (self-triggering)
        BlockData event_block_data = WorldUtil.getBlockData(event.getBlock());
        if (event_block_data.isType(Material.LEVER)) {
            final Block leverBlock = event.getBlock();
            final boolean isPowered = event.getNewCurrent() > 0;
            this.forEachNearbyVerify(leverBlock, entry -> {
                Block signBlock = entry.getBlock();
                if (leverBlock.getX() == signBlock.getX() &&
                    leverBlock.getZ() == signBlock.getZ() &&
                    Math.abs(leverBlock.getY() - signBlock.getY()) == 1
                ) {
                    entry.updateRedstonePowerVerify(isPowered);
                }
            });
            ignoreOutputLever(event.getBlock());
        }
    }

    // Triggered by things like WorldEdit editing
    @EventHandler(priority = EventPriority.MONITOR)
    private void onMultiBlockChange(MultiBlockChangeEvent event) {
        SignControllerWorld worldController = forWorld(event.getWorld());
        for (IntVector2 chunkCoord : event.getChunkCoordinates()) {
            Chunk chunk = WorldUtil.getChunk(event.getWorld(), chunkCoord.x, chunkCoord.z);
            if (chunk != null) {
                worldController.refreshInChunk(chunk);
            }
        }
    }

    Entry createEntry(Sign sign, SignControllerWorld world, long blockKey, long chunkKey) {
        return new Entry(sign, world, blockKey, chunkKey, this);
    }

    void activateEntry(Entry entry) {
        activateEntry(entry, false, true, true, true);
    }

    void activateEntry(Entry entry, boolean refreshRailSigns, boolean handleLoadChange, boolean activateFront, boolean activateBack) {
        // Refresh signs mapped to rails at all times, if specified
        // The tracked rail is made available later on to handle the load change, if set
        TrackedSign frontTrackedSign = null, backTrackedSign = null;
        if (refreshRailSigns) {
            if (activateFront && !entry.front.getHeader().isEmpty()) {
                frontTrackedSign = TrackedSign.forRealSign(entry.sign.getSign(), true, null);
            }
            if (activateBack && !entry.back.getHeader().isEmpty()) {
                backTrackedSign = TrackedSign.forRealSign(entry.sign.getSign(), false, null);
            }

            // Both use the same Rails guaranteed, so only verify once
            if (frontTrackedSign != null) {
                frontTrackedSign.getRail().forceCacheVerification();
            } else if (backTrackedSign != null) {
                backTrackedSign.getRail().forceCacheVerification();
            }
        }

        // Skip if there is nothing to do
        boolean wasFrontActivated = entry.front.activated;
        boolean wasBackActivated = entry.back.activated;
        if ((!activateFront || wasFrontActivated) && (!activateBack || wasBackActivated)) {
            return;
        }

        Block b = entry.sign.getBlock();
        try {
            entry.setActivated(activateFront, activateBack);

            if (handleLoadChange) {
                if (refreshRailSigns) {
                    if (frontTrackedSign != null && !wasFrontActivated) {
                        SignAction.handleLoadChange(frontTrackedSign, true);
                    }
                    if (backTrackedSign != null && !wasBackActivated) {
                        SignAction.handleLoadChange(backTrackedSign, true);
                    }
                } else {
                    if (activateFront && !wasFrontActivated) {
                        entry.front.handleLoadChange(true);
                    }
                    if (activateBack && !wasBackActivated) {
                        entry.back.handleLoadChange(true);
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error while initializing sign in world " +
                    b.getWorld().getName() + " at " + b.getX() + " / " + b.getY() + " / " + b.getZ(), t);
        }
    }

    /**
     * Updates the sign's redstone state right away, firing a redstone change
     * event.
     */
    private void updateRedstoneNow(Entry entry) {
        // Lever-triggered changes are ignored for one tick
        if (entry.ignoreRedstoneUpdateTracker.isSet()) {
            return;
        }

        // Update sign text/information and check it still actually exists
        if (!entry.verify()) {
            return;
        }

        // All good. Update redstone power now.
        entry.updateRedstonePower();
    }

    /**
     * Checks whether the BlockPhysicsEvent fires for signs on the current server in use.
     * This stuff broke on Minecraft 1.19
     *
     * @return True if block physics fire for signs
     */
    private static boolean doesBlockPhysicsFireForSigns() {
        // No problem on 1.18.2 and before
        if (Common.evaluateMCVersion("<=", "1.18.2")) {
            return true;
        }

        // Only fixed on Paper servers 1.19.3+, or 1.19.2 build 165 onwards
        if (Common.IS_PAPERSPIGOT_SERVER) {
            if (Common.evaluateMCVersion(">=", "1.19.3")) {
                // Fixed here for sure
                return true;
            } else if (Common.evaluateMCVersion("==", "1.19.2")) {
                // Assume most people use a most up to date server
                // Only when we detect a server older than it do we return false

                // Fixed on: git-Paper-165 (MC: 1.19.2)
                if (checkBuildNumberLessThan("Paper", 165)) {
                    return false;
                }

                // Fixed on: git-Purpur-1788 (MC: 1.19.2)
                if (checkBuildNumberLessThan("Purpur", 1788)) {
                    return false;
                }

                return true;
            }
        }

        return false;
    }

    private static boolean checkBuildNumberLessThan(String serverName, int buildNumberThreshold) {
        Matcher m;
        String version = Bukkit.getVersion();
        if (version != null && (m = Pattern.compile("^git-" + serverName + "-(\\d+)\\s.*$").matcher(version)).matches()) {
            try {
                int build = Integer.parseInt(m.group(1));
                if (build < buildNumberThreshold) {
                    return true;
                }
            } catch (NumberFormatException ex) {}
        }
        return false;
    }

    /**
     * An optionally sorted list of sign entries
     */
    public static final class EntryList {
        public static final EntryList NONE = new EntryList(new Entry[0], true);

        private final Entry[] values;
        private boolean sorted;

        private EntryList(Entry[] values, boolean sorted) {
            this.values = values;
            this.sorted = sorted;
        }

        public int count() {
            return values.length;
        }

        public Entry[] unsortedValues() {
            return values;
        }

        public Entry[] values() {
            if (!sorted) {
                sorted = true;
                Arrays.sort(values, Comparator.comparingLong(e -> e.blockKey));
            }
            return values;
        }

        public EntryList add(Entry entry) {
            Entry[] values = this.values;
            int len = values.length;
            if (len == 0) {
                return entry.singletonList;
            } else {
                Entry[] tmp = Arrays.copyOf(values, len + 1);
                tmp[len] = entry;
                return new EntryList(tmp, false /* needs sorting */);
            }
        }

        public static EntryList of(Entry entry) {
            return new EntryList(new Entry[] { entry }, true);
        }
    }

    /**
     * A single sign
     */
    public static final class Entry {
        public final SignChangeTracker sign;
        private SignChangeTracker signLastState; // Can be null if removed!
        public final SignControllerWorld world;
        final SignSide front, back;
        private final FastTrackedUpdateSet.Tracker<Entry> redstoneUpdateTracker;
        private final FastTrackedUpdateSet.Tracker<Entry> ignoreRedstoneUpdateTracker;
        final long blockKey;
        SignBlocksAround blocks;
        final long chunkKey;
        final EntryList singletonList;

        private Entry(Sign sign, SignControllerWorld world, long blockKey, long chunkKey, SignController controller) {
            this.sign = SignChangeTracker.track(sign);
            this.world = world;
            this.front = new SignSide(true, SignChangeTracker::getFrontLine);
            this.back = new SignSide(false, SignChangeTracker::getBackLine);
            this.redstoneUpdateTracker = controller.pendingRedstoneUpdates.track(this);
            this.ignoreRedstoneUpdateTracker = controller.ignoreRedstoneUpdates.track(this);
            this.blockKey = blockKey;
            this.chunkKey = chunkKey;
            this.blocks = SignBlocksAround.of(this.sign.getAttachedFace());
            this.singletonList = EntryList.of(this);
            this.updateLastSignState();
        }

        /**
         * Updates the last-known state of the sign. This makes this information available
         * for when signs change text or are removed entirely to fire the appropriate
         * events.
         */
        void updateLastSignState() {
            signLastState = sign.clone();
        }

        /**
         * Updates the facing orientation of the sign. Re-registers the sign at the right
         * blocks when facing changes.
         */
        void updateSignFacing() {
            if (sign.getAttachedFace() != blocks.getAttachedFace()) {
                blocks.forAllBlocks(this, world::removeChunkByBlockEntry);
                blocks = SignBlocksAround.of(sign.getAttachedFace());
                blocks.forAllBlocks(this, world::addChunkByBlockEntry);
            }
        }

        public Block getBlock() {
            return this.sign.getBlock();
        }

        public SignActionHeader getFrontHeader() {
            return this.front.getHeader();
        }

        public SignActionHeader getBackHeader() {
            return this.back.getHeader();
        }

        /**
         * Called when the Entry is removed from a cache
         */
        void remove() {
            this.redstoneUpdateTracker.untrack();
            this.ignoreRedstoneUpdateTracker.untrack();
        }

        /**
         * Deactivates the front and back side of this sign entry, firing unload events
         * if needed.
         */
        void deactivate() {
            try {
                front.deactivate();
                back.deactivate();
            } catch (Throwable t) {
                Block b = sign.getBlock();
                world.getPlugin().getLogger().log(Level.SEVERE, "Error while unloading sign in world " +
                        b.getWorld().getName() + " at " + b.getX() + " / " + b.getY() + " / " + b.getZ(), t);
            }
        }

        /**
         * Verifies that this sign is still there, and updates the text and facing information.
         * Fires the appropriate events if sign text changes.
         *
         * @return True if the sign still exists, False if it was removed
         */
        boolean verify() {
            boolean changed = sign.update();
            return verifyAfterUpdate(changed, changed);
        }

        /**
         * Verifies that this sign is still there, and updates the text and facing information.
         * Fires the appropriate events if sign text changes.<br>
         * <br>
         * This assumes a sign update has already occurred earlier.
         *
         * @param frontChanged Whether the front of the sign changed/was removed
         * @param backChanged Whether the back of the sign changed/was removed
         * @return True if the sign still exists, False if it was removed
         */
        boolean verifyAfterUpdate(boolean frontChanged, boolean backChanged) {
            // If removed, and it wasn't before, and the chunk is loaded, fire destroy events
            if (sign.isRemoved() && signLastState != null && !signLastState.isRemoved() && WorldUtil.isLoaded(sign.getBlock())) {
                handleDestroy(frontChanged, backChanged);
                return false;
            }

            //TODO: Change event for changed text?

            // Refresh last-known state
            if ((frontChanged || backChanged) || (signLastState == null && !sign.isRemoved())) {
                this.updateLastSignState();
            }

            // If removed/unloaded, there is nothing more to do
            if (sign.isRemoved()) {
                return false;
            }

            // If loaded and changed, also tell the offline sign metadata store to verify what it knows there
            if (frontChanged && backChanged) {
                world.getPlugin().getOfflineSigns().verifySign(sign.getSign());
            } else if (frontChanged) {
                world.getPlugin().getOfflineSigns().verifySign(sign.getSign(), true, null);
            } else if (backChanged) {
                world.getPlugin().getOfflineSigns().verifySign(sign.getSign(), false, null);
            }

            updateSignFacing();

            return true;
        }

        /**
         * Called when a new sign is placed by a Player at the same position as an existing sign.
         * Verifies the sign is still there, and fires a removal event if so.
         *
         * @param frontText Whether the front changed (true) or the back (false)
         * @return True if the sign still exists, False if it was removed
         */
        boolean verifyBeforeSignChange(boolean frontText) {
            sign.update();
            if (sign.isRemoved()) {
                // Handle removal of the sign in the normal way
                verifyAfterUpdate(frontText, !frontText);
                return false;
            } else {
                // Fire destroy events for the previous sign details, if any
                // Then, update the sign state for later
                handleDestroy(frontText, !frontText);
                updateSignFacing();
                updateLastSignState();
                return true;
            }
        }

        /**
         * Handles destruction of this sign. This informs sign actions and the offline sign metadata
         * store that the sign has been removed.
         */
        private void handleDestroy(boolean destroyFront, boolean destroyBack) {
            if (signLastState == null || signLastState.isRemoved()) {
                return;
            }

            // Fire destroy event to tell sign actions the sign was broken
            if (destroyFront && !front.cachedHeader.isEmpty()) {
                RailLookup.TrackedSign sign = RailLookup.TrackedSign.forRealSign(signLastState, true, RailPiece.NONE);
                SignAction.handleDestroy(new SignActionEvent(sign));
            }
            if (destroyBack && !back.cachedHeader.isEmpty()) {
                RailLookup.TrackedSign sign = RailLookup.TrackedSign.forRealSign(signLastState, false, RailPiece.NONE);
                SignAction.handleDestroy(new SignActionEvent(sign));
            }

            // Remove from the offline signs cache as well
            if (destroyFront && destroyBack) {
                world.getPlugin().getOfflineSigns().removeAll(signLastState.getBlock());

                // Make sure it does not fire again until a sign is detected
                signLastState = null;
            } else if (destroyFront) {
                world.getPlugin().getOfflineSigns().removeAll(signLastState.getBlock(), true);
            } else if (destroyBack) {
                world.getPlugin().getOfflineSigns().removeAll(signLastState.getBlock(), false);
            }
        }

        /**
         * Tells the engine to ignore the redstone changes for the upcoming tick. This
         * is used to ignore circular input triggers from levers attached to the block
         * of the sign.
         */
        public void ignoreRedstone() {
            this.ignoreRedstoneUpdateTracker.set(true);
        }

        /**
         * Requests this sign's redstone state to be updated, firing a redstone change
         * event a tick later. Efficiently debounces.
         */
        public void updateRedstoneLater() {
            this.redstoneUpdateTracker.set(true);
        }

        private static boolean skipReadingPower(SignActionHeader header) {
            return header.isEmpty() || header.isAlwaysOn() || header.isAlwaysOff();
        }

        void setActivated(boolean activateFront, boolean activateBack) {
            boolean powered;
            if ((!activateFront || skipReadingPower(front.getHeader())) &&
                (!activateBack || skipReadingPower(back.getHeader()))
            ) {
                powered = false;
            } else {
                powered = PowerState.isSignPowered(this.sign.getBlock());
            }
            if (activateFront) {
                front.activated = true;
                front.setInitialPower(powered);
            }
            if (activateBack) {
                back.activated = true;
                back.setInitialPower(powered);
            }
        }

        public void updateRedstonePower() {
            SignActionHeader frontHeader = this.getFrontHeader();
            SignActionHeader backHeader = this.getBackHeader();

            // Read power state or not?
            boolean powered = (!skipReadingPower(frontHeader) || !skipReadingPower(backHeader)) &&
                    PowerState.isSignPowered(this.sign.getBlock());

            // Only handle the REDSTONE_CHANGE action when using [+train] or [-train]
            // Improves performance by avoiding a needless isSignPowered() calculation
            if (!frontHeader.isEmpty()) {
                if (frontHeader.isAlwaysOn() || frontHeader.isAlwaysOff()) {
                    front.setRedstonePowerChanged(frontHeader);
                } else {
                    front.setRedstonePower(frontHeader, powered);
                }
            }
            if (!backHeader.isEmpty()) {
                if (backHeader.isAlwaysOn() || backHeader.isAlwaysOff()) {
                    back.setRedstonePowerChanged(backHeader);
                } else {
                    back.setRedstonePower(backHeader, powered);
                }
            }
        }

        public void updateRedstonePowerVerify(boolean isPowered) {
            SignActionHeader frontHeader = this.getFrontHeader();
            SignActionHeader backHeader = this.getBackHeader();

            // If important, verify power has changed
            // Only handle the REDSTONE_CHANGE action when using [+train] or [-train]
            // Improves performance by avoiding a needless isSignPowered() calculation
            boolean powerStateCorrect = (!skipReadingPower(frontHeader) || !skipReadingPower(backHeader)) &&
                    (isPowered == PowerState.isSignPowered(this.sign.getBlock()));

            if (!frontHeader.isEmpty()) {
                if (frontHeader.isAlwaysOn() || frontHeader.isAlwaysOff()) {
                    front.setRedstonePowerChanged(frontHeader);
                } else if (powerStateCorrect) {
                    front.setRedstonePower(frontHeader, isPowered);
                }
            }
            if (!backHeader.isEmpty()) {
                if (backHeader.isAlwaysOn() || backHeader.isAlwaysOff()) {
                    back.setRedstonePowerChanged(backHeader);
                } else if (powerStateCorrect) {
                    back.setRedstonePower(backHeader, isPowered);
                }
            }
        }

        class SignSide {
            private final boolean front;
            private final GetLineFunction lineFunc;
            public String headerLine;
            private SignActionHeader cachedHeader;
            public boolean powered;
            public boolean activated;

            public SignSide(boolean front, GetLineFunction lineFunc) {
                this.front = front;
                this.lineFunc = lineFunc;
                this.headerLine = lineFunc.getLine(sign, 0);
                this.cachedHeader = SignActionHeader.parse(Util.cleanSignLine(headerLine));
                this.powered = false; // Initialized later on
                this.activated = false; // Activated when neighbouring chunks load as well
            }

            public SignActionHeader getHeader() {
                String headerLine = lineFunc.getLine(sign, 0);
                if (headerLine.equals(this.headerLine)) {
                    return cachedHeader;
                } else {
                    this.headerLine = headerLine;
                    return this.cachedHeader = SignActionHeader.parse(Util.cleanSignLine(headerLine));
                }
            }

            public void setInitialPower(boolean powered) {
                this.powered = powered;
            }

            public void deactivate() {
                if (activated) {
                    activated = false;
                    handleLoadChange(false);
                }
            }

            public void handleLoadChange(boolean loaded) {
                if (!getHeader().isEmpty()) {
                    SignAction.handleLoadChange(sign.getSign(), front, loaded);
                }
            }

            public void setRedstonePower(SignActionHeader header, boolean newPowerState) {
                // Is the event allowed?
                SignActionEvent info = createSignActionEvent(header);
                SignActionType type = info.getHeader().getRedstoneAction(newPowerState);

                // Change in redstone power?
                if (this.powered != newPowerState) {
                    this.powered = newPowerState;
                    if (type != SignActionType.NONE) {
                        SignAction.executeAll(info, type);
                    }
                }

                // Fire a REDSTONE_CHANGE event afterwards at all times
                SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
            }

            public void setRedstonePowerChanged(SignActionHeader header) {
                SignActionEvent info = createSignActionEvent(header);
                SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
            }

            private SignActionEvent createSignActionEvent(SignActionHeader header) {
                TrackedSign trackedSign = TrackedSign.forRealSign(sign, front, (RailPiece) null);
                trackedSign.setCachedHeader(header);
                return new SignActionEvent(trackedSign);
            }
        }

        @FunctionalInterface
        public interface GetLineFunction {
            String getLine(SignChangeTracker sign, int index);
        }
    }

    private class RedstoneUpdateTask extends Task {

        public RedstoneUpdateTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            pendingRedstoneUpdates.forEachAndClear(SignController.this::updateRedstoneNow);
            ignoreRedstoneUpdates.clear();
            cleanupUnloaded();
        }
    }
}
