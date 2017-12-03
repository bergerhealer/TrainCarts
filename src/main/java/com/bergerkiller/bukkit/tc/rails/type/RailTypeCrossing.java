package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class RailTypeCrossing extends RailTypeHorizontal {

    @Override
    public boolean isRail(BlockData blockData) {
        return MaterialUtil.ISPRESSUREPLATE.get(blockData);
    }

    @Override
    public BlockFace[] getPossibleDirections(Block trackBlock) {
        BlockFace dir = getDirection(trackBlock);
        if (dir == BlockFace.SELF) {
            return FaceUtil.RADIAL;
        } else {
            return RailTypeRegular.getPossibleDirections(dir);
        }
    }

    @Override
    public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
        return currentTrack.getRelative(currentDirection);
    }

    @Override
    public BlockFace getDirection(Block railsBlock) {
        return Util.getPlateDirection(railsBlock);
    }

    @Override
    public RailLogic getLogic(MinecartMember<?> member, Block railsBlock, BlockFace direction) {
        // Get the direction of the rails to find out the logic to use
        BlockFace dir = Util.getPlateDirection(railsBlock);
        if (dir == BlockFace.SELF) {
            //set track direction based on direction of this cart
            dir = FaceUtil.toRailsDirection(direction);
        }
        return RailLogicHorizontal.get(dir);
    }

    @Override
    public void onPostMove(MinecartMember<?> member) {
        super.onPostMove(member);
        member.getEntity().loc.y.add(0.1);
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        BlockFace dir = Util.getPlateDirection(railsBlock);
        if (dir == BlockFace.SELF) {
            dir = orientation;
        }

        // Get position on rails and adjust the y-coordinate based on horizontal rail logic
        Location result = super.getSpawnLocation(railsBlock, dir);

        // Correct the yaw of the minecart
        if (FaceUtil.isAlongX(dir)) {
            result.setYaw(0.0F);
        } else if (FaceUtil.isAlongZ(dir)) {
            result.setYaw(-90.0F);
        }

        return result;
    }
}
