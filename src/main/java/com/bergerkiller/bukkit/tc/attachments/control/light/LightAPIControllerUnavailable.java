package com.bergerkiller.bukkit.tc.attachments.control.light;

import com.bergerkiller.bukkit.common.bases.IntVector3;

class LightAPIControllerUnavailable extends LightAPIController {
    public static final LightAPIControllerUnavailable INSTANCE = new LightAPIControllerUnavailable();

    private LightAPIControllerUnavailable() {
    }

    @Override
    public void add(IntVector3 position, int level) {
    }

    @Override
    public void remove(IntVector3 position, int level) {
    }

    @Override
    public void move(IntVector3 old_position, IntVector3 new_position, int level) {
    }

    @Override
    protected boolean onSync() {
        return false;
    }
}
