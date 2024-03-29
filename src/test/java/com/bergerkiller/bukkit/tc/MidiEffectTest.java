package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChartParameters;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiTimeSignature;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests maths and logic of Midi (noteblock-esque) Playback
 */
public class MidiEffectTest {

    @Test
    public void testTimeRoundDiv() {
        assertEquals(2, roundDiv(1000, 500));
        assertEquals(2, roundDiv(1249, 500));
        assertEquals(2, roundDiv(751, 500));
        assertEquals(1, roundDiv(1000, 1000));
        assertEquals(1, roundDiv(501, 1000));
        assertEquals(1, roundDiv(1499, 1000));
        // Not going to happen in practise, but it's good to play it safe
        assertEquals(-1, roundDiv(-1000, 1000));
        assertEquals(-1, roundDiv(-1499, 1000));
        assertEquals(-1, roundDiv(-501, 1000));
    }

    private static int roundDiv(int num, int div) {
        return EffectLoop.Time.nanos(num).roundDiv(EffectLoop.Time.nanos(div));
    }

    @Test
    public void testChromaticPitchToPitchClass() {
        MidiChartParameters p = MidiChartParameters.chromatic(MidiTimeSignature.COMMON, 150);
        assertEquals(0, p.getPitchClass(1.0));
        assertEquals(0, p.getPitchClass(0.9999));
        assertEquals(0, p.getPitchClass(1.0001));
        assertEquals(-6, p.getPitchClass(0.707107));
        assertEquals(6, p.getPitchClass(1.414214));
        assertEquals(-12, p.getPitchClass(0.5));
        assertEquals(12, p.getPitchClass(2.0));
    }

    @Test
    public void testChromaticPitchClassToPitch() {
        MidiChartParameters p = MidiChartParameters.chromatic(MidiTimeSignature.COMMON, 150);
        assertEquals(1.0, p.getPitch(0), 1e-6);
        assertEquals(0.707107, p.getPitch(-6), 1e-6);
        assertEquals(1.414214, p.getPitch(6), 1e-6);
        assertEquals(0.5, p.getPitch(-12), 1e-6);
        assertEquals(2.0, p.getPitch(12), 1e-6);
    }

    @Test
    public void testGetTimeStepIndex() {
        MidiChartParameters p = MidiChartParameters.chromatic(MidiTimeSignature.COMMON, 150);
        assertEquals(0, p.getTimeStepIndex(0.0));
        assertEquals(1, p.getTimeStepIndex(0.1));
        assertEquals(1, p.getTimeStepIndex(0.099));
        assertEquals(1, p.getTimeStepIndex(0.101));
        assertEquals(5, p.getTimeStepIndex(0.5));
        assertEquals(200, p.getTimeStepIndex(20.0));
    }

    @Test
    public void testGetTimestamp() {
        MidiChartParameters p = MidiChartParameters.chromatic(MidiTimeSignature.COMMON, 150);
        assertEquals(0.0, p.getTimestamp(0), 1e-8);
        assertEquals(0.1, p.getTimestamp(1), 1e-8);
        assertEquals(20.0, p.getTimestamp(200), 1e-8);
    }
}
