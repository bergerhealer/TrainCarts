package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberMobSpawner;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionEProperties extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("eproperty", "eprop", "e-property", "e-prop");
	}

	@Override
	public void execute(SignActionEvent info) {
		final boolean powerChange = info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF);
		final List<MinecartMemberMobSpawner> spawners = new ArrayList<MinecartMemberMobSpawner>();
		for(MinecartMember<?> member : info.getGroup()) {
			if(member instanceof MinecartMemberMobSpawner) {
				spawners.add((MinecartMemberMobSpawner) member);
			}
		}
		
		if((powerChange || info.isAction(SignActionType.REDSTONE_ON)) && info.isCartSign() && info.hasMember()) {
			
		}
	}

	@Override
	public boolean build(SignChangeActionEvent info) {
		return true;
	}
	
	//private void parseSet()
}