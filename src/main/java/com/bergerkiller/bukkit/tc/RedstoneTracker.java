package com.bergerkiller.bukkit.tc;

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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.logging.Level;

/**
 * Keeps track of Redstone Power for signs, raising proper Sign redstone events in the process
 */
public class RedstoneTracker implements Listener {
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
        initPowerLevels();
    }

    public void disable() {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        BlockData event_block_type = WorldUtil.getBlockData(event.getBlock());
        if (MaterialUtil.ISSIGN.get(event_block_type) || MaterialUtil.ISREDSTONETORCH.get(event_block_type)) {
            if (nextTickPhysicsBlocks.isEmpty()) {
                CommonUtil.nextTick(nextTickPhysicsHandler);
            }
            nextTickPhysicsBlocks.add(event.getBlock());
        }
    }

    /**
     * Initializes the power levels of all signs on a server.
     * Should be called only once, ever, as this method is quite slow.
     */
    public void initPowerLevels() {
        for (World world : WorldUtil.getWorlds()) {
            loadSignsInWorld(world);
        }
    }

    public void loadSignsInWorld(World world) {
        for (Chunk chunk : world.getLoadedChunks()) {
            loadSignsInChunk(chunk, true);
        }
    }

    public void loadSignsInChunk(Chunk chunk, boolean checkNeighboursLoaded) {
        // Check that this chunk has all 8 neighbouring chunks loaded too
        if (TCConfig.initRedstoneWithRadius && checkNeighboursLoaded) {
            for (int cx = -1; cx <= 1; cx++) {
                for (int cz = -1; cz <= 1; cz++) {
                    if (cx == 0 && cz == 0) {
                        continue;
                    }
                    if (!WorldUtil.isLoaded(chunk.getWorld(), chunk.getX()+cx, chunk.getZ()+cz)) {
                        return;
                    }
                }
            }
        }

        // Actually load it
        try {
            loadSigns(WorldUtil.getBlockStates(chunk));
        } catch (Throwable t) {
            TrainCarts.plugin.getLogger().log(Level.SEVERE, "Error while initializing sign power states in chunk " + chunk.getWorld().getName() +
                    " [" + chunk.getX() + "/" + chunk.getZ() + "]", t);
        }
    }

    public void loadSigns(Collection<BlockState> states) {
        for (BlockState state : states) {
            if (state instanceof Sign) {
                Block block = state.getBlock();
                LogicUtil.addOrRemove(poweredBlocks, block, PowerState.isSignPowered(block));
                SignAction.handleLoadChange((Sign) state, true);
            }
        }
    }

    public void unloadSignsInChunk(Chunk chunk) {
        try {
            unloadSigns(WorldUtil.getBlockStates(chunk));
        } catch (Throwable t) {
            TrainCarts.plugin.getLogger().log(Level.SEVERE, "Error while initializing sign power states in chunk " + chunk.getX() + "/" + chunk.getZ(), t);
        }
    }

    public void unloadSigns(Collection<BlockState> states) {
        for (BlockState state : states) {
            if (state instanceof Sign) {
                SignAction.handleLoadChange((Sign) state, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        loadSignsInWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        boolean isAllNeighboursLoaded = true;
        if (TCConfig.initRedstoneWithRadius) {
            for (int cx = -1; cx <= 1; cx++) {
                for (int cz = -1; cz <= 1; cz++) {
                    if (cx == 0 && cz == 0) {
                        continue;
                    }

                    Chunk neigh_chunk = WorldUtil.getChunk(chunk.getWorld(), chunk.getX()+cx, chunk.getZ()+cz);
                    if (neigh_chunk == null) {
                        isAllNeighboursLoaded = false;
                    } else {
                        this.loadSignsInChunk(neigh_chunk, true);
                    }
                }
            }
        }
        if (isAllNeighboursLoaded) {
            this.loadSignsInChunk(chunk, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        
    }

    // Checks that all neighbouring chunks, except one, are loaded
    private static boolean checkAllNeighboursLoadedExcept(Chunk chunk, Chunk except) {
        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
                if (dcx == 0 && dcz == 0) {
                    continue;
                }

                int cx = chunk.getX() + dcx;
                int cz = chunk.getZ() + dcz;
                if (cx == except.getX() && cz == except.getZ()) {
                    continue;
                }
                if (!WorldUtil.isLoaded(chunk.getWorld(), cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        boolean isAllNeighboursLoaded = true;
        if (TCConfig.initRedstoneWithRadius) {
            // All neighbouring chunks unload when their loaded neighbouring chunk count is 8
            for (int cx = -1; cx <= 1; cx++) {
                for (int cz = -1; cz <= 1; cz++) {
                    if (cx == 0 && cz == 0) {
                        continue;
                    }

                    Chunk neigh_chunk = WorldUtil.getChunk(chunk.getWorld(), chunk.getX()+cx, chunk.getZ()+cz);
                    if (neigh_chunk == null) {
                        // Neighbour not loaded, which means the chunk unloading already has all signs unloaded
                        isAllNeighboursLoaded = false;
                    } else if (checkAllNeighboursLoadedExcept(neigh_chunk, chunk)) {
                        // When the chunk unloads, this neighbour no longer has all 8 neighbours loaded
                        // Unloads the chunk!
                        unloadSignsInChunk(neigh_chunk);
                    }
                }
            }
        }
        if (isAllNeighboursLoaded) {
            unloadSignsInChunk(chunk);
        }
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
                    CommonUtil.nextTick(() -> ignoredSigns.clear());
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
        if (type == SignActionType.NONE) {
            LogicUtil.addOrRemove(poweredBlocks, info.getBlock(), newPowerState);
            return;
        }

        // Change in redstone power?
        if (!LogicUtil.addOrRemove(poweredBlocks, info.getBlock(), newPowerState)) {

            // No change in redstone power, but a redstone change nevertheless
            SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
            return;
        }

        // Fire the event, with a REDSTONE_CHANGE afterwards
        SignAction.executeAll(info, type);
        SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
    }

}
