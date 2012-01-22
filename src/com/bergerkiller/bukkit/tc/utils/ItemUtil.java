package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.logging.Level;

import me.Perdog.BleedingMobs.BleedingMobs;
import net.minecraft.server.IInventory;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.TreeSpecies;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.material.TexturedMaterial;
import org.bukkit.material.Tree;
import org.bukkit.material.Wool;

import com.bergerkiller.bukkit.tc.ItemParser;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.miykeal.showCaseStandalone.ShowCaseStandalone;
import com.narrowtux.showcase.Showcase;

public class ItemUtil {
	
	public static void transfer(IInventory from, IInventory to) {
		net.minecraft.server.ItemStack[] items = from.getContents();
		for (int i = 0;i < items.length;i++) {
			if (items[i] != null) {
				to.setItem(i, new net.minecraft.server.ItemStack(items[i].id, items[i].count, items[i].b));
			}
		}
		for (int i = 0;i < items.length;i++) from.setItem(i, null);
	}
	
	public static int transfer(Inventory from, Inventory to, ItemParser parser, int limit) {
		return transfer(from, new Inventory[] {to}, parser, limit);
	}
	public static int transfer(Inventory[] from, Inventory to, ItemParser parser, int limit) {
		return transfer(from, new Inventory[] {to}, parser, limit);
	}
	public static int transfer(Inventory[] from, Inventory[] to, ItemParser parser, int limit) {
		if (limit == 0) return 0;
		int transferred = 0;
		for (Inventory ifrom : from) {
			transferred += transfer(ifrom, to, parser, limit - transferred);
		}
		return transferred;
	}
	public static int transfer(Inventory from, Inventory[] to, ItemParser parser, int limit) {
		if (limit == 0) return 0;
		ItemStack item;
		int transferred = 0;
		for (int i = 0; i < from.getSize(); i++) {
			item = from.getItem(i);
			if (item == null) continue;
			if (parser != null && !parser.match(item)) continue;
			transferred += transfer(item, to, limit - transferred);
			if (item.getAmount() == 0) from.setItem(i, null);
		}
		return transferred;
	}
	
	public static int transfer(ItemStack item, Inventory[] inventories, int limit) {
		if (limit == 0) return 0;
		int transferred = 0;
		for (Inventory inv : inventories) {
			transferred += transfer(item, inv, limit - transferred);
			if (item.getAmount() <= 0) break;
		}
		return transferred;
	}
	public static int transfer(ItemStack item, Inventory inventory, int limit) {
		if (limit == 0) return 0;
		int transferred = 0;
		if (item == null) return transferred;
		if (item.getTypeId() == 0) return transferred;
		if (item.getAmount() <= 0) return transferred;
		
		//try to add to already existing items
		for (ItemStack iitem : inventory.getContents()) {
			transferred += transfer(item, iitem, limit - transferred);
		}
		limit -= transferred;
		//try to add it to empty slots
		if (limit > 0 && item.getAmount() > 0) {
			for (int i = 0; i < inventory.getSize(); i++) {
				if (inventory.getItem(i) != null) {
					if (inventory.getItem(i).getTypeId() != 0) continue;
				}
				if (item.getAmount() <= limit) {
					transferred += item.getAmount();
					inventory.setItem(i, item.clone());
					item.setAmount(0);
				} else {
					ItemStack newitem = item.clone();
					newitem.setAmount(limit);
					transferred += limit;
					inventory.setItem(i, newitem);
					item.setAmount(item.getAmount() - limit);
				}
				break;
			}
		}
		return transferred;
	}
	public static int transfer(ItemStack from, ItemStack to, int limit) {
		if (limit == 0) return 0;
		final int max = getMaxSize(from);
		if (!canTransfer(from, to, max)) return 0;
		final int newamount, remainder, trans;
		if (from.getAmount() <= limit) {
			newamount = from.getAmount() + to.getAmount();
			remainder = 0;
		} else {
			newamount = limit + to.getAmount();
			remainder = from.getAmount() - limit;
		}
		if (newamount >= max) {
			trans = max - to.getAmount();
			to.setAmount(max);
			from.setAmount(newamount - max + remainder);
		} else {
			trans = newamount - to.getAmount();
			to.setAmount(newamount);
			from.setAmount(remainder);
		}
		return trans;
	}
	
