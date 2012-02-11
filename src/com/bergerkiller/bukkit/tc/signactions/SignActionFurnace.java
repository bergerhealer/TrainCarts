package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public class SignActionFurnace extends SignAction {

	public void transferFurnace(int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
				}
			}
		}
	}
	
	@Override
	public void execute(SignActionEvent info) {
		//parse the sign
		boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
		boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
		if (!docart && !dotrain) return;
		if (!info.isPoweredFacing()) return;
		if (!info.hasRails()) return;
		boolean in = info.isType("furnace in");
		boolean out = info.isType("furnace out");
		if (!in && !out) return;
		//get the radius to look for chests
		int radius;
		try {
			String lname = info.getLine(1).substring(in ? 11 : 12);
			radius = MathUtil.limit(Integer.parseInt(lname), 1, 5);
		} catch (Exception ex) {
			radius = TrainCarts.defaultTransferRadius;
		}
		
		//actual processing here
		Inventory chest = ItemUtil.getChestInventory(info.getRails(), radius);
		if (chest == null) return;
		
		//get items to transfer
		final String items;
		if (info.getLine(2).isEmpty()) {
			if (info.getLine(3).isEmpty()) {
				items = null;
			} else {
				items = info.getLine(3);
			}
		} else if (info.getLine(3).isEmpty()) {
			items = info.getLine(2);
		} else {
			items = info.getLine(2) + ";" + info.getLine(3);
		}
		String[] types = items.split(";");
        ItemParser[] parsers = new ItemParser[types.length]; //ItemParser.parse(info.getLine(3));
        for (int i = 0; i < types.length; i++) {
        	parsers[i] = ItemParser.parse(types[i]);
        }
        
		//actually transfer
        int limit;
		if (docart) {
			if (info.getMember().isStorageMinecart()) {
				for (ItemParser p : parsers) {
					if (p == null) continue;
					limit = p.hasAmount() ? p.getAmount() : Integer.MAX_VALUE;
					if (in) {
						ItemUtil.transfer(info.getMember().getInventory(), chest, p, limit);
					} else {
						ItemUtil.transfer(chest, info.getMember().getInventory(), p, limit);
					}
				}
			}
		} else {
			for (ItemParser p : parsers) {
				if (p == null) continue;
				limit = p.hasAmount() ? p.getAmount() : Integer.MAX_VALUE;
				if (in) {
					ItemUtil.transfer(info.getGroup().getInventory(), chest, p, limit);
				} else {
					ItemUtil.transfer(chest, info.getGroup().getInventory(), p, limit);
				}
			}
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("furnace in")) {
				handleBuild(event, Permission.BUILD_FURNACE, "storage minecart to furnace dispenser", 
						"transfer items from storage minecarts to multiple furnaces connected to the tracks to start smelting");
			} else if (type.startsWith("furnace out")) {
				handleBuild(event, Permission.BUILD_FURNACE, "furnace to storage minecart dispenser", 
						"transfer items from multiple furnaces connected to the tracks to storage minecarts");
			}
		}
	}

}
