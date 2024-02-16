package com.bergerkiller.bukkit.tc.properties.standard.type;

import org.bukkit.util.Vector;

/**
 * Offset relative to the cart position where players are ejected.
 * Used when seat attachments don't have one explicitly defined.
 */
public final class ExitOffset {
    public static final ExitOffset DEFAULT = new ExitOffset(false, 0.0, 0.0, 0.0, Float.NaN, Float.NaN);

    private final boolean absolute;
    private final double rx, ry, rz;
    private final float yaw, pitch;

    private ExitOffset(boolean absolute, double rx, double ry, double rz, float yaw, float pitch) {
        this.absolute = absolute;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Gets whether the exit position is absolute (true) or relative to
     * the seat (false)
     *
     * @return True if the exit position is absolute, False if relative
     */
    public boolean isAbsolute() {
        return this.absolute;
    }

    /**
     * Gets the relative or absolute exit position
     *
     * @return exit position
     */
    public Vector getPosition() {
        return new Vector(this.rx, this.ry, this.rz);
    }

    public double getX() {
        return this.rx;
    }

    public double getY() {
        return this.ry;
    }

    public double getZ() {
        return this.rz;
    }

    public float getYaw() {
        return this.yaw;
    }

    @Deprecated
    public Vector getRelativePosition() {
        return getPosition();
    }

    @Deprecated
    public double getRelativeX() {
        return this.rx;
    }

    @Deprecated
    public double getRelativeY() {
        return this.ry;
    }

    @Deprecated
    public double getRelativeZ() {
        return this.rz;
    }

    public float getPitch() {
        return this.pitch;
    }

    public boolean hasLockedYaw() {
        return !Float.isNaN(this.yaw);
    }

    public boolean hasLockedPitch() {
        return !Float.isNaN(this.pitch);
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
            return this.absolute == other.absolute &&
                   this.rx == other.rx &&
                   this.ry == other.ry &&
                   this.rz == other.rz &&
                   (this.hasLockedYaw() ? (this.yaw == other.yaw) : !other.hasLockedYaw()) &&
                   (this.hasLockedPitch() ? (this.pitch == other.pitch) : !other.hasLockedPitch());
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        if (absolute) {
            return "ExitLocation{x=" + rx + ", y=" + ry + ", z=" + rz + ", yaw=" + yaw + ", pitch=" + pitch + "}";
        } else {
            return "ExitOffset{dx=" + rx + ", dy=" + ry + ", dz=" + rz + ", yaw=" + yaw + ", pitch=" + pitch + "}";
        }
    }

    public static ExitOffset createAbsolute(Vector absolutePosition, float yaw, float pitch) {
        return createAbsolute(absolutePosition.getX(), absolutePosition.getY(), absolutePosition.getZ(), yaw, pitch);
    }

    public static ExitOffset createAbsolute(double posX, double posY, double posZ, float yaw, float pitch) {
        return new ExitOffset(true, posX, posY, posZ, yaw, pitch);
    }

    public static ExitOffset create(boolean positionIsAbsolute, Vector position, float yaw, float pitch) {
        return new ExitOffset(positionIsAbsolute, position.getX(), position.getY(), position.getZ(), yaw, pitch);
    }

    public static ExitOffset create(Vector relativePosition, float yaw, float pitch) {
        return create(relativePosition.getX(), relativePosition.getY(), relativePosition.getZ(), yaw, pitch);
    }

    public static ExitOffset create(double rx, double ry, double rz, float yaw, float pitch) {
        return new ExitOffset(false, rx, ry, rz, yaw, pitch);
    }
}
