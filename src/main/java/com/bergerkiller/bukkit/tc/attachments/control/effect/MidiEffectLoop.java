package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChart;

/**
 * Plays a sequence of speed (pitch) and volume values over time,
 * based on a certain BPM and play duration. Can optionally be set to loop
 * infinitely.
 */
public class MidiEffectLoop extends SequenceEffectLoop {
    private Attachment.EffectSink effectSink = Attachment.EffectSink.DISABLED_EFFECT_SINK;
    private MidiChart chart = MidiChart.empty();

    public Attachment.EffectSink getEffectSink() {
        return effectSink;
    }

    public void setEffectSink(Attachment.EffectSink effectSink) {
        this.effectSink = effectSink;
    }

    public MidiChart getChart() {
        return chart;
    }

    public void setChart(MidiChart chart) {
        this.chart = chart;
    }

    @Override
    public boolean advance(long prevNanos, long currNanos) {
        return chart.forNotesInRange(prevNanos, currNanos, n -> n.play(effectSink));
    }
}
