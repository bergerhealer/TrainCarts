package com.bergerkiller.bukkit.tc.Listeners;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Utils.BlockUtil;

public class TCPlayerListener extends PlayerListener {

	//this class is only used when debugging
	
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
						if (!event.getPlayer().hasPermission("train.place.minecart")) {
							event.setCancelled(true);
						} else if (TrainCarts.setOwnerOnPlacement) {
							TCVehicleListener.lastPlayer = event.getPlayer().getName();
						}
					}
				}
			}
		}
	}
	
	@Override
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		MinecartGroup g = MinecartGroup.get(event.getRightClicked());
		if (g != null) {
			g.getProperties().setEditing(event.getPlayer());
		}
	}
	
}
