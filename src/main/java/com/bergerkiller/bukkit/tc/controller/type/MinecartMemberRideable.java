package com.bergerkiller.bukkit.tc.controller.type;

import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartRideable;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

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
			// This was a temporary hotfix for the passengers teleporting out of minecarts all the time
			// This bug is now fixed, and thus this hotfix is no longer needed
			// In case of re-occurance, uncomment this piece of code
			// Note: this DOES cause plugins like Lift and LaunchMe to fail! (they replace network controllers too)
			/*
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
			*/
			oldPassenger = currentPassenger;
			this.onPropertiesChanged();
		}
	}
}
