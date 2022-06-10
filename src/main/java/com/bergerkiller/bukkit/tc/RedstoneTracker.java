package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkNeighbourList;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkStateListener;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider.ChunkStateTracker;
import com.bergerkiller.bukkit.common.collections.BlockSet;
import com.bergerkiller.bukkit.common.collections.CollectionBasics;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.logging.Level;

/**
 * Keeps track of Redstone Power for signs, raising proper Sign redstone events in the process
 */
public class RedstoneTracker implements Listener {
    private final ChunkLoadHandler chunkLoadHandler;
    private final BlockSet ignoredSigns = new BlockSet();
    private BlockSet poweredBlocks = new BlockSet();

    /* ============= Handles raw block physics in a cached manner to reduce overhead ============ */
    private HashSet<Block> nextTickPhysicsBlocks = new HashSet<Block>();
    private final Runnable nextTickPhysicsHandler = new Runnable() {
        private final ArrayList<Block> pending = new ArrayList<Block>();

        @Override
        public void run() {
            // Detect all signs from the blocks we've cached
            // Detect signs around redstone torches that fired events
            // Verify other blocks are indeed signs
            CollectionBasics.setAll(this.pending, nextTickPhysicsBlocks);
            ListIterator<Block> iter = this.pending.listIterator();
            while (iter.hasNext()) {
                Block block = iter.next();
                BlockData block_data = WorldUtil.getBlockData(block);
                if (MaterialUtil.ISREDSTONETORCH.get(block_data)) {
                    for (BlockFace face : FaceUtil.RADIAL) {
                        final Block rel = block.getRelative(face);
                        if (MaterialUtil.ISSIGN.get(rel) && nextTickPhysicsBlocks.add(rel)) {
                            iter.add(rel);
                        }
                    }
                } else if (!MaterialUtil.ISSIGN.get(block_data)) {
                    iter.remove(); // Not a sign, ignore it
                }
            }
            nextTickPhysicsBlocks.clear();

            // Handle all signs we've found
            for (Block signBlock : this.pending) {
                if (Util.isSignSupported(signBlock)) {
                    // Check for potential redstone changes
                    updateRedstonePower(signBlock);
                } else {
                    // Remove from block power storage
                    poweredBlocks.remove(signBlock);
                }
            }
        }
    };

    public RedstoneTracker(TrainCarts plugin) {
        this.chunkLoadHandler = new ChunkLoadHandler(plugin);

        initPowerLevels();
    }

    public void disable() {
    }

    /**
     * Tracks the blocks that had meaningful physics happen to them.
     * Only signs and redstone torches are tracked.
     *
     * @param block
     */
    public void trackPhysics(Block block) {
        if (nextTickPhysicsBlocks.isEmpty()) {
            CommonUtil.nextTick(nextTickPhysicsHandler);
        }
        nextTickPhysicsBlocks.add(block);
    }

