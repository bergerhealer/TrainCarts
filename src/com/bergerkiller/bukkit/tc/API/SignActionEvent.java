package com.bergerkiller.bukkit.tc.API;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Utils.FaceUtil;

public class SignActionEvent extends Event implements Cancellable {
	private static final long serialVersionUID = -7414386763414357918L;

	public static enum ActionType {REDSTONE_CHANGE, REDSTONE_ON, REDSTONE_OFF, MEMBER_ENTER, MEMBER_MOVE, MEMBER_LEAVE, GROUP_ENTER, GROUP_LEAVE}
	
	public SignActionEvent(Block signblock, MinecartMember member) {
		super("SignActionEvent");
		this.signblock = signblock;
		this.member = member;
		this.memberchecked = true;
	}
	public SignActionEvent(Block signblock, MinecartGroup group) {
		super("SignActionEvent");
		this.signblock = signblock;
		this.group = group;
		this.memberchecked = true;
	}
	public SignActionEvent(Block signblock) {
		super("SignActionEvent");
		this.signblock = signblock;
	}

	public SignActionEvent(ActionType actionType, Block signblock, MinecartMember member) {
		super("SignActionEvent");
		this.signblock = signblock;
		this.member = member;
		this.memberchecked = true;
		this.actionType = actionType;
	}
	public SignActionEvent(ActionType actionType, Block signblock, MinecartGroup group) {
		super("SignActionEvent");
		this.signblock = signblock;
		this.group = group;
		this.memberchecked = true;
		this.actionType = actionType;
	}
	public SignActionEvent(ActionType actionType, Block signblock) {
		super("SignActionEvent");
		this.signblock = signblock;
		this.actionType = actionType;
	}

	private Block signblock;
	private BlockFace facing = null;
	private BlockFace raildirection = null;
	private Sign sign = null;
	private MinecartMember member = null;
	private MinecartGroup group = null;
	private boolean memberchecked = false;
	private boolean cancelled = false;
	private ActionType actionType;
	
	public void setLevers(boolean down) {
		//Toggle the lever if present
		Block main = BlockUtil.getAttachedBlock(getBlock());
		for (Block b : BlockUtil.getRelative(main, FaceUtil.getAttached())) {
			BlockUtil.setLever(b, down);
		}
	}
	public void setRails(BlockFace to) {
		BlockUtil.setRails(this.getRails(), this.getFacing(), to);
	}
	public void setRailsRelative(BlockFace direction) {
		BlockFace main = this.getFacing().getOppositeFace();
		setRails(FaceUtil.offset(main, direction));
	}
	
	public ActionType getAction() {
		return this.actionType;
	}
	public boolean isAction(ActionType... types) {
		for (ActionType type : types) {
			if (this.actionType == type) return true;
		}
		return false;
	}
	public void setAction(ActionType type) {
		this.actionType = type;
	}
	
	public boolean isPowered(BlockFace from) {
		return this.getBlock().getRelative(from).isBlockIndirectlyPowered();
	}
	public boolean isPowered() {
		return this.getBlock().isBlockIndirectlyPowered() ||
				isPowered(BlockFace.NORTH) ||
				isPowered(BlockFace.EAST) ||
				isPowered(BlockFace.SOUTH) ||
				isPowered(BlockFace.WEST);
	}
	public Block getBlock() {
		return this.signblock;
	}
	public Block getRails() {
		return this.signblock.getRelative(0, 2, 0);
	}
	public boolean hasRails() {
		return BlockUtil.isRails(this.getRails());
	}
	public BlockFace getRailDirection() {
		if (this.raildirection == null) {
			this.raildirection = BlockUtil.getRails(getRails()).getDirection();
		}
		return this.raildirection;
	}
	public Location getRailLocation() {
		return this.getLocation().add(0.5, 2, 0.5);
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
		return getMember().getDirection() != getFacing();
	}
	public Sign getSign() {
		if (this.sign == null) {
			this.sign = BlockUtil.getSign(signblock);
		}
		return this.sign;
	}
	public MinecartMember getMember() {
		if (!this.memberchecked) {
			this.member = MinecartMember.getAt(getRailLocation(), this.group);
			this.memberchecked = true;
		}
		if (this.member == null && this.group != null && this.group.size() > 0) {
			if (this.actionType == ActionType.GROUP_LEAVE) {
				this.member = this.group.tail();
			} else {
				this.member = this.group.head();
			}
		}
		return this.member;
	}
	public MinecartGroup getGroup() {
		if (this.group != null) return this.group;
		MinecartMember mm = this.getMember();
		if (mm == null) return null;
		return mm.getGroup();
	}
	public String getLine(int index) {
		return this.getSign().getLine(index);
	}

	public boolean isCancelled() {
		return this.cancelled;
	}

	public void setCancelled(boolean arg0) {
		this.cancelled = arg0;
		
	}
}
