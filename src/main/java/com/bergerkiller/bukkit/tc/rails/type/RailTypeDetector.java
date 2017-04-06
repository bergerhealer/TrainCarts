package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.Material;

import com.bergerkiller.bukkit.common.wrappers.BlockData;

public class RailTypeDetector extends RailTypeRegular {

    @Override
    public boolean isRail(BlockData blockData) {
        return blockData.getType() == Material.DETECTOR_RAIL;
    }
}
