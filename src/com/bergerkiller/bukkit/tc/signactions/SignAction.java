package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;

import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public abstract class SignAction {
	
	public static void init() {
		actions = new ArrayList<SignAction>();
		register(new SignActionStation());
		register(new SignActionTag());
		register(new SignActionSpawn());
		register(new SignActionProperties());
		register(new SignActionTeleport());
		register(new SignActionCart());
		register(new SignActionTrain());
	}
	public static void deinit() {
		actions = null;
	}
		
	public abstract void execute(SignActionEvent info);

	private static List<SignAction> actions;
	public static <T extends SignAction> T register(T action) {
		if (actions == null) return action;
		actions.add(action);
		return action;
	}
	public void register() {
		register(this);
	}
	public static void unregister(SignAction action) {
		if (actions == null) return;
		actions.remove(action);
	}
	
	public static void executeAll(SignActionEvent info, SignActionType actiontype) {
		info.setAction(actiontype);
		executeAll(info);
	}
	public static void executeAll(SignActionEvent info) {
		if (info.getSign() == null) return;
		//Event
		info.setCancelled(false);
		Bukkit.getServer().getPluginManager().callEvent(info);
		if (!info.isCancelled() && actions != null) {
			for (SignAction action : actions) {
				action.execute(info);
				if (info.isCancelled()) break;
			}
		}
	}
	
}
