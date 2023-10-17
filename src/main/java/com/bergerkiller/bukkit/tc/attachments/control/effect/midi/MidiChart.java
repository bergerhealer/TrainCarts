package com.bergerkiller.bukkit.tc.attachments.control.effect.midi;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sequence of notes, sorted by time and then pitch, aligned on a
 * 'piano roll' chart. Offers methods for placing, removing and moving
 * notes on the chart, as well as handling serialization from/to YAML.
 */
public final class MidiChart implements Cloneable {
    private final MidiChartParameters chartParams;
    private final List<MidiNote> notes = new ArrayList<>();
    private int lastIndex = 0;

    /**
     * Initializes a new, empty, MIDI chart. The chart parameters configure the
     * time and pitch scaling of the bars put on the chart.
     *
     * @param chartParams MIDI Chart parameters
     */
    public MidiChart(MidiChartParameters chartParams) {
        this.chartParams = chartParams;
    }

    /**
     * Gets the parameters that control the time step and pitch classes
     * of this chart
     *
     * @return MIDI Chart Parameters
     */
    public MidiChartParameters getParameters() {
        return chartParams;
    }

    /**
     * Transforms this chart so that it uses different chart parameters.
     * All original notes are updated to fit on this new chart.
     * Notes that clash because they occupy the same note position are
     * removed.
     *
     * @param chartParams New MIDI chart parameters to put all the notes on
     * @return MidiChart that uses the new parameters
     */
    public MidiChart withChartParameters(MidiChartParameters chartParams) {
        MidiChart updated = new MidiChart(chartParams);
        for (MidiNote note : this.notes) {
            updated.addNote(note);
        }
        return updated;
    }

    /**
     * Gets whether this chart is empty, that is, has no notes
     *
     * @return True if empty
     */
    public boolean isEmpty() {
        return notes.isEmpty();
    }

    /**
     * Gets a List of all the notes that exist, sorted by time and then by speed (pitch)
     *
     * @return notes
     */
    public List<MidiNote> getNotes() {
        return notes;
    }

    /**
     * Gets the minimum and maximum time step index and pitch classes of all notes on this
     * chart
     *
     * @return Bounds
     * @see Bounds#isEmpty()
     */
    public Bounds getBounds() {
        if (isEmpty()) {
            return Bounds.EMPTY;
        }

        int minTimeStepIndex = notes.get(0).timeStepIndex();
        int maxTimeStepIndex = notes.get(notes.size() - 1).timeStepIndex();
        int minPitch = Integer.MAX_VALUE;
        int maxPitch = Integer.MIN_VALUE;
        for (MidiNote note : notes) {
            minPitch = Math.min(minPitch, note.pitchClass());
            maxPitch = Math.max(maxPitch, note.pitchClass());
        }
        return new Bounds(minTimeStepIndex, maxTimeStepIndex, minPitch, maxPitch);
    }

    /**
     * Clears all previously added notes, making this chart empty
     */
    public void clearNotes() {
        notes.clear();
    }

    /**
     * Shifts all the notes forwards or backwards in time by one or more time steps.
     *
     * @param numTimeSteps Number of time steps to shift. Negative to shift them backwards in time.
     */
    public void timeShift(int numTimeSteps) {
        if (numTimeSteps != 0) {
            for (ListIterator<MidiNote> it = notes.listIterator(); it.hasNext();) {
                it.set(it.next().withTimeShift(numTimeSteps));
            }
        }
    }

    /**
     * Shifts the speed (pitch) up or down by a number of pitch classes
     *
     * @param numPitchClasses Number of pitch shift classes to shift. Positive number
     *                        makes the pitch higher, negative number makes it lower.
     */
    public void pitchShift(int numPitchClasses) {
        if (numPitchClasses != 0) {
            for (ListIterator<MidiNote> it = notes.listIterator(); it.hasNext();) {
                it.set(it.next().withPitchShift(numPitchClasses));
            }
        }
    }

    /**
     * Performs an operation for all the MIDI notes that lie within a range of two nanos timestamps.
     * Is optimized for sequential playback. Returns False if no more notes can be found
     * without looping back to the beginning.
     *
     * @param prevNanos Previous nanos timestamp
     * @param currNanos Current nanos timestamp
     * @param action Action to perform on the notes within
     * @return True if more notes will be played later, False if the end of the chart has been reached
     */
    public boolean forNotesInRange(long prevNanos, long currNanos, Consumer<MidiNote> action) {
        int currIndex = this.lastIndex;
        List<MidiNote> notes = this.notes;
        int notesCount = notes.size();

        // Reset search to beginning if we're skipping over stuff
        if (currIndex >= notesCount || notes.get(currIndex).timeStepTimestampNanos > prevNanos) {
            currIndex = 0;
        }

        // Advance currIndex until it is beyond currNanos
        // Call callback if it is after or equal to prevNanos
        MidiNote n;
        while (currIndex < notesCount && (n = notes.get(currIndex)).timeStepTimestampNanos < currNanos) {
            if (n.timeStepTimestampNanos >= prevNanos) {
                action.accept(n);
            }
            currIndex++;
        }

        // Remember for next time
        this.lastIndex = currIndex;

        // There's more if currIndex is not beyond the notes list
        return currIndex < notesCount;
    }

