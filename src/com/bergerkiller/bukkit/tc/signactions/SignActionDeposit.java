package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.server.IInventory;
import net.minecraft.server.TileEntity;
import net.minecraft.server.TileEntityChest;
import net.minecraft.server.TileEntityDispenser;
import net.minecraft.server.TileEntityFurnace;

import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.common.MergedInventory;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.RecipeUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.InventoryWatcher;

public class SignActionDeposit extends SignAction {

	public void deposit(List<TileEntityFurnace> furnaces, ItemParser[] parsers, Inventory cartinv, MinecartMember m) {
    	//put stuff from the inventory into the furnaces
		if (furnaces.isEmpty()) return;
		int limit;
		Inventory furnaceinv;
		
		//split parsers into the burned and cooked items
		List<ItemParser> cooked = new ArrayList<ItemParser>();
		List<ItemParser> burned = new ArrayList<ItemParser>();
		boolean useDefault = true;
		for (ItemParser parser : parsers) {
			useDefault = false;
			if (!parser.hasType()) {
				useDefault = true;
				break;
			} else if (RecipeUtil.isFuelItem(parser.getTypeId())) {
				//this item is fuel
				burned.add(parser);
			} else if (RecipeUtil.getFurnaceResult(parser.getTypeId()) != null) {
				//this item is NOT
				cooked.add(parser);
			}
		}
		if (useDefault) {
			cooked.clear();
			burned.clear();
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
		
    	//transfer items
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
    	    	
    	//transfer coal (requires manual limiting if no amount is set)
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
		
		//get the block types to collect and the radius (2nd line)
		LinkedHashSet<Material> typesToCheck = SignActionCollect.parseName(info.getLine(1), "deposit");
		if (typesToCheck.isEmpty()) return;
		int radius = Util.parse(info.getLine(1), TrainCarts.defaultTransferRadius);
		
		//get the tile entities to deposit to
		Set<TileEntity> found = SignActionCollect.getTileEntities(info, radius);
		if (found.isEmpty()) return;
		if (!info.isPowered() || !info.hasRails()) return;
		
		List<IInventory> invlist = new ArrayList<IInventory>();
		List<TileEntityFurnace> furnaces = new ArrayList<TileEntityFurnace>();
		for (Material mat : typesToCheck) {
			if (mat == Material.CHEST) {
				for (TileEntity tile : found) {
					if (tile instanceof TileEntityChest) {
						invlist.add((IInventory) tile);
					}
				}
			} else if (mat == Material.FURNACE) {
				for (TileEntity tile : found) {
					if (tile instanceof TileEntityFurnace) {
						furnaces.add((TileEntityFurnace) tile);
					}
				}
			} else if (mat == Material.DISPENSER) {
				for (TileEntity tile : found) {
					if (tile instanceof TileEntityDispenser) {
						invlist.add((IInventory) tile);
					}
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
        
		ItemParser[] parsers = Util.getParsers(info.getLine(2), info.getLine(3));
		
		if (!invlist.isEmpty()) {
			int limit;
			final Inventory to = MergedInventory.convert(invlist);
			for (ItemParser p : parsers) {
				if (p == null) continue;
				limit = p.hasAmount() ? p.getAmount() : Integer.MAX_VALUE;
				ItemUtil.transfer(cartinv, to, p, limit);
			}
		}
		if (!furnaces.isEmpty()) {
			deposit(furnaces, parsers, cartinv, info.getMember());
		}
	}

	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			LinkedHashSet<Material> typesToCheck = SignActionCollect.parseName(type, "deposit");
			if (typesToCheck.isEmpty()) return false;
			String[] types = new String[typesToCheck.size()];
			int i = 0;
			for (Material mat : typesToCheck) {
				types[i] = mat.toString().toLowerCase() + "s";
				i++;
			}
			return handleBuild(event, Permission.BUILD_DEPOSITOR, "storage minecart item depositor", 
					"make trains put items into " + StringUtil.combineNames(types));
		}
		return false;
	}

}
