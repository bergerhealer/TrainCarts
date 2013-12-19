package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.Material;

public class RailTypeActivator extends RailTypeRegular {
	private final boolean isPowered;

	protected RailTypeActivator(boolean isPowered) {
		this.isPowered = isPowered;
	}

	public boolean isPowered() {
		return this.isPowered;
	}

	@Override
	public boolean isRail(Material type, int data) {
		return type == Material.ACTIVATOR_RAIL && ((data & 0x8) == 0x8) == isPowered;
	}
}
