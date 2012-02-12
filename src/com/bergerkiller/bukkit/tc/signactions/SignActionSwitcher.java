package com.bergerkiller.bukkit.tc.signactions;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
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
	
	public boolean handleDestination(SignActionEvent info) {
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
					return true;
				}
			}
		}
		return false;
	}
	
	public void handleRails(SignActionEvent info, boolean left, boolean right) {
		boolean down = false;
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER, 
				SignActionType.GROUP_UPDATE, SignActionType.MEMBER_UPDATE) && info.isFacing()) {
			down = left || right;         
			if (info.isPowered()) info.setRails(left, right);
		}
		info.setLevers(down);
	}
	
	public boolean handleCounter(SignActionEvent info, String l, String r) {
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER) && info.isFacing()) {
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
			if (!handleDestination(info)) {
				if (!handleCounter(info, l, r)) {
					boolean left = !l.equals("") && info.getGroup().hasTag(l);
					boolean right = !r.equals("") && info.getGroup().hasTag(r);
					handleRails(info, left, right);
				}
			}
		} else if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_LEAVE, 
				SignActionType.MEMBER_UPDATE) && info.isCartSign()) {
			if (!info.hasRailedMember()) return;
			if (!handleDestination(info)) {
				if (!handleCounter(info, l, r)) {
					boolean left = !l.equals("") && info.getMember().hasTag(l);
					boolean right = !r.equals("") && info.getMember().hasTag(r);
					handleRails(info, left, right);
				}
			}	
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.CART) {
			if (type.startsWith("switcher") || type.startsWith("tag")) {
				handleBuild(event, Permission.BUILD_SWITCHER, "cart switcher", "switch between tracks based on properties of the cart above");
			}
		} else if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("switcher") || type.startsWith("tag")) {
				handleBuild(event, Permission.BUILD_SWITCHER, "train switcher", "switch between tracks based on properties of the train above");
			}
		}
	}

}
