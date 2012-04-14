package com.bergerkiller.bukkit.tc.events;

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
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

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
	
	public void setRailsFromTo(BlockFace from, BlockFace to) {
		if (!this.hasMember()) return;
		BlockUtil.setRails(this.getRails(), from, to);
		if (this.member.getDirection().getOppositeFace() == to){
			double force = this.member.getForce();
			this.getGroup().stop();
			this.getGroup().clearActions();
			this.member.addActionLaunch(to, 1, force);
		}
	}
	
	public BlockFace getCartDirection() {
		if (!this.hasMember()) return BlockFace.NORTH;
		return this.member.getDirectionTo();
	}
	
	/**
	 * Sets rail of current event in given direction, coming from direction the minecart is coming from.
	 * This will go straight if trying to go into the direction the cart is coming from.
	 * This function requires a MinecartMember to work!
	 * @param to Absolute direction to go to.
	 */
	public void setRailsFromCart(BlockFace to) {
		if (!this.hasMember()) return;
		BlockFace from = this.member.getDirectionTo().getOppositeFace();
		//set the rails
		this.setRailsFromTo(from, to);
	}
	public void setRails(BlockFace from, boolean left, boolean right) {
		if (right) {
			setRailsRight(from);
		} else if (left) {
			setRailsLeft(from);
		} else {
			setRailsForward(from);
		}
	}
	public void setRailsLeft(BlockFace from) {
		//is a track present at this direction?
		BlockFace main = FaceUtil.offset(from.getOppositeFace(), BlockFace.WEST);
		if (!Util.isRails(this.getRails().getRelative(main))) {
			main = FaceUtil.offset(from.getOppositeFace(), BlockFace.NORTH);
		}
		//Set it
		this.setRailsFromTo(from, main);
	}
	public void setRailsRight(BlockFace from) {
		//is a track present at this direction?
		BlockFace main = FaceUtil.offset(from.getOppositeFace(), BlockFace.EAST);
		if (!Util.isRails(this.getRails().getRelative(main))) {
			main = FaceUtil.offset(from.getOppositeFace(), BlockFace.NORTH);
		}
		//Set it
		this.setRailsFromTo(from, main);
	}
	public void setRailsForward(BlockFace from) {
		//is a track present at this direction?
		BlockFace main = FaceUtil.offset(from.getOppositeFace(), BlockFace.NORTH);
		if (!Util.isRails(this.getRails().getRelative(main))) {
			main = FaceUtil.offset(from.getOppositeFace(), BlockFace.EAST);
			if (!BlockUtil.isRails(this.getRails().getRelative(main))) {
				main = FaceUtil.offset(from.getOppositeFace(), BlockFace.WEST);
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

	public PowerState getPower(BlockFace from) {
		if (this.fixedPowerDir == from) {
			return this.isfixedpower ? PowerState.ON : PowerState.OFF;
		} else {
			return PowerState.get(this.signblock, from);
		}
	}
	public boolean isPowered(BlockFace from) {
		return this.powerinv != this.getPower(from).hasPower();
	}
	public boolean isPowered() {
		BlockFace att = BlockUtil.getAttachedFace(this.signblock);
		Block attblock = this.signblock.getRelative(att);
		if (attblock.isBlockPowered()) {
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
	
	public MinecartGroup getRCTrainGroup() {
		return MinecartGroup.get(this.getRCTrainName());
	}
	public TrainProperties getRCTrainProperties() {
		return TrainProperties.get(this.getRCTrainName());
	}
	public String getRCTrainName() {
		if (this.isRCSign()) {
			String name = this.getLine(0);
			int idx = name.indexOf(' ') + 1;
			return name.substring(idx, name.length() - 1);
		} else {
			return null;
		}
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
