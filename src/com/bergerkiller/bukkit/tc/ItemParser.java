package com.bergerkiller.bukkit.tc;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemParser {
	
	private ItemParser(boolean hasdata) {
		this.hasdata = hasdata;
	}
	private byte data = 0;
	private Material type;
	private boolean hasdata;
	
	public static ItemParser parse(String name) {
		int index = name.indexOf(':');
		if (index == -1) {
			return parse(name, null);
		} else {
			return parse(name.substring(0, index), name.substring(index + 1));
		}
	}
	public static ItemParser parse(String name, String dataname) {
		ItemParser parser = new ItemParser(dataname != null);
		//match material from name
		parser.type = Util.getMaterial(name);
		if (parser.type == null) return null;
		//match data name if needed
		if (parser.hasdata) {
			Byte dat = Util.getData(parser.type, dataname);
			if (dat == null) {
				parser.hasdata = false;
			} else {
				parser.data = dat;
			}
		}
		return parser;
	}
	
	public boolean compare(net.minecraft.server.ItemStack stack) {
		return this.compare(stack.id, stack.getData());
	}
	public boolean compare(ItemStack stack) {
		return this.compare(stack.getTypeId(), stack.getData().getData());
	}
	public boolean compare(Material type, int data) {
		return this.compare(type.getId(), data);
	}
	public boolean compare(int typeid, int data) {
		if (typeid != this.getTypeId()) return false; 
		if (this.hasdata) {
			return this.data == data;
		} else {
			return true;
		}
	}
	
	public boolean hasData() {
		return this.hasdata;
	}
	public byte getData() {
	    return this.data;
	}
	public Material getType() {
		return this.type;
	}
	public int getTypeId() {
		return this.type.getId();
	}

}
