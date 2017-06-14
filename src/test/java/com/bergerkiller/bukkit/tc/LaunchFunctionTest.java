package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.bukkit.tc.utils.LaunchFunction;

public class LaunchFunctionTest {

    @Test
    public void testLinearFunction() {
        testAll(new LaunchFunction.Linear());
    }

    @Test
    public void testBezierFunction() {
        testAll(new LaunchFunction.Bezier());
    }

    @Test
    public void testLogFunction() {
        LaunchFunction function = new LaunchFunction.Bezier();
        function.setVelocityRange(0.2, 0.4);
        function.setTotalDistance(20.0);

        //logVelocityCurve(function);
        //logDistanceCurve(function);
    }

    public void testAll(LaunchFunction function) {
        testTime(function, 0.0, 0.4, 100, 20.0);
        testTime(function, 0.0, 0.4, 200, 40.2);
        testTime(function, 0.4, 0.0, 100, 20.0);
        testTime(function, 0.2, 0.4, 100, 30.3);
        testTime(function, 0.4, 0.2, 100, 30.3);
        testTime(function, 0.2, 0.2, 20, 4.2);
        testTime(function, 0.2, 0.2, 100, 20.2);

        testDistance(function, 0.0, 0.4, 15.0, 75);
        testDistance(function, 0.4, 0.0, 15.0, 75);
        testDistance(function, 0.2, 0.4, 15.0, 50);
        testDistance(function, 0.4, 0.2, 15.0, 50);
        testDistance(function, 0.1, 0.1, 15.0, 150);
        testDistance(function, 0.2, 0.4, 20.0, 66);
        testDistance(function, 0.0, 0.4, 100.0, 500);
    }

    public void testTime(LaunchFunction function, double vStart, double vEnd, int totalTime, double expDistance) {
        function.setVelocityRange(vStart, vEnd);
        function.setTotalTime(totalTime);
        assertEquals(totalTime, function.getTotalTime());

        double totalDistance = function.getTotalDistance();
        if (totalDistance < (expDistance - 2.0) || totalDistance > (expDistance + 2.0)) {
            fail("Function total distance out of expected range (expected=" + expDistance + "): " + totalDistance);
        }

        verifyMovement(function);
    }

    public void testDistance(LaunchFunction function, double vStart, double vEnd, double totalDistance, int expTime) {
        function.setVelocityRange(vStart, vEnd);
        function.setTotalDistance(totalDistance);
        assertEquals(totalDistance, function.getTotalDistance(), 0.001);

        int totalTime = function.getTotalTime();
        if (totalTime < (expTime - 10) || totalTime > (expTime + 10)) {
            fail("Function total time out of expected range (expected=" + expTime + "): " + totalTime);
        }

        verifyMovement(function);
    }

    public void verifyMovement(LaunchFunction function) {
        int totalTime = function.getTotalTime();
        double velStart = (function.getDistance(1) - function.getDistance(0));
        double velEnd = (function.getDistance(totalTime) - function.getDistance(totalTime - 1));
        assertEquals(function.getStartVelocity(), velStart, 0.01);
        assertEquals(function.getEndVelocity(), velEnd, 0.01);

        double last = 0.0;
        for (int tick = 0; tick <= totalTime; tick++) {
            double distance = function.getDistance(tick);
            if (distance < last) {
                fail("Distance went back down! Old=" + last + " New=" + distance + " at tick=" + tick);
            }
            last = distance;
        }
    }

    public void logDistanceCurve(LaunchFunction function) {
        int totalTime = function.getTotalTime();
        for (int tick = 0; tick <= totalTime; tick++) {
            System.out.println(function.getDistance(tick));
        }
    }

    public void logVelocityCurve(LaunchFunction function) {
        double d = 0.0;
        int totalTime = function.getTotalTime();
        for (int tick = 0; tick <= totalTime; tick++) {
            double distance = function.getDistance(tick);
            System.out.println(distance - d);
            d = distance;
        }
    }
}
