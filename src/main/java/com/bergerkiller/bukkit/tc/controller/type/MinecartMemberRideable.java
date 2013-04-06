package com.bergerkiller.bukkit.tc.controller.type;

import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.common.controller.DefaultEntityNetworkController;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartRideable;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartPassengerNetwork;

public class MinecartMemberRideable extends MinecartMember<CommonMinecartRideable> {
	private Entity oldPassenger;

	@Override
	public void onAttached() {
		super.onAttached();
		oldPassenger = entity.getPassenger();
	}

	@Override
	public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
		super.onPhysicsPostMove(speedFactor);
		Entity currentPassenger = entity.getPassenger();
		if (oldPassenger != currentPassenger) {
			if (currentPassenger != null) {
				CommonEntity<?> entity = CommonEntity.get(currentPassenger);
				if (!(entity.getNetworkController() instanceof MinecartPassengerNetwork)) {
					entity.setNetworkController(new MinecartPassengerNetwork());
				}
			} else if (oldPassenger != null) {
				CommonEntity<?> entity = CommonEntity.get(oldPassenger);
				if (entity.getNetworkController() instanceof MinecartPassengerNetwork) {
					entity.setNetworkController(new DefaultEntityNetworkController());
				}
			}
			oldPassenger = currentPassenger;
			this.onPropertiesChanged();
		}
	}
}
