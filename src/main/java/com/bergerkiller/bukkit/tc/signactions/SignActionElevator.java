package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.utils.BlockTimeoutMap;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class SignActionElevator extends TrainCartsSignAction {
    public static final SignActionElevator INSTANCE = new SignActionElevator();
    public final BlockTimeoutMap ignoreTimes = new BlockTimeoutMap();

    public SignActionElevator() {
        super("elevator");
    }

    public ElevatorRail findNextElevator(RailPiece from, BlockFace direction, int elevatorCount) {
        while ((from = Util.findNextRailPiece(from.block(), direction)) != null) {
            for (RailLookup.TrackedSign sign : from.signs()) {
                if (sign.getAction() != this) {
                    continue;
                }

                // Skip a number of elevator-capable rail blocks
                if (--elevatorCount > 0) {
                    break;
                }

                // Found our elevator
                return new ElevatorRail(from, sign);
            }
        }
        return null;
    }

    private static double getTrackDistance(RailState state) {
        TrackWalkingPoint p = new TrackWalkingPoint(state);
        p.setLoopFilter(true);
        p.skipFirst();
        p.move(16.0);
        return p.movedTotal;
    }

    @Override
    public void execute(SignActionEvent info) {
        if (info.getMode() == SignActionMode.NONE || !info.hasRailedMember() || !info.isPowered()) {
            return;
        }
        if (!info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_CHANGE)) {
            return;
        }

        // Is it allowed?
        if (ignoreTimes.isMarked(info.getRails(), 1000)) {
            return;
        }

        // Where to go?
        boolean forced = false;
        BlockFace mode = BlockFace.UP;
        if (info.isLine(2, "down")) {
            mode = BlockFace.DOWN;
            forced = true;
        } else if (info.isLine(2, "up")) {
            forced = true;
        }

        // Possible amounts to skip?
        int elevatorCount = ParseUtil.parseInt(info.getLine(2), 1);

        // Look up the rail above or below this one that has an elevator sign on it
        // If we don't find it in the initial direction and none was set on the sign, try
        // in the opposite direction as well
        ElevatorRail nextElevator = findNextElevator(info.getRailPiece(), mode, elevatorCount);
        if (!forced && nextElevator == null) {
            nextElevator = findNextElevator(info.getRailPiece(), mode.getOppositeFace(), elevatorCount);
        }
        if (nextElevator == null) {
            return;
        }

        ignoreTimes.mark(nextElevator.rail.block());

        // Of the rail the next elevator sign is on, find out how to teleport the train to it
        RailState spawnState = nextElevator.findSpawnState(info);

        // Teleport train
        info.getGroup().teleportAndGo(spawnState.railBlock(), spawnState.motionVector());
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_ENTER)
                .setName("train elevator")
                .setDescription("teleport trains vertically")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Elevator")
                .handle(event);
    }

    /**
     * A rail block with an elevator sign on it
     */
    public static class ElevatorRail {
        public final RailPiece rail;
        public final RailLookup.TrackedSign sign;

        public ElevatorRail(RailPiece rail, RailLookup.TrackedSign sign) {
            this.rail = rail;
            this.sign = sign;
        }

        public RailState findSpawnState(SignActionEvent info) {
            // Of the rail the next elevator sign is on, figure out the motion vector over it
            // This is done by evaluating the Rail-logic path when spawning on top of it
            RailState spawnState = RailState.getSpawnState(this.rail);

            // Was a direction explicitly stated on the last line of THIS sign? If so, honor that
            // We ignore configuration of the sign elevated to, to allow for more options
            Direction launchDirection = Direction.parse(info.getLine(3));
            if (launchDirection != Direction.NONE) {
                if (spawnState.position().motDot(launchDirection.getDirection(info.getFacing(), info.getCartEnterFace())) < 0.0) {
                    spawnState.position().invertMotion();
                }
                return spawnState;
            }

            // If no direction set, first try to see if the sign face points exactly into one of the directions
            // This mostly only works for vanilla rails
            {
                Vector signForward = FaceUtil.faceToVector(this.sign.getFacing());
                double dot = signForward.dot(spawnState.motionVector());
                if (Math.abs(dot) > MathUtil.HALFROOTOFTWO) {
                    if (dot < 0.0) {
                        spawnState.position().invertMotion();
                    }
                    return spawnState;
                }
            }

            // If facing away from the track, teleport to one with the longest track following the rail block
            RailState spawnStateReverse = spawnState.cloneAndInvertMotion();
            if (getTrackDistance(spawnStateReverse) > getTrackDistance(spawnState)) {
                return spawnStateReverse;
            } else {
                return spawnState;
            }
        }
    }
}
