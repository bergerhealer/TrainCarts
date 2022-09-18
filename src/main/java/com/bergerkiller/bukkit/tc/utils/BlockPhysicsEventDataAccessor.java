package com.bergerkiller.bukkit.tc.utils;

import java.util.logging.Level;

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPhysicsEvent;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.Logging;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.generated.net.minecraft.world.level.block.state.IBlockDataHandle;
import com.bergerkiller.mountiplex.reflection.util.FastConstructor;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;

/**
 * Reads the BlockData of a block that changed during a BlockPhysicsEvent.
 * Since Paper 1.17.1 there is a special optimized function to get this
 * without querying the world block data. Also includes a method to
 * create a new block physics event in a multi-version supported way.
 */
public abstract class BlockPhysicsEventDataAccessor {
    public static BlockPhysicsEventDataAccessor INSTANCE;
    static {
        try {
            INSTANCE = new BlockPhysicsEventDataAccessorEventField();
        } catch (Throwable t) {
            //t.printStackTrace();
            try {
                if (Common.evaluateMCVersion(">=", "1.13")) {
                    INSTANCE = new BlockPhysicsEventDataAccessorDefaultModern();
                } else {
                    INSTANCE = new BlockPhysicsEventDataAccessorDefaultLegacy();
                }
            } catch (Throwable t2) {
                TrainCarts.plugin.getLogger().log(Level.SEVERE,
                        "Failed to initialize block physics event data accessor", t);
                INSTANCE = new BlockPhysicsEventDataAccessorFallback();
            }
        }
    }

    /**
     * Gets the BlockData of the Block of a BlockPhysicsEvent
     *
     * @param event
     * @return BlockData
     */
    public abstract BlockData get(BlockPhysicsEvent event);

    /**
     * Creates a new BlockPhysicsEvent for a Block and its BlockData
     *
     * @param block
     * @param blockData
     * @return BlockPhysicsEvent
     */
    public abstract BlockPhysicsEvent createEvent(Block block, BlockData blockData);

    // MC 1.8 - 1.12.2 used an int typeid field only
    private static final class BlockPhysicsEventDataAccessorDefaultLegacy extends BlockPhysicsEventDataAccessor {
        private final FastConstructor<BlockPhysicsEvent> eventConstructor;

        public BlockPhysicsEventDataAccessorDefaultLegacy() throws Throwable {
            this.eventConstructor = new FastConstructor<>(BlockPhysicsEvent.class.getDeclaredConstructor(
                    Block.class, int.class));
        }

        @Override
        public BlockData get(BlockPhysicsEvent event) {
            return WorldUtil.getBlockData(event.getBlock());
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockPhysicsEvent createEvent(Block block, BlockData blockData) {
            return this.eventConstructor.newInstance(block, blockData.getType().getId());
        }
    }

    // Since MC 1.13 typeid was replaced with BlockData for the constructor, but still no field
    private static class BlockPhysicsEventDataAccessorDefaultModern extends BlockPhysicsEventDataAccessor {
        private final FastMethod<Object> toBukkitBlockData;
        private final FastConstructor<BlockPhysicsEvent> eventConstructor;

        public BlockPhysicsEventDataAccessorDefaultModern() throws Throwable {
            Class<?> bd = CommonUtil.getClass("org.bukkit.block.data.BlockData");
            Class<?> cbd = CommonUtil.getClass("org.bukkit.craftbukkit.block.data.CraftBlockData");
            this.toBukkitBlockData = new FastMethod<Object>(cbd.getDeclaredMethod("fromData", IBlockDataHandle.T.getType()));
            this.eventConstructor = new FastConstructor<BlockPhysicsEvent>(BlockPhysicsEvent.class.getConstructor(Block.class, bd));
            this.toBukkitBlockData.forceInitialization();
            this.eventConstructor.forceInitialization();
        }

        @Override
        public BlockData get(BlockPhysicsEvent event) {
            return WorldUtil.getBlockData(event.getBlock());
        }

        @Override
        public BlockPhysicsEvent createEvent(Block block, BlockData blockData) {
            return this.eventConstructor.newInstance(block, this.toBukkitBlockData.invoke(null, blockData.getData()));
        }
    }
    
    // Since a late 1.17.1 version of paper there is a custom method to quickly retrieve the block data
    private static final class BlockPhysicsEventDataAccessorEventField extends BlockPhysicsEventDataAccessorDefaultModern {
        private final FastMethod<Object> blockDataGetter;
        private final FastMethod<Object> blockDataGetState;

        public BlockPhysicsEventDataAccessorEventField() throws Throwable {
            Class<?> cbd = CommonUtil.getClass("org.bukkit.craftbukkit.block.data.CraftBlockData");
            this.blockDataGetter = new FastMethod<Object>(BlockPhysicsEvent.class.getDeclaredMethod("getChangedBlockData"));
            this.blockDataGetState = new FastMethod<Object>(cbd.getDeclaredMethod("getState"));
            this.blockDataGetter.forceInitialization();
            this.blockDataGetState.forceInitialization();
        }

        @Override
        public BlockData get(BlockPhysicsEvent event) {
            try {
                Object bukkit_blockdata = blockDataGetter.invoke(event);
                Object iblockdata = blockDataGetState.invoke(bukkit_blockdata);
                return BlockData.fromBlockData(iblockdata);
            } catch (Throwable t) {
                Logging.LOGGER_REFLECTION.log(Level.SEVERE, "BlockPhysicsEvent getChangedBlockData failed", t);
                
                BlockPhysicsEventDataAccessor.INSTANCE = new BlockPhysicsEventDataAccessorFallback();
                return WorldUtil.getBlockData(event.getBlock());
            }
        }
    }

    // Fallback impl that cant possibly raise errors anymore, but disables stuff
    private static final class BlockPhysicsEventDataAccessorFallback extends BlockPhysicsEventDataAccessor {
        @Override
        public BlockData get(BlockPhysicsEvent event) {
            return WorldUtil.getBlockData(event.getBlock());
        }

        @Override
        public BlockPhysicsEvent createEvent(Block block, BlockData blockData) {
            throw new UnsupportedOperationException("Error initializing handler");
        }
    }
}