    /**
     * Initializes the power levels of all signs on a server.
     * Should be called only once, ever, as this method is quite slow.
     */
    public void initPowerLevels() {
        for (World world : WorldUtil.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                this.chunkLoadHandler.onChunkLoaded(chunk, true);
            }
        }
    }

    private Collection<BlockState> getBlockStatesSafe(Chunk chunk) {
        try {
            return WorldUtil.getBlockStates(chunk);
        } catch (Throwable t) {
            TrainCarts.plugin.getLogger().log(Level.SEVERE, "Error reading sign block states in chunk " + chunk.getWorld().getName() +
                    " [" + chunk.getX() + "/" + chunk.getZ() + "]", t);
            return Collections.emptyList();
        }
    }

    public void loadSignsInChunk(Chunk chunk) {
        Collection<BlockState> states = getBlockStatesSafe(chunk);

        try {
            for (BlockState state : states) {
                if (state instanceof Sign) {
                    Block block = state.getBlock();
                    LogicUtil.addOrRemove(poweredBlocks, block, PowerState.isSignPowered(block));
                    SignAction.handleLoadChange((Sign) state, true);
                }
            }
        } catch (Throwable t) {
            TrainCarts.plugin.getLogger().log(Level.SEVERE, "Error while initializing sign power states in chunk " + chunk.getWorld().getName() +
                    " [" + chunk.getX() + "/" + chunk.getZ() + "]", t);
        }
    }

    public void unloadSignsInChunk(Chunk chunk) {
        Collection<BlockState> states = getBlockStatesSafe(chunk);

        try {
            for (BlockState state : states) {
                if (state instanceof Sign) {
                    Block block = state.getBlock();
                    SignAction.handleLoadChange((Sign) state, false);
                    poweredBlocks.remove(block);
                }
            }
        } catch (Throwable t) {
            TrainCarts.plugin.getLogger().log(Level.SEVERE, "Error while unloading signs in chunk " + chunk.getX() + "/" + chunk.getZ(), t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        // Wouldn't all chunks unload before this point? Oh well.
        for (Chunk chunk : event.getWorld().getLoadedChunks()) {
            this.chunkLoadHandler.onChunkUnloaded(chunk, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        this.chunkLoadHandler.onChunkLoaded(event.getChunk(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        this.chunkLoadHandler.onChunkUnloaded(event.getChunk(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockRedstoneChange(BlockRedstoneEvent event) {
        if (TrainCarts.isWorldDisabled(event)) {
            return;
        }
        BlockData event_block_data = WorldUtil.getBlockData(event.getBlock());
        if (event_block_data.isType(Material.LEVER)) {
            Block up = event.getBlock().getRelative(BlockFace.UP);
            Block down = event.getBlock().getRelative(BlockFace.DOWN);
            if (MaterialUtil.ISSIGN.get(up)) {
                updateRedstonePowerVerify(up, event.getNewCurrent() > 0);
            }
            if (MaterialUtil.ISSIGN.get(down)) {
                updateRedstonePowerVerify(down, event.getNewCurrent() > 0);
            }
            ignoreOutputLever(event.getBlock());
        } else if (MaterialUtil.ISSIGN.get(event_block_data)) {
            updateRedstonePowerVerify(event.getBlock(), event.getNewCurrent() > 0);
        }
    }

    /**
     * Ignores signs of current-tick redstone changes caused by the lever
     *
     * @param lever to ignore
     */
    public void ignoreOutputLever(Block lever) {
        // Ignore signs that are attached to the block the lever is attached to
        Block att = BlockUtil.getAttachedBlock(lever);
        for (BlockFace face : FaceUtil.ATTACHEDFACES) {
            Block signblock = att.getRelative(face);
            if (MaterialUtil.ISSIGN.get(signblock) && BlockUtil.getAttachedFace(signblock) == face.getOppositeFace()) {
                if (ignoredSigns.isEmpty()) {
                    // clear this the next tick
                    CommonUtil.nextTick(new Runnable() {
                        public void run() {
                            ignoredSigns.clear();
                        }
                    });
                }
                ignoredSigns.add(signblock);
            }
        }
    }

    public void updateRedstonePower(final Block signblock) {
        // Update power level
        setRedstonePower(signblock, PowerState.isSignPowered(signblock));
    }

    public void updateRedstonePowerVerify(final Block signblock, boolean isPowered) {
        // Verify that the power state is correct
        if (PowerState.isSignPowered(signblock) != isPowered) {
            return;
        }

        // Update power level
        setRedstonePower(signblock, isPowered);
    }

    public void setRedstonePower(final Block signblock, boolean newPowerState) {
        // Do not proceed if the sign disallows on/off changes
        if (ignoredSigns.remove(signblock)) {
            return;
        }

        // Is the event allowed?
        SignActionEvent info = new SignActionEvent(signblock);
        SignActionType type = info.getHeader().getRedstoneAction(newPowerState);

        // Change in redstone power?
        if (LogicUtil.addOrRemove(poweredBlocks, info.getBlock(), newPowerState) && type != SignActionType.NONE) {
            SignAction.executeAll(info, type);
        }

        // Fire a REDSTONE_CHANGE event afterwards at all times
        SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
    }

    private final class ChunkLoadHandler {
        private final ChunkFutureProvider provider;

        public ChunkLoadHandler(TrainCarts plugin) {
            this.provider = ChunkFutureProvider.of(plugin);
        }

        public void onChunkLoaded(Chunk chunk, boolean isWorldLoad) {
            provider.trackNeighboursLoaded(chunk, ChunkNeighbourList.neighboursOf(chunk, 1), new ChunkStateListener() {
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
                    loadSignsInChunk(tracker.getChunk());
                }

                @Override
                public void onUnloaded(ChunkStateTracker tracker) {
                    unloadSignsInChunk(tracker.getChunk());
                }
            });
        }

        public void onChunkUnloaded(Chunk chunk, boolean isWorldUnload) {
        }
    }
}
