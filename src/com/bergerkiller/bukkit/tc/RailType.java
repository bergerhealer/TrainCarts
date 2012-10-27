package com.bergerkiller.bukkit.tc;

import org.bukkit.Material;

/**
 * The type of rails below a minecart
 */
public enum RailType {
	PRESSUREPLATE(false), REGULAR(true), BRAKE(true), BOOST(true), DETECTOR(true), VERTICAL(false), NONE(false);

	private boolean track;
	private RailType(boolean isTrack) {
		this.track = isTrack;
	}

	public boolean isTrack() {
		return this.track;
	}

	public static RailType get(int typeId, int data) {
		if (typeId == Material.POWERED_RAIL.getId()) {
			if ((data & 0x8) == 0x8) {
				return BOOST;
			} else {
				return BRAKE;
			}
		} else if (typeId == Material.DETECTOR_RAIL.getId()) {
			return DETECTOR;
		} else if (typeId == Material.RAILS.getId()) {
			return REGULAR;
		} else if (Util.isPressurePlate(typeId)) {
			return PRESSUREPLATE;
		} else if (Util.isVerticalRail(typeId)) {
			return VERTICAL;
		} else {
			return NONE;
		}
	}
}
