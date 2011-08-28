package com.bergerkiller.bukkit.tc.Listeners;

import net.minecraft.server.Entity;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Minecart;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.VelocityTarget;

public class TCPlayerListener extends PlayerListener {

	//this class is only used when debugging

	@Override
	public void onPlayerInteract(PlayerInteractEvent event) {

	}
	
	@Override
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {

	}
	
}
