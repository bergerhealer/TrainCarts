package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartMobSpawner;
import com.bergerkiller.bukkit.common.wrappers.MobSpawner;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.eproperties.CartEProperties;
import com.bergerkiller.bukkit.tc.eproperties.EProperties;

public class MinecartMemberMobSpawner extends MinecartMember<CommonMinecartMobSpawner> {
	private EProperties eprops = new CartEProperties(this);
	
	@Override
	public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
		super.onPhysicsPostMove(speedFactor);
		getEntity().getMobSpawner().onTick();
	}
	
	public MobSpawner getSpawner() {
		return getEntity().getMobSpawner();
	}
	
	public EProperties getSpawnerProperties() {
		return this.eprops;
	}
}
