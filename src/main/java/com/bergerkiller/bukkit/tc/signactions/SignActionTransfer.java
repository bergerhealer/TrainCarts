package com.bergerkiller.bukkit.tc.signactions;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.InteractType;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.TransferSignUtil;

/**
 * Deals with item transfer between chests/furnaces/dispensers/other and the train
 */
public class SignActionTransfer extends SignAction {
	public static final String DEPOSIT = "deposit";
	public static final String COLLECT = "collect";
	public static final String KEY_TYPE_TARGET = "target";

	@Override
	public boolean match(SignActionEvent info) {
		return info.getMode() != SignActionMode.NONE && (!InteractType.parse(COLLECT, info.getLine(1)).isEmpty() 
				|| !InteractType.parse(DEPOSIT, info.getLine(1)).isEmpty());
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
			return;
		}
		if (!info.hasRails() || !info.isPowered()) {
			return;
		}
		final boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
		final boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
		if (!docart && !dotrain) {
			return;
		}
		// Deposit or collect? Obtain the other inventories
		boolean collect = true;
		Collection<InventoryHolder> otherInvs = TransferSignUtil.findBlocks(info, COLLECT);
		if (otherInvs.isEmpty()) {
			collect = false;
			otherInvs = TransferSignUtil.findBlocks(info, DEPOSIT);
			if (otherInvs.isEmpty()) {
				// Nothing to do here
				return;
			}
		}

		// Obtain the inventory holders of the train or minecart
		//get the inventory to transfer from
		Collection<InventoryHolder> trainInvs = TransferSignUtil.getInventories(info);
		if (trainInvs.isEmpty()) {
			return;
		}

		// Get item parsers to use for transferring
		// Make sure that the 'target' constant is properly updated
		if (collect) {
			setTargetConstant(trainInvs);
		} else {
			setTargetConstant(otherInvs);
		}
		ItemParser[] parsers = Util.getParsers(info.getLine(2), info.getLine(3));
		TrainCarts.plugin.putParsers(KEY_TYPE_TARGET, null);

		// Perform the transfer logic (collect OR deposit)
		if (collect) {
			// Collect logic: simplified
			for (ItemParser parser : parsers) {
				TransferSignUtil.transferAllItems(otherInvs, trainInvs, parser, false);
			}
		} else {
			// Deposit logic: take care of furnaces
			final int fuelHalfIndex;
			if (info.getLine(2).isEmpty()) {
				// Only line 3 is used - always fuel
				fuelHalfIndex = 0;
			} else if (info.getLine(3).isEmpty()) {
				// Only line 2 is used - always heatable
				fuelHalfIndex = Integer.MAX_VALUE;
			} else {
				// Neither lines are empty
				// Parsers starting at the second line are for fuel
				fuelHalfIndex = Util.getParsers(info.getLine(2)).length;
			}

			// Go through all item parsers, handling them one by one
			for (int i = 0; i < parsers.length; i++) {
				TransferSignUtil.transferAllItems(trainInvs, otherInvs, parsers[i], i >= fuelHalfIndex);
			}
		}
		// Perform physics on the 'other' inventories if they are blocks
		for (InventoryHolder holder : otherInvs) {
			if (holder instanceof BlockState) {
				BlockUtil.applyPhysics(((BlockState) holder).getBlock(), Material.AIR);
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		Collection<InteractType> typesToCheck = InteractType.parse(COLLECT, event.getLine(1));
		boolean collect = true;
		if (typesToCheck.isEmpty()) {
			collect = false;
			typesToCheck = InteractType.parse(DEPOSIT, event.getLine(1));
		}
		String[] types = new String[typesToCheck.size()];
		int i = 0;
		for (InteractType mat : typesToCheck) {
			types[i] = mat.toString().toLowerCase() + "s";
			i++;
		}
		// Show appropriate message and permission
		if (collect) {
			return handleBuild(event, Permission.BUILD_COLLECTOR, "storage minecart item collector", 
					"obtain items from " + StringUtil.combineNames(types));
		} else {
			return handleBuild(event, Permission.BUILD_DEPOSITOR, "storage minecart item depositor", 
					"make trains put items into " + StringUtil.combineNames(types));
		}
	}

	private static void setTargetConstant(Collection<InventoryHolder> inventories) {
		HashSet<String> types = new HashSet<String>();
		StringBuilder nameBuilder = new StringBuilder();
		for (InventoryHolder holder : inventories) {
			for (ItemStack item : holder.getInventory()) {
				if (LogicUtil.nullOrEmpty(item)) {
					continue;
				}
				nameBuilder.setLength(0);
				nameBuilder.append(item.getType().toString().toLowerCase(Locale.ENGLISH));
				if (MaterialUtil.HASDATA.get(item)) {
					nameBuilder.append(':');
					nameBuilder.append(item.getDurability());
				}
				types.add(nameBuilder.toString());
			}
		}
		ItemParser[] parsers = new ItemParser[types.size()];
		Iterator<String> iter = types.iterator();
		for (int i = 0; i < parsers.length; i++) {
			parsers[i] = ItemParser.parse(iter.next());
		}
		TrainCarts.plugin.putParsers(KEY_TYPE_TARGET, parsers);
	}
}
