package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;

import java.io.DataInputStream;
import java.io.IOException;

public class MemberActionWaitDistance extends MemberAction implements WaitAction {
    private double distance;

    public MemberActionWaitDistance(double distance) {
        this.distance = distance;
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public boolean update() {
        this.distance -= this.getEntity().getMovedXZDistance();
        return this.distance <= 0;
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }

    public static class Serializer implements ActionRegistry.Serializer<MemberActionWaitDistance> {
        @Override
        public boolean save(MemberActionWaitDistance action, OfflineDataBlock data) throws IOException {
            data.addChild("wait-distance", stream -> {
                stream.writeDouble(action.getDistance());
            });
            return true;
        }

        @Override
        public MemberActionWaitDistance load(OfflineDataBlock data) throws IOException {
            final double distance;
            try (DataInputStream stream = data.findChildOrThrow("wait-distance").readData()) {
                distance = stream.readDouble();
            }
            return new MemberActionWaitDistance(distance);
        }
    }
}
