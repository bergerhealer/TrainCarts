package com.bergerkiller.bukkit.tc.portals;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class PortalDestination {
    private final Block railsBlock;
    private final BlockFace[] directions;

    public PortalDestination(Block railsBlock, BlockFace[] directions) {
        this.railsBlock = railsBlock;
        this.directions = directions;
    }

    public Block getRailsBlock() {
        return this.railsBlock;
    }

    public BlockFace[] getDirections() {
        return this.directions;
    }

    public boolean hasDirections() {
        return this.directions != null && this.directions.length > 0;
    }
}
