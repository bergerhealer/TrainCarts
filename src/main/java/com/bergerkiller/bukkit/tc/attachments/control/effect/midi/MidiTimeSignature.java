package com.bergerkiller.bukkit.tc.attachments.control.effect.midi;

/**
 * A midi time signature, controlling how many notes can be put down per beat,
 * and how many beats exist per measure.
 */
public class MidiTimeSignature {
    private final int beatsPerMeasure;
    private final int noteValue;

    /**
     * A common time signature (4/4)
     */
    public static final MidiTimeSignature COMMON = of(4, 4);

    /**
     * Gets a specific time signature
     *
     * @param beatsPerMeasure Numerator, number of beats per measure
     * @param noteValue Denominator, 4 for quarter notes, 16 for 16th notes.
     *                  Controls how many notes can be put down per beat.
     * @return MidiTimeSignature
     */
    public static MidiTimeSignature of(int beatsPerMeasure, int noteValue) {
        return new MidiTimeSignature(beatsPerMeasure, noteValue);
    }

    private MidiTimeSignature(int beatsPerMeasure, int noteValue) {
        if (beatsPerMeasure < 1) {
            throw new IllegalArgumentException("Invalid number of beats per measure: " + beatsPerMeasure);
        }
        if (noteValue < 1) {
            throw new IllegalArgumentException("Invalid note value: 1/" + noteValue);
        }

        this.beatsPerMeasure = beatsPerMeasure;
        this.noteValue = noteValue;
    }

    /**
     * Gets the number of beats that exist in a single measure
     *
     * @return Beats per measure
     */
    public int beatsPerMeasure() {
        return beatsPerMeasure;
    }

    /**
     * Gets the note value, e.g. 4 for quarter notes, 16 for 16th notes.
     * This controls the number of notes that can be put down per beat.
     *
     * @return Note value
     */
    public int noteValue() {
        return noteValue;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof MidiTimeSignature) {
            MidiTimeSignature other = (MidiTimeSignature) o;
            return beatsPerMeasure == other.beatsPerMeasure && noteValue == other.noteValue;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return beatsPerMeasure + "/" + noteValue;
    }
}
