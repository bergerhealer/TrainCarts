package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatusProvider;
import com.bergerkiller.bukkit.tc.events.MutexZoneConflictEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCacheWorld;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlot;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlot.EnteredGroup;
import com.bergerkiller.bukkit.tc.utils.ForwardChunkArea;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Checks the rails ahead of the train for any obstacles that exist there.
 * These can be stationary obstacles, like mutex zones and blocker signs,
 * but also moving obstacles like other trains.<br>
 * <br>
 * With this information it controls the maximum speed of the train to maintain
 * distance from these obstacles as configured. Uses the configured acceleration
 * and deceleration to slow down the train, or launch it again to the original
 * speed when the blockage up ahead clears.
 */
public class ObstacleTracker implements TrainStatusProvider {
    private final MinecartGroup group;
    private double waitDistanceLastSpeedLimit = Double.MAX_VALUE;
    private double waitDistanceLastTrainSpeed = Double.MAX_VALUE;
    private int waitRemainingTicks = Integer.MAX_VALUE;
    private ObstacleSpeedLimit lastObstacleSpeedLimit = ObstacleSpeedLimit.NONE;
    private List<MutexZone> enteredMutexZones = Collections.emptyList();
    private int tickCounter = 0;

    public ObstacleTracker(MinecartGroup group) {
        this.group = group;
    }

    /**
     * Gets the speed limit imposed by this waiter, for the current sub-physics tick.
     * Returns MAX_VALUE if no limit is imposed.
     * This speed limit is in absolute speeds, no update speed factor is involved.
     *
     * @return speed limit, MAX_VALUE if none is imposed
     */
    public double getSpeedLimit() {
        return this.waitDistanceLastSpeedLimit;
    }

    /**
     * Gets the number of obstacle tracker update ticks that have elapsed since the group's
     * creation. This counter can be used by obstacles to check whether group has seen
     * it since last tick.
     *
     * @return tick counter
     */
    public int getTickCounter() {
        return this.tickCounter;
    }

