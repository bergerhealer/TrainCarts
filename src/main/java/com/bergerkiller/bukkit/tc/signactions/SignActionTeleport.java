package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.Portal;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.BlockTimeoutMap;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class SignActionTeleport extends SignAction {
    private BlockTimeoutMap teleportTimes = new BlockTimeoutMap();

    @Override
    public boolean verify(SignActionEvent info) {
        return true; // we do not require TrainCarts verification because we use MyWorlds' logic
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.getLine(0).equalsIgnoreCase("[portal]") && info.hasRails();
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!TrainCarts.MyWorldsEnabled) {
            return;
        }
        if (!info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) || !info.hasGroup() || !info.isPowered()) {
            return;
        }
        Portal portal = Portal.get(info.getLocation());
        if (portal == null) {
            return;
        }
        String destname = portal.getDestinationName();
        Location dest = Portal.getPortalLocation(destname, info.getGroup().getWorld().getName());
        if (dest != null) {
            //Teleport the ENTIRE train to the destination...
            Block sign = dest.getBlock();
            sign.getChunk(); //load the chunk
            if (MaterialUtil.ISSIGN.get(sign)) {
                Block destinationRail = Util.getRailsFromSign(sign);
                RailType rail = RailType.getType(destinationRail);
                if (rail == RailType.NONE) {
                    return;
                }

                // This prevents instant teleporting back to the other end
                if (this.teleportTimes.isMarked(info.getBlock(), MyWorlds.teleportInterval)) {
                    return;
                } else {
                    this.teleportTimes.mark(sign);
                }

                // Get a list of possible directions to 'spawn' the train at the destination
                ArrayList<BlockFace> possibleDirs = new ArrayList<BlockFace>();
                ArrayList<TrackIterator> possibleIters = new ArrayList<TrackIterator>();
                BlockFace[] directions = rail.getPossibleDirections(destinationRail);

                SignActionEvent dest_info = new SignActionEvent(sign);
                for (BlockFace dir : dest_info.getSpawnDirections()) {
                    if (LogicUtil.contains(dir, directions)) {
                        possibleDirs.add(dir);
                        possibleIters.add(new TrackIterator(destinationRail, dir));
                    }
                }
                if (possibleIters.isEmpty()) {
                    return;
                }

                // If more than one direction is possible, pick the one with longest track length
                // Check up to 30 blocks
                BlockFace spawnDirection = possibleDirs.get(0);
                if (possibleDirs.size() > 1) {
                    for (int n = 0; n < 30; n++) {
                        int num_succ = 0;
                        for (int i = 0; i < possibleIters.size(); i++) {
                            TrackIterator iter = possibleIters.get(i);
                            if (iter.hasNext()) {
                                iter.next();
                                num_succ++;
                                spawnDirection = possibleDirs.get(i);
                            }
                        }
                        if (num_succ <= 1) {
                            break;
                        }
                    }
                }

                // Teleport!
                info.getGroup().teleportAndGo(destinationRail, spawnDirection);
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (TrainCarts.MyWorldsEnabled) {
            return event.hasRails() && handleBuild(event, Permission.BUILD_TELEPORTER, "train teleporter", "teleport trains large distances to another teleporter sign");
        } else {
            event.getPlayer().sendMessage(ChatColor.RED + "MyWorlds" + ChatColor.YELLOW + " is not enabled on this server. Teleporter signs will not function as a result.");
            return false;
        }
    }
}
