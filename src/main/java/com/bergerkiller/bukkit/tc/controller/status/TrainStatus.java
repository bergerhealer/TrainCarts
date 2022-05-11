package com.bergerkiller.bukkit.tc.controller.status;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.debug.DebugToolUtil;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

/**
 * A single action or behavior the train is currently doing
 * 
 * @see {@link TrainStatusProvider}
 */
public interface TrainStatus {

    /**
     * Gets a message summary about this status. This is what is sent in chat
     * when requesting an overview.
     *
     * @return Message
     */
    String getMessage();

    /**
     * Train is launching to a new speed
     */
    public static final class Launching implements TrainStatus {
        private final double targetSpeed;
        private final double targetSpeedLimit;
        private final LauncherConfig config;

        public Launching(double targetSpeed, double targetSpeedLimit, LauncherConfig config) {
            this.targetSpeed = targetSpeed;
            this.targetSpeedLimit = targetSpeedLimit;
            this.config = config;
        }

        public double getTargetSpeed() {
            return this.targetSpeed;
        }

        public double getTargetSpeedLimit() {
            return this.targetSpeedLimit;
        }

        public LauncherConfig getConfig() {
            return this.config;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW).append("Launching to a speed of ").append(ChatColor.WHITE);
            if (Double.isNaN(this.targetSpeedLimit) || this.targetSpeed <= this.targetSpeedLimit) {
                str.append(DebugToolUtil.formatNumber(this.targetSpeed)).append("b/t");
            } else {
                str.append(DebugToolUtil.formatNumber(this.targetSpeedLimit)).append("b/t");
                str.append(ChatColor.YELLOW).append(" (").append(ChatColor.WHITE).append('+');
                str.append(DebugToolUtil.formatNumber(this.targetSpeed - this.targetSpeedLimit));
                str.append(ChatColor.YELLOW).append(" energy)");
            }
            str.append(ChatColor.YELLOW);
            if (this.config.hasDuration()) {
                str.append(" for ")
                   .append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.config.getDuration()))
                   .append(ChatColor.YELLOW).append(" ticks");
            } else if (this.config.hasDistance()) {
                str.append(" over a distance of ")
                   .append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.config.getDistance()))
                   .append(ChatColor.YELLOW).append(" blocks");
            } else if (this.config.hasAcceleration()) {
                str.append(" at ")
                   .append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.config.getAcceleration()))
                   .append(ChatColor.YELLOW).append("b/t/t");
            }
            return str.toString();
        }
    }

    /**
     * Train is immobile, waiting for a certain condition to occur
     */
    public static interface Waiting extends TrainStatus {
    }

    /**
     * Waiting forever (at a station sign, etc.)
     */
    public static final class WaitingForever implements Waiting {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Waiting forever for an external trigger";
        }
    }

    /**
     * Waiting for a period of time, on a timer (station usually)
     */
    public static final class WaitingForDuration implements Waiting {
        private final long durationMillis;

        public WaitingForDuration(long durationMillis) {
            this.durationMillis = durationMillis;
        }

        public long getDuration() {
            return this.durationMillis;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.RED).append("Waiting for a time of ").append(ChatColor.WHITE);
            double timeSeconds = (double) this.durationMillis / 1000.0;
            int timeMinutes = (int) timeSeconds / 60;
            if (timeMinutes > 0) {
                timeSeconds -= timeMinutes * 60;
                str.append(timeMinutes).append(" minutes");
                str.append(ChatColor.RED).append(" and ").append(ChatColor.WHITE);
                str.append((int) timeSeconds).append(" seconds");
            } else {
                str.append(timeSeconds).append(" seconds");
            }
            return str.toString();
        }
    }

    /**
     * Waiting for the pathfinding module to finish calculation the new routes
     */
    public static final class WaitingForRouting implements Waiting {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Waiting for path finding router to finish";
        }
    }

    /**
     * Train is not moving, and is therefore waiting, because the speed limit is set to 0
     */
    public static final class WaitingZeroSpeedLimit implements Waiting {
        @Override
        public String getMessage() {
            return ChatColor.YELLOW + "Waiting because the speed limit is set to " + ChatColor.RED + "zero";
        }
    }

    /**
     * Train is waiting for a train up ahead to move away. This is the case when
     * waiting at a waiter sign, or when a train is standing still up ahead and
     * a wait distance is set.
     */
    public static final class WaitingForTrain implements Waiting {
        private final MinecartMember<?> member;
        private final double distance;

        public WaitingForTrain(MinecartMember<?> member, double distance) {
            this.member = member;
            this.distance = distance;
        }

        public MinecartMember<?> getMember() {
            return this.member;
        }

        public double getDistance() {
            return this.distance;
        }

        @Override
        public String getMessage() {
            return ChatColor.YELLOW + "Waiting for train " +
                    ChatColor.RED + this.member.getGroup().getProperties().getTrainName() +
                    ChatColor.YELLOW + " which is " +
                    ChatColor.WHITE + DebugToolUtil.formatNumber(this.distance) +
                    ChatColor.YELLOW + " blocks up ahead";
        }
    }

    /**
     * Train is waiting for a mutex zone that is currently occupied by another train.
     */
    public static final class WaitingForMutexZone implements Waiting {
        private final MutexZone zone;

        public WaitingForMutexZone(MutexZone zone) {
            this.zone = zone;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW).append("Waiting for mutex zone ");
            OfflineBlock pos = zone.signBlock;
            if (!zone.slot.isAnonymous()) {
                str.append(ChatColor.RED).append(zone.slot.getName());
                str.append(ChatColor.YELLOW).append(" at ");
            }
            str.append(ChatColor.RED);
            str.append(pos.getX()).append("/").append(pos.getY()).append("/").append(pos.getZ());

            MinecartGroup group = zone.slot.getCurrentGroup();
            if (group != null) {
                str.append(ChatColor.YELLOW).append(" currently occupied by ");
                str.append(ChatColor.RED).append(group.getProperties().getTrainName());
            }

            return str.toString();
        }
    }

    /**
     * Train is moving and approaching/following another train up ahead
     */
    public static final class FollowingTrain implements TrainStatus {
        private final MinecartMember<?> member;
        private final double distance;
        private final double speed;

        public FollowingTrain(MinecartMember<?> member, double distance, double speed) {
            this.member = member;
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW);
            if (member.getForce() > 1e-4) {
                str.append("Following train ");
            } else {
                str.append("Approaching train ");
            }
            str.append(ChatColor.WHITE).append(this.member.getGroup().getProperties().getTrainName());
            str.append(ChatColor.YELLOW).append(" at a speed of ");
            str.append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.speed)).append("b/t");
            str.append(ChatColor.YELLOW + " which is ");
            str.append(ChatColor.WHITE).append(DebugToolUtil.formatNumber(this.distance));
            str.append(ChatColor.YELLOW).append(" blocks up ahead");
            return str.toString();
        }
    }

    public static final class ApproachingMutexZone implements TrainStatus {
        private final MutexZone zone;
        private final double distance;
        private final double speed;

        public ApproachingMutexZone(MutexZone zone, double distance, double speed) {
            this.zone = zone;
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.YELLOW).append("Approaching mutex zone ");
            OfflineBlock pos = zone.signBlock;
            if (!zone.slot.isAnonymous()) {
                str.append(ChatColor.RED).append(zone.slot.getName());
                str.append(ChatColor.YELLOW).append(" at ");
            }
            str.append(ChatColor.WHITE);
            str.append(pos.getX()).append("/").append(pos.getY()).append("/").append(pos.getZ());

            str.append(ChatColor.YELLOW).append(", ").append(ChatColor.WHITE);
            str.append(DebugToolUtil.formatNumber(this.distance)).append(ChatColor.YELLOW).append(" blocks ahead");

            MinecartGroup group = zone.slot.getCurrentGroup();
            if (group != null) {
                str.append(", currently occupied by ");
                str.append(ChatColor.RED).append(group.getProperties().getTrainName());
                str.append(ChatColor.YELLOW);
            }

            str.append(", slowed down to a speed of ").append(ChatColor.WHITE);
            str.append(DebugToolUtil.formatNumber(this.speed)).append("b/t");

            return str.toString();
        }
    }

    public static final class KeepingChunksLoaded implements TrainStatus {
        @Override
        public String getMessage() {
            return ChatColor.YELLOW + "Is keeping chunks " + ChatColor.GREEN + "loaded";
        }
    }

    public static final class NotMoving implements TrainStatus {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Has zero velocity: is not moving";
        }
    }

    public static final class Moving implements TrainStatus {
        private final double speed;

        public Moving(double speed) {
            this.speed = speed;
        }

        @Override
        public String getMessage() {
            return ChatColor.GREEN + "Is moving at " + ChatColor.WHITE +
                    DebugToolUtil.formatNumber(this.speed) + "b/t";
        }
    }

    public static final class Derailed implements TrainStatus {
        @Override
        public String getMessage() {
            return ChatColor.RED + "Is (partially) derailed";
        }
    }

    public static final class EnteredMutexZone implements TrainStatus {
        private final MutexZone zone;

        public EnteredMutexZone(MutexZone zone) {
            this.zone = zone;
        }

        @Override
        public String getMessage() {
            StringBuilder str = new StringBuilder();
            str.append(ChatColor.GREEN).append("Entered mutex zone ");
            OfflineBlock pos = zone.signBlock;
            if (!zone.slot.isAnonymous()) {
                str.append(ChatColor.WHITE).append(zone.slot.getName());
                str.append(ChatColor.GREEN).append(" at ");
            }
            str.append(ChatColor.WHITE);
            str.append(pos.getX()).append("/").append(pos.getY()).append("/").append(pos.getZ());

            return str.toString();
        }
    }
}
