package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.Objects; /**
     * A class which holds easing function parameters
     */
    public class Easing {

        private final float x1;
        private final float y1;
        private final float x2;
        private final float y2;
        private static final float EPSILON = 0.0001f;

        /**
         *
         * @param x1 x coordinate for point 1
         * @param y1 y coordinate for point 1
         * @param x2 x coordinate for point 2
         * @param y2 y coordinate for point 2
         */
        public Easing(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        public float getX1() {
            return x1;
        }

        public float getY1() {
            return y1;
        }

        public float getX2() {
            return x2;
        }

        public float getY2() {
            return y2;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Easing)) {
                return false;
            }

            Easing other = (Easing) obj;

            return Float.compare(x1, other.x1) == 0
                    && Float.compare(y1, other.y1) == 0
                    && Float.compare(x2, other.x2) == 0
                    && Float.compare(y2, other.y2) == 0;
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
        public boolean approximatelyEquals(Easing other) {
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
        public static Easing parse(String string) {
            String[] parts = string.split(",");
            if (parts.length != 4) {
                return null;
            }
            try {
                float x1 = Float.parseFloat(parts[0]);
                float y1 = Float.parseFloat(parts[1]);
                float x2 = Float.parseFloat(parts[2]);
                float y2 = Float.parseFloat(parts[3]);
                return new Easing(x1, y1, x2, y2);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * All easing type presets. Also contains CUSTOM which can be used to specify a custom easing curve.
         */
        public enum EasingType {
            LINEAR(false, 0, 0, 1, 1),
            SINE_IN(false, 0.12f, 0, 0.39f, 0),
            SINE_OUT(false, 0.61f, 1, 0.88f, 1),
            SINE_IN_OUT(false, 0.37f, 0, 0.63f, 1),
            CUBIC_IN(false, 0.32f, 0, 0.67f, 0),
            CUBIC_OUT(false, 0.33f, 1, 0.68f, 1),
            CUBIC_IN_OUT(false, 0.65f, 0, 0.35f, 1),
            EXP_IN(false, 0.7f, 0, 0.84f, 0),
            EXP_OUT(false, 0.16f, 1, 0.3f, 1),
            EXP_IN_OUT(false, 0.87f, 0, 0.13f, 1),
            CUSTOM(true, 0, 0, 1, 1);

            private final boolean editable;
            private final float x1;
            private final float y1;
            private final float x2;
            private final float y2;

            /**
             * EasingType constructor. All arguments must be between 0 and 1.
             * @param editable whether this type should be automatically set when it is selected
             * @param x1 x coordinate of first point
             * @param y1 y coordinate of first point
             * @param x2 x coordinate of second point
             * @param y2 y coordinate of second point
             * @throws IllegalArgumentException when arguments are out of bounds.
             */
            EasingType(boolean editable, float x1, float y1, float x2, float y2) {
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

                this.editable = editable;
                this.x1 = x1;
                this.y1 = y1;
                this.x2 = x2;
                this.y2 = y2;
            }

            public boolean isEditable() {
                return editable;
            }

            public float getX1() {
                return x1;
            }

            public float getY1() {
                return y1;
            }

            public float getX2() {
                return x2;
            }

            public float getY2() {
                return y2;
            }

            public float cubicBezierY(float t) {
                return cubicBezier(0, y1, y2, 1, 0.5f);
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
            public static float cubicBezier(float p0, float p1, float p2, float p3, float t) {
                float mt = 1.0f - t;

                return mt * mt * mt * p0
                        + 3.0f * mt * mt * t * p1
                        + 3.0f * mt * t * t * p2
                        + t * t * t * p3;
            }

        }
}
