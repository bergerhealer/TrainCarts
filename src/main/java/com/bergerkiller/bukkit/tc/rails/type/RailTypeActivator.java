package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.Material;

import com.bergerkiller.bukkit.common.wrappers.BlockData;

public class RailTypeActivator extends RailTypeRegular {
    private final boolean isPowered;

    protected RailTypeActivator(boolean isPowered) {
        this.isPowered = isPowered;
    }

    public boolean isPowered() {
        return this.isPowered;
    }

    @Override
    public boolean isRail(BlockData blockData) {
        return blockData.getType() == Material.ACTIVATOR_RAIL && ((blockData.getRawData() & 0x8) == 0x8) == isPowered;
    }
}
