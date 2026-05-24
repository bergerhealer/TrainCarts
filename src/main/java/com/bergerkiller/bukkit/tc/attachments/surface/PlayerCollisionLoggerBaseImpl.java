package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Default abstract logger that implements the structured debug entry points and
 * associated helper utilities. Concrete loggers can extend this class or use
 * the provided DISABLED instance for no-op logging.
 */
public abstract class PlayerCollisionLoggerBaseImpl implements PlayerCollisionLogger {
    private static final String DEBUG_FILE = "/tmp/playercollision-debug.txt";

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        System.out.println("[PCS-INFO] " + msg);
    }

    @Override
    public void warn(String msg) {
        System.out.println("[PCS-WARN] " + msg);
    }

    @Override
    public void debug(String msg) {
        System.out.println(msg);
    }

    @Override
    public void debugCandidate(OBBSurfaceTransition<?> st, double bestTheta, boolean isVertical, boolean feetCrossed, boolean headCrossed, Vector[] fromCorners, Vector[] toCorners) {
        StringBuilder sb = new StringBuilder();
        sb.append("[PCS] candidate surface center=").append(st.from.center).append(" theta=").append(bestTheta)
                .append(" vertical=").append(isVertical)
                .append(" feetCrossed=").append(feetCrossed).append(" headCrossed=").append(headCrossed).append('\n');
        for (int i = 0; i < fromCorners.length; i++) {
            double sf = signedDistanceToPlane(st.from, fromCorners[i]);
            double stt = signedDistanceToPlane(st.to, toCorners[i]);
            sb.append("  corner=").append(i).append(" fromY=").append(fromCorners[i].getY()).append(" toY=").append(toCorners[i].getY())
                    .append(" signedFrom=").append(sf).append(" signedTo=").append(stt).append('\n');
        }
        writeDebugFile(sb.toString());
        System.out.print(sb.toString());
    }

    @Override
    public void debugFeetClamp(OBBSurfaceTransition<?> st, double dy, double theta) {
        String msg = "[PCS] feet clamp surface=" + st.from.center + " dy=" + dy + " theta=" + theta;
        writeDebugFile(msg + '\n');
        System.out.println(msg);
    }

    @Override
    public void debugHeadClamp(OBBSurfaceTransition<?> st, double dy, double theta) {
        String msg = "[PCS] head clamp surface=" + st.from.center + " dy=" + dy + " theta=" + theta;
        writeDebugFile(msg + '\n');
        System.out.println(msg);
    }

    @Override
    public void debugWallClamp(OBBSurfaceTransition<?> st, double dx, double dy, double dz) {
        String msg = "[PCS] wall clamp surface=" + st.from.center + " delta=(" + dx + "," + dy + "," + dz + ")";
        writeDebugFile(msg + '\n');
        System.out.println(msg);
    }

    @Override
    public void debugMultiSurfacePick(double bestScore, OBBSurfaceState lastSurfaceFromState) {
        StringBuilder sb = new StringBuilder();
        sb.append("MULTI_SURFACE_PICK bestScore=").append(bestScore).append(" chosenSurface=");
        if (lastSurfaceFromState != null) sb.append(lastSurfaceFromState.center.getX()).append(',').append(lastSurfaceFromState.center.getY()).append(',').append(lastSurfaceFromState.center.getZ());
        sb.append('\n');
        writeDebugFile(sb.toString());
        System.out.print(sb.toString());
    }

    @Override
    public void debugFeetLog(AABBHandle aabb, OBBSurfaceState surf, double y, double minY) {
        StringBuilder sb = new StringBuilder();
        sb.append("FEET_LOG ");
        sb.append("surfN=").append(surf.normal.getX()).append(',').append(surf.normal.getY()).append(',').append(surf.normal.getZ()).append(' ');
        sb.append("center=").append(surf.center.getX()).append(',').append(surf.center.getY()).append(',').append(surf.center.getZ()).append(' ');
        sb.append("facePlaneY=");
        for (Vector c : faceCorners(aabb, false)) {
            Vector proj = surf.projectPointOntoPlane(c, new Vector());
            sb.append(proj.getY()).append(',');
        }
        sb.append(' ');
        Vector[] corners = allCorners(aabb);
        sb.append("signedBefore=");
        for (Vector c : corners) {
            double sd = signedDistanceToPlane(surf, c);
            sb.append(sd).append(',');
        }
        sb.append(' ');
        sb.append("minY=").append(minY).append(' ');
        sb.append("y=").append(y).append('\n');
        writeDebugFile(sb.toString());
        System.out.print(sb.toString());
    }

    @Override
    public void debugHeadLog(AABBHandle aabb, OBBSurfaceState surf, double y, double maxY) {
        StringBuilder sb = new StringBuilder();
        sb.append("HEAD_LOG ");
        sb.append("surfN=").append(surf.normal.getX()).append(',').append(surf.normal.getY()).append(',').append(surf.normal.getZ()).append(' ');
        sb.append("center=").append(surf.center.getX()).append(',').append(surf.center.getY()).append(',').append(surf.center.getZ()).append(' ');
        sb.append("facePlaneY=");
        for (Vector c : faceCorners(aabb, true)) {
            Vector proj = surf.projectPointOntoPlane(c, new Vector());
            sb.append(proj.getY()).append(',');
        }
        sb.append(' ');
        Vector[] corners = allCorners(aabb);
        sb.append("signedBefore=");
        for (Vector c : corners) {
            double sd = signedDistanceToPlane(surf, c);
            sb.append(sd).append(',');
        }
        sb.append(' ');
        sb.append("maxY=").append(maxY).append(' ');
        sb.append("y=").append(y).append('\n');
        writeDebugFile(sb.toString());
        System.out.print(sb.toString());
    }

    // Helper utilities duplicated from PlayerCollisionSolver for logging purposes
    private static double signedDistanceToPlane(OBBSurfaceState surf, Vector p) {
        Vector proj = surf.projectPointOntoPlane(p, new Vector());
        return (p.getX() - proj.getX()) * surf.normal.getX()
                + (p.getY() - proj.getY()) * surf.normal.getY()
                + (p.getZ() - proj.getZ()) * surf.normal.getZ();
    }

    private static Vector[] faceCorners(AABBHandle aabb, boolean top) {
        double y = top ? aabb.getMaxY() : aabb.getMinY();
        return new Vector[] {
                new Vector(aabb.getMinX(), y, aabb.getMinZ()),
                new Vector(aabb.getMinX(), y, aabb.getMaxZ()),
                new Vector(aabb.getMaxX(), y, aabb.getMinZ()),
                new Vector(aabb.getMaxX(), y, aabb.getMaxZ())
        };
    }

    private static Vector[] allCorners(AABBHandle aabb) {
        return new Vector[] {
                new Vector(aabb.getMinX(), aabb.getMinY(), aabb.getMinZ()),
                new Vector(aabb.getMinX(), aabb.getMinY(), aabb.getMaxZ()),
                new Vector(aabb.getMaxX(), aabb.getMinY(), aabb.getMinZ()),
                new Vector(aabb.getMaxX(), aabb.getMinY(), aabb.getMaxZ()),
                new Vector(aabb.getMinX(), aabb.getMaxY(), aabb.getMinZ()),
                new Vector(aabb.getMinX(), aabb.getMaxY(), aabb.getMaxZ()),
                new Vector(aabb.getMaxX(), aabb.getMaxY(), aabb.getMinZ()),
                new Vector(aabb.getMaxX(), aabb.getMaxY(), aabb.getMaxZ())
        };
    }

    private static void writeDebugFile(String content) {
        try {
            Files.write(Paths.get(DEBUG_FILE), content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }
}

