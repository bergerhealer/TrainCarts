package com.bergerkiller.bukkit.tc;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.tc.utils.ItemUtil;

public class ItemParser {
	
	private ItemParser(boolean hasdata) {
		this.hasdata = hasdata;
	}
	private byte data = 0;
	private Material type = null;
	private int amount = 1;
	private boolean hasdata, hasamount;
	
	public static ItemParser parse(String fullname) {
		int index = fullname.lastIndexOf(' ');
		if (index == -1) {
			return parse(fullname, null);
		} else {
			return parse(fullname.substring(0, index), fullname.substring(index + 1));
		}
	}
	public static ItemParser parse(String name, String amount) {
		int index = name.indexOf(':');
		if (index == -1) {
			return parse(name, null, amount);
		} else {
			return parse(name.substring(0, index), name.substring(index + 1), amount);
		}
	}
	public static ItemParser parse(String name, String dataname, String amount) {
		ItemParser parser = new ItemParser(dataname != null);
		if (amount == null) {
			parser.hasamount = false;
		} else {
			try {
				parser.amount = Integer.parseInt(amount);
				parser.hasamount = true;
			} catch (NumberFormatException ex) {
				parser.hasamount = false;
			}
		}
		//match material from name
		parser.type = ItemUtil.getMaterial(name);
		//match data name if needed
		if (parser.hasdata) {
			Byte dat = ItemUtil.getData(parser.type, dataname);
			if (dat == null) {
				parser.hasdata = false;
			} else {
				parser.data = dat;
			}
		}
		return parser;
	}
	
	public boolean match(net.minecraft.server.ItemStack stack) {
		return this.match(stack.id, stack.getData());
	}
	public boolean match(ItemStack stack) {
		return this.match(stack.getTypeId(), stack.getData().getData());
	}
	public boolean match(Material type, int data) {
		return this.match(type.getId(), data);
	}
	public boolean match(int typeid, int data) {
		if (this.hasType() && typeid != this.getTypeId()) return false; 
		if (this.hasData() && data != this.getData()) return false;
		return true;
	}
	
	public boolean hasAmount() {
		return this.hasamount;
	}
	public boolean hasData() {
		return this.hasdata;
	}
	public boolean hasType() {
		return this.type != null;
	}
	public byte getData() {
	    return this.data;
	}
	public int getAmount() {
		return this.amount;
	}
	public Material getType() {
		return this.type;
	}
	public int getTypeId() {
		return this.type.getId();
	}
	public ItemStack getItemStack() {
		return this.getItemStack(this.amount);
	}
	public ItemStack getItemStack(int amount) {
		return new ItemStack(this.type, this.amount, this.data);
	}
	public int getMaxStackSize() {
		if (this.hasData()) {
			return this.getItemStack(1).getMaxStackSize();
		} else {
			return this.getType().getMaxStackSize();
		}
	}

}
