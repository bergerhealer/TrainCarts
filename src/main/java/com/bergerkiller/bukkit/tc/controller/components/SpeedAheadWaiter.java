package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatusProvider;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlot;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Controls the maximum speed of the train to maintain distance
 * with trains or mutex zones up ahead. Uses the configured acceleration
 * to slow down the train, or launch it again to the original speed when
 * the blockage up ahead clears.
 */
public class SpeedAheadWaiter implements TrainStatusProvider {
    private final MinecartGroup group;
    private double waitDistanceLastSpeedLimit = Double.MAX_VALUE;
    private double waitDistanceLastTrainSpeed = Double.MAX_VALUE;
    private int waitRemainingTicks = Integer.MAX_VALUE;
    private Obstacle lastObstacle = null;
    private DesiredSpeed lastDesiredSpeed = new DesiredSpeed(Double.MAX_VALUE, true);
    private List<MutexZone> enteredMutexZones = Collections.emptyList();

    public SpeedAheadWaiter(MinecartGroup group) {
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

        // Calculate the amount of distance ahead of the train we have to look for other
        // trains or mutex zones or any other type of obstacle. This is calculated based
        // on the maximum projected movement the train will have this tick. This is the
        // full train speed, limited by the applied speed limit right now
        double searchAheadDistance = 0.0;
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

        DesiredSpeed newDesiredSpeed = getDesiredSpeedLimit(searchAheadDistance,
                properties.getWaitDeceleration(), checkTrains, true, properties.getWaitDistance());

        // Every time the speed drops to 0 consistently, reset the wait tick timer to 0
        // This causes it to wait until the remaining ticks reaches the configured delay
        if (this.waitDistanceLastSpeedLimit <= 0.0 && newDesiredSpeed.speed <= 0.0) {
            this.waitRemainingTicks = 0;
            this.waitDistanceLastSpeedLimit = 0.0;
            return;
        }

        // Until the configured delay is reached, keep the speed on 0
        if (this.waitRemainingTicks != Integer.MAX_VALUE) {
            double delay = properties.getWaitDelay();
            if (delay <= 0.0) {
                this.waitRemainingTicks = Integer.MAX_VALUE; // No delay
            } else {
                if (group.isLastUpdateStep() && ++this.waitRemainingTicks >= MathUtil.ceil(delay*20.0)) {
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
                    this.waitDistanceLastSpeedLimit += group.getUpdateSpeedFactor() * acceleration;
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
        if (this.lastObstacle == null && this.enteredMutexZones.isEmpty()) {
            return Collections.emptyList();
        }

        List<TrainStatus> statuses = new ArrayList<>();

        for (MutexZone zone : this.enteredMutexZones) {
            statuses.add(new TrainStatus.EnteredMutexZone(zone));
        }

        if (this.lastObstacle != null) {
            if (this.lastDesiredSpeed.instant && this.lastDesiredSpeed.speed == 0.0) {
                // Immobile object, train is waiting and not moving at all
                if (this.lastObstacle instanceof TrainObstacle) {
                    TrainObstacle train = (TrainObstacle) this.lastObstacle;
                    statuses.add(new TrainStatus.WaitingForTrain(
                            train.member, train.fullDistance));
                } else if (this.lastObstacle instanceof MutexZoneObstacle) {
                    statuses.add(new TrainStatus.WaitingForMutexZone(
                            ((MutexZoneObstacle) this.lastObstacle).zone));
                }
            } else {
                // Mobile object, train is (slowly) following or approaching that object
                if (this.lastObstacle instanceof TrainObstacle) {
                    TrainObstacle train = (TrainObstacle) this.lastObstacle;
                    statuses.add(new TrainStatus.FollowingTrain(train.member, train.fullDistance,
                            this.lastDesiredSpeed.speed));
                } else if (this.lastObstacle instanceof MutexZoneObstacle) {
                    statuses.add(new TrainStatus.ApproachingMutexZone(
                            ((MutexZoneObstacle) this.lastObstacle).zone,
                            this.lastObstacle.distance, this.lastDesiredSpeed.speed));
                }
            }
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
    private DesiredSpeed getDesiredSpeedLimit(double searchAheadDistance, double deceleration,
            boolean checkTrains, boolean checkRailObstacles, double trainDistance
    ) {
        // No obstacle means full steam ahead!
        DesiredSpeed minDesiredSpeed = new DesiredSpeed(Double.MAX_VALUE, true);

        // Check all obstacles ahead and find the one with the minimal speed limit to impose
        Obstacle newLimitingObstacle = null;
        for (Obstacle obstacle : this.findObstaclesAhead(Math.min(2000.0, searchAheadDistance + 1.0),
                                                         checkTrains, checkRailObstacles, trainDistance))
        {
            // If obstacle is closer than it is allowed to ever be, emergency stop
            // This also ignores the vehicle's actual speed, because we're too close to
            // it already, so we need to stop and wait for the distance to go above
            // the safe wait distance threshold again.
            if (obstacle.distance <= 0.0) {
                double speed = Math.max(0.0, obstacle.speed + obstacle.distance);
                if (speed < minDesiredSpeed.speed) {
                    minDesiredSpeed = new DesiredSpeed(speed, true);
                    newLimitingObstacle = obstacle;
                }
                continue;
            }

            // If no wait deceleration is used, just keep on going at the speed following
            // this train ahead, plus the max distance we can move extra this tick.
            if (deceleration <= 0.0) {
                double speed = Math.max(0.0, obstacle.speed + obstacle.distance / group.getUpdateSpeedFactor());
                if (speed < minDesiredSpeed.speed) {
                    minDesiredSpeed = new DesiredSpeed(speed, true);
                    newLimitingObstacle = obstacle;
                }
                continue;
            }

            // Based on this formula: a = (v^2 - u^2) / (2s) where v=0
            //                        (-u^2) / (2s) = a
            //                        (u^2) = -2*s*a
            //                        u = sqrt(-2*s*a)
            // Where: a = acceleration, v=final speed (0), u=start speed, s=distance
            // This computes the rough start speed (u)
            double startSpeed = Math.sqrt(2.0 * deceleration * obstacle.distance);

            // The above is for linear time, not discrete time. So it's not completely accurate
            // We divide start speed by deceleration to get the number of 1/20 seconds periods
            //                        t = sqrt(2*s*d) / d where d=deceleration
            // This is ceiled, to get the number of discrete ticks we got to slow down to 0
            int numSlowdownTicks = MathUtil.ceil(startSpeed / deceleration);

            // Reduce the number of ticks of deceleration we got until traveled distance <= threshold
            // It's not pretty, but this while will probably only loop once or twice.
            while ((((numSlowdownTicks+1) * numSlowdownTicks) * 0.5 * deceleration) > obstacle.distance) {
                numSlowdownTicks--;
            }

            // Knowing how many ticks of deceleration it takes, we can turn it into an approximate start speed
            startSpeed = numSlowdownTicks * deceleration + obstacle.speed;

            if (startSpeed < minDesiredSpeed.speed) {
                minDesiredSpeed = new DesiredSpeed(Math.max(0.0, startSpeed), false);
                newLimitingObstacle = obstacle;
            }
        }
        this.lastObstacle = newLimitingObstacle;
        this.lastDesiredSpeed = minDesiredSpeed;

        return minDesiredSpeed;
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
        // Not sure if fixed, but skip if this train is empty
        if (group.isEmpty()) {
            return Collections.emptyList();
        }

        // If no wait distance is set and no mutex zones are anywhere close, skip these expensive calculations
        if (distance <= 0.0 && trainDistance <= 0.0) {
            OfflineWorld world = OfflineWorld.of(group.getWorld());
            IntVector3 block = group.head().getEntity().loc.block();
            if (!checkRailObstacles || !MutexZoneCache.isMutexZoneNearby(world, block, 8)) {
                return Collections.emptyList();
            }
        }

        // Take into account that the head minecart has a length also, so we count distance from the edge (half length)
        // TODO: This does not take into account wheel offset!!!
        double selfCartOffset = (0.5 * group.head().getEntity().getWidth());

        // The actual minimum distance allowed from the walking point position to any minecarts discovered
        // This takes into account that the start position is halfway the length of the Minecart
        // When distance is not greater than 0, we don't check for other trains at all.
        double waitDistance = distance + trainDistance;

        // Two blocks are used to slow down the train, to make it match up to speed with the train up ahead
        // Check for any mutex zones ~2 blocks ahead, and stop before we enter them
        // If a wait distance is set, also check for trains there
        final double mutexHardDistance = 0.0;
        final double mutexSoftDistance = 2.0 + distance;
        final double checkDistance = selfCartOffset + Math.max(mutexSoftDistance, waitDistance);

        boolean foundMutexBlock = false;
        MutexZone lastSoftMutexZone = null;
        List<MutexZone> enteredMutexZones = Collections.emptyList();
        List<Obstacle> obstacles = new ArrayList<>();
        TrackWalkingPoint iter = new TrackWalkingPoint(group.head().discoverRail());
        while (iter.movedTotal <= checkDistance && iter.moveFull()) {

            // The distance traveled from the physical front of the cart
            // The first iteration will likely have a negative distance
            double distanceFromFront = iter.movedTotal - selfCartOffset;

            // Check for mutex zones the next block. If one is found that is occupied, stop right away
            if (checkRailObstacles && !foundMutexBlock && distanceFromFront < mutexSoftDistance) {
                MutexZone zone = MutexZoneCache.find(iter.state.positionOfflineBlock());
                if (zone != null && !enteredMutexZones.contains(zone)) {
                    MutexZoneSlot.EnterResult result = zone.slot.tryEnter(group, distanceFromFront <= mutexHardDistance);
                    if (result == MutexZoneSlot.EnterResult.OCCUPIED_HARD) {
                        // At this point the train is guaranteed stopped. Don't check for more mutex zones now.
                        // This is a hard stop, so we slow down to speed 0
                        foundMutexBlock = true;
                        obstacles.add(new MutexZoneObstacle(distanceFromFront, 0.0, zone));
                    } else if (result == MutexZoneSlot.EnterResult.OCCUPIED_SOFT && zone != lastSoftMutexZone) {
                        // At this point the train is guaranteed stopped. Don't check for more mutex zones now.
                        // This is a soft stop, so we slow down to a crawl, but still moving
                        lastSoftMutexZone = zone;
                        obstacles.add(new MutexZoneObstacle(distanceFromFront, 0.01, zone));
                    } else if (result == MutexZoneSlot.EnterResult.SUCCESS) {
                        if (enteredMutexZones.isEmpty()) {
                            enteredMutexZones = new ArrayList<>();
                        }
                        enteredMutexZones.add(zone);
                    }
                }
            }

            // Only check for trains on the rails when a wait distance is set
            if (!checkTrains) {
                continue;
            }

            // Check all other minecarts on the same rails to see if they are too close
            Location state_position = null;
            Location member_position = null;
            MinecartMember<?> minMemberAhead = null;
            double minSpeedAhead = Double.MAX_VALUE;
            double minDistanceAhead = 0.0;
            for (MinecartMember<?> member : iter.state.railPiece().members()) {
                if (member.getGroup() == group) {
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

                // Find the distance we can still move from our current position
                if ((distanceFromFront + distanceToMember) > waitDistance) {
                    continue;
                }

                // Movement speed of the minecart, taking maximum speed into account
                Vector member_velocity = member.getEntity().getVelocity();
                double speedAhead = MathUtil.clamp(member_velocity.length(), member.getEntity().getMaxSpeed());

                // If moving towards me, stop right away! When barely moving, ignore this check.
                if (speedAhead > 1e-6 && iter.state.position().motDot(member_velocity) < 0.0) {
                    obstacles.add(new TrainObstacle(distanceFromFront + distanceToMember, trainDistance, 0.0, member));
                    continue;
                }

                // Too close, match the speed of the Minecart ahead. For the overshoot, slow ourselves down.
                if (speedAhead < 0.0) {
                    speedAhead = 0.0;
                }
                if (speedAhead < minSpeedAhead) {
                    minMemberAhead = member;
                    minSpeedAhead = speedAhead;
                    minDistanceAhead = distanceFromFront + distanceToMember;
                }
            }
            if (minSpeedAhead != Double.MAX_VALUE) {
                obstacles.add(new TrainObstacle(minDistanceAhead, trainDistance, minSpeedAhead, minMemberAhead));
            }
        }

        this.enteredMutexZones = enteredMutexZones;
        return obstacles;
    }

    /**
     * A detected obstacle, with the distance away from the train the obstacle exists,
     * and the speed it is moving forwards.
     */
    public static class Obstacle {
        public final double distance;
        public final double speed;

        public Obstacle(double distance, double speed) {
            this.distance = Math.max(0.0, distance); // Avoid pain
            this.speed = speed;
        }
    }

    /**
     * Another train as obstacle
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
    }
    
    public static class DesiredSpeed {
        public final double speed;
        public final boolean instant;

        public DesiredSpeed(double speed, boolean instant) {
            this.speed = speed;
            this.instant = instant;
        }
    }
}
