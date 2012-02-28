package com.bergerkiller.bukkit.tc.API;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

public class SignActionEvent extends Event implements Cancellable {
	private static final long serialVersionUID = 1L;
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
		super("SignActionEvent");
		this.signblock = signblock;
		this.sign = BlockUtil.getSign(signblock);
		this.mode = SignActionMode.fromSign(this.sign);
		this.railsblock = railsblock;
		this.railschecked = this.railsblock != null;
		if (this.sign == null) {
			this.powerinv = false;
		} else {
			this.powerinv = this.getLine(0).startsWith("[!");
		}
	}

	private final Block signblock;
	private Block railsblock;
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
	private final boolean powerinv;
	private BlockFace fixedPowerDir = null;
	private boolean isfixedpower = false;

	public void setLevers(boolean down) {
		BlockUtil.setLeversAroundBlock(this.getAttachedBlock(), down);
	}
	public void setRails(BlockFace to) {
		BlockUtil.setRails(this.getRails(), this.getFacing(), to);
	}
	public void setRailsRelative(BlockFace direction) {
		BlockFace main = this.getFacing().getOppositeFace();
		setRails(FaceUtil.offset(main, direction));
	}

	public SignActionEvent assumePower(BlockFace from, boolean powered) {
		this.fixedPowerDir = from;
		this.isfixedpower = powered;
		return this;
	}
	
	/**
	 * Sets rail of current event in given direction, coming from direction the minecart is coming from.
	 * This will go straight if trying to go into the direction the cart is coming from.
	 * This function requires a MinecartMember to work!
	 * @param to Absolute direction to go to.
	 */
	public void setRailsFromCart(BlockFace to) {
		if (this.getMember() == null) return;
		BlockFace from = this.member.getDirectionTo().getOppositeFace();
		
		//set the rails
		BlockUtil.setRails(this.getRails(), from, to);
		if (this.member.getDirection().getOppositeFace() == to){
			double force = this.member.getForce();
			this.getGroup().stop();
			this.getGroup().clearActions();
			this.member.addActionLaunch(to, 1, force);
		}
	}
	public void setRailsRelativeFromCart(BlockFace direction) {
		setRailsFromCart(getRelativeFromCart(direction));
	}
	public BlockFace getRelativeFromCart(BlockFace to) {
		if (this.getMember() == null) return to;
		return FaceUtil.offset(this.getMember().getDirection(), to);
	}
	public void setRails(boolean left, boolean right) {
		if (right) {
			setRailsRight();
		} else if (left) {
			setRailsLeft();
		} else {
			setRailsForward();
		}
	}
	public void setRailsLeft() {
		//is a track present at this direction?
		BlockFace main = this.getRelativeFromCart(BlockFace.WEST);
		if (!BlockUtil.isRails(this.getRails().getRelative(main))) {
			main = this.getRelativeFromCart(BlockFace.NORTH);
		}
		//Set it
		this.setRailsFromCart(main);
	}
	public void setRailsRight() {
		//is a track present at this direction?
		BlockFace main = this.getRelativeFromCart(BlockFace.EAST);
		if (!BlockUtil.isRails(this.getRails().getRelative(main))) {
			main = this.getRelativeFromCart(BlockFace.NORTH);
		}
		//Set it
		this.setRailsFromCart(main);
	}
	public void setRailsForward() {
		//is a track present at this direction?
		BlockFace main = this.getRelativeFromCart(BlockFace.NORTH);
		if (!BlockUtil.isRails(this.getRails().getRelative(main))) {
			main = this.getRelativeFromCart(BlockFace.EAST);
			if (!BlockUtil.isRails(this.getRails().getRelative(main))) {
				main = this.getRelativeFromCart(BlockFace.WEST);
			}
		}
		//Set it
		this.setRailsFromCart(main);
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

	public PowerState getPower(BlockFace from) {
		if (this.fixedPowerDir == from) {
			return this.isfixedpower ? PowerState.ON : PowerState.OFF;
		} else {
			return PowerState.get(this.getBlock(), from);
		}
	}
	public boolean isPowered(BlockFace from) {
		return this.powerinv != this.getPower(from).hasPower();
	}
	public boolean isPowered() {
		BlockFace att = BlockUtil.getAttachedFace(this.signblock);
		if (this.signblock.isBlockIndirectlyPowered()) {
			Block attblock = this.signblock.getRelative(att);
			boolean found = false;
			for (BlockFace face : FaceUtil.attachedFaces) {
				if (BlockUtil.isType(attblock.getRelative(face), Material.LEVER)) {
					//check EVERYTHING
					PowerState state;
					for (BlockFace alter : FaceUtil.attachedFacesDown) {
						if (alter == face) continue;
						state = PowerState.get(attblock, alter, false);
						switch (state) {
						case ON : return !this.powerinv;
						}
					}
					found = true;
					break;
				}
			}
			if (!found) return !this.powerinv;
		}
		if (this.powerinv) {
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
		if (!this.railschecked)  {
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
			if (BlockUtil.isRails(this.railsblock)) {
				this.raildirection = BlockUtil.getRails(this.railsblock).getDirection();
			} else if (Util.isPressurePlate(this.railsblock.getTypeId())) {
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
		return getMember().getDirection() != getFacing();
	}
	public Sign getSign() {
		return this.sign;
	}
	
	public MinecartMember getMember() {
		return this.getMember(false);
	}
	public MinecartMember getMember(boolean checkMoving) {
		if (this.member == null) {
			if (!this.memberchecked) {
				this.member = this.hasRails() ? MinecartMember.getAt(this.railsblock, checkMoving) : null;
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
	
	public MinecartGroup getGroup() {
		return this.getGroup(false);
	}
	public MinecartGroup getGroup(boolean checkMoving) {
		if (this.group != null) return this.group;
		MinecartMember mm = this.getMember(checkMoving);
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
