package com.bergerkiller.bukkit.tc.detector;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public interface DetectorListener {
    void onRegister(DetectorRegion region);

    void onUnregister(DetectorRegion region);

    void onLeave(final MinecartMember<?> member);

    void onEnter(final MinecartMember<?> member);

    void onLeave(final MinecartGroup group);

    void onEnter(final MinecartGroup group);

    void onUnload(final MinecartGroup group);

    void onUpdate(final MinecartMember<?> member);

    void onUpdate(final MinecartGroup group);
}
