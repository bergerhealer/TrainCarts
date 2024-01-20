package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;
import org.bukkit.Location;

import java.io.DataInputStream;
import java.io.IOException;

public class MemberActionWaitLocation extends MemberAction implements WaitAction {
    private final Location dest;
    private final double radiussquared;

    public MemberActionWaitLocation(final Location dest) {
        this(dest, 1);
    }

    public MemberActionWaitLocation(final Location dest, final double radius) {
        this.dest = dest;
        this.radiussquared = radius * radius;
    }

    public Location getTargetLocation() {
        return dest;
    }

    public double getRadius() {
        return Math.sqrt(radiussquared);
    }

    @Override
    public boolean update() {
        return this.getWorld() == dest.getWorld() && this.getEntity().loc.distanceSquared(dest) <= this.radiussquared;
    }

    @Override
    public boolean isMovementSuppressed() {
        return true;
    }

    public static class Serializer implements ActionRegistry.Serializer<MemberActionWaitLocation> {
        @Override
        public boolean save(MemberActionWaitLocation action, OfflineDataBlock data) throws IOException {
            // Save the location information + radius to get within
            data.addChild("wait-location", stream -> {
                Location loc = action.getTargetLocation();
                StreamUtil.writeUUID(stream, loc.getWorld().getUID());
                stream.writeDouble(loc.getX());
                stream.writeDouble(loc.getY());
                stream.writeDouble(loc.getZ());
                stream.writeDouble(action.getRadius());
            });
            return true;
        }

        @Override
        public MemberActionWaitLocation load(OfflineDataBlock data) throws IOException {
            // Read the location information + radius to get within
            final Location target;
            final double radius;
            try (DataInputStream stream = data.findChildOrThrow("wait-location").readData()) {
                OfflineWorld world = OfflineWorld.of(StreamUtil.readUUID(stream));
                if (!world.isLoaded()) {
                    throw new IllegalStateException("Wait target world is not loaded");
                }
                double x = stream.readDouble();
                double y = stream.readDouble();
                double z = stream.readDouble();
                target = new Location(world.getLoadedWorld(), x, y, z);
                radius = stream.readDouble();
            }

            return new MemberActionWaitLocation(target, radius);
        }
    }
}
