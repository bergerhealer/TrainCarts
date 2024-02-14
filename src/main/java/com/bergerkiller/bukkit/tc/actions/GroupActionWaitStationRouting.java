package com.bergerkiller.bukkit.tc.actions;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.ActionTracker;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
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

    public boolean isTrainCentered() {
        return trainIsCentered;
    }

    public Station getStation() {
        return station;
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

    public static class Serializer implements ActionRegistry.Serializer<GroupActionWaitStationRouting> {
        private final TrainCarts plugin;

        public Serializer(TrainCarts plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean save(GroupActionWaitStationRouting action, OfflineDataBlock data, ActionTracker tracker) throws IOException {
            // Save the station sign information (unique key) so we can restore this later
            final byte[] signData = plugin.getTrackedSignLookup().serializeUniqueKey(
                    action.getStation().getSignInfo().getTrackedSign().getUniqueKey());
            if (signData == null) {
                return false;
            }

            // If this was a [cart] station sign, some special stuff needs to be tracked
            if (action.getStation().getSignInfo().isCartSign()) {
                data.addChild("cart-station-member", stream -> {
                    StreamUtil.writeUUID(stream, action.getStation().getSignInfo().getMember().getEntity().getUniqueId());
                });
            }

            data.addChild("wait-station-routing", stream -> {
                Util.writeByteArray(stream, signData);
                stream.writeBoolean(action.isTrainCentered());
            });
            return true;
        }

        @Override
        public GroupActionWaitStationRouting load(OfflineDataBlock data, ActionTracker tracker) throws IOException {
            // Decode data
            final byte[] signData;
            final boolean trainIsCentered;
            try (DataInputStream stream = data.findChildOrThrow("wait-station-routing").readData()) {
                signData = Util.readByteArray(stream);
                trainIsCentered = stream.readBoolean();
            }

            // Decode and find the sign. If missing, fail.
            Object uniqueKey = plugin.getTrackedSignLookup().deserializeUniqueKey(signData);
            if (uniqueKey == null) {
                return null; // Fail!
            }

            RailLookup.TrackedSign trackedSign = plugin.getTrackedSignLookup().getTrackedSign(uniqueKey);
            if (trackedSign == null || trackedSign.isRemoved()) {
                return null; // Fail!
            }

            RailPiece rail = trackedSign.getRail();
            if (rail.isNone()) {
                return null; // Fail!
            }

            // Create a new sign event using this information
            SignActionEvent event = new SignActionEvent(trackedSign);
            if (event.isCartSign()) {
                // Find the specific cart that triggered this action
                final UUID memberUUID;
                try (DataInputStream stream = data.findChildOrThrow("cart-station-member").readData()) {
                    memberUUID = StreamUtil.readUUID(stream);
                }
                MinecartMember<?> member = null;
                for (MinecartMember<?> memberOfGroup : tracker.getGroupOwner()) {
                    if (memberUUID.equals((memberOfGroup.getEntity().getUniqueId()))) {
                        member = memberOfGroup;
                        break;
                    }
                }
                if (member == null) {
                    return null; // Fail! Member is gone.
                }

                event.setMember(member);
                event.setAction(SignActionType.MEMBER_ENTER);
            } else {
                event.setGroup(tracker.getGroupOwner());
                event.setAction(SignActionType.GROUP_ENTER);
            }

            // Initialize a station using this
            Station station = new Station(event);

            // And turn it all into an action
            return new GroupActionWaitStationRouting(station, rail, trainIsCentered);
        }
    }
}
