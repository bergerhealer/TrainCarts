package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class StatementName extends Statement {

	@Override
	public boolean match(String text) {
		return LogicUtil.contains(text, "renamed", "rename", "ren", "name", "named");
	}

	@Override
	public boolean matchArray(String text) {
		return text.equals("name") || text.equals("n");
	}

	@Override
	public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
		return group.getProperties().isTrainRenamed();
	}

	@Override
	public boolean handleArray(MinecartGroup group, String[] text, SignActionEvent event) {
		TrainProperties prop = group.getProperties();
		for (String name : text) {
			if (prop.getTrainName().equals(name)) {
				return true;
			}
		}
		return false;
	}
}
