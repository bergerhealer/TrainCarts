package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChart;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiNote;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiTimeSignature;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;

import java.util.HashSet;
import java.util.Set;

/**
 * A menu dialog that displays a {@link MidiChart} and includes controls to place notes,
 * remove notes, shift notes around and navigate by time or pitch scale.
 * When the chart is updated a callback is called.
 */
public abstract class MidiChartDialog extends MapWidgetMenu {
    private MidiChart chart = MidiChart.bergersTune(); //MidiChart.empty();
    private MidiChart selection = MidiChart.empty(chart.getParameters());

    public MidiChartDialog(MapWidgetAttachmentNode attachment) {
        this.setAttachment(attachment);
        this.setPositionAbsolute(true);
        this.setBounds(5, 5, 118, 118);
        this.setBackgroundColor(MapColorPalette.getColor(16, 16, 128));
    }

    public abstract void onChartChanged(MidiChart chart);

    public MidiChartDialog setChart(MidiChart chart) {
        this.chart = chart;
        this.selection = MidiChart.empty(chart.getParameters());
        return this;
    }

    @Override
    public void onAttached() {
        selection.addNoteOnBar(0, 0, 1.0);

        this.addWidget(new MidiPianoRollWidget().setBounds(5, 5, getWidth()-10, getHeight()-10));
        super.onAttached();
    }

    /**
     * Draws a piano to the left with horizontal lines going off to the right.
     * A single key can be set to be pressed down, changing appearance.
     */
    private class MidiPianoRollWidget extends MapWidget {
        private int startPitchClass = 0; // Base pitch (middle)
        private int startTimeStepIndex = 0;

        public MidiPianoRollWidget() {
            this.setFocusable(true);
        }

        public void setStartTimeStepIndex(int startTimeStepIndex) {
            if (this.startTimeStepIndex != startTimeStepIndex) {
                this.startTimeStepIndex = startTimeStepIndex;
                this.invalidate();
            }
        }

        public void setStartPitchClass(int startPitchClass) {
            if (this.startPitchClass != startPitchClass) {
                this.startPitchClass = startPitchClass;
                this.invalidate();
            }
        }

        public int getNumTimeSteps() {
            return (this.getWidth()-6) / 3;
        }

        public int getNumPitchClassesFromMiddle() {
            return getHeight() / 4;
        }

        public void scrollToSelection() {
            MidiChart.Bounds bounds = selection.getBounds();
            if (!bounds.isEmpty()) {
                int spacing, safeSpacing;

                // Time-wise
                safeSpacing = Math.min(2, (getNumTimeSteps() - bounds.getNumTimeSteps()) / 2);
                if (safeSpacing <= 0) {
                    // Align left at all times, we don't want any weird oscillations
                    setStartTimeStepIndex(bounds.minTimeStepIndex());
                } else if ((spacing = startTimeStepIndex - bounds.minTimeStepIndex() + safeSpacing) > 0) {
                    // Contents to the left need to be displayed
                    setStartTimeStepIndex(Math.max(0, startTimeStepIndex - spacing));
                } else if ((spacing = bounds.maxTimeStepIndex() - (startTimeStepIndex + getNumTimeSteps() - 1) + safeSpacing) > 0) {
                    // Contents to the right need to be displayed
                    setStartTimeStepIndex(startTimeStepIndex + spacing);
                }

                // Pitch-wise
                safeSpacing = 2;
                if (safeSpacing <= 0) {
                    // Align middle at all times, we don't want any weird oscillations
                    setStartPitchClass((bounds.minPitchClass() + bounds.maxPitchClass()) / 2);
                } else if ((spacing = startPitchClass - bounds.minPitchClass() - getNumPitchClassesFromMiddle() + 1 + safeSpacing) > 0) {
                    // Contents below need to be displayed
                    setStartPitchClass(startPitchClass - spacing);
                } else if ((spacing = bounds.maxPitchClass() - startPitchClass - getNumPitchClassesFromMiddle() + safeSpacing) > 0) {
                    // Contents above need to be displayed
                    setStartPitchClass(startPitchClass + spacing);
                }
            }
        }

        public void timeShiftSelection(int numTimeSteps) {
            if (numTimeSteps < 0 && selection.getBounds().minTimeStepIndex() <= 0) {
                return; // Reached left chart limit
            }
            selection.timeShift(numTimeSteps);
            scrollToSelection();
            invalidate();
        }

        public void pitchShiftSelection(int numPitchClasses) {
            selection.pitchShift(numPitchClasses);
            scrollToSelection();
            invalidate();
        }

