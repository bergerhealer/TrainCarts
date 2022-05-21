package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

public class SignActionWait extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("wait");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (info.isAction(SignActionType.GROUP_ENTER) && info.isPowered()) {
            if (!info.hasRailedMember()) return;

            BlockFace launchDirection = null;
            String[] launchData = Util.splitBySeparator(info.getLine(3));
            double launchDistance;
            Double launchVelocity = null;

            if (launchData.length == 3) {
                launchDistance = ParseUtil.parseDouble(launchData[0], 2.0);
                launchDirection = Direction.parse(launchData[1]).getDirectionLegacy(info.getFacing(), info.getCartEnterFace());
                launchVelocity = Util.parseVelocity(launchData[2], info.getGroup().getAverageForce());
            } else if (launchData.length == 1) {
                launchDistance = ParseUtil.parseDouble(launchData[0], 2.0);
            } else {
                launchDistance = 2.0;
            }

            // Second line without the name of the sign
            String distanceData = info.getLine(1);
            if (distanceData.startsWith("waiter ")) {
                distanceData = distanceData.replaceFirst("waiter ", "");
            } else if (distanceData.startsWith("waiter")) {
                distanceData = distanceData.replaceFirst("waiter", "");
            } else if (distanceData.startsWith("wait ")) {
                distanceData = distanceData.replaceFirst("wait ", "");
            } else if (distanceData.startsWith("wait")) {
                distanceData = distanceData.replaceFirst("wait", "");
            }

            double distance = Double.NaN;

            // Check if the distance is not a number
            if (distanceData.matches("[a-zA-Z]+")) {
                RailState state = info.getGroup().head().discoverRail();
                if (launchDirection != null) {
                    state.setMotionVector(FaceUtil.faceToVector(launchDirection));
                }

                TrackWalkingPoint walkingPoint = new TrackWalkingPoint(state);

                walk:
                while (walkingPoint.movedTotal < TCConfig.maxDetectorLength && walkingPoint.moveFull()) {
                    for (TrackedSign sign : walkingPoint.state.railSigns()) {
                        if (sign.rail.block().equals(info.getRails())) {
                            continue;
                        }

                        SignActionEvent found = new SignActionEvent(sign, info.getGroup());
                        if (found.isType(distanceData)) {
                            distance = walkingPoint.movedTotal;
                            break walk;
                        }
                    }
                }

                if (Double.isNaN(distance)) {
                    Localization.WAITER_TARGET_NOT_FOUND.broadcast(info.getGroup(), distanceData);
                } else {
                    // Store distance
                    info.setLine(1, "waiter" + String.valueOf(MathUtil.round(distance, 3)));
                }
            } else {
                distance = ParseUtil.parseDouble(info.getLine(1), 100.0);
            }

            long delay = ParseUtil.parseTime(info.getLine(2));

            //distance
            if (info.getGroup().isObstacleAhead(distance, true, false)) {
                info.getGroup().getActions().clear();
                info.getMember().getActions().addActionWaitOccupied(distance, delay, launchDistance, launchDirection, launchVelocity)
                        .setToggleLeversOf(info.getAttachedBlock());
            }
        } else if (info.isAction(SignActionType.REDSTONE_OFF)) {
            info.setLevers(false);

            if (info.hasRailedMember()) {
                info.getGroup().getActions().clear();
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_WAIT)
                .setName("train waiter sign")
                .setDescription("waits the train until the tracks ahead are clear")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Waiter")
                .handle(event.getPlayer());
    }
}
