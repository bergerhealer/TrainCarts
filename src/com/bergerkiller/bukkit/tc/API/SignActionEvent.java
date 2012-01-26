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
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.utils.FaceUtil;

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
		this(signblock);
		this.member = member;
		this.memberchecked = true;
	}
	public SignActionEvent(Block signblock, MinecartGroup group) {
		this(signblock);
		this.group = group;
		this.memberchecked = true;
	}
	public SignActionEvent(SignActionType actionType, Block signblock, MinecartMember member) {
		this(actionType, signblock);
		this.member = member;
		this.memberchecked = true;
	}
	public SignActionEvent(SignActionType actionType, Block signblock, MinecartGroup group) {
		this(actionType, signblock);
		this.group = group;
		this.memberchecked = true;
	}	
	public SignActionEvent(SignActionType actionType, Block signblock) {
		this(signblock);
		this.actionType = actionType;
	}
	public SignActionEvent(final Block signblock) {
		this(signblock, BlockUtil.getRailsBlockFromSign(signblock));
	}
	public SignActionEvent(final Block signblock, final Block railsblock) {
		super("SignActionEvent");
		this.signblock = signblock;
		this.sign = BlockUtil.getSign(signblock);
		this.mode = SignActionMode.fromSign(this.sign);
		this.railsblock = railsblock;
	}
	
	private final Block signblock;
	private final Block railsblock;
	private final SignActionMode mode;
	private SignActionType actionType;
	private BlockFace facing = null;
	private final Sign sign;
	private BlockFace raildirection = null;
	private MinecartMember member = null;
	private MinecartGroup group = null;
	private boolean memberchecked = false;
	private boolean cancelled = false;
	
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
	
	/**
	 * Sets rail of current event in given direction, coming from direction the minecart is coming from.
	 * This will go straight if trying to go into the direction the cart is coming from.
	 * This function requires a MinecartMember to work!
	 * @param to Absolute direction to go to.
	 */
	public void setRailsFromCart(BlockFace to) {
		if (this.getMember() == null) return;
		BlockUtil.setRails(this.getRails(), this.member.getDirection().getOppositeFace(), to);
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
	
	public boolean isPowered(BlockFace from) {
		Block block = this.getBlock().getRelative(from);
		Material type = block.getType();
		if (type == Material.REDSTONE_TORCH_ON) return true;
		if (type == Material.REDSTONE_TORCH_OFF) return false;
		if (type == Material.REDSTONE_WIRE) {
			return block.getData() != 0;
		}
		if (from != BlockFace.DOWN && type == Material.DIODE_BLOCK_ON) {
			return BlockUtil.getFacing(block) != from;
		}
		return this.getBlock().isBlockFacePowered(from);
	}
	public boolean isPowered() {
		return this.getBlock().isBlockIndirectlyPowered() ||
				isPowered(BlockFace.DOWN) || 
				isPowered(BlockFace.NORTH) ||
				isPowered(BlockFace.EAST) ||
				isPowered(BlockFace.SOUTH) ||
				isPowered(BlockFace.WEST);
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
		return this.railsblock;
	}
	public World getWorld() {
		return this.signblock.getWorld();
	}
	public boolean hasRails() {
		return this.railsblock != null;
	}
	public BlockFace getRailDirection() {
		if (this.raildirection == null) {
			this.raildirection = BlockUtil.getRails(this.railsblock).getDirection();
		}
		return this.raildirection;
	}
	public Location getRailLocation() {
		if (this.railsblock == null) return null;
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
		if (getMember() == null) return false;
		if (!getMember().isMoving()) return true;
		return getMember().getDirection() != getFacing();
	}
	public Sign getSign() {
		return this.sign;
	}
	public MinecartMember getMember() {
		if (this.member == null) {
			if (!this.memberchecked) {
				this.member = MinecartMember.getAt(this.railsblock);
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
