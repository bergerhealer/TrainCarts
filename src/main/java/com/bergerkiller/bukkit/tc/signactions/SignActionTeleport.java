package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.portals.PortalDestination;
import com.bergerkiller.bukkit.tc.portals.TCPortalManager;
import com.bergerkiller.bukkit.tc.portals.plugins.MyWorldsPortalsProvider;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.BlockTimeoutMap;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.block.BlockFace;

public class SignActionTeleport extends SignAction {
    private BlockTimeoutMap teleportTimes = new BlockTimeoutMap();

    @Override
    public boolean verify(SignActionEvent info) {
        // we do not require TrainCarts verification when using MyWorlds' logic
        return matchMyWorlds(info) || super.verify(info);
    }

    @Override
    public boolean match(SignActionEvent info) {
        return matchMyWorlds(info) || info.isType("teleport");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) || !info.hasGroup() || !info.isPowered()) {
            return;
        }

        String destName;
        if (matchMyWorlds(info)) {
            if (!TCPortalManager.isAvailable("My_Worlds")) {
                return;
            }
            destName = MyWorldsPortalsProvider.getPortalDestination(info.getLocation());
        } else {
            // Parse destination on third line
            destName = info.getLine(2);
        }
        if (destName == null) {
            return;
        }

        PortalDestination dest = TCPortalManager.getPortalDestination(info.getGroup().getWorld(), destName);
        if (dest != null && dest.getRailsBlock() != null) {

            // This prevents instant teleporting back to the other end
            if (this.teleportTimes.isMarked(info.getRails(), 2000)) {
                return;
            } else {
                this.teleportTimes.mark(dest.getRailsBlock());
            }

            // Get a list of possible directions to 'spawn' the train at the destination
            ArrayList<BlockFace> possibleDirs = new ArrayList<BlockFace>();
            ArrayList<TrackIterator> possibleIters = new ArrayList<TrackIterator>();
            BlockFace[] railDirections = RailType.getType(dest.getRailsBlock()).getPossibleDirections(dest.getRailsBlock());
            for (BlockFace dir : railDirections) {
                if (!dest.hasDirections() || LogicUtil.contains(dir, dest.getDirections())) {
                    possibleDirs.add(dir);
                    possibleIters.add(new TrackIterator(dest.getRailsBlock(), dir));
                }
            }

            BlockFace spawnDirection = null;
            if (possibleIters.isEmpty()) {
                // Select any direction we can find
                if (railDirections.length > 0) {
                    spawnDirection = railDirections[0];
                } else if (dest.hasDirections()) {
                    spawnDirection = dest.getDirections()[0];
                } else {
                    return;
                }
            } else {
                // If more than one direction is possible, pick the one with longest track length
                // Check up to 30 blocks
                spawnDirection = possibleDirs.get(0);
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
            }

            // Teleport!
            info.getGroup().teleportAndGo(dest.getRailsBlock(), spawnDirection);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (matchMyWorlds(event) && !TCPortalManager.isAvailable("My_Worlds")) {
            event.getPlayer().sendMessage(ChatColor.RED + "MyWorlds" + ChatColor.YELLOW + " is not enabled on this server. Teleporter signs will not function as a result.");
            return false;
        }
        return handleBuild(event, Permission.BUILD_TELEPORTER, "train teleporter", "teleport trains large distances to another teleporter sign");
    }

    private boolean matchMyWorlds(SignActionEvent info) {
        return info.getLine(0).equalsIgnoreCase("[portal]") && info.hasRails();
    }
}