    /**
     * Main update tick function. Checks if the train should slow down, or use altered speeds,
     * and if so, returns a new max speed value the train should use. This operates in the
     * speed-factor applied to domain. Meaning this update() function is called multiple
     * times per tick at high speeds.
     * 
     * @param trainSpeed Current true speed of the train (update speed factor accounted for)
     * @return speed the train should limit itself to.
     */
    public void update(double trainSpeed) {
        TrainProperties properties = group.getProperties();

        // Increment mutex tick counter every tick this is called
        ++tickCounter;

        // Calculate the amount of distance ahead of the train we have to look for other
        // trains or mutex zones or any other type of obstacle. This is calculated based
        // on the maximum projected movement the train will have this tick. This is the
        // full train speed, limited by the applied speed limit right now
        double searchAheadDistance = Math.max(1.0, properties.getSpeedLimit() + 0.5); // Little extra to avoid skipping past obstacles in a single tick
        if (properties.getWaitDeceleration() > 0.0) {
            double speedLimitLastTick = (this.waitDistanceLastSpeedLimit == Double.MAX_VALUE) ?
                    properties.getSpeedLimit() : this.waitDistanceLastSpeedLimit;
            double maxProjectedSpeed = Math.min(trainSpeed, speedLimitLastTick);

            // At the current speed, how much extra distance does it take to slow the train down to 0?
            // Look for obstacles this much extra distance ahead, to allow for stopping in time.
            // As this train slows down due to a reduced speed limit, we have to check less and less
            // distance up a head, since it takes less long to slow to a complete stop.

            // Based on this formula: (v^2 - u^2) / (2s) = a where v=0
            //                        (2s) = -(v^2) / a
            //                        s = -(v^2) / a / 2
            // Where: a = acceleration, v=final speed (0), u=start speed, s=distance
            // This computes the rough distance traveled de-accellerating
            searchAheadDistance += 0.5 * (maxProjectedSpeed * maxProjectedSpeed) / properties.getWaitDeceleration();
        }

        // Update the last speed the train actually had. This forms the basis for the
        // slowdown calculations. The current speed has not yet resulted in
        // a position update, so we are safe in overriding that in this current tick. But
        // last tick's speed resulted in movement, so that cannot be dramatically deviated
        // from.
        double baseSpeedLimitThisTick;
        {
            double speedLimitLastTick = (this.waitDistanceLastSpeedLimit == Double.MAX_VALUE) ?
                    properties.getSpeedLimit() : this.waitDistanceLastSpeedLimit;
            double trainSpeedLastTick = (this.waitDistanceLastTrainSpeed == Double.MAX_VALUE) ?
                    trainSpeed : this.waitDistanceLastTrainSpeed;
            baseSpeedLimitThisTick = Math.min(speedLimitLastTick, trainSpeedLastTick);

            this.waitDistanceLastTrainSpeed = trainSpeed;
        }

        // At the current speed, how much extra distance does it take to slow the train down to 0?
        // Look for obstacles this much extra distance ahead, to allow for stopping in time.
        // As this train slows down due to a reduced speed limit, we have to check less and less
        // distance up a head, since it takes less long to slow to a complete stop.
        boolean checkTrains = (properties.getWaitDistance() > 0.0);

        ObstacleSpeedLimit newDesiredSpeed = getDesiredSpeedLimit(searchAheadDistance,
                properties.getWaitDeceleration(), checkTrains, true, properties.getWaitDistance());

        // Every time the speed drops to 0 consistently, reset the wait tick timer to 0
        // This causes it to wait until the remaining ticks reaches the configured delay
        if (this.waitDistanceLastSpeedLimit <= 1e-6 && newDesiredSpeed.speed <= 1e-6) {
            this.waitRemainingTicks = 0;
            this.waitDistanceLastSpeedLimit = newDesiredSpeed.speed;
            return;
        }

        // Until the configured delay is reached, keep the speed on 0
        if (this.waitRemainingTicks != Integer.MAX_VALUE) {
            double delay = properties.getWaitDelay();
            if (delay <= 0.0) {
                this.waitRemainingTicks = Integer.MAX_VALUE; // No delay
            } else {
                if (++this.waitRemainingTicks >= MathUtil.ceil(delay*20.0)) {
                    this.waitRemainingTicks = Integer.MAX_VALUE; // Delay elapsed
                }
                this.waitDistanceLastSpeedLimit = 0.0;
                return;
            }
        }

        // Unlimited speed, speed up the train and stop once speed limit is reached
        // Speed up based on configured acceleration
        if (newDesiredSpeed.speed >= properties.getSpeedLimit()) {
            if (this.waitDistanceLastSpeedLimit >= newDesiredSpeed.speed) {
                this.waitDistanceLastSpeedLimit = Double.MAX_VALUE;
            }
            if (this.waitDistanceLastSpeedLimit != Double.MAX_VALUE) {
                double acceleration = properties.getWaitAcceleration();
                if (acceleration > 0.0) {
                    this.waitDistanceLastSpeedLimit += acceleration;
                    if (this.waitDistanceLastSpeedLimit >= properties.getSpeedLimit()) {
                        this.waitDistanceLastSpeedLimit = Double.MAX_VALUE;
                    }
                } else {
                    this.waitDistanceLastSpeedLimit = Double.MAX_VALUE;
                }
            }
            return;
        }

        // If no speed limit was imposed before, set one right now
        if (this.waitDistanceLastSpeedLimit == Double.MAX_VALUE) {
            this.waitDistanceLastSpeedLimit = properties.getSpeedLimit();
        }

        double speedDiff = (newDesiredSpeed.speed - this.waitDistanceLastSpeedLimit);
        if (speedDiff >= 0.0) {
            // Speed up
            double acceleration = properties.getWaitAcceleration();
            if (acceleration <= 0.0 || acceleration >= speedDiff) {
                this.waitDistanceLastSpeedLimit = newDesiredSpeed.speed;
            } else {
                this.waitDistanceLastSpeedLimit += acceleration;
            }
        } else {
            double deceleration = properties.getWaitDeceleration();
            if (deceleration <= 0.0 || deceleration >= (-speedDiff) || newDesiredSpeed.instant) {
                // Slow down to the new speed instantly
                this.waitDistanceLastSpeedLimit = newDesiredSpeed.speed;
            } else if (newDesiredSpeed.speed > baseSpeedLimitThisTick) {
                // Use desired speed directly, as it's higher than the current minimum speed
                this.waitDistanceLastSpeedLimit = newDesiredSpeed.speed;
            } else {
                // Slow down gradually
                this.waitDistanceLastSpeedLimit = baseSpeedLimitThisTick - deceleration;
            }
        }
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        if (!this.lastObstacleSpeedLimit.hasLimit() && this.enteredMutexZones.isEmpty()) {
            return Collections.emptyList();
        }

        List<TrainStatus> statuses = new ArrayList<>();

        if (!this.enteredMutexZones.isEmpty()) {
            // Group zones by slot
            IdentityHashMap<MutexZoneSlot, List<MutexZone>> zones = new IdentityHashMap<>();
            for (MutexZone zone : this.enteredMutexZones) {
                zones.compute(zone.slot, (s, curr_zones) -> {
                    ArrayList<MutexZone> newZones = new ArrayList<>();
                    if (curr_zones != null) {
                        newZones.addAll(curr_zones);
                    }
                    newZones.add(zone);
                    return newZones;
                });
            }

            // Add statuses
            for (Map.Entry<MutexZoneSlot, List<MutexZone>> e : zones.entrySet()) {
                // Find EnteredGroup that matches this owner
                EnteredGroup entered = e.getKey().findEntered(group);
                statuses.add(new TrainStatus.EnteredMutexZone(e.getKey(), e.getValue(), entered));
            }
        }

        if (this.lastObstacleSpeedLimit.hasLimit()) {
            statuses.add(this.lastObstacleSpeedLimit.getStatus());
        } else if (this.waitRemainingTicks != Integer.MAX_VALUE) {
            double remaining = this.group.getProperties().getWaitDelay() - (double) this.waitRemainingTicks * 0.05;
            statuses.add(new TrainStatus.WaitingForDelay(remaining));
        }

        return statuses;
    }

