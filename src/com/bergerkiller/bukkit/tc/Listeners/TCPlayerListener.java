package com.bergerkiller.bukkit.tc.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;

public class TCPlayerListener extends PlayerListener {

	@Override
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if (BlockUtil.isRails(event.getClickedBlock())) {
				ItemStack item = event.getPlayer().getItemInHand();
				if (item != null) {
					if (item.getType() == Material.MINECART || 
							item.getType() == Material.POWERED_MINECART || 
							item.getType() == Material.STORAGE_MINECART) {
						//Placing a minecart on the tracks
						if (event.getPlayer().hasPermission("train.place.minecart")) {
							//Not already a minecart at this spot?
							Location at = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
							if (MinecartMember.getAt(at, null, 0.5) == null) {
								TCVehicleListener.lastPlayer = event.getPlayer();
								return;
							}
						}
						event.setCancelled(true);
					}
				}
			}
		}
	    if ((event.getAction() == Action.RIGHT_CLICK_BLOCK) || (event.getAction() == Action.LEFT_CLICK_BLOCK)) {
	    	Sign sign = BlockUtil.getSign(event.getClickedBlock());
	    	if (sign != null) {
	    		if (sign.getLine(0).equalsIgnoreCase("[train]")) {
		    		if (sign.getLine(1).equalsIgnoreCase("destination")) {
		    	    	//get the train this player is editing
		    			Player p = event.getPlayer();
		    			MinecartMember mm = MinecartMember.getEditing(p);
		    			if (mm != null) {
		    				TrainProperties prop = mm.getGroup().getProperties();
			    	    	if (prop == null) {
			    		    	if (CartProperties.canHaveOwnership(p)) {
			    		    		p.sendMessage(ChatColor.YELLOW + "You haven't selected a train to edit yet!");
			    		    	} else {
			    		    		p.sendMessage(ChatColor.RED + "You are not allowed to own trains!");
			    		    	}
			    	    	} else if (!prop.isOwner(p)) {
			    	    		p.sendMessage(ChatColor.RED + "You don't own this train!");
			    	    	} else {
			    	    		String dest = sign.getLine(2);
			    	    		prop.setDestination(dest);
			    	    		p.sendMessage(ChatColor.YELLOW + "You have selected " + ChatColor.WHITE + dest + ChatColor.YELLOW + " as your destination!");
			    	    	}
		    			}
		    		}
	    		}
	    	}
	    }
	}
	
	@Override
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		MinecartMember mm = MinecartMember.get(event.getRightClicked());
		if (mm != null) {
			mm.setEditing(event.getPlayer());
			MinecartMember entered = MinecartMember.get(event.getPlayer().getVehicle());
			if (entered != null && !entered.getProperties().allowPlayerExit) {
				event.setCancelled(true);
			}
		}
	}
	
}
