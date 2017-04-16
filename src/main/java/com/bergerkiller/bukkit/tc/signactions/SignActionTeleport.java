package com.bergerkiller.bukkit.tc.signactions;

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
        return TrainCarts.MyWorldsEnabled && info.getLine(0).equalsIgnoreCase("[portal]") && info.hasRails();
    }

    @Override
    public void execute(SignActionEvent info) {
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
                BlockFace facing = info.getFacing().getOppositeFace();
                Block destinationRail = Util.getRailsFromSign(sign);
                RailType rail = RailType.getType(destinationRail);
                if (rail == RailType.NONE) {
                    return;
                }

                for (BlockFace dir : rail.getPossibleDirections(destinationRail)) {
                    if (dir == facing) {
                        //Allowed?
                        if (!this.teleportTimes.isMarked(info.getBlock(), MyWorlds.teleportInterval)) {
                            this.teleportTimes.mark(sign);
                            info.getGroup().teleportAndGo(destinationRail, dir);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return event.hasRails() && handleBuild(event, Permission.BUILD_TELEPORTER, "train teleporter", "teleport trains large distances to another teleporter sign");
    }
}
