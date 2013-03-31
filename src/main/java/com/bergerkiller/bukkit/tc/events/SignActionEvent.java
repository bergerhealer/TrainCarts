package com.bergerkiller.bukkit.tc.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

public class SignActionEvent extends Event implements Cancellable {
	private static final HandlerList handlers = new HandlerList();
	private final Block signblock;
	private Block railsblock;
	private boolean verticalRail;
	private final SignActionMode mode;
	private SignActionType actionType;
	private final BlockFace facing;
	private final Sign sign;
	private BlockFace raildirection = null;
	private MinecartMember<?> member = null;
	private MinecartGroup group = null;
	private boolean memberchecked = false;
	private boolean cancelled = false;
	private boolean railschecked = false;
	private final BlockFace[] watchedDirections;
	private final boolean powerinv;
	private final boolean poweron;

	public SignActionEvent(Block signblock, MinecartMember<?> member) {
		this(signblock, member.isDerailed() ? null : member.getBlock());
		this.member = member;
		this.memberchecked = true;
	}
	public SignActionEvent(Block signblock, MinecartGroup group) {
		this(signblock);
		this.group = group;
		this.memberchecked = true;
	}
	public SignActionEvent(final Block signblock) {
		this(signblock, (Block) null);
	}
	public SignActionEvent(final Block signblock, Block railsblock) {
		this(signblock, BlockUtil.getSign(signblock), railsblock);
	}
	public SignActionEvent(final Block signblock, final Sign sign, Block railsblock) {
		this.signblock = signblock;
		this.sign = sign;
		this.mode = SignActionMode.fromSign(this.sign);
		this.railsblock = railsblock;
		this.railschecked = this.railsblock != null;
		this.verticalRail = Util.ISVERTRAIL.get(this.railsblock);
		String mainLine;
		if (this.sign == null) {
			// No sign available - set default values and abort
			this.powerinv = false;
			this.poweron = false;
			this.facing = null;
			this.watchedDirections = FaceUtil.AXIS;
			return;
		} else {
			// Sign available - initialize the sign
			mainLine = this.getLine(0);
			this.poweron = mainLine.startsWith("[+");
			this.powerinv = mainLine.startsWith("[!");
			this.facing = BlockUtil.getFacing(this.signblock);
		}
		HashSet<BlockFace> watchedFaces = new HashSet<BlockFace>(4);
		// Find out what directions are watched by this sign
		int idx = mainLine.indexOf(':');
		if (idx == -1) {
			// find out using the rails above and sign facing
			if (this.hasRails()) {
				if (this.isVerticalRails()) {
					watchedFaces.add(BlockFace.UP);
					watchedFaces.add(BlockFace.DOWN);
				} else {
					Rails rails = BlockUtil.getRails(this.getRails());
					if (rails != null && rails.isOnSlope() && Util.isVerticalAbove(this.getRails(), rails.getDirection())) {
						watchedFaces.add(BlockFace.UP);
						watchedFaces.add(rails.getDirection().getOppositeFace());
					} else if (FaceUtil.isSubCardinal(this.getFacing())) {
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
			} else {
				watchedFaces.add(facing.getOppositeFace());
			}
		} else {
			// Find out by parsing the main line
			mainLine = mainLine.substring(idx + 1);
			Direction dir = Direction.parse(mainLine);
			if (dir == Direction.NONE) {
				for (char c : mainLine.toCharArray()) {
					dir = Direction.parse(c);
					if (dir != Direction.NONE) {
						watchedFaces.add(dir.getDirection(this.getFacing()).getOppositeFace());
					}
				}
			} else {
				watchedFaces.add(dir.getDirection(this.getFacing().getOppositeFace()));
			}
		}
		// Apply watched faces
		if (watchedFaces.isEmpty()) {
			watchedFaces.add(this.getFacing().getOppositeFace());
		}
		this.watchedDirections = watchedFaces.toArray(new BlockFace[0]);
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
			return this.member.getDirectionFrom();
		}
		if (this.hasRails()) {
			return FaceUtil.getFaces(this.getRailDirection())[0];
		}
		if (this.watchedDirections.length > 0) {
			return this.watchedDirections[0];
		}
		return this.getFacing().getOppositeFace();
	}

	/**
	 * Sets the rails above this sign to connect with the from and to directions<br>
	 * If the cart has to be reversed, that is done
	 * 
	 * @param from direction
	 * @param to direction
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
				this.getGroup().clearActions();
				this.member.addActionLaunch(to, 1, force);
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
		this.setRailsTo(direction.getDirection(this.getFacing()));
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
	 * Checks whether power reading is inverted for this Sign
	 * 
	 * @return True if it is inverted, False if not
	 */
	public boolean isPowerInverted() {
		return this.powerinv;
	}

	/**
	 * Checks whether power reading always returns on for this Sign
	 * 
	 * @return True if the power is always on, False if not
	 */
	public boolean isPowerAlwaysOn() {
		return this.poweron;
	}

	public PowerState getPower(BlockFace from) {
		return PowerState.get(this.signblock, from);
	}
	public boolean isPowered(BlockFace from) {
		return this.poweron || this.powerinv != this.getPower(from).hasPower();
	}
	public boolean isPowered() {
		if (this.poweron) {
			return true;
		}
		return this.isPoweredRaw(this.powerinv);
	}

	/**
	 * Checks if this sign is powered, ignoring settings on the sign
	 * @param invert True to invert the power as a result, False to get the normal result
	 * @return True if powered when not inverted, or not powered and inverted
	 */
	public boolean isPoweredRaw(boolean invert) {
		BlockFace att = BlockUtil.getAttachedFace(this.signblock);
		Block attblock = this.signblock.getRelative(att);
		if (attblock.isBlockPowered()) {
			boolean found = false;
			for (BlockFace face : FaceUtil.ATTACHEDFACES) {
				if (BlockUtil.isType(attblock.getRelative(face), Material.LEVER)) {
					//check EVERYTHING
					for (BlockFace alter : FaceUtil.ATTACHEDFACESDOWN) {
						if (alter != face) {
							if (PowerState.get(attblock, alter, false) == PowerState.ON) {
								return !invert;
							}
						}
					}
					found = true;
					break;
				}
			}
			if (!found) return !invert;
		}
		if (invert) {
			boolean def = true;
			for (BlockFace face : FaceUtil.ATTACHEDFACESDOWN) {
				if (face == att) continue;
				PowerState state = this.getPower(face);
				switch (state) {
				case NONE : continue;
				case ON : return false; //def = false; continue;
				case OFF : continue; //return true;
				}
			}
			return def;
		} else {
			for (BlockFace face : FaceUtil.ATTACHEDFACESDOWN) {
				if (face == att) continue;
				if (this.getPower(face).hasPower()) return true;
			}
			return false;
		}
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
			this.verticalRail = this.railsblock != null && Util.ISVERTRAIL.get(this.railsblock);
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
	public boolean isVerticalRails() {
		return this.verticalRail;
	}
	public BlockFace getRailDirection() {
		if (!this.hasRails()) return null;
		if (this.raildirection == null) {
			if (MaterialUtil.ISRAILS.get(this.railsblock)) {
				this.raildirection = BlockUtil.getRails(this.railsblock).getDirection();
			} else if (MaterialUtil.ISPRESSUREPLATE.get(this.railsblock)) {
				this.raildirection = Util.getPlateDirection(this.railsblock);
			}
		}
		return this.raildirection;
	}
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
		BlockFace dir = this.isAction(SignActionType.MEMBER_MOVE) ? member.getDirectionTo() : member.getDirectionFrom();
		return !getMember().isMoving() || this.isWatchedDirection(dir);
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
		ArrayList<Sign> below = new ArrayList<Sign>(1);
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
		// Slope upwards? Then offset the rails block
		Block railsBlock = this.railsblock;
		if (MaterialUtil.ISRAILS.get(railsBlock)) {
			Rails rails = BlockUtil.getRails(railsBlock);
			if (rails.isOnSlope() && rails.getDirection() == direction) {
				railsBlock = railsBlock.getRelative(BlockFace.UP);
			}
		}
		// Check the connection
		Block block = Util.getRailsBlock(railsBlock.getRelative(direction));
		if (block != null) {
			int id = block.getTypeId();
			if (MaterialUtil.ISPRESSUREPLATE.get(id)) {
				// Connection if on the same level
				return block.getY() == railsBlock.getY();
			} else if (Util.ISVERTRAIL.get(id)) {
				// Never a vertical connection
				return false;
			} else if (MaterialUtil.ISRAILS.get(id)) {
				Rails rails = BlockUtil.getRails(block);
				if (rails.isOnSlope()) {
					if (block.getY() < railsBlock.getY()) {
						return rails.getDirection() == direction.getOppositeFace();
					} else {
						return rails.getDirection() == direction;
					}
				} else {
					return LogicUtil.contains(direction, FaceUtil.getFaces(rails.getDirection()));
				}
			}
		}
		return false;
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
			if (this.group != null && !this.group.isEmpty()) {
				if (this.actionType == SignActionType.GROUP_LEAVE) {
					this.member = this.group.tail();
				} else {
					this.member = this.group.head();
				}
			}
		}
		if (this.member == null || this.member.getEntity().isDead() || this.member.isUnloaded()) {
			return null; 
		}
		return this.member;
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
	 * Gets the directions minecarts have to move to be detected by this sign
	 * 
	 * @return Watched directions
	 */
	public BlockFace[] getWatchedDirections() {
		return this.watchedDirections;
	}

	/**
	 * Checks if a given direction is watched by this sign
	 * 
	 * @param direction to check
	 * @return True if watched, False otherwise
	 */
	public boolean isWatchedDirection(BlockFace direction) {
		return LogicUtil.contains(direction, this.watchedDirections);
	}

	/**
	 * Gets the Minecart Group that is associated with this event
	 * 
	 * @return Minecart group
	 */
	public MinecartGroup getGroup() {
		if (this.group != null) {
			if (this.group.isEmpty()) {
				this.group = null;
			} else {
				return this.group;
			}
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

	public String getLine(int index) {
		return this.sign.getLine(index);
	}

	public String[] getLines() {
		return this.sign.getLines();
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
		return this.mode;
	}

	public boolean isCartSign() {
		return this.mode == SignActionMode.CART;
	}

	public boolean isTrainSign() {
		return this.mode == SignActionMode.TRAIN;
	}

	public boolean isRCSign() {
		return this.mode == SignActionMode.RCTRAIN;
	}

	/**
	 * Checks whether a given line starts with any of the text types specified
	 * 
	 * @param line number to check, 0 - 3
	 * @param texttypes to check against
	 * @return True if the line starts with any of the specified types, False if not
	 */
	public boolean isLine(int line, String... texttypes) {
		String linetext = this.getLine(line).toLowerCase();
		for (String type : texttypes) {
			if (linetext.startsWith(type)) return true;
		}
		return false;
	}

	/**
	 * Checks the first line of this sign to see if it starts with one of the sign types specified
	 * 
	 * @param signtypes to check against
	 * @return True if the first line starts with any of the types, False if not
	 */
	public boolean isType(String... signtypes) {
		return isLine(1, signtypes);
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

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
