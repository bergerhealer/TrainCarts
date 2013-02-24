package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.RecipeUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.InteractType;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.InventoryWatcher;
import com.bergerkiller.bukkit.tc.utils.GroundItemsInventory;
import com.bergerkiller.bukkit.tc.utils.TransferSignUtil;

public class SignActionDeposit extends SignAction {

	public static int depositInFurnace(Inventory from, Inventory to, Furnace toFurnace, ItemParser parser, boolean parserIsFuelHalf) {
		List<ItemParser> heatables = new ArrayList<ItemParser>();
		List<ItemParser> fuels = new ArrayList<ItemParser>();
		if (!parser.hasType()) {
			// Add all heatables and fuels
			for (ItemParser p : TrainCarts.plugin.getParsers("heatable")) {
				if (p == null || !p.hasType()) {
					heatables.clear();
					break;
				} else {
					heatables.add(p);
				}
			}
			for (ItemParser p : TrainCarts.plugin.getParsers("fuel")) {
				if (p == null || !p.hasType()) {
					fuels.clear();
					break;
				} else {
					fuels.add(p);
				}
			}
			if (heatables.isEmpty() && fuels.isEmpty()) {
				return 0;
			}
		} else {
			// Is the parser fuel or heatable?
			boolean heatable = RecipeUtil.isHeatableItem(parser.getTypeId());
			boolean fuel = RecipeUtil.isFuelItem(parser.getTypeId());
			if (heatable && fuel) {
				if (parserIsFuelHalf) {
					fuels.add(parser);
				} else {
					heatables.add(parser);
				}
			} else if (heatable) {
				heatables.add(parser);
			} else if (fuel) {
				fuels.add(parser);
			} else {
				return 0;
			}
		}
		final int startAmount = parser.hasAmount() ? parser.getAmount() : Integer.MAX_VALUE;
		int amountToTransfer = startAmount;

		// Transfer heatable items
		for (ItemParser p : heatables) {
			ItemStack item = to.getItem(0);
			if (item == null) {
				item = ItemUtil.emptyItem();
			}
			amountToTransfer -= ItemUtil.transfer(from, item, p, amountToTransfer);
			to.setItem(0, item);
		}

		// Transfer fuel (requires manual limiting if no amount is set)
		for (ItemParser p : fuels) {
			if (p == null) {
				continue;
			}
			if (amountToTransfer == 0) {
				break;
			}

			int transferCount = amountToTransfer;
			ItemStack fuel = to.getItem(1);
			if (fuel == null) {
				fuel = ItemUtil.emptyItem();
			}
			if (!p.hasAmount()) {
				// Fill the minimal amount needed to burn all the heatables in the furnace
				ItemStack cookeditem = to.getItem(0);
				if (cookeditem == null || cookeditem.getTypeId() == 0) continue;
				int fuelNeeded = cookeditem.getAmount() * 200;
				if (fuelNeeded == 0) continue; //nothing to cook
				//===================================================
				fuelNeeded -= toFurnace.getCookTime();
				if (fuelNeeded <= 0) continue; //we got enough
				//===================================================
				int fuelPerItem = 0;
				if (fuel.getTypeId() == 0) {
					fuelPerItem = RecipeUtil.getFuelTime(p.getTypeId());
				} else {
					fuelPerItem = RecipeUtil.getFuelTime(fuel.getTypeId());
				}
				//====================================================
				if (fuelPerItem == 0) continue;
				fuelNeeded -= fuelPerItem * fuel.getAmount();
				if (fuelNeeded <= 0) continue;
				//====================================================
				transferCount = Math.min(amountToTransfer, (int) Math.ceil((double) fuelNeeded / (double) fuelPerItem));
			}
			amountToTransfer -= ItemUtil.transfer(from, fuel, p, transferCount);
			to.setItem(1, fuel);
		}
		return startAmount - amountToTransfer;
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

		Collection<BlockState> blocks = TransferSignUtil.findBlocks(info, "deposit");
		if (blocks.isEmpty()) {
			return;
		}

		//get the inventory to transfer from
		Inventory from;
		if (docart) {
			if (!info.getMember().isStorageCart()) {
				return;
			}
			from = info.getMember().getInventory();
		} else {
			from = info.getGroup().getInventory();
		}

		//get item parsers to use for transferring
		ItemParser[] parsers = Util.getParsers(info.getLine(2), info.getLine(3));
		int furnaceFuelOffset = 0; // 0 by default, sees both lines as fuel
		if (!info.getLine(2).isEmpty() && !info.getLine(3).isEmpty()) {
			// Only second line is seen as fuel
			furnaceFuelOffset = Util.getParsers(info.getLine(2)).length;
		}

		// Go through all the inventories to deposit items in
		int amount;
		for (BlockState block : blocks) {
			if (!(block instanceof InventoryHolder)) {
				continue;
			}
			// Obtain the inventory
			Inventory inv = ((InventoryHolder) block).getInventory();
			// Do not deposit using animations for ground items, it shows duplicates which looks bad
			if (TrainCarts.showTransferAnimations && !(inv instanceof GroundItemsInventory)) {
				inv = InventoryWatcher.convert(inv, block, info.getMember());
			}

			// Parse all the parsers
			for (int i = 0; i < parsers.length; i++) {
				ItemParser p = parsers[i];
				if (block instanceof Furnace) {
					// Deposit into fuel and heatable slots
					amount = depositInFurnace(from, inv, (Furnace) block, p, i >= furnaceFuelOffset);
				} else {
					// Collect all contents
					amount = ItemUtil.transfer(from, inv, p, p.getAmount());
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
			Collection<InteractType> typesToCheck = InteractType.parse("deposit", event.getLine(1));
			if (typesToCheck.isEmpty()) return false;
			String[] types = new String[typesToCheck.size()];
			int i = 0;
			for (InteractType mat : typesToCheck) {
				types[i] = mat.toString().toLowerCase() + "s";
				i++;
			}
			return handleBuild(event, Permission.BUILD_DEPOSITOR, "storage minecart item depositor", 
					"make trains put items into " + StringUtil.combineNames(types));
		}
		return false;
	}
}
