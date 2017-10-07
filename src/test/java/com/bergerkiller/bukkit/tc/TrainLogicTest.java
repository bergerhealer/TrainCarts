package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.junit.Test;

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
