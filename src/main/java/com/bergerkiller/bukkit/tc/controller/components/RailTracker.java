package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class RailTracker {

    protected static RailInfo findInfo(MinecartMember<?> member) {
        IntVector3 blockPos = member.getEntity().loc.block();
        Block block = blockPos.toBlock(member.getEntity().getWorld());

        RailType railType = RailType.NONE;
        for (RailType type : RailType.values()) {
            try {
                IntVector3 pos = type.findRail(member, member.getEntity().getWorld(), blockPos);
                if (pos != null) {
                    railType = type;
                    blockPos = pos;
                    block = blockPos.toBlock(member.getEntity().getWorld());
                    break;
                }
            } catch (Throwable t) {
                RailType.handleCriticalError(type, t);
                break;
            }
        }

        if (member.isSingle()) {
            // Direction will be based on its own momentum and can not be evaluated
            return new RailInfo(block, railType, BlockFace.SELF);
        }

        // A group is most definitely available!
        // Figure out what direction to move to, to get to the next member in line
        
        
        return new RailInfo(block, railType, BlockFace.SELF);
    }

    public static class RailInfo {
        public final IntVector3 railsPos;
        public final Block railsBlock;
        public final RailType railsType;
        public final BlockFace direction;

        public RailInfo(Block railsBlock, RailType railsType, BlockFace direction) {
            this.railsBlock = railsBlock;
            this.railsType = railsType;
            this.direction = direction;
            if (railsBlock == null) {
                this.railsPos = new IntVector3(0, 0, 0);
            } else {
                this.railsPos = new IntVector3(railsBlock.getX(), railsBlock.getY(), railsBlock.getZ());
            }
        }
    }
}