    /**
     * Calculates the desired speed limit the train should ideally have right now.
     * Based on acceleration/deceleration, the actual speed limit is adjusted to reach
     * this speed.
     * 
     * @param searchAheadDistance How much distance ahead of the train to look for obstacles
     *                            that would alter the maximum desired speed.
     * @param deceleration The de-acceleration the train has to stop for the obstacle. 0 for instant.
     *                     This controls the speed limit found, assuming the train can stop at this rate
     *                     to avoid collision in the future.
     * @param checkTrains Whether to check for trains ahead blocking the track
     * @param checkRailObstacles Whether to check for rail obstacles, like mutex zones
     * @param trainDistance How much extra distance should be kept between this train and any
     *                      other trains ahead
     * @return desired speed limit
     */
    private ObstacleSpeedLimit getDesiredSpeedLimit(double searchAheadDistance, double deceleration,
            boolean checkTrains, boolean checkRailObstacles, double trainDistance
    ) {
        // Find obstacles. Update the mutex zone found (train status)
        ObstacleFinder finder = new ObstacleFinder(Math.min(2000.0, searchAheadDistance),
                                                   checkTrains, checkRailObstacles, trainDistance);
        List<Obstacle> obstacles = finder.search();
        this.enteredMutexZones = finder.enteredMutexZones;
        return this.lastObstacleSpeedLimit = minimumSpeedLimit(obstacles, deceleration);
    }

    /**
     * Looks up ahead on the track for obstacles. These can be other trains, or mutex
     * signs that disallow movement further.
     * 
     * @param distance Distance in blocks to check ahead of the train
     * @param checkTrains Whether to look for trains or only for mutex signs
     * @param checkRailObstacles Whether to check for rail obstacles, like mutex zones
     * @param trainDistance If checkTrains true, what distance to subtract from obstacles
     *                      distance to maintain a safety distance from them.
     * @return obstacle that was detected, null if there is no obstacle
     */
    public List<Obstacle> findObstaclesAhead(double distance, boolean checkTrains, boolean checkRailObstacles, double trainDistance) {
        return (new ObstacleFinder(distance, checkTrains, checkRailObstacles, trainDistance)).search();
    }

    /**
     * Finds the minimum speed limit in a Collection of obstacles. If the collection
     * is empty, returns {@link #NONE}
     *
     * @param obstacles Obstacles
     * @param deceleration Maximum rate of deceleration
     * @return Minimum speed limit to avoid the nearest obstacle
     */
    public static ObstacleSpeedLimit minimumSpeedLimit(Iterable<Obstacle> obstacles, double deceleration) {
        ObstacleSpeedLimit min = ObstacleSpeedLimit.NONE;
        for (Obstacle obstacle : obstacles) {
            ObstacleSpeedLimit limit = obstacle.findSpeedLimit(deceleration);
            if (limit.speed < min.speed) {
                min = limit;
            }
        }
        return min;
    }

