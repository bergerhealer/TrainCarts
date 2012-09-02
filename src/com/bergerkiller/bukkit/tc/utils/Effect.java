package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

import com.bergerkiller.bukkit.common.utils.EnumUtil;

public class Effect {
	public final List<Object> effects = new ArrayList<Object>();
	public float pitch = 1.0f, volume = 1.0f;
	public int range;

	public void parseEffect(String text) {
		if (text.equalsIgnoreCase("link")) {
			this.effects.add(org.bukkit.Effect.SMOKE);
			this.effects.add(org.bukkit.Effect.EXTINGUISH);
			return;
		}
		org.bukkit.Effect effect = EnumUtil.parse(org.bukkit.Effect.values(), text, null);
		if (effect != null) {
			this.effects.add(effect);
			return;
		}
		Sound sound = EnumUtil.parse(Sound.values(), text, null);
		if (sound != null) {
			this.effects.add(sound);
			return;
		}
	}

	public void play(Location location) {
		World world = location.getWorld();
		for (Object o : effects) {
			if (o instanceof Sound) {
				world.playSound(location, (Sound) o, pitch, volume);
			} else if (o instanceof org.bukkit.Effect) {
				world.playEffect(location, (org.bukkit.Effect) o, 0);
			}
		}
	}
}
