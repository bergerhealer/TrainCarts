package com.bergerkiller.bukkit.tc.actions;

import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;

public class GroupActionWaitTicks extends GroupActionWaitForever {
    private int ticks;

    public GroupActionWaitTicks(int ticks) {
        this.ticks = ticks;
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        if (this.ticks > 0) {
            return Collections.singletonList(new TrainStatus.WaitingForDuration(this.ticks * 50));
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean update() {
        if (this.ticks <= 0) {
            return true;
        } else {
            this.ticks--;
            return super.update();
        }
    }
}
