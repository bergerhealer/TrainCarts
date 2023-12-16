package com.bergerkiller.bukkit.tc.debug.particles;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy way of showing particles, which uses dust particles
 */
class DebugParticlesLegacy extends DebugParticles {
    private static final double PARTICLE_SPACING = 0.3;
    private static final int PARTICLE_ITERATIONS = 20;
    private static final int PARTICLE_INTERVAL = 4;

    private final List<Element> elements = new ArrayList<>();

    public DebugParticlesLegacy(Player player) {
        super(player);
    }

    @Override
    public void line(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dist = MathUtil.distance(x1, y1, z1, x2, y2, z2);
        if (dist >= 1e-8) {
            int n = MathUtil.ceil(dist / PARTICLE_SPACING);
            elements.add(new Line(color, x1, y1, z1, x2, y2, z2, n));
            startUpdating();
        }
    }

    @Override
    public void point(Color color, double x, double y, double z) {
        elements.add(new Point(color, x, y, z));
        startUpdating();
    }

    @Override
    protected boolean update() {
        elements.removeIf(l -> l.update(player));
        return elements.isEmpty();
    }

    private static class Point extends Element {
        public final Color color;
        public final Vector pos;

        public Point(Color color, double x, double y, double z) {
            this.color = color;
            this.pos = new Vector(x, y, z);
        }

        @Override
        public void spawn(Player viewer) {
            PlayerUtil.spawnDustParticles(viewer, pos, color);
        }
    }

    private static class Line extends Element {
        public final Color color;
        public final double x, y, z;
        public final double dx, dy, dz;
        public final int count;

        public Line(Color color, double x1, double y1, double z1, double x2, double y2, double z2, int count) {
            this.color = color;
            this.x = x1;
            this.y = y1;
            this.z = z1;
            this.dx = x2 - x1;
            this.dy = y2 - y1;
            this.dz = z2 - z1;
            this.count = count;
        }

        @Override
        public void spawn(Player viewer) {
            Vector position = new Vector();
            int n = count;
            for (int i = 0; i < n; i++) {
                double t = (double) i / (double) (n-1);
                position.setX(x + dx * t);
                position.setY(y + dy * t);
                position.setZ(z + dz * t);
                PlayerUtil.spawnDustParticles(viewer, position, color);
            }
        }
    }

    private static abstract class Element {
        public int age;
        public int skip;

        public Element() {
            this.age = 0;
            this.skip = PARTICLE_INTERVAL;
        }

        public abstract void spawn(Player viewer);

        public boolean update(Player viewer) {
            if (++skip >= PARTICLE_INTERVAL) {
                skip = 0;
            } else {
                return false; // Skipped
            }

            spawn(viewer);

            return ++age >= PARTICLE_ITERATIONS;
        }
    }
}
