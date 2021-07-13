package com.bergerkiller.bukkit.tc.debug.types;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.debug.DebugToolType;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Type of tool that operates on the rails being clicked.
 * It will first identify the exact position on the rails that was clicked,
 * and then execute the actual handler.
 */
public abstract class DebugToolTrackWalkerType implements DebugToolType {

    @Override
    public final void onBlockInteract(Player player, Block clickedBlock, ItemStack item, boolean isRightClick) {
        TrackWalkingPoint walker = null;
        if (clickedBlock != null) {
            // From rails block clicked
            Vector direction = player.getEyeLocation().getDirection();
            walker = new TrackWalkingPoint(clickedBlock, FaceUtil.getDirection(direction, false));
            walker.setLoopFilter(true);
            if (walker.state.railType() == RailType.NONE) {
                walker = null;
            }
        }
        if (walker == null) {
            // When clicking air, raytrace to find the first rails block in view and walk from the exact position on the path
            Location loc = player.getEyeLocation();
            Vector dir = loc.getDirection();
            RailState result = null;
            RailState state = new RailState();
            state.setRailPiece(RailPiece.createWorldPlaceholder(loc.getWorld()));
            state.position().setMotion(dir);
            state.initEnterDirection();

            double minDist = Double.MAX_VALUE;
            for (double d = 0.0; d <= 200.0; d += 0.01) {
                RailPath.Position p = state.position();
                p.posX = loc.getX() + dir.getX() * d;
                p.posY = loc.getY() + dir.getY() * d;
                p.posZ = loc.getZ() + dir.getZ() * d;
                if (RailType.loadRailInformation(state)) {
                    RailPath path = state.loadRailLogic().getPath();
                    double distSq = path.distanceSquared(state.railPosition());
                    if (distSq < minDist) {
                        minDist = distSq;
                        result = state.clone();
                        path.snap(result.position(), result.railBlock());
                    } else {
                        break;
                    }
                }
            }

            if (result == null) {
                player.sendMessage(ChatColor.RED + "No rails found here");
                return;
            }

            // Go!
            walker = new TrackWalkingPoint(result);
            walker.setLoopFilter(true);
        }

        onBlockInteract(player, walker, item, isRightClick);
    }

    public abstract void onBlockInteract(Player player, TrackWalkingPoint walker, ItemStack item, boolean isRightClick);
}
