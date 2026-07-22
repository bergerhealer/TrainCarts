package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.wrappers.BlockData;

import java.util.Collections;
import java.util.List;

public class RailTypeDetector extends RailTypeRegular {

    @Override
    public boolean isRail(BlockData blockData) {
        return blockData.isType(RailMaterials.DETECTOR);
    }

    @Override
    public boolean hasBlockActivation(Block railBlock) {
        return true;
    }


    @Override
    public List<RailJunction> getJunctions(Block railBlock) {
        return Collections.emptyList();
    }

    @Override
    public void switchJunction(Block railBlock, RailJunction from, RailJunction to) {
        // No junctions
    }
}
