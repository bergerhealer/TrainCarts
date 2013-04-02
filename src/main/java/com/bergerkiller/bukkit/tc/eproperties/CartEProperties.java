package com.bergerkiller.bukkit.tc.eproperties;

import com.avaje.ebeaninternal.server.deploy.BeanDescriptor.EntityType;
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
