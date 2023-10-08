package com.bergerkiller.bukkit.tc.attachments.control.effect;

class EffectLoopAdvanceModifier implements EffectLoop {
    private final EffectLoop base;
    private final AdvanceModifier modifier;

    public EffectLoopAdvanceModifier(EffectLoop base, AdvanceModifier modifier) {
        this.base = base;
        this.modifier = modifier;
    }

    @Override
    public RunMode runMode() {
        return base.runMode();
    }

    @Override
    public boolean advance(double dt) {
        return modifier.advance(base, dt);
    }
}
