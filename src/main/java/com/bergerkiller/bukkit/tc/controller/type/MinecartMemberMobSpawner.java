package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartMobSpawner;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.wrappers.MobSpawner;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MinecartMemberMobSpawner extends MinecartMember<CommonMinecartMobSpawner> {

    @Override
    public void onPhysicsPostMove() throws MemberMissingException, GroupUnloadedException {
        super.onPhysicsPostMove();
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

    /**
     * TODO: This is no longer being called from anywhere
     * Maybe a sign specifically for configuring stuff like this?
     * 
     * @param name
     * @param input
     * @return
     */
    public boolean parseAndSet(String name, String input) {
        if (LogicUtil.contains(name, "mobtype")) {
            if (Util.isValidEntity(input)) {
                getSpawner().setMobName(input);
            }
        } else if (LogicUtil.contains(name, "delay", "minspawndelay")) {
            getSpawner().setSpawnDelay(ParseUtil.parseInt(input, getSpawner().getSpawnDelay()));
        } else if (LogicUtil.contains(name, "mindelay", "minspawndelay")) {
            getSpawner().setMinSpawnDelay(ParseUtil.parseInt(input, getSpawner().getMinSpawnDelay()));
        } else if (LogicUtil.contains(name, "maxdelay", "maxspawndelay")) {
            getSpawner().setMaxSpawnDelay(ParseUtil.parseInt(input, getSpawner().getMaxSpawnDelay()));
        } else {
            return false;
        }
        return true;
    }
}
