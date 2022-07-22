package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;

/**
 * Similar to {@link BlockActionSetLevers} but also supports fake signs with their
 * own output mechanics.
 */
public class TrackedSignActionSetOutput extends Action {
    private final TrackedSign sign;
    private final boolean output;

    public TrackedSignActionSetOutput(TrackedSign sign, boolean output) {
        this.sign = sign;
        this.output = output;
    }

    @Override
    public void start() {
        if (!this.sign.isRemoved()) {
            this.sign.setOutput(output);
        }
    }
}
