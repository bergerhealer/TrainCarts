package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.actions.MemberActionLaunchDirection;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerGroup;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Represents the Station sign information
 */
public class Station {
    private final SignActionEvent info;
    private final LauncherConfig launchConfig;
    private final double launchForce;
    private final long delay;
    private final BlockFace instruction;
    private final Direction nextDirection;
    private final boolean valid;
    private final Block railsBlock;
    private boolean wasCentered = false;
    private double centerOffset = 0.0;

    public Station(SignActionEvent info) {
        this.info = info;
        this.delay = ParseUtil.parseTime(info.getLine(2));
        this.railsBlock = info.getRails();

        // Parse the (next) launch direction and launch force (speed) on the fourth line
        Direction parsedNextDirection = Direction.NONE;
        double parsedLaunchForce = TCConfig.launchForce;
        for (String part : info.getLine(3).split(" ")) {
            Direction direction = Direction.parse(part);
            if (direction != Direction.NONE) {
                parsedNextDirection = direction;
            } else {
                parsedLaunchForce = parseLaunchForce(part, info);
            }
        }
        this.nextDirection = parsedNextDirection;
        this.launchForce = parsedLaunchForce;

        // Vertical or horizontal rail logic
        if (info.isRailsVertical()) {
            // Up, down or center based on redstone power
            boolean up = info.isPowered(BlockFace.UP);
            boolean down = info.isPowered(BlockFace.DOWN);
            if (up && !down) {
                this.instruction = BlockFace.UP;
            } else if (!up && down) {
                this.instruction = BlockFace.DOWN;
            } else if (info.isPowered()) {
                this.instruction = BlockFace.SELF;
            } else {
                this.instruction = null;
            }
        } else {
            Vector railDirection = info.getCartEnterDirection();
            boolean diagonal = Util.isDiagonal(railDirection);

            // Diagonal logic is only used for SIGN_POST, so verify that this is the case
            if (diagonal) {
                org.bukkit.material.Sign sign_material = BlockUtil.getData(info.getBlock(), org.bukkit.material.Sign.class);
                if (sign_material == null || sign_material.isWallSign()) {
                    diagonal = false;
                }
            }

            if (Util.isDiagonal(railDirection)) {
                org.bukkit.material.Sign sign_material = BlockUtil.getData(info.getBlock(), org.bukkit.material.Sign.class);
                if (sign_material == null || sign_material.isWallSign()) {
                    // A wall sign used with a diagonal piece of track
                    // The direction is based on the facing of the sign, so adjust for that
                    BlockFace facing = info.getFacing();
                    if (FaceUtil.isAlongX(facing)) {
                        // Sign facing is along X, launch along Z
                        boolean north = info.isPowered(BlockFace.NORTH);
                        boolean south = info.isPowered(BlockFace.SOUTH);
                        if (north && !south) {
                            this.instruction = BlockFace.NORTH;
                        } else if (south && !north) {
                            this.instruction = BlockFace.SOUTH;
                        } else if (info.isPowered()) {
                            this.instruction = BlockFace.SELF;
                        } else {
                            this.instruction = null;
                        }
                    } else {
                        // Sign facing is along Z, launch along X
                        boolean west = info.isPowered(BlockFace.WEST);
                        boolean east = info.isPowered(BlockFace.EAST);
                        if (west && !east) {
                            this.instruction = BlockFace.WEST;
                        } else if (east && !west) {
                            this.instruction = BlockFace.EAST;
                        } else if (info.isPowered()) {
                            this.instruction = BlockFace.SELF;
                        } else {
                            this.instruction = null;
                        }
                    }
                } else {
                    // Diagonal logic is only used for SIGN_POST
                    // Sub-cardinal checks: Both directions have two possible powered sides
                    BlockFace face_x = (railDirection.getX() > 0.0) ? BlockFace.EAST : BlockFace.WEST;
                    BlockFace face_z = (railDirection.getZ() > 0.0) ? BlockFace.SOUTH : BlockFace.NORTH;
                    boolean pow1 = info.isPowered(face_x) || info.isPowered(face_z);
                    boolean pow2 = info.isPowered(face_x.getOppositeFace()) || info.isPowered(face_z.getOppositeFace());
                    if (pow1 && !pow2) {
                        this.instruction = FaceUtil.combine(face_x, face_z);
                    } else if (!pow1 && pow2) {
                        this.instruction = FaceUtil.combine(face_x.getOppositeFace(), face_z.getOppositeFace());
                    } else if (info.isPowered()) {
                        this.instruction = BlockFace.SELF;
                    } else {
                        this.instruction = null;
                    }
                }
            } else if (Math.abs(railDirection.getX()) > Math.abs(railDirection.getZ())) {
                // Along X
                boolean west = info.isPowered(BlockFace.WEST);
                boolean east = info.isPowered(BlockFace.EAST);
                if (west && !east) {
                    this.instruction = BlockFace.WEST;
                } else if (east && !west) {
                    this.instruction = BlockFace.EAST;
                } else if (info.isPowered()) {
                    this.instruction = BlockFace.SELF;
                } else {
                    this.instruction = null;
                }
            } else {
                // Along Z
                boolean north = info.isPowered(BlockFace.NORTH);
                boolean south = info.isPowered(BlockFace.SOUTH);
                if (north && !south) {
                    this.instruction = BlockFace.NORTH;
                } else if (south && !north) {
                    this.instruction = BlockFace.SOUTH;
                } else if (info.isPowered()) {
                    this.instruction = BlockFace.SELF;
                } else {
                    this.instruction = null;
                }
            }
        }

        // Parse and filter offset before parsing launcher configuration
        // Offset is specified using '0.352m'
        String launchConfigStr = info.getLine(1).substring(7);
        int offsetTextEndIdx = launchConfigStr.indexOf('m');
        if (offsetTextEndIdx != -1) {
            launchConfigStr = launchConfigStr.substring(0, offsetTextEndIdx) + launchConfigStr.substring(offsetTextEndIdx + 1);

            int offsetTextStartIdx = offsetTextEndIdx - 1;
            while (offsetTextStartIdx >= 0) {
                char c = launchConfigStr.charAt(offsetTextStartIdx);
                if (!Character.isDigit(c) && c != '.' && c != ',' && c != '-') {
                    break;
                } else {
                    offsetTextStartIdx--;
                }
            }
            offsetTextStartIdx++;

            if (offsetTextStartIdx < offsetTextEndIdx) {
                String offsetStr = launchConfigStr.substring(offsetTextStartIdx, offsetTextEndIdx);
                launchConfigStr = launchConfigStr.substring(0, offsetTextStartIdx) + launchConfigStr.substring(offsetTextEndIdx);
                this.centerOffset = ParseUtil.parseDouble(offsetStr, 0.0);
            }
        }

        // Get initial station length, delay and direction
        this.launchConfig = LauncherConfig.parse(launchConfigStr);
        if (!this.launchConfig.hasDuration() && !this.launchConfig.hasDistance() && this.instruction != null) {
            // Manually calculate the length
            // Use the amount of straight blocks
            BlockFace launchDir = this.instruction;
            if (launchDir == BlockFace.SELF) {
                launchDir = getNextDirectionFace();
            }
            double length = Util.calculateStraightLength(this.railsBlock, launchDir);
            if (length == 0.0) {
                length++;
            }
            this.launchConfig.setDistance(length);
        }
        this.valid = true;
    }

