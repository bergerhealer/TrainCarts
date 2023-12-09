package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChart;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChartParameters;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiNote;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiTimeSignature;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetTooltip;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A menu dialog that displays a {@link MidiChart} and includes controls to place notes,
 * remove notes, shift notes around and navigate by time or pitch scale.
 * When the chart is updated a callback is called.
 */
public abstract class MidiChartDialog extends MapWidgetMenu {
    private static final MapTexture MIDI_BUTTON_ICONS = MapTexture.loadPluginResource(TrainCarts.plugin,
            "com/bergerkiller/bukkit/tc/textures/attachments/midi_buttons.png");
    private MidiChart chart = MidiChart.empty();
    private MidiChart selection = MidiChart.empty(chart.getParameters());
    private MidiChart pattern = MidiChart.empty(chart.getParameters());
    private EffectLoop.RunMode midiRunMode = EffectLoop.RunMode.ASYNCHRONOUS;
    private Mode mode = Mode.NOTE;

    private TopMenuButton btnModeNote, btnModeSelect, btnModePattern;
    private TopMenuButton prevSelectedButton = null;
    private MidiPianoRollWidget pianoRoll;
    private EffectLoop.Time duration = null; // If non-null, has an end time

    private volatile int previewCtr = 0;
    private volatile EffectLoop.Time currentPreviewTime = null; // Null if not playing

    public MidiChartDialog() {
        this.setPositionAbsolute(true);
        this.setBounds(5, 5, 118, 116);
        this.setBackgroundColor(MapColorPalette.getColor(16, 16, 128));
    }

    /**
     * Called when changes are made to the chart
     *
     * @param chart New updated chart
     */
    public abstract void onChartChanged(MidiChart chart);

    /**
     * Gets the Effect Sink used to preview the effects played by this MIDI chart
     *
     * @return Preview Effect Sink
     */
    public abstract Attachment.EffectSink getEffectSink();

    public MidiChartDialog setChart(MidiChart chart) {
        this.chart = chart;
        this.selection = this.selection.withChartParameters(chart.getParameters());
        this.pattern = this.pattern.withChartParameters(chart.getParameters());
        this.stopPreview();
        if (this.pianoRoll != null) {
            this.pianoRoll.invalidate();
        }
        return this;
    }

    public MidiChartDialog setDuration(EffectLoop.Time duration) {
        this.duration = duration;
        return this;
    }

    /**
     * Sets the run mode of effect loops previewed in this dialog
     *
     * @param runMode Run Mode
     * @return this
     */
    public MidiChartDialog setMidiRunMode(EffectLoop.RunMode runMode) {
        this.midiRunMode = runMode;
        return this;
    }

