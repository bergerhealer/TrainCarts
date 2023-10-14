package com.bergerkiller.bukkit.tc.attachments.control.effect.midi;

import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;

/**
 * The parameters defining the appearance of a MIDI chart of notes.
 * Defines the duration of a single time step ('bar') and how
 * many speed/pitch classes exist. The chromatic scale uses
 * 12 pitch classes, which is the default. Includes mathematical
 * helper methods to work with these parameters.
 */
public final class MidiChartParameters {
    /**
     * The default (initial) MidiChartParameters, with a BPM of 120 (0.5s time step)
     * and a chromatic (12 pitch classes) scale.
     */
    public static final MidiChartParameters DEFAULT = chromatic(0.5);

    private static final double LOG2 = 0.6931471805599453;
    private final EffectLoop.Time timeStep;
    private final int pitchClasses;
    private final double pitchClassesInv;
    private final double pitchClassesDivLog2;

    /**
     * Creates new Chart Parameters for a chromatic scale (12 pitch classes)
     *
     * @param timeStep Time duration of a single time step (bar) in seconds
     * @return Chart Parameters
     */
    public static MidiChartParameters chromatic(double timeStep) {
        return of(timeStep, 12);
    }

    /**
     * Creates new Chart Parameters
     *
     * @param timeStep Time duration of a single time step (bar) in seconds
     * @param pitchClasses Number of pitch classes per doubling of the speed
     * @return Chart Parameters
     */
    public static MidiChartParameters of(double timeStep, int pitchClasses) {
        return new MidiChartParameters(timeStep, pitchClasses);
    }

    private MidiChartParameters(double timeStep, int pitchClasses) {
        if (pitchClasses <= 0) {
            throw new IllegalArgumentException("Number of pitch classes must be at least 1");
        }
        if (timeStep < 1e-6) {
            throw new IllegalArgumentException("Time Step must be at least 1e-6 seconds");
        }
        this.timeStep = EffectLoop.Time.seconds(timeStep);
        this.pitchClasses = pitchClasses;
        this.pitchClassesInv = 1.0 / pitchClasses;
        this.pitchClassesDivLog2 = (double) pitchClasses / LOG2;
    }

    /**
     * Gets the amount of time that elapses for a single 'bar' on the chart
     *
     * @return Time step duration
     */
    public EffectLoop.Time timeStep() {
        return timeStep;
    }

    /**
     * Gets the time step index (bar X-coordinate) a given timestamp in seconds
     * falls within.
     *
     * @param timestamp Timestamp from start in seconds
     * @return Time step bar index
     */
    public int getTimeStepIndex(double timestamp) {
        return (int) (EffectLoop.Time.secondsToNanos(timestamp) / timeStep.nanos);
    }

    /**
     * Gets the time step index (bar X-coordinate) a given timestamp in seconds
     * falls within.
     *
     * @param timestamp Timestamp from start
     * @return Time step bar index
     */
    public int getTimeStepIndex(EffectLoop.Time timestamp) {
        return (int) (timestamp.nanos / timeStep.nanos);
    }

    /**
     * Gets a timestamp in seconds based on a time index
     *
     * @param timeStepIndex Time index, or bar X-coordinate
     * @return Timestamp in seconds
     * @see #getTimeStepIndex(double)
     */
    public double getTimestamp(int timeStepIndex) {
        return timeStepIndex * timeStep.seconds;
    }

    /**
     * Gets a timestamp in nanoseconds based on a time index
     *
     * @param timeStepIndex Time index, or bar X-coordinate
     * @return Timestamp in nanoseconds
     * @see #getTimeStepIndex(double)
     */
    public long getTimestampNanos(int timeStepIndex) {
        return timeStepIndex * timeStep.nanos;
    }

    /**
     * Gets the number of pitch classes. This is the amount of pitch values that
     * exist to double or halve the frequency of a sound.
     *
     * @return Number of pitch classes, e.g. 12
     */
    public int pitchClasses() {
        return pitchClasses;
    }

    /**
     * Gets a pitch class index (bar Y-coordinate) that a certain playback speed
     * roughly falls within. This assumes that a speed of 1.0 is at pitch class 0,
     * and a speed of 2.0 is at pitch class {@link #pitchClasses()}
     *
     * @param speed Speed or pitch value
     * @return pitch class, or bar Y-coordinate. Can be positive or negative.
     */
    public int getPitchClass(double speed) {
        return (int) Math.round(pitchClassesDivLog2 * Math.log(speed));
    }

    /**
     * Gets the pitch (speed) value of a certain pitch class (bar Y-coordinate)
     *
     * @param pitchClass Pitch class index, negative or positive bar Y-coordinate
     * @return Speed or pitch value
     * @see #getPitchClass(double)
     */
    public double getPitch(int pitchClass) {
        return Math.pow(2.0, pitchClassesInv * pitchClass);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof MidiChartParameters) {
            MidiChartParameters other = (MidiChartParameters) o;
            return this.timeStep == other.timeStep && this.pitchClasses == other.pitchClasses;
        } else {
            return false;
        }
    }
}
