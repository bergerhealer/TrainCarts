package com.bergerkiller.bukkit.tc.detector;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public interface DetectorListener {
	public void onRegister(DetectorRegion region);
	public void onUnregister(DetectorRegion region);
	public void onLeave(final MinecartMember<?> member);
	public void onEnter(final MinecartMember<?> member);
	public void onLeave(final MinecartGroup group);
	public void onEnter(final MinecartGroup group);
	public void onUnload(final MinecartGroup group);
	public void onUpdate(final MinecartMember<?> member);
	public void onUpdate(final MinecartGroup group);
}
