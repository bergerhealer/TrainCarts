package com.bergerkiller.bukkit.tc.utils.signtracker;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.block.SignChangeTracker;

class SignChangeTrackerBKCL extends SignChangeTrackerWrap {
    private final SignChangeTracker tracker;

    public SignChangeTrackerBKCL(Block signBlock) {
        this.tracker = SignChangeTracker.track(signBlock);
    }

    @Override
    public boolean isRemoved() {
        return this.tracker.isRemoved();
    }

    @Override
    public Block getBlock() {
        return this.tracker.getBlock();
    }

    @Override
    public Sign getSign() {
        return this.tracker.getSign();
    }

    @Override
    public boolean update() {
        return this.tracker.update();
    }
}
