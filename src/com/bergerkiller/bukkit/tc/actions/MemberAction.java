package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.World;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberAction extends Action {
	
	public boolean doTick() {
		if (this.member.dead) return true;
		return super.doTick();
	}
	
	private final MinecartMember member;
	public MemberAction(final MinecartMember member) {
		this.member = member;
	}
	public MinecartGroup getGroup() {
		return this.member.getGroup();
	}
	public MinecartMember getMember() {
		return this.member;
	}
	public World getWorld() {
		return this.member.getWorld();
	}

}
