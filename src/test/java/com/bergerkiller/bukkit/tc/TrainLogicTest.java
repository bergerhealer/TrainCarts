package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import java.util.Random;

import org.bukkit.util.Vector;
import org.junit.Test;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Tests various mathematical train-related logics
 */
public class TrainLogicTest {

    @Test
    public void testProtAngles() {
        testProtAngleBoth(-1.0f, 1.0f, false);
        testProtAngleBoth(90.0f, 92.0f, false);
        testProtAngle(170.0f, 120.0f, false);
        //testProtAngle(-90.0f, 90.0f, true);
        testProtAngleBoth(179.0f, 181.0f, true);
        testProtAngleBoth(179.0f, 180.0f, true);
        testProtAngleBoth(-180.0f, -181.0f, true);
    }

    @Test
    public void isOrientationInvertedTest() {
        Random rand = new Random();
        long nanos_slow = 0;
        long nanos_fast = 0;
        for (int n = 0; n < 10000; n++) {
            Quaternion q = Quaternion.fromYawPitchRoll(
                    360.0 * rand.nextDouble(),
                    360.0 * rand.nextDouble(),
                    360.0 * rand.nextDouble());
            Vector vel = new Vector(rand.nextDouble(), rand.nextDouble(), rand.nextDouble());

            assertEquals(_isOrientationInverted_slow(vel.clone(), q), Util.isOrientationInverted(vel, q));

            // Benchmark with this value pair x10000
            int bench_cnt = 10000;
            {
                Vector vtmp = vel.clone();
                long t1 = System.nanoTime();
                for (int k = 0; k < bench_cnt; k++) {
                    vtmp.setX(vel.getX());
                    vtmp.setY(vel.getY());
                    vtmp.setZ(vel.getZ());
                    _isOrientationInverted_slow(vtmp, q);
                }
                nanos_slow += System.nanoTime() - t1;

                long t2 = System.nanoTime();
                for (int k = 0; k < bench_cnt; k++) {
                    Util.isOrientationInverted(vel, q);
                }
                nanos_fast += System.nanoTime() - t2;
            }
        }

        System.out.println("Performance of Util.isOrientationInverted:");
        System.out.println("SLOW=" + nanos_slow);
        System.out.println("FAST=" + nanos_fast + " (-" + MathUtil.round(100.0*(nanos_slow-nanos_fast)/nanos_slow, 2) + "%)");
    }

    // Original version of isOrientationInverted, prior to optimizations. Is 100% accurate.
    private static boolean _isOrientationInverted_slow(Vector vel, Quaternion q) {
        q = q.clone();
        q.invert();
        q.transformPoint(vel);
        return (vel.getZ() <= 0.0);
    }

    private static void testProtAngleBoth(float a, float b, boolean expected) {
        testProtAngle(a, b, expected);
        testProtAngle(b, a, expected);
    }

    private static void testProtAngle(float a, float b, boolean expected) {
        boolean result = Util.isProtocolRotationGlitched(a, b);
        if (result != expected) {
            fail("calcProtAngle(" + a + " -> " + b + ") = " + result + ", but " + expected + " was expected");
        }
    }
}
