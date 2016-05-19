package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.MemberActionWaitOccupied;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import org.bukkit.block.BlockFace;

public class SignActionWait extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("wait");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (info.isAction(SignActionType.GROUP_ENTER) && info.isPowered()) {
            if (!info.hasRailedMember()) return;
            int dist = Math.min(ParseUtil.parseInt(info.getLine(1), 100), TrainCarts.maxDetectorLength);
            long delay = ParseUtil.parseTime(info.getLine(2));
            String[] launchData = Util.splitBySeparator(info.getLine(3));
            double launchDistance;
            BlockFace launchDirection = null;
            Double launchVelocity = null;
            if (launchData.length == 3) {
                launchDistance = ParseUtil.parseDouble(launchData[0], 2.0);
                launchDirection = Direction.parse(launchData[1]).getDirection(info.getFacing(), info.getCartDirection());
                launchVelocity = ParseUtil.parseDouble(launchData[2], (Double) info.getGroup().getAverageForce());
            } else if (launchData.length == 1) {
                launchDistance = ParseUtil.parseDouble(launchData[0], 2.0);
            } else {
                launchDistance = 2.0;
            }

            //allowed?
            BlockFace dir = info.getMember().getDirectionTo();

            //distance
            if (MemberActionWaitOccupied.handleOccupied(info.getRails(), dir, info.getMember(), dist)) {
                info.getGroup().getActions().clear();
                info.getMember().getActions().addActionWaitOccupied(dist, delay, launchDistance, launchDirection, launchVelocity);
            }
        } else if (info.isAction(SignActionType.REDSTONE_OFF)) {
            if (!info.hasRailedMember()) return;
            info.getGroup().getActions().clear();
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return event.getMode() != SignActionMode.NONE && handleBuild(event, Permission.BUILD_WAIT, "train waiter sign", "waits the train until the tracks ahead are clear");
    }
}
