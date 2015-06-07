package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.event.Event;

public abstract class GroupEvent extends Event {
    private final MinecartGroup group;

    public GroupEvent(final MinecartGroup group) {
        this.group = group;
    }

    public MinecartGroup getGroup() {
        return this.group;
    }
}