    public void setMode(Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            applyMode();
        }
    }

    private void applyMode() {
        if (getDisplay() != null) {
            btnModeNote.setSelected(mode == Mode.NOTE);
            btnModeSelect.setSelected(mode == Mode.SELECT);
            btnModePattern.setSelected(mode == Mode.PATTERN);
            pianoRoll.invalidate();
            mode.select(this);
        }
    }

    private void setNoteSelect() {
        if (selection.isEmpty()) {
            selection.addNoteOnBar(0, 0, 1.0);
        } else {
            while (selection.getNotes().size() > 1) {
                selection.removeNote(selection.getNotes().get(selection.getNotes().size() - 1));
            }
        }
        pianoRoll.scrollToSelection();
    }

    private void setPatternSelect() {
        if (pattern.isEmpty()) {
            setNoteSelect();
        } else {
            selection.clearNotes();
            selection.addChartNotes(pattern);
            pianoRoll.scrollToSelection();
        }
    }

    private void exitPianoRoll() {
        if (prevSelectedButton == null) {
            prevSelectedButton = btnModeNote;
        }
        prevSelectedButton.focus();
    }

    private void stopPreview() {
        previewCtr++;
    }

    private void preview(MidiChart chart, boolean shiftToStart, boolean ignoreDuration) {
        stopPreview(); // Also increments preview ctr

        if (chart.isEmpty()) {
            return;
        }

        // Shift playback to the beginning
        chart = chart.clone();
        final EffectLoop.Time shifted = shiftToStart ? chart.timeShiftToStart() : EffectLoop.Time.ZERO;
        final MidiScheduledEffectLoop midiEffectLoop = new MidiScheduledEffectLoop();
        final int previewId = previewCtr;
        midiEffectLoop.setChart(chart);
        midiEffectLoop.setEffectSink(getEffectSink());

        final ScheduledEffectLoop.SequentialEffectLoop effectLoop = midiEffectLoop.asEffectLoop();
        TrainCarts.plugin.createEffectLoopPlayer().play(effectLoop.withAdvance((base, dt, duration, loop) -> {
            // If a duration limit was set, use it
            if (MidiChartDialog.this.duration != null && !ignoreDuration) {
                duration = MidiChartDialog.this.duration;
            }

            // Abort older running loops
            if (previewId != previewCtr || !base.advance(dt, duration, loop)) {
                currentPreviewTime = null; // Not playing
                return false;
            }

            // Refresh play position
            currentPreviewTime = EffectLoop.Time.nanos(shifted.nanos + effectLoop.nanosElapsed());
            return true;
        }), midiRunMode);
    }

    @Override
    public void onAttached() {
        btnModeNote = addWidget(new TopMenuButton(MIDI_BUTTON_ICONS.getView(0, 0, 12, 12).clone()) {
            @Override
            public void onClick() {
                setMode(Mode.NOTE);
                pianoRoll.activate();
            }
        }).setTooltip("Place notes");
        btnModeNote.setPosition(5, 5);

        btnModeSelect = addWidget(new TopMenuButton(MIDI_BUTTON_ICONS.getView(12, 0, 12, 12).clone()) {
            @Override
            public void onClick() {
                setMode(Mode.SELECT);
                pianoRoll.activate();
            }
        }).setTooltip("Select note pattern");
        btnModeSelect.setPosition(18, 5);

        btnModePattern = addWidget(new TopMenuButton(MIDI_BUTTON_ICONS.getView(24, 0, 12, 12).clone()) {
            @Override
            public void onClick() {
                setMode(Mode.PATTERN);
                pianoRoll.activate();
            }
        }).setTooltip("Place note pattern");
        btnModePattern.setPosition(31, 5);

        addWidget(new TopMenuButton(MIDI_BUTTON_ICONS.getView(36, 0, 12, 12).clone()) {
            @Override
            public void onClick() {
                MidiChartDialog.this.addWidget(new ConfirmClearDialog() {
                    @Override
                    public void onConfirmClear() {
                        chart.clearNotes();
                        selection.clearNotes();
                        pattern.clearNotes();
                        pianoRoll.invalidate();
                        mode.select(MidiChartDialog.this);
                        stopPreview();
                        onChartChanged(chart);
                    }
                });
            }
        }).setTooltip("Clear chart")
          .setPosition(46, 5);

        addWidget(new TopMenuButton(MIDI_BUTTON_ICONS.getView(48, 0, 12, 12).clone()) {
            private boolean playing = false;

            private void setPlaying(boolean newPlaying) {
                if (playing != newPlaying) {
                    playing = newPlaying;
                    if (newPlaying) {
                        setIcon(MIDI_BUTTON_ICONS.getView(60, 0, 12, 12).clone());
                        setTooltip("Stop playing chart");
                    } else {
                        setIcon(MIDI_BUTTON_ICONS.getView(48, 0, 12, 12).clone());
                        setTooltip("Play chart");
                    }
                }
            }

            @Override
            public void onClick() {
                if (playing) {
                    stopPreview();
                    setPlaying(false);
                } else {
                    preview(chart, false, false);
                    setPlaying(true);
                }
            }

            @Override
            public void onTick() {
                super.onTick();
                setPlaying(currentPreviewTime != null);
            }
        }).setTooltip("Play chart")
          .setPosition(getWidth() - 30, 5);

        addWidget(new TopMenuButton(MIDI_BUTTON_ICONS.getView(72, 0, 12, 12).clone()) {
            @Override
            public void onClick() {
                MidiChartDialog.this.addWidget(new ChartSettingsDialog() {
                    @Override
                    public void onParamsChanged(MidiChartParameters params) {
                        setChart(chart.withChartParameters(params));
                        onChartChanged(chart);
                    }
                }).setParams(chart.getParameters());
            }
        }).setTooltip("Chart settings")
          .setPosition(getWidth() - 17, 5);

        pianoRoll = this.addWidget(new MidiPianoRollWidget());
        pianoRoll.setBounds(5, 19, getWidth()-10, getHeight()-24);

        applyMode();
        super.onAttached();
    }

    @Override
    public void onDetached() {
        stopPreview();
        super.onDetached();
    }

    /**
     * Draws a piano to the left with horizontal lines going off to the right.
     * A single key can be set to be pressed down, changing appearance.
     */
    private class MidiPianoRollWidget extends MapWidget {
        private int startPitchClass = 0; // Base pitch (middle)
        private int startTimeStepIndex = 0;
        private int playVerticalLineX = -1;

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
            if (mode == Mode.PATTERN) {
                pattern.timeShift(numTimeSteps);
            }
            scrollToSelection();
            invalidate();
        }

        public void pitchShiftSelection(int numPitchClasses) {
            selection.pitchShift(numPitchClasses);
            if (mode == Mode.PATTERN) {
                pattern.pitchShift(numPitchClasses);
            }
            scrollToSelection();
            invalidate();
        }

        @Override
        public void onTick() {
            // Update the position of a vertical play line
            {
                int newVerticalLineX = calcChartXFromTime(currentPreviewTime);
                if (newVerticalLineX != playVerticalLineX) {
                    playVerticalLineX = newVerticalLineX;
                    this.invalidate();
                }
            }
        }

        private int calcChartXFromTime(EffectLoop.Time time) {
            if (time == null) {
                return -1;
            } else {
                long elapsed = time.nanos - chart.getParameters().getTimestampNanos(this.startTimeStepIndex);
                if (elapsed < 0) {
                    return -1;
                } else {
                    int xPos = 7 + (int) ((3 * elapsed) / chart.getParameters().timeStep().nanos);
                    if (xPos >= getWidth()) {
                        return -1;
                    }
                    return xPos;
                }
            }
        }

        @Override
        public void onDraw() {
            int numPitchValues = (getHeight() / 4) + 1;
            int baseY = getHeight() / 2;
            final boolean active = isActivated();

            // Draw the background of the piano roll
            {
                // Figure out what pitch classes are selected based on the selected notes
                Set<Integer> selectedPitchClasses = new HashSet<>();
                if (active) {
                    for (MidiNote selectedNote : selection.getNotes()) {
                        selectedPitchClasses.add(selectedNote.pitchClass());
                    }
                }

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

            // Draw the notes on top
            {
                // Draw the selected notes
                if (active) {
                    drawAllNotes(selection, note -> {
                        if (chart.containsNote(note)) {
                            return PianoRendering.NOTE_NONE;
                        }

                        if (mode == Mode.SELECT && pattern.containsNote(note)) {
                            return PianoRendering.NOTE_PATTERN_SELECTED;
                        } else {
                            return PianoRendering.NOTE_INACTIVE;
                        }
                    });
                }

                // Draw the current pattern
                if (mode == Mode.SELECT || mode == Mode.PATTERN) {
                    drawAllNotes(pattern, note -> {
                        if (chart.containsNote(note)) {
                            return PianoRendering.NOTE_NONE;
                        }

                        if (active && selection.containsNote(note)) {
                            return PianoRendering.NOTE_NONE;
                        } else {
                            return PianoRendering.NOTE_PATTERN_DEFAULT;
                        }
                    });
                }

                // Draw the notes of the chart
                drawAllNotes(chart, note -> {
                    if (active) {
                        // In pattern selection mode we draw these things differently
                        if (mode == Mode.SELECT) {
                            if (pattern.containsNote(note)) {
                                if (selection.containsNote(note)) {
                                    return PianoRendering.NOTE_PATTERN_SELECTED;
                                } else {
                                    return PianoRendering.NOTE_PATTERN_OVERLAP;
                                }
                            }
                        }

                        // In the other modes, only selection matters
                        if (selection.containsNote(note)) {
                            return PianoRendering.NOTE_SELECTED;
                        }
                    }
                    return PianoRendering.NOTE_DEFAULT;
                });
            }

            // Draw a vertical line for the current play position
            if (playVerticalLineX >= 0) {
                view.drawLine(playVerticalLineX, 0, playVerticalLineX, getHeight() - 1,
                        MapColorPalette.COLOR_RED);
            }

            // Draw a vertical line for the duration end point
            if (duration != null) {
                int durationVerticalLineX = calcChartXFromTime(duration);
                if (durationVerticalLineX >= 0) {
                    view.drawLine(durationVerticalLineX, 0, durationVerticalLineX, getHeight() - 1,
                            MapColorPalette.COLOR_WHITE);
                }
            }
        }

        private void drawAllNotes(MidiChart chart, Function<MidiNote, MidiChartDialog.NoteColors> colorsFunc) {
            int numTimeSteps = getNumTimeSteps();
            for (MidiNote note : chart.getChartVisibleNotes(startTimeStepIndex, numTimeSteps)) {
                drawNote(note, colorsFunc);
            }
        }

        private void drawNote(MidiNote note, Function<MidiNote, MidiChartDialog.NoteColors> colorsFunc) {
            int baseY = getHeight() / 2;
            int noteX = 7 + (note.timeStepIndex() - startTimeStepIndex) * 3;
            int noteY = baseY - (note.pitchClass() - startPitchClass) * 2;
            if (noteY >= -1 && noteY < getHeight()) {
                MidiChartDialog.NoteColors colors = colorsFunc.apply(note);
                if (colors != PianoRendering.NOTE_NONE) {
                    colors.draw(view, noteX, noteY);
                }
            }
        }

        @Override
        public void onFocus() {
            this.activate(); // Makes things easier. Avoids back button exiting dialog.
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
                mode.activate(MidiChartDialog.this);
                invalidate();
                onChartChanged(chart);
            } else if (event.getKey() == MapPlayerInput.Key.BACK) {
                exitPianoRoll();
            }
        }
    }

    /**
     * Operating mode of the chart dialog
     */
    public enum Mode {
        /** Places or removes individual notes freely */
        NOTE(dialog -> { dialog.setNoteSelect(); dialog.pattern.clearNotes(); },
             dialog -> {
                 if (dialog.chart.toggleChartNotes(dialog.selection)) {
                     dialog.preview(dialog.selection, true, true);
                 }
             }),
        /** Selects one or more notes (ghosts) for use as a pattern */
        SELECT(MidiChartDialog::setNoteSelect,
               dialog -> {
                   if (dialog.pattern.toggleChartNotes(dialog.selection)) {
                       dialog.preview(dialog.pattern, true, true);
                   } else {
                       dialog.stopPreview();
                   }
               }),
        /** Uses selection as a pattern for placing or removing multiple notes */
        PATTERN(MidiChartDialog::setPatternSelect,
                dialog -> {
                    if (dialog.chart.toggleChartNotes(dialog.selection)) {
                        dialog.preview(dialog.selection, true, true);
                    } else {
                        dialog.stopPreview();
                    }
                });

        private final Consumer<MidiChartDialog> selectAction;
        private final Consumer<MidiChartDialog> activateAction;

        Mode(Consumer<MidiChartDialog> selectAction, Consumer<MidiChartDialog> activateAction) {
            this.selectAction = selectAction;
            this.activateAction = activateAction;
        }

        public void select(MidiChartDialog dialog) {
            selectAction.accept(dialog);
        }

        public void activate(MidiChartDialog dialog) {
            activateAction.accept(dialog);
        }
    }

    private static abstract class ChartSettingsDialog extends MapWidgetMenu {
        private MidiChartParameters params = MidiChartParameters.DEFAULT;

        public ChartSettingsDialog() {
            this.setBounds(10, 22, 98, 88);
            this.setBackgroundColor(MapColorPalette.getColor(138, 152, 180));
        }

        public abstract void onParamsChanged(MidiChartParameters params);

        public MidiChartParameters getParams() {
            return params;
        }

        public ChartSettingsDialog setParams(MidiChartParameters params) {
            this.params = params;
            this.invalidate();
            return this;
        }

        @Override
        public void onAttached() {
            super.onAttached();

            final int num_x_offset = 40;
            int y_pos = 12;

            // Time signature
            {
                MapWidgetText label = new MapWidgetText();
                label.setFont(MapFont.TINY);
                label.setText("- Time Signature -");
                label.setPosition(15, 5);
                label.setColor(MapColorPalette.getColor(115, 108, 18));
                this.addWidget(label);
            }

            // Beats per measure
            addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    setRange(1, 16);
                    setIncrement(1);
                    setInitialValue(params.timeSignature().beatsPerMeasure());
                    super.onAttached();
                }

                @Override
                public void onResetValue() {
                    setValue(4);
                }

                @Override
                public void onValueChangeEnd() {
                    params = params.withTimeSignature(MidiTimeSignature.of(
                            (int) getValue(), params.timeSignature().noteValue()));
                    onParamsChanged(params);
                }
            }.setBounds(num_x_offset, y_pos, getWidth() - num_x_offset, 13));
            addLabel(5, y_pos + 1, "Beats per");
            addLabel(5, y_pos + 7, "measure");
            y_pos += 16;

            // Note Value
            addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    setRange(1, 16);
                    setIncrement(1);
                    setTextPrefix("1/");
                    setInitialValue(params.timeSignature().noteValue());
                    super.onAttached();
                }

                @Override
                public void onResetValue() {
                    setValue(4);
                }

                @Override
                public void onValueChangeEnd() {
                    params = params.withTimeSignature(MidiTimeSignature.of(
                            params.timeSignature().beatsPerMeasure(), (int) getValue()));
                    onParamsChanged(params);
                }
            }).setBounds(num_x_offset, y_pos, getWidth() - num_x_offset, 13);
            addLabel(5, y_pos + 4, "Note value");
            y_pos += 20;

            // BPM
            addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    setRange(1, 10000);
                    setIncrement(1);
                    setInitialValue(params.bpm());
                    super.onAttached();
                }

                @Override
                public void onResetValue() {
                    setValue(120);
                }

                @Override
                public void onValueChangeEnd() {
                    params = params.withBPM((int) getValue());
                    onParamsChanged(params);
                }
            }).setBounds(num_x_offset, y_pos, getWidth() - num_x_offset, 13);
            addLabel(5, y_pos + 1, "Beats per");
            addLabel(5, y_pos + 7, "minute");
            y_pos += 17;

            // Pitch classes
            addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    setRange(1, 192);
                    setIncrement(1);
                    setInitialValue(params.pitchClasses());
                    super.onAttached();
                }

                @Override
                public void onResetValue() {
                    setValue(12);
                }

                @Override
                public void onValueChangeEnd() {
                    params = params.withPitchClasses((int) getValue());
                    onParamsChanged(params);
                }
            }).setBounds(num_x_offset, y_pos, getWidth() - num_x_offset, 13);
            addLabel(5, y_pos + 1, "Pitch");
            addLabel(5, y_pos + 7, "classes");
        }
    }

    private static abstract class ConfirmClearDialog extends MapWidgetMenu {

        public ConfirmClearDialog() {
            this.setBounds(10, 22, 98, 58);
            this.setBackgroundColor(MapColorPalette.getColor(135, 33, 33));
        }

        /**
         * Called when the player specifically said 'yes' to clearing
         */
        public abstract void onConfirmClear();

        @Override
        public void onAttached() {
            super.onAttached();

            // Label
            this.addWidget(new MapWidgetText()
                    .setText("Are you sure you\nwant to clear\nthis chart?")
                    .setBounds(5, 5, 80, 30));

            // Cancel
            this.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    ConfirmClearDialog.this.close();
                }
            }.setText("No").setBounds(10, 40, 36, 13));

            // Yes!
            this.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    ConfirmClearDialog.this.close();
                    ConfirmClearDialog.this.onConfirmClear();
                }
            }.setText("Yes").setBounds(52, 40, 36, 13));
        }
    }

    /**
     * One of the top menu buttons. Can optionally be set to be selected by default,
     * as a select button.
     */
    private abstract class TopMenuButton extends MapWidget {
        private MapTexture icon;
        private boolean selected = false;
        private boolean buttonDown = false;
        private final MapWidgetTooltip tooltip = new MapWidgetTooltip();

        public TopMenuButton(MapTexture icon) {
            this.icon = icon;
            this.setSize(icon.getWidth(), icon.getHeight());
            this.setFocusable(true);
        }

        public abstract void onClick();

        public TopMenuButton setTooltip(String text) {
            this.tooltip.setText(text);
            return this;
        }

        public TopMenuButton setSelected(boolean selected) {
            if (this.selected != selected) {
                this.selected = selected;
                this.invalidate();
            }
            return this;
        }

        public TopMenuButton setIcon(MapTexture icon) {
            this.icon = icon;
            this.invalidate();
            return this;
        }

        @Override
        public void onFocus() {
            addWidget(tooltip);
            prevSelectedButton = this; // So that we come back to it from the piano roll
        }

        @Override
        public void onBlur() {
            removeWidget(tooltip);
            buttonDown = false;
        }

        @Override
        public void onDraw() {
            byte edgeColor, topRim, background, bottomRim;

            if (isFocused()) {
                // Lighter purple color
                edgeColor = MapColorPalette.COLOR_BLACK;
                topRim = MapColorPalette.getColor(216, 76, 178);
                background = MapColorPalette.getColor(186, 65, 153);
                bottomRim = MapColorPalette.getColor(152, 53, 125);
            } else {
                // Darker purple color
                edgeColor = MapColorPalette.COLOR_BLACK;
                topRim = MapColorPalette.getColor(142, 109, 208);
                background = MapColorPalette.getColor(116, 89, 170);
                bottomRim = MapColorPalette.getColor(97, 63, 148);
            }

            // If pressed down, invert top/bottom rim to make an indent effect
            if (selected || buttonDown) {
                byte b = topRim;
                topRim = bottomRim;
                bottomRim = b;
            }

            // Draw background using lines
            view.fillRectangle(2, 2, getWidth()-4, getHeight()-4, background);
            view.drawRectangle(0, 0, getWidth(), getHeight(), edgeColor);
            view.drawLine(1, 1, getWidth()-2, 1, topRim);
            view.drawLine(1, 2, 1, getHeight()-3, topRim);
            view.drawLine(getWidth()-2, 2, getWidth()-2, getHeight()-3, bottomRim);
            view.drawLine(1, getHeight()-2, getWidth()-2, getHeight()-2, bottomRim);

            // Draw icon
            view.draw(icon, 0, 0);
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.ENTER) {
                if (!buttonDown) {
                    buttonDown = true;
                    invalidate();
                    onClick();
                }
            } else {
                super.onKeyPressed(event);
            }
        }

        @Override
        public void onKeyReleased(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.ENTER) {
                if (buttonDown) {
                    buttonDown = false;
                    invalidate();
                }
            } else {
                super.onKeyReleased(event);
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
                .key_top(25, 93, 131).key_btm(44, 109, 186).grid_bg(25, 93, 131)
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

        /** Does not draw anything */
        public static final NoteColors NOTE_NONE = NoteColors.builder().build();
        /** Defines the colors for drawing a non-selected note */
        public static final NoteColors NOTE_DEFAULT = NoteColors.builder()
                .top(255, 64, 64).btm(220, 55, 55).build();
        /** Defines the colors for drawing a note that has been selected (cursor intersection) */
        public static final NoteColors NOTE_SELECTED = NoteColors.builder()
                .top(213, 219, 92).btm(183, 188, 79).build();
        /** Defines the colors for drawing the cursor, which is a note that is not active / not played */
        public static final NoteColors NOTE_INACTIVE = NoteColors.builder()
                .top(211, 217, 220).btm(199, 199, 199).build();
        /** Defines the colors for drawing a note that is part of a pattern in pattern selection mode */
        public static final NoteColors NOTE_PATTERN_DEFAULT = NoteColors.builder()
                .top(54, 168, 176).btm(36, 161, 161).build();
        /** Defines the colors for drawing a note that is part of a pattern that the cursor is also at */
        public static final NoteColors NOTE_PATTERN_SELECTED = NoteColors.builder()
                .top(77, 238, 250).btm(66, 205, 215).build();
        /** Defines the colors for drawing a note that is part of a pattern that a real note is also at*/
        public static final NoteColors NOTE_PATTERN_OVERLAP = NoteColors.builder()
                .top(25, 204, 127).btm(56, 178, 127).build();

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
            public byte TOP = MapColorPalette.COLOR_TRANSPARENT;
            public byte BTM = MapColorPalette.COLOR_TRANSPARENT;

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
