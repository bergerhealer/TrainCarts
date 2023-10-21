package com.bergerkiller.bukkit.tc.attachments.control.effect.midi;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
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
     * The default (initial) MidiChartParameters, with a BPM of 120 at common 4/4
     * time signature (0.125s time step) and a chromatic (12 pitch classes) scale.
     */
    public static final MidiChartParameters DEFAULT = chromatic(MidiTimeSignature.COMMON, 120);

    private static final double LOG2 = 0.6931471805599453;
    private final MidiTimeSignature timeSignature;
    private final int bpm;
    private final EffectLoop.Time timeStep;
    private final int pitchClasses;
    private final double pitchClassesInv;
    private final double pitchClassesDivLog2;

    /**
     * Creates new Chart Parameters for a chromatic scale (12 pitch classes)
     *
     * @param timeSignature How many notes per beat can be placed (e.g. 4/4)
     * @param bpm Beats per minute. Controls the time duration of a single measure
     * @return Chart Parameters
     */
    public static MidiChartParameters chromatic(MidiTimeSignature timeSignature, int bpm) {
        return of(timeSignature, bpm, 12);
    }

    /**
     * Creates new Chart Parameters
     *
     * @param timeSignature How many notes per beat can be placed (e.g. 4/4)
     * @param bpm Beats per minute. Controls the time duration of a single measure
     * @param pitchClasses Number of pitch classes per doubling of the speed
     * @return Chart Parameters
     */
    public static MidiChartParameters of(MidiTimeSignature timeSignature, int bpm, int pitchClasses) {
        return new MidiChartParameters(timeSignature, bpm, pitchClasses);
    }

    private MidiChartParameters(MidiTimeSignature timeSignature, int bpm, int pitchClasses) {
        if (timeSignature == null) {
            throw new IllegalArgumentException("Null time signature");
        }
        if (bpm < 1) {
            throw new IllegalArgumentException("Beats per minute must be at least 1");
        }
        if (bpm > 60000) {
            throw new IllegalArgumentException("Beats per minute must be no more than 60000");
        }
        if (pitchClasses <= 0) {
            throw new IllegalArgumentException("Number of pitch classes must be at least 1");
        }

        this.timeSignature = timeSignature;
        this.bpm = bpm;
        this.timeStep = EffectLoop.Time.seconds(60.0 / (bpm * timeSignature.noteValue()));
        this.pitchClasses = pitchClasses;
        this.pitchClassesInv = 1.0 / pitchClasses;
        this.pitchClassesDivLog2 = (double) pitchClasses / LOG2;
    }

    /**
     * Gets the amount of time that elapses for a single note that fits on the chart
     *
     * @return Time step duration
     */
    public EffectLoop.Time timeStep() {
        return timeStep;
    }

    /**
     * Gets the time signature, which controls how many notes can be placed per beat,
     * and how many beats exist per measure.
     *
     * @return Time signature
     * @see #bpm()
     * @see #timeStep()
     */
    public MidiTimeSignature timeSignature() {
        return timeSignature;
    }

    /**
     * Clones this MidiChartParameters with a new time signature
     *
     * @param signature New time signature
     * @return New MidiChartParameters with time signature updated
     */
    public MidiChartParameters withTimeSignature(MidiTimeSignature signature) {
        return new MidiChartParameters(signature, this.bpm, this.pitchClasses);
    }

    /**
     * Clones this MidiChartParameters with a new beats per minute
     *
     * @param bpm New beats per minute
     * @return New MidiChartParameters with beats per minute updated
     */
    public MidiChartParameters withBPM(int bpm) {
        return new MidiChartParameters(this.timeSignature, bpm, this.pitchClasses);
    }

    /**
     * Clones this MidiChartParameters with a new number of pitch classes
     *
     * @param numPitchClasses New number of pitch classes
     * @return New MidiChartParameters with the number of pitch classesupdated
     */
    public MidiChartParameters withPitchClasses(int numPitchClasses) {
        return new MidiChartParameters(this.timeSignature, this.bpm, numPitchClasses);
    }

    /**
     * Gets the beats-per-minute configured for the chart. This controls the duration of
     * a single measure of notes.
     *
     * @return Beats per minute
     * @see #timeSignature()
     * @see #timeStep()
     */
    public int bpm() {
        return bpm;
    }

    /**
     * Gets the time step index (note X-coordinate) a given timestamp in seconds
     * falls within.
     *
     * @param timestamp Timestamp from start in seconds
     * @return Time step index
     */
    public int getTimeStepIndex(double timestamp) {
        return getTimeStepIndex(EffectLoop.Time.seconds(timestamp));
    }

    /**
     * Gets the time step index (note X-coordinate) a given timestamp in seconds
     * falls within.
     *
     * @param timestamp Timestamp from start
     * @return Time step note index
     */
    public int getTimeStepIndex(EffectLoop.Time timestamp) {
        return timestamp.roundDiv(timeStep);
    }

    /**
     * Gets a timestamp in seconds based on a time index
     *
     * @param timeStepIndex Time index, or note X-coordinate
     * @return Timestamp in seconds
     * @see #getTimeStepIndex(double)
     */
    public double getTimestamp(int timeStepIndex) {
        return timeStepIndex * timeStep.seconds;
    }

    /**
     * Gets a timestamp in nanoseconds based on a time index
     *
     * @param timeStepIndex Time index, or note X-coordinate
     * @return Timestamp in nanoseconds
     * @see #getTimeStepIndex(double)
     */
    public long getTimestampNanos(int timeStepIndex) {
        return timeStepIndex * timeStep.nanos;
    }

    /**
     * Adjusts the timestamp so that it stays in the same time step, when changing from one
     * set of chart parameters to another.
     *
     * @param time Timestamp
     * @param from MidiChartParameters the timestamp was in
     * @param to MidiChartParameters the timestamp should be in
     * @return Adjusted timestamp
     */
    public static EffectLoop.Time preserveTimeStep(EffectLoop.Time time, MidiChartParameters from, MidiChartParameters to) {
        if (from.timeStep.equals(to.timeStep)) {
            return time;
        } else {
            return EffectLoop.Time.nanos((time.nanos * to.timeStep.nanos) / from.timeStep.nanos);
        }
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
     * @return pitch class, or note Y-coordinate. Can be positive or negative.
     */
    public int getPitchClass(double speed) {
        return (int) Math.round(pitchClassesDivLog2 * Math.log(speed));
    }

    /**
     * Gets the pitch (speed) value of a certain pitch class (bar Y-coordinate)
     *
     * @param pitchClass Pitch class index, negative or positive note Y-coordinate
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
            return this.bpm == other.bpm
                    && this.timeSignature.equals(other.timeSignature)
                    && this.pitchClasses == other.pitchClasses;
        } else {
            return false;
        }
    }

    /**
     * Writes these chart parameters to a YAML configuration
     *
     * @param config YAML configuration to write to
     */
    public void toYaml(ConfigurationNode config) {
        config.set("timeSignature", timeSignature().toString());
        config.set("bpm", bpm());
        config.set("pitchClasses", pitchClasses());
    }

    /**
     * Reads chart parameters from a YAML configuration
     *
     * @param config YAML configuration to read from
     * @return decoded MidiChartParameters. Uses defaults for missing fields.
     */
    public static MidiChartParameters fromYaml(ConfigurationNode config) {
        MidiTimeSignature timeSignature = MidiTimeSignature.fromString(
                config.getOrDefault("timeSignature", ""), DEFAULT.timeSignature());
        int bpm = config.getOrDefault("bpm", DEFAULT.bpm());
        int pitchClasses = config.getOrDefault("pitchClasses", DEFAULT.pitchClasses());
        return of(timeSignature, bpm, pitchClasses);
    }
}
