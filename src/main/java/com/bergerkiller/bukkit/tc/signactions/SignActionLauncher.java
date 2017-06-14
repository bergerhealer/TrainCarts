package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
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
        int timeTicks = Util.parseTimeTicks(info.getLine(1));
        double distance = (timeTicks >= 0) ? 0.0 : ParseUtil.parseDouble(info.getLine(1), 1.0);

        if (info.isRCSign()) {
            boolean reverse = Direction.parse(info.getLine(3)) == Direction.BACKWARD;

            // Launch all groups
            for (MinecartGroup group : info.getRCTrainGroups()) {
                if (reverse) {
                    group.reverse();
                }
                if (timeTicks >= 0) {
                    group.head().getActions().addActionTimedLaunch(timeTicks, velocity);
                } else {
                    group.head().getActions().addActionLaunch(distance, velocity);
                }
                group.head().getActions().addActionLaunch(distance, velocity);
            }
        } else if (info.hasRailedMember()) {
            // Parse the direction to launch into
            BlockFace direction = Direction.parse(info.getLine(3)).getDirection(info.getFacing(), info.getCartDirection());

            // Calculate the launch distance if left empty
            if (timeTicks < 0 && distance <= 0.0) {
                distance = Util.calculateStraightLength(info.getRails(), direction);
            }

            // Launch
            if (timeTicks >= 0) {
                info.getMember().getActions().addActionTimedLaunch(direction, timeTicks, velocity);
            } else {
                info.getMember().getActions().addActionLaunch(direction, distance, velocity);
            }
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