    /**
     * Finds the minimum speed limit in a Collection of speed limits. If the collection
     * is empty, returns {@link #NONE}
     *
     * @param limits Limits
     * @return Minimum
     */
    public static ObstacleSpeedLimit minimumSpeedLimit(Iterable<ObstacleSpeedLimit> limits) {
        ObstacleSpeedLimit min = ObstacleSpeedLimit.NONE;
        for (ObstacleSpeedLimit limit : limits) {
            if (limit.speed < min.speed) {
                min = limit;
            }
        }
        return min;
    }

    /**
     * Searches for obstacles up ahead on the track. One instance of this class represents
     * a single search operation and cannot be re-used.
     */
    private class ObstacleFinder {
        final double distance;
        final boolean checkTrains;
        final boolean checkRailObstacles;
        final double trainDistance;

        // Take into account that the head minecart has a length also, so we count distance from the edge (half length)
        // TODO: This does not take into account wheel offset!!!
        final double selfCartOffset;

        // The actual minimum distance allowed from the walking point position to any minecarts discovered
        // This takes into account that the start position is halfway the length of the Minecart
        // When distance is not greater than 0, we don't check for other trains at all.
        double waitDistance;

        // Two blocks are used to slow down the train, to make it match up to speed with the train up ahead
        // Check for any mutex zones ~2 blocks ahead, and stop before we enter them
        // If a wait distance is set, also check for trains there
        final double mutexHardDistance;
        final double mutexSoftDistance;
        final double checkDistance;

        // If rail obstacles are found which impose a 0-speed speed limit, this is set to the distance
        // away from the train these are found. Is used to not add too many obstacles that fall after
        // such a 0-speed obstacle, as there is no real use tracking those.
        double closestHardRailObstacle = Double.MAX_VALUE;

        // Last-encountered speed limit imposed by a rail obstacle
        double lastRailSpeedLimit = Double.MAX_VALUE;

        // Tracks the current mutex zone the train is inside of while navigating the track
        MutexZone currentMutex = null;
        MutexZoneSlot.EnteredGroup currentMutexGroup = null;
        boolean currentMutexHard = false;

        // Mutex zones that have been (soft-) entered
        public List<MutexZone> enteredMutexZones = Collections.emptyList();

        // Resulting obstacles
        List<Obstacle> obstacles = new ArrayList<>();

        public ObstacleFinder(double distance, boolean checkTrains, boolean checkRailObstacles, double trainDistance) {
            this.distance = distance;
            this.checkTrains = checkTrains;
            this.checkRailObstacles = checkRailObstacles;
            this.trainDistance = trainDistance;
            this.selfCartOffset = (0.5 * group.head().getEntity().getWidth());
            this.waitDistance = distance + trainDistance;
            this.mutexHardDistance = 0.0;
            this.mutexSoftDistance = 2.0 + distance;
            this.checkDistance = selfCartOffset + Math.max(mutexSoftDistance, waitDistance) + 1.0;
        }

