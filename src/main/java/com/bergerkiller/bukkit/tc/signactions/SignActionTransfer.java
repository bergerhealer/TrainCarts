package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.bukkit.inventory.InventoryHolder;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.InteractType;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.TransferSignUtil;

/**
 * Deals with item transfer between chests/furnaces/dispensers/other and the train
 */
public class SignActionTransfer extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.getMode() != SignActionMode.NONE && (!InteractType.parse("collect", info.getLine(1)).isEmpty() 
				|| !InteractType.parse("deposit", info.getLine(1)).isEmpty());
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
		Collection<InventoryHolder> otherInvs = TransferSignUtil.findBlocks(info, "collect");
		if (otherInvs.isEmpty()) {
			collect = false;
			otherInvs = TransferSignUtil.findBlocks(info, "deposit");
			if (otherInvs.isEmpty()) {
				// Nothing to do here
				return;
			}
		}

		// Obtain the inventory holders of the train or minecart
		//get the inventory to transfer from
		Collection<InventoryHolder> trainInvs;
		if (docart) {
			if (!info.getMember().isStorageCart()) {
				return;
			}
			trainInvs = Arrays.asList((InventoryHolder) info.getMember().getMinecart());
		} else {
			trainInvs = new ArrayList<InventoryHolder>(info.getGroup().size());
			for (MinecartMember member : info.getGroup()) {
				if (member.isStorageCart()) {
					trainInvs.add((InventoryHolder) member.getMinecart());
				}
			}
		}

		//get item parsers to use for transferring
		ItemParser[] parsers = Util.getParsers(info.getLine(2), info.getLine(3));

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
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		Collection<InteractType> typesToCheck = InteractType.parse("collect", event.getLine(1));
		boolean collect = true;
		if (typesToCheck.isEmpty()) {
			collect = false;
			typesToCheck = InteractType.parse("deposit", event.getLine(1));
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
}
