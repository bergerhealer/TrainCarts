package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.ChatColor;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

public class SignActionChest extends SignAction {
	
	public boolean canBeUsed(final String owner, MinecartMember member) {
		return canBeUsed(owner, member.getProperties());
	}
	public boolean canBeUsed(final String owner, CartProperties prop) {
		if (owner.length() == 0) {
			return prop.isPublic;
		} else {
			return prop.isOwner(owner);
		}
	}

	@Override
	public void execute(SignActionEvent info) {
		boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
		boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
		if (!docart && !dotrain) return;
		if (!info.isPoweredFacing()) return;
		if (!info.hasRails()) return;
		boolean in = info.isType("chest in");
		boolean out = info.isType("chest out");
		if (!in && !out) return;
		//actual processing here
		Inventory[] chests = ItemUtil.getChests(info.getRails());
        if (chests.length == 0) return;
        final String owner = info.getLine(2);
        ItemParser parser = ItemParser.parse(info.getLine(3));
        int limit = (parser != null && parser.hasAmount()) ? parser.getAmount() : Integer.MAX_VALUE;
		if (docart) {
			if (info.getMember().isStorageMinecart()) {
				if (canBeUsed(owner, info.getMember())) {
					if (in) {
						ItemUtil.transfer(info.getMember().getInventory(), chests, parser, limit);
					} else {
						ItemUtil.transfer(chests, info.getMember().getInventory(), parser, limit);
					}
				}
			}
		} else if (dotrain) {
			for (MinecartMember mm : info.getGroup()) {
				if (mm.isStorageMinecart()) {
					if (canBeUsed(owner, mm)) {
						if (in) {
							ItemUtil.transfer(mm.getInventory(), chests, parser, limit);
						} else {
							ItemUtil.transfer(chests, mm.getInventory(), parser, limit);
						}
					}
				}
			}
		}
	}
	
	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("chest ")) {
				if (!event.getPlayer().hasPermission("train.build.chest.admin")) {
					String owner = event.getLine(2);
					if (owner.length() > 0) {
						if (!owner.equalsIgnoreCase(event.getPlayer().getName())) {
							event.getPlayer().sendMessage(ChatColor.RED + "You are not allowed to touch storage minecarts of others!");
							event.setCancelled(true);
							return;
						}
					}
				}
				if (type.startsWith("chest in")) {
					handleBuild(event, Permission.BUILD_CHEST, "storage minecart to chest dispenser", 
							"transfer items from storage minecarts to multiple chests connected to the tracks");
				} else if (type.startsWith("chest out")) {
					handleBuild(event, Permission.BUILD_CHEST, "chest to storage minecart dispenser", 
							"transfer items from multiple chests connected to the tracks to storage minecarts");
				}
			}
		}
	}

}
