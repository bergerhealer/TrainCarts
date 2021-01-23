package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
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
                .setTraincartsWIKIHelp("TrainCarts/Signs/Spawner")
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
    public static SpawnableGroup.SpawnLocationList spawn(SpawnSign spawnSign, SignActionEvent info) {
        if ((info.isTrainSign() || info.isCartSign()) && info.hasRails()) {
            if (spawnSign.getSpawnableGroup().getMembers().isEmpty()) {
                return null;
            }

            // Find the movement direction vector on the rails
            // This, and the inverted vector, are the two directions in which can be spawned
            Vector railDirection;
            {
                RailState state = RailState.getSpawnState(info.getRailPiece());
                railDirection = state.motionVector();
            }

            // Figure out a preferred direction to spawn into, and whether to allow centering or not
            // This is defined by:
            // - Watched directions ([train:right]), which disables centering
            // - Which block face of the sign is powered, which disables centering
            // - Facing of the sign if no direction is set, which enables centering
            boolean isBothDirections;
            boolean useCentering;
            Vector spawnDirection;
            {
                boolean spawnA = info.isWatchedDirection(railDirection.clone().multiply(-1.0));
                boolean spawnB = info.isWatchedDirection(railDirection);
                if (isBothDirections = (spawnA && spawnB)) {
                    // Decide using redstone power if both directions are watched
                    BlockFace face = Util.vecToFace(railDirection, false);
                    spawnA = info.isPowered(face);
                    spawnB = info.isPowered(face.getOppositeFace());
                }

                if (spawnA && !spawnB) {
                    // Definitively into spawn direction A
                    spawnDirection = railDirection;
                    useCentering = false;
                } else if (!spawnA && spawnB) {
                    // Definitively into spawn direction B
                    spawnDirection = railDirection.clone().multiply(-1.0);
                    useCentering = false;
                } else {
                    // No particular direction is decided
                    // Center the train and spawn relative right of the sign
                    if (FaceUtil.isVertical(Util.vecToFace(railDirection, false))) {
                        // Vertical rails, launch downwards
                        if (railDirection.getY() < 0.0) {
                            spawnDirection = railDirection;
                        } else {
                            spawnDirection = railDirection.clone().multiply(-1.0);
                        }
                    } else {
                        // Horizontal rails, launch most relative right of the sign facing
                        Vector facingDir = FaceUtil.faceToVector(FaceUtil.rotate(info.getFacing(), -2));
                        if (railDirection.dot(facingDir) >= 0.0) {
                            spawnDirection = railDirection;
                        } else {
                            spawnDirection = railDirection.clone().multiply(-1.0);
                        }
                    }
                    useCentering = true;
                }
            }

            // If a center mode is defined in the declared spawned train, then adjust the
            // centering rule accordingly.
            SpawnableGroup spawnable = spawnSign.getSpawnableGroup();
            if (spawnable.getCenterMode() == CenterMode.MIDDLE) {
                useCentering = true;
            } else if (spawnable.getCenterMode() == CenterMode.LEFT || spawnable.getCenterMode() == CenterMode.RIGHT) {
                useCentering = false;
            }

            // If CenterMode is LEFT, then we use the REVERSE spawn mode instead of DEFAULT
            // This places the head close to the sign, rather than the tail
            SpawnableGroup.SpawnMode directionalSpawnMode = SpawnableGroup.SpawnMode.DEFAULT;
            if (spawnable.getCenterMode() == CenterMode.LEFT) {
                directionalSpawnMode = SpawnableGroup.SpawnMode.REVERSE;
            }

            // Attempt spawning the train in priority of operations
            SpawnableGroup.SpawnLocationList spawnLocations = null;
            if (useCentering) {
                // First try spawning it centered, facing in the suggested spawn direction
                spawnLocations = spawnable.findSpawnLocations(info.getRailPiece(), spawnDirection, SpawnableGroup.SpawnMode.CENTER);

                // If this hits a dead-end, in particular with single-cart spawns, try the opposite direction
                if (spawnLocations != null && !spawnLocations.can_move) {
                    Vector opposite = spawnDirection.clone().multiply(-1.0);
                    SpawnableGroup.SpawnLocationList spawnOpposite = spawnable.findSpawnLocations(
                            info.getRailPiece(), opposite, SpawnableGroup.SpawnMode.CENTER);

                    if (spawnOpposite != null && spawnOpposite.can_move) {
                        spawnDirection = opposite;
                        spawnLocations = spawnOpposite;
                    }
                }
            }

            // First try the suggested direction
            if (spawnLocations == null) {
                spawnLocations = spawnable.findSpawnLocations(info.getRailPiece(), spawnDirection, directionalSpawnMode);
            }

            // Try opposite direction if not possible
            // If movement into this direction is not possible, and both directions
            // can be spawned (watched directions), also try other direction.
            // If that direction can be moved into, then use that one instead.
            if (spawnLocations == null || (!spawnLocations.can_move && isBothDirections)) {
                Vector opposite = spawnDirection.clone().multiply(-1.0);
                SpawnableGroup.SpawnLocationList spawnOpposite = spawnable.findSpawnLocations(
                        info.getRailPiece(), opposite, directionalSpawnMode);

                if (spawnOpposite != null && (spawnLocations == null || spawnOpposite.can_move)) {
                    spawnDirection = opposite;
                    spawnLocations = spawnOpposite;
                }
            }

            // If still not possible, try centered if we had not tried yet, just in case
            if (spawnLocations == null && !useCentering) {
                spawnLocations = spawnable.findSpawnLocations(info.getRailPiece(), spawnDirection, SpawnableGroup.SpawnMode.CENTER);
            }

            // If still no spawn locations could be found, fail
            if (spawnLocations == null) {
                return null; // Failed
            }

            // Load the chunks first
            spawnLocations.loadChunks();

            // Check that the area isn't occupied by another train
            if (spawnLocations.isOccupied()) {
                return null; // Occupied
            }

            // Spawn and launch
            MinecartGroup group = spawnable.spawn(spawnLocations);
            double spawnForce = spawnSign.getSpawnForce();
            if (group != null && spawnForce != 0.0) {
                Vector headDirection = spawnLocations.locations.get(spawnLocations.locations.size()-1).forward;
                BlockFace launchDirection = Util.vecToFace(headDirection, false);

                // Negative spawn force launches in reverse
                if (spawnForce < 0.0) {
                    launchDirection = launchDirection.getOppositeFace();
                    spawnForce = -spawnForce;
                }

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
     * to make sure the cart edge does not clip past startLoc.<br>
     * <br>
     * <b>Deprecated: there are now methods for this in {@link SpawnableGroup}</b>
     * 
     * @param startLoc position to start spawning from
     * @param atCenter whether the first spawn position is the startLoc (true), or an offset away (false)
     * @param directionFace of spawning
     * @param types of spawnable members to spawn
     * @return spawn locations list. Number of locations may be less than the number of types.
     */
    @Deprecated
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
     * @deprecated There are now methods for this in {@link SpawnableGroup}
     */
    @Deprecated
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
}
