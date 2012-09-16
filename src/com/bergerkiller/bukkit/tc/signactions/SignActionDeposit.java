package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.minecraft.server.IInventory;
import net.minecraft.server.TileEntity;
import net.minecraft.server.TileEntityChest;
import net.minecraft.server.TileEntityDispenser;
import net.minecraft.server.TileEntityFurnace;

import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.common.MergedInventory;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.RecipeUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.InteractType;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.InventoryWatcher;
import com.bergerkiller.bukkit.tc.utils.GroundItemsInventory;

public class SignActionDeposit extends SignAction {
	public void deposit(List<TileEntityFurnace> furnaces, String line2, String line3, Inventory cartinv, MinecartMember m) {
		//split parsers into the burned and cooked items
		List<ItemParser> cooked = new ArrayList<ItemParser>();
		List<ItemParser> burned = new ArrayList<ItemParser>();
		for (ItemParser parser : Util.getParsers(line2)) {
			if (!parser.hasType()) {
				continue;
			}
			if (RecipeUtil.getHeatableItems().contains(parser.getTypeId())) {
				cooked.add(parser);
			} else if (RecipeUtil.isFuelItem(parser.getTypeId())) {
				burned.add(parser);
			}
		}
		for (ItemParser parser : Util.getParsers(line3)) {
			if (!parser.hasType()) {
				continue;
			}
			if (RecipeUtil.isFuelItem(parser.getTypeId())) {
				burned.add(parser);
			} else if (RecipeUtil.getHeatableItems().contains(parser.getTypeId())) {
				cooked.add(parser);
			}
		}
		if (cooked.isEmpty() && burned.isEmpty()) {
			for (ItemParser p : TrainCarts.plugin.getParsers("heatable")) {
				if (!p.hasType()) {
					cooked.clear();
					break;
				} else {
					cooked.add(p);
				}
			}
			for (ItemParser p : TrainCarts.plugin.getParsers("fuel")) {
				if (!p.hasType()) {
					burned.clear();
					break;
				} else {
					burned.add(p);
				}
			}
		}
		deposit(furnaces, cooked, burned, cartinv, m);
	}

	public void deposit(List<TileEntityFurnace> furnaces, List<ItemParser> cooked, List<ItemParser> burned, Inventory cartinv, MinecartMember m) {
    	//put stuff from the inventory into the furnaces
		if (furnaces.isEmpty()) return;
		int limit;
		Inventory furnaceinv;

    	//transfer cooked items
    	for (ItemParser p : cooked) {
    		limit = p.hasAmount() ? p.getAmount() : Integer.MAX_VALUE;
    		for (TileEntityFurnace f : furnaces) {
    			if (TrainCarts.showTransferAnimations) {
    				furnaceinv = InventoryWatcher.convert(m, f, f);
    			} else {
    				furnaceinv = new CraftInventory(f);
    			}
    			ItemStack item = furnaceinv.getItem(0);
    			if (item == null) {
    				item = new CraftItemStack(0, 0);
    			}
    			limit -= ItemUtil.transfer(cartinv, item, p, limit);
    			ItemUtil.setItem(furnaceinv, 0, item);
    		}
    	}

    	//transfer fuel (requires manual limiting if no amount is set)
		for (ItemParser p : burned) {
			if (p == null) continue;
			limit = p.hasAmount() ? p.getAmount() : Integer.MAX_VALUE;
			//first fill the amount needed
			for (TileEntityFurnace f : furnaces) {
				if (limit == 0) return;
    			if (TrainCarts.showTransferAnimations) {
    				furnaceinv = InventoryWatcher.convert(m, f, f);
    			} else {
    				furnaceinv = new CraftInventory(f);
    			}
				ItemStack cookeditem = furnaceinv.getItem(0);
				if (cookeditem == null || cookeditem.getTypeId() == 0) continue;
				int fuelNeeded = cookeditem.getAmount() * 200;
				if (fuelNeeded == 0) continue; //nothing to cook
				//===================================================
				fuelNeeded -= f.cookTime;
				if (fuelNeeded <= 0) continue; //we got enough
				//===================================================
				ItemStack fuel = furnaceinv.getItem(1);
				if (fuel == null) {
					fuel = new CraftItemStack(0, 0);
				} else {
					fuel = fuel.clone();
				}
				int fuelPerItem = 0;
				if (fuel.getTypeId() == 0) {
					if (p.hasType()) {
						fuelPerItem = RecipeUtil.getFuelTime(p.getTypeId());
					}
				} else {
					fuelPerItem = RecipeUtil.getFuelTime(fuel.getTypeId());
				}
				//====================================================
				if (fuelPerItem == 0) continue;
				fuelNeeded -= fuelPerItem * fuel.getAmount();
				if (fuelNeeded <= 0) continue;
				//====================================================
				int itemcount = Math.min(limit, (int) Math.ceil((double) fuelNeeded / (double) fuelPerItem));
				limit -= ItemUtil.transfer(cartinv, fuel, p, itemcount);
				ItemUtil.setItem(furnaceinv, 1, fuel);
			}

			//if an amount is set; top it off
			if (p.hasAmount()) {
        		for (TileEntityFurnace f : furnaces) {
        			if (TrainCarts.showTransferAnimations) {
        				furnaceinv = InventoryWatcher.convert(m, f, f);
        			} else {
        				furnaceinv = new CraftInventory(f);
        			}
        			ItemStack item = furnaceinv.getItem(1);
        			limit -= ItemUtil.transfer(cartinv, item, p, limit);
        			ItemUtil.setItem(furnaceinv, 1, item);
        		}
			}
		}
	}
	
