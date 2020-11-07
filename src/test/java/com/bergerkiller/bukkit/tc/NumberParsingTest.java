package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests number parsing functions used in Traincarts
 */
public class NumberParsingTest {

    @Test
    public void testParseVelocity() {
        // Various ways of specifying velocity
        assertEquals(12, Util.parseVelocity("12", Double.NaN), 1e-5);
        assertEquals(12.5, Util.parseVelocity("12.5", Double.NaN), 1e-5);
        assertEquals(-12.5, Util.parseVelocity("-12.5", Double.NaN), 1e-5);
        assertEquals(1.0, Util.parseVelocity("20m/s", Double.NaN), 1e-5);
        assertEquals(0.27778, Util.parseVelocity("20km/h", Double.NaN), 1e-5);
        assertEquals(0.44704, Util.parseVelocity("20mi/h", Double.NaN), 1e-5);
        assertEquals(0.05, Util.parseVelocity("3.28ft/s", Double.NaN), 1e-5);
        assertEquals(0.27778, Util.parseVelocity("20kmh", Double.NaN), 1e-5);
        assertEquals(0.27778, Util.parseVelocity("20kmph", Double.NaN), 1e-5);
        assertEquals(0.44704, Util.parseVelocity("20mph", Double.NaN), 1e-5);
        assertEquals(-0.44704, Util.parseVelocity("-20mph", Double.NaN), 1e-5);

        // Invalid numbers
        assertEquals(20.0, Util.parseVelocity("gibberish", 20.0), 0.0);
    }

    @Test
    public void testParseAcceleration() {
        // Various ways of specifying acceleration
        assertEquals(0.05, Util.parseAcceleration("20/ss", Double.NaN), 0.0);
        assertEquals(0.05, Util.parseAcceleration("20/s/s", Double.NaN), 0.0);
        assertEquals(0.05, Util.parseAcceleration("20/S2", Double.NaN), 0.0);
        assertEquals(0.05, Util.parseAcceleration("20/s2", Double.NaN), 0.0);
        assertEquals(50.0, Util.parseAcceleration("20k/s2", Double.NaN), 0.0);
        assertEquals(50.0, Util.parseAcceleration("20K/ss", Double.NaN), 0.0);
        assertEquals(75.0, Util.parseAcceleration("30km/ss", Double.NaN), 0.0);
        assertEquals(0.020833, Util.parseAcceleration("30km/h/s", Double.NaN), 1e-5);
        assertEquals(0.020833, Util.parseAcceleration("30KM/H/S", Double.NaN), 1e-5);
        assertEquals(0.020833, Util.parseAcceleration("30km/hs", Double.NaN), 1e-5);
        assertEquals(0.020833, Util.parseAcceleration("30kmh/s", Double.NaN), 1e-5);
        assertEquals(0.020833, Util.parseAcceleration("30kmph/s", Double.NaN), 1e-5);
        assertEquals(0.0025, Util.parseAcceleration("3.28ft/s/s", Double.NaN), 1e-5);
        assertEquals(0.0011176, Util.parseAcceleration("1.0mi/h/s", Double.NaN), 1e-5);
        assertEquals(0.0011176, Util.parseAcceleration("1.0mph/s", Double.NaN), 1e-5);

        // Based on gravity
        assertEquals(0.024525, Util.parseAcceleration("1g", Double.NaN), 0.0);
        assertEquals(0.0367875, Util.parseAcceleration("1.5G", Double.NaN), 0.0);

        // Invalid numbers
        assertEquals(20.0, Util.parseAcceleration("gibberish", 20.0), 0.0);
    }
}
