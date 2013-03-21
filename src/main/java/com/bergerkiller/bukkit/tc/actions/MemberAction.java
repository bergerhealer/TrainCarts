package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberAction extends Action {
	private final MinecartMember<?> member;

	public MemberAction(final MinecartMember<?> member) {
		this.member = member;
	}

	@Override
	public boolean doTick() {
		return getEntity().isDead() ? true : super.doTick();
	}

	public MinecartGroup getGroup() {
		return this.member.getGroup();
	}

	public MinecartMember<?> getMember() {
		return this.member;
	}

	public CommonMinecart<?> getEntity() {
		return this.member.getEntity();
	}

	public World getWorld() {
		return getEntity().getWorld();
	}
}
