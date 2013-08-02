package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.Material;

public class RailTypeDetector extends RailTypeRegular {

	@Override
	public boolean isRail(int typeId, int data) {
		return typeId == Material.DETECTOR_RAIL.getId();
	}
}
