package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.TrainCarts;

public class BlockAction extends Action {
    private final TrainCarts traincarts;
    private final Block block;

    public BlockAction(TrainCarts traincarts, Block block) {
        this.traincarts = traincarts;
        this.block = block;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    public Block getBlock() {
        return this.block;
    }
}
