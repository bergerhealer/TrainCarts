package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.Material;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.wrappers.BlockData;

public class RailTypeDetector extends RailTypeRegular {

    @Override
    public boolean isRail(BlockData blockData) {
        return blockData.isType(Material.DETECTOR_RAIL);
    }

    @Override
    public boolean hasBlockActivation(Block railBlock) {
        return true;
    }
}
