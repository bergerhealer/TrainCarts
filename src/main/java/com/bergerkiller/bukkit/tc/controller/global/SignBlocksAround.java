package com.bergerkiller.bukkit.tc.controller.global;

import java.util.EnumMap;
import java.util.function.LongUnaryOperator;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.global.SignController.Entry;
import com.bergerkiller.bukkit.tc.utils.LongBlockCoordinates;

/**
 * Caches the long-key transformations to perform to iterate by all long
 * block keys around a sign. This is the sign itself, blocks around the sign,
 * as well as all the blocks around the block the sign is attached to.
 */
abstract class SignBlocksAround {
    private static final EnumMap<BlockFace, SignBlocksAround> cache = new EnumMap<>(BlockFace.class);
    private SignBlocksAround opposite; // Final in it's use
    private final LongUnaryOperator operator;
    private final BlockFace attachedFace;
    static {
        cache.put(BlockFace.SELF, new SignBlocksAround(BlockFace.SELF) {
            @Override
            public void forAllNeighboursExceptDirection(long blockKey, Entry entry, EntryBlockConsumer consumer) {
                consumer.accept(entry, blockKey);
                consumer.accept(entry, LongBlockCoordinates.shiftUp(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftDown(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftEast(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftWest(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftSouth(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftNorth(blockKey));
            }
        });
        cache.get(BlockFace.SELF).opposite = new SignBlocksAround(BlockFace.SELF) {
            @Override
            public void forAllNeighboursExceptDirection(long blockKey, Entry entry, EntryBlockConsumer consumer) {
                // No-Op. Already covered by SELF. It's not even used, so whatever...
            }
        };

        cache.put(BlockFace.NORTH, new SignBlocksAround(BlockFace.NORTH) {
            @Override
            public void forAllNeighboursExceptDirection(long blockKey, Entry entry, EntryBlockConsumer consumer) {
                consumer.accept(entry, blockKey);
                consumer.accept(entry, LongBlockCoordinates.shiftUp(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftDown(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftEast(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftWest(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftSouth(blockKey));
            }
        });
        cache.put(BlockFace.EAST, new SignBlocksAround(BlockFace.EAST) {
            @Override
            public void forAllNeighboursExceptDirection(long blockKey, Entry entry, EntryBlockConsumer consumer) {
                consumer.accept(entry, blockKey);
                consumer.accept(entry, LongBlockCoordinates.shiftUp(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftDown(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftWest(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftSouth(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftNorth(blockKey));
            }
        });
        cache.put(BlockFace.SOUTH, new SignBlocksAround(BlockFace.SOUTH) {
            @Override
            public void forAllNeighboursExceptDirection(long blockKey, Entry entry, EntryBlockConsumer consumer) {
                consumer.accept(entry, blockKey);
                consumer.accept(entry, LongBlockCoordinates.shiftUp(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftDown(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftEast(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftWest(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftNorth(blockKey));
            }
        });
        cache.put(BlockFace.WEST, new SignBlocksAround(BlockFace.WEST) {
            @Override
            public void forAllNeighboursExceptDirection(long blockKey, Entry entry, EntryBlockConsumer consumer) {
                consumer.accept(entry, blockKey);
                consumer.accept(entry, LongBlockCoordinates.shiftUp(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftDown(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftEast(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftSouth(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftNorth(blockKey));
            }
        });
        cache.put(BlockFace.UP, new SignBlocksAround(BlockFace.UP) {
            @Override
            public void forAllNeighboursExceptDirection(long blockKey, Entry entry, EntryBlockConsumer consumer) {
                consumer.accept(entry, blockKey);
                consumer.accept(entry, LongBlockCoordinates.shiftDown(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftEast(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftWest(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftSouth(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftNorth(blockKey));
            }
        });
        cache.put(BlockFace.DOWN, new SignBlocksAround(BlockFace.DOWN) {
            @Override
            public void forAllNeighboursExceptDirection(long blockKey, Entry entry, EntryBlockConsumer consumer) {
                consumer.accept(entry, blockKey);
                consumer.accept(entry, LongBlockCoordinates.shiftUp(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftEast(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftWest(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftSouth(blockKey));
                consumer.accept(entry, LongBlockCoordinates.shiftNorth(blockKey));
            }
        });

        // Link opposites
        for (BlockFace face : FaceUtil.BLOCK_SIDES) {
            cache.get(face).opposite = cache.get(face.getOppositeFace());
        }

        // Store SELF for all unknown/weird faces as a fall-back
        for (BlockFace other : BlockFace.values()) {
            if (!cache.containsKey(other)) {
                cache.put(other, cache.get(BlockFace.SELF));
            }
        }
    }

    public static SignBlocksAround of(BlockFace attachedFace) {
        return cache.get(attachedFace);
    }

    private SignBlocksAround(BlockFace attachedFace) {
        this.attachedFace = attachedFace;
        this.operator = LongBlockCoordinates.shiftOperator(attachedFace);
    }

    public final BlockFace getAttachedFace() {
        return this.attachedFace;
    }

    public abstract void forAllNeighboursExceptDirection(long blockKey, SignController.Entry entry, EntryBlockConsumer consumer);

    public final void forAllBlocks(SignController.Entry entry, EntryBlockConsumer consumer) {
        final long blockKey = entry.blockKey;
        this.forAllNeighboursExceptDirection(blockKey, entry, consumer);
        final long blockKeyNeighbour = this.operator.applyAsLong(blockKey);
        this.opposite.forAllNeighboursExceptDirection(blockKeyNeighbour, entry, consumer);
    }

    @FunctionalInterface
    public static interface EntryBlockConsumer {
        void accept(Entry entry, long key);
    }
}