        public List<Obstacle> search() {
            // Not sure if fixed, but skip if this train is empty
            if (group.isEmpty()) {
                group.getChunkArea().getForwardChunkArea().reset();
                return Collections.emptyList();
            }

            ForwardChunkArea forwardChunks = null;
            if (group.getProperties().isKeepingChunksLoaded()) {
                forwardChunks = group.getChunkArea().getForwardChunkArea();
                forwardChunks.begin();
            } else {
                group.getChunkArea().getForwardChunkArea().reset();
            }

            MutexZoneCacheWorld.MovingPoint mutexZones = group.head().railLookup().getMutexZones()
                    .track(group.head().getEntity().loc.block());

            // If no wait distance is set and no mutex zones are anywhere close, skip these expensive calculations
            if (distance <= 0.0 && trainDistance <= 0.0 && (!checkRailObstacles || !mutexZones.isNear())) {
                return Collections.emptyList();
            }

            RailState startState = group.head().discoverRail();
            startState.setMember(null); // Make sure this is NOT used for prediction
            TrackWalkingPoint iter = new TrackWalkingPoint(startState);
            if (group.getProperties().isWaitPredicted()) {
                iter.setFollowPredictedPath(group.head());
            }

            while (iter.movedTotal <= checkDistance && iter.moveFull()) {
                // The distance traveled from the physical front of the cart
                // The first iteration will likely have a negative distance
                double distanceFromFront = iter.movedTotal - selfCartOffset;

                // Refresh that we've visited this rail/position block, keeping the area loaded for this tick
                if (forwardChunks != null) {
                    forwardChunks.addBlock(iter.state.railBlock());
                }

                if (checkRailObstacles) {
                    // Check last smart mutex still valid for the current rail
                    MutexZone prevMutex = currentMutex;
                    if (currentMutex != null && !currentMutex.containsBlock(iter.state.positionOfflineBlock().getPosition())) {
                        // Exited the mutex zone
                        currentMutex = null;
                    }

                    // If the current rail block imposes a speed limit, set that right now
                    // Don't check for these if we already found a 0-speed obstacle before
                    // Ignore successive equal speed limits (speed traps), that would cause too many obstacles
                    boolean checkForNewHardObstacles = (distanceFromFront < closestHardRailObstacle);
                    if (checkForNewHardObstacles) {
                        double railSpeedLimit = iter.getPredictedSpeedLimit();
                        if (railSpeedLimit < lastRailSpeedLimit) {
                            lastRailSpeedLimit = railSpeedLimit;
                            obstacles.add(new RailObstacle(distanceFromFront, railSpeedLimit, iter.state.railPiece()));
                            if (railSpeedLimit <= 0.0) {
                                closestHardRailObstacle = distanceFromFront;
                                checkForNewHardObstacles = false;
                            }
                        }
                    }

                    // Check for mutex zones the next block. If one is found that is occupied, stop right away
                    if (currentMutex == null) {
                        boolean checkForNewMutexes = (checkForNewHardObstacles && distanceFromFront < mutexSoftDistance);
                        if (prevMutex != null || checkForNewMutexes) {
                            MutexZoneCacheWorld.MutexZoneResult newMutexResult = mutexZones.get(iter);
                            if (newMutexResult != null) {
                                // If checking for soft mutexes, always allow if its within range
                                // If not, it must be the same slot / expanded smart mutex zone to count
                                double distanceToMutex = distanceFromFront + newMutexResult.distance;
                                boolean accept;
                                if (prevMutex != null && prevMutex.slot == newMutexResult.zone.slot) {
                                    accept = true;
                                } else {
                                    accept = checkForNewMutexes && (distanceToMutex < mutexSoftDistance);
                                }
                                if (accept) {
                                    currentMutex = newMutexResult.zone;
                                    currentMutexGroup = newMutexResult.zone.slot.track(group, distanceToMutex);
                                    currentMutexHard = currentMutexGroup.distanceToMutex <= mutexHardDistance;
                                }
                            }
                        }
                    }

                    // Refresh smart mutex zones' occupied rail blocks.
                    if (currentMutex != null) {
                        updateCurrentMutex(iter);
                    }
                }

                // Only check for trains on the rails when a wait distance is set
                if (!checkTrains) {
                    continue;
                }

                // Check all other minecarts on the same rails to see if they are too close
                Location state_position = null;
                Location member_position = null;
                for (MinecartMember<?> member : iter.state.railPiece().members()) {
                    if (member.isUnloaded() || member.getEntity().isRemoved() || member.getGroup() == group) {
                        continue;
                    }

                    // Retrieve & re-use (readonly)
                    if (state_position == null) {
                        state_position = iter.state.positionLocation();
                    }

                    // Member center position & re-use (readonly)
                    if (member_position == null) {
                        member_position = member.getEntity().getLocation();
                    } else {
                        member.getEntity().getLocation(member_position);
                    }

                    // Is the minecart 'in front' of the current position on the rails, or behind us?
                    // This is important when iterating over the first track only, because then this is not guaranteed
                    if (iter.movedTotal == 0.0) {
                        Vector delta = new Vector(member_position.getX() - state_position.getX(),
                                                  member_position.getY() - state_position.getY(),
                                                  member_position.getZ() - state_position.getZ());
                        if (delta.dot(iter.state.motionVector()) < 0.0) {
                            continue;
                        }
                    }

                    // Compute distance from the current rail position to the 'edge' of the minecart.
                    // This is basically the distance to center, with half the length of the minecart subtracted.
                    double distanceToMember = member_position.distance(state_position) -
                                              (double) member.getEntity().getWidth() * 0.5;

                    // Movement speed of the minecart, taking maximum speed into account
                    Vector member_velocity = member.getEntity().getVelocity();
                    double speedAhead = Math.min(member_velocity.length(), member.getEntity().getMaxSpeed());
                    if (speedAhead < 0.0) {
                        speedAhead = 0.0; // In case of negative max speed (???)
                    }

                    if (speedAhead > 1e-6 && iter.state.position().motDot(member_velocity) < 0.0) {
                        // If moving towards me, assume speed of 0. If too close, will slow down to a stop.
                        obstacles.add(new TrainObstacle(distanceFromFront + distanceToMember, trainDistance, 0.0, member));
                    } else {
                        // Following another train
                        obstacles.add(new TrainObstacle(distanceFromFront + distanceToMember, trainDistance, speedAhead, member));
                    }
                }
            }

            // While we are still updating mutex information, navigate the track until we exit the zone
            // This is only important for smart mutexes
            // This might cause a new obstacle to be inserted from when the train reached the start of the zone
            if (currentMutex != null) {
                // Exceeding 64 blocks we enable the loop filter, as we probably reached an infinite loop of sorts...
                double enabledLoopFilterLimit = iter.movedTotal + 64.0;
                while (!currentMutexGroup.isOccupiedFully() && iter.moveFull()) {
                    if (iter.movedTotal >= enabledLoopFilterLimit) {
                        enabledLoopFilterLimit = Double.MAX_VALUE;
                        iter.setLoopFilter(true);
                    }

                    // Refresh that we've visited this rail/position block, keeping the area loaded for this tick
                    if (forwardChunks != null) {
                        forwardChunks.addBlock(iter.state.railBlock());
                    }

                    // Check still within mutex. If not, abort.
                    // Do check whether perhaps a different mutex zone with the same slot as the previous
                    // one exists at this position. In that case, continue looking.
                    IntVector3 currBlockPos = iter.state.positionOfflineBlock().getPosition();
                    if (!currentMutex.containsBlock(currBlockPos)) {
                        MutexZoneCacheWorld.MutexZoneResult otherMutex = mutexZones.get(iter);
                        if (otherMutex == null || otherMutex.zone.slot != currentMutex.slot) {
                            break;
                        }

                        // Resume
                        currentMutex = otherMutex.zone;
                    }

                    // Update
                    if (!updateCurrentMutex(iter)) {
                        break;
                    }
                }
            }

            return obstacles;
        }

