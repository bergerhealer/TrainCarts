package com.bergerkiller.bukkit.tc.signactions;

import java.util.Collection;

import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.InteractType;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.InventoryWatcher;
import com.bergerkiller.bukkit.tc.utils.TransferSignUtil;

public class SignActionCollect extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return !InteractType.parse("collect", info.getLine(1)).isEmpty();
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
			return;
		}
		if (!info.hasRails() || !info.isPowered()) {
			return;
		}

		//parse the sign
		boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
		boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
		if (!docart && !dotrain) {
			return;
		}

		Collection<BlockState> blocks = TransferSignUtil.findBlocks(info, "collect");
		if (blocks.isEmpty()) {
			return;
		}

		//get the inventory to transfer to
		Inventory to;
		if (docart) {
			if (!info.getMember().isStorageCart()) {
				return;
			}
			to = info.getMember().getInventory();
		} else {
			to = info.getGroup().getInventory();
		}

		//get items parsers to use for transferring
		ItemParser[] parsers = Util.getParsers(info.getLine(2), info.getLine(3));

		// Go through all the inventories to collect
		int amount;
		for (BlockState block : blocks) {
			if (!(block instanceof InventoryHolder)) {
				continue;
			}

			// Obtain the inventory
			Inventory inv = ((InventoryHolder) block).getInventory();
			if (TrainCarts.showTransferAnimations) {
				inv = InventoryWatcher.convert(inv, block, info.getMember());
			}

			// Parse all the parsers
			for (int i = 0; i < parsers.length; i++) {
				ItemParser p = parsers[i];
				if (block instanceof Furnace) {
					// Collect from result slot
					ItemStack result = inv.getItem(2);
					if (p.match(result)) {
						amount = ItemUtil.transfer(result, to, p.getAmount());
						if (amount > 0) {
							inv.setItem(2, result);
						}
					} else {
						amount = 0;
					}
				} else {
					// Collect all contents
					amount = ItemUtil.transfer(inv, to, p, p.getAmount());
				}
				// Update parser amount
				if (amount > 0 && p.hasAmount()) {
					p = p.setAmount(p.getAmount() - amount);
				}
				parsers[i] = p;
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
