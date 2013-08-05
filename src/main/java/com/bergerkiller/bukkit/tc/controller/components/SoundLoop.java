package com.bergerkiller.bukkit.tc.controller.components;

import java.util.Random;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles the constant playing of sounds in a minecart
 */
public class SoundLoop<T extends MinecartMember<?>> {
	protected final T member;
	protected final Random random = new Random();

	public SoundLoop(T member) {
		this.member = member;
	}

	public void play(String soundName, float pitch, float volume) {
		this.member.getEntity().makeSound(soundName, volume, pitch);
	}

	public void onTick() {
	}
}