        /**
         * Updates the current mutex being tracked. Returns whether to continue feeding it more track.
         *
         * @param iter
         * @return True if more track is requested
         */
        private boolean updateCurrentMutex(TrackWalkingPoint iter) {
            MutexZoneSlot.EnterResult result;
            result = currentMutexGroup.enter(currentMutex.type,                      /* Mutex zone slot type */
                                             iter.state.railPiece().blockPosition(), /* Rail block */
                                             currentMutexHard);                      /* Really needs to enter it */

            // Track mutex zones we have entered or are approaching (train status!)
            if (!enteredMutexZones.contains(currentMutex)) {
                if (enteredMutexZones.isEmpty()) {
                    enteredMutexZones = new ArrayList<MutexZone>();
                }
                enteredMutexZones.add(currentMutex);
            }

            double currentMutexDistance = currentMutexGroup.distanceToMutex;
            if (result.isOccupied()) {
                // Add the mutex zone as a new obstacle, if there is no other obstacle closerby
                // This also protects against a large amount of obstacles with the OCCUPIED_DISCOVER result
                if (currentMutexDistance < closestHardRailObstacle) {
                    closestHardRailObstacle = currentMutexDistance;
                    obstacles.add(new MutexZoneObstacle(currentMutexDistance, 0.0, currentMutex));
                }

                // For DISCOVER, discover more rails until we exit the zone to fully map out the blocks
                if (result == MutexZoneSlot.EnterResult.OCCUPIED_DISCOVER) {
                    return true;
                }

                // Hard occupied, we can stop checking this mutex or mutexes in general
                currentMutex = null;
                currentMutexGroup = null;
                return false;
            } else if (result.isConflict()) {
                // Mutex broke! Just keep on moving and hope the problem "solves" itself...
                if (result == MutexZoneSlot.EnterResult.CONFLICT) {
                    MutexZoneConflictEvent conflict = currentMutexGroup.getConflict();
                    if (TCConfig.logMutexConflicts) {
                        Logger l = group.getTrainCarts().getLogger();
                        l.log(Level.WARNING, "[Mutex] Train '" + group.getProperties().getTrainName() +
                                "' is in violation inside mutex '" +
                                conflict.getMutexZoneSlot().getNameWithoutWorldUUID() +
                                "' crossing train '" +
                                conflict.getGroupCrossed().getProperties().getTrainName() +
                                "' at rail " + conflict.getRailPosition());
                    }

                    CommonUtil.callEvent(conflict);
                }
                return true;
            } else if (result == MutexZoneSlot.EnterResult.SUCCESS) {
            } else if (result == MutexZoneSlot.EnterResult.IGNORED) {
                // Break out, no need to check this.
                return false;
            }

            return true;
        }
    }

