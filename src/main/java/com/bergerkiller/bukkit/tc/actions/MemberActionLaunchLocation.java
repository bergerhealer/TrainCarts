package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.offline.train.format.DataBlock;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

public class MemberActionLaunchLocation extends MemberActionLaunchDirection implements MovementAction {
    private final Location target;

    public MemberActionLaunchLocation(double targetvelocity, Location target) {
        this.initDistance(0.0, targetvelocity);
        this.target = target.clone();
    }

    public Location getTargetLocation() {
        return target;
    }

    @Override
    public void bind() {
        super.bind();
        this.setTargetDistance(getMember().getEntity().loc.distance(target));
        this.setDirection(getMember().getDirection());
    }

    @Override
    public void start() {
        //update direction to launch at
        super.setDirection(FaceUtil.getDirection(this.getEntity().getLocation(), this.target, false));
        double d = this.getEntity().loc.xz.distance(this.target);
        d += Math.abs(this.target.getBlockY() - this.getEntity().loc.y.block());
        super.setTargetDistance(d);
        super.start();
    }

    public static class Serializer extends MemberActionLaunchDirection.BaseSerializer<MemberActionLaunchLocation> {
        @Override
        public boolean save(MemberActionLaunchLocation action, DataBlock data) throws IOException {
            super.save(action, data);

            // Save the location information
            data.addChild("launch-location", stream -> {
                Location loc = action.getTargetLocation();
                StreamUtil.writeUUID(stream, loc.getWorld().getUID());
                stream.writeDouble(loc.getX());
                stream.writeDouble(loc.getY());
                stream.writeDouble(loc.getZ());
            });
            return true;
        }

        @Override
        public MemberActionLaunchLocation create(DataBlock data) throws IOException {
            final Location target;

            // Load the location information
            try (DataInputStream stream = data.findChildOrThrow("launch-location").readData()) {
                OfflineWorld world = OfflineWorld.of(StreamUtil.readUUID(stream));
                if (!world.isLoaded()) {
                    throw new IllegalStateException("Launch target world is not loaded");
                }
                double x = stream.readDouble();
                double y = stream.readDouble();
                double z = stream.readDouble();
                target = new Location(world.getLoadedWorld(), x, y, z);
            }

            return new MemberActionLaunchLocation(0.0, target);
        }
    };
}
