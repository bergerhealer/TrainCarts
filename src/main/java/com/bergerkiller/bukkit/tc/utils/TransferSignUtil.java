package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.inventory.InventoryBase;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.RecipeUtil;
import com.bergerkiller.bukkit.tc.InteractType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberChest;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimatedInventory;

/**
 * Utilities for dealing with item transfers between different containers
 */
public class TransferSignUtil {

	public static Inventory getInventory(SignActionEvent info) {
		if (info.isCartSign()) {
			if (info.getMember() instanceof MinecartMemberChest) {
				return ((MinecartMemberChest) info.getMember()).getEntity().getInventory();
			} else {
				return null;
			}
		} else {
			return info.getGroup().getInventory();
		}
	}

	public static Collection<InventoryHolder> getInventories(SignActionEvent info) {
		if (info.isCartSign()) {
			if (info.getMember() instanceof MinecartMemberChest) {
				return Arrays.asList((InventoryHolder) info.getMember().getEntity().getEntity());
			} else {
				return Collections.emptyList();
			}
		} else {
			Collection<InventoryHolder> trainInvs = new ArrayList<InventoryHolder>(info.getGroup().size());
			for (MinecartMember<?> member : info.getGroup()) {
				if (member instanceof MinecartMemberChest) {
					trainInvs.add((InventoryHolder) member.getEntity().getEntity());
				}
			}
			return trainInvs;
		}
	}

	public static int depositInFurnace(Inventory from, Furnace toFurnace, ItemParser parser, boolean isFuelPreferred) {
		final Inventory to = toFurnace.getInventory();
		List<ItemParser> heatables = new ArrayList<ItemParser>();
		List<ItemParser> fuels = new ArrayList<ItemParser>();
		if (!parser.hasType()) {
			// Add all heatables and fuels
			for (ItemParser p : TrainCarts.plugin.getParsers("heatable", 1)) {
				if (p == null || !p.hasType()) {
					heatables.clear();
					break;
				} else {
					heatables.add(p);
				}
			}
			for (ItemParser p : TrainCarts.plugin.getParsers("fuel", 1)) {
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
				if (isFuelPreferred) {
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

	public static Collection<BlockState> getBlockStates(SignActionEvent info, int radius) {
		int radX = radius;
		int radZ = radius;
		BlockFace dir = info.getRailDirection();
		if (FaceUtil.isAlongX(dir)) {
			radX = 0;
		} else if (FaceUtil.isAlongZ(dir)) {
			radZ = 0;
		}
		return BlockUtil.getBlockStates(info.getRails(), radX, radius, radZ);
	}

	/**
	 * Finds all nearby block states with which can be interacted according to the sign
	 * 
	 * @param info to use
	 * @param mode to use, collect or dispense
	 * @return A collection of BlockStates to operate on
	 */
	public static Collection<InventoryHolder> findBlocks(SignActionEvent info, String mode) {
		Collection<InteractType> typesToCheck = InteractType.parse(mode, info.getLine(1));
		if (typesToCheck.isEmpty()) {
			return Collections.emptyList();
		}

		//get the block types to collect and the radius (2nd line)
		int radius = ParseUtil.parseInt(info.getLine(1), TrainCarts.defaultTransferRadius);

		//get the tile entities to collect
		Collection<BlockState> found = TransferSignUtil.getBlockStates(info, radius);
		if (found.isEmpty()) {
			return Collections.emptyList();
		}
		List<InventoryHolder> rval = new ArrayList<InventoryHolder>(found.size());
		// This weird for loop is needed because typesToCheck is not a set!
		// The order in which inventories are added is of importance
		for (InteractType type : typesToCheck) {
			switch (type) {
				case CHEST : {
					for (BlockState state : found) {
						if (state instanceof Chest) {
							rval.add((Chest) state);
						}
					}
					break;
				}
				case FURNACE : {
					for (BlockState state : found) {
						if (state instanceof Furnace) {
							rval.add((Furnace) state);
						}
					}
					break;
				}
				case DISPENSER : {
					for (BlockState state : found) {
						if (state instanceof Dispenser) {
							rval.add((Dispenser) state);
						}
					}
					break;
				}
				case GROUNDITEM : {
					rval.add(new GroundItemsState(info.getRails(), radius));
					break;
				}
			}
		}
		return rval;
	}

	public static int transferAllItems(Collection<InventoryHolder> fromHolders, Collection<InventoryHolder> toHolders, ItemParser itemParser, boolean isFuelPreferred) {
		int amount, transferred = 0;
		for (InventoryHolder fromHolder : fromHolders) {
			Inventory from = fromHolder.getInventory();

			// Averaged?
			if (itemParser instanceof AveragedItemParser) {
				// Perform 'one by one' logic - which is a lot slower
				final int totalAmount = itemParser.hasAmount() ? itemParser.getAmount() : Integer.MAX_VALUE;
				final ItemParser single = itemParser.setAmount(1);
				boolean continueTransferring;
				int transferredAmount = 0;
				do {
					// Start of the loop: If nothing is transferred, break it.
					continueTransferring = false;
					// Go by all inventories
					for (InventoryHolder toHolder : toHolders) {
						Inventory to = toHolder.getInventory();
						amount = transferItems(from, to, single, isFuelPreferred);
						if (amount > 0) {
							transferred += amount;
							transferredAmount += amount;
							// Continue transferring? Evaluate the current transfer amount
							if (!(continueTransferring = (transferredAmount < totalAmount))) {
								break;
							}
						}
					}
				} while (continueTransferring);
			} else {
				// Perform regular item transfer: fill one by one
				for (InventoryHolder toHolder : toHolders) {
					Inventory to = toHolder.getInventory();
					amount = transferItems(from, to, itemParser, isFuelPreferred);
					transferred += amount;
					// Update item parser amount
					if (amount > 0 && itemParser.hasAmount()) {
						itemParser = itemParser.setAmount(itemParser.getAmount() - amount);
					}
				}
			}
		}
		return transferred;
	}
	
	/**
	 * Performs item transfer from one inventory to the other, utilizing the item parser specified
	 * 
	 * @param from inventory
	 * @param to inventory
	 * @param itemParser to use
	 * @param isFuelPreferred - whether (when depositing into furnaces) fuel is preferred over heatable
	 * @return the amount of items transferred
	 */
	public static int transferItems(Inventory from, Inventory to, ItemParser itemParser, boolean isFuelPreferred) {
		final InventoryHolder toHolder = to.getHolder();
		final InventoryHolder fromHolder = from.getHolder();

		// Obtaining items from a furnace? Only use output slot!
		if (from instanceof FurnaceInventory) {
			final FurnaceInventory finv = (FurnaceInventory) from;
			from = new InventoryBase() {
				@Override
				public int getSize() {
					return 1;
				}

				@Override
				public ItemStack getItem(int index) {
					return finv.getResult();
				}

				@Override
				public void setItem(int index, ItemStack item) {
					finv.setResult(item);
				}
			};
		}

		// Do not deposit using animations for ground items, it shows duplicates which looks bad
		if (TrainCarts.showTransferAnimations && !(from instanceof GroundItemsInventory)) {
			from = ItemAnimatedInventory.convert(from, fromHolder, toHolder);
		}

		// Depositing into a furnace or other type of inventory?
		if (toHolder instanceof Furnace) {
			return depositInFurnace(from, (Furnace) toHolder, itemParser, isFuelPreferred);
		} else {
			return ItemUtil.transfer(from, to, itemParser, itemParser.getAmount());
		}
	}
}
