package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Station;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitStationRouting;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.block.BlockFace;

public class SignActionStation extends TrainCartsSignAction {

    public SignActionStation() {
        super("station");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE)) {
            return;
        }
        if (info.isAction(SignActionType.GROUP_LEAVE)) {
            if (info.getGroup().getActions().isWaitAction()) {
                info.getGroup().getActions().clear();
            }
            info.setLevers(false);
            return;
        }
        if (!info.hasRails() || !info.hasGroup() || info.getGroup().isEmpty()) {
            return;
        }
        //Check if not already targeting
        MinecartGroup group = info.getGroup();
        Station station = new Station(info);

        //What do we do?
        if (station.getInstruction() == null) {
            // Clear actions, but only if requested to do so because of a redstone change
            if (info.isAction(SignActionType.REDSTONE_CHANGE)) {
                if (info.getGroup().getActions().isCurrentActionTag(station.getTag())) {
                    info.getGroup().getActions().clear();
                }
            }
        } else if (station.getInstruction() == BlockFace.SELF) {
            MinecartMember<?> centerMember = station.getCenterPositionCart();
            // Do not allow redstone changes to center a launching train
            if (info.isAction(SignActionType.REDSTONE_CHANGE) && (centerMember.isMovementControlled() || info.getGroup().isMoving())) {
                return;
            }

            // This erases all previous (station/launch) actions scheduled for the train
            // It allows this station to fully redefine what the train should be doing
            group.getActions().launchReset();

            // Train is waiting on top of the station sign indefinitely, with no end-condition
            if (!station.isAutoRouting() && station.getNextDirection() == Direction.NONE) {
                station.centerTrain();
                station.waitTrain(Long.MAX_VALUE);
                return;
            }

            // If auto-routing, perform auto-routing checks and such
            // All this logic runs one tick delayed, because it is possible a destination sign
            // sits on the same block as this station sign. We want the destination sign logic
            // to execute before the station does routing, otherwise this can go wrong.
            // It also makes it much easier to wait for path finding to finish, or for
            // a (valid) destination to be set on the train.
            if (station.isAutoRouting()) {
                // If there's a delay, wait for that delay and toggle levers, but do
                // not toggle levers back up after the delay times out. This is because
                // the actual station routing logic may want to hold the train for longer.
                if (station.hasDelay()) {
                    station.centerTrain();
                    station.waitTrainKeepLeversDown(station.getDelay());
                }

                // All the station auto-routing logic occurs in this action, which may spawn
                // new actions such as centering the train or launching it again.
                group.getActions().addAction(
                        new GroupActionWaitStationRouting(station,
                                info.getRailPiece(), station.hasDelay()))
                        .addTag(station.getTag());
                return;
            }

            // Order the train to center prior to launching again if not launching into the same
            // direction the train is already moving. Respect any set delay on the sign.
            // Levers are automatically toggled as part of waiting
            BlockFace trainDirection = station.getNextDirectionFace();
            if (station.hasDelay()) {
                station.centerTrain();
                station.waitTrain(station.getDelay());
            } else if (!info.getMember().isDirectionTo(trainDirection)) {
                station.centerTrain();
            }

            // Launch into the direction
            station.launchTo(trainDirection);
        } else {
            //Launch
            group.getActions().launchReset();

            if (station.hasDelay() || (group.head().isMoving() && !info.getMember().isDirectionTo(station.getInstruction()))) {
                //Reversing or has delay, need to center it in the middle first
                station.centerTrain();
            }
            if (station.hasDelay()) {
                station.waitTrain(station.getDelay());
            }
            station.launchTo(station.getInstruction());
        }
    }

    @Override
    public void predictPathFinding(SignActionEvent info, PathPredictEvent prediction) {
        // Parse the components of the station config we actually care about (performance)
        Station.StationConfig stationConfig = new Station.StationConfig();
        stationConfig.setAutoModeUsingSign(info);
        if (!stationConfig.isAutoRouting()) {
            return;
        }

        stationConfig.setInstructionUsingSign(info);
        if (stationConfig.getInstruction() != BlockFace.SELF) {
            return;
        }

        PathNode node = PathNode.getOrCreate(info.getRails());
        if (node == null) {
            return;
        }

        node.addSwitcher();
        if (info.getTrainCarts().getPathProvider().isProcessing()) {
            // Train should wait until this is done. Polls every tick.
            prediction.setSpeedLimit(0.0);
            return;
        }

        // Continue with path finding if a valid destination is specified
        // If the current node denotes the destination - don't switch!
        String destination = prediction.group().getProperties().getDestination();
        if (!LogicUtil.nullOrEmpty(destination) && !node.containsName(destination)) {
            PathConnection conn = node.findConnection(destination);
            if (conn != null) {
                RailJunction junction = info.findJunction(conn.junctionName);
                if (junction != null) {
                    prediction.setSwitchedJunction(junction);
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_STATION)
                .setName("station")
                .setDescription("stop, wait and launch trains")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Station")
                .handle(event);
    }

    @Override
    public boolean overrideFacing() {
        return true;
    }

    @Override
    public boolean isRailSwitcher(SignActionEvent info) {
        // This causes too many problems because it expects a train
        //return StationConfig.fromSign(info).isAutoRouting();

        // Check last line of sign for 'route' keyword
        for (String part : info.getLine(3).split(" ")) {
            if (part.equalsIgnoreCase("route")) {
                return true;
            }
        }
        return false;
    }
}