	@Override
	public void execute(SignActionEvent info) {		   
		if (!info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
			return;
		}
		
		if (!info.hasRailedMember() || !info.isPowered()) return;
		
		//get the block types to collect and the radius (2nd line)
		Collection<InteractType> typesToCheck = InteractType.parse("deposit", info.getLine(1));
		if (typesToCheck.isEmpty()) return;
		int radius = Util.parse(info.getLine(1), TrainCarts.defaultTransferRadius);
		
		//get the tile entities to deposit to
		Set<TileEntity> found = SignActionCollect.getTileEntities(info, radius);
		if (found.isEmpty()) return;
		
		List<IInventory> invlist = new ArrayList<IInventory>();
		List<TileEntityFurnace> furnaces = new ArrayList<TileEntityFurnace>();
		for (InteractType type : typesToCheck) {
			switch (type) {
				case CHEST : {
					for (TileEntity tile : found) {
						if (tile instanceof TileEntityChest) {
							invlist.add((IInventory) tile);
						}
					}
					break;
				}
				case FURNACE : {
					for (TileEntity tile : found) {
						if (tile instanceof TileEntityFurnace) {
							furnaces.add((TileEntityFurnace) tile);
						}
					}
					break;
				}
				case DISPENSER : {
					for (TileEntity tile : found) {
						if (tile instanceof TileEntityDispenser) {
							invlist.add((IInventory) tile);
						}
					}
					break;
				}
				case GROUNDITEM : {
					invlist.add(new GroundItemsInventory(info.getRailLocation(), (double) radius + 0.5));
					break;
				}
			}
		}

		if (invlist.isEmpty() && furnaces.isEmpty()) return;

		//parse the sign
		boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
		boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
		if (!docart && !dotrain) return;
		
		//get the inventory to transfer from
        Inventory cartinv;
        if (docart) {
        	if (!info.getMember().isStorageCart()) return;
        	cartinv = info.getMember().getInventory();
        } else {
        	cartinv = info.getGroup().getInventory();
        }

        //deposit into furnaces
		if (!furnaces.isEmpty()) {
			deposit(furnaces, info.getLine(2), info.getLine(3), cartinv, info.getMember());
		}
		//deposit into other inventories
		if (!invlist.isEmpty()) {
			if (TrainCarts.showTransferAnimations) {
				InventoryWatcher.convertAll(invlist, info.getMember());
			}
			ItemParser[] parsers = Util.getParsers(info.getLine(2), info.getLine(3));
			int limit;
			final Inventory to = MergedInventory.convert(invlist);
			for (ItemParser p : parsers) {
				if (p == null) continue;
				limit = p.hasAmount() ? p.getAmount() : Integer.MAX_VALUE;
				ItemUtil.transfer(cartinv, to, p, limit);
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			Collection<InteractType> typesToCheck = InteractType.parse("deposit", event.getLine(1));
			if (typesToCheck.isEmpty()) return false;
			String[] types = new String[typesToCheck.size()];
			int i = 0;
			for (InteractType mat : typesToCheck) {
				types[i] = mat.toString().toLowerCase() + "s";
				i++;
			}
			return handleBuild(event, Permission.BUILD_DEPOSITOR, "storage minecart item depositor", 
					"make trains put items into " + StringUtil.combineNames(types));
		}
		return false;
	}

}
