package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

/**
 * A basic ScheduledEffectLoop implementation that adds a configurable effect
 * sink that can be used to play effects through.
 */
public abstract class ScheduledEffectLoopBase implements ScheduledEffectLoop {
    private Attachment.EffectSink effectSink = Attachment.EffectSink.DISABLED_EFFECT_SINK;

    /**
     * Gets the effect sink used to play effects part of this sequence
     *
     * @return Effect Sink
     */
    public Attachment.EffectSink getEffectSink() {
        return effectSink;
    }

    /**
     * Sets the effect sink used to play effects part of this sequence
     *
     * @param effectSink Effect Sink
     */
    public void setEffectSink(Attachment.EffectSink effectSink) {
        this.effectSink = effectSink;
    }
}
