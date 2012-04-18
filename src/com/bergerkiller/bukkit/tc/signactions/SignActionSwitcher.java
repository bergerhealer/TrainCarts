package com.bergerkiller.bukkit.tc.signactions;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;

public class SignActionSwitcher extends SignAction {

	private HashMap<Location, AtomicInteger> switchedTimes = new HashMap<Location, AtomicInteger>();
	private AtomicInteger getSwitchedTimes(Location signloc) {
		AtomicInteger i = switchedTimes.get(signloc);
		if (i == null) {
			i = new AtomicInteger();
			switchedTimes.put(signloc, i);
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

	public boolean isFacing(SignActionEvent info) {
		if (!info.getMember().isMoving() || info.getMember().getDirectionTo() == info.getFacing().getOppositeFace()) {
			return true;
		} else {
			//can a train face the sign at all?
			Block b = info.getRails().getRelative(info.getFacing());
			return !Util.isRails(b) && info.getFacing() == info.getMember().getDirectionTo();
		}
	}

	public boolean handleCounter(SignActionEvent info, String l, String r) {
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) {
			try {
				boolean left = false;
				boolean right = false;
				int lcount = Integer.parseInt(l);
				int rcount = Integer.parseInt(r);
				AtomicInteger i = getSwitchedTimes(info.getLocation());
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
		String l = info.getLine(2);
		String r = info.getLine(3);
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE, 
				SignActionType.GROUP_UPDATE) && info.isTrainSign()) {
			if (!info.hasRailedMember()) return;
			if (isFacing(info) && !handleCounter(info, l, r)) {
				boolean left = !l.equals("") && info.getGroup().hasTag(l);
				boolean right = !r.equals("") && info.getGroup().hasTag(r);
				if (left || right || !info.getGroup().getProperties().hasDestination()) {
					handleRails(info, left, right);
					return;
				}
			}
		} else if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_LEAVE, 
				SignActionType.MEMBER_UPDATE) && info.isCartSign()) {
			if (!info.hasRailedMember()) return;
			if (isFacing(info) && !handleCounter(info, l, r)) {
				boolean left = !l.equals("") && info.getMember().hasTag(l);
				boolean right = !r.equals("") && info.getMember().hasTag(r);
				if (left || right || !info.getMember().getProperties().hasDestination()) {
					handleRails(info, left, right);
					return;
				}
			}
		} else {
			return;
		}
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) {
			PathNode node = PathNode.getOrCreate(info);
			if (node != null) {
				PathConnection conn = null;
				if (info.isCartSign()) {
					conn = node.findConnection(info.getMember().getProperties().destination);
				} else if (info.isTrainSign()) {
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
