package com.bergerkiller.bukkit.tc.actions;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;

public class GroupActionWaitTill extends GroupActionWaitForever {
    private long finishtime;

    public GroupActionWaitTill(final long finishtime) {
        this.setTime(finishtime);
    }

    protected void setTime(long finishtime) {
        this.finishtime = finishtime;
    }

    public long getTime() {
        return this.finishtime;
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

    public static class Serializer implements ActionRegistry.Serializer<GroupActionWaitTill> {
        @Override
        public boolean save(GroupActionWaitTill action, OfflineDataBlock data) throws IOException {
            data.addChild("wait-till", stream -> {
                stream.writeLong(action.getTime());
            });
            return true;
        }

        @Override
        public GroupActionWaitTill load(OfflineDataBlock data) throws IOException {
            final long time;
            try (DataInputStream stream = data.findChildOrThrow("wait-till").readData()) {
                time = stream.readLong();
            }
            return new GroupActionWaitTill(time);
        }
    }
}
