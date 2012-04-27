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
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.common.MergedInventory;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.InventoryWatcher;

public class SignActionCollect extends SignAction {

	/*
	 * Root: collect or deposit
	 */
	protected static LinkedHashSet<Material> parseName(String name, String root) {
		name = name.toLowerCase();
		LinkedHashSet<Material> typesToCheck = new LinkedHashSet<Material>();
		if (root.equals("collect")) {
			if (name.startsWith("chest out")) {
				typesToCheck.add(Material.CHEST);
			} else if (name.startsWith("dispenser out")) {
				typesToCheck.add(Material.DISPENSER);
			} else if (name.startsWith("furnace out")) {
				typesToCheck.add(Material.FURNACE);
			}
		} else if (root.equals("deposit")) {
			if (name.startsWith("chest in")) {
				typesToCheck.add(Material.CHEST);
			} else if (name.startsWith("dispenser in")) {
				typesToCheck.add(Material.DISPENSER);
			} else if (name.startsWith("furnace in")) {
				typesToCheck.add(Material.FURNACE);
			} else if (name.startsWith("smelt")) {
				typesToCheck.add(Material.FURNACE);
			}
		}
		if (name.startsWith(root + ' ')) {
			String types = name.substring(8).toLowerCase();
			if (types.startsWith("chest")) {
				typesToCheck.add(Material.CHEST);
			} else if (types.startsWith("furn")) {
				typesToCheck.add(Material.FURNACE);
			} else if (types.startsWith("disp")) {
				typesToCheck.add(Material.DISPENSER);
			} else {
				for (char c : types.toCharArray()) {
					if (c == 'c') {
						typesToCheck.add(Material.CHEST);
					} else if (c == 'f') {
						typesToCheck.add(Material.FURNACE);
					} else if (c == 'd') {
						typesToCheck.add(Material.DISPENSER);
					}
				}
			}	
		} else if (name.startsWith(root)) {
			typesToCheck.add(Material.CHEST);
			typesToCheck.add(Material.FURNACE);
			typesToCheck.add(Material.DISPENSER);
		}
		return typesToCheck;
	}
	
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
		
		LinkedHashSet<Material> typesToCheck = parseName(info.getLine(1), "collect");
		if (typesToCheck.isEmpty()) return;
		if (!info.isPowered() || !info.hasRails()) return;
		
		//get the block types to collect and the radius (2nd line)
		int radius = Util.parse(info.getLine(1), TrainCarts.defaultTransferRadius);
		
		//get the tile entities to collect
		List<IInventory> invlist = new ArrayList<IInventory>();
		List<IInventory> furnacelist = new ArrayList<IInventory>();
		Set<TileEntity> found = getTileEntities(info, radius);
		if (found.isEmpty()) return;
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
						furnacelist.add((IInventory) tile);
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
		if (invlist.isEmpty() && furnacelist.isEmpty()) return;
		
		//convert inventories to watched inventories
		if (TrainCarts.showTransferAnimations) {
			for (int i = 0; i < invlist.size(); i++) {
				IInventory inv = invlist.get(i);
				invlist.set(i, new InventoryWatcher(info.getMember(), inv, inv));
			}
			for (int i = 0; i < furnacelist.size(); i++) {
				IInventory inv = furnacelist.get(i);
				furnacelist.set(i, new InventoryWatcher(info.getMember(), inv, inv));
			}
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
    		//if (TrainCarts.showTransferAnimations) to = new InventoryWatcher(invlist.get(0), info.getMember(), to);

            
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
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			LinkedHashSet<Material> typesToCheck = SignActionCollect.parseName(type, "collect");
			if (typesToCheck.isEmpty()) return false;
			String[] types = new String[typesToCheck.size()];
			int i = 0;
			for (Material mat : typesToCheck) {
				types[i] = mat.toString().toLowerCase() + "s";
				i++;
			}
			return handleBuild(event, Permission.BUILD_COLLECTOR, "storage minecart item collector", 
					"obtain items from " + StringUtil.combineNames(types));
		}
		return false;
	}

}
