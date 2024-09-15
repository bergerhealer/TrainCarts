package com.bergerkiller.bukkit.tc.chest;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * An existing train on the tracks that can be extended (additional carts connected to it).
 * If found, this class can then be used to spawn these additional carts.
 */
public class TrainChestExtendableTrain {
    /** Member first encountered */
    public final MinecartMember<?> member;
    /** First position a gap away from the member */
    public final RailState startState;

    public TrainChestExtendableTrain(MinecartMember<?> member, RailState startState) {
        this.member = member;
        this.startState = startState;
    }

    /**
     * Tries to extend a train that was found occupying previously calculated spawn locations
     *
     * @param occupiedLocations List of occupied locations
     * @return Found extendable train, or null if none could be identified or there is no room on the track
     */
    public static TrainChestExtendableTrain findOccupied(List<SpawnableGroup.OccupiedLocation> occupiedLocations) {
        if (occupiedLocations.isEmpty()) {
            return null;
        }

        // This assumes the train chest was used to spawn into a direction, but then a member was found.
        // Pick the first slot that is occupied, and use that to find the relative direction vector.
        SpawnableGroup.OccupiedLocation firstOccupied = occupiedLocations.get(0);
        return findEndOfTrain(firstOccupied.member, firstOccupied.spawnLocation.forward.clone().multiply(-1.0));
    }

    /**
     * Attempts to find another train starting at the starting state and moving into its
     * direction specified.
     *
     * @param startState Start state to begin looking for a member to connect with
     * @param searchDistance Maximum distance to look for other trains past start state
     * @return Found extendable train, or null if none are found
     */
    public static TrainChestExtendableTrain find(RailState startState, double searchDistance) {
        if (startState.railPiece().isNone()) {
            return null;
        }

        // Create a track walking point and iterate the rails for a maximum distance
        // It's possible we encounter a train on the first track already,
        // so no skipping the first rail.
        TrackWalkingPoint p = new TrackWalkingPoint(startState);
        do {
            List<MinecartMember<?>> members = p.state.railPiece().members();
            if (members.isEmpty()) {
                continue;
            }

            // If there is more than one, pick the member closest to the start state
            // This step is not too important because we are going to be navigating to
            // the end of the train anyway, but it might avoid some errors.
            MinecartMember<?> bestMember = members.get(0);
            if (members.size() >= 2) {
                double lowestDistanceSq = Double.MAX_VALUE;
                Location startLoc = startState.positionLocation();
                for (MinecartMember<?> member : members) {
                    double distanceSq = member.getEntity().loc.distanceSquared(startLoc);
                    if (distanceSq < lowestDistanceSq) {
                        lowestDistanceSq = distanceSq;
                        bestMember = member;
                    }
                }
            }

            // Always pick first. It doesn't matter if there is more than one, because
            // we will be navigating from this member forwards anyway to find the front
            // or back of the train.
            return findEndOfTrain(bestMember, p.state.motionVector().clone().multiply(-1.0));
        } while (p.moveStep(searchDistance - p.movedTotal));

        // Not found
        return null;
    }

    private static TrainChestExtendableTrain findEndOfTrain(MinecartMember<?> member, Vector spawnDirection) {
        // What direction is forwards? Use the motion direction vector of the member.
        // Based on this pick either the first or last cart of the train
        RailState memberStartState;
        if (spawnDirection.dot(member.getRailTracker().getMotionVector()) >= 0.0) {
            // Front of train
            member = member.getGroup().head();
            memberStartState = member.getRailTracker().getState().clone();
        } else {
            // Back of train
            member = member.getGroup().tail();
            memberStartState = member.getRailTracker().getState().cloneAndInvertMotion();
        }

        // Walk half the width + a gap away from the member and return the position
        double extraDistance = 0.5 * member.getEntity().getWidth() + member.getGroup().getCartGap();
        TrackWalkingPoint p = new TrackWalkingPoint(memberStartState);
        p.skipFirst();
        if (!p.move(extraDistance)) {
            return null; // Fail! I guess it didn't actually connect? Or no room on track?
        }

        return new TrainChestExtendableTrain(member, p.state);
    }
}
