package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

import org.bukkit.block.Block;

public class BlockActionSetLevers extends BlockAction {
    private final boolean down;

    public BlockActionSetLevers(TrainCarts plugin, Block block, boolean down) {
        super(plugin, block);
        this.down = down;
    }

    @Override
    public void start() {
        if (this.getBlock() != null) {
            BlockUtil.setLeversAroundBlock(this.getBlock(), this.down);
        }
    }
}
