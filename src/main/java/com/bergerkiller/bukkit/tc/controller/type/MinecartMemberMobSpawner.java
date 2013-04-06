package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartMobSpawner;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.wrappers.MobSpawner;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MinecartMemberMobSpawner extends MinecartMember<CommonMinecartMobSpawner> {

	@Override
	public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
		super.onPhysicsPostMove(speedFactor);
		getEntity().getMobSpawner().onTick();
	}

	/**
	 * Gets the mob spawner that is used by this Mob Spawner Minecart to spawn mobs at an interval
	 * 
	 * @return mob spawner
	 */
	public MobSpawner getSpawner() {
		return getEntity().getMobSpawner();
	}

	@Override
	public boolean parseSet(String key, String args) {
		if (super.parseSet(key, args)) {
			return true;
		}
		if (LogicUtil.contains(key, "mobtype")) {
			if (Util.isValidEntity(args)) {
				getSpawner().setMobName(args);
			}
		} else if (LogicUtil.contains(key, "delay", "minspawndelay")) {
			getSpawner().setSpawnDelay(ParseUtil.parseInt(args, getSpawner().getSpawnDelay()));
		} else if (LogicUtil.contains(key, "mindelay", "minspawndelay")) {
			getSpawner().setMinSpawnDelay(ParseUtil.parseInt(args, getSpawner().getMinSpawnDelay()));
		} else if (LogicUtil.contains(key, "maxdelay", "maxspawndelay")) {
			getSpawner().setMaxSpawnDelay(ParseUtil.parseInt(args, getSpawner().getMaxSpawnDelay()));
		} else {
			return false;
		}
		return true;
	}
}