    /**
     * Gets the notes that are visible within a certain time step index range.
     *
     * @param startTimeStepIndex Start index of the first note to show
     * @param numTimeSteps Number of time steps ('bars') displayed
     * @return List of MIDI notes visible in this time range on the chart
     */
    public List<MidiNote> getChartVisibleNotes(int startTimeStepIndex, int numTimeSteps) {
        //TODO: Could be optimized, do binary search maybe
        List<MidiNote> result = new ArrayList<>();
        for (MidiNote note : notes) {
            int offset = note.timeStepIndex() - startTimeStepIndex;
            if (offset >= 0 && offset < numTimeSteps) {
                result.add(note);
            }
        }
        return result;
    }

    /**
     * Updates a single note
     *
     * @param note Note to update
     * @param operation Operation to perform on the note, that returns an updated note
     * @return The resulting updated note
     */
    public MidiNote update(MidiNote note, Function<MidiNote, MidiNote> operation) {
        MidiNote updated = operation.apply(note);
        removeNote(note);
        addNoteDirect(updated);
        return updated;
    }

    /**
     * Gets whether this chart contains a particular note
     *
     * @param note MIDI note
     * @return True if this note is contained on this chart
     */
    public boolean containsNote(MidiNote note) {
        return Collections.binarySearch(notes, note) >= 0;
    }

