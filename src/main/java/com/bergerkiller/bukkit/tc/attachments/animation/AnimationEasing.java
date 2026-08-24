package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable class which holds easing function parameters
 */
public class AnimationEasing {

    // Constants used in algorithm to find u
    private static final double MARGIN_EPSILON = 0.001;
    private static final double SOLVER_EPSILON = 1.0e-7;
    private static final double MINIMUM_SLOPE = 1.0e-7;

    // Uses Newton-Raphson to find u, when it fails, algorithm falls back to bisection
    private static final int NEWTON_ITERATIONS = 6;
    private static final int BISECTION_ITERATIONS = 16;

    // Coordinates of the 2 control points
    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;

    // Polynomial coefficients
    private final double ax;
    private final double bx;
    private final double cx;

    private final double ay;
    private final double by;
    private final double cy;

    private final boolean linear;

    // Static linear easing constant as default
    public static final AnimationEasing LINEAR = new AnimationEasing(0.0, 0.0, 1.0, 1.0) {
        @Override
        public double evaluate(double theta) {
            return theta;
        }
    };

    /**
     * All values must lie between [0.0, 1.0].
     * <br>
     * For creating linear easing, use {@link AnimationEasing#LINEAR}.
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

        this.cx = 3.0 * x1;
        this.bx = 3.0 * (x2 - x1) - this.cx;
        this.ax = 1.0 - this.cx - this.bx;

        this.cy = 3.0 * y1;
        this.by = 3.0 * (y2 - y1) - this.cy;
        this.ay = 1.0 - this.cy - this.by;

        this.linear = Double.compare(x1, y1) == 0 && Double.compare(x2, y2) == 0;
    }

    public boolean isLinear() {
        return linear;
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

    public double evaluate(double theta) {
        if (theta <= 0.0) {
            return 0.0;
        }
        if (theta >= 1.0) {
            return 1.0;
        }

        if (this.linear) {
            return theta; // For linear curves, elapsed time is the same as elapsed animation progress
        }

        double parameter = solveCurveParameter(theta);
        return sampleCurveY(parameter);
    }

    private double solveCurveParameter(double theta) {
        double u = theta;

        /*
         * First try Newton-Raphson (fall back to binary subdivision on fail)
         * Starting approximation at u = theta
         */
        for (int i = 0; i < NEWTON_ITERATIONS; i++) {
            double error = sampleCurveX(u) - theta;

            // When error is very little, return found u
            if (Math.abs(error) <= SOLVER_EPSILON) {
                return u;
            }

            double slope = sampleCurveDerivativeX(u);

            // When derivative of x(u) is too big, fall back to binary subdivision
            if (Math.abs(slope) < MINIMUM_SLOPE) {
                break;
            }

            double next = u - (error / slope);

            if (next <= 0.0 || next >= 1.0) {
                break;
            }

            u = next;
        }

        /*
         * Fallback: binary subdivision
         */
        double lower = 0.0;
        double upper = 1.0;

        for (int i = 0; i < BISECTION_ITERATIONS; i++) {
            u = 0.5 * (lower + upper);
            double x = sampleCurveX(u);

            if (Math.abs(x - theta) /* = error */ <= SOLVER_EPSILON) {
                return u;
            }

            if (x < theta) {
                lower = u;
            } else {
                upper = u;
            }
        }

        return 0.5 * lower + upper;
    }

    private double sampleCurveX(double u) {
        return ((this.ax * u + this.bx) * u + this.cx) * u;
    }

    private double sampleCurveY(double u) {
        return ((this.ay * u + this.by) * u + this.cy) * u;
    }

    private double sampleCurveDerivativeX(double u) {
        return (3.0 * this.ax * u + 2.0 * this.bx) * u + this.cx;
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
        return Math.abs(x1 - other.x1) < MARGIN_EPSILON
                && Math.abs(y1 - other.y1) < MARGIN_EPSILON
                && Math.abs(x2 - other.x2) < MARGIN_EPSILON
                && Math.abs(y2 - other.y2) < MARGIN_EPSILON;
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
            return LINEAR;
        }
        try {
            double x1 = Double.parseDouble(parts[0]);
            double y1 = Double.parseDouble(parts[1]);
            double x2 = Double.parseDouble(parts[2]);
            double y2 = Double.parseDouble(parts[3]);
            return new AnimationEasing(x1, y1, x2, y2);
        } catch (NumberFormatException e) {
            return LINEAR;
        }
    }

    /**
     * Calculates the cubic bezier value at t for the given points.
     * To receive the x value,enter the x-coordinates of the points,
     * to receive the y value, enter the y-coordinates of the points.
     * <br>
     * This is useful for drawing the parametric curve, if you want to
     * calculate the easing using a time parameter, use
     * {@link #evaluate(double)}.
     *
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

        private final boolean preset;
        private final AnimationEasing easing;

        /**
         * EasingType constructor. All arguments must be between 0 and 1.
         * @param preset whether this type should be automatically set when it is selected
         * @param x1 x coordinate of first point
         * @param y1 y coordinate of first point
         * @param x2 x coordinate of second point
         * @param y2 y coordinate of second point
         * @throws IllegalArgumentException when arguments are out of bounds.
         */
        EasingType(boolean preset, double x1, double y1, double x2, double y2) {
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

            this.preset = preset;
            easing = new AnimationEasing(x1, y1, x2, y2);
        }

        public boolean isPreset() {
            return preset;
        }

        public AnimationEasing getEasing() {
            return this.easing;
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
