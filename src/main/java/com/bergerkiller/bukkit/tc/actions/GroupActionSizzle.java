package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;

import java.io.IOException;

public class GroupActionSizzle extends GroupAction {

    @Override
    public void start() {
        int j;
        for (int i = 0; i < this.getGroup().size(); i++) {
            j = i * 3;
            if (j < this.getGroup().size()) {
                this.getGroup().get(j).playLinkEffect(false);
            }
        }
    }

    public static class Serializer implements ActionRegistry.Serializer<GroupActionSizzle> {
        @Override
        public boolean save(GroupActionSizzle action, OfflineDataBlock data) throws IOException {
            return true;
        }

        @Override
        public GroupActionSizzle load(OfflineDataBlock data) throws IOException {
            return new GroupActionSizzle();
        }
    }
}
