package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.cache.RailSignCache.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Used as a return value for {@link RailType#findRailInfo(posBlock)}
 */
public class RailInfo {
    public final Block posBlock;
    public final Block railBlock;
    public final RailType railType;
    private TrackedSign[] signs;

    public RailInfo(Block posBlock, Block railBlock, RailType railType) {
        this.posBlock = posBlock;
        this.railBlock = railBlock;
        this.railType = railType;
        this.signs = null;
    }

    public void resetSigns() {
        this.signs = null;
    }

    public void verifySigns() {
        if (this.signs != null && !RailSignCache.verifySigns(this.signs)) {
            this.signs = null;
        }
    }

    public TrackedSign[] getSigns() {
        if (this.signs == null) {
            this.signs = RailSignCache.discoverSigns(this.railType, this.railBlock);
        }
        return this.signs;
    }
}
