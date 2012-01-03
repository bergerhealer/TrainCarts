package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;

public class SignActionSpawn extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.hasRails()) return;
		if (info.isAction(SignActionType.REDSTONE_ON)) {
			if (info.isTrainSign() || info.isCartSign()) {
				if (info.isType("spawn")) {
					double force = 0;
					try {
						force = Double.parseDouble(info.getLine(1).substring(5).trim());
					} catch (Exception ex) {};

					//Get the cart types to spawn
					ArrayList<Integer> types = new ArrayList<Integer>();
					for (char cart : (info.getLine(2) + info.getLine(3)).toCharArray()) {
						if (cart == 'm') {
							types.add(0);
						} else if (cart == 's') {
							types.add(1);
						} else if (cart == 'p') {
							types.add(2);
						}
					}

					if (types.size() == 0) return;

					BlockFace dir = info.getFacing();
					Location[] locs = TrackMap.walk(info.getRails(), dir, types.size(), TrainCarts.cartDistance);

					//Check if spot is taken
					for (int i = 0;i < locs.length;i++) {
						if (MinecartMember.getAt(locs[i]) != null) return;
					}		
					
					//Spawn the group
					BlockFace direction = info.getFacing();
					MinecartGroup group = MinecartGroup.spawn(info.getRails(), info.getFacing(), types);
					group.head().addActionLaunch(direction, 2, force);
				}
			}
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("spawn")) {
				handleBuild(event, Permission.BUILD_SPAWNER, "train spawner", "spawn trains on the tracks above when powered by redstone");
			}
		}
	}

}
