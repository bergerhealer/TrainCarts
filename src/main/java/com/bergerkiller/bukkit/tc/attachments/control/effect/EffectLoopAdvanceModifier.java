package com.bergerkiller.bukkit.tc.attachments.control.effect;

class EffectLoopAdvanceModifier implements EffectLoop {
    private final EffectLoop base;
    private final AdvanceModifier modifier;

    public EffectLoopAdvanceModifier(EffectLoop base, AdvanceModifier modifier) {
        this.base = base;
        this.modifier = modifier;
    }

    @Override
    public boolean advance(Time dt, Time duration, boolean loop) {
        return modifier.advance(base, dt, duration, loop);
    }
}
