package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.Material;

public class RailTypeDetector extends RailTypeRegular {

	@Override
	public boolean isRail(Material type, int data) {
		return type == Material.DETECTOR_RAIL;
	}
}
