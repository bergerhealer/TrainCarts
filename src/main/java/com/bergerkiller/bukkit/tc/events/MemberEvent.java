package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.event.Event;

/**
 * An event involving a cart of a train
 */
public abstract class MemberEvent extends Event {
    protected MinecartMember<?> member;

    public MemberEvent(final MinecartMember<?> member) {
        this.member = member;
    }

    /**
     * Gets the MinecartMember involved in this Event. This is the
     * cart of a train.
     *
     * @return MinecartMember
     */
    public MinecartMember<?> getMember() {
        return this.member;
    }

    /**
     * Gets the MinecartGroup involved in this Event. This is the train.
     *
     * @return MinecartGroup
     */
    public MinecartGroup getGroup() {
        return this.getMember().getGroup();
    }
}