    /**
     * A detected obstacle ahead of the train. Includes information about how far away the obstacle is,
     * and the speed the obstacle is moving away from the train. To calculate a safe speed for
     * the train to avoid it, use {@link Obstacle#findSpeedLimit(double)}
     */
    public static abstract class Obstacle {
        public final double distance;
        public final double speed;

        public Obstacle(double distance, double speed) {
            this.distance = distance;
            this.speed = speed;
        }

        /**
         * Whether this obstacle itself is actually moving at the {@link #speed} specified.
         * In that case, the train behind should take distance into account while adjusting speed.
         *
         * @return True if this obstacle is a moving one
         */
        public boolean isObstacleMoving() {
            return false;
        }

        /**
         * Creates a suitable train status for this Obstacle
         *
         * @param speedLimit Speed limit calculated using {@link #findSpeedLimit(double)}
         * @return Train Status
         */
        protected abstract TrainStatus createStatus(ObstacleSpeedLimit speedLimit);

        /**
         * Gets the maximum speed the original train that found this obstacle can have without
         * colliding with it at the train's wait deceleration rate.
         *
         * @param deceleration The rate of deceleration the train can have at most. Use 0.0 or
         *                     {@link Double#MAX_VALUE} for instantaneous.
         * @return speed of the train to stay clear of the obstacle
         */
        public ObstacleSpeedLimit findSpeedLimit(double deceleration) {
            // If distance is extremely close to 0, only track the obstacle with constant speed
            // Trying to correct for this causes horrible jitter in long train following chains
            if (distance > -1e-6 && distance < 1e-6) {
                return new ObstacleSpeedLimit(this, Math.max(0.0, speed), true);
            }

            // If obstacle is closer than it is allowed to ever be, emergency stop
            // This also ignores the vehicle's actual speed, because we're too close to
            // it already, so we need to stop and wait for the distance to go above
            // the safe wait distance threshold again.
            if (distance <= 0.0) {
                if (this.isObstacleMoving()) {
                    // Adjust the distance spacing between this cart and the obstacle
                    // We can do this because the obstacle ahead is moving (or, we just stop)
                    return new ObstacleSpeedLimit(this, Math.max(0.0, speed + distance), true);
                } else {
                    // Obstacle is not a moving one. Maintain the speed as is desired by it.
                    // This is used for blocker signs / speed traps
                    return new ObstacleSpeedLimit(this, Math.max(0.0, speed), true);
                }
            }

            // If no wait deceleration is used, just keep on going at the speed following
            // this train ahead, plus the max distance we can move extra this tick.
            if (deceleration <= 0.0 || deceleration == Double.MAX_VALUE) {
                return new ObstacleSpeedLimit(this, Math.max(0.0, speed + distance), true);
            }

            // Based on this formula: a = (v^2 - u^2) / (2s) where v=0
            //                        (-u^2) / (2s) = a
            //                        (u^2) = -2*s*a
            //                        u = sqrt(-2*s*a)
            // Where: a = acceleration, v=final speed (0), u=start speed, s=distance
            // This computes the rough start speed (u)
            double startSpeed = Math.sqrt(2.0 * deceleration * this.distance);

            // The above is for linear time, not discrete time. So it's not completely accurate
            // We divide start speed by deceleration to get the number of 1/20 seconds periods
            //                        t = sqrt(2*s*d) / d where d=deceleration
            // This is ceiled, to get the number of discrete ticks we got to slow down to 0
            int numSlowdownTicks = MathUtil.ceil(startSpeed / deceleration);

            // Reduce the number of ticks of deceleration we got until traveled distance <= threshold
            // It's not pretty, but this while will probably only loop once or twice.
            while ((((numSlowdownTicks+1) * numSlowdownTicks) * 0.5 * deceleration) > this.distance) {
                numSlowdownTicks--;
            }

            if (numSlowdownTicks == 0) {
                // If number of ticks is 0, then there is a less than deceleration rate distance remaining
                // Move this tick at most the full obstacle distance remaining
                startSpeed = this.distance + this.speed;
            } else {
                // Knowing how many ticks of deceleration it takes, we can turn it into an approximate start speed
                startSpeed = numSlowdownTicks * deceleration + this.speed;
            }

            return new ObstacleSpeedLimit(this, Math.max(0.0, startSpeed), false);
        }
    }

