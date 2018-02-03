package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.SignActionHeader;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.RailInfo;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.material.Rails;

import java.util.*;

public class SignActionEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Block signblock;
    private final BlockFace facing;
    private final SignActionHeader header;
    private final Sign sign;
    private BlockFace[] watchedDirections;
    private Block railsblock;
    private SignActionType actionType;
    private BlockFace raildirection = null;
    private MinecartMember<?> member = null;
    private MinecartGroup group = null;
    private boolean memberchecked = false;
    private boolean cancelled = false;
    private boolean railschecked = false;

    public SignActionEvent(Block signblock, MinecartMember<?> member) {
        this(signblock);
        this.member = member;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, Block railsBlock, MinecartMember<?> member) {
        this(signblock, railsBlock);
        this.member = member;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, MinecartGroup group) {
        this(signblock);
        this.group = group;
        this.memberchecked = true;
    }

    public SignActionEvent(Block signblock, Block railsBlock, MinecartGroup group) {
        this(signblock, railsBlock);
        this.group = group;
        this.memberchecked = true;
    }

    public SignActionEvent(final Block signblock) {
        this(signblock, (Block) null);
    }

    public SignActionEvent(final Block signblock, Block railsblock) {
        this(signblock, signblock == null ? null : BlockUtil.getSign(signblock), railsblock);
    }

    public SignActionEvent(final Block signblock, final Sign sign, Block railsblock) {
        this.signblock = signblock;
        this.sign = sign;
        this.railsblock = railsblock;
        this.railschecked = this.railsblock != null;
        if (this.sign == null) {
            // No sign available - set default values and abort
            this.header = SignActionHeader.parse(null);
            this.facing = null;
            this.watchedDirections = FaceUtil.AXIS;
            return;
        } else {
            // Sign available - initialize the sign
            this.header = SignActionHeader.parseFromEvent(this);
            this.facing = BlockUtil.getFacing(this.signblock);
            if (this.header.isLegacyConverted() && this.header.isValid()) {
                this.setLine(0, this.header.toString());
            }
            this.watchedDirections = null;
        }
        this.actionType = SignActionType.NONE;
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
     * Gets the direction a minecart has above the rails of this Sign
     *
     * @return cart direction
     */
    public BlockFace getCartDirection() {
        if (this.hasMember()) {
            // Find the rails block matching the one that triggered this event
            // Return the enter ('from') direction for that rails block if found
            if (this.hasRails()) {
                Block rails = this.getRails();
                for (TrackedRail rail : this.member.getGroup().getRailTracker().getRailInformation()) {
                    if (rail.member == this.member && rail.block.equals(rails)) {
                        return rail.enterFace;
                    }
                }
            }

            // Ask the minecart itself alternatively
            return this.member.getDirectionFrom();
        }
        if (this.hasRails()) {
            return FaceUtil.getFaces(this.getRailDirection())[0];
        }
        if (this.getWatchedDirections().length > 0) {
            return this.getWatchedDirections()[0];
        }
        return this.getFacing().getOppositeFace();
    }

    /**
     * Sets the rails above this sign to connect with the from and to directions<br>
     * If the cart has to be reversed, that is done
     *
     * @param from direction
     * @param to   direction
     */
    public void setRailsFromTo(BlockFace from, BlockFace to) {
        if (this.hasRails()) {
            if (from == to) {
                // Try to find out a better from direction
                for (BlockFace face : FaceUtil.getFaces(this.getRailDirection())) {
                    if (face != to) {
                        from = face;
                        break;
                    }
                }
            }
            BlockUtil.setRails(this.getRails(), from, to);
            if (this.hasMember() && this.member.getDirectionFrom().getOppositeFace() == to) {
                // Break this cart from the train if needed
                this.member.getGroup().split(this.member.getIndex());
                // Launch in the other direction
                double force = this.member.getForce();
                this.getGroup().stop();
                this.getGroup().getActions().clear();
                this.member.getActions().addActionLaunch(to, 1, force);
            }
        }
    }

    /**
     * Sets the rails above this sign to lead from the minecart direction to the direction specified
     *
     * @param to direction
     */
    public void setRailsTo(BlockFace to) {
        setRailsFromTo(getCartDirection().getOppositeFace(), to);
    }

    /**
     * Sets the rails above this sign to lead from the minecart direction into a direction specified<br>
     * Relative directions, like left and right, are relative to the sign direction
     *
     * @param direction to set the rails to
     */
    public void setRailsTo(Direction direction) {
        BlockFace to = direction.getDirection(this.getFacing());
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            if (!this.isConnectedRails(to)) {
                to = Direction.FORWARD.getDirection(this.getFacing());
            }
        }
        this.setRailsTo(to);
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

    public Block getRails() {
        if (!this.railschecked) {
            this.railsblock = Util.getRailsFromSign(this.signblock);
            this.railschecked = true;
        }
        return this.railsblock;
    }

    public World getWorld() {
        return this.signblock.getWorld();
    }

    public boolean hasRails() {
        return this.getRails() != null;
    }

    public BlockFace getRailDirection() {
        if (!this.hasRails()) return null;
        if (this.raildirection == null) {
            this.raildirection = RailType.getType(this.railsblock).getDirection(this.railsblock);
        }
        return this.raildirection;
    }

    /**
     * Gets the center location of the rails where the minecart is centered at the rails
     *
     * @return Center location
     */
    public Location getCenterLocation() {
        if (!this.hasRails()) return null;
        RailType type = RailType.getType(this.railsblock);
        return type.getSpawnLocation(this.railsblock, this.getFacing());
    }

    /**
     * Gets the Location of the rails
     *
     * @return Rail location, or null if there are no rails
     */
    public Location getRailLocation() {
        if (!this.hasRails()) return null;
        return this.railsblock.getLocation().add(0.5, 0, 0.5);
    }

    public Location getLocation() {
        return this.signblock.getLocation();
    }

    public BlockFace getFacing() {
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
        final BlockFace dir = this.isAction(SignActionType.MEMBER_MOVE) ? member.getDirectionTo() : member.getDirectionFrom();
        return this.isWatchedDirection(dir);
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
        if (!this.hasRails()) {
            return false;
        }

        // Move from the current rail minecart position one block into the direction
        // Check if a rail exists there. If there is, check if it points at this rail
        // If so, then there is a rails there!
        RailType currentType = RailType.getType(getRails());
        Block currentPos = currentType.findMinecartPos(getRails());
        Block nextPos = currentType.getNextPos(getRails(), direction);

        // If not null, verify its the general direction we had chosen
        if (nextPos != null && currentPos != null) {
            if (direction.getModX() != 0 && direction.getModX() != (nextPos.getX() - currentPos.getX())) {
                nextPos = null;
            } else if (direction.getModY() != 0 && direction.getModY() != (nextPos.getY() - currentPos.getY())) {
                nextPos = null;
            } else if (direction.getModZ() != 0 && direction.getModZ() != (nextPos.getZ() - currentPos.getZ())) {
                nextPos = null;
            }
        }

        if (nextPos == null) {
            if (currentPos == null) {
                return false;
            } else {
                nextPos = currentPos.getRelative(direction);
            }
        }

        // Find a rails at this offset position
        Block nextRail = null;
        RailInfo nextRailInfo = RailType.findRailInfo(nextPos);
        RailType nextRailType = RailType.NONE;
        if (nextRailInfo != null) {
            nextRailType = nextRailInfo.railType;
            nextRail = nextRailInfo.railBlock;
        }
        if (nextRailType == RailType.NONE) {
            return false;
        }

        // Find out if the direction we came from is one of the possible directions
        return LogicUtil.contains(direction.getOppositeFace(), nextRailType.getPossibleDirections(nextRail));
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
     * @return Train properties of remotely controlled groups
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
            String name = this.getLine(0);
            int idx = name.indexOf(' ') + 1;
            return name.substring(idx, name.length() - 1);
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
                this.member = this.hasRails() ? MinecartMemberStore.getAt(this.railsblock) : null;
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
            HashSet<BlockFace> watchedFaces = new HashSet<>(4);
            // Find out what directions are watched by this sign
            if (!this.header.hasDirections()) {
                // find out using the rails above and sign facing
                if (this.hasRails()) {
                    if (FaceUtil.isVertical(this.getRailDirection())) {
                        watchedFaces.add(BlockFace.UP);
                        watchedFaces.add(BlockFace.DOWN);
                    } else {
                        if (FaceUtil.isSubCardinal(this.getFacing())) {
                            // More advanced corner checks - NE/SE/SW/NW
                            // Use rail directions validated against sign facing to
                            // find out what directions are watched
                            BlockFace[] faces = FaceUtil.getFaces(this.getFacing());
                            for (BlockFace face : faces) {
                                if (this.isConnectedRails(face)) {
                                    watchedFaces.add(face.getOppositeFace());
                                }
                            }
                            // Try an inversed version, maybe rails can be found there
                            if (watchedFaces.isEmpty()) {
                                for (BlockFace face : faces) {
                                    if (this.isConnectedRails(face.getOppositeFace())) {
                                        watchedFaces.add(face);
                                    }
                                }
                            }
                        } else {
                            // Sloped rails also include UP/DOWN, handling from/to vertical rail movement
                            Rails rails = BlockUtil.getRails(this.getRails());
                            if (rails != null && rails.isOnSlope()) {
                                watchedFaces.add(BlockFace.UP);
                                watchedFaces.add(BlockFace.DOWN);
                            }

                            // Simple facing checks - NESW
                            if (this.isConnectedRails(facing)) {
                                watchedFaces.add(facing.getOppositeFace());
                            } else if (this.isConnectedRails(facing.getOppositeFace())) {
                                watchedFaces.add(facing);
                            } else {
                                watchedFaces.add(FaceUtil.rotate(facing, -2));
                                watchedFaces.add(FaceUtil.rotate(facing, 2));
                            }
                        }
                    }
                }
            } else {
                watchedFaces.addAll(Arrays.asList(this.header.getFaces(this.getFacing().getOppositeFace())));
            }
            // Apply watched faces
            if (watchedFaces.isEmpty()) {
                watchedFaces.add(this.getFacing().getOppositeFace());
            }
            this.watchedDirections = watchedFaces.toArray(new BlockFace[0]);
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
     * Checks if a given direction is watched by this sign
     *
     * @param direction to check
     * @return True if watched, False otherwise
     */
    public boolean isWatchedDirection(BlockFace direction) {
        return LogicUtil.contains(direction, this.getWatchedDirections());
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
