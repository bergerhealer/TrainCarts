package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public class Action {
    private final ToggledState started = new ToggledState();
    private int _timeTicks = 0;
    private long _startTimeMillis = 0;

    public boolean doTick() {
        if (this.started.set()) {
            this._startTimeMillis = System.currentTimeMillis();
            this.start();
        }
        boolean result = this.update();
        MinecartGroup group = this.getGroup();
        if (group == null || group.isLastUpdateStep()) {
            this._timeTicks++;
        }
        return result;
    }

    /**
     * Gets the Minecart Group that applies to this Action.
     * For some actions, this may return null when no group is involved.
     * 
     * @return group
     */
    public MinecartGroup getGroup() {
        return null;
    }

    /**
     * Refreshes the tick counter at the end of a (real!) update tick
     */
    public final void endTick() {
        this._timeTicks++;
    }

    /**
     * Gets the number of ticks that have elapsed since starting this action
     * 
     * @return elapsed ticks
     */
    public final int elapsedTicks() {
        return this._timeTicks;
    }

    /**
     * Gets the number of milliseconds that have elapsed since starting this action
     * 
     * @return elapsed milliseconds
     */
    public final long elapsedTimeMillis() {
        return System.currentTimeMillis() - this._startTimeMillis;
    }

    public boolean update() {
        return true;
    }

    /**
     * Called right after this Action is bound to a group or member
     */
    public void bind() {
    }

    public void start() {
        // Default implementation does nothing here
    }
}
