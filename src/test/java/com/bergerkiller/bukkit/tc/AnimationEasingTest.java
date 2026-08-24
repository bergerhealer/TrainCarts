package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.attachments.animation.AnimationEasing;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AnimationEasingTest {

    // Check if easing makes any unusual large jumps
    @Test
    public void testBisectionIterationsLargeJumps() {
        testBisectionIterationLargeJump(new AnimationEasing(0.975, 0.025, 0.975, 0.025));
        testBisectionIterationLargeJump(new AnimationEasing(0.95, 0.05, 0.95, 0.05));
        testBisectionIterationLargeJump(new AnimationEasing(0.90, 0.05, 0.90, 0.05));
    }

    private void testBisectionIterationLargeJump(AnimationEasing easing) {
        final int samples = 100000;

        double prev = easing.evaluate(0.0);
        double largestJump = 0.0;

        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            double value = easing.evaluate(t);

            largestJump = Math.max(largestJump,
                    Math.abs(value - prev));

            prev = value;
        }

        assertTrue("Largest jump was " + largestJump, largestJump < 0.01);
    }

    // Check if easing is monotonically non-decreasing
    @Test
    public void testBisectionIterationsNoNegativeJumps() {
        testBisectionIterationNoNegativeJumps(new AnimationEasing(1.0, 0.0, 1.0, 0.0));
        testBisectionIterationNoNegativeJumps(new AnimationEasing(0.975, 0.025, 0.975, 0.025));
        testBisectionIterationNoNegativeJumps(new AnimationEasing(0.95, 0.05, 0.95, 0.05));
        testBisectionIterationNoNegativeJumps(new AnimationEasing(0.90, 0.05, 0.90, 0.05));
    }

    private void testBisectionIterationNoNegativeJumps(AnimationEasing easing) {
        final int samples = 100000;

        double prev = easing.evaluate(0.0);

        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            double value = easing.evaluate(t);

            double delta = value - prev;

            assertTrue(
                    "Negative jump of " + delta + " at t=" + t,
                    delta >= 0.0);

            prev = value;
        }
    }

}
