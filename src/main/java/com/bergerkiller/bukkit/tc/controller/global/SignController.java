package com.bergerkiller.bukkit.tc.controller.global;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
    private boolean disabled = false;
    private SignControllerWorld byWorldLastGet = NONE;
    private final RedstoneUpdateTask updateTask;

    public SignController(TrainCarts plugin) {
        this.plugin = plugin;
        this.updateTask = new RedstoneUpdateTask(plugin);
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
     * Informs this controller that a particular sign is (about) to be removed.
     * Does not fire any events.
     *
     * @param signBlock
     */
    public void notifySignRemoved(Block signBlock) {
        // Remove loaded sign information
        SignControllerWorld worldController = forWorld(signBlock.getWorld());
        Entry entry = worldController.findForSign(signBlock);
        if (entry != null) {
            worldController.removeInvalidEntry(entry);
        }

        // Remove from the offline signs cache as well
        plugin.getOfflineSigns().removeAll(signBlock);
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

    @EventHandler(priority = EventPriority.MONITOR)
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

        if (event.isCancelled()) {
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
        controller.addSign(event.getBlock(), false);

        // Handle building the sign. Might cancel it (permissions)
        SignAction.handleBuild(event);
    }

    // This is also needed to support block placement / piston / WR / etc.
    @EventHandler(priority = EventPriority.MONITOR)
    private void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        SignControllerWorld controller = forWorld(block.getWorld());

        if (MaterialUtil.ISSIGN.get(event.getChangedType())) {
            controller.detectNewSigns(block);
        }

        for (Entry e : controller.findNearby(block)) {
            e.updateRedstoneLater();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockRedstoneChange(BlockRedstoneEvent event) {
        if (TrainCarts.isWorldDisabled(event)) {
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
        activateEntry(entry, false, true);
    }

    void activateEntry(Entry entry, boolean refreshRailSigns, boolean handleLoadChange) {
        // Refresh signs mapped to rails at all times, if specified
        // The tracked rail is made available later on to handle the load change, if set
        TrackedSign trackedSign = null;
        if (refreshRailSigns) {
            trackedSign = TrackedSign.forRealSign(entry.sign.getSign(), null);
            trackedSign.getRail().forceCacheVerification();
        }

        if (entry.activated) {
            return;
        }

        Block b = entry.sign.getBlock();
        try {
            entry.activated = true;
            entry.initRedstonePower();

            if (handleLoadChange) {
                if (refreshRailSigns) {
                    SignAction.handleLoadChange(trackedSign, true);
                } else {
                    SignAction.handleLoadChange(entry.sign.getSign(), true);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error while initializing sign in world " +
                    b.getWorld().getName() + " at " + b.getX() + " / " + b.getY() + " / " + b.getZ(), t);
        }
    }

    void deactivateEntry(Entry entry) {
        if (!entry.activated) {
            return;
        }

        try {
            entry.activated = false;
            SignAction.handleLoadChange(entry.sign.getSign(), false);
        } catch (Throwable t) {
            Block b = entry.sign.getBlock();
            plugin.getLogger().log(Level.SEVERE, "Error while unloading sign in world " +
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
        if (!SignControllerWorld.verifyEntry(entry)) {
            return;
        }

        // All good. Update redstone power now.
        entry.updateRedstonePower();
    }

    /**
     * A single sign
     */
    public static final class Entry {
        public static final Entry[] NO_ENTRIES = new Entry[0];
        public final SignChangeTracker sign;
        public final SignControllerWorld world;
        public boolean powered;
        public boolean activated;
        private SignActionHeader header;
        private String headerLine;
        private final FastTrackedUpdateSet.Tracker<Entry> redstoneUpdateTracker;
        private final FastTrackedUpdateSet.Tracker<Entry> ignoreRedstoneUpdateTracker;
        final long blockKey;
        SignBlocksAround blocks;
        final long chunkKey;
        final Entry[] singletonArray;

        private Entry(Sign sign, SignControllerWorld world, long blockKey, long chunkKey, SignController controller) {
            this.sign = SignChangeTracker.track(sign);
            this.world = world;
            this.powered = false; // Initialized later once neighbouring chunks are also loaded
            this.activated = false; // Activated when neighbouring chunks load as well
            this.headerLine = sign.getLine(0);
            this.header = SignActionHeader.parse(Util.cleanSignLine(headerLine));
            this.redstoneUpdateTracker = controller.pendingRedstoneUpdates.track(this);
            this.ignoreRedstoneUpdateTracker = controller.ignoreRedstoneUpdates.track(this);
            this.blockKey = blockKey;
            this.chunkKey = chunkKey;
            this.blocks = SignBlocksAround.of(this.sign.getAttachedFace());
            this.singletonArray = new Entry[] { this };
        }

        public Block getBlock() {
            return this.sign.getBlock();
        }

        public SignActionHeader getHeader() {
            String signLine = this.sign.getSign().getLine(0);
            if (signLine.equals(this.headerLine)) {
                return this.header;
            } else {
                this.headerLine = signLine;
                return this.header = SignActionHeader.parse(Util.cleanSignLine(signLine));
            }
        }

        /**
         * Called when the Entry is removed from a cache
         */
        void remove() {
            this.redstoneUpdateTracker.untrack();
            this.ignoreRedstoneUpdateTracker.untrack();
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

        public void initRedstonePower() {
            SignActionHeader header = this.getHeader();
            if (header.isAlwaysOn() || header.isAlwaysOff()) {
                this.powered = false;
            } else {
                this.powered = PowerState.isSignPowered(this.sign.getBlock());
            }
        }

        public void updateRedstonePower() {
            // Only handle the REDSTONE_CHANGE action when using [+train] or [-train]
            // Improves performance by avoiding a needless isSignPowered() calculation
            SignActionHeader header = this.getHeader();
            if (header.isAlwaysOn() || header.isAlwaysOff()) {
                this.setRedstonePowerChanged(header);
                return;
            }

            setRedstonePower(header, PowerState.isSignPowered(this.sign.getBlock()));
        }

        public void updateRedstonePowerVerify(boolean isPowered) {
            // Only handle the REDSTONE_CHANGE action when using [+train] or [-train]
            // Improves performance by avoiding a needless isSignPowered() calculation
            SignActionHeader header = this.getHeader();
            if (header.isAlwaysOn() || header.isAlwaysOff()) {
                this.setRedstonePowerChanged(header);
                return;
            }

            // Verify that the power state is correct
            if (PowerState.isSignPowered(this.sign.getBlock()) != isPowered) {
                return;
            }

            // Update power level
            setRedstonePower(header, isPowered);
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
            TrackedSign trackedSign = TrackedSign.forRealSign(this.sign, (RailPiece) null);
            trackedSign.setCachedHeader(header);
            return new SignActionEvent(trackedSign);
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