	public static int getMaxSize(ItemStack stack) {
		if (stack == null) return 0;
		if (TrainCarts.stackMinecarts && isMinecartItem(stack.getType())) return 64;
		int max = stack.getMaxStackSize();
		if (max == -1) max = 64;
		return max;
	}
	
	public static boolean canTransfer(ItemStack from, Inventory to) {
		final int max = getMaxSize(from);
		for (ItemStack item : to.getContents()) {
			if (item == null) return true;
			if (item.getTypeId() == 0) return true;
			if (canTransfer(from, item, max)) return true;
		}
		return false;
	}
	public static boolean canTransfer(ItemStack from, ItemStack to) {
		return canTransfer(from, to, getMaxSize(from));
	}
	public static boolean canTransfer(ItemStack from, ItemStack to, final int maxstacksize) {
		if (from == null || to == null) return false;
		if (to.getTypeId() != from.getTypeId()) return false;
		if (to.getDurability() != from.getDurability()) return false;
		if (from.getAmount() <= 0) return false;
		if (from.getAmount() >= maxstacksize) return false;
		if (to.getAmount() >= maxstacksize) return false;
		return true;
	}
	
	public static boolean isMinecartItem(Item item) {
		return isMinecartItem(item.getItemStack().getType());
	}
	public static boolean isMinecartItem(Material type) {
		switch (type) {
		case MINECART :
		case POWERED_MINECART :
		case STORAGE_MINECART : return true;
		default : return false;
		}
	}
		
	public static Byte getData(Material type, String name) {
		try {
			return Byte.parseByte(name);
		} catch (NumberFormatException ex) {
			MaterialData dat = type.getNewData((byte) 0);
			if (dat instanceof TexturedMaterial) {
				TexturedMaterial tdat = (TexturedMaterial) dat;
				tdat.setMaterial(getMaterial(name));
			} else if (dat instanceof Wool) {
				Wool wdat = (Wool) dat;
				wdat.setColor(getDyeColor(name));
			} else if (dat instanceof Tree) {
				Tree tdat = (Tree) dat;
				tdat.setSpecies(getTreeSpecies(name));
			} else {
				return null;
			}
			return dat.getData();
		}
	}
	public static TreeSpecies getTreeSpecies(String name) {
		name = name.toUpperCase();
		for (TreeSpecies specie : TreeSpecies.values()) {
			if (specie.toString().contains(name)) return specie;
		}
		return null;
	}
	public static DyeColor getDyeColor(String name) {
		name = name.toUpperCase();
		for (DyeColor color : DyeColor.values()) {
			if (color.toString().contains(name)) return color;
		}
		return null;
	}	
    public static Material getMaterial(String name) {
    	Material m = Material.matchMaterial(name);
    	name = name.trim().replace(" ", "_").toUpperCase().replace("SHOVEL", "SPADE").replace("SLAB", "STEP").replace("GOLDEN", "GOLD");       	
    	if (m == null) m = Material.getMaterial(name);
    	if (name.equalsIgnoreCase("CROP")) m = Material.CROPS;
    	if (name.equalsIgnoreCase("WOODEN_DOOR")) m = Material.WOOD_DOOR;
    	if (name.equalsIgnoreCase("IRON_DOOR_BLOCK")) m = Material.IRON_DOOR;
    	if (name.equalsIgnoreCase("REPEATER")) m = Material.DIODE;
    	if (name.equalsIgnoreCase("REDSTONE_REPEATER")) m = Material.DIODE;
    	if (name.equalsIgnoreCase("REDSTONE_DUST")) m = Material.REDSTONE;
    	if (name.equalsIgnoreCase("REDSTONE_TORCH")) m = Material.REDSTONE_TORCH_ON;
    	if (name.equalsIgnoreCase("STONE_PRESSURE_PLATE")) m = Material.STONE_PLATE;
    	if (name.equalsIgnoreCase("BUTTON")) m = Material.STONE_BUTTON;
    	if (name.equalsIgnoreCase("WOOD_PRESSURE_PLATE")) m = Material.WOOD_PLATE;
    	if (name.equalsIgnoreCase("WOODEN_PRESSURE_PLATE")) m = Material.WOOD_PLATE;
    	if (name.equalsIgnoreCase("PISTON")) m = Material.PISTON_BASE;	
       	if (name.equalsIgnoreCase("STICKY_PISTON")) m = Material.PISTON_STICKY_BASE;
       	if (name.equalsIgnoreCase("MOSS_STONE")) m = Material.MOSSY_COBBLESTONE;
       	if (name.equalsIgnoreCase("STONE_STAIRS")) m = Material.COBBLESTONE_STAIRS;
       	if (name.equalsIgnoreCase("WOODEN_STAIRS")) m = Material.WOOD_STAIRS;  	
       	if (name.equalsIgnoreCase("DIAM_CHESTPLATE")) m = Material.DIAMOND_CHESTPLATE; 
       	if (name.equalsIgnoreCase("DIAM_LEGGINGS")) m = Material.DIAMOND_LEGGINGS; 
       	if (name.equalsIgnoreCase("LEAT_CHESTPLATE")) m = Material.LEATHER_CHESTPLATE; 
       	if (name.equalsIgnoreCase("LEAT_LEGGINGS")) m = Material.LEATHER_LEGGINGS;     	
       	if (name.equalsIgnoreCase("LEATHER_PANTS")) m = Material.LEATHER_LEGGINGS;  
    	if (name.equalsIgnoreCase("LIGHTER")) m = Material.FLINT_AND_STEEL;  
    	if (name.equalsIgnoreCase("DOUBLE_SLAB")) m = Material.DOUBLE_STEP;
    	if (name.equalsIgnoreCase("DOUBLESLAB")) m = Material.DOUBLE_STEP;
    	if (name.equalsIgnoreCase("BOOK_SHELF")) m = Material.BOOKSHELF;
    	if (name.equalsIgnoreCase("LIT_PUMPKIN")) m = Material.JACK_O_LANTERN;
    	if (m != null) {
    		return m;
    	} else if (name.endsWith("S")) {  	
    		return getMaterial(name.substring(0, name.length() - 1));
    	} else {
    	    try {
    	    	return Material.getMaterial(Integer.parseInt(name));
    	    } catch (Exception ex) {
    	    	return null;
    	    }
    	}
	}