    /**
     * Gets a tag unique to this station's location.
     * Is applied to all actions executed by this station
     * 
     * @return tag
     */
    public String getTag() {
        return StringUtil.blockToString(this.info.getBlock());
    }

    /**
     * Gets whether this station has a delay set
     *
     * @return True if a delay is set, False if not
     */
    public boolean hasDelay() {
        return this.delay > 0;
    }

    /**
     * Gets the delay between action and launch (in milliseconds)
     *
     * @return action delay
     */
    public long getDelay() {
        return this.delay;
    }

    /**
     * Checks if this Station is valid for use
     *
     * @return True if valid, False if not
     */
    public boolean isValid() {
        return this.valid;
    }

    /**
     * Gets the launch configuration, which contains the distance/time and launch function used
     * 
     * @return launcher configuration
     */
    public LauncherConfig getLaunchConfig() {
        return this.launchConfig;
    }

    /**
     * Gets the instruction this station has right now<br>
     * - This is SELF when it has to center the train<br>
     * - This is the direction to launch to if it has to launch<br>
     * - This is null if the station should do nothing and release the train
     *
     * @return instruction
     */
    public BlockFace getInstruction() {
        return this.instruction;
    }

    /**
     * Gets the direction to launch to after waiting, as a BlockFace
     *
     * @return post wait launch direction
     */
    public BlockFace getNextDirectionFace() {
        return getNextDirection().getDirection(info.getFacing(), info.getMember().getDirection());
    }

