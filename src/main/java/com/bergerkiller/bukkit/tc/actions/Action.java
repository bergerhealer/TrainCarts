package com.bergerkiller.bukkit.tc.actions;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatusProvider;

public abstract class Action implements TrainStatusProvider, TrainCarts.Provider {
    private final ToggledState started = new ToggledState();
    private int _timeTicks = 0;
    private int _subTicks = 1;
    private long _startTimeMillis = 0;
    private final HashSet<String> tags = new HashSet<String>();

    public boolean doTick() {
        if (this.started.set()) {
            this._startTimeMillis = System.currentTimeMillis();
            this.start();
        }
        boolean result = this.update();
        if (this.isFullTick()) {
            this._subTicks = 1;
            this._timeTicks++;
        } else {
            this._subTicks++;
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
     * Gets whether this action has been started. Is true if {@link #start()} has been called
     *
     * @return True if this action has been started
     */
    public boolean hasActionStarted() {
        return started.get();
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
     * Returns True when the current update() is the end of a new full tick.
     * When this is True, {@link #elapsedTicks()} will be incremented this update.
     * 
     * @return True if this is the full end of a tick
     */
    public final boolean isFullTick() {
        MinecartGroup group = this.getGroup();
        return group == null || this._subTicks >= group.getUpdateStepCount();
    }

    /**
     * Gets the number of milliseconds that have elapsed since starting this action
     * 
     * @return elapsed milliseconds
     */
    public final long elapsedTimeMillis() {
        return System.currentTimeMillis() - this._startTimeMillis;
    }

    /**
     * Gets the set of tags added to this action
     *
     * @return Tags
     */
    public Set<String> getTags() {
        return Collections.unmodifiableSet(this.tags);
    }

    /**
     * Adds a metadata tag for this action
     * 
     * @param tag
     */
    public void addTag(String tag) {
        this.tags.add(tag);
    }

    /**
     * Checks whether a metadata tag is set for this action
     * 
     * @param tag
     * @return True if the tag is set
     */
    public boolean hasTag(String tag) {
        return this.tags.contains(tag);
    }

    /**
     * Updates the action. When the action has completed, true
     * should be returned so the action can be removed.
     * 
     * @return True if the action has finished, False if the action is still ongoing
     */
    public boolean update() {
        return true;
    }

    /**
     * Called when this action has been cancelled, either because the train/member
     * was destroyed, or because the actions were cleared. No new actions
     * should be scheduled inside this callback!
     */
    public void cancel() {
    }

    /**
     * Called right after this Action is bound to a group or member
     */
    public void bind() {
    }

    public void start() {
        // Default implementation does nothing here
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        return Collections.emptyList();
    }

    /**
     * Loads the elapsed time of an Action. Only used when initializing a new action.
     * Should not be called for actions that have already been scheduled.
     *
     * @param action Action
     * @param elapsedTicks Number of elapsed ticks. 0 if not started.
     * @param elapsedMillis Elapsed time in milliseconds. 0 if not started.
     */
    public static void loadElapsedTime(Action action, int elapsedTicks, long elapsedMillis) {
        if (elapsedTicks > 0) {
            action.started.set();
            action._timeTicks = elapsedTicks;
            action._startTimeMillis = System.currentTimeMillis() - elapsedMillis;
        } else {
            action.started.clear();
            action._timeTicks = 0;
            action._startTimeMillis = 0L;
        }
        action._subTicks = 1;
    }
}
