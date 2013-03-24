package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionBlockChanger extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("blockchanger", "setblock", "changeblock");
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isPowered()) {
			return;
		}
		ItemParser[] blocks = Util.getParsers(info.getLine(2), info.getLine(3));
		if (info.isTrainSign() && info.hasGroup() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
			setBlocks(info.getGroup(), blocks);
		} else if (info.isCartSign() && info.hasMember() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
			List<MinecartMember<?>> tmp = new ArrayList<MinecartMember<?>>(1);
			tmp.add(info.getMember());
			setBlocks(tmp, blocks);
		} else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
			for (MinecartGroup group : info.getRCTrainGroups()) {
				setBlocks(group, blocks);
			}
		} else {
			return;
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.isCartSign()) {
			return handleBuild(event, Permission.BUILD_BLOCKCHANGER, "cart block changer", "change the block displayed in a minecart");
		} else if (event.isTrainSign()) {
			return handleBuild(event, Permission.BUILD_BLOCKCHANGER, "train block changer", "change the blocks displayed in a train");
		} else if (event.isRCSign()) {
			return handleBuild(event, Permission.BUILD_BLOCKCHANGER, "train block changer", "change the blocks displayed in a train remotely");
		}
		return false;
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}

	private static void setBlocks(List<MinecartMember<?>> members, ItemParser[] blocks) {
		Iterator<MinecartMember<?>> iter = members.iterator();
		while (true) {
			for (ItemParser block : blocks) {
				final int amount = block.hasAmount() ? block.getAmount() : 1;
				for (int i = 0; i < amount; i++) {
					if (!iter.hasNext()) {
						return;
					}
					iter.next().getEntity().setBlock(block.getTypeId(), block.getData());
				}
			}
		}
	}
}
