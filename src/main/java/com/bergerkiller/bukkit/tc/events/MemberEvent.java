package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.event.Event;

public abstract class MemberEvent extends Event {
    protected MinecartMember<?> member;

    public MemberEvent(final MinecartMember<?> member) {
        this.member = member;
    }

    public MinecartMember<?> getMember() {
        return this.member;
    }

    public MinecartGroup getGroup() {
        return this.getMember().getGroup();
    }
}
