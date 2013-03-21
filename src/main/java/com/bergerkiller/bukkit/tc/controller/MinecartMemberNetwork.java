package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;

public class MinecartMemberNetwork<T extends CommonMinecart<?>> extends EntityNetworkController<T> {
	public static final long MIN_SYNC_INTERVAL = 10;

	@Override
	public synchronized void onSync() {
		if (entity.isDead()) {
			return;
		}
		MinecartMember<?> member = (MinecartMember<?>) entity.getController();
		if (member.isUnloaded() || member.isSingle()) {
			// Unloaded or only one minecart: Synchronize just this Minecart
			super.onSync();
			return;
		} else if (member.getIndex() != 0) {
			// Ignore
			return;
		}
		// Update for the entire group
		MinecartGroup group = member.getGroup();
		boolean teleport = this.getTicksSinceLocationSync() > 200;
		boolean location = teleport || this.isUpdateTick();
		boolean velocity = false;
		for (MinecartMember<?> mm : group) {
			location |= mm.getEntity().isPositionChanged();
			velocity |= mm.getEntity().isVelocityChanged();
		}
		// SYnchronize based on settings
		EntityNetworkController<?> networkController;
		for (MinecartMember<?> mm : group) {
			networkController = mm.getEntity().getNetworkController();
			if (location) {
				if (teleport) {
					networkController.syncLocationAbsolute();
				} else {
					networkController.syncLocation();
				}
				mm.getEntity().setPositionChanged(false);
			}
			if (velocity) {
				networkController.syncVelocity();
				mm.getEntity().setVelocityChanged(false);
			}
			networkController.syncMeta();
		}
	}
}
