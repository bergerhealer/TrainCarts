package com.bergerkiller.bukkit.tc.properties.standard.type;

import org.bukkit.util.Vector;

/**
 * Offset relative to the cart position where players are ejected.
 * Used when seat attachments don't have one explicitly defined.
 */
public final class ExitOffset {
    public static final ExitOffset DEFAULT = new ExitOffset(0.0, 0.0, 0.0, 0.0f, 0.0f);

    private final double rx, ry, rz;
    private final float yaw, pitch;

    private ExitOffset(double rx, double ry, double rz, float yaw, float pitch) {
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Vector getRelativePosition() {
        return new Vector(this.rx, this.ry, this.rz);
    }

    public double getRelativeX() {
        return this.rx;
    }

    public double getRelativeY() {
        return this.ry;
    }

    public double getRelativeZ() {
        return this.rz;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(this.rx) ^ Double.hashCode(this.rz);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof ExitOffset) {
            ExitOffset other = (ExitOffset) o;
            return this.rx == other.rx &&
                   this.ry == other.ry &&
                   this.rz == other.rz &&
                   this.yaw == other.yaw &&
                   this.pitch == other.pitch;
        } else {
            return true;
        }
    }

    public static ExitOffset create(Vector relativePosition, float yaw, float pitch) {
        return create(relativePosition.getX(), relativePosition.getY(), relativePosition.getZ(), yaw, pitch);
    }

    public static ExitOffset create(double rx, double ry, double rz, float yaw, float pitch) {
        return new ExitOffset(rx, ry, rz, yaw, pitch);
    }
}
