package com.bergerkiller.bukkit.tc.attachments.control.effect.midi;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;

/**
 * A single MIDI note that can be played. Stores the note play parameters,
 * as well as its position in the chart according to {@link MidiChartParameters}
 */
public final class MidiNote implements Comparable<MidiNote> {
    private final MidiChartParameters chartParams;
    private final double timestamp;
    private final Attachment.EffectAttachment.EffectOptions options;

    // These are calculated using the chartParams and define whether a note is equal to another
    private final int timeStepIndex;
    final long timeStepTimestampNanos;
    private final int pitchClass;
    private final Attachment.EffectAttachment.EffectOptions optionsAdjusted;

    public MidiNote(MidiChartParameters chartParams, double timestamp, Attachment.EffectAttachment.EffectOptions options) {
        this.chartParams = chartParams;
        this.timestamp = timestamp;
        this.options = options;
        // Computed using chartParams
        this.timeStepIndex = chartParams.getTimeStepIndex(timestamp);
        this.timeStepTimestampNanos = chartParams.getTimestampNanos(this.timeStepIndex);
        this.pitchClass = chartParams.getPitchClass(options.speed());
        this.optionsAdjusted = options.withSpeed(chartParams.getPitch(this.pitchClass));
    }

    /**
     * Transforms this note so that it can exist on a different chart with different chart parameters.
     * If the chart parameters are identical return this same note instance.
     *
     * @param chartParams New MIDI chart parameters to put the note on
     * @return MidiNote that sits on a chart of these parameters
     */
    public MidiNote withChartParameters(MidiChartParameters chartParams) {
        if (this.chartParams.equals(chartParams)) {
            return this;
        } else {
            return new MidiNote(chartParams, this.timestamp, this.options);
        }
    }

    /**
     * Gets the timestamp of this note in seconds. This timestamp has not
     * been adjusted to sit on the MIDI chart. It will also not change when
     * {@link #withChartParameters(MidiChartParameters)} is used to change them.
     *
     * @return Timestamp in seconds from the start when this note is played
     */
    public double timestamp() {
        return timestamp;
    }

    /**
     * Gets the time step index. This is the X-axis of where the bar lies on the
     * chart.
     *
     * @return Time step index
     */
    public int timeStepIndex() {
        return timeStepIndex;
    }

    /**
     * Gets the pitch class, where a class of 0 means exactly speed 1.0. This is the
     * Y-axis of where the bar lies on the chart, centered around 0. This value
     * can be both positive (faster) and negative (slower).
     *
     * @return Pitch class
     */
    public int pitchClass() {
        return pitchClass;
    }

    /**
     * Gets the playback options of this note. These are the original options,
     * the pitch has not been adjusted to sit on the MIDI chart. It will also
     * not change when {@link #withChartParameters(MidiChartParameters)} is
     * used to change them.
     *
     * @return Original MIDI note configured options
     */
    public Attachment.EffectAttachment.EffectOptions options() {
        return options;
    }

    /**
     * Plays this note
     *
     * @param effects Effects (instruments) to activate with this note
     */
    public void play(AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> effects) {
        effects.forEach(e -> e.playEffect(optionsAdjusted));
    }

    @Override
    public int compareTo(MidiNote note) {
        int comp = Integer.compare(this.timeStepIndex, note.timeStepIndex);
        if (comp == 0) {
            comp = Integer.compare(this.pitchClass, note.pitchClass);
        }
        return comp;
    }
}
