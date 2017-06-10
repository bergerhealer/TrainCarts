package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.actions.BlockActionSetLevers;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Represents the Station sign information
 */
public class Station {
    private final SignActionEvent info;
    private final double length;
    private final long delay;
    private final BlockFace instruction;
    private final Direction nextDirection;
    private final boolean valid;
    private final BlockFace railDirection;
    private final Block railsBlock;
    private boolean wasCentered = false;

    public Station(SignActionEvent info) {
        this.info = info;
        this.delay = ParseUtil.parseTime(info.getLine(2));
        this.nextDirection = Direction.parse(info.getLine(3));
        this.railsBlock = info.getRails();

        // Vertical or horizontal rail logic
        this.railDirection = info.getRailDirection();
        if (FaceUtil.isVertical(this.railDirection)) {
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
            if (FaceUtil.isSubCardinal(this.railDirection) && FaceUtil.isSubCardinal(info.getFacing())) {
                // Sub-cardinal checks: Both directions have two possible powered sides
                final BlockFace[] faces = FaceUtil.getFaces(this.railDirection);
                boolean pow1 = info.isPowered(faces[0]) || info.isPowered(faces[1].getOppositeFace());
                boolean pow2 = info.isPowered(faces[1]) || info.isPowered(faces[0].getOppositeFace());
                if (pow1 && !pow2) {
                    this.instruction = FaceUtil.combine(faces[0], faces[1].getOppositeFace());
                } else if (!pow1 && pow2) {
                    this.instruction = FaceUtil.combine(faces[0].getOppositeFace(), faces[1]);
                } else if (info.isPowered()) {
                    this.instruction = BlockFace.SELF;
                } else {
                    this.instruction = null;
                }
            } else {
                // Which directions to move, or brake?
                if (FaceUtil.isAlongX(this.railDirection)) {
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
                } else if (FaceUtil.isAlongZ(this.railDirection)) {
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
                    this.length = 0.0;
                    this.instruction = null;
                    this.valid = false;
                    return;
                }
            }
        }

        // Get initial station length, delay and direction
        double length = ParseUtil.parseDouble(info.getLine(1).substring(7), 0.0);
        if (length == 0.0 && this.instruction != null) {
            // Manually calculate the length
            // Use the amount of straight blocks
            length = Util.calculateStraightLength(this.railsBlock, this.instruction);
            if (length == 0.0) {
                length++;
            }
        }
        this.length = length;
        this.valid = true;
    }

    /**
     * Gets the length of the station
     *
     * @return station length
     */
    public double getLength() {
        return this.length;
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
     * Gets the direction to launch to after waiting
     *
     * @return post wait launch direction
     */
    public Direction getNextDirection() {
        return this.nextDirection;
    }

    /**
     * Gets the minecart that has to be centered above the sign
     *
     * @param offset forwards into the train
     * @return center minecart
     */
    public MinecartMember<?> getCenterCart(int offset) {
        MinecartGroup group = this.info.getGroup();
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
     * Gets the minecart that has to be centered above the sign
     *
     * @return center minecart
     */
    public MinecartMember<?> getCenterCart() {
        return getCenterCart(0);
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
        if (TrainCarts.playSoundAtStation) {
            actions.addActionSizzle();
        }
        if (TrainCarts.refillAtStations) {
            actions.addActionRefill();
        }
        actions.addAction(new BlockActionSetLevers(info.getAttachedBlock(), true));
        if (delay == Long.MAX_VALUE) {
            actions.addActionWaitForever();
        } else if (delay > 0) {
            actions.addActionWait(delay);
        }
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
                getCenterCart().getActions().addActionLaunch(stationInfo.cartDir, stationInfo.distance, 0.0);
            } else {
                // Alternative: get as close as possible (may fail)
                getCenterCart().getActions().addActionLaunch(info.getCenterLocation(), 0);
            }
        }
        this.wasCentered = true;
    }

    /**
     * Launches the train so that the middle or front cart is the distance away from this station
     *
     * @param direction to launch into
     * @param distance  to launch
     */
    public void launchTo(BlockFace direction, double distance) {
        double newDistance = distance;
        if (!wasCentered) {
            // Apply distance correction from center cart to station
            CartToStationInfo stationInfo = getCartToStationInfo();
            // Adjust the direction and distance
            if (stationInfo.cartDir == direction) {
                // Adjust the direction and distance
                newDistance += stationInfo.distance;
            }
        }
        getCenterCart().getActions().addActionLaunch(direction, newDistance, TrainCarts.launchForce);
        this.wasCentered = false;
    }

    private CartToStationInfo getCartToStationInfo() {
        MinecartMember<?> member = getCenterCart();
        CartToStationInfo info = new CartToStationInfo();
        info.cartBlock = member.getBlock();
        BlockFace[] possible = RailType.getType(info.cartBlock).getPossibleDirections(info.cartBlock);
        Location centerPos = this.info.getCenterLocation();
        Location cartPos = member.getEntity().getLocation();

        info.distance = centerPos.distance(cartPos);
        if (info.distance <= 1.0) {
            // The minecart is very close to the goal already - no need to follow the tracks
            // Find the cart direction that heads in the same direction
            Vector offset = new Vector(centerPos.getX() - cartPos.getX(),
                    centerPos.getY() - cartPos.getY(),
                    centerPos.getZ() - cartPos.getZ());
            offset = offset.normalize();

            double minDiff = Double.MAX_VALUE;
            info.cartDir = member.getDirectionTo();
            for (BlockFace dir : possible) {
                Vector diff = new Vector(offset.getX() - dir.getModX(),
                        offset.getY() - dir.getModY(),
                        offset.getZ() - dir.getModZ());
                double len = diff.lengthSquared();
                if (len < minDiff) {
                    minDiff = len;
                    info.cartDir = dir;
                }
            }
        } else {
            // Use a track iterator to find the rails belonging to the sign from the targeted cart
            int trackLimit = (int) (info.distance * 1.5);
            info.distance = Double.MAX_VALUE;
            for (BlockFace dir : possible) {
                TrackIterator iterator = new TrackIterator(info.cartBlock, dir, trackLimit, true);
                if (iterator.tryFind(this.info.getRails()) && iterator.getCartDistance() < info.distance) {
                    info.distance = iterator.getCartDistance();
                    info.cartDir = dir;
                }
            }

            // If found, adjust for the distance traveled on the start block
            if (info.cartDir != null) {
                IntVector3 blockPos = member.getEntity().loc.block();
                double tx = (member.getEntity().loc.getX() - blockPos.midX());
                double ty = (member.getEntity().loc.getY() - blockPos.midY());
                double tz = (member.getEntity().loc.getZ() - blockPos.midZ());
                info.distance -= tx * info.cartDir.getModX() + ty * info.cartDir.getModY() + tz * info.cartDir.getModZ();
                info.distance -= 1.0;
            }
        }

        // Adjust distance for even-count trains (center is in between two carts then!)
        if (this.info.isTrainSign() && (member.getGroup().size() & 1) == 0) {
            Location m2 = getCenterCart(1).getEntity().getLocation();
            info.distance -= (m2.distance(cartPos) / 2.0);
        }

        return info;
    }

    private static class CartToStationInfo {
        public Block cartBlock;
        public BlockFace cartDir;
        public double distance;
    }
}
