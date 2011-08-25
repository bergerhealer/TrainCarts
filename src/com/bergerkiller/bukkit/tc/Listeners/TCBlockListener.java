package com.bergerkiller.bukkit.tc.Listeners;

import java.util.ArrayList;
import java.util.HashSet;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockRedstoneEvent;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;

public class TCBlockListener extends BlockListener {
	
	private HashSet<Block> poweredBlocks = new HashSet<Block>();
	
	@Override
	public void onBlockRedstoneChange(BlockRedstoneEvent event) {
		Sign s = Util.getSign(event.getBlock());
		if (s != null) {
			if (s.getLine(0).equalsIgnoreCase("[train]")) {
				//Did its power go from 0 to > 0?
				boolean powered = poweredBlocks.contains(event.getBlock());
				if (event.getNewCurrent() > 0 && !powered) {
					poweredBlocks.add(event.getBlock());
					String command = s.getLine(1).toLowerCase();
					//Handle the command on the second line
					if (command.startsWith("spawn")) {
						//No train present?
						
						double force = 0;
						try {
							force = Double.parseDouble(command.substring(5).trim());
						} catch (Exception ex) {};

						//Get the cart types to spawn
						ArrayList<Integer> types = new ArrayList<Integer>();
						for (char cart : (s.getLine(2) + s.getLine(3)).toCharArray()) {
							if (cart == 'm') {
								types.add(0);
							} else if (cart == 's') {
								types.add(1);
							} else if (cart == 'p') {
								types.add(2);
							}
						}
						//Create the group
						MinecartGroup g = new MinecartGroup();
						BlockFace dir = ((org.bukkit.material.Sign) s.getData()).getFacing();
						Location[] locs = TrackMap.walk(event.getBlock().getRelative(0, 2, 0), dir, types.size(), TrainCarts.cartDistance);
						
						//Check if spot is taken
						for (int i = 0;i < locs.length;i++) {
							if (MinecartMember.getAt(locs[i]) != null) return;
						}
						//Spawn the train
						for (int i = 0;i < types.size();i++) {
							g.addMember(MinecartMember.get(locs[i], types.get(i), g));
						}
						MinecartGroup.load(g);
						g.move(force);
					}
				} else if (powered && event.getNewCurrent() == 0) {
					poweredBlocks.remove(event.getBlock());
				}
				if (s.getLine(1).equalsIgnoreCase("station")) {
					//get a rails
					Block rb = event.getBlock().getRelative(0, 2, 0);
					if (Util.isRails(rb)) {
						MinecartMember mm = MinecartMember.getAt(rb.getLocation());
						if (mm != null && mm.grouped()) {
							double length = 0;
							try {
								length = Double.parseDouble(s.getLine(2));
							} catch (Exception ex) {};
							TCVehicleListener.handleStation(mm.getGroup(), rb, event.getBlock(), length);
							return;
						}
					}
				}
			}				
		}
		
	}
	
}
