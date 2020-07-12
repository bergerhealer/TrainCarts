package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Random;

import org.junit.Test;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;

public class FastRotationTest {
    // maximum allowed error in the calculated armor stand angle in degrees
    private static final double MAX_ANGLE_ERROR = 1e-4;

    @Test
    public void getFastRotationYawTest() {
        Random r = new Random();
        Quaternion[] rotations = new Quaternion[10000];
        double[] yaw_a = new double[rotations.length];
        double[] yaw_b = new double[rotations.length];
        for (int i = 0; i < rotations.length; i++) {
            rotations[i] = new Quaternion(r.nextDouble()-0.5, r.nextDouble()-0.5, r.nextDouble()-0.5, r.nextDouble()-0.5);
        }
        rotations[0] = Quaternion.fromYawPitchRoll(90.0, 0.0, 20.0);
        rotations[1] = Quaternion.fromYawPitchRoll(-90.0, 0.0, 20.0);

        // Warm up
        (new Quaternion()).getYawPitchRoll().getY();
        Util.fastGetRotationYaw(new Quaternion());

        // Run test
        long time_a = System.currentTimeMillis();
        for (int i = 0; i < rotations.length; i++) {
            yaw_a[i] = rotations[i].getYawPitchRoll().getY();
        }
        long time_b = System.currentTimeMillis();
        for (int i = 0; i < rotations.length; i++) {
            yaw_b[i] = Util.fastGetRotationYaw(rotations[i]);
        }
        long time_c = System.currentTimeMillis();

        System.out.println("getYawPitchRoll().getY() took " + (time_b-time_a) + " ms");
        System.out.println("fastGetRotationYaw() took " + (time_c-time_b) + " ms");

        for (int i = 0; i < rotations.length; i++) {
            assertEquals(yaw_a[i], yaw_b[i], MAX_ANGLE_ERROR);
        }
    }

    @Test
    public void testFastAsin() {
        Random r = new Random();
        double[] values = new double[100000];
        double[] yaw_a = new double[values.length];
        double[] yaw_b = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = 2.0 * r.nextDouble() - 1.0;
        }

        // Warm up
        Util.fastAsin(0.0);

        // Run test
        long time_a = System.currentTimeMillis();
        for (int i = 0; i < values.length; i++) {
            yaw_a[i] = Math.toDegrees(Math.asin(values[i]));
        }
        long time_b = System.currentTimeMillis();
        for (int i = 0; i < values.length; i++) {
            yaw_b[i] = Util.fastAsin(values[i]);
        }
        long time_c = System.currentTimeMillis();

        System.out.println("Math.asin() took " + (time_b-time_a) + " ms");
        System.out.println("fastAsin() took " + (time_c-time_b) + " ms");

        for (int i = 0; i < values.length; i++) {
            assertEquals(yaw_a[i], yaw_b[i], MAX_ANGLE_ERROR);
        }
    }

    @Test
    public void testEntityYaw() {
        for (float base_angle = -360.0f; base_angle <= 360.0f; base_angle += 0.01f) {
            for (double yaw_change = -89.0; yaw_change <= 89.0; yaw_change += 0.01) {
                double new_yaw = Util.getNextEntityYaw(base_angle, yaw_change);
                double actual_change = new_yaw - base_angle;
                while (actual_change <= -180.0) {
                    actual_change += 360.0;
                }
                while (actual_change > 180.0) {
                    actual_change -= 360.0;
                }

                if (MathUtil.getAngleDifference((float) yaw_change, (float) actual_change) > 1.41f) {
                    fail("Yaw change undershoot: base=" + base_angle + " change=" + yaw_change + " output_change=" + actual_change);
                }
                if ((yaw_change >= 0.0) ? (actual_change > yaw_change) : (actual_change < yaw_change)) {
                    fail("Yaw change overshoot: base=" + base_angle + " change=" + yaw_change + " output_change=" + actual_change);
                }
            }
        }
    }
}
