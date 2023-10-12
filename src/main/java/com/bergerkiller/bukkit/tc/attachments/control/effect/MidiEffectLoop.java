package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Plays a sequence of speed (pitch) and intensity (volume) values over time,
 * based on a certain BPM and play duration. Can optionally be set to loop
 * infinitely.
 */
public class MidiEffectLoop extends SequenceEffectLoop {
    private AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> effects = AttachmentNameLookup.NameGroup.none();
    private NoteSequence notes = new NoteSequence(10.0, 1.0);

    public AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> getEffects() {
        return effects;
    }

    public void setEffects(AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> effects) {
        this.effects = effects;
    }

    public NoteSequence getNotes() {
        return notes;
    }

    public void setNotes(NoteSequence notes) {
        this.notes = notes;
    }

    @Override
    public long durationNanos() {
        return notes.totalDurationNanos;
    }

    @Override
    public boolean advance(long prevNanos, long currNanos) {
        notes.forNotesInRange(prevNanos, currNanos, n -> n.play(effects));
        return true;
    }

    /**
     * A single note in a {@link NoteSequence}
     */
    public static final class Note implements Comparable<Note> {
        /** Timestamp in seconds since beginning of the sequence of this note */
        public final double timestamp;
        /** Timestamp in nanoseconds in units of step duration */
        private final long timestampNanos;
        /** Number of sequence time step durations that have elapsed that this note is on */
        public final int timeStepNumber;
        /** Options of this note, such as intensity (volume) and speed (pitch) */
        public final Attachment.EffectAttachment.EffectOptions options; // Pitch and Volume

        private Note(double timestamp, long stepDurationNanos, Attachment.EffectAttachment.EffectOptions options) {
            long n = toNanos(timestamp) / stepDurationNanos;

            this.timestamp = timestamp;
            this.timestampNanos = n * stepDurationNanos;
            this.timeStepNumber = (int) n;
            this.options = options;
        }

        /**
         * Plays this note
         *
         * @param effects Effects (instruments) to activate with this note
         */
        public void play(AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> effects) {
            effects.forEach(e -> e.playEffect(options));
        }

        @Override
        public int compareTo(@NotNull MidiEffectLoop.Note note) {
            if (this.timestampNanos == note.timestampNanos) {
                if (this == note) {
                    return 0;
                } else if (options.speed() != note.options.speed()) {
                    return Double.compare(this.options.speed(), note.options.speed());
                } else {
                    // Preserve some sort of arbitrary order. It's important for note
                    // removal that this works correctly according to identity equals()
                    return System.identityHashCode(this) - System.identityHashCode(note);
                }
            } else {
                return Long.compareUnsigned(this.timestampNanos, note.timestampNanos);
            }
        }
    }

    /**
     * Sequence of notes, sorted by time, aligned on a step duration.
     * Represents the 'piano roll' chart, and offers methods to load/save
     * it to YAML.
     */
    public static final class NoteSequence {
        private final double totalDuration;
        private final long totalDurationNanos;
        private final double stepDuration;
        private final long stepDurationNanos;
        private final List<Note> notes = new ArrayList<>();
        private int lastIndex = 0;

        public NoteSequence(double totalDuration, double stepDuration) {
            this.totalDuration = totalDuration;
            this.totalDurationNanos = toNanos(totalDuration);
            this.stepDuration = stepDuration;
            this.stepDurationNanos = toNanos(stepDuration);
        }

        /**
         * Total duration of this note sequence in seconds
         *
         * @return Total duration in seconds
         */
        public double totalDuration() {
            return totalDuration;
        }

        /**
         * Duration of a single time step in seconds
         *
         * @return Step duration in seconds
         */
        public double stepDuration() {
            return stepDuration;
        }

        /**
         * Performs an operation for all the notes that lie within a range of two nanos timestamps.
         * Is optimized for sequential playback.
         *
         * @param prevNanos Previous nanos timestamp
         * @param currNanos Current nanos timestamp
         * @param action Action to perform on the notes within
         */
        public void forNotesInRange(long prevNanos, long currNanos, Consumer<Note> action) {
            int currIndex = this.lastIndex;
            List<Note> notes = this.notes;
            int notesCount = notes.size();

            // Reset search to beginning if we're skipping over stuff
            if (currIndex >= notesCount || notes.get(currIndex).timestampNanos > prevNanos) {
                currIndex = 0;
            }

            // Advance currIndex until it is beyond currNanos
            // Call callback if it is after or equal to prevNanos
            Note n;
            while (currIndex < notesCount && (n = notes.get(currIndex)).timestampNanos < currNanos) {
                if (n.timestampNanos >= prevNanos) {
                    action.accept(n);
                }
                currIndex++;
            }

            // Remember for next time
            this.lastIndex = currIndex;
        }

        /**
         * Gets a List of all the notes that exist in this sequence, sorted by time and then by speed (pitch)
         *
         * @return notes
         */
        public List<Note> getNotes() {
            return notes;
        }

        /**
         * Changes the speed (pitch) of a note
         *
         * @param n Note to change speed of
         * @param newSpeed New speed to set
         * @return New updated note
         */
        public Note changeSpeed(Note n, double newSpeed) {
            removeNote(n);
            return addNote(n.timestamp, n.options.withSpeed(newSpeed));
        }

        /**
         * Changes the timestamp of a note
         *
         * @param n Note to change timestamp of
         * @param timestamp New timestamp
         * @return New updated note
         */
        public Note changeTimestamp(Note n, double timestamp) {
            removeNote(n);
            return addNote(timestamp, n.options);
        }

        /**
         * Adds a note to this sequence. Can be in any order.
         *
         * @param timestamp Timestamp of the note in seconds from 0
         * @param intensity Intensity (or volume)
         * @param speed Speed (or pitch)
         * @return new Note
         */
        public Note addNote(double timestamp, double intensity, double speed) {
            return addNote(timestamp, Attachment.EffectAttachment.EffectOptions.of(intensity, speed));
        }

        /**
         * Adds a note to this sequence. Can be in any order.
         *
         * @param timestamp Timestamp of the note in seconds from 0
         * @param options Intensity and Speed options (Volume and Pitch) of the note
         * @return new Note
         */
        public Note addNote(double timestamp, Attachment.EffectAttachment.EffectOptions options) {
            Note n = new Note(timestamp, this.stepDurationNanos, options);
            {
                int index = Collections.binarySearch(notes, n);
                if (index < 0) {
                    index = -index - 1;
                }
                notes.add(index, n);
            }
            return n;
        }

        /**
         * Removes a previously added note
         *
         * @param n Note to remove
         */
        public void removeNote(Note n) {
            int index = Collections.binarySearch(notes, n);
            if (index >= 0) {
                notes.remove(index);
            }
        }
    }
}
