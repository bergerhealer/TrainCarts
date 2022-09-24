package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.World;

public class MemberAction extends Action {
    private MinecartMember<?> member;

    @Override
    public TrainCarts getTrainCarts() {
        return this.member.getTrainCarts();
    }

    @Override
    public boolean doTick() {
        return getEntity().isRemoved() || super.doTick();
    }

    @Override
    public MinecartGroup getGroup() {
        return this.member.getGroup();
    }

    public MinecartMember<?> getMember() {
        return this.member;
    }

    public void setMember(MinecartMember<?> member) {
        this.member = member;
    }

    public CommonMinecart<?> getEntity() {
        return this.member.getEntity();
    }

    public World getWorld() {
        return getEntity().getWorld();
    }
}
