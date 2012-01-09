package com.bergerkiller.bukkit.tc.signactions;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.Destinations;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;

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
	
	public boolean handleDestination(SignActionEvent info, CartProperties prop) {
		if (prop.hasDestination()) {
			//Handle rails based on destination
			if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)){
				BlockFace check = Destinations.getDir(prop.destination, info.getRails());
				if (check != BlockFace.UP) {
					info.setRailsFromCart(check);
					return true;
				}
			}
		}
		return false;
	}
	
	public void handleRails(SignActionEvent info, boolean left, boolean right) {
		boolean down = false;
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER) && info.isFacing()) {
			down = left || right;         
			if (info.isPowered()) info.setRails(left, right);
		}
		info.setLevers(down);
	}
	
	public boolean handleCounter(SignActionEvent info, String l, String r) {
		try {
			boolean left = false;
			boolean right = false;
			if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER) && info.isFacing()) {
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
			}
			handleRails(info, left, right);
			return true;
		} catch (NumberFormatException ex) {
			return false;
		}
	}

	@Override
	public void execute(SignActionEvent info) {
		String l = info.getLine(2);
		String r = info.getLine(3);
		if (!info.hasRails()) return;
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE) && info.isTrainSign()) {
			if (info.isType("switcher", "tag")) {
				if (!info.getGroup().isValid() || !handleDestination(info, info.getGroup().head().getProperties())) {
					if (!handleCounter(info, l, r)) {
						boolean left = !l.equals("") && info.getGroup().hasTag(l);
						boolean right = !r.equals("") && info.getGroup().hasTag(r);
						handleRails(info, left, right);
					}
				}
			}
		} else if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_LEAVE) && info.isCartSign()) {
			if (info.isType("switcher", "tag")) {
				CartProperties prop = info.getMember().getProperties();
				if (!handleDestination(info, prop)) {
					if (!handleCounter(info, l, r)) {
						boolean left = !l.equals("") && info.getMember().hasTag(l);
						boolean right = !r.equals("") && info.getMember().hasTag(r);
						handleRails(info, left, right);
					}
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
