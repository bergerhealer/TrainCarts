package com.bergerkiller.bukkit.tc.actions;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.offline.train.format.DataBlock;

public class GroupActionWaitTicks extends GroupActionWaitForever {
    private int ticks;

    public GroupActionWaitTicks(int ticks) {
        this.ticks = ticks;
    }

    public int getRemainingTicks() {
        return ticks;
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

    public static class Serializer implements ActionRegistry.Serializer<GroupActionWaitTicks> {
        @Override
        public boolean save(GroupActionWaitTicks action, DataBlock data) throws IOException {
            data.addChild("wait-ticks", stream -> {
                stream.writeInt(action.getRemainingTicks());
            });
            return true;
        }

        @Override
        public GroupActionWaitTicks load(DataBlock data) throws IOException {
            final int ticks;
            try (DataInputStream stream = data.findChildOrThrow("wait-ticks").readData()) {
                ticks = stream.readInt();
            }
            return new GroupActionWaitTicks(ticks);
        }
    }
}
