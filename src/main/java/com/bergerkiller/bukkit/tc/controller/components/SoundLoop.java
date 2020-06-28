package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import java.util.Random;

import org.bukkit.Sound;

/**
 * Handles the constant playing of sounds in a minecart
 */
public class SoundLoop<T extends MinecartMember<?>> {
    protected final T member;
    protected final Random random = new Random();

    public SoundLoop(T member) {
        this.member = member;
    }

    public void play(Sound sound, float pitch, float volume) {
        this.member.getEntity().makeSound(sound, volume, pitch);
    }

    public void play(ResourceKey<SoundEffect> sound, float pitch, float volume) {
        WorldUtil.playSound(this.member.getEntity().getLocation(), sound, volume, pitch);
    }

    public void onTick() {
    }
}
