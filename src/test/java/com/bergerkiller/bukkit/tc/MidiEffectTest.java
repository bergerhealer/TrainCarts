package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChartParameters;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiTimeSignature;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests maths and logic of Midi (noteblock-esque) Playback
 */
public class MidiEffectTest {

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
