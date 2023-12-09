package com.bergerkiller.bukkit.tc.attachments.control.effect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Plays multiple effect loops at the same time. Useful when having to track
 * a single advance().
 */
class EffectLoopGroup implements EffectLoop {
    private final List<EffectLoop> originalGroup;
    private final List<EffectLoop> group;

    public EffectLoopGroup(Collection<EffectLoop> group) {
        this.originalGroup = new ArrayList<>(group);
        this.group = new ArrayList<>(originalGroup);
    }

    @Override
    public boolean advance(Time dt, Time duration, boolean loop) {
        this.group.removeIf(e -> !e.advance(dt, duration, loop));
        return !this.group.isEmpty();
    }

    @Override
    public void resetToBeginning() {
        this.group.clear();
        this.originalGroup.forEach(EffectLoop::resetToBeginning);
        this.group.addAll(this.originalGroup);
    }
}
