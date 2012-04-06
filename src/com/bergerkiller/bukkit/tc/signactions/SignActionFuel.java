package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.ItemStack;
import net.minecraft.server.TileEntity;
import net.minecraft.server.TileEntityChest;

import org.bukkit.Material;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation;

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
			
			//get nearby chests
			int radius = Util.parse(info.getLine(1), TrainCarts.defaultTransferRadius);
			
			List<TileEntityChest> chests = new ArrayList<TileEntityChest>();
			for (TileEntity tile : SignActionCollect.getTileEntities(info, radius)) {
				if (tile instanceof TileEntityChest) {
					chests.add((TileEntityChest) tile);
				}
			}
			
			if (chests.isEmpty()) return;

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
					for (TileEntityChest chest : chests) {
						for (i = 0; i < chest.getSize(); i++) {
							ItemStack item = chest.getItem(i);
							if (item != null && item.id == Material.COAL.getId()) {
								item.count--;
								chest.setItem(i, item.count == 0 ? null : item);
								found = true;
								member.fuel += 3600;
								member.b = member.motX;
								member.c = member.motZ;
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
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE && type.startsWith("fuel")) {
			handleBuild(event, Permission.BUILD_COLLECTOR, "powered minecart coal collector", 
					"fuel the powered minecart using coal from a chest");
		}
	}

}
