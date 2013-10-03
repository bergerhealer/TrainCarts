package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberChest;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementItems extends Statement {

	@Override
	public boolean match(String text) {
		return text.startsWith("items");
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		final Inventory inventory = getInventory(member);
		if (inventory == null) {
			return false;
		}
		int count = ItemUtil.getItemCount(getInventory(member), -1, -1);
		return Util.evaluate(count, text);
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		final Inventory inventory = getInventory(group);
		if (inventory == null) {
			return false;
		}
		int count = ItemUtil.getItemCount(inventory, -1, -1);
		return Util.evaluate(count, text);
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("i");
	}
	
	public boolean handleInventory(Inventory inv, String[] items) {
		if (inv == null) {
			return false;
		}
		int opidx;
		int count;
		for (String itemname : items) {
			opidx = Util.getOperatorIndex(itemname);
			String itemnamefixed;
			if (opidx > 0) {
				itemnamefixed = itemname.substring(0, opidx);
			} else {
				itemnamefixed = itemname;
			}
			for (ItemParser parser : Util.getParsers(itemnamefixed)) {
				count = ItemUtil.getItemCount(inv, parser.getTypeId(), parser.getData());
				if (opidx == -1) {
					if (parser.hasAmount()) {
						if (count >= parser.getAmount()) {
							return true;
						}
					} else if (count > 0) {
						return true;
					}
				} else if (Util.evaluate(count, itemname)) {
					return true;
				}
			}
			// Check for 'special' named items
			count = 0;
			for (ItemStack item : inv) {
				if (item != null && ItemUtil.hasDisplayName(item) && ItemUtil.getDisplayName(item).equals(itemnamefixed)) {
					count += item.getAmount();
				}
			}
			if (Util.evaluate(count, itemname)) {
				return true;
			}
		}
		return false;
	}

	public Inventory getInventory(MinecartMember<?> member) {
		if (member instanceof MinecartMemberChest) {
			return ((MinecartMemberChest) member).getEntity().getInventory();
		} else {
			return null;
		}
	}

	public Inventory getInventory(MinecartGroup group) {
		return group.getInventory();
	}

	@Override
	public boolean handleArray(MinecartMember<?> member, String[] items, SignActionEvent event) {
		return handleInventory(getInventory(member), items);
	}
	
	@Override
	public boolean handleArray(MinecartGroup group, String[] items, SignActionEvent event) {
		return handleInventory(getInventory(group), items);
	}
}
