package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.inventory.ItemParser;

/**
 * An ItemParser that distributes items in an averaged way
 */
public class AveragedItemParser extends ItemParser {

	public AveragedItemParser(ItemParser itemParser, int multiplier) {
		super(itemParser.getType(), itemParser.hasAmount() ? itemParser.getAmount() * multiplier : multiplier, itemParser.getData());
	}
}
