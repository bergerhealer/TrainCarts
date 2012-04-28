package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.BlockMap;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.DirectionStatement;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;

public class SignActionSwitcher extends SignAction {

	private BlockMap<AtomicInteger> switchedTimes = new BlockMap<AtomicInteger>();
	private AtomicInteger getSwitchedTimes(Block signblock) {
		AtomicInteger i = switchedTimes.get(signblock);
		if (i == null) {
			i = new AtomicInteger();
			switchedTimes.put(signblock, i);
		}
		return i;
	}

	public void handleRails(SignActionEvent info, boolean left, boolean right) {
		boolean down = false;
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER, 
				SignActionType.GROUP_UPDATE, SignActionType.MEMBER_UPDATE)) {
			down = left || right;
			BlockFace from = info.getFacing();
			if (Util.getRailsBlock(info.getRails().getRelative(from)) == null) {
				from = from.getOppositeFace();
			}
			if (info.isPowered()) info.setRails(from, left, right);
		}
		info.setLevers(down);
	}

	@Override
	public boolean overrideFacing() {
		return true;
	}

	public boolean handleCounter(SignActionEvent info, String l, String r) {
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) {
			try {
				boolean left = false;
				boolean right = false;
				int lcount = Integer.parseInt(l);
				int rcount = Integer.parseInt(r);
				AtomicInteger i = getSwitchedTimes(info.getBlock());
				int count = i.get();
				if (count < lcount) {
					left = true;
					i.incrementAndGet();
				} else if (count >= lcount + rcount - 1) {
					right = true;
					i.set(0);
				} else {
					right = true;
					i.incrementAndGet();
				}
				handleRails(info, left, right);
				return true;
			} catch (NumberFormatException ex) {}
		}
		return false;
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("switcher", "tag")) return;
		boolean doCart = false;
		boolean doTrain = false;
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_UPDATE) && info.isTrainSign()) {
			if (!info.hasRailedMember()) return;
			doTrain = true;
		} else if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_UPDATE) && info.isCartSign()) {
			if (!info.hasRailedMember()) return;
			doCart = true;
		} else if (info.isAction(SignActionType.MEMBER_LEAVE) && info.isCartSign()) {
			info.setLevers(false);
			return;
		} else if (info.isAction(SignActionType.GROUP_LEAVE) && info.isTrainSign()) {
			info.setLevers(false);
			return;
	    } else {
			return;
		}
		
		if ((doCart || doTrain) && info.isFacing()) {
			//find out what statements to parse
			List<DirectionStatement> statements = new ArrayList<DirectionStatement>();
			statements.add(new DirectionStatement(info.getLine(2), Direction.LEFT));
			statements.add(new DirectionStatement(info.getLine(3), Direction.RIGHT));
			//other signs below this sign we could parse?
			Block signblock = info.getBlock();
			while (BlockUtil.isSign(signblock = signblock.getRelative(BlockFace.DOWN))) {
				Sign sign = BlockUtil.getSign(signblock);
				if (sign == null) break;
				boolean valid = true;
				for (String line : sign.getLines()) {
					DirectionStatement stat = new DirectionStatement(line);
					if (stat.direction == Direction.NONE) {
						valid = false;
						break;
					} else {
						statements.add(stat);
					}
				}
				if (!valid) break;
			}
			//parse all of the statements
			//are we going to use a counter?
			int maxcount = 0;
			int currentcount = 0;
			AtomicInteger signcounter = null;
			for (DirectionStatement stat : statements) {
				if (stat.hasNumber()) {
					maxcount += stat.number;
					if (signcounter == null) {
						signcounter = getSwitchedTimes(info.getBlock());
						if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) {
							currentcount = signcounter.getAndIncrement();
						} else {
							currentcount = signcounter.get();
						}
					}
				}
			}
			if (signcounter != null && currentcount >= maxcount) {
				signcounter.set(1);
				currentcount = 0;
			}
			
			int counter = 0;
			Direction dir = Direction.NONE;
			for (DirectionStatement stat : statements) {
				if ((stat.hasNumber() && (counter += stat.number) > currentcount)
						|| (doCart && stat.has(info.getMember()))
						|| (doTrain && stat.has(info.getGroup()))) {

					dir = stat.direction;
					break;
				}
			}
			info.setLevers(dir != Direction.NONE);
			if (dir != Direction.NONE) {
				//handle this direction
				info.setRailsFromCart(dir.getDirection(info.getFacing()));
				return; //don't do destination stuff
			}
		}

		//handle destination alternatively
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) {
			PathNode node = PathNode.getOrCreate(info);
			if (node != null) {
				PathConnection conn = null;
				if (doCart) {
					conn = node.findConnection(info.getMember().getProperties().destination);
				} else if (doTrain) {
					conn = node.findConnection(info.getGroup().getProperties().getDestination());
				}
				if (conn != null) {
					info.setRailsFromCart(conn.direction);
					return;
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.CART) {
			if (type.startsWith("switcher") || type.startsWith("tag")) {
				return handleBuild(event, Permission.BUILD_SWITCHER, "cart switcher", "switch between tracks based on properties of the cart above");
			}
		} else if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("switcher") || type.startsWith("tag")) {
				return handleBuild(event, Permission.BUILD_SWITCHER, "train switcher", "switch between tracks based on properties of the train above");
			}
		}
		return false;
	}

}
