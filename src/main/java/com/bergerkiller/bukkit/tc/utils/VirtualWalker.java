package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class VirtualWalker {

    public VirtualWalker(Location loc, BlockFace direction) {
        
    }

    /**
     * Attempts to move a certain distance from the current position and direction
     * 
     * @param distance to move
     * @return True if movement succeeded, False if not (derailed or looped)
     */
    public boolean move(double distance) {
        // At most move a distance of 0.4 at a time
        while (distance > 0.4) {
            distance -= 0.4;
            if (!move(0.4)) return false;
        }

        // Check nothing to move
        if (distance <= 0.0) return true;

        // Set minecart velocity to the direction * distance we wish to move / normalize
        /*
        double startVelLength = minecart.getEntity().vel.length();
        if (startVelLength < 0.001) {
            BlockFace dir = minecart.getDirection();
            minecart.getEntity().vel.set(FaceUtil.faceToVector(dir).multiply(distance));
        } else {
            minecart.getEntity().vel.multiply(distance / startVelLength);
        }

        // Find rails at the current position
        CommonMinecart<?> entity = minecart.getEntity();
        Block posBlock = entity.getWorld().getBlockAt(entity.loc.x.block(), entity.loc.y.block(), entity.loc.z.block());
        Block railBlock = null;
        RailType railType = null;
        for (RailType possibleRailType : RailType.values()) {
            railBlock = possibleRailType.findRail(posBlock);
            if (railBlock != null) {
                railType = possibleRailType;
                break;
            }
        }
        if (railBlock == null) {
            return false; // No rails here
        }

        // Create track logic using the rail and perform physics updates
        RailLogic logic = railType.getLogic(this.minecart, railBlock);

        // Refresh direction
        logic.getMovementDirection(minecart
        */
        return false;
    }
}
