package com.bergerkiller.bukkit.tc.eproperties;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberMobSpawner;

public class CartEProperties implements EProperties {
	private MinecartMemberMobSpawner member;
	
	public CartEProperties(MinecartMemberMobSpawner member) {
		this.member = member;
	}
	
	@Override
	public void parseSet(String mode, String text) {
		if(mode.equalsIgnoreCase("type")) {
			if(this.validMob(text))
				member.getSpawner().setMobName(text);
		} else if(mode.equalsIgnoreCase("delay")) {
			int delay = ParseUtil.parseInt(text, member.getSpawner().getSpawnDelay());
			member.getSpawner().setSpawnDelay(delay);
		} else if(mode.equalsIgnoreCase("mindelay")) {
			int delay = ParseUtil.parseInt(text, member.getSpawner().getMinSpawnDelay());
			member.getSpawner().setMinSpawnDelay(delay);
		} else if(mode.equalsIgnoreCase("maxdelay")) {
			int delay = ParseUtil.parseInt(text, member.getSpawner().getMaxSpawnDelay());
			member.getSpawner().setMaxSpawnDelay(delay);
		}
	}
	
	private boolean validMob(String text) {
		try {
			EntityType.valueOf(text.toUpperCase());
			return true;
		} catch(Exception e) {
			return false;
		}
	}
}
