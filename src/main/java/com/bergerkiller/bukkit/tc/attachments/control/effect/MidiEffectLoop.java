package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChart;

import java.util.Collections;
import java.util.List;

/**
 * Plays a sequence of speed (pitch) and volume values over time,
 * based on a certain BPM and play duration. Can optionally be set to loop
 * infinitely.
 */
public class MidiEffectLoop extends SequenceEffectLoop {
    private List<AttachmentNameLookup.NameGroup<Attachment.EffectAttachment>> effects = Collections.emptyList();
    private MidiChart chart = MidiChart.empty();

    public List<AttachmentNameLookup.NameGroup<Attachment.EffectAttachment>> getEffects() {
        return effects;
    }

    public void setEffects(List<AttachmentNameLookup.NameGroup<Attachment.EffectAttachment>> effects) {
        this.effects = effects;
    }

    public void setEffects(AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> effects) {
        this.effects = Collections.singletonList(effects);
    }

    public MidiChart getChart() {
        return chart;
    }

    public void setChart(MidiChart chart) {
        this.chart = chart;
    }

    @Override
    public boolean advance(long prevNanos, long currNanos) {
        return chart.forNotesInRange(prevNanos, currNanos, n -> effects.forEach(n::play));
    }
}