    /**
     * Checks whether this chart contains all the notes specified
     *
     * @param notes MIDI notes to find in this chart
     * @return True if all notes are contained
     */
    public boolean containsAllNotes(Collection<MidiNote> notes) {
        for (MidiNote note : notes) {
            if (!containsNote(note)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes a previously added note
     *
     * @param note MIDI Note to remove
     */
    public void removeNote(MidiNote note) {
        int index = Collections.binarySearch(notes, note);
        if (index >= 0) {
            notes.remove(index);
        }
    }

    /**
     * Adds a note on a specific position on this chart
     *
     * @param timeStepIndex Number of time steps ('notes') from the start of the chart.
     *                      This number is controlled by the BPM and time signature.
     * @param pitchClass Number of pitch classes up or down (speed/pitch)
     * @param volume Volume to play the note at
     * @return Newly added MidiNote
     */
    public MidiNote addNoteOnBar(int timeStepIndex, int pitchClass, double volume) {
        MidiNote note = new MidiNote(chartParams,
                chartParams.timeStep().multiply(timeStepIndex),
                Attachment.EffectAttachment.EffectOptions.of(volume, chartParams.getPitch(pitchClass)));
        addNoteDirect(note);
        return note;
    }

    /**
     * Adds a new MIDI note to this chart. Can be in any timestamp order.
     * If a note already existed at this position in the chart, it is overwritten.
     *
     * @param timestamp Timestamp of the note in seconds from 0
     * @param volume Volume
     * @param speed Speed (or pitch)
     * @return Newly added MidiNote
     */
    public MidiNote addNote(double timestamp, double volume, double speed) {
        return addNote(timestamp, Attachment.EffectAttachment.EffectOptions.of(volume, speed));
    }

    /**
     * Adds a new MIDI note to this chart. Can be in any timestamp order.
     * If a note already existed at this position in the chart, it is overwritten.
     *
     * @param timestamp Timestamp of the note in seconds from 0
     * @param options Volume and Speed options (Volume and Pitch) of the note
     * @return Newly added MidiNote
     */
    public MidiNote addNote(double timestamp, Attachment.EffectAttachment.EffectOptions options) {
        MidiNote note = new MidiNote(chartParams, timestamp, options);
        addNoteDirect(note);
        return note;
    }

    /**
     * Adds all the notes defined in another chart to this chart.
     * The notes will be aligned according to this chart's parameters,
     * if they are different.
     *
     * @param chart Chart whose notes to add to this chart
     */
    public void addChartNotes(MidiChart chart) {
        // If parameters are identical we can skip the per-note withChartParameters stuff
        if (chart.chartParams.equals(this.chartParams)) {
            chart.notes.forEach(this::addNoteDirect);
        } else {
            chart.notes.forEach(this::addNote);
        }
    }

    /**
     * Removes all the notes defined in another chart from this chart.
     * The notes will be aligned according to this chart's parameters,
     * if they are different.
     *
     * @param chart Chart whose notes to remove from this chart
     */
    public void removeChartNotes(MidiChart chart) {
        // If parameters are identical we can skip the per-note withChartParameters stuff
        if (chart.chartParams.equals(this.chartParams)) {
            chart.notes.forEach(this::removeNote);
        } else {
            chart.notes.forEach(n -> removeNote(n.withChartParameters(chartParams)));
        }
    }

    /**
     * Adds a note to this chart, replacing any previous note at that
     * chart position.
     *
     * @param note MidiNote to add
     * @return Newly added note. Might be a different instance if the input
     *         note uses different chart parameters than this chart.
     */
    public MidiNote addNote(MidiNote note) {
        note = note.withChartParameters(this.chartParams);
        addNoteDirect(note);
        return note;
    }

    private void addNoteDirect(MidiNote note) {
        int index = Collections.binarySearch(notes, note);
        if (index >= 0) {
            notes.set(index, note); // Overwrite
        } else {
            notes.add(-index - 1, note); // Insert new
        }
    }

    @Override
    public MidiChart clone() {
        MidiChart copy = new MidiChart(chartParams);
        copy.notes.addAll(this.notes);
        return copy;
    }

    /**
     * Saves this chart to YAML
     *
     * @return YAML
     * @see #fromYaml(ConfigurationNode)
     */
    public ConfigurationNode toYaml() {
        ConfigurationNode yaml = new ConfigurationNode();
        getParameters().toYaml(yaml);
        if (!isEmpty()) {
            List<String> notesStr = yaml.getList("notes", String.class);
            for (MidiNote note : notes) {
                notesStr.add(note.toString());
            }
        }
        return yaml;
    }

    /**
     * Loads a MidiChart from previously saved YAML
     *
     * @param config Midi Chart configuration
     * @return MidiChart
     * @see #toYaml()
     */
    public static MidiChart fromYaml(ConfigurationNode config) {
        MidiChart chart = MidiChart.empty(MidiChartParameters.fromYaml(config));
        if (config.contains("notes")) {
            for (String noteStr : config.getList("notes", String.class)) {
                MidiNote note = MidiNote.fromString(chart.getParameters(), noteStr);
                if (note != null) {
                    chart.addNoteDirect(note);
                }
            }
        }
        return chart;
    }

    /**
     * Returns a new empty midi chart with default chart parameters
     *
     * @return Empty MIDI chart
     */
    public static MidiChart empty() {
        return empty(MidiChartParameters.DEFAULT);
    }

    /**
     * Returns a new empty midi chart with the chart parameters specified
     *
     * @return Empty MIDI chart
     */
    public static MidiChart empty(MidiChartParameters chartParams) {
        return new MidiChart(chartParams);
    }

    /**
     * Creates a MIDI chart of bergerkiller's test tune
     *
     * @return MidiChart with berger's tune
     */
    public static MidiChart bergersTune() {
        MidiChart chart = new MidiChart(MidiChartParameters.chromatic(MidiTimeSignature.COMMON, 150));
        chart.addNote(0.0, 1.0, 0.6);
        chart.addNote(0.1, 1.0, 0.8);
        chart.addNote(0.2, 1.0, 1.0);
        chart.addNote(0.3, 1.0, 1.2);
        chart.addNote(0.4, 1.0, 1.4);
        chart.addNote(0.5, 1.0, 1.6);
        chart.addNote(0.6, 1.0, 1.8);
        chart.addNote(0.7, 1.0, 2.0);

        chart.addNote(1.0, 1.0, 1.0);
        chart.addNote(1.2, 1.0, 1.2);
        chart.addNote(1.4, 1.0, 1.4);

        chart.addNote(1.6, 1.0, 1.4);
        chart.addNote(1.7, 1.0, 1.2);
        chart.addNote(1.8, 1.0, 1.0);
        chart.addNote(1.9, 1.0, 0.8);

        chart.addNote(2.0, 1.0, 0.9);
        chart.addNote(2.2, 1.0, 1.1);
        chart.addNote(2.4, 1.0, 0.5);
        chart.addNote(2.6, 1.0, 0.7);
        return chart;
    }

    /**
     * Stores the minimum and maximum bounds of the time step index and pitch classes used in the chart
     */
    public static class Bounds {
        public static final Bounds EMPTY = new Bounds(0, 0, 0, 0);
        private final int minTimeStepIndex;
        private final int maxTimeStepIndex;
        private final int minPitchClass;
        private final int maxPitchClass;

        public Bounds(int minTimeStepIndex, int maxTimeStepIndex, int minPitchClass, int maxPitchClass) {
            this.minTimeStepIndex = minTimeStepIndex;
            this.maxTimeStepIndex = maxTimeStepIndex;
            this.minPitchClass = minPitchClass;
            this.maxPitchClass = maxPitchClass;
        }

        public int minTimeStepIndex() {
            return minTimeStepIndex;
        }

        public int maxTimeStepIndex() {
            return maxTimeStepIndex;
        }

        public int getNumTimeSteps() {
            return maxTimeStepIndex - minTimeStepIndex + 1;
        }

        public int minPitchClass() {
            return minPitchClass;
        }

        public int maxPitchClass() {
            return maxPitchClass;
        }

        public int getNumPitchClasses() {
            return maxPitchClass - minPitchClass + 1;
        }

        /**
         * Gets whether the chart is/was empty. In that case there are no real bounds.
         *
         * @return True if empty
         */
        public boolean isEmpty() {
            return this == EMPTY;
        }
    }
}
