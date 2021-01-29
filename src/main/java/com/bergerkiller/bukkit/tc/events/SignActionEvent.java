package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.SignActionHeader;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;

public class SignActionEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Block signblock;
    private BlockFace facing;
    private final SignActionHeader header;
    private final Sign sign;
    private BlockFace[] watchedDirections;
    private RailPiece rail;
    private SignActionType actionType;
    private BlockFace raildirection = null;
    private MinecartMember<?> member = null;
    private MinecartGroup group = null;
    private boolean memberchecked = false;
    private boolean cancelled = false;

    public SignActionEvent(Block signblock, MinecartMember<?> member) {
        this(signblock);
        this.member = member;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, RailPiece rail, MinecartMember<?> member) {
        this(signblock, rail);
        this.member = member;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, MinecartGroup group) {
        this(signblock);
        this.group = group;
        this.memberchecked = true;
    }

    public SignActionEvent(RailSignCache.TrackedSign trackedSign, MinecartMember<?> member) {
        this(trackedSign);
        this.member = member;
        this.memberchecked = true;
    }

    public SignActionEvent(RailSignCache.TrackedSign trackedSign, MinecartGroup group) {
        this(trackedSign);
        this.group = group;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, RailPiece rail, MinecartGroup group) {
        this(signblock, rail);
        this.group = group;
        this.memberchecked = true;
    }

    public SignActionEvent(final Block signblock) {
        this(signblock, (RailPiece) null);
    }

    public SignActionEvent(final Block signblock, RailPiece rail) {
        this(signblock, signblock == null ? null : BlockUtil.getSign(signblock), rail);
    }

    public SignActionEvent(RailSignCache.TrackedSign trackedSign) {
        this(trackedSign.signBlock, trackedSign.sign, trackedSign.rail);
    }

    public SignActionEvent(final Block signblock, final Sign sign, RailPiece rail) {
        this.signblock = signblock;
        this.sign = sign;
        this.rail = rail;
        this.actionType = SignActionType.NONE;
        this.facing = null;
        if (this.sign == null) {
            // No sign available - set default values and abort
            this.header = SignActionHeader.parse(null);
            this.watchedDirections = FaceUtil.AXIS;
        } else {
            // Sign available - initialize the sign
            this.header = SignActionHeader.parseFromEvent(this);
            if (this.header.isLegacyConverted() && this.header.isValid()) {
                this.setLine(0, this.header.toString());
            }
            this.watchedDirections = null;
        }
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * Sets whether levers connected to this Sign are toggled
     *
     * @param down state to set to
     */
    public void setLevers(boolean down) {
        BlockUtil.setLeversAroundBlock(this.getAttachedBlock(), down);
    }

    /**
     * Gets whether this sign is used with track that is vertically-aligned.
     * 
     * @return True if this sign is used with a vertically-aligned track
     */
    public boolean isRailsVertical() {
        if (!this.hasRails()) {
            return false;
        }

        BlockFace signDirection = this.getFacing().getOppositeFace();
        RailState state = new RailState();
        state.setRailPiece(this.getRailPiece());
        state.position().setLocation(state.railType().getSpawnLocation(state.railBlock(), signDirection));
        state.position().setMotion(signDirection);
        state.initEnterDirection();
        state.loadRailLogic().getPath().snap(state.position(), state.railBlock());
        //TODO: This could be done more efficiently!
        return FaceUtil.isVertical(Util.vecToFace(state.position().getMotion(), false));
    }

    /**
     * Gets the direction vector of the cart upon entering the rails
     * that triggered this sign. If no cart exists, it defaults to the activating direction
     * of the sign (facing or watched directions).
     * 
     * @return enter direction vector
     */
    public Vector getCartEnterDirection() {
        if (this.hasMember()) {
            // Find the rails block matching the one that triggered this event
            // Return the enter ('from') direction for that rails block if found
            if (this.hasRails()) {
                Block rails = this.getRails();
                for (TrackedRail rail : this.member.getGroup().getRailTracker().getRailInformation()) {
                    if (rail.member == this.member && rail.state.railBlock().equals(rails)) {
                        return rail.state.enterDirection();
                    }
                }
            }

            // Ask the minecart itself alternatively
            return this.member.getEntity().getVelocity();
        }

        // Find the facing direction from watched directions, or sign orientation
        BlockFace signDirection;
        if (this.getWatchedDirections().length > 0) {
            signDirection = this.getWatchedDirections()[0];
        } else {
            signDirection = this.getFacing().getOppositeFace();
        }

        // Snap sign direction to the rails, if a rails exists
        if (this.hasRails()) {
            RailState state = new RailState();
            state.setRailPiece(this.getRailPiece());
            state.position().setLocation(state.railType().getSpawnLocation(state.railBlock(), signDirection));
            state.position().setMotion(signDirection);
            state.loadRailLogic().getPath().snap(state.position(), state.railBlock());
            return state.position().getMotion();
        }

        return FaceUtil.faceToVector(signDirection);
    }

    /**
     * Gets the direction Block Face of the cart upon entering the rails
     * that triggered this sign. If no cart exists, it defaults to the activating direction
     * of the sign (facing or watched directions).
     * 
     * @return enter direction face
     */
    public BlockFace getCartEnterFace() {
        if (this.hasMember()) {
            // Find the rails block matching the one that triggered this event
            // Return the enter ('from') direction for that rails block if found
            if (this.hasRails()) {
                Block rails = this.getRails();
                for (TrackedRail rail : this.member.getGroup().getRailTracker().getRailInformation()) {
                    if (rail.member == this.member && rail.state.railBlock().equals(rails)) {
                        return rail.state.enterFace();
                    }
                }
            }

            // Ask the minecart itself alternatively
            return Util.vecToFace(this.member.getEntity().getVelocity(), false);
        }

        // Find the facing direction from watched directions, or sign orientation
        BlockFace signDirection;
        if (this.getWatchedDirections().length > 0) {
            signDirection = this.getWatchedDirections()[0];
        } else {
            signDirection = this.getFacing().getOppositeFace();
        }

        // Snap sign direction to the rails, if a rails exists
        if (this.hasRails()) {
            RailState state = new RailState();
            state.setRailPiece(this.getRailPiece());
            state.position().setLocation(state.railType().getSpawnLocation(state.railBlock(), signDirection));
            state.position().setMotion(signDirection);
            state.initEnterDirection();
            state.loadRailLogic().getPath().snap(state.position(), state.railBlock());
            state.initEnterDirection();
            return state.enterFace();
        }

        return signDirection;
    }

    /**
     * Gets the direction a minecart has above the rails of this Sign.<br>
     * <br>
     * <b>Deprecated: use {@link #getCartEnterFace} instead</b>
     *
     * @return cart direction
     */
    @Deprecated
    public BlockFace getCartDirection() {
        return this.getCartEnterFace();
    }

    /* ============================= Deprecated BlockFace Junctions =============================== */

    /**
     * Sets the rails above this sign to connect with the from and to directions<br>
     * If the cart has to be reversed, that is done<br>
     * <br>
     * <b>Deprecated: no longer limited to BlockFace directions, use junctions instead</b>
     *
     * @param from direction
     * @param to   direction
     */
    @Deprecated
    public void setRailsFromTo(BlockFace from, BlockFace to) {
        setRailsFromTo(findJunction(from), findJunction(to));
    }

    /**
     * Sets the rails above this sign to lead from the minecart direction to the direction specified<br>
     * <br>
     * <b>Deprecated: no longer limited to BlockFace directions, use junctions instead</b>
     *
     * @param to direction
     */
    @Deprecated
    public void setRailsTo(BlockFace to) {
        setRailsTo(findJunction(to));
    }

    /**
     * Sets the rails above this sign to lead from the minecart direction into a direction specified<br>
     * Relative directions, like left and right, are relative to the sign direction<br>
     * <br>
     * <b>Deprecated: no longer limited to BlockFace directions, use junctions instead</b>
     *
     * @param direction to set the rails to
     */
    @Deprecated
    public void setRailsTo(Direction direction) {
        setRailsTo(findJunction(direction));
    }

    /* ===================================================================================== */

    /**
     * Gets a list of valid junctions that can be taken on the rails block of this sign
     * 
     * @return junctions
     */
    public List<RailJunction> getJunctions() {
        Block railBlock = this.getRails();
        if (railBlock == null) {
            return Collections.emptyList();
        } else {
            return RailType.getType(railBlock).getJunctions(railBlock);
        }
    }

    /**
     * Attempts to find a junction of the rails block belonging to this sign event by name
     * 
     * @param junctionName
     * @return junction, null if not found
     */
    public RailJunction findJunction(String junctionName) {
        // Match the junction by name exactly
        for (RailJunction junc : getJunctions()) {
            if (junc.name().equals(junctionName)) {
                return junc;
            }
        }

        // Attempt parsing the junctionName into a Direction statement
        // This includes special handling for continue/reverse, which uses cart direction
        final String dirText = junctionName.toLowerCase(Locale.ENGLISH);
        BlockFace enterFace = this.getCartEnterFace();
        if (LogicUtil.contains(dirText, "c", "continue")) {
            return findJunction(Direction.fromFace(enterFace));
        } else if (LogicUtil.contains(dirText, "i", "rev", "reverse", "inverse")) {
            return findJunction(Direction.fromFace(enterFace.getOppositeFace()));
        } else {
            return findJunction(Direction.parse(dirText));
        }
    }

    /**
     * Attempts to find a junction of the rails block belonging to this sign event by face direction
     * 
     * @param face
     * @return junction, null if not found
     */
    public RailJunction findJunction(BlockFace face) {
        return Util.faceToJunction(getJunctions(), face);
    }

    /**
     * Attempts to find a junction of the rails block belonging to this sign event by a
     * Direction statement. This also handles logic such as sign-relative left, right and forward.
     * 
     * @param direction
     * @return junction, null if not found
     */
    public RailJunction findJunction(Direction direction) {
        if (direction == Direction.NONE || direction == null) {
            return null;
        }
        BlockFace to = direction.getDirection(this.getFacing());
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            if (!this.isConnectedRails(to)) {
                to = Direction.FORWARD.getDirection(this.getFacing());
            }
        }
        return findJunction(to);
    }

    /**
     * Gets the rail junction from which the rails of this sign were entered.
     * This is used when switching rails to select the 'from' junction.
     * 
     * @return rail junction
     */
    public RailJunction getEnterJunction() {
        if (this.hasMember()) {
            // Find the rails block matching the one that triggered this event
            // Return the enter ('from') direction for that rails block if found
            TrackedRail memberRail = null;
            if (this.hasRails()) {
                Block rails = this.getRails();
                for (TrackedRail rail : this.member.getGroup().getRailTracker().getRailInformation()) {
                    if (rail.member == this.member && rail.state.railBlock().equals(rails)) {
                        memberRail = rail;
                        break;
                    }
                }
            }

            // Ask the minecart itself alternatively
            if (memberRail == null) {
                memberRail = this.member.getRailTracker().getRail();
            }

            // Compute the position at the start of the rail's path by walking 'back'
            RailPath.Position pos = RailPath.Position.fromPosDir(memberRail.state.enterPosition(), memberRail.state.enterDirection());

            // Find the junction closest to this start position
            double min_dist = Double.MAX_VALUE;
            RailJunction best_junc = null;
            for (RailJunction junc : memberRail.state.railType().getJunctions(memberRail.state.railBlock())) {
                if (junc.position().relative) {
                    pos.makeRelative(memberRail.state.railBlock());
                } else {
                    pos.makeAbsolute(memberRail.state.railBlock());
                }
                double dist_sq = junc.position().distanceSquared(pos);
                if (dist_sq < min_dist) {
                    min_dist = dist_sq;
                    best_junc = junc;
                }
            }
            return best_junc;
        }

        //TODO: Do we NEED a fallback?
        return null;
    }

    public void setRailsTo(String toJunctionName) {
        setRailsFromTo(getEnterJunction(), findJunction(toJunctionName));
    }

    public void setRailsTo(RailJunction toJunction) {
        setRailsFromTo(getEnterJunction(), toJunction);
    }

    public void setRailsFromTo(String fromJunctionName, String toJunctionName) {
        setRailsFromTo(findJunction(fromJunctionName), findJunction(toJunctionName));
    }

    public void setRailsFromTo(RailJunction fromJunction, String toJunctionName) {
        setRailsFromTo(fromJunction, findJunction(toJunctionName));
    }

    public void setRailsFromTo(RailJunction fromJunction, RailJunction toJunction) {
        if (!this.hasRails() || fromJunction == null || toJunction == null) {
            return;
        }

        // If from and to are the same, the train is launched back towards where it came
        // In this special case, select another junction part of the path as the from
        // and launch the train backwards
        if (fromJunction.name().equals(toJunction.name())) {
            // Pick any other junction that is not equal to 'to'
            // Prefer junctions that have already been selected (assert from rail path)
            RailState state = RailState.getSpawnState(this.rail);
            RailPath path = state.loadRailLogic().getPath();
            RailPath.Position p0 = path.getStartPosition();
            RailPath.Position p1 = path.getEndPosition();
            double min_dist = Double.MAX_VALUE;
            for (RailJunction junc : this.rail.getJunctions()) {
                if (junc.name().equals(fromJunction.name())) {
                    continue;
                }
                if (junc.position().relative) {
                    p0.makeRelative(this.rail.block());
                    p1.makeRelative(this.rail.block());
                } else {
                    p0.makeAbsolute(this.rail.block());
                    p1.makeAbsolute(this.rail.block());
                }
                double dist_sq = Math.min(p0.distanceSquared(junc.position()),
                                          p1.distanceSquared(junc.position()));
                if (dist_sq < min_dist) {
                    min_dist = dist_sq;
                    fromJunction = junc;
                }
            }

            // Switch it
            this.rail.switchJunction(fromJunction, toJunction);

            // Launch train into the opposite direction, if required
            if (this.hasMember()) {
                // Break this cart from the train if needed
                MinecartGroup group = this.member.getGroup();
                if (group != null) {
                    group.getActions().clear();
                    group.split(this.member.getIndex());
                }
                group = this.member.getGroup();
                if (group != null) {
                    group.reverse();
                }
            }

            return;
        }

        // All the switching logic under normal conditions happens here
        this.rail.switchJunction(fromJunction, toJunction);
    }

    /**
     * Gets the action represented by this event
     *
     * @return Event action type
     */
    public SignActionType getAction() {
        return this.actionType;
    }

    /**
     * Sets the action represented by this event
     *
     * @param type to set to
     * @return This sign action event
     */
    public SignActionEvent setAction(SignActionType type) {
        this.actionType = type;
        return this;
    }

    /**
     * Checks whether one of the types specified equal the action of this event
     *
     * @param types to check against
     * @return True if one of the types is the action, False if not
     */
    public boolean isAction(SignActionType... types) {
        return LogicUtil.contains(this.actionType, types);
    }

    /**
     * Checks whether a rails with a minecart on it is available above this sign
     *
     * @return True if available, False if not
     */
    public boolean hasRailedMember() {
        return this.hasRails() && this.hasMember();
    }

    /**
     * Obtains the header of this sign containing relevant properties that are contained
     * on the first line of a TrainCarts sign.
     * 
     * @return sign header
     */
    public SignActionHeader getHeader() {
        return this.header;
    }

    /**
     * Checks whether power reading is inverted for this Sign<br>
     * <br>
     * <b>Deprecated:</b> use the properties in {@link #getHeader()} instead
     *
     * @return True if it is inverted, False if not
     */
    @Deprecated
    public boolean isPowerInverted() {
        return getHeader().isInverted();
    }

    /**
     * Checks whether power reading always returns on for this Sign<br>
     * <br>
     * <b>Deprecated:</b> use {@link #isPowerMode(mode)} instead
     *
     * @return True if the power is always on, False if not
     */
    @Deprecated
    public boolean isPowerAlwaysOn() {
        return getHeader().isAlwaysOn();
    }

    public PowerState getPower(BlockFace from) {
        return PowerState.get(this.signblock, from);
    }

    public boolean isPowered(BlockFace from) {
        if (this.header.isAlwaysOff()) {
            return false;
        }
        return this.header.isAlwaysOn() || this.header.isInverted() != this.getPower(from).hasPower();
    }

    /**
     * Gets whether this Sign is powered according to the sign rules.
     * <ul>
     * <li>If this is a REDSTONE_ON event, true is returned</li>
     * <li>If this is a REDSTONE_OFF event, false is returned</li>
     * <li>If the sign header indicates always-on, true is returned all the time</li>
     * <li>If the sign header indicates power inversion, true is returned when no Redstone power exists</li>
     * <li>For other cases (default), true is returned when Redstone power exists to this sign</li>
     * </ul>
     * 
     * @return True if the sign is powered, False if not
     */
    public boolean isPowered() {
        if (this.header.isAlwaysOff()) {
            return false;
        }
        if (this.actionType == SignActionType.REDSTONE_ON) {
            return true;
        }
        if (this.actionType == SignActionType.REDSTONE_OFF) {
            return false;
        }
        return this.header.isAlwaysOn() || this.isPoweredRaw(this.header.isInverted());
    }

    /**
     * Checks if this sign is powered, ignoring settings on the sign.<br>
     * <br>
     * <b>Deprecated:</b> Use {@link PowerState#isSignPowered(signBlock, inverted)} instead
     *
     * @param invert True to invert the power as a result, False to get the normal result
     * @return True if powered when not inverted, or not powered and inverted
     */
    @Deprecated
    public boolean isPoweredRaw(boolean invert) {
        return PowerState.isSignPowered(this.signblock, invert);
    }

    public boolean isPoweredFacing() {
        return this.actionType == SignActionType.REDSTONE_ON || (this.isFacing() && this.isPowered());
    }

    public Block getBlock() {
        return this.signblock;
    }

    public Block getAttachedBlock() {
        return BlockUtil.getAttachedBlock(this.signblock);
    }

    public RailPiece getRailPiece() {
        if (this.rail == null) {
            this.rail = RailSignCache.getRailsFromSign(this.signblock);
        }
        return this.rail;
    }

    public RailType getRailType() {
        return getRailPiece().type();
    }

    public Block getRails() {
        return getRailPiece().block();
    }

    public World getWorld() {
        return this.signblock.getWorld();
    }

    public boolean hasRails() {
        return !this.getRailPiece().isNone();
    }

    /**
     * <b>Deprecated: BlockFace offers too little resolution</b>
     * 
     * @return direction of the rails (BlockFace), for example, 'north-east' or 'up'
     */
    @Deprecated
    public BlockFace getRailDirection() {
        if (!this.hasRails()) return null;
        if (this.raildirection == null) {
            this.raildirection = this.rail.type().getDirection(this.rail.block());
        }
        return this.raildirection;
    }

    /**
     * Gets the center location of the rails where the minecart is centered at the rails
     *
     * @return Center location
     */
    public Location getCenterLocation() {
        RailPiece railPiece = this.getRailPiece();
        if (railPiece.isNone()) return null;
        return railPiece.type().getSpawnLocation(railPiece.block(), this.getFacing());
    }

    /**
     * Gets the Location of the rails
     *
     * @return Rail location, or null if there are no rails
     */
    public Location getRailLocation() {
        if (!this.hasRails()) return null;
        return this.rail.block().getLocation().add(0.5, 0, 0.5);
    }

    public Location getLocation() {
        return this.signblock.getLocation();
    }

    public BlockFace getFacing() {
        if (this.facing == null) {
            this.facing = BlockUtil.getFacing(this.signblock);
        }
        return this.facing;
    }

    /**
     * Checks whether the minecart that caused this event is facing the sign correctly
     *
     * @return True if the minecart is able to invoke this sign, False if not
     */
    public boolean isFacing() {
        MinecartMember<?> member = this.getMember();
        if (member == null) {
            return false;
        }
        if (!member.isMoving()) {
            return true;
        }
        return this.isWatchedDirection(this.getCartEnterFace());
    }

    /**
     * Gets the sign associated with this sign action event
     *
     * @return Sign
     */
    public Sign getSign() {
        return this.sign;
    }

    /**
     * Finds all signs below this sign that can extend the amount of lines
     *
     * @return Signs below this sign
     */
    public Sign[] findSignsBelow() {
        ArrayList<Sign> below = new ArrayList<>(1);
        //other signs below this sign we could parse?
        Block signblock = this.getBlock();
        while (MaterialUtil.ISSIGN.get(signblock = signblock.getRelative(BlockFace.DOWN))) {
            Sign sign = BlockUtil.getSign(signblock);
            if (sign == null || BlockUtil.getFacing(signblock) != this.getFacing()) {
                break;
            }
            below.add(sign);
        }
        return below.toArray(new Sign[0]);
    }

    /**
     * Checks if rails at the offset specified are connected to the rails at this sign
     *
     * @param direction to connect to
     * @return True if connected, False if not
     */
    public boolean isConnectedRails(BlockFace direction) {
        return Util.isConnectedRails(this.getRailPiece(), direction);
    }

    /**
     * Gets a collection of all Minecart Groups this sign remote controls
     *
     * @return Remotely controlled groups
     */
    public Collection<MinecartGroup> getRCTrainGroups() {
        return MinecartGroup.matchAll(this.getRCName());
    }

    /**
     * Gets a collection of all Minecart Group train properties this sign remotely controls
     *
     * @return Train properties of remotely controlled groups (unmodifiable)
     */
    public Collection<TrainProperties> getRCTrainProperties() {
        return TrainProperties.matchAll(this.getRCName());
    }

    /**
     * Gets the remote-controlled train name format used on this sign
     *
     * @return Remote control name, or null if this is not a RC sign
     */
    public String getRCName() {
        if (this.isRCSign()) {
            return this.header.getRemoteName();
        } else {
            return null;
        }
    }

    /**
     * Gets or finds the minecart associated with this sign right now<br>
     * Will find a possible minecart on rails above this sign
     * if none was specified while creating this event
     *
     * @return Minecart Member
     */
    public MinecartMember<?> getMember() {
        if (this.member == null) {
            if (!this.memberchecked) {
                this.member = this.hasRails() ? MinecartMemberStore.getAt(this.getRailPiece().block()) : null;
                this.memberchecked = true;
            }
            if (this.member == null && this.group != null && !this.group.isEmpty()) {
                if (this.actionType == SignActionType.GROUP_LEAVE) {
                    this.member = this.group.tail();
                } else {
                    // Get the Minecart in the group that contains this sign
                    for (MinecartMember<?> member : this.group) {
                        if (member.getSignTracker().containsSign(this.signblock)) {
                            this.member = member;
                            break;
                        }
                    }
                    // Fallback: use head
                    if (this.member == null) {
                        this.member = this.group.head();
                    }
                }
            }
        }
        if (this.member == null || !this.member.isInteractable()) {
            return null;
        }
        return this.member;
    }

    /**
     * Sets the minecart associated with this event, overriding any previous members and groups
     *
     * @param member to set to
     */
    public void setMember(MinecartMember<?> member) {
        this.member = member;
        this.memberchecked = true;
        this.group = member.getGroup();
    }

    /**
     * Checks whether a minecart is associated with this event
     *
     * @return True if a member is available, False if not
     */
    public boolean hasMember() {
        return this.getMember() != null;
    }

    /**
     * Gets whether the watched directions of this Sign are defined on the first line.
     * If this returns True, user-specified watched directions are used.
     * If this returns False, environment-specific watched directions are used.
     *
     * @return True if defined, False if not
     */
    public boolean isWatchedDirectionsDefined() {
        return this.getHeader().hasDirections();
    }

    /**
     * Gets the directions minecarts have to move to be detected by this sign
     *
     * @return Watched directions
     */
    public BlockFace[] getWatchedDirections() {
        // Lazy initialization here
        if (this.watchedDirections == null) {
            // Find out what directions are watched by this sign
            if (this.header.hasDirections()) {
                // From first line header ([train:left] -> blockface[] for left)
                this.watchedDirections = this.header.getFaces(this.getFacing().getOppositeFace());
            } else {
                // Ask rails, the RailType NONE also handled this function, so no NPE here
                this.watchedDirections = this.getRailPiece().type().getSignTriggerDirections(
                        this.getRailPiece().block(), this.getBlock(), this.getFacing());
            }
        }
        return this.watchedDirections;
    }

    /**
     * Gets an array of possible directions in which spawner and teleporter signs can lay down trains
     * 
     * @return spawn directions
     */
    public BlockFace[] getSpawnDirections() {
        BlockFace[] spawndirs = new BlockFace[this.getWatchedDirections().length];
        for (int i = 0; i < spawndirs.length; i++) {
            spawndirs[i] = this.getWatchedDirections()[i].getOppositeFace();
        }
        return spawndirs;
    }

    /**
     * Checks if a given BlockFace direction is watched by this sign
     *
     * @param direction to check
     * @return True if watched, False otherwise
     */
    public boolean isWatchedDirection(BlockFace direction) {
        return LogicUtil.contains(direction, this.getWatchedDirections());
    }

    /**
     * Checks if a given movement direction is watched by this sign.
     * When this returns true, the sign should be activated.
     * 
     * @param direction to check
     * @return True if watched, False otherwise
     */
    public boolean isWatchedDirection(Vector direction) {
        for (BlockFace watched : this.getWatchedDirections()) {
            if (MathUtil.isHeadingTo(watched, direction)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the Minecart Group that is associated with this event
     *
     * @return Minecart group
     */
    public MinecartGroup getGroup() {
        if (this.group != null) {
            return this.group;
        }
        MinecartMember<?> mm = this.getMember();
        return mm == null ? null : mm.getGroup();
    }

    /**
     * Checks whether a minecart group is associated with this event
     *
     * @return True if a group is available, False if not
     */
    public boolean hasGroup() {
        return this.getGroup() != null;
    }

    /**
     * Gets all the Minecart Members this sign (based on RC/train/cart type) is working on
     *
     * @return all Minecart Members being worked on
     */
    @SuppressWarnings("unchecked")
    public Collection<MinecartMember<?>> getMembers() {
        if (isTrainSign()) {
            return hasGroup() ? getGroup() : Collections.EMPTY_LIST;
        } else if (isCartSign()) {
            return hasMember() ? Collections.singletonList(getMember()) : Collections.EMPTY_LIST;
        } else if (isRCSign()) {
            ArrayList<MinecartMember<?>> members = new ArrayList<>();
            for (MinecartGroup group : getRCTrainGroups()) {
                members.addAll(group);
            }
            return members;
        }
        return Collections.EMPTY_LIST;
    }

    public String getLine(int index) {
        return Util.getCleanLine(this.sign, index);
    }

    public String[] getLines() {
        return Util.cleanSignLines(this.sign.getLines());
    }

    public void setLine(int index, String line) {
        this.sign.setLine(index, line);
        this.sign.update(true);
    }

    /**
     * Gets the sign mode of this TrainCarts sign
     * 
     * @return Sign mode
     */
    public SignActionMode getMode() {
        return this.getHeader().getMode();
    }

    public boolean isCartSign() {
        return this.getHeader().isCart();
    }

    public boolean isTrainSign() {
        return this.getHeader().isTrain();
    }

    public boolean isRCSign() {
        return this.getHeader().isRC();
    }

    /**
     * Checks whether a given line starts with any of the text types specified
     *
     * @param line      number to check, 0 - 3
     * @param texttypes to check against
     * @return True if the line starts with any of the specified types, False if not
     */
    public boolean isLine(int line, String... texttypes) {
        String linetext = this.getLine(line).toLowerCase(Locale.ENGLISH);
        for (String type : texttypes) {
            if (linetext.startsWith(type)) return true;
        }
        return false;
    }

    /**
     * Checks the first line of this sign to see if it starts with one of the sign types specified
     *
     * @param signtypes to check against
     * @return True if the first line starts with any of the types AND the sign has a valid mode, False if not
     */
    public boolean isType(String... signtypes) {
        return getHeader().isValid() && isLine(1, signtypes);
    }

    @Override
    public String toString() {
        String text = "{ block=[" + signblock.getX() + "," + signblock.getY() + "," + signblock.getZ() + "]";
        text += ", action=" + this.actionType;
        text += ", watched=[";
        for (int i = 0; i < this.getWatchedDirections().length; i++) {
            if (i > 0) text += ",";
            text += this.getWatchedDirections()[i].name();
        }
        text += "]";
        if (this.sign == null) {
            text += " }";
        } else {
            text += ", lines=";
            String[] lines = this.getLines();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0 && lines[i].length() > 0) text += " ";
                text += lines[i];
            }
            text += " }";
        }
        return text;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
