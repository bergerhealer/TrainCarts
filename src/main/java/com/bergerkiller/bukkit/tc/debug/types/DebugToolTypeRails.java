package com.bergerkiller.bukkit.tc.debug.types;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.debug.DebugToolUtil;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

public class DebugToolTypeRails extends DebugToolTrackWalkerType {

    @Override
    public String getIdentifier() {
        return "Rails";
    }

    @Override
    public String getTitle() {
        return "Rail path tool";
    }

    @Override
    public String getDescription() {
        return "Display the positions on the rails along which trains move";
    }

    @Override
    public String getInstructions() {
        return "Right-click rails to see the path a train would take on it";
    }

    @Override
    public void onBlockInteract(TrainCarts plugin, Player player, TrackWalkingPoint walker, ItemStack item, boolean isRightClick) {
        player.sendMessage(ChatColor.YELLOW + "Checking for rails from path [" +
                MathUtil.round(walker.state.position().posX, 3) + "/" +
                MathUtil.round(walker.state.position().posY, 3) + "/" +
                MathUtil.round(walker.state.position().posZ, 3) + "]");

        int lim = 10000;
        if (player.isSneaking()) {
            if (walker.moveFull()) {
                // Show the exact path of the first section
                for (RailPath.Point point : walker.currentRailPath.getPoints()) {
                    Util.spawnDustParticle(point.getLocation(walker.state.railBlock()), 0.1, 0.1, 1.0);
                }

                // Show the rail blocks
                do {
                    DebugToolUtil.showParticle(walker.state.railBlock().getLocation().add(0.5, 0.5, 0.5));
                } while (walker.moveFull() && --lim > 0);
            }
        } else {
            RailPath oldPath = null;
            int segmentCounter = 0;
            double[][] colors = new double[][] {{1.0, 0.1, 0.1}, {0.9, 0.1, 0.1}, {0.8, 0.1, 0.1}};
            while (walker.move(0.3) && --lim > 0) {
                if (oldPath != walker.currentRailPath) {
                    oldPath = walker.currentRailPath;
                    segmentCounter++;
                }
                Location loc = walker.state.positionLocation();
                double[] color = colors[segmentCounter % colors.length];
                Util.spawnDustParticle(loc, color[0], color[1], color[2]);
            }
        }
    }
}
