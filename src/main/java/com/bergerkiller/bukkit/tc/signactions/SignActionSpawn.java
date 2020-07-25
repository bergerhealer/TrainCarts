package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup.CenterMode;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SignActionSpawn extends SignAction {
    
    @Override
    public boolean match(SignActionEvent info) {
        return SpawnSign.isValid(info);
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF)) {
            return;
        }

        // Find and parse the spawn sign
        SpawnSign sign = TrainCarts.plugin.getSpawnSignManager().create(info);
        if (sign.isActive()) {
            sign.spawn(info);
            sign.resetSpawnTime();
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!SignBuildOptions.create()
                .setPermission(Permission.BUILD_SPAWNER)
                .setName("train spawner")
                .setDescription("spawn trains on the tracks above when powered by redstone")
                .setMinecraftWIKIHelp("Mods/TrainCarts/Signs/Spawner")
                .handle(event.getPlayer()))
        {
            return false;
        }

        // Create a new spawn sign by parsing the event
        SpawnSign sign = TrainCarts.plugin.getSpawnSignManager().create(event);

        // When an interval is specified, check permission for it.
        // No permission? Cancel the building of the sign.
        if (sign.hasInterval() && !Permission.SPAWNER_AUTOMATIC.handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to use automatic signs")) {
            sign.remove();
            return false;
        }

        // Check for all minecart types specified, whether the player has permission for it.
        // No permission? Cancel the building of the sign.
        for (SpawnableMember member : sign.getSpawnableGroup().getMembers()) {
            if (!member.getPermission().handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to create minecarts of type " + member.toString())) {
                sign.remove();
                return false;
            }
        }

        // Success!
        if (sign.hasInterval()) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "This spawner will automatically spawn trains every " + Util.getTimeString(sign.getInterval()) + " while powered");
        }

        // If the sign is active, initiate a spawn right away
        sign.resetSpawnTime();
        return true;
    }

    @Override
    public void destroy(SignActionEvent info) {
        TrainCarts.plugin.getSpawnSignManager().remove(info);
    }
    
    

    // only called from spawn sign
    public static List<Location> spawn(SpawnSign spawnSign, SignActionEvent info) {
        if ((info.isTrainSign() || info.isCartSign()) && info.hasRails()) {
            final double spawnForce = spawnSign.getSpawnForce();
            if (spawnSign.getSpawnableGroup().getMembers().isEmpty()) {
                return null;
            }

            CenterMode centerMode = spawnSign.getSpawnableGroup().getCenterMode();
            List<SpawnableMember> types = spawnSign.getSpawnableGroup().getMembers();

            // Find minecart spawn position information for each possible direction
            int sizeLim = ((types.size() - 1) / 2) + 1;
            List<SpawnPositions> modes = new ArrayList<SpawnPositions>();
            for (BlockFace direction : info.getWatchedDirections()) {
                SpawnPositions mode = getSpawnPositions(info, direction.getOppositeFace(), types);
                if (mode.locs.size() >= sizeLim) {
                    modes.add(mode);
                }
            }
            Collections.sort(modes);
            if (modes.isEmpty()) {
                if (types.size() == 1 && info.getWatchedDirections().length > 0) {
                    // Spawn at the exact position at the sign (single cart)
                    SpawnPositions pos = new SpawnPositions();
                    pos.direction = info.getWatchedDirections()[0];
                    pos.locs = new ArrayList<Location>(1);
                    pos.locs.add(info.getCenterLocation());
                    pos.powered = info.isPowered(pos.direction);
                    modes.add(pos);
                } else {
                    // No room
                    return null;
                }
            }

            // Trim off modes > 2, we don't use those
            while (modes.size() > 2) {
                modes.remove(modes.size() - 1);
            }

            // Figure out what spawn direction we should launch the train into after spawn
            BlockFace launchDirection = BlockFace.SELF;
            for (SpawnPositions mode : modes) {
                if (mode.powered) {
                    launchDirection = mode.direction;
                }
            }

            // If the sign is powered from a non-direction and multiple ways are possible, figure a direction out
            // If none can be figured out based on centering modes, resolve to spawning in the center without launching
            if (launchDirection == BlockFace.SELF) {
                if (modes.size() >= 2) {
                    // Centering is possible; more than one direction can be spawned
                    if (info.isRailsVertical()) {
                        if (centerMode == CenterMode.LEFT) {
                            launchDirection = BlockFace.DOWN;
                        } else if (centerMode == CenterMode.RIGHT) {
                            launchDirection = BlockFace.UP;
                        } else {
                            centerMode = CenterMode.MIDDLE;
                            launchDirection = BlockFace.SELF;
                        }
                    } else {
                        if (centerMode == CenterMode.LEFT) {
                            launchDirection = FaceUtil.rotate(info.getFacing(), 2);
                        } else if (centerMode == CenterMode.RIGHT) {
                            launchDirection = FaceUtil.rotate(info.getFacing(), -2);
                        } else {
                            centerMode = CenterMode.MIDDLE;
                            launchDirection = BlockFace.SELF;
                        }

                        // This is actually dead code right now.
                        /*
                        if (types.centerMode != CenterMode.MIDDLE) {
                            int minAngle = 1000;
                            SpawnPositions selectedMode = modes.get(0);
                            for (SpawnPositions mode : modes) {
                                int angle = FaceUtil.getFaceYawDifference(mode.direction, launchDirection);
                                if (angle < minAngle) {
                                    minAngle = angle;
                                    selectedMode = mode;
                                }
                            }
                            launchDirection = selectedMode.direction;
                        }
                        */
                    }
                } else {
                    // Centering not possible. Restrict to one mode only.
                    centerMode = CenterMode.NONE;
                    launchDirection = modes.get(0).direction;
                }
            }

            // Find desired spawn locations for the minecarts
            List<Location> spawnLocations = new ArrayList<Location>(types.size());
            if (centerMode == CenterMode.MIDDLE && modes.size() >= 2) {
                // Center-Mode: combine two directions into one long stretch of minecarts
                Iterator<Location> iter0 = modes.get(0).locs.iterator();
                Iterator<Location> iter1 = modes.get(1).locs.iterator();

                // Add and skip middle cart
                spawnLocations.add(iter0.next());
                iter1.next();

                boolean mode = false; // alternates iter0/iter1
                while (spawnLocations.size() < types.size()) {
                    if (mode && iter0.hasNext()) {
                        spawnLocations.add(iter0.next());
                    } else if (iter1.hasNext()) {
                        spawnLocations.add(0, Util.invertRotation(iter1.next()));
                    } else {
                        break; // failure
                    }
                    mode = !mode;
                }
            } else {
                // Spawn direction from center mode. Default to the launch direction.
                SpawnPositions selectedMode = modes.get(0);
                BlockFace spawnDirection = launchDirection;
                if (info.isRailsVertical()) {
                    // Up/down of the sign
                    if (centerMode == CenterMode.LEFT) {
                        spawnDirection = BlockFace.DOWN;
                    } else if (centerMode == CenterMode.RIGHT) {
                        spawnDirection = BlockFace.UP;
                    }

                    for (SpawnPositions mode : modes) {
                        if (mode.locs.size() < types.size()) {
                            if (mode.direction == launchDirection) {
                                launchDirection = BlockFace.SELF; // invalidate, cant launch there
                            }
                            continue;
                        }

                        if (mode.direction == spawnDirection && mode.locs.size() >= types.size()) {
                            selectedMode = mode;
                        }
                    }
                } else {
                    // Left/right of the sign
                    if (centerMode == CenterMode.LEFT) {
                        spawnDirection = FaceUtil.rotate(info.getFacing(), 2);
                    } else if (centerMode == CenterMode.RIGHT) {
                        spawnDirection = FaceUtil.rotate(info.getFacing(), -2);
                    }

                    // Figure out the best direction to spawn in and take over those locations
                    // This defaults to the longest ([0]), and favors the direction in which we launch
                    int minAngle = 1000;
                    for (SpawnPositions mode : modes) {
                        if (mode.locs.size() < types.size()) {
                            if (mode.direction == launchDirection) {
                                launchDirection = BlockFace.SELF; // invalidate, cant launch there
                            }
                            continue;
                        }

                        int angle = FaceUtil.getFaceYawDifference(mode.direction, spawnDirection);
                        if (angle < minAngle && mode.locs.size() >= types.size()) {
                            minAngle = angle;
                            selectedMode = mode;
                        }
                    }
                }

                spawnLocations.addAll(selectedMode.locs);

                // Invalidated launch direction? Use spawn direction instead.
                if (launchDirection == BlockFace.SELF) {
                    launchDirection = selectedMode.direction;
                }
            }
            if (spawnLocations.size() < types.size()) {
                return null; // failed
            }

            // Prepare chunks
            for (Location loc : spawnLocations) {
                WorldUtil.loadChunks(loc, 2);
            }

            // Verify spawn area is clear of trains before spawning
            for (Location loc : spawnLocations) {
                if (MinecartMemberStore.getAt(loc) != null) {
                    return null; // occupied
                }
            }

            //Spawn
            MinecartGroup group = MinecartGroupStore.spawn(spawnSign.getSpawnableGroup(), spawnLocations);
            if (group != null && spawnForce != 0 && launchDirection != BlockFace.SELF) {
                group.head().getActions().addActionLaunch(launchDirection, 2, spawnForce);
            }

            return spawnLocations;
        }
        return null;
    }

    /**
     * Gets the Minecart spawn positions into a certain direction.
     * The first location is always the startLoc Location.
     * With atCenter is true, the first cart spawned will be positioned at the start location,
     * even if that width clips through other blocks. When false, it will be spawned at an offset away
     * to make sure the cart edge does not clip past startLoc.
     * 
     * @param startLoc position to start spawning from
     * @param atCenter whether the first spawn position is the startLoc (true), or an offset away (false)
     * @param directionFace of spawning
     * @param types of spawnable members to spawn
     * @return spawn locations list. Number of locations may be less than the number of types.
     */
    public static List<Location> getSpawnPositions(Location startLoc, boolean atCenter, BlockFace directionFace, List<SpawnableMember> types) {
        return getSpawnPositions(startLoc, atCenter, FaceUtil.faceToVector(directionFace), types);
    }

    /**
     * Gets the Minecart spawn positions into a certain direction.
     * The first location is always the startLoc Location.
     * With atCenter is true, the first cart spawned will be positioned at the start location,
     * even if that width clips through other blocks. When false, it will be spawned at an offset away
     * to make sure the cart edge does not clip past startLoc.
     * 
     * @param startLoc position to start spawning from
     * @param atCenter whether the first spawn position is the startLoc (true), or an offset away (false)
     * @param direction of spawning
     * @param types of spawnable members to spawn
     * @return spawn locations list. Number of locations may be less than the number of types.
     */
    public static List<Location> getSpawnPositions(Location startLoc, boolean atCenter, Vector direction, List<SpawnableMember> types) {
        List<Location> result = new ArrayList<Location>(types.size());
        if (atCenter && types.size() == 1) {
            // Single-minecart spawning logic
            // Require there to be one extra free rail in the direction we are spawning
            if (MinecartMemberStore.getAt(startLoc) == null) {
                TrackWalkingPoint walker = new TrackWalkingPoint(startLoc, direction);
                Location firstPos = walker.state.positionLocation();
                walker.skipFirst();
                if (walker.moveFull()) {
                    result.add(firstPos);
                }
            }
        } else {
            // Multiple-minecart spawning logic
            TrackWalkingPoint walker = new TrackWalkingPoint(startLoc, direction);
            walker.skipFirst();
            for (int i = 0; i < types.size(); i++) {
                SpawnableMember type = types.get(i);
                if (atCenter && i == 0) {
                    if (!walker.move(0.0)) {
                        break;
                    }
                } else {
                    if (!walker.move(0.5 * type.getLength() - (i == 0 ? 0.5 : 0.0))) {
                        break;
                    }
                }
                result.add(walker.state.positionLocation());
                if ((i == types.size() - 1) || !walker.move(0.5 * type.getLength() + TCConfig.cartDistanceGap)) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Gets the Minecart spawn positions into a certain direction.
     * The first location is always the middle on top of the current rail of the sign.
     * 
     * @param info sign event information
     * @param directionFace of spawning
     * @param types of spawnable members to spawn
     * @return SpawnPositions with locs limited to the amount that could be spawned
     */
    private static SpawnPositions getSpawnPositions(SignActionEvent info, BlockFace directionFace, List<SpawnableMember> types) {
        SpawnPositions result = new SpawnPositions();
        result.direction = directionFace;
        result.locs = getSpawnPositions(info.getCenterLocation(), true, directionFace, types);
        result.powered = info.isPowered(directionFace);
        return result;
    }

    private static class SpawnPositions implements Comparable<SpawnPositions> {
        public BlockFace direction;
        public List<Location> locs;
        public boolean powered;

        @Override
        public int compareTo(SpawnPositions o) {
            return o.locs.size() - locs.size();
        }
    }

}
