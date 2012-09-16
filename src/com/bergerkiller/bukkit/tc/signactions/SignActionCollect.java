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

import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.common.MergedInventory;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
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

public class SignActionCollect extends SignAction {

	protected static Set<TileEntity> getTileEntities(SignActionEvent info, int radius) {
		int radX = radius;
		int radZ = radius;
		BlockFace dir = info.getRailDirection();
		if (dir == BlockFace.SOUTH) {
			radX = 0;
		} else if (dir == BlockFace.WEST) {
			radZ = 0;
		}
		return BlockUtil.getTileEntities(info.getRails(), radX, radius, radZ);
	}
	
	public void collect(List<IInventory> furnaces, ItemParser[] parsers, Inventory cartinv, MinecartMember m) {
		int limit;
		Inventory furnaceinv;
		for (ItemParser p : parsers) {
    		limit = p.hasAmount() ? p.getAmount() : Integer.MAX_VALUE;
    		for (IInventory f : furnaces) {
    			furnaceinv = new CraftInventory(f);
    			ItemStack item = furnaceinv.getItem(2);
    			if (item != null && item.getTypeId() != 0 && p.match(item)) {
        			limit -= ItemUtil.transfer(item, cartinv, limit);
        			ItemUtil.setItem(furnaceinv, 2, item);
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
		
		Collection<InteractType> typesToCheck = InteractType.parse("collect", info.getLine(1));
		if (typesToCheck.isEmpty()) return;

		//get the block types to collect and the radius (2nd line)
		int radius = Util.parse(info.getLine(1), TrainCarts.defaultTransferRadius);
		
		//get the tile entities to collect
		List<IInventory> invlist = new ArrayList<IInventory>();
		List<IInventory> furnacelist = new ArrayList<IInventory>();
		Set<TileEntity> found = getTileEntities(info, radius);
		if (found.isEmpty()) return;
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
							furnacelist.add((IInventory) tile);
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
		if (invlist.isEmpty() && furnacelist.isEmpty()) return;

		//convert inventories to watched inventories
		if (TrainCarts.showTransferAnimations) {
			InventoryWatcher.convertAll(invlist, info.getMember());
			InventoryWatcher.convertAll(furnacelist, info.getMember());
		}

		//parse the sign
		boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
		boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
		if (!docart && !dotrain) return;
		
		//get the inventory to transfer to
        Inventory to;
        if (docart) {
        	if (!info.getMember().isStorageCart()) return;
        	to = info.getMember().getInventory();
        } else {
        	to = info.getGroup().getInventory();
        }
        
		//get items to transfer
        ItemParser[] parsers = Util.getParsers(info.getLine(2), info.getLine(3));
        
        //collect from furnaces
        if (!furnacelist.isEmpty()) {
        	collect(furnacelist, parsers, to, info.getMember());
        }
        
        if (!invlist.isEmpty()) {
    		//get inventory
    		Inventory from = MergedInventory.convert(invlist);

    		//actually transfer
            int limit;
    		for (ItemParser p : parsers) {
    			if (p == null) continue;
    			limit = p.hasAmount() ? p.getAmount() : Integer.MAX_VALUE;
    			ItemUtil.transfer(from, to, p, limit);
    		}
        }
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			Collection<InteractType> typesToCheck = InteractType.parse("collect", event.getLine(1));
			if (typesToCheck.isEmpty()) return false;
			String[] types = new String[typesToCheck.size()];
			int i = 0;
			for (InteractType mat : typesToCheck) {
				types[i] = mat.toString().toLowerCase() + "s";
				i++;
			}
			return handleBuild(event, Permission.BUILD_COLLECTOR, "storage minecart item collector", 
					"obtain items from " + StringUtil.combineNames(types));
		}
		return false;
	}
}
