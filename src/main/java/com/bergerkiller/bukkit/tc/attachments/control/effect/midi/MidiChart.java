package com.bergerkiller.bukkit.tc.attachments.control.effect.midi;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

import java.util.ArrayList;
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
     * Removes a previously added note
     *
     * @param note MIDI Note to remove
     */
    public void removeNote(MidiNote note) {
        int index = Collections.binarySearch(notes, note);
        if (index >= 0) {
            MidiNote removed = notes.remove(index);
            if (removed != note) {
                notes.add(index, removed); // Weird!
            }
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
}
