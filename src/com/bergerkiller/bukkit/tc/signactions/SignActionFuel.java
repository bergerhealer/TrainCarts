package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.v1_4_6.ItemStack;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;

import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation;
import com.bergerkiller.bukkit.tc.utils.TransferSignUtil;

public class SignActionFuel extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
			return;
		}
		
		if (info.isType("fuel")) {			
			//parse the sign
			boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
			boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
			if (!docart && !dotrain) return;
			if (!info.isPowered()) return;
			
			//get nearby chests
			int radius = ParseUtil.parseInt(info.getLine(1), TrainCarts.defaultTransferRadius);
			List<Chest> chests = new ArrayList<Chest>();
			for (BlockState state : TransferSignUtil.getBlockStates(info, radius)) {
				if (state instanceof Chest) {
					chests.add((Chest) state);
				}
			}
			if (chests.isEmpty()) {
				return;
			}

			List<MinecartMember> carts;
			if (dotrain) {
				carts = info.getGroup();
			} else {
				carts = new ArrayList<MinecartMember>(1);
				carts.add(info.getMember());
			}

			int i;
			boolean found = false;
			for (MinecartMember member : carts) {
				if (member.isPoweredCart() && !member.hasFuel()) {
					found = false;
					for (Chest chest : chests) {
						Inventory inv = chest.getInventory();
						for (i = 0; i < inv.getSize(); i++) {
							org.bukkit.inventory.ItemStack item = inv.getItem(i);
							if (!LogicUtil.nullOrEmpty(item) && item.getTypeId() == Material.COAL.getId()) {
								ItemUtil.subtractAmount(item, 1);
								inv.setItem(i, item);
								found = true;
								member.addFuel(3600);
								if (TrainCarts.showTransferAnimations) {
									ItemAnimation.start(chest, member, new ItemStack(Material.COAL.getId(), 1, 0));
								}
								break;
							}
						}
						if (found) break;
					}
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE && event.isType("fuel")) {
			return handleBuild(event, Permission.BUILD_COLLECTOR, "powered minecart coal collector", 
					"fuel the powered minecart using coal from a chest");
		}
		return false;
	}
}
