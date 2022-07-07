package com.bergerkiller.bukkit.tc.controller.global;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.collections.FastTrackedUpdateSet;
import com.bergerkiller.bukkit.common.component.LibraryComponent;
import com.bergerkiller.bukkit.common.events.MultiBlockChangeEvent;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld.RefreshResult;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
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
     * Informs this controller that a particular sign was placed by a player.
     * Does not fire any events, does initialize redstone state.
     *
     * @param signBlock
     */
    public void notifySignAdded(Block signBlock) {
        forWorld(signBlock.getWorld()).addSign(signBlock);
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

    // This is also needed to support block placement / piston / WR / etc.
    @EventHandler(priority = EventPriority.MONITOR)
    private void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        for (Entry e : forWorld(block.getWorld()).findNearby(block)) {
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

    Entry createEntry(Sign sign, long blockKey, long chunkKey) {
        return new Entry(sign, blockKey, chunkKey, this);
    }

    void activateEntry(Entry entry) {
        if (entry.activated) {
            return;
        }

        Block b = entry.sign.getBlock();
        try {
            entry.activated = true;
            entry.powered = PowerState.isSignPowered(b);
            SignAction.handleLoadChange(entry.sign.getSign(), true);
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

        // Update text & verify attached face / exists
        // If different, force an update (requires slow by-world lookup)
        entry.sign.update();
        if (entry.sign.isRemoved()) {
            forWorld(entry.sign.getWorld()).removeInvalidEntry(entry);
            return;
        }
        if (entry.sign.getAttachedFace() != entry.blocks.getAttachedFace()) {
            forWorld(entry.sign.getWorld()).verifyEntry(entry); // Updates mapping
        }

        entry.updateRedstonePower();
    }

    /**
     * A single sign
     */
    public static final class Entry {
        public static final Entry[] NO_ENTRIES = new Entry[0];
        public final SignChangeTracker sign;
        public boolean powered;
        public boolean activated;
        private final FastTrackedUpdateSet.Tracker<Entry> redstoneUpdateTracker;
        private final FastTrackedUpdateSet.Tracker<Entry> ignoreRedstoneUpdateTracker;
        final long blockKey;
        SignBlocksAround blocks;
        final long chunkKey;
        final Entry[] singletonArray;

        private Entry(Sign sign, long blockKey, long chunkKey, SignController controller) {
            this.sign = SignChangeTracker.track(sign);
            this.powered = false; // Initialized later once neighbouring chunks are also loaded
            this.activated = false; // Activated when neighbouring chunks load as well
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

        public void updateRedstonePower() {
            // Update power level
            setRedstonePower(PowerState.isSignPowered(this.sign.getBlock()));
        }

        public void updateRedstonePowerVerify(boolean isPowered) {
            // Verify that the power state is correct
            if (PowerState.isSignPowered(this.sign.getBlock()) != isPowered) {
                return;
            }

            // Update power level
            setRedstonePower(isPowered);
        }

        public void setRedstonePower(boolean newPowerState) {
            // Is the event allowed?
            SignActionEvent info = new SignActionEvent(sign.getBlock(), sign.getSign(), (RailPiece) null);
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
