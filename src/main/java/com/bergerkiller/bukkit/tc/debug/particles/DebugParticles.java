package com.bergerkiller.bukkit.tc.debug.particles;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.protocol.PlayerGameInfo;
import com.bergerkiller.bukkit.tc.TrainCarts;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the display of "particle" effects to highlight shapes and objects
 */
public abstract class DebugParticles {
    private static final Map<Player, List<DebugParticles>> byPlayer = new HashMap<>();
    protected final Player player;
    private boolean started = false;

    /**
     * Gets the debug particles instance for the given player
     *
     * @param player Player
     * @return DebugParticles of this player
     */
    public static DebugParticles of(Player player) {
        List<DebugParticles> existing = byPlayer.get(player);
        if (existing != null) {
            return existing.get(0);
        } else {
            DebugParticles result = createFor(player);
            result.startUpdating(); // Also cleans it up if not used the next tick
            return result;
        }
    }

    private static DebugParticles createFor(Player player) {
        if (CommonCapabilities.HAS_DISPLAY_ENTITY && PlayerGameInfo.of(player).evaluateVersion(">=", "1.20")) {
            return new DebugParticlesLegacy(player); //TODO: Implement
        } else {
            return new DebugParticlesLegacy(player);
        }
    }

    protected DebugParticles(Player player) {
        this.player = player;
    }

    /**
     * Shows a cuboid
     *
     * @param color Color of the cuboid
     * @param x1 X-coordinate of the first point
     * @param y1 Y-coordinate of the first point
     * @param z1 Z-coordinate of the first point
     * @param x2 X-coordinate of the second point
     * @param y2 Y-coordinate of the second point
     * @param z2 Z-coordinate of the second point
     */
    public void cube(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        face(color, x1, y1, z1, x2, y1, z2);
        face(color, x1, y2, z1, x2, y2, z2);
        line(color, x1, y1, z1, x1, y2, z1);
        line(color, x2, y1, z1, x2, y2, z1);
        line(color, x1, y1, z2, x1, y2, z2);
        line(color, x2, y1, z2, x2, y2, z2);
    }

    /**
     * Shows a face (single square surface)
     *
     * @param color Color of the face
     * @param x1 X-coordinate of the first point
     * @param y1 Y-coordinate of the first point
     * @param z1 Z-coordinate of the first point
     * @param x2 X-coordinate of the second point
     * @param y2 Y-coordinate of the second point
     * @param z2 Z-coordinate of the second point
     */
    public void face(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        line(color, x1, y1, z1, x2, y1, z1);
        line(color, x1, y1, z1, x1, y2, z1);
        line(color, x1, y1, z1, x1, y1, z2);
        line(color, x1, y2, z2, x2, y2, z2);
        line(color, x2, y1, z2, x2, y2, z2);
        line(color, x2, y2, z1, x2, y2, z2);
    }

    /**
     * Displays a line from one coordinate to another
     * @param color Color of the line
     * @param p1 First point
     * @param p2 Second point
     */
    public final void line(Color color, Vector p1, Vector p2) {
        line(color, p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ());
    }

    /**
     * Displays a line from one coordinate to another
     *
     * @param color Color of the line
     * @param x1 X-coordinate of the first point
     * @param y1 Y-coordinate of the first point
     * @param z1 Z-coordinate of the first point
     * @param x2 X-coordinate of the second point
     * @param y2 Y-coordinate of the second point
     * @param z2 Z-coordinate of the second point
     */
    public abstract void line(Color color, double x1, double y1, double z1, double x2, double y2, double z2);

    /**
     * Displays a (path) point of interest
     *
     * @param color Color of the point
     * @param pos Coordinates
     */
    public final void point(Color color, Vector pos) {
        point(color, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Displays a (path) point of interest
     *
     * @param color Color of the point
     * @param x X-coordinate of the point
     * @param y Y-coordinate of the point
     * @param z Z-coordinate of the point
     */
    public abstract void point(Color color, double x, double y, double z);

    /**
     * Updates the particles being displayed for this player
     *
     * @return True when no more particles are displayed and this
     *         object can be cleaned up. False if more particles need
     *         to be displayed still.
     */
    protected abstract boolean update();

    private boolean doUpdate() {
        if (update()) {
            started = false;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Ensures this DebugParticles instance is registered and updated.
     */
    protected void startUpdating() {
        if (started) {
            return;
        }
        if (byPlayer.isEmpty()) {
            byPlayer.put(player, Collections.singletonList(this));
            new Task(TrainCarts.plugin) {
                @Override
                public void run() {
                    byPlayer.values().removeIf(particles -> {
                        if (particles.size() == 1) {
                            return particles.get(0).doUpdate();
                        } else {
                            particles.removeIf(DebugParticles::doUpdate);
                            return particles.isEmpty();
                        }
                    });
                    if (byPlayer.isEmpty()) {
                        stop();
                    }
                }
            }.start(1, 1);
        } else {
            byPlayer.compute(player, (p, current) -> {
                if (current == null) {
                    return Collections.singletonList(this);
                } else if (current.size() == 1) {
                    List<DebugParticles> multi = new ArrayList<>(current);
                    multi.add(this);
                    return multi;
                } else {
                    current.add(this);
                    return current;
                }
            });
        }
        started = true;
    }
}
