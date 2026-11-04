package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.block.BlockFace;

import java.util.function.Supplier;

/**
 * Logger interface used by {@link CollisionSurfaceTracker} for simulation/debug flow logging.
 */
interface CollisionSurfaceLogger {
    /** A no-op logger instance constant for disabling all logging */
    CollisionSurfaceLogger DISABLED = new CollisionSurfaceLogger() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void debugDefer(Supplier<String> messageSupplier) {
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
        }
    };

    boolean isEnabled();

    void debug(String message);

    void debugDefer(Supplier<String> messageSupplier);

    void debugBlockCollisionEvent(
            BlockFace face,
            double theta,
            double feetY,
            AABBHandle blockBounds,
            double stopThreshold,
            double simulationFeetY,
            boolean simulationFlying
    );
}
