package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

import org.bukkit.block.BlockFace;

public class SignActionLauncher extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("launch");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) || !info.isPowered()) {
            return;
        }
        // Parse the launch speed
        double velocity = ParseUtil.parseDouble(info.getLine(2), TrainCarts.launchForce);

        if (info.getLine(2).startsWith("+") || info.getLine(2).startsWith("-")) {
            velocity += info.getMember().getForce();
        }

        // Parse the launch distance
        int launchEndIdx = info.getLine(1).indexOf(' ');
        String launchConfigStr = (launchEndIdx == -1) ? "" : info.getLine(1).substring(launchEndIdx + 1);
        LauncherConfig launchConfig = LauncherConfig.parse(launchConfigStr);

        if (info.isRCSign()) {
            boolean reverse = Direction.parse(info.getLine(3)) == Direction.BACKWARD;

            // Launch all groups
            for (MinecartGroup group : info.getRCTrainGroups()) {
                if (reverse) {
                    group.reverse();
                }
                group.head().getActions().addActionLaunch(launchConfig, velocity);
            }
        } else if (info.hasRailedMember()) {
            // Parse the direction to launch into
            BlockFace direction = Direction.parse(info.getLine(3)).getDirection(info.getFacing(), info.getCartDirection());

            // Calculate the launch distance if left empty
            if (!launchConfig.hasDistance() && !launchConfig.hasDuration()) {
                launchConfig.setDistance(Util.calculateStraightLength(info.getRails(), direction));
            }

            // Launch
            info.getMember().getActions().addActionLaunch(direction, launchConfig, velocity);
        }
    }

    public void execute(MinecartGroup group) {

    }

    @Override
    public boolean canSupportRC() {
        return true;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return handleBuild(event, Permission.BUILD_LAUNCHER, "launcher", "launch (or brake) trains at a desired speed");
    }
}
