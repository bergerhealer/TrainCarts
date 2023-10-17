package com.bergerkiller.bukkit.tc.attachments.control.effect.midi;

import com.bergerkiller.bukkit.common.utils.ParseUtil;

/**
 * A midi time signature, controlling how many notes can be put down per beat,
 * and how many beats exist per measure.
 */
public class MidiTimeSignature {
    private final int beatsPerMeasure;
    private final int noteValue;
    private final int notesPerMeasure;

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
        this.notesPerMeasure = noteValue * beatsPerMeasure;
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
     * Gets the number of notes displayed per measure of music
     *
     * @return Notes per measure (beats per measure * note value)
     */
    public int notesPerMeasure() {
        return notesPerMeasure;
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

    /**
     * Parses {@link #toString()} back into a MidiTimeSignature
     *
     * @param signatureText Text output of {@link #toString()}, e.g. "4/4"
     * @param defaultSig Default time signature to return on failure
     * @return MidiTimeSignature. On failure returns defaultSig
     */
    public static MidiTimeSignature fromString(String signatureText, MidiTimeSignature defaultSig) {
        int sep;
        if (signatureText != null && (sep = signatureText.indexOf('/')) != -1) {
            String beatsPerMeasureStr = signatureText.substring(0, sep).trim();
            String noteValueStr = signatureText.substring(sep + 1).trim();
            return of(ParseUtil.parseInt(beatsPerMeasureStr, defaultSig.beatsPerMeasure),
                      ParseUtil.parseInt(noteValueStr, defaultSig.noteValue));
        } else {
            return defaultSig;
        }
    }
}
