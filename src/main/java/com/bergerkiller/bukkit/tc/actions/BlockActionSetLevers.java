package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import org.bukkit.block.Block;

public class BlockActionSetLevers extends BlockAction {
    private final boolean down;

    public BlockActionSetLevers(Block block, boolean down) {
        super(block);
        this.down = down;
    }

    @Override
    public void start() {
        if (this.getBlock() != null) {
            BlockUtil.setLeversAroundBlock(this.getBlock(), this.down);
        }
    }
}
