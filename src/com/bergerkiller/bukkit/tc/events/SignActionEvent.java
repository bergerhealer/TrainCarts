package com.bergerkiller.bukkit.tc.events;

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
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

public class SignActionEvent extends Event implements Cancellable {
	private static final HandlerList handlers = new HandlerList();
	public HandlerList getHandlers() {
		return handlers;
	}
	public static HandlerList getHandlerList() {
		return handlers;
	}

	public SignActionEvent(Block signblock, MinecartMember member) {
		this(signblock, member.isDerailed() ? null : member.getRailsBlock());
		this.member = member;
		this.memberchecked = true;
	}
	public SignActionEvent(Block signblock, Block railsblock, MinecartGroup group) {
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
		this.railschecked = this.railsblock != null;
		this.verticalRail = Util.ISVERTRAIL.get(this.railsblock);
		if (this.sign == null) {
			this.powerinv = false;
			this.poweron = false;
			this.watchedDirections = FaceUtil.axis;
		} else {
			String linez = this.getLine(0);
			this.poweron = linez.startsWith("[+");
			this.powerinv = linez.startsWith("[!");
			int idx = linez.indexOf(':');
			if (idx == -1) {
				//find out using the rails above and sign facing
				if (this.hasRails()) {
					BlockFace facing = this.getFacing();
					if (this.isConnectedRails(facing)) {
						this.watchedDirections = new BlockFace[] {facing.getOppositeFace()};
					} else if (this.isConnectedRails(facing.getOppositeFace())) {
						this.watchedDirections = new BlockFace[] {facing};
					} else {
						this.watchedDirections = new BlockFace[] {FaceUtil.rotate(facing, -2), FaceUtil.rotate(facing, 2)};
					}
				} else {
					this.watchedDirections = new BlockFace[] {this.getFacing().getOppositeFace()};
				}
			} else {
				linez = linez.substring(idx + 1);
				Direction dir = Direction.parse(linez);
				if (dir == Direction.NONE) {
					HashSet<BlockFace> faces = new HashSet<BlockFace>(linez.length());
					for (char c : linez.toCharArray()) {
						dir = Direction.parse(c);
						if (dir != Direction.NONE) {
							faces.add(dir.getDirection(this.getFacing()).getOppositeFace());
						}
					}
					if (faces.isEmpty()) {
						this.watchedDirections = new BlockFace[] {this.getFacing().getOppositeFace()};
					} else {
						this.watchedDirections = faces.toArray(new BlockFace[0]);
					}
				} else {
					this.watchedDirections = new BlockFace[] {dir.getDirection(this.getFacing().getOppositeFace())};
				}
			}
		}
	}

