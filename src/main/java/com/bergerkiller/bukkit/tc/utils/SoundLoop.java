package com.bergerkiller.bukkit.tc.utils;

import java.util.Random;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles the constant playing of sounds in a minecart
 */
public class SoundLoop {
	protected final MinecartMember member;
	protected final Random random = new Random();

	public SoundLoop(MinecartMember member) {
		this.member = member;
	}

	public void play(String soundName, float pitch, float volume) {
		this.member.world.makeSound(this.member, soundName, volume, pitch);
	}

	public void onTick() {
	}
}
