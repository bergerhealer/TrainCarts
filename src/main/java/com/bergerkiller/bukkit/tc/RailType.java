package com.bergerkiller.bukkit.tc;

import org.bukkit.Material;

import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * The type of rails below a minecart
 */
public enum RailType {
	PRESSUREPLATE(true), REGULAR(true), BRAKE(true), BOOST(true), DETECTOR(true), VERTICAL(false), NONE(false);

	private final boolean horizontal;

	private RailType(boolean isHorizontal) {
		this.horizontal = isHorizontal;
	}

	/**
	 * Whether the rail type controls minecarts horizontally only
	 * 
	 * @return True if minecarts are horizontally controlled, False if not
	 */
	public boolean isHorizontal() {
		return this.horizontal;
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
		} else if (MaterialUtil.ISPRESSUREPLATE.get(typeId)) {
			return PRESSUREPLATE;
		} else if (Util.ISVERTRAIL.get(typeId)) {
			return VERTICAL;
		} else {
			return NONE;
		}
	}
}
