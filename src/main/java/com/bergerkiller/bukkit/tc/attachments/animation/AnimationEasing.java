package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.Arrays;
import java.util.Objects;
/**
 * A class which holds easing function parameters
 */
public class AnimationEasing {

    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;
    private static final double EPSILON = 0.001;

    /**
     *
     * @param x1 x coordinate for point 1
     * @param y1 y coordinate for point 1
     * @param x2 x coordinate for point 2
     * @param y2 y coordinate for point 2
     */
    public AnimationEasing(double x1, double y1, double x2, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AnimationEasing)) {
            return false;
        }

        AnimationEasing other = (AnimationEasing) obj;

        return Double.compare(x1, other.x1) == 0
                && Double.compare(y1, other.y1) == 0
                && Double.compare(x2, other.x2) == 0
                && Double.compare(y2, other.y2) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x1, y1, x2, y2);
    }

    /**
     * Checks whether the easing function is approximately equal to another easing function,
     * within a small epsilon range. This prevents issues with floating-point precision when
     * comparing easing functions.
     * @param other the easing to compare with
     * @return whether the easings are approximately equal
     */
    public boolean approximatelyEquals(AnimationEasing other) {
        return Math.abs(x1 - other.x1) < EPSILON
                && Math.abs(y1 - other.y1) < EPSILON
                && Math.abs(x2 - other.x2) < EPSILON
                && Math.abs(y2 - other.y2) < EPSILON;
    }

    @Override
    public String toString() {
        return x1 + "," + y1 + "," + x2 + "," + y2;
    }

    /**
     * Parses an easing function from a string representation in the format "x1,y1,x2,y2".
     * @param string the string representation
     * @return the easing function if valid, null otherwise
     */
    public static AnimationEasing parse(String string) {
        String[] parts = string.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            float x1 = Float.parseFloat(parts[0]);
            float y1 = Float.parseFloat(parts[1]);
            float x2 = Float.parseFloat(parts[2]);
            float y2 = Float.parseFloat(parts[3]);
            return new AnimationEasing(x1, y1, x2, y2);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Calculates the cubic bezier value at t for the given points. To receive the x value,
     * enter the x-coordinates of the points, to receive the y value, enter the y-coordinates of the points.
     * @param p0 the start point
     * @param p1 the first control point
     * @param p2 the second control point
     * @param p3 the finish point
     * @param t progression between 0 and 1
     */
    public static double cubicBezier(double p0, double p1, double p2, double p3, double t) {
        double mt = 1.0 - t;

        return mt * mt * mt * p0
                + 3.0 * mt * mt * t * p1
                + 3.0 * mt * t * t * p2
                + t * t * t * p3;
    }

    public static double cubicBezier(AnimationEasing easing, double t) {
        return cubicBezier(easing.getX1(), easing.getY1(), easing.getX2(), easing.getY2(), t);
    }

    /**
     * All easing type presets. Also contains CUSTOM which can be used to specify a custom easing curve.
     */
    public enum EasingType {
        LINEAR(true, 0.0, 0.0, 1.0, 1.0),
        SINE_IN(true, 0.12, 0.0, 0.39, 0.0),
        SINE_OUT(true, 0.61, 1.0, 0.88, 1.0),
        SINE_IN_OUT(true, 0.37, 0.0, 0.63, 1.0),
        CUBIC_IN(true, 0.32, 0.0, 0.67, 0.0),
        CUBIC_OUT(true, 0.33, 1.0, 0.68, 1.0),
        CUBIC_IN_OUT(true, 0.65, 0.0, 0.35, 1.0),
        EXP_IN(true, 0.7, 0.0, 0.84, 0.0),
        EXP_OUT(true, 0.16, 1.0, 0.3, 1.0),
        EXP_IN_OUT(true, 0.87, 0.0, 0.13, 1.0),
        CUSTOM(false, 0.0, 0.0, 1.0, 1.0);

        private final boolean isPreset;
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;

        /**
         * EasingType constructor. All arguments must be between 0 and 1.
         * @param isPreset whether this type should be automatically set when it is selected
         * @param x1 x coordinate of first point
         * @param y1 y coordinate of first point
         * @param x2 x coordinate of second point
         * @param y2 y coordinate of second point
         * @throws IllegalArgumentException when arguments are out of bounds.
         */
        EasingType(boolean isPreset, double x1, double y1, double x2, double y2) {
            if (x1 < 0 || x1 > 1) {
                throw new IllegalArgumentException("x1 must be between 0 and 1");
            }
            if (y1 < 0 || y1 > 1) {
                throw new IllegalArgumentException("y1 must be between 0 and 1");
            }
            if (x2 < 0 || x2 > 1) {
                throw new IllegalArgumentException("x2 must be between 0 and 1");
            }
            if (y2 < 0 || y2 > 1) {
                throw new IllegalArgumentException("y2 must be between 0 and 1");
            }

            this.isPreset = isPreset;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        public boolean isPreset() {
            return isPreset;
        }

        public double getX1() {
            return x1;
        }

        public double getY1() {
            return y1;
        }

        public double getX2() {
            return x2;
        }

        public double getY2() {
            return y2;
        }

        public AnimationEasing getEasing() {
            return new AnimationEasing(getX1(), getY1(), getX2(), getY2());
        }

        public double cubicBezierY(double t) {
            return cubicBezier(0, y1, y2, 1, t);
        }

        public static EasingType getEasingType(AnimationEasing easing) {
            if (easing == null) {
                return LINEAR;
            }

            return Arrays.stream(values())
                    .filter(EasingType::isPreset)
                    .filter(type -> type.getEasing().approximatelyEquals(easing))
                    .findFirst()
                    .orElse(CUSTOM);
        }

    }
}
