package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Input parameters for when retrieving the logic for a rail
 */
public class RailLogicState {
    private MinecartMember<?> _member;
    private Block _railsBlock;
    private BlockFace _enterDirection;

    public RailLogicState(MinecartMember<?> member, Block railsBlock, BlockFace enterDirection) {
        this._member = member;
        this._railsBlock = railsBlock;
        this._enterDirection = enterDirection;
    }

    /**
     * Gets the Minecart Member for which rail logic is requested. Returns null when
     * rail logic is requested for something other than a Minecart, such as when spawning
     * a new train or for pathfinding.
     * 
     * @return member
     */
    public MinecartMember<?> getMember() {
        return this._member;
    }

    /**
     * Gets the rails block for which rail logic is requested
     * 
     * @return rails block
     */
    public Block getRailsBlock() {
        return this._railsBlock;
    }

    /**
     * Gets the block face direction from which the rails block was entered.
     * 
     * @return enter direction
     */
    public BlockFace getEnterDirection() {
        return this._enterDirection;
    }
}
