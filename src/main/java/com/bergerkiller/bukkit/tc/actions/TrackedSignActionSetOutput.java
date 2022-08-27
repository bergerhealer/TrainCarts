package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;

/**
 * Similar to {@link BlockActionSetLevers} but also supports fake signs with their
 * own output mechanics.
 */
public class TrackedSignActionSetOutput extends Action {
    private final TrainCarts traincarts;
    private final TrackedSign sign;
    private final boolean output;

    public TrackedSignActionSetOutput(TrainCarts traincarts, TrackedSign sign, boolean output) {
        this.traincarts = traincarts;
        this.sign = sign;
        this.output = output;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    @Override
    public void start() {
        if (!this.sign.isRemoved()) {
            this.sign.setOutput(output);
        }
    }
}