	private final Block signblock;
	private Block railsblock;
	private boolean verticalRail;
	private final SignActionMode mode;
	private SignActionType actionType;
	private BlockFace facing = null;
	private final Sign sign;
	private BlockFace raildirection = null;
	private MinecartMember member = null;
	private MinecartGroup group = null;
	private boolean memberchecked = false;
	private boolean cancelled = false;
	private boolean railschecked = false;
	private final BlockFace[] watchedDirections;
	private final boolean powerinv;
	private final boolean poweron;

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
		if (this.hasMember() && this.member.isMoving()) {
			return this.member.getDirectionTo();
		}
		return this.getFacing().getOppositeFace();
	}

	/**
	 * Sets the rails direction above this sign
	 * 
	 * @param from direction
	 * @param to direction
	 */
	public void setRails(BlockFace from, BlockFace to) {
		BlockUtil.setRails(this.getRails(), from, to);
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
			setRails(from, to);
			if (this.hasMember() && this.member.getDirection().getOppositeFace() == to) {
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
		setRails(getCartDirection().getOppositeFace(), to);
	}

	/**
	 * Sets the rails above this sign to lead from the minecart direction into a direction specified<br>
	 * Left, right and forward are handled separately from setRailsTo!
	 * 
	 * @param direction to set the rails to
	 */
	public void setRailsTo(Direction direction) {
		switch (direction) {
			case LEFT :
				setRailsLeft();
				break;
			case RIGHT :
				setRailsRight();
				break;
			case FORWARD :
				setRailsForward();
				break;
			default :
				this.setRailsTo(direction.getDirection(this.getFacing()));
				break;
		}
	}

	public void setRailsLeft() {
		BlockFace from = this.getCartDirection().getOppositeFace();
		//is a track present at this direction?
		BlockFace main = FaceUtil.add(from.getOppositeFace(), BlockFace.WEST);
		if (!Util.ISTCRAIL.get(this.getRails().getRelative(main))) {
			main = FaceUtil.add(from.getOppositeFace(), BlockFace.NORTH);
		}
		//Set it
		this.setRailsFromTo(from, main);
	}

	public void setRailsRight() {
		BlockFace from = this.getCartDirection().getOppositeFace();
		//is a track present at this direction?
		BlockFace main = FaceUtil.add(from.getOppositeFace(), BlockFace.EAST);
		if (!Util.ISTCRAIL.get(this.getRails().getRelative(main))) {
			main = FaceUtil.add(from.getOppositeFace(), BlockFace.NORTH);
		}
		//Set it
		this.setRailsFromTo(from, main);
	}

	public void setRailsForward() {
		BlockFace from = this.getCartDirection().getOppositeFace();
		//is a track present at this direction?
		BlockFace main = FaceUtil.add(from.getOppositeFace(), BlockFace.NORTH);
		if (!Util.ISTCRAIL.get(this.getRails().getRelative(main))) {
			main = FaceUtil.add(from.getOppositeFace(), BlockFace.EAST);
			if (!MaterialUtil.ISRAILS.get(this.getRails().getRelative(main))) {
				main = FaceUtil.add(from.getOppositeFace(), BlockFace.WEST);
			}
		}
		//Set it
		this.setRailsFromTo(from, main);
	}

	public SignActionType getAction() {
		return this.actionType;
	}
	public boolean isAction(SignActionType... types) {
		for (SignActionType type : types) {
			if (this.actionType == type) return true;
		}
		return false;
	}
	public SignActionEvent setAction(SignActionType type) {
		this.actionType = type;
		return this;
	}

	public boolean hasRailedMember() {
		return this.hasRails() && this.hasMember();
	}

	public boolean isPowerInverted() {
		return this.powerinv;
	}

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
			for (BlockFace face : FaceUtil.attachedFaces) {
				if (BlockUtil.isType(attblock.getRelative(face), Material.LEVER)) {
					//check EVERYTHING
					for (BlockFace alter : FaceUtil.attachedFacesDown) {
						if (alter == face) continue;
						if (PowerState.get(attblock, alter, false) == PowerState.ON) {
							return !invert;
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
			for (BlockFace face : FaceUtil.attachedFacesDown) {
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
			for (BlockFace face : FaceUtil.attachedFacesDown) {
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
		if (this.facing == null) {
			this.facing = BlockUtil.getFacing(this.getBlock());
		}
		return this.facing;
	}
	public boolean isFacing() {
		if (!this.hasMember()) return false;
		if (!getMember().isMoving()) return true;
		return this.isWatchedDirection(this.getMember().getDirectionTo());
	}
	public Sign getSign() {
		return this.sign;
	}

	/**
	 * Checks if rails at the offset specified are connected to the rails at this sign
	 * 
	 * @param direction to connect to
	 * @return True if connected, False if not
	 */
	public boolean isConnectedRails(BlockFace direction) {
		Block block = Util.getRailsBlock(this.railsblock.getRelative(direction));
		if (block != null) {
			Rails rails = BlockUtil.getRails(block);
			if (rails == null) return true; //pressure plate
			direction = direction.getOppositeFace();
			for (BlockFace dir : FaceUtil.getFaces(rails.getDirection())) {
				if (dir == direction) {
					return true;
				}
			}
		}
		return false;
	}

	public Collection<MinecartGroup> getRCTrainGroups() {
		return MinecartGroup.matchAll(this.getRCName());
	}
	public Collection<TrainProperties> getRCTrainProperties() {
		return TrainProperties.matchAll(this.getRCName());
	}
	public String getRCName() {
		if (this.isRCSign()) {
			String name = this.getLine(0);
			int idx = name.indexOf(' ') + 1;
			return name.substring(idx, name.length() - 1);
		} else {
			return null;
		}
	}

	public MinecartMember getMember() {
		if (this.member == null) {
			if (!this.memberchecked) {
				this.member = this.hasRails() ? MinecartMember.getAt(this.railsblock) : null;
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
		if (this.member == null || this.member.dead) return null; 
		return this.member;
	}
	public boolean hasMember() {
		return this.getMember() != null;
	}

	public void setMember(MinecartMember member) {
		this.member = member;
		this.group = member.getGroup();
	}

	public BlockFace[] getWatchedDirections() {
		return this.watchedDirections;
	}
	public boolean isWatchedDirection(BlockFace direction) {
		for (BlockFace face : this.watchedDirections) {
			if (face == direction) return true;
		}
		return false;
	}

	public MinecartGroup getGroup() {
		if (this.group != null) return this.group;
		MinecartMember mm = this.getMember();
		if (mm == null) return null;
		return mm.getGroup();
	}
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
	public boolean isLine(int line, String... texttypes) {
		String linetext = this.getLine(line).toLowerCase();
		for (String type : texttypes) {
			if (linetext.startsWith(type)) return true;
		}
		return false;
	}
	public boolean isType(String... signtypes) {
		return isLine(1, signtypes);
	}

	public boolean isCancelled() {
		return this.cancelled;
	}

	public void setCancelled(boolean arg0) {
		this.cancelled = arg0;	
	}
}