        @Override
        public void onDraw() {
            int numPitchValues = getHeight() / 4;
            int baseY = getHeight() / 2;

            // Figure out what pitch classes are selected based on the selected notes
            Set<Integer> selectedPitchClasses = new HashSet<>();
            for (MidiNote selectedNote : selection.getNotes()) {
                selectedPitchClasses.add(selectedNote.pitchClass());
            }

            // Draw the background of the piano roll
            {
                final MidiTimeSignature signature = chart.getParameters().timeSignature();
                for (int i = -numPitchValues; i <= numPitchValues; i++) {
                    int pitch = i + startPitchClass;
                    PianoRendering.PianoKey key;
                    if (pitch == 0) {
                        key = PianoRendering.BLACK_KEY_BASE;
                    } else {
                        key = PianoRendering.PIANO_KEYS[Math.floorMod(pitch, 12)];
                    }

                    key.draw(view, baseY - i * 2, getWidth(), selectedPitchClasses.contains(pitch), timeStepIndex -> {
                        int adjTimeStepIndex = timeStepIndex + startTimeStepIndex;
                        if ((adjTimeStepIndex % signature.notesPerMeasure()) == 0) {
                            return TimeSeparator.MEASURE;
                        } else if ((adjTimeStepIndex % signature.noteValue()) == 0) {
                            return TimeSeparator.BEAT;
                        } else {
                            return TimeSeparator.NOTE;
                        }
                    });
                }
            }

            {
                int numTimeSteps = getNumTimeSteps();

                // Draw the selected notes
                for (MidiNote note : selection.getChartVisibleNotes(startTimeStepIndex, numTimeSteps)) {
                    int noteX = 7 + (note.timeStepIndex() - startTimeStepIndex) * 3;
                    int noteY = baseY - (note.pitchClass() - startPitchClass) * 2;
                    if (noteY >= -1 && noteY < getHeight()) {
                        if (chart.containsNote(note)) {
                            PianoRendering.NOTE_SELECTED.draw(view, noteX, noteY);
                        } else {
                            PianoRendering.NOTE_INACTIVE.draw(view, noteX, noteY);
                        }
                    }
                }

                // Draw the notes
                for (MidiNote note : chart.getChartVisibleNotes(startTimeStepIndex, numTimeSteps)) {
                    int noteX = 7 + (note.timeStepIndex() - startTimeStepIndex) * 3;
                    int noteY = baseY - (note.pitchClass() - startPitchClass) * 2;
                    if (noteY >= -1 && noteY < getHeight() && !selection.containsNote(note)) {
                        PianoRendering.NOTE_DEFAULT.draw(view, noteX, noteY);
                    }
                }
            }
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.LEFT) {
                timeShiftSelection(-1);
            } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
                timeShiftSelection(1);
            } else if (event.getKey() == MapPlayerInput.Key.UP) {
                pitchShiftSelection(1);
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                pitchShiftSelection(-1);
            } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
                if (chart.containsAllNotes(selection.getNotes())) {
                    chart.removeChartNotes(selection);
                } else {
                    chart.addChartNotes(selection);
                }
                invalidate();
                onChartChanged(chart);
            } else {
                super.onKeyPressed(event);
            }
        }
    }

    /**
     * Stores rendering logic for keyboard keys and notes. Keeps stuff tidy.
     */
    private static final class PianoRendering {
        /** The colors of the black keys on the keyboard (idle) */
        public static final PianoKeyColors COLORS_BLACK_KEY_IDLE = PianoKeyColors.builder()
                .key_top(27, 40, 54).key_btm(13, 13, 13).grid_bg(27, 40, 54)
                .grid_note(69, 75, 95).grid_beat(13, 13, 13).grid_measure(152, 108, 72).build();
        /** The colors of the black keys on the keyboard (pressed) */
        public static final PianoKeyColors COLORS_BLACK_KEY_PRESSED = PianoKeyColors.builder()
                .key_top(25, 57, 112).key_btm(36, 82, 159).grid_bg(25, 57, 112)
                .grid_note(78, 77, 160).grid_beat(15, 13, 48).grid_measure(119, 93, 96).build();
        /** The colors of the black key at pitch=1.0 which is the base of the keyboard (idle) */
        public static final PianoKeyColors COLORS_BLACK_BASE_KEY_IDLE = PianoKeyColors.builder()
                .key_top(64, 43, 53).key_btm(48, 32, 40).grid_bg(48, 32, 40)
                .grid_note(135, 84, 84).grid_beat(25, 25, 25).grid_measure(125, 53, 36).build();
        /** The colors of the black key at pitch=1.0 which is the base of the keyboard (pressed) */
        public static final PianoKeyColors COLORS_BLACK_BASE_KEY_PRESSED = PianoKeyColors.builder()
                .key_top(94, 40, 27).key_btm(135, 67, 39).grid_bg(94, 40, 27)
                .grid_note(138, 108, 112).grid_beat(48, 32, 40).grid_measure(186, 132, 88).build();
        /** The colors of the white keys that sit between the black keys (idle) */
        public static final PianoKeyColors COLORS_WHITE_KEY_IDLE = PianoKeyColors.builder()
                .key_top(220, 220, 220).key_btm(255, 255, 255).grid_bg(38, 63, 75)
                .grid_note(84, 92, 116).grid_beat(18, 21, 30).grid_measure(153, 127, 76).build();
        /** The colors of the white keys that sit between the black keys (pressed) */
        public static final PianoKeyColors COLORS_WHITE_KEY_PRESSED = PianoKeyColors.builder()
                .key_top(180, 180, 180).key_btm(112, 112, 112).grid_bg(25, 93, 131)
                .grid_note(44, 109, 186).grid_beat(32, 42, 100).grid_measure(150, 154, 64).build();

        /* Defines unique key drawing routines */

        /** The black key */
        public static final PianoKey BLACK_KEY = new BlackPianoKey(COLORS_BLACK_KEY_IDLE, COLORS_BLACK_KEY_PRESSED);
        /** The base black key at pitch = 1 */
        public static final PianoKey BLACK_KEY_BASE = new BlackPianoKey(COLORS_BLACK_BASE_KEY_IDLE, COLORS_BLACK_BASE_KEY_PRESSED);
        /** The white key surrounded by two black keys */
        public static final PianoKey WHITE_KEY = new PianoKey(COLORS_WHITE_KEY_IDLE, COLORS_WHITE_KEY_PRESSED) {
            @Override
            public void drawKey(MapCanvas view, int y, PianoKeyColors colors) {
                view.drawLine(4, y - 1, 5, y - 1, colors.KEY_TOP);
                view.fillRectangle(0, y, 6, 2, colors.KEY_BTM);
                view.drawLine(4, y + 2, 5, y + 2, colors.KEY_BTM);
            }
        };
        /** The white key with a black key above and a white key below */
        public static final PianoKey WHITE_KEY_HALF_TOP = new PianoKey(COLORS_WHITE_KEY_IDLE, COLORS_WHITE_KEY_PRESSED) {
            @Override
            public void drawKey(MapCanvas view, int y, PianoKeyColors colors) {
                view.drawLine(4, y - 1, 5, y - 1, colors.KEY_TOP);
                view.fillRectangle(0, y, 6, 2, colors.KEY_BTM);
            }
        };
        /** The white key with a white key above and a black key below */
        public static final PianoKey WHITE_KEY_HALF_BTM = new PianoKey(COLORS_WHITE_KEY_IDLE, COLORS_WHITE_KEY_PRESSED) {
            @Override
            public void drawKey(MapCanvas view, int y, PianoKeyColors colors) {
                view.drawLine(0, y, 5, y, colors.KEY_TOP);
                view.drawLine(0, y + 1, 5, y + 1, colors.KEY_BTM);
                view.drawLine(4, y + 2, 5, y + 2, colors.KEY_BTM);
            }
        };

        /** Defines the 12 piano keys in order of appearance relative to the base key */
        public static final PianoRendering.PianoKey[] PIANO_KEYS = new PianoRendering.PianoKey[] {
                BLACK_KEY,
                WHITE_KEY,
                BLACK_KEY,
                WHITE_KEY,
                BLACK_KEY,
                WHITE_KEY_HALF_BTM,
                WHITE_KEY_HALF_TOP,
                BLACK_KEY,
                WHITE_KEY,
                BLACK_KEY,
                WHITE_KEY_HALF_BTM,
                WHITE_KEY_HALF_TOP
        };

        /** Defines the colors for drawing a non-selected note */
        public static final NoteColors NOTE_DEFAULT = NoteColors.builder()
                .top(255, 64, 64).btm(220, 55, 55).build();
        /** Defines the colors for drawing a note that has been selected (cursor intersection) */
        public static final NoteColors NOTE_SELECTED = NoteColors.builder()
                .top(213, 219, 92).btm(183, 188, 79).build();
        /** Defines the colors for drawing the cursor, which is a note that is not active / not played */
        public static final NoteColors NOTE_INACTIVE = NoteColors.builder()
                .top(211, 217, 220).btm(199, 199, 199).build();

        private static class BlackPianoKey extends PianoKey {
            public BlackPianoKey(PianoKeyColors colors_idle, PianoKeyColors colors_pressed) {
                super(colors_idle, colors_pressed);
            }

            @Override
            public void drawKey(MapCanvas view, int y, PianoKeyColors colors) {
                view.drawLine(0, y, 3, y, colors.KEY_TOP);
                view.drawLine(0, y + 1, 3, y + 1, colors.KEY_BTM);
            }
        }

        private static abstract class PianoKey {
            private final PianoKeyColors colors_idle, colors_pressed;

            public PianoKey(PianoKeyColors colors_idle, PianoKeyColors colors_pressed) {
                this.colors_idle = colors_idle;
                this.colors_pressed = colors_pressed;
            }

            public abstract void drawKey(MapCanvas view, int y, PianoKeyColors colors);

            public final void draw(MapCanvas view, int y, int w, boolean pressed, FindTimeSeparatorFunc timeSepFunc) {
                // Draw the piano key to the left
                PianoKeyColors colors = pressed ? colors_pressed : colors_idle;
                drawKey(view, y, colors);

                // Draw the horizontal line from left to right
                // Use a highlight color at the separator locations

                // First draw a full line with the background color, is easier
                {
                    byte bgColor = colors.getGridColor(TimeSeparator.BACKGROUND);
                    view.drawLine(6, y, w - 1, y, bgColor);
                    view.drawLine(6, y + 1, w - 1, y + 1, bgColor);
                }

                // Go by all locations where a vertical separator line is and draw it in
                int timeStepIndex = 0;
                for (int x = 6; x < w; x += 3) {
                    byte bgColor = colors.getGridColor(timeSepFunc.find(timeStepIndex));
                    view.writePixel(x, y, bgColor);
                    view.writePixel(x, y + 1, bgColor);
                    timeStepIndex++;
                }
            }
        }

        /**
         * The colors used for drawing a particular piano key effect
         */
        private static class PianoKeyColors {
            public final byte KEY_TOP;
            public final byte KEY_BTM;
            public final byte[] GRID_COLORS;

            private PianoKeyColors(Builder builder) {
                KEY_TOP = builder.KEY_TOP;
                KEY_BTM = builder.KEY_BTM;
                GRID_COLORS = new byte[] {
                        builder.GRID_BG, builder.GRID_NOTE,
                        builder.GRID_BEAT, builder.GRID_MEASURE
                };
            }

            public byte getGridColor(TimeSeparator sep) {
                return GRID_COLORS[sep.ordinal()];
            }

            public static Builder builder() {
                return new Builder();
            }

            public static class Builder {
                public byte KEY_TOP;
                public byte KEY_BTM;
                public byte GRID_BG;
                public byte GRID_NOTE;
                public byte GRID_BEAT;
                public byte GRID_MEASURE;

                public Builder key_top(int r, int g, int b) {
                    KEY_TOP = MapColorPalette.getColor(r, g, b);
                    return this;
                }

                public Builder key_btm(int r, int g, int b) {
                    KEY_BTM = MapColorPalette.getColor(r, g, b);
                    return this;
                }

                public Builder grid_bg(int r, int g, int b) {
                    GRID_BG = MapColorPalette.getColor(r, g, b);
                    return this;
                }

                public Builder grid_note(int r, int g, int b) {
                    GRID_NOTE = MapColorPalette.getColor(r, g, b);
                    return this;
                }

                public Builder grid_beat(int r, int g, int b) {
                    GRID_BEAT = MapColorPalette.getColor(r, g, b);
                    return this;
                }

                public Builder grid_measure(int r, int g, int b) {
                    GRID_MEASURE = MapColorPalette.getColor(r, g, b);
                    return this;
                }

                public PianoKeyColors build() {
                    return new PianoKeyColors(this);
                }
            }
        }
    }

    /**
     * The colors for drawing a single note
     */
    public static class NoteColors {
        public final byte TOP, BTM;

        private NoteColors(Builder builder) {
            TOP = builder.TOP;
            BTM = builder.BTM;
        }

        public void draw(MapCanvas view, int x, int y) {
            view.writePixel(x, y, TOP);
            view.writePixel(x + 1, y, TOP);
            view.writePixel(x, y + 1, BTM);
            view.writePixel(x + 1, y + 1, BTM);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            public byte TOP;
            public byte BTM;

            public Builder top(int r, int g, int b) {
                TOP = MapColorPalette.getColor(r, g, b);
                return this;
            }

            public Builder btm(int r, int g, int b) {
                BTM = MapColorPalette.getColor(r, g, b);
                return this;
            }

            public NoteColors build() {
                return new NoteColors(this);
            }
        }
    }

    private enum TimeSeparator {
        BACKGROUND, NOTE, BEAT, MEASURE
    }

    @FunctionalInterface
    private interface FindTimeSeparatorFunc {
        TimeSeparator find(int timeStepIndex);
    }
}