    /**
     * Gets the direction to launch to after waiting
     *
     * @return post wait launch direction
     */
    public Direction getNextDirection() {
        return this.nextDirection;
    }

    /**
     * Gets the Minecart Group initiating this station
     * 
     * @return group
     */
    public MinecartGroup getGroup() {
        return this.info.getGroup();
    }

    /**
     * Gets the minecart that has to be centered above the sign<br>
     * <b>Deprecated: unused because it fails with different size carts</b>
     *
     * @param offset forwards into the train
     * @return center minecart
     */
    @Deprecated
    public MinecartMember<?> getCenterCart(int offset) {
        MinecartGroup group = this.getGroup();
        int size = group.size();
        if (this.info.isCartSign()) {
            // Always use the member that triggered the sign
            return this.info.getMember();
        } else if ((size & 0x1) == 0x1) {
            // Odd number of minecarts - always pick the middle one
            int index = (int) Math.floor((double) size / 2.0);
            if (offset != 0 && size >= 3) {
                // Offsets are towards the sign - figure out whether that is + or -
                Location s = this.info.getCenterLocation();
                double d1 = group.get(index - 1).getEntity().loc.distance(s);
                double d2 = group.get(index + 1).getEntity().loc.distance(s);
                if (d1 < d2) {
                    index += offset;
                } else {
                    index -= offset;
                }
            }
            return group.get(index);
        } else {
            // Even number of minecarts - take one furthest away from sign
            int mIdx1 = (int) Math.ceil((double) size / 2) - 1;
            int mIdx2 = mIdx1 + 1;
            Location s = this.info.getCenterLocation();
            double d1 = group.get(mIdx1).getEntity().loc.distance(s);
            double d2 = group.get(mIdx2).getEntity().loc.distance(s);
            if (d1 > d2) {
                return group.get(mIdx1 + offset);
            } else {
                return group.get(mIdx2 - offset);
            }
        }
    }

    /**
     * Gets the minecart that has to be centered above the sign<br>
     * <b>Deprecated: unused because it fails with different size carts</b>
     *
     * @return center minecart
     */
    @Deprecated
    public MinecartMember<?> getCenterCart() {
        return getCenterCart(0);
    }

    /**
     * Gets the minecart that is closest towards the center of the entire train
     * 
     * @return center position Minecart
     */
    public MinecartMember<?> getCenterPositionCart() {
        MinecartGroup group = this.getGroup();

        // Easy mode
        if (group.size() == 1) {
            return group.get(0);
        }
        
        if(this.info.isCartSign()) {
            return this.info.getMember();
        }

        // Calculate total size first
        double total_size = 0.5 * (double) group.head().getEntity().getWidth();
        for (int i = 1; i < group.size(); i++) {
            total_size += group.get(i).getEntity().loc.distance(group.get(i-1).getEntity().loc);
        }
        total_size += 0.5 * (double) group.tail().getEntity().getWidth();

        // Goal is half
        double half_size = total_size * 0.5;

        // Now iterate the minecarts, tracking accumulated size, until we cross the half boundary
        // Then we decide whether to pick that minecart, or the one that came before
        double accum_size = 0.5 * (double) group.head().getEntity().getWidth();

        // First cart is really big! Then this is the only option.
        if (accum_size > half_size) {
            return group.head();
        }

        // Go down the train's members accumulating until we cross the half-point
        // When this occurs, compare old and new accumulated sizes
        // Based on this, return either [i] or [i-1]
        for (int i = 1; i < group.size(); i++) {
            double new_accum_size = accum_size;
            new_accum_size += group.get(i).getEntity().loc.distance(group.get(i-1).getEntity().loc);
            if (new_accum_size > half_size) {
                double d_prev = half_size - accum_size;
                double d_curr = new_accum_size - half_size;
                if (d_prev < d_curr) {
                    return group.get(i-1);
                } else {
                    return group.get(i);
                }
            }
            accum_size = new_accum_size;
        }

        // Weird? Last cart might just be too big. Assume tail.
        return group.tail();
    }

