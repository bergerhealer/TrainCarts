package com.bergerkiller.bukkit.tc.signactions;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.ItemParser;
import com.bergerkiller.bukkit.tc.TrainProperties;
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
				BlockFace check = info.getDestDir(prop.destination);
				if (check != BlockFace.UP){
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

	public boolean hasOther(SignActionEvent info, String line, boolean forTrain) {
	    boolean inv = false;
	    while (line.startsWith("!")) {
	    	line = line.substring(1);
	    	inv = !inv;
	    }
	    boolean state = false; 
	    //parse line here
	    if (line.equalsIgnoreCase("passenger") || line.equalsIgnoreCase("passengers")) {
	    	if (forTrain) {
	    		state = info.getGroup().hasPassenger();
	    	} else {
	    		state = info.getMember().hasPassenger();
	    	}
	    } else if (line.equalsIgnoreCase("items")) {
	    	if (forTrain) {
	    		state = info.getGroup().hasItems();
	    	} else {
	    		state = info.getMember().hasItems();
	    	}
	    } else if (line.toLowerCase().startsWith("item")) {
	    	ItemParser parser = ItemParser.parse(line.substring(4));
	    	if (parser != null) {
		    	if (forTrain) {
		    		state = info.getGroup().hasItem(parser);
		    	} else {
		    		state = info.getMember().hasItem(parser);
		    	}
	    	} else {
		    	if (forTrain) {
		    		state = info.getGroup().hasItems();
		    	} else {
		    		state = info.getMember().hasItems();
		    	}
	    	}
	    } else if (line.equalsIgnoreCase("empty")) {
	    	if (forTrain) {
	    		state = !info.getGroup().hasItems() && !info.getGroup().hasPassenger();
	    	} else {
	    		state = !info.getMember().hasItems() && !info.getMember().hasPassenger();
	    	}
	    } else if (line.equalsIgnoreCase("coal") || line.equalsIgnoreCase("fuel")  || line.equalsIgnoreCase("fueled")) {
	    	if (forTrain) {
	    		state = info.getGroup().hasFuel();
	    	} else {
	    		state = info.getMember().hasFuel();
	    	}
	    } else if (line.equalsIgnoreCase("powered")) {
	    	if (forTrain) {
	    		state = info.getGroup().size(Material.POWERED_MINECART) > 0;
	    	} else {
	    		state = info.getMember().isPoweredMinecart();
	    	}
	    } else if (line.equalsIgnoreCase("storage")) {
	    	if (forTrain) {
	    		state = info.getGroup().size(Material.STORAGE_MINECART) > 0;
	    	} else {
	    		state = info.getMember().isStorageMinecart();
	    	}
	    } else if (line.equalsIgnoreCase("minecart")) {
	    	if (forTrain) {
	    		state = info.getGroup().size(Material.MINECART) > 0;
	    	} else {
	    		state = info.getMember().isRegularMinecart();
	    	}
	    }
	    return state != inv;
	}
	
	@Override
	public void execute(SignActionEvent info) {
		String l = info.getLine(2);
		String r = info.getLine(3);
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE) && info.isTrainSign()) {
			if (info.isType("switcher")) {
				if (!handleDestination(info, info.getGroup().head().getProperties())) {
					if (!handleCounter(info, l, r)) {
						TrainProperties prop = info.getGroup().getProperties();
						boolean left = prop.hasTag(l) || hasOther(info, l, true);
						boolean right = prop.hasTag(r) || hasOther(info, r, true);
						handleRails(info, left, right);
					}
				}
			}
		} else if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_LEAVE) && info.isCartSign()) {
			if (info.isType("switcher")) {
				CartProperties prop = info.getMember().getProperties();
				if (!handleDestination(info, prop)) {
					if (!handleCounter(info, l, r)) {
						boolean left = prop.hasTag(l) || hasOther(info, l, false);
						boolean right = prop.hasTag(r) || hasOther(info, r, false);
						handleRails(info, left, right);
					}
				}				
			}
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.CART) {
			if (type.startsWith("switcher")) {
				handleBuild(event, Permission.BUILD_SWITCHER, "cart switcher", "switch between tracks based on properties of the cart above");
			}
		} else if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("switcher")) {
				handleBuild(event, Permission.BUILD_SWITCHER, "train switcher", "switch between tracks based on properties of the train above");
			}
		}
	}

}
