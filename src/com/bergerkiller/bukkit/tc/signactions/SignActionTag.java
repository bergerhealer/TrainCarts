package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public class SignActionTag extends SignAction {

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
	
	public void handleTag(SignActionEvent info, boolean left, boolean right) {
		boolean down = false;
		if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER) && info.isFacing()) {
			down = left || right;         
			if (info.isPowered()) {   
				BlockFace dir = BlockFace.NORTH;  
				if (left) dir = BlockFace.WEST;  
				if (right) dir = BlockFace.EAST; 
				info.setRailsRelative(dir);
			}
		}
		info.setLevers(down);
	}
	
	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE) && info.isTrainSign()) {
			if (info.isType("tag")) {
				if (!handleDestination(info, info.getGroup().head().getProperties())) {
					TrainProperties prop = info.getGroup().getProperties();
					handleTag(info, prop.hasTag(info.getLine(2)), prop.hasTag(info.getLine(3)));
				}
			}
		} else if (info.isAction(SignActionType.MEMBER_ENTER, SignActionType.MEMBER_LEAVE) && info.isCartSign()) {
			if (info.isType("tag")) {
				CartProperties prop = info.getMember().getProperties();
				if (!handleDestination(info, prop)) {
					//Toggle levers and rails based on tags					
					handleTag(info, prop.hasTag(info.getLine(2)), prop.hasTag(info.getLine(3)));
				}				
			}
		}
	}

}
