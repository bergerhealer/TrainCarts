package com.bergerkiller.bukkit.tc.actions;

import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;

public class GroupActionWaitTill extends GroupActionWaitForever {
    private long finishtime;

    public GroupActionWaitTill(final long finishtime) {
        this.setTime(finishtime);
    }

    protected void setTime(long finishtime) {
        this.finishtime = finishtime;
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        long remaining = (this.finishtime - System.currentTimeMillis());
        if (remaining > 0) {
            return Collections.singletonList(new TrainStatus.WaitingForDuration(remaining));
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean update() {
        return this.finishtime <= System.currentTimeMillis() || super.update();
    }
}
