package com.bergerkiller.bukkit.tc.actions;

import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;

public class GroupActionWaitForever extends GroupAction implements WaitAction {

    @Override
    public boolean update() {
        getGroup().stop();
        return false;
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        return Collections.singletonList(new TrainStatus.WaitingForever());
    }
}