    /**
     * Waits a train for a specific amount of time.
     * This causes the train to play the station sound, refill the fuel
     * and toggle the station levers on.
     *
     * @param delay to wait, use 0 for no delay, MAX_VALUE to wait forever.
     */
    public void waitTrain(long delay) {
        ActionTrackerGroup actions = info.getGroup().getActions();
        if (TCConfig.playHissWhenStopAtStation) {
            actions.addActionSizzle().addTag(this.getTag());
        }
        if (TCConfig.refillAtStations) {
            actions.addActionRefill().addTag(this.getTag());
        }
        setLevers(true);
        if (delay == Long.MAX_VALUE) {
            actions.addActionWaitForever().addTag(this.getTag());
        } else if (delay > 0) {
            actions.addActionWait(delay).addTag(this.getTag());
            setLevers(false);
        }
    }

    public void setLevers(boolean down) {
        info.getGroup().getActions().addActionSetLevers(info.getAttachedBlock(), down).addTag(this.getTag());
    }

    /**
     * Orders the train to center above this Station
     */
    public void centerTrain() {
        CartToStationInfo stationInfo = getCartToStationInfo();
        if (!info.getGroup().getActions().hasAction() && stationInfo.distance <= 0.01) {
            // Already in range of station, we can stop here
            info.getGroup().stop();
        } else {
            // We have to launch to get the train stopped at the station
            if (stationInfo.cartDir != null) {
                // Launch the center cart into the direction of the station
                stationInfo.cart.getActions().addActionLaunch(stationInfo.cartDir, stationInfo.distance, 0.0).addTag(this.getTag());
            } else {
                // Alternative: get as close as possible (may fail)
                stationInfo.cart.getActions().addActionLaunch(stationInfo.centerLocation, 0).addTag(this.getTag());
            }
        }
        this.wasCentered = true;
    }

    /**
     * Launches the train so that the middle or front cart is launched away from this station.
     * Station length or launch time is used for this launch.
     * 
     * @param direction to launch into
     */
    public void launchTo(BlockFace direction) {
        if (!wasCentered) {
            // Apply distance correction from center cart to station
            CartToStationInfo stationInfo = getCartToStationInfo();
            // Adjust the direction and distance
            if (stationInfo.cartDir == direction && this.launchConfig.hasDistance()) {
                // Adjust the direction and distance
                this.launchConfig.setDistance(this.launchConfig.getDistance() + stationInfo.distance);
            }
        }

        setLevers(false);
        MemberActionLaunchDirection action = getCenterPositionCart().getActions().addActionLaunch(direction, this.launchConfig, this.launchForce);
        action.addTag(this.getTag());
        this.wasCentered = false;
    }

