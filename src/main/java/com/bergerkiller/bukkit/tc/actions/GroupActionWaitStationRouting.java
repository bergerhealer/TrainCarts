package com.bergerkiller.bukkit.tc.actions;

import java.util.Collections;
import java.util.List;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Station;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;

public class GroupActionWaitStationRouting extends GroupAction implements WaitAction {
    private final Station station;
    private final RailPiece rails;
    private final boolean trainIsCentered;
    private boolean discoveryStarted = false;

    public GroupActionWaitStationRouting(Station station, RailPiece rails, boolean trainIsCentered) {
        this.station = station;
        this.rails = rails;
        this.trainIsCentered = trainIsCentered;
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }

    @Override
    public boolean update() {
        // Before wasting a lot of time, check there is a train destination at all
        String destination = getGroup().getProperties().getDestination();
        if (destination.isEmpty()) {
            // Fallback?
            if (tryFallback()) {
                return true;
            }
        } else if (!getTrainCarts().getPathProvider().isProcessing()) {
            // If path finding is ready, see if this destination can be reached
            PathNode node = getTrainCarts().getPathProvider().getWorld(rails.world())
                    .getNodeAtRail(rails.block());
            if (node == null && !discoveryStarted) {
                // Not found yet, may need to be discovered. Actually, probably will need to be!
                discoveryStarted = true;
                getTrainCarts().getPathProvider().discoverFromRail(new BlockLocation(rails.block()));
            } else if (node != null && !node.getNames().contains(destination)) {
                // Node found. Is this our destination? If so, don't do anything.
                // If it is not, try to find a route to the destination
                PathConnection connection = node.findConnection(destination);
                if (connection == null) {
                    // Destination doesn't exist. Fallback?
                    if (tryFallback()) {
                        return true;
                    }
                } else {
                    // Translate the junction information to a direction vector to launch into
                    Vector launchVector = null;
                    for (RailJunction junction : rails.getJunctions()) {
                        if (junction.name().equals(connection.junctionName)) {
                            launchVector = junction.position().getMotion();
                            break;
                        }
                    }
                    if (launchVector != null) {
                        // Found a match! Now, do we have to center the train first?
                        prepareLaunchTo(Util.vecToFace(launchVector, false));
                        return true;
                    }
                }
            }
        }

        // If not already centered, and this is our first update, we will
        // need to center the train first. For the remainder we're just waiting
        // for conditions to be right.
        if (!trainIsCentered) {
            station.centerTrain();
            station.waitTrainKeepLeversDown(0);
            getGroup().getActions().addAction(new GroupActionWaitStationRouting(station, rails, true))
                    .addTag(station.getTag());
            return true;
        }

        // Wait
        return false;
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        return Collections.singletonList(new TrainStatus.WaitingForRouting());
    }

    private boolean tryFallback() {
        if (station.getNextDirection() != Direction.NONE) {
            prepareLaunchTo(station.getNextDirectionFace());
            return true;
        }

        return false;
    }

    private void prepareLaunchTo(BlockFace direction) {
        if (!trainIsCentered && !station.getSignInfo().getMember().isDirectionTo(direction)) {
            station.centerTrain();
        }
        station.setLevers(false);
        station.launchTo(direction);
    }
}
