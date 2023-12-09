package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChart;

/**
 * Plays a sequence of speed (pitch) and volume values over time,
 * based on a certain BPM and play duration. Can optionally be set to loop
 * infinitely.
 */
public class MidiScheduledEffectLoop extends ScheduledEffectLoopBase {
    private MidiChart chart = MidiChart.empty();

    public MidiChart getChart() {
        return chart;
    }

    public void setChart(MidiChart chart) {
        this.chart = chart;
    }

    @Override
    public boolean advance(long prevNanos, long currNanos) {
        final Attachment.EffectSink effectSink = getEffectSink();
        return chart.forNotesInRange(prevNanos, currNanos, n -> n.play(effectSink));
    }
}