	public static Inventory[] getChests(Block attached) {
		ArrayList<Inventory> invs = new ArrayList<Inventory>();
		Block c1, c2;
		for (BlockFace face : FaceUtil.axis) {
			c1 = attached.getRelative(face);
			if (c1.getType() != Material.CHEST) continue;
			invs.add(((Chest) c1.getState()).getInventory());
			for (BlockFace sface : FaceUtil.axis) { 
				c2 = c1.getRelative(sface);
				if (c2.getType() != Material.CHEST) continue;
				invs.add(((Chest) c2.getState()).getInventory());
			}
		}	
		return invs.toArray(new Inventory[0]);
	}

	public static boolean isIgnoredItem(Entity itementity) {
		if (!(itementity instanceof Item)) return true; 
		Item item = (Item) itementity;
		if (TrainCarts.isShowcaseEnabled) {
			try {
		        if (Showcase.instance.getItemByDrop(item) != null) return true;
			} catch (Throwable t) {
				Util.log(Level.SEVERE, "Showcase item verification failed (update needed?), contact the authors!");
				t.printStackTrace();
				TrainCarts.isShowcaseEnabled = false;
			}
		}
		if (TrainCarts.isSCSEnabled) { 
			try {
				if (ShowCaseStandalone.get().isShowCaseItem(item)) return true;
			} catch (Throwable t) {
				Util.log(Level.SEVERE, "ShowcaseStandalone item verification failed (update needed?), contact the authors!");
		        t.printStackTrace();
		        TrainCarts.isSCSEnabled = false;
			}
		}
		if (TrainCarts.bleedingMobsInstance != null) {
			try {
				BleedingMobs bm = (BleedingMobs) TrainCarts.bleedingMobsInstance;
				if (bm.isSpawning()) return true;
				if (bm.isWorldEnabled(item.getWorld())) {
					if (bm.isParticleItem(((CraftItem) item).getUniqueId())) {
						return true;
					}
				}
			} catch (Throwable t) {
				Util.log(Level.SEVERE, "Bleeding Mobs item verification failed (update needed?), contact the authors!");
		        t.printStackTrace();
		        TrainCarts.bleedingMobsInstance = null;
			}
		}
		return false;
	}
}
