package com.bergerkiller.bukkit.tc.controller.global;

import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bergerkiller.bukkit.common.block.SignSide;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.events.SignBuildEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.util.SignActionLookupMap;
import com.bergerkiller.bukkit.tc.utils.RecursionGuard;
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
import org.bukkit.event.block.BlockRedstoneEvent;
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
    private static final int MAX_REDSTONE_UPDATES_PER_TICK = 100000; // Purely to avoid a server crash
    private final TrainCarts plugin;
    private final SignControllerWorld NONE = new SignControllerWorld(this); // Dummy
    private final IdentityHashMap<World, SignControllerWorld> byWorld = new IdentityHashMap<>();
    private int pendingRedstoneUpdatesThisTick = 0;
    private final FastTrackedUpdateSet<Entry> pendingRedstoneUpdates = new FastTrackedUpdateSet<Entry>();
    private final FastTrackedUpdateSet<Entry> ignoreRedstoneUpdates = new FastTrackedUpdateSet<Entry>();
    private final boolean blockPhysicsFireForSigns;
    private boolean disabled = false;
    private SignControllerWorld byWorldLastGet = NONE;
    private final RedstoneUpdateTask updateTask;
    private boolean redstonePhysicsSuppressed = false;
    private final RecursionGuard<ChunkLoadEvent> loadChunkRecursionGuard;

    public SignController(TrainCarts plugin) {
        this.plugin = plugin;
        this.updateTask = new RedstoneUpdateTask(plugin);
        this.blockPhysicsFireForSigns = doesBlockPhysicsFireForSigns();
        this.loadChunkRecursionGuard = RecursionGuard.handleOnce(event -> {
            if (!TCConfig.logSyncChunkLoads) {
                return;
            }

            plugin.getLogger().log(Level.WARNING, "Sync chunk load detected loading signs in chunk "
                    + event.getWorld().getName() + " [" + event.getChunk().getX()
                    + ", " + event.getChunk().getZ() + "]", new RuntimeException("Stack"));
        });
    }

    public TrainCarts getPlugin() {
        return plugin;
    }

    @Override
    public void enable() {
        plugin.register(this);
        if (Common.hasCapability("Common:SignEditTextEvent")) {
            plugin.register(new SignControllerEditListenerBKCL(this));
        } else {
            plugin.register(new SignControllerEditListenerLegacy(this));
        }
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
     * @param block Near Block
     * @param mustHaveSignActions Whether the signs to look for must have sign actions, such as
     *                            redstone change handlers or train activation.
     * @param handler Callback for each entry
     */
    public void forEachNearbyVerify(Block block, boolean mustHaveSignActions, Consumer<SignController.Entry> handler) {
        forWorld(block.getWorld()).forEachNearbyVerify(block, mustHaveSignActions, handler);
    }

    /**
     * Ignores signs of current-tick redstone changes caused by the lever
     *
     * @param lever to ignore
     */
    public void ignoreOutputLever(Block lever) {
        final Block att = BlockUtil.getAttachedBlock(lever);

        // Check whether there are any signs attached to the same block the lever is
        forEachNearbyVerify(att, true, entry -> {
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
        Entry entry = worldController.findForSign(tracker.getBlock(), false);
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
        try (RecursionGuard.Token t = loadChunkRecursionGuard.open(event)) {
            forWorld(event.getWorld()).loadChunk(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onChunkUnload(ChunkUnloadEvent event) {
        SignControllerWorld controller = tryGetForWorld(event.getWorld());
        if (controller != null) {
            controller.unloadChunk(event.getChunk());
        }
    }

    protected void handleSignChange(SignBuildEvent event, Block signBlock, SignSide signSide, boolean isSignEdit) {
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
        Entry newSignEntry = controller.addSign(event.getBlock(), true, signSide.isFront());

        // Handle building the sign. Might cancel it (permissions)
        SignAction.handleBuild(event);

        // If not cancelled, update later so the true text is known
        if (newSignEntry != null && !event.isCancelled()) {
            newSignEntry.updateRedstoneLater();
        }

        // This stuff only occurs on <= 1.19.4. On 1.20 the sign change is separate
        // from sign placement, so we shouldn't break the sign at all.
        if (event.isCancelled() && !CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            // Properly give the sign back to the player that placed it
            // We do not want to place down an empty sign, that is annoying
            // If this is impossible for whatever reason, just drop it
            Material signBlockType = signBlock.getType();
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
                    Location loc = signBlock.getLocation().add(0.5, 0.5, 0.5);
                    loc.getWorld().dropItemNaturally(loc, new ItemStack(signItemType, 1));
                }
            }

            // Break the block
            signBlock.setType(Material.AIR);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        SignControllerWorld controller = forWorld(block.getWorld());
        Entry e = controller.findForSign(block, false);
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
            Entry e = controller.findForSign(block, true);
            if (e != null) {
                e.updateRedstoneLater();
            }
        } else {
            // Check signs are nearby
            for (Entry e : controller.findNearby(block, true)) {
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
            for (Entry e : forWorld(block.getWorld()).findNearby(block, true)) {
                e.updateRedstoneLater();
            }
        }

        // If lever, suppress changes on signs nearby (self-triggering)
        BlockData event_block_data = WorldUtil.getBlockData(event.getBlock());
        if (event_block_data.isType(Material.LEVER)) {
            final Block leverBlock = event.getBlock();
            final boolean isPowered = event.getNewCurrent() > 0;
            this.forEachNearbyVerify(leverBlock, true, entry -> {
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

    Entry createEntry(Sign sign, SignControllerWorld world, SignControllerChunk chunk, long blockKey) {
        return new Entry(sign, world, chunk, blockKey, this);
    }

    void activateEntry(Entry entry) {
        activateEntry(entry, false, true);
    }

    void activateEntry(Entry entry, boolean refreshRailSigns, boolean handleLoadChange) {
        // Refresh signs mapped to rails at all times, if specified
        // The tracked rail is made available later on to handle the load change, if set
        // Only load the tracked sign if there is an actual load-change handler for this sign
        TrackedSign frontTrackedSign = null, backTrackedSign = null;
        if (refreshRailSigns) {
            if (entry.front.hasLoadedChangeHandler()) {
                frontTrackedSign = TrackedSign.forRealSign(entry.sign.getSign(), true, null);
            }
            if (entry.back.hasLoadedChangeHandler()) {
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
        if (wasFrontActivated && wasBackActivated) {
            return;
        }

        Block b = entry.sign.getBlock();
        try {
            entry.activate();

            if (handleLoadChange) {
                if (refreshRailSigns) {
                    if (frontTrackedSign != null && !wasFrontActivated) {
                        SignAction.handleLoadChange(frontTrackedSign, true);
                    }
                    if (backTrackedSign != null && !wasBackActivated) {
                        SignAction.handleLoadChange(backTrackedSign, true);
                    }
                } else {
                    if (!wasFrontActivated) {
                        entry.front.handleLoadChange(true);
                    }
                    if (!wasBackActivated) {
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
        // If there are too many updates this tick, skip the updates entirely and log what
        // blocks are involved.
        pendingRedstoneUpdatesThisTick++;
        if (pendingRedstoneUpdatesThisTick >= MAX_REDSTONE_UPDATES_PER_TICK) {
            Block b = entry.sign.getBlock();
            plugin.getLogger().warning("Too many Redstone updates! Skipped sign at world=" + b.getWorld().getName() +
                    " x=" + b.getX() + " y=" + b.getY() + " z=" + b.getZ());
            return;
        }

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

        public boolean contains(Entry entry) {
            for (Entry value : this.values) {
                if (value == entry) {
                    return true;
                }
            }
            return false;
        }

        public EntryList filter(Predicate<Entry> filter) {
            Entry[] values = this.values;
            int len = values.length;

            // Count how many entries pass the filter
            int numPassingFilter = 0;
            for (int i = 0; i < len; i++) {
                if (filter.test(values[i])) {
                    numPassingFilter++;
                }
            }

            // Optimizations
            if (numPassingFilter == len) {
                return this;
            } else if (numPassingFilter == 0) {
                return NONE;
            }

            // Create a new array of entries with just the ones that pass the filter
            Entry[] filteredValues = new Entry[numPassingFilter];
            int currentIndex = 0;
            for (int i = 0; i < len; i++) {
                Entry e = values[i];
                if (filter.test(e)) {
                    filteredValues[currentIndex++] = e;
                }
            }
            return new EntryList(filteredValues, this.sorted);
        }

        public static EntryList of(List<Entry> entries) {
            int count = entries.size();
            if (count == 0) {
                return NONE;
            } else if (count == 1) {
                return entries.get(0).singletonList;
            } else {
                return new EntryList(entries.toArray(new Entry[0]), false);
            }
        }

        public static EntryList createSingleton(Entry entry) {
            return new EntryList(new Entry[] { entry }, true);
        }
    }

    /**
     * A single Minecraft chunk, with information about the signs that exist inside.
     * Keeps track of whether these signs have been registered in the by-neighbouring-block
     * mapping.
     */
    public static final class ChunkEntryList {
        public EntryList entries = EntryList.NONE;
        public boolean isNeighbouringBlocksLoaded = false;


    }

    /**
     * A single sign
     */
    public static final class Entry {
        public final SignChangeTracker sign;
        private SignChangeTracker signLastState; // Can be null if removed!
        public final SignControllerWorld world;
        public final SignControllerChunk chunk;
        public final SignSide front, back;
        private final FastTrackedUpdateSet.Tracker<Entry> redstoneUpdateTracker;
        private final FastTrackedUpdateSet.Tracker<Entry> ignoreRedstoneUpdateTracker;
        final long blockKey;
        SignBlocksAround blocks;
        private boolean registeredInNeighbouringBlocks;
        final EntryList singletonList;

        private Entry(Sign sign, SignControllerWorld world, SignControllerChunk chunk, long blockKey, SignController controller) {
            this.sign = SignChangeTracker.track(sign);
            this.world = world;
            this.chunk = chunk;
            this.front = new SignSide(true, SignChangeTracker::getFrontLine);
            this.back = new SignSide(false, SignChangeTracker::getBackLine);
            this.redstoneUpdateTracker = controller.pendingRedstoneUpdates.track(this);
            this.ignoreRedstoneUpdateTracker = controller.ignoreRedstoneUpdates.track(this);
            this.blockKey = blockKey;
            this.blocks = SignBlocksAround.of(this.sign.getAttachedFace());
            this.registeredInNeighbouringBlocks = false;
            this.singletonList = EntryList.createSingleton(this);
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
                if (registeredInNeighbouringBlocks) {
                    blocks.forAllBlocks(this, world::removeChunkByBlockEntry);
                    blocks = SignBlocksAround.of(sign.getAttachedFace());
                    blocks.forAllBlocks(this, world::addChunkByBlockEntry);
                } else {
                    blocks = SignBlocksAround.of(sign.getAttachedFace());
                }
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

        public TrackedSign createFrontTrackedSign(RailPiece rail) {
            return this.front.createTrackedSign(rail);
        }

        public TrackedSign createBackTrackedSign(RailPiece rail) {
            return this.back.createTrackedSign(rail);
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

            // Detect when text is edited on a sign from having an action or not, or other way around
            {
                boolean hadSignActions = hasSignActionEvents();
                if (frontChanged) {
                    front.updateSignAction();
                }
                if (backChanged) {
                    back.updateSignAction();
                }
                boolean nowHasSignActions = hasSignActionEvents();
                if (hadSignActions != nowHasSignActions) {
                    chunk.updateEntryHasSignActions(this, nowHasSignActions);
                }
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
         * Gets whether this sign responds to redstone change events. If the sign isn't a TC
         * sign, returns false if configuration disables handling of redstone in that case.
         *
         * @return True if this sign responds to redstone
         */
        public boolean hasSignActionEvents() {
            return !TCConfig.onlyRegisteredSignsHandleRedstone || front.hasSignAction() || back.hasSignAction();
        }

        /**
         * Registers this entry in the WorldController by-neighbouring-block mapping.
         * 
         * @see SignControllerWorld#addChunkByBlockEntry(Entry, long) 
         */
        void registerInNeighbouringBlocks() {
            if (!registeredInNeighbouringBlocks) {
                registeredInNeighbouringBlocks = true;
                blocks.forAllBlocks(this, world::addChunkByBlockEntry);
            }
        }

        /**
         * Un-registers this entry in the WorldController by-neighbouring-block mapping.
         *
         * @see SignControllerWorld#removeChunkByBlockEntry(Entry, long)
         */
        void unregisterInNeighbouringBlocks() {
            unregisterInNeighbouringBlocks(false);
        }

        /**
         * Un-registers this entry in the WorldController by-neighbouring-block mapping.
         *
         * @see SignControllerWorld#removeChunkByBlockEntry(Entry, long) 
         */
        void unregisterInNeighbouringBlocks(boolean purgeAllInSameChunk) {
            if (registeredInNeighbouringBlocks) {
                registeredInNeighbouringBlocks = false;
                if (purgeAllInSameChunk) {
                    blocks.forAllBlocks(this, (e, key) -> world.removeChunkByBlockEntry(e, key, true));
                } else {
                    blocks.forAllBlocks(this, world::removeChunkByBlockEntry);
                }
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

        private boolean checkIsSignPowered() {
            // Only signs with registered sign actions auto-connect with redstone wire,
            // as we don't want all signs serverwide to have this behavior suddenly.
            PowerState.Options opt = (front.hasSignAction() || back.hasSignAction())
                    ? PowerState.Options.SIGN_CONNECT_WIRE : PowerState.Options.SIGN;
            return PowerState.isSignPowered(this.sign.getBlock(), opt);
        }

        void activate() {
            boolean powered;
            if ((skipReadingPower(front.getHeader())) &&
                (skipReadingPower(back.getHeader()))
            ) {
                powered = false;
            } else {
                powered = checkIsSignPowered();
            }

            front.activated = true;
            front.setInitialPower(powered);

            back.activated = true;
            back.setInitialPower(powered);
        }

        public void updateRedstonePower() {
            SignActionHeader frontHeader = this.getFrontHeader();
            SignActionHeader backHeader = this.getBackHeader();

            // Read power state or not?
            boolean powered = (!skipReadingPower(frontHeader) || !skipReadingPower(backHeader)) &&
                    checkIsSignPowered();

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
                    (isPowered == checkIsSignPowered());

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

        public class SignSide {
            private final boolean front;
            private final GetLineFunction lineFunc;
            public String headerLine;
            private SignActionHeader cachedHeader;
            private boolean hasSignAction;
            private boolean hasLoadedChangeHandler;
            public boolean powered;
            public boolean activated;

            public SignSide(boolean front, GetLineFunction lineFunc) {
                this.front = front;
                this.lineFunc = lineFunc;
                this.headerLine = lineFunc.getLine(sign, 0);
                this.cachedHeader = SignActionHeader.parse(Util.cleanSignLine(headerLine));
                this.detectSignAction(this.cachedHeader);
                this.powered = false; // Initialized later on
                this.activated = false; // Activated when neighbouring chunks load as well
            }

            public SignActionHeader getHeader() {
                return syncAndGetHeader(false);
            }

            private SignActionHeader syncAndGetHeader(boolean alwaysCheckHasSignAction) {
                String headerLine = lineFunc.getLine(sign, 0);
                if (headerLine.equals(this.headerLine)) {
                    if (alwaysCheckHasSignAction) {
                        this.detectSignAction(cachedHeader);
                    }
                    return cachedHeader;
                } else {
                    this.headerLine = headerLine;
                    SignActionHeader header = this.cachedHeader = SignActionHeader.parse(Util.cleanSignLine(headerLine));
                    this.detectSignAction(header);
                    return header;
                }
            }

            public void updateSignAction() {
                syncAndGetHeader(true);
            }

            public boolean hasSignAction() {
                return hasSignAction;
            }

            public boolean hasLoadedChangeHandler() {
                return hasLoadedChangeHandler;
            }

            private void detectSignAction(SignActionHeader header) {
                Optional<SignActionLookupMap.Entry> actionEntry = SignAction.getLookup().lookup(
                        this.createSignActionEvent(header, RailPiece.NONE /* ignore */));

                this.hasSignAction = actionEntry.isPresent();
                this.hasLoadedChangeHandler = actionEntry.map(SignActionLookupMap.Entry::hasLoadedChangedHandler)
                        .orElse(false);
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
                if (hasLoadedChangeHandler) {
                    SignAction.handleLoadChange(sign.getSign(), front, loaded);
                }
            }

            public void setRedstonePower(SignActionHeader header, boolean newPowerState) {
                // Is the event allowed?
                SignActionEvent info = createSignActionEvent(header, null /* discover */);
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
                SignActionEvent info = createSignActionEvent(header, null /* discover */);
                SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
            }

            public TrackedSign createTrackedSign(RailPiece rail) {
                return createTrackedSign(this.getHeader(), rail);
            }

            private TrackedSign createTrackedSign(SignActionHeader header, RailPiece rail) {
                TrackedSign trackedSign = TrackedSign.forRealSign(sign, front, rail);
                trackedSign.setCachedHeader(header);
                return trackedSign;
            }

            private SignActionEvent createSignActionEvent(SignActionHeader header, RailPiece rail) {
                return new SignActionEvent(createTrackedSign(header, rail));
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
            pendingRedstoneUpdatesThisTick = 0;
            pendingRedstoneUpdates.forEachAndClear(SignController.this::updateRedstoneNow);
            ignoreRedstoneUpdates.clear();
            cleanupUnloaded();
        }
    }
}
