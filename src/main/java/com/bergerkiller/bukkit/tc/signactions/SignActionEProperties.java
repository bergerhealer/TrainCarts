package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberMobSpawner;
import com.bergerkiller.bukkit.tc.eproperties.EProperties;
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
			if(info.getMember() instanceof MinecartMemberMobSpawner)
				this.parseSet(((MinecartMemberMobSpawner) info.getMember()).getSpawnerProperties(), info);
		} else if ((powerChange || info.isAction(SignActionType.GROUP_ENTER)) && info.isTrainSign() && info.hasGroup()) {
			for(MinecartMemberMobSpawner spawner : spawners) {
				this.parseSet(spawner.getSpawnerProperties(), info);
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.isCartSign()) {
			return handleBuild(event, Permission.BUILD_PROPERTY, "spawner cart property setter", "set properties on the cart above");
		} else if (event.isTrainSign()) {
			return handleBuild(event, Permission.BUILD_PROPERTY, "spawner train property setter", "set properties on the train above");
		} else if (event.isRCSign()) {
			return handleBuild(event, Permission.BUILD_PROPERTY, "spawner train property setter", "remotely set properties on the train specified");
		}
		return false;
	}
	
	private void parseSet(EProperties props, SignActionEvent info) {
		String mode = info.getLine(2).toLowerCase().trim();
		String[] args = Util.splitBySeparator(info.getLine(3));
		if (args.length >= 2) {
			props.parseSet(mode, info.isPowered() ? args[0] : args[1]);
		} else if (args.length == 1 && info.isPowered()) {
			props.parseSet(mode, args[0]);
		}
	}
}