    /**
     * Another train obstacle
     */
    public static class TrainObstacle extends Obstacle {
        /** The full distance, which includes the distance to keep between the trains */
        public final double fullDistance;
        /** The first Member encountered of the train */
        public final MinecartMember<?> member;

        public TrainObstacle(double fullDistance, double spaceDistance, double speed, MinecartMember<?> member) {
            super(fullDistance - spaceDistance, speed);
            this.fullDistance = fullDistance;
            this.member = member;
        }

        @Override
        public boolean isObstacleMoving() {
            return true;
        }

        @Override
        protected TrainStatus createStatus(ObstacleSpeedLimit speedLimit) {
            if (speedLimit.isStopped()) {
                return new TrainStatus.WaitingForTrain(member, fullDistance);
            } else {
                return new TrainStatus.FollowingTrain(member, fullDistance, speedLimit.speed);
            }
        }
    }

    /**
     * A mutex zone obstacle
     */
    public static class MutexZoneObstacle extends Obstacle {
        public final MutexZone zone;

        public MutexZoneObstacle(double distance, double speed, MutexZone zone) {
            super(distance, speed);
            this.zone = zone;
        }

        @Override
        protected TrainStatus createStatus(ObstacleSpeedLimit speedLimit) {
            if (speedLimit.isStopped()) {
                return new TrainStatus.WaitingForMutexZone(zone);
            } else {
                return new TrainStatus.ApproachingMutexZone(zone, distance, speed);
            }
        }
    }

    /**
     * A generic rail obstacle, like a blocker or speed trap
     */
    public static class RailObstacle extends Obstacle {
        public final RailPiece rail;

        public RailObstacle(double distance, double speed, RailPiece rail) {
            super(distance, speed);
            this.rail = rail;
        }

        @Override
        protected TrainStatus createStatus(ObstacleSpeedLimit speedLimit) {
            if (speedLimit.isStopped()) {
                return new TrainStatus.WaitingAtRailBlock(this.rail);
            } else {
                return new TrainStatus.ApproachingRailSpeedTrap(this.rail, this.distance, this.speed);
            }
        }
    }

    /**
     * A speed limit a train should stick to, to avoid hitting the obstacle.
     * Contains the speed to limit the train to, and whether the train should
     * adjust instantly to this speed or not.
     */
    public static class ObstacleSpeedLimit {
        /**
         * A constant speed limit that represents 'no' speed limit
         */
        public static final ObstacleSpeedLimit NONE = new ObstacleSpeedLimit(null, Double.MAX_VALUE, false);
        /**
         * The {@link Obstacle} that imposes this speed limit
         */
        public final Obstacle obstacle;
        /**
         * Speed to maintain. {@link Double#MAX_VALUE} if there is no speed limit.
         */
        public final double speed;
        /**
         * Whether the train should adjust it's speed instantly, or allow for a
         * gradual slowdown using wait deceleration.
         */
        public final boolean instant;

        public ObstacleSpeedLimit(Obstacle obstacle, double speed, boolean instant) {
            this.obstacle = obstacle;
            this.speed = speed;
            this.instant = instant;
        }

        /**
         * Creates a suitable train status for this speed limit and its obstacle
         *
         * @return Train Status
         */
        public TrainStatus getStatus() {
            return obstacle.createStatus(this);
        }

        /**
         * Gets whether a speed limit exists at all. If false, then there is
         * no obstacle ahead, or it is far enough away to not pose a threat.
         *
         * @return True if there is a speed limit
         */
        public boolean hasLimit() {
            return this.speed != Double.MAX_VALUE;
        }

        /**
         * Gets whether the train should be stopped completely
         *
         * @return True if the train is stopped completely
         */
        public boolean isStopped() {
            return this.instant && this.speed <= 0.0;
        }

        @Override
        public String toString() {
            if (this.obstacle == null) {
                return "{NONE}";
            }
            return "{speed=" + this.speed + ", instant=" + this.instant + ", obstacle=" +
                    this.obstacle.getClass().getSimpleName() + "}";
        }
    }
}
