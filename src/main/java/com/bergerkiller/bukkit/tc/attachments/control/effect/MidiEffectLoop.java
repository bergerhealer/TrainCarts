package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChart;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChartParameters;

/**
 * Plays a sequence of speed (pitch) and volume values over time,
 * based on a certain BPM and play duration. Can optionally be set to loop
 * infinitely.
 */
public class MidiEffectLoop extends SequenceEffectLoop {
    private AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> effects = AttachmentNameLookup.NameGroup.none();
    private MidiChart chart = new MidiChart(MidiChartParameters.DEFAULT);

    public AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> getEffects() {
        return effects;
    }

    public void setEffects(AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> effects) {
        this.effects = effects;
    }

    public MidiChart getChart() {
        return chart;
    }

    public void setChart(MidiChart chart) {
        this.chart = chart;
    }

    @Override
    public boolean advance(long prevNanos, long currNanos) {
        chart.forNotesInRange(prevNanos, currNanos, n -> n.play(effects));
        return true;
    }
}
