package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Used as a return value for {@link RailType#findRailInfo(posBlock)}
 */
public class RailInfo {
    public final Block posBlock;
    public final Block railBlock;
    public final RailType railType;

    public RailInfo(Block posBlock, Block railBlock, RailType railType) {
        this.posBlock = posBlock;
        this.railBlock = railBlock;
        this.railType = railType;
    }
}
