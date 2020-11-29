package com.bergerkiller.bukkit.tc.controller.components;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.cache.RailMemberCache;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Controls the maximum speed of the train to maintain distance
 * with trains or mutex zones up ahead. Uses the configured acceleration
 * to slow down the train, or launch it again to the original speed when
 * the blockage up ahead clears.
 */
public class SpeedAheadWaiter {
    private final MinecartGroup group;
    private double safeDistance = 0.0;
    private double waitDistanceLastSpeedLimit = Double.MAX_VALUE;
    private int waitRemainingTicks = Integer.MAX_VALUE;

    public SpeedAheadWaiter(MinecartGroup group) {
        this.group = group;
    }

    /**
     * Gets the speed limit imposed by this waiter, for the current sub-physics tick.
     * Returns MAX_VALUE if no limit is imposed.
     * Note that this speed limit does not have the update speed factor factored in yet!
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
     * @param trainSpeed Current speed of the train
     * @return speed the train should limit itself to.
     */
    public void update(double trainSpeed) {
        TrainProperties properties = group.getProperties();
        double oldSpeedLimit = (this.waitDistanceLastSpeedLimit == Double.MAX_VALUE) ?
                properties.getSpeedLimit() : this.waitDistanceLastSpeedLimit;
        DesiredSpeed newDesiredSpeed = getDesiredSpeedLimit(oldSpeedLimit);

        // Every time the speed drops to 0 consistently, reset the wait tick timer to 0
        // This causes it to wait until the remaining ticks reaches the configured delay
        if (oldSpeedLimit <= 0.0 && newDesiredSpeed.speed <= 0.0) {
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

        // If no speed limit was used before, assume properties speed limit
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
                this.waitDistanceLastSpeedLimit += group.getUpdateSpeedFactor() * acceleration;
            }
        } else {
            // Slow down
            double deceleration = properties.getWaitDeceleration();
            if (deceleration <= 0.0 || deceleration >= (-speedDiff)) {
                this.waitDistanceLastSpeedLimit = newDesiredSpeed.speed;
            } else {
                this.waitDistanceLastSpeedLimit -= group.getUpdateSpeedFactor() * deceleration;
            }

            // Make sure that the amount we move in a single tick never exceeds remaining distance
            // This is as if the train 'crashes' into another train or mutex zone
            // When this happens, use the desired speed instantly
            if (newDesiredSpeed.remaining <= this.waitDistanceLastSpeedLimit) {
                this.waitDistanceLastSpeedLimit = newDesiredSpeed.speed;
            }
        }
    }

    /**
     * Calculates the desired speed limit the train should ideally have right now.
     * Based on acceleration/deceleration, the actual speed limit is adjusted to reach
     * this speed.
     * 
     * @param oldSpeedLimit The currently configured speed limit
     * @return desired speed limit
     */
    private DesiredSpeed getDesiredSpeedLimit(double oldSpeedLimit) {
        TrainProperties properties = group.getProperties();

        // At the current speed, how much extra distance does it take to slow the train down to 0?
        // Look for obstacles this much extra distance ahead, to allow for stopping in time.
        // As this train slows down due to a reduced speed limit, we have to check less and less
        // distance up a head, since it takes less long to slow to a complete stop.
        this.safeDistance = properties.getWaitDistance();
        boolean checkTrains = (this.safeDistance > 0.0);
        if (properties.getWaitDeceleration() > 0.0) {
            int numSlowdownSteps = MathUtil.floor(oldSpeedLimit / properties.getWaitDeceleration());
            for (int n = 0; n < numSlowdownSteps; n++) {
                oldSpeedLimit -= properties.getWaitDeceleration();
                this.safeDistance += oldSpeedLimit;
            }
        }

        // Limit this so people can't crash the server. Dunno what's best, decided on 2000.
        this.safeDistance = Math.min(2000.0, this.safeDistance);

        // Check for obstacles, with a +1 block leeway
        Obstacle obstacle = this.findObstacleAhead(this.safeDistance + 1.0, checkTrains);

        // No obstacle means full steam ahead!
        if (obstacle == null) {
            return new DesiredSpeed(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        // If obstacle is closer than the safe distance, we must slow down at the maximum rate
        // This is basically an emergency stop
        double remainingActual = (obstacle.distance - properties.getWaitDistance());
        double remaining = (obstacle.distance - this.safeDistance);
        if (remaining <= -1e-5) {
            return new DesiredSpeed(0.0, remainingActual);
        }

        return new DesiredSpeed(Math.max(0.0, obstacle.speed + remaining / group.getUpdateSpeedFactor()),
                remainingActual);
    }

    /**
     * Looks up ahead on the track for obstacles. These can be other trains, or mutex
     * signs that disallow movement further.
     * 
     * @param distance Distance in blocks to check ahead of the train
     * @param checkTrains Whether to look for trains or only for mutex signs
     * @return obstacle that was detected, null if there is no obstacle
     */
    public Obstacle findObstacleAhead(double distance, boolean checkTrains) {
        // Not sure if fixed, but if this train is empty, return MAX_VALUE
        if (group.isEmpty()) {
            return null;
        }

        // If no wait distance is set and no mutex zones are anywhere close, skip these expensive calculations
        if (distance <= 0.0) {
            UUID world = group.head().getEntity().getWorld().getUID();
            IntVector3 block = group.head().getBlockPos();
            if (!MutexZoneCache.isMutexZoneNearby(world, block, 8)) {
                return null;
            }
        }

        // Take into account that the head minecart has a length also, so we count distance from the edge (half length)
        // TODO: This does not take into account wheel offset!!!
        double selfCartOffset = (0.5 * group.head().getEntity().getWidth());

        // Speed of this train, limited by the maximum speed property
        double selfSpeed = group.head().getEntity().vel.length();
        selfSpeed = Math.min(selfSpeed, group.getProperties().getSpeedLimit());

        // The actual minimum distance allowed from the walking point position to any minecarts discovered
        // This takes into account that the start position is halfway the length of the Minecart
        // When distance is not greater than 0, we don't check for other trains at all.
        double waitDistance = distance;
        if (checkTrains) {
            waitDistance += selfCartOffset;
        }

        // Two blocks are used to slow down the train, to make it match up to speed with the train up ahead
        // Check for any mutex zones ~2 blocks ahead, and stop before we enter them
        // If a wait distance is set, also check for trains there
        final double mutexDistance = 2.0 + selfCartOffset;
        final double checkDistance = Math.max(mutexDistance, waitDistance);

        UUID worldUUID = group.getWorld().getUID();
        TrackWalkingPoint iter = new TrackWalkingPoint(group.head().discoverRail());
        while (iter.movedTotal <= checkDistance && iter.moveFull()) {

            // Check for mutex zones the next block. If one is found that is occupied, stop right away
            if (iter.movedTotal <= mutexDistance) {
                MutexZone zone = MutexZoneCache.find(worldUUID, new IntVector3(iter.state.railBlock()));
                if (zone != null && !zone.slot.tryEnter(group)) {
                    return new Obstacle(iter.movedTotal, 0.0);
                }

                if (!checkTrains) {
                    break;
                }
            }

            // Only check for trains on the rails when a wait distance is set
            if (!checkTrains) {
                continue;
            }

            // Check all other minecarts on the same rails to see if they are too close
            Location state_position = null;
            Location member_position = null;
            double minSpeedAhead = Double.MAX_VALUE;
            double minDistanceAhead = 0.0;
            for (MinecartMember<?> member : RailMemberCache.findAll(iter.state.railBlock())) {
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
                if ((iter.movedTotal + distanceToMember) > waitDistance) {
                    continue;
                }

                // Movement speed of the minecart, taking maximum speed into account
                Vector member_velocity = member.getEntity().getVelocity();
                double speedAhead = MathUtil.clamp(member_velocity.length(), member.getEntity().getMaxSpeed());

                // If moving towards me, stop right away! When barely moving, ignore this check.
                if (speedAhead > 1e-6 && iter.state.position().motDot(member_velocity) < 0.0) {
                    return new Obstacle(iter.movedTotal + distanceToMember, 0.0);
                }

                // Too close, match the speed of the Minecart ahead. For the overshoot, slow ourselves down.
                if (speedAhead < 0.0) {
                    speedAhead = 0.0;
                }
                if (speedAhead < minSpeedAhead) {
                    minSpeedAhead = speedAhead;
                    minDistanceAhead = iter.movedTotal + distanceToMember;
                }
            }
            if (minSpeedAhead != Double.MAX_VALUE) {
                return new Obstacle(minDistanceAhead, minSpeedAhead);
            }
        }

        return null;
    }

    /**
     * A detected obstacle, with the distance away from the train the obstacle exists,
     * and the speed it is moving forwards.
     */
    public static class Obstacle {
        public final double distance;
        public final double speed;

        public Obstacle(double distance, double speed) {
            this.distance = distance;
            this.speed = speed;
        }
    }

    private static class DesiredSpeed {
        public final double speed;
        public final double remaining;

        public DesiredSpeed(double speed, double remaining) {
            this.speed = speed;
            this.remaining = remaining;
        }
    }
}
