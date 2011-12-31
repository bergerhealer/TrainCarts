package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public enum SignActionMode {
    TRAIN, CART, NONE;
    public static SignActionMode fromString(String name) {
    	if (name.equalsIgnoreCase("[train]")) return TRAIN;
    	if (name.equalsIgnoreCase("[cart]")) return CART;
    	return NONE;
    }
    public static SignActionMode fromSign(Sign sign) {
    	if (sign == null) return NONE;
    	return fromString(sign.getLine(0));
    }
    public static SignActionMode fromEvent(SignActionEvent event) {
    	return fromSign(event.getSign());
    }
    public static SignActionMode fromEvent(SignChangeEvent event) {
    	return fromString(event.getLine(0));
    }
}