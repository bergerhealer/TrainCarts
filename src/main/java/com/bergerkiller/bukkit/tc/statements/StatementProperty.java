package com.bergerkiller.bukkit.tc.statements;

import java.util.ArrayList;

import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class StatementProperty extends Statement {
	private ArrayList<String> properties = new ArrayList<String>();
	private String[] maxspeed;
	private String[] playerEnter;
	private String[] playerExit;
	private String[] mobEnter;
	public StatementProperty() {
		maxspeed = add("maxspeed", "speedlimit");
		playerEnter = add("playerenter", "playersenter");
		playerExit = add("playerexit", "playersexit");
		mobEnter = add("mobenter", "mobsenter");
	}

	private String[] add(String... properties) {
		for (String property : properties) {
			this.properties.add(property);
		}
		return properties;
	}

	private boolean match(String[] property, String text) {
		for (String propval : property) {
			if (text.startsWith(propval)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean match(String text) {
		for (String property : properties) {
			if (text.startsWith(property)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean matchArray(String text) {
		return false;
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		TrainProperties prop = group.getProperties();
		String lower = text.toLowerCase();
		if (match(maxspeed, lower)) {
			return Util.evaluate(prop.getSpeedLimit(), text);
		} else if (match(playerEnter, lower)) {
			return prop.getPlayersEnter();
		} else if (match(playerExit, lower)) {
			return prop.getPlayersExit();
		} else if (match(mobEnter, lower)) {
			return prop.mobCollision == CollisionMode.ENTER;
		}

		// Perform checks on cart properties
		return super.handle(group, text, event);
	}

	@Override
	public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
		CartProperties prop = member.getProperties();
		String lower = text.toLowerCase();
		if (match(playerEnter, lower)) {
			return prop.getPlayersEnter();
		} else if (match(playerExit, lower)) {
			return prop.getPlayersExit();
		}
		return false;
	}
}
