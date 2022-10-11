package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlot;

/**
 * A conflict occurred when two trains find themselves inside a mutex zone at the same time,
 * when they really shouldn't be. For smart mutexes this can also be caused by a path
 * through the mutex that suddenly changed to now cross another train.<br>
 * <br>
 * By listening to this event such situations can be detected, and further actions could
 * be taken.
 */
public class MutexZoneConflictEvent extends GroupEvent {
    private static final HandlerList handlers = new HandlerList();
    private final MinecartGroup groupCrossed;
    private final MutexZoneSlot slot;
    private final IntVector3 rail;

    public MutexZoneConflictEvent(MinecartGroup group, MinecartGroup groupCrossed, MutexZoneSlot slot, IntVector3 rail) {
        super(group);
        this.groupCrossed = groupCrossed;
        this.slot = slot;
        this.rail = rail;
    }

    /**
     * Gets the mutex zone slot for which this conflict occurred
     *
     * @return slot
     */
    public MutexZoneSlot getMutexZoneSlot() {
        return slot;
    }

    /**
     * Gets the MinecartGroup of the other train that was crossed inside the mutex zones for the slot
     *
     * @return group crossed
     */
    public MinecartGroup getGroupCrossed() {
        return groupCrossed;
    }

    /**
     * Gets the rail block coordinates where the conflict first occurred
     *
     * @return conflict rail block coordinates
     */
    public IntVector3 getRailPosition() {
        return rail;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
