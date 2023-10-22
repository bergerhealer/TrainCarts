package com.bergerkiller.bukkit.tc.attachments.control.effect.midi;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;

/**
 * A single MIDI note that can be played. Stores the note play parameters,
 * as well as its position in the chart according to {@link MidiChartParameters}
 */
public final class MidiNote implements Comparable<MidiNote> {
    private final MidiChartParameters chartParams;
    private final EffectLoop.Time timestamp;
    private final Attachment.EffectAttachment.EffectOptions options;

    // These are calculated using the chartParams and define whether a note is equal to another
    private final int timeStepIndex;
    final long timeStepTimestampNanos;
    private final int pitchClass;
    private final Attachment.EffectAttachment.EffectOptions optionsAdjusted;

    public MidiNote(MidiChartParameters chartParams, double timestampSecs, Attachment.EffectAttachment.EffectOptions options) {
        this(chartParams, EffectLoop.Time.seconds(timestampSecs), options);
    }

    public MidiNote(MidiChartParameters chartParams, EffectLoop.Time timestamp, Attachment.EffectAttachment.EffectOptions options) {
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
            return new MidiNote(chartParams,
                    this.timestamp.adjustBPM(this.chartParams.bpm(), chartParams.bpm()),
                    this.options);
        }
    }

    /**
     * Shifts this note forwards or backwards in time by a number of time steps
     *
     * @param numTimeSteps Number of time steps to shift, negative to go back in time
     * @return MidiNote with timestamp updated
     */
    public MidiNote withTimeShift(int numTimeSteps) {
        if (numTimeSteps == 0) {
            return this;
        } else {
            return new MidiNote(this.chartParams, this.timestamp.add(this.chartParams.timeStep(), numTimeSteps), options);
        }
    }

    /**
     * Shifts this note up or down to a higher or lower pitch, based on pitch classes
     *
     * @param numPitchClasses Number of pitch shift classes to shift. Positive number
     *                        makes the pitch higher, negative number makes it lower.
     * @return MidiNote with pitch (speed) updated
     */
    public MidiNote withPitchShift(int numPitchClasses) {
        if (numPitchClasses == 0) {
            return this;
        } else {
            return new MidiNote(this.chartParams, this.timestamp, options.withSpeed(
                    this.chartParams.getPitch(this.pitchClass + numPitchClasses)));
        }
    }

    /**
     * Gets the timestamp of this note in seconds. This timestamp has not
     * been adjusted to sit on the MIDI chart. It will also not change when
     * {@link #withChartParameters(MidiChartParameters)} is used to change them.
     *
     * @return Timestamp in seconds from the start when this note is played
     */
    public EffectLoop.Time timestamp() {
        return timestamp;
    }

    /**
     * Gets the time step index. This is the X-axis of where the note lies on the
     * chart.
     *
     * @return Time step index
     */
    public int timeStepIndex() {
        return timeStepIndex;
    }

    /**
     * Gets the pitch class, where a class of 0 means exactly speed 1.0. This is the
     * Y-axis of where the note lies on the chart, centered around 0. This value
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

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("t=").append(timestamp().seconds).append(" s=" ).append(options.speed());
        if (options.volume() != 1.0) {
            str.append(" v=").append(options.volume());
        }
        return str.toString();
    }

    /**
     * Decodes {@link #toString()} back into a MidiNote
     *
     * @param chartParams Chart Parameters on which the Midi note will be put
     * @param noteStr String to decode
     * @return Decoded MidiNote. Returns <i>null</i> if the String lacks information about
     *         the note timestamp or speed (pitch). Volume is assumed to be 1.0 if omitted.
     */
    public static MidiNote fromString(MidiChartParameters chartParams, String noteStr) {
        int startIndex = 0;
        boolean done = false;
        double timestamp = Double.NaN, speed = Double.NaN, volume = 1.0;
        do {
            int endIndex = noteStr.indexOf(' ', startIndex);
            if (endIndex == -1) {
                endIndex = noteStr.length();
                done = true;
            }

            String entry = noteStr.substring(startIndex, endIndex);
            if (entry.startsWith("t=")) {
                timestamp = ParseUtil.parseDouble(entry.substring(2), timestamp);
            } else if (entry.startsWith("s=")) {
                speed = ParseUtil.parseDouble(entry.substring(2), speed);
            } else if (entry.startsWith("v=")) {
                volume = ParseUtil.parseDouble(entry.substring(2), volume);
            }

            startIndex = endIndex + 1;
        } while (!done);

        if (Double.isNaN(timestamp) || Double.isNaN(speed)) {
            return null;
        }

        return new MidiNote(chartParams, timestamp, Attachment.EffectAttachment.EffectOptions.of(volume, speed));
    }
}
