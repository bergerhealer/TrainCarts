package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.type.MinecartType;
import com.bergerkiller.bukkit.tc.events.GroupCreateEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import com.bergerkiller.bukkit.tc.utils.TrackWalkIterator;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class SignActionSpawn extends SignAction {
    private static boolean hasChanges = false;
    private static BlockMap<SpawnSign> spawnSigns = new BlockMap<>();

    public static void remove(Block signBlock) {
        SpawnSign sign = spawnSigns.remove(signBlock);
        if (sign != null) {
            sign.remove(signBlock);
        }
        hasChanges = true;
    }

    public static boolean isValid(SignActionEvent event) {
        return event != null && event.getMode() != SignActionMode.NONE && event.isType("spawn");
    }

    private static String[] getArgs(SignActionEvent event) {
        final String line = event.getLine(1).toLowerCase(Locale.ENGLISH);
        final int idx = line.indexOf(' ');
        if (idx == -1) {
            return StringUtil.EMPTY_ARRAY;
        }
        return line.substring(idx + 1).split(" ");
    }

    public static double getSpawnForce(SignActionEvent event) {
        String[] bits = getArgs(event);
        if (bits.length >= 2) {
            // Choose
            if (!bits[0].contains(":")) {
                return ParseUtil.parseDouble(bits[0], 0.0);
            } else {
                return ParseUtil.parseDouble(bits[1], 0.0);
            }
        } else if (bits.length >= 1 && !bits[0].contains(":")) {
            return ParseUtil.parseDouble(bits[0], 0.0);
        }
        return 0.0;
    }

    public static long getSpawnTime(SignActionEvent event) {
        String[] bits = getArgs(event);
        if (bits.length >= 2) {
            // Choose
            if (bits[1].contains(":")) {
                return ParseUtil.parseTime(bits[1]);
            } else {
                return ParseUtil.parseTime(bits[0]);
            }
        } else if (bits.length >= 1 && bits[0].contains(":")) {
            return ParseUtil.parseTime(bits[0]);
        }
        return 0;

    }

    public static boolean hasCartPerms(SignChangeActionEvent event) {
        MinecartType type;
        boolean validCartTypeSpecified = false; // If no valid cart types failed, fail
        int cartAmountSpecified = -1;           // Keep track of numbers, to avoid things like "0m"
        for (char cart : (event.getLine(2) + event.getLine(3)).toCharArray()) {
            if (isLeftChar(cart) || isRightChar(cart)) {
                continue;
            }
            if (Character.isDigit(cart)) {
                if (cartAmountSpecified == -1) {
                    // This is the first digit of the cart specification
                    cartAmountSpecified = Character.getNumericValue(cart);
                } else {
                    // This is the second, third, ... digit of the cart specification
                    cartAmountSpecified = (10 * cartAmountSpecified) + Character.getNumericValue(cart);
                }
                continue;
            }
            type = MinecartType.get(Character.toString(cart));
            if (type == null) {
                event.getPlayer().sendMessage(ChatColor.RED + "Invalid minecart type (" + Character.toString(cart) + ")");
                return false;
            }
            if (type.getPermission().handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to create minecarts of type " + Character.toString(cart))) {
                if (cartAmountSpecified != 0) {
                    validCartTypeSpecified = true;
                } else {
                    event.getPlayer().sendMessage(ChatColor.YELLOW + "Warning: You have specified 0 minecarts of type " + Character.toString(cart));
                }
                cartAmountSpecified = -1;
            } else {
                return false;
            }
        }
        if (! validCartTypeSpecified) {
            event.getPlayer().sendMessage(ChatColor.RED + "No valid minecart types were specified");
            return false;
        }
        return true;
    }

    public static void spawn(SignActionEvent info) {
        if ((info.isTrainSign() || info.isCartSign()) && isValid(info) && info.isPowered() && info.hasRails()) {
            final double spawnForce = getSpawnForce(info);

            //Get the cart types to spawn
            SpawnTypes types = getSpawnTypes(info.getLine(2) + info.getLine(3));
            if (types.types.isEmpty()) {
                return;
            }

            // Find minecart spawn position information for each possible direction
            int sizeLim = ((types.types.size() - 1) / 2) + 1;
            List<SpawnPositions> modes = new ArrayList<SpawnPositions>();
            for (BlockFace direction : info.getWatchedDirections()) {
                SpawnPositions mode = getSpawnPositions(info, direction.getOppositeFace(), types.types.size());
                if (mode.locs.size() >= sizeLim) {
                    modes.add(mode);
                }
            }
            Collections.sort(modes);
            if (modes.isEmpty()) {
                return;
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
                    if (FaceUtil.isVertical(info.getRailDirection())) {
                        if (types.centerMode == CenterMode.LEFT) {
                            launchDirection = BlockFace.DOWN;
                        } else if (types.centerMode == CenterMode.RIGHT) {
                            launchDirection = BlockFace.UP;
                        } else {
                            types.centerMode = CenterMode.MIDDLE;
                            launchDirection = BlockFace.SELF;
                        }
                    } else {
                        if (types.centerMode == CenterMode.LEFT) {
                            launchDirection = FaceUtil.rotate(info.getFacing(), 2);
                        } else if (types.centerMode == CenterMode.RIGHT) {
                            launchDirection = FaceUtil.rotate(info.getFacing(), -2);
                        } else {
                            types.centerMode = CenterMode.MIDDLE;
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
                    types.centerMode = CenterMode.NONE;
                    launchDirection = modes.get(0).direction;
                }
            }

            // Find desired spawn locations for the minecarts
            List<Location> spawnLocations = new ArrayList<Location>(types.types.size());
            if (types.centerMode == CenterMode.MIDDLE && modes.size() >= 2) {
                // Center-Mode: combine two directions into one long stretch of minecarts
                Iterator<Location> iter0 = modes.get(0).locs.iterator();
                Iterator<Location> iter1 = modes.get(1).locs.iterator();

                // Add and skip middle cart
                spawnLocations.add(iter0.next());
                iter1.next();

                boolean mode = false; // alternates iter0/iter1
                while (spawnLocations.size() < types.types.size()) {
                    if (mode && iter0.hasNext()) {
                        spawnLocations.add(iter0.next());
                    } else if (iter1.hasNext()) {
                        spawnLocations.add(0, iter1.next());
                    } else {
                        break; // failure
                    }
                    mode = !mode;
                }
            } else {
                // Spawn direction from center mode. Default to the launch direction.
                SpawnPositions selectedMode = modes.get(0);
                BlockFace spawnDirection = launchDirection;
                if (FaceUtil.isVertical(info.getRailDirection())) {
                    // Up/down of the sign
                    if (types.centerMode == CenterMode.LEFT) {
                        spawnDirection = BlockFace.DOWN;
                    } else if (types.centerMode == CenterMode.RIGHT) {
                        spawnDirection = BlockFace.UP;
                    }

                    for (SpawnPositions mode : modes) {
                        if (mode.locs.size() < types.types.size()) {
                            if (mode.direction == launchDirection) {
                                launchDirection = BlockFace.SELF; // invalidate, cant launch there
                            }
                            continue;
                        }

                        if (mode.direction == spawnDirection && mode.locs.size() >= types.types.size()) {
                            selectedMode = mode;
                        }
                    }
                } else {
                    // Left/right of the sign
                    if (types.centerMode == CenterMode.LEFT) {
                        spawnDirection = FaceUtil.rotate(info.getFacing(), 2);
                    } else if (types.centerMode == CenterMode.RIGHT) {
                        spawnDirection = FaceUtil.rotate(info.getFacing(), -2);
                    }

                    // Figure out the best direction to spawn in and take over those locations
                    // This defaults to the longest ([0]), and favors the direction in which we launch
                    int minAngle = 1000;
                    for (SpawnPositions mode : modes) {
                        if (mode.locs.size() < types.types.size()) {
                            if (mode.direction == launchDirection) {
                                launchDirection = BlockFace.SELF; // invalidate, cant launch there
                            }
                            continue;
                        }

                        int angle = FaceUtil.getFaceYawDifference(mode.direction, spawnDirection);
                        if (angle < minAngle && mode.locs.size() >= types.types.size()) {
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
            if (spawnLocations.size() < types.types.size()) {
                return; // failed
            }

            // Prepare chunks
            for (Location loc : spawnLocations) {
                WorldUtil.loadChunks(loc, 2);
            }

            //Spawn
            MinecartGroup group = MinecartGroup.create();
            for (int i = spawnLocations.size() - 1; i >= 0; i--) {
                MinecartMember<?> mm = MinecartMemberStore.spawn(spawnLocations.get(i), types.types.get(i));
                group.add(mm);
            }
            group.updateDirection();
            group.getProperties().setDefault("spawner");
            if (spawnForce != 0 && launchDirection != BlockFace.SELF) {
                group.head().getActions().addActionLaunch(launchDirection, 2, spawnForce);
            }
            GroupCreateEvent.call(group);
        }
    }

    public static SpawnTypes getSpawnTypes(String text) {
        SpawnTypes result = new SpawnTypes();
        result.centerMode = CenterMode.NONE;
        StringBuilder amountBuilder = new StringBuilder();
        MinecartType type;
        for (char cart : text.toCharArray()) {
            // Center-Mode designating characters
            if (isLeftChar(cart)) {
                result.addCenterMode(CenterMode.LEFT);
                continue;
            }
            if (isRightChar(cart)) {
                result.addCenterMode(CenterMode.RIGHT);
                continue;
            }

            // Types and amounts for each type
            type = MinecartType.get(Character.toString(cart));
            if (type != null) {
                if (amountBuilder.length() > 0) {
                    int amount = ParseUtil.parseInt(amountBuilder.toString(), 1);
                    amountBuilder.setLength(0);
                    for (int i = 0; i < amount; i++) {
                        result.types.add(type);
                    }
                } else {
                    result.types.add(type);
                }
            } else if (Character.isDigit(cart)) {
                amountBuilder.append(cart);
            }
        }
        return result;
    }

    /**
     * Gets the Minecart spawn positions into a certain drection.
     * The first location is always the middle on top of the current rail of the sign.
     * 
     * @param info sign event information
     * @param direction of spawning
     * @param nLimit limit amount of minecarts to spawn where we can stop looking for more spaces
     * @return SpawnPositions with locs limited to the amount that could be spawned
     */
    public static SpawnPositions getSpawnPositions(SignActionEvent info, BlockFace direction, int nLimit) {
        SpawnPositions result = new SpawnPositions();
        result.direction = direction;
        result.powered = info.isPowered(direction);
        Location centerLoc = info.getCenterLocation();
        if (nLimit == 1) {
            // Single-minecart spawning logic
            if (MinecartMemberStore.getAt(centerLoc) == null) {
                TrackIterator iter = new TrackIterator(info.getRails(), direction);
                // Ignore the starting block
                iter.next();
                // Next block available?
                if (iter.hasNext()) {
                    result.locs.add(centerLoc);
                }
            }
        } else {
            // Multiple-minecart spawning logic
            TrackWalkIterator iter = new TrackWalkIterator(info.getCenterLocation(), direction);
            for (int i = 0; i < nLimit && iter.hasNext(); i++) {
                // Next location, not taken?
                Location loc = iter.next();
                if (MinecartMemberStore.getAt(loc) == null) {
                    result.locs.add(loc);
                } else {
                    break;
                }
            }
        }
        return result;
    }

    public static void init(String filename) {
        spawnSigns.clear();
        new DataReader(filename) {
            public void read(DataInputStream stream) throws IOException {
                int count = stream.readInt();
                for (; count > 0; --count) {
                    SpawnSign sign = SpawnSign.read(stream);
                    spawnSigns.put(sign.getWorldName(), sign.getLocation(), sign);
                    sign.start();
                }
            }
        }.read();
        hasChanges = false;
    }

    public static void deinit() {
        for (SpawnSign sign : spawnSigns.values()) {
            sign.stop();
        }
    }

    public static void save(boolean autosave, String filename) {
        if (autosave && !hasChanges) {
            return;
        }
        new DataWriter(filename) {
            public void write(DataOutputStream stream) throws IOException {
                stream.writeInt(spawnSigns.size());
                for (SpawnSign sign : spawnSigns.values()) {
                    sign.write(stream);
                }
            }
        }.write();
        hasChanges = false;
    }

    @Override
    public boolean match(SignActionEvent info) {
        return isValid(info);
    }

    @Override
    public void execute(SignActionEvent info) {
        if (info.isAction(SignActionType.REDSTONE_ON) && getSpawnTime(info) == 0) {
            spawn(info);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (hasCartPerms(event) && (handleBuild(event, Permission.BUILD_SPAWNER, "train spawner", "spawn trains on the tracks above when powered by redstone"))) {
            long interval = getSpawnTime(event);
            if (interval > 0 && (Permission.SPAWNER_AUTOMATIC.handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to use automatic signs"))) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + "This spawner will automatically spawn trains every " + Util.getTimeString(interval) + " while powered");
                SpawnSign sign = new SpawnSign(event.getBlock(), interval);
                SpawnSign oldSign = spawnSigns.put(event.getBlock(), sign);
                if (oldSign != null) {
                    oldSign.stop();
                }
                hasChanges = true;
                sign.start();
            }
            return true;
        }
        return false;
    }

    @Override
    public void destroy(SignActionEvent info) {
        remove(info.getBlock());
    }

    private static boolean isLeftChar(char c) {
        return LogicUtil.containsChar(c, "]>)}");
    }

    private static boolean isRightChar(char c) {
        return LogicUtil.containsChar(c, "[<({");
    }

    private static class SpawnTypes {
        public CenterMode centerMode = CenterMode.MIDDLE;
        public List<MinecartType> types = new ArrayList<MinecartType>();

        public void addCenterMode(CenterMode mode) {
            if (this.centerMode == CenterMode.NONE || this.centerMode == mode) {
                this.centerMode = mode;
            } else {
                this.centerMode = CenterMode.MIDDLE;
            }
        }
    }

    private static class SpawnPositions implements Comparable<SpawnPositions> {
        public BlockFace direction;
        public List<Location> locs = new ArrayList<Location>();
        public boolean powered;

        @Override
        public int compareTo(SpawnPositions o) {
            return o.locs.size() - locs.size();
        }
    }

    private static enum CenterMode {
        NONE, MIDDLE, LEFT, RIGHT
    }
    
}
