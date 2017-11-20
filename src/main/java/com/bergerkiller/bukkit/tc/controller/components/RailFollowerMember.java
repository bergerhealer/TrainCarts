package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class RailFollowerMember extends RailFollower {
    private final MinecartMember<?> member;

    public RailFollowerMember(MinecartMember<?> member) {
        this.member = member;
    }

    @Override
    public boolean isFlying() {
        return member.isFlying();
    }

    @Override
    public BlockFace getDirectionFrom() {
        return member.getDirectionFrom();
    }

    @Override
    public BlockFace getDirectionTo() {
        return member.getDirectionTo();
    }

    @Override
    public RailType getRailType() {
        return member.getRailTracker().getRailType();
    }

    @Override
    public Block getRailBlock() {
        return member.getRailTracker().getBlock();
    }
}
