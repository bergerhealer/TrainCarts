package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.collections.InterpolatedMap;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberFurnace;

/**
 * Keeps track of the current sound to
 */
public class PoweredCartSoundLoop extends SoundLoop<MinecartMemberFurnace> {
    private static InterpolatedMap nodes = new InterpolatedMap();

    static {
        nodes.put(0.0, Integer.MAX_VALUE);
        nodes.put(0.001, 50.0);
        nodes.put(0.005, 23.0);
        nodes.put(0.01, 18.0);
        nodes.put(0.05, 16.0);
        nodes.put(0.1, 14.0);
        nodes.put(0.2, 8.0);
        nodes.put(0.4, 5.0);
    }

    private int swooshSoundCounter = 0;

    public PoweredCartSoundLoop(MinecartMemberFurnace member) {
        super(member);
    }

    @Override
    public void onTick() {
        if (!member.getEntity().hasFuel()) {
            return;
        }
        this.swooshSoundCounter++;
        int interval = (int) nodes.get(member.getEntity().getMovedDistance());
        if (this.swooshSoundCounter >= interval) {
            this.swooshSoundCounter = 0;

            play(SoundEffect.WALK_CLOTH, 0.6f + 0.2f * random.nextFloat(), 0.2f);
            play(SoundEffect.EXTINGUISH, 1.5f + 0.3f * random.nextFloat(), 0.05f + 0.1f * random.nextFloat());
        }
    }
}
