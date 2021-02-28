package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

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

        // Parse the third line, which can have the following syntaxes:
        // - 0.3 = launch to 0.3b/t speed, don't change the speed limit
        // - 0.3m/s = launch to 0.3m/s speed (converted), don't change the speed limit
        // - maxspeed 0.3 / speedlimit 0.3 = launch to 0.3b/t speed, change speed limit to 0.3 right away
        // - speedlimit 0.3m/s = same as above, but with unit conversion
        // - speedlimit 0.3 +0.1 = set speedlimit to 0.3, launch to 0.3 with an 'energy' of 0.1 (also units!)
        // - speedlimit 0.3 m/s = set speedlimit and launch at 0.3 m/s (space!)
        FormattedSpeed velocity = null;
        FormattedSpeed speedLimitToSet = null;
        {
            String speedStr = info.getLine(2).trim();

            // If two values are set rather than one, then that is the speed limit + energy
            // In that case we set a new speed limit on the train
            int spaceIdx;
            if ((spaceIdx = speedStr.indexOf(' ')) != -1) {
                FormattedSpeed speed = FormattedSpeed.parse(speedStr.substring(0, spaceIdx).trim(), null);
                FormattedSpeed energy = FormattedSpeed.parse(speedStr.substring(spaceIdx + 1).trim(), null);

                // Both must succeed or we fail here.
                if (speed != null && energy != null) {
                    speedLimitToSet = speed;
                    velocity = FormattedSpeed.of(speed.getValue() + energy.getValue());
                }
            }

            // Parse entire text as a single speed unit
            if (velocity == null) {
                velocity = FormattedSpeed.parse(speedStr, FormattedSpeed.of(TCConfig.launchForce));
            }
        }

        // Parse the launch distance
        int launchEndIdx = info.getLine(1).indexOf(' ');
        String launchConfigStr = (launchEndIdx == -1) ? "" : info.getLine(1).substring(launchEndIdx + 1);
        LauncherConfig launchConfig = LauncherConfig.parse(launchConfigStr);

        if (info.isRCSign()) {

            Direction direction = Direction.parse(info.getLine(3));
            // Launch all groups
            for (MinecartGroup group : info.getRCTrainGroups()) {
                BlockFace cartDirection = group.head().getDirection();
                BlockFace directionFace = direction.getDirectionLegacy(cartDirection, cartDirection);

                // Launch
                initiateLaunch(group.head(), group,
                        directionFace, launchConfig, velocity, speedLimitToSet);
                
            }
        } else if (info.hasRailedMember()) {
            // Parse the direction to launch into
            BlockFace direction = Direction.parse(info.getLine(3)).getDirectionLegacy(info.getFacing(), info.getCartEnterFace());

            // Calculate the launch distance if left empty
            if (!launchConfig.isValid()) {
                launchConfig.setDistance(Util.calculateStraightLength(info.getRails(), direction));
            }

            // Launch
            initiateLaunch(info.getMember(), info.getGroup(),
                    direction, launchConfig, velocity, speedLimitToSet);
        }
    }

    private void initiateLaunch(
            final MinecartMember<?> member,
            final MinecartGroup group,
            final BlockFace direction,
            final LauncherConfig launchConfig,
            final FormattedSpeed launchSpeed,
            final FormattedSpeed speedLimitToSet
    ) {
        // When prefixed with + or - the speed should be added on top of the current speed of the train
        // Relative velocity -> add to current speed
        double launchVelocityABS = launchSpeed.getValue();
        if (launchSpeed.isRelative()) {
            launchVelocityABS += member.getRealSpeed();
        }

        // Launch
        group.getActions().clear();
        if (speedLimitToSet != null) {
            // Updates the speed limit as well, and restricts the launch curve to within
            // the current speed limit and new speed limit. The remainder turns into 'energy'.
            member.getActions().addActionLaunch(direction, launchConfig, launchVelocityABS, speedLimitToSet.getValue());
        } else {
            // Speed limit isn't changed
            member.getActions().addActionLaunch(direction, launchConfig, launchVelocityABS);
        }
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_LAUNCHER)
                .setName("launcher")
                .setDescription("launch (or brake) trains at a desired speed")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Launcher")
                .handle(event.getPlayer());
    }
}
