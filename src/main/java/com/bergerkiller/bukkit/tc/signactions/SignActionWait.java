package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.*;
import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
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
                launchDirection = Direction.parse(launchData[1]).getDirection(info.getFacing(), info.getCartDirection());
                launchVelocity = ParseUtil.parseDouble(launchData[2], (Double) info.getGroup().getAverageForce());
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
                    for (RailSignCache.TrackedSign sign : walkingPoint.state.railSigns()) {
                        if (sign.railBlock.equals(info.getRails())) {
                            continue;
                        }

                        SignActionEvent found = new SignActionEvent(sign.signBlock, sign.railBlock, info.getGroup());
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
            if (info.getGroup().getSpeedAhead(distance) != Double.MAX_VALUE) {
                info.getGroup().getActions().clear();
                info.getMember().getActions().addActionWaitOccupied(distance, delay, launchDistance, launchDirection, launchVelocity);
            }
        } else if (info.isAction(SignActionType.REDSTONE_OFF)) {
            if (!info.hasRailedMember()) return;

            info.getGroup().getActions().clear();
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return handleBuild(event, Permission.BUILD_WAIT, "train waiter sign", "waits the train until the tracks ahead are clear");
    }
}
