package com.bergerkiller.bukkit.tc.attachments.control.effect;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

/**
 * Simple effect loop. Plays the effect once, with an optional start delay.
 */
public class SimpleEffectLoop extends SequenceEffectLoop {
    private long nanosDelay = 0;

    /**
     * Sets the delay until the effect is played from the start of this
     * sequence
     *
     * @param delay Delay
     */
    public void setDelay(EffectLoop.Time delay) {
        nanosDelay = delay.nanos;
    }

    @Override
    public boolean advance(long prevNanos, long currNanos) {
        if (currNanos < nanosDelay) {
            return true;
        }
        if (prevNanos <= nanosDelay) {
            getEffectSink().playEffect(Attachment.EffectAttachment.EffectOptions.DEFAULT);
        }
        return false;
    }
}
