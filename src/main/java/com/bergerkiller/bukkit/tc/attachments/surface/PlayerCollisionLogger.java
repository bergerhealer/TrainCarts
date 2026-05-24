package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

/**
 * Logger interface used by the collision solver. Implementations live in
 * {@link PlayerCollisionLoggerBaseImpl} and concrete variants such as
 * {@link PlayerCollisionLoggerImpl}.
 */
public interface PlayerCollisionLogger {
    /** A no-op logger instance constant for disabling all logging */
    PlayerCollisionLogger DISABLED = new PlayerCollisionLogger() {
        @Override public boolean isEnabled() { return false; }
        @Override public void info(String msg) {}
        @Override public void warn(String msg) {}
        @Override public void debug(String msg) {}
        @Override public void debugCandidate(OBBSurfaceTransition<?> st, double bestTheta, boolean isVertical, boolean feetCrossed, boolean headCrossed, Vector[] fromCorners, Vector[] toCorners) {}
        @Override public void debugFeetClamp(OBBSurfaceTransition<?> st, double dy, double theta) {}
        @Override public void debugHeadClamp(OBBSurfaceTransition<?> st, double dy, double theta) {}
        @Override public void debugWallClamp(OBBSurfaceTransition<?> st, double dx, double dy, double dz) {}
        @Override public void debugMultiSurfacePick(double bestScore, OBBSurfaceState lastSurfaceFromState) {}
        @Override public void debugFeetLog(AABBHandle aabb, OBBSurfaceState surf, double y, double minY) {}
        @Override public void debugHeadLog(AABBHandle aabb, OBBSurfaceState surf, double y, double maxY) {}
    };

    boolean isEnabled();
    void info(String msg);
    void warn(String msg);
    void debug(String msg);
    // Structured debug entry points to avoid building log strings in the solver
    void debugCandidate(OBBSurfaceTransition<?> st, double bestTheta, boolean isVertical, boolean feetCrossed, boolean headCrossed, Vector[] fromCorners, Vector[] toCorners);
    void debugFeetClamp(OBBSurfaceTransition<?> st, double dy, double theta);
    void debugHeadClamp(OBBSurfaceTransition<?> st, double dy, double theta);
    void debugWallClamp(OBBSurfaceTransition<?> st, double dx, double dy, double dz);
    void debugMultiSurfacePick(double bestScore, OBBSurfaceState lastSurfaceFromState);
    void debugFeetLog(AABBHandle aabb, OBBSurfaceState surf, double y, double minY);
    void debugHeadLog(AABBHandle aabb, OBBSurfaceState surf, double y, double maxY);
}
