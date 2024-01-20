package com.bergerkiller.bukkit.tc.actions;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;

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

    public static class Serializer implements ActionRegistry.Serializer<GroupActionWaitForever> {
        @Override
        public boolean save(GroupActionWaitForever action, OfflineDataBlock data) throws IOException {
            return true;
        }

        @Override
        public GroupActionWaitForever load(OfflineDataBlock data) throws IOException {
            return new GroupActionWaitForever();
        }
    }
}
