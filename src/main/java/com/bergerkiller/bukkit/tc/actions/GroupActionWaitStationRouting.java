package com.bergerkiller.bukkit.tc.actions;

import java.util.Collections;
import java.util.List;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Station;
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
    private TrainStatus.WaitingForRouting status = TrainStatus.WaitingForRouting.CALCULATING;

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
        // If not already centered, and this is our first update, we will
        // need to center the train first. As part of centering the train
        // could activate other signs that change the current destination.
        // So it's important centering completes first.
        if (!trainIsCentered) {
            station.centerTrain();
            station.waitTrainKeepLeversDown(0);
            getGroup().getActions().addAction(new GroupActionWaitStationRouting(station, rails, true))
                    .addTag(station.getTag());
            return true;
        }

        // Before wasting a lot of time, check there is a train destination at all
        String destination = getGroup().getProperties().getDestination();
        if (destination.isEmpty()) {
            // Fallback launch direction?
            return tryFallback(TrainStatus.WaitingForRouting.NO_DESTINATION);
        }

        // While processing, wait
        if (getTrainCarts().getPathProvider().isProcessing()) {
            status = TrainStatus.WaitingForRouting.CALCULATING;
            return false;
        }

        // If path finding is ready, see if this destination can be reached
        PathNode node = getTrainCarts().getPathProvider().getWorld(rails.world())
                .getNodeAtRail(rails.block());

        // If no node was found at this rail, attempt discovering new nodes at this rail position
        // Try this only once
        if (node == null && !discoveryStarted) {
            discoveryStarted = true;
            getTrainCarts().getPathProvider().discoverFromRail(new BlockLocation(rails.block()));
            return false;
        }

        // If node is still not found, try a fallback
        if (node == null) {
            return tryFallback(TrainStatus.WaitingForRouting.NO_ROUTE);
        }

        // If train is at the current destination already, wait and do nothing
        if (node.getNames().contains(destination)) {
            status = TrainStatus.WaitingForRouting.AT_DESTINATION;
            return false;
        }

        // Node found. Is this our destination? If so, don't do anything.
        // If it is not, try to find a route to the destination
        PathConnection connection = node.findConnection(destination);

        // Destination doesn't exist. Fallback?
        if (connection == null) {
            return tryFallback(TrainStatus.WaitingForRouting.NO_ROUTE);
        }

        // Translate the junction information to a direction vector to launch into
        Vector launchVector = null;
        for (RailJunction junction : rails.getJunctions()) {
            if (junction.name().equals(connection.junctionName)) {
                launchVector = junction.position().getMotion();
                break;
            }
        }
        if (launchVector == null) {
            return tryFallback(TrainStatus.WaitingForRouting.NO_ROUTE);
        }

        // Found a match! Now, do we have to center the train first?
        prepareLaunchTo(Util.vecToFace(launchVector, false));
        return true;
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        return Collections.singletonList(status);
    }

    private boolean tryFallback(TrainStatus.WaitingForRouting failStatus) {
        if (station.getNextDirection() != Direction.NONE) {
            prepareLaunchTo(station.getNextDirectionFace());
            return true;
        }

        status = failStatus;
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
