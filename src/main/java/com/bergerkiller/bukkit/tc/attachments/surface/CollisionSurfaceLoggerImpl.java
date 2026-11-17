package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.block.BlockFace;

import java.util.function.Supplier;

/**
 * Default logger implementation for {@link CollisionSurfaceLogger} that writes to the plugin logger.
 */
final class CollisionSurfaceLoggerImpl implements CollisionSurfaceLogger {
    private final AttachmentViewer viewer;

    CollisionSurfaceLoggerImpl(AttachmentViewer viewer) {
        this.viewer = viewer;
    }

    @Override
    public boolean isEnabled() {
        return TrainCarts.plugin != null && viewer != null;
    }

    @Override
    public void debug(String message) {
        if (!isEnabled()) {
            return;
        }
        TrainCarts.plugin.getLogger().info("[SurfaceCollisionDebug][" + viewer.getName() + "][tick="
                + CommonUtil.getServerTicks() + "] " + message);
    }

    @Override
    public void debugDefer(Supplier<String> messageSupplier) {
        if (!isEnabled()) {
            return;
        }
        debug(messageSupplier.get());
    }

    @Override
    public void debugBlockCollisionEvent(
            BlockFace face,
            double theta,
            double feetY,
            AABBHandle blockBounds,
            double stopThreshold,
            double simulationFeetY,
            boolean simulationFlying
    ) {
        if (!isEnabled()) {
            return;
        }
        debug("event face=" + face + " theta=" + theta
                + " feetY=" + feetY + " blockTopY=" + blockBounds.getMaxY()
                + " stopThreshold=" + stopThreshold
                + " simFeetY=" + simulationFeetY
                + " simFlying=" + simulationFlying
                + " block=[" + blockBounds.getMinX() + "," + blockBounds.getMinY() + "," + blockBounds.getMinZ()
                + " -> " + blockBounds.getMaxX() + "," + blockBounds.getMaxY() + "," + blockBounds.getMaxZ() + "]");
    }
}