    private CartToStationInfo getCartToStationInfo() {
        CartToStationInfo info = new CartToStationInfo();
        info.cart = this.getCenterPositionCart();
        info.centerLocation = this.info.getCenterLocation();

        // Get rail state info of the center cart, plus one in the opposite direction
        RailState centercart_state = info.cart.getRailTracker().getState();
        RailState centercart_state_inv = centercart_state.clone();
        centercart_state_inv.position().invertMotion();
        centercart_state_inv.initEnterDirection();

        // Try both directions of movement from the center cart perspective and find the rails block
        info.distance = centercart_state.position().distance(info.centerLocation);
        info.cartDir = Util.vecToFace(info.cart.getRailTracker().getMotionVector(), false);
        info.centerMoveDir = info.cart.getRailTracker().getMotionVector();
        double maxDistance = 2.0 * info.distance;
        TrackWalkingPoint p = new TrackWalkingPoint(centercart_state);
        TrackWalkingPoint p_inv = new TrackWalkingPoint(centercart_state_inv);
        if (p.moveFindRail(this.info.getRails(), maxDistance)) {
            maxDistance = p.movedTotal;
            info.distance = p.movedTotal;
            info.centerMoveDir = p.state.motionVector();
        }
        if (p_inv.moveFindRail(this.info.getRails(), maxDistance)) {
            p = p_inv;
            maxDistance = p.movedTotal;
            info.distance = p.movedTotal;
            info.centerMoveDir = p.state.motionVector();
            info.cartDir = info.cartDir.getOppositeFace();
        }

        // Adjust distance moved since calculating the center cart's position
        //info.distance -= info.cart.getRailTracker().getState().position().distance(info.cart.getEntity().getLocation());

        // The center of the train is not exactly where this center cart is at
        // Calculate an additional distance offset to center the train
        // This also takes care of uneven-cart count trains

        // Calculate total size of the train, at the same time calculate distance
        // to the center of the center member.
        // Use the actual distance between carts for this, instead of 'expected'
        // Also take the half-sizes on either end into account
        MinecartGroup group = this.getGroup();
        if (group.size() > 1 && !this.info.isCartSign()) {
            double center_size = 0.5 * (double) group.get(0).getEntity().getWidth();
            double total_size = center_size;
            for (int i = 1; i < group.size(); i++) {
                MinecartMember<?> m = group.get(i);

                // Add half the widths of both minecarts
                total_size += 0.5 * (double) m.getEntity().getWidth();
                total_size += 0.5 * (double) group.get(i-1).getEntity().getWidth();

                // Find the size of the gap between the two minecarts, as this can vary
                // TODO: Using the calculated value works but caused far worse station centering performance
                //       It seems specifying the hardcoded configured gap works best
                //       Perhaps this is because the gap changes during the station launching?
                total_size += TCConfig.cartDistanceGap;
                //total_size += MinecartMember.calculateGapAndDirection(m, group.get(i-1), new org.bukkit.util.Vector());

                if (m == info.cart) {
                    center_size = total_size;
                }
            }
            total_size += 0.5 * (double) group.tail().getEntity().getWidth();

            // Adjust distance based on this information
            info.distance += (0.5*total_size) - center_size;
        }

        // Add distance based on center offset
        if (this.centerOffset != 0.0) {
            // Figure out what movement vector is positive for this station based on sign facing
            Vector stationMoveDir = info.centerMoveDir.clone();
            if ((stationMoveDir.getX()+stationMoveDir.getY()+stationMoveDir.getZ()) < 0.0) {
                stationMoveDir.multiply(-1.0);
            }
            Vector facingVec = FaceUtil.faceToVector(this.info.getFacing());
            facingVec = new Vector(facingVec.getZ(), facingVec.getY(), facingVec.getX());
            if (stationMoveDir.dot(facingVec) < 0.0) {
                stationMoveDir.multiply(-1.0);
            }

            // Add or subtract the offset from launch distance
            if (stationMoveDir.dot(info.centerMoveDir) < 0.0) {
                info.distance += this.centerOffset;
            } else {
                info.distance -= this.centerOffset;
            }
        }

        return info;
    }

    private double parseLaunchForce(String text, SignActionEvent info) {
        if (text.equalsIgnoreCase("max") && info.hasGroup()) {
            return info.getGroup().getProperties().getSpeedLimit();
        }

        return ParseUtil.parseDouble(text, TCConfig.launchForce);
    }

    private static class CartToStationInfo {
        public MinecartMember<?> cart;
        public BlockFace cartDir;
        public Vector centerMoveDir;
        public double distance;
        public Location centerLocation;
    }
}
