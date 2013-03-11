package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Sound;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;

public class Effect {
	private static final Map<String, Integer> DIR_NAMES = new HashMap<String, Integer>();
	private static final Map<String, Integer> DISK_NAMES = new HashMap<String, Integer>();
	static {
		// Smoke
		DIR_NAMES.put("U", 4);
		DIR_NAMES.put("M", 4);
		DIR_NAMES.put("N", 7);
		DIR_NAMES.put("E", 3);
		DIR_NAMES.put("S", 1);
		DIR_NAMES.put("W", 5);
		DIR_NAMES.put("NE", 6);
		DIR_NAMES.put("SE", 0);
		DIR_NAMES.put("NW", 8);
		DIR_NAMES.put("SW", 2);
		// Record disks
		DISK_NAMES.put("NONE", 0);
		DISK_NAMES.put("13", 2256);
		DISK_NAMES.put("CAT", 2257);
		DISK_NAMES.put("BLOCKS", 2258);
		DISK_NAMES.put("CHIRP", 2259);
		DISK_NAMES.put("FAR", 2260);
		DISK_NAMES.put("MALL", 2261);
		DISK_NAMES.put("MELLOHI", 2262);
		DISK_NAMES.put("STAL", 2263);
		DISK_NAMES.put("STRAD", 2264);
		DISK_NAMES.put("WARD", 2265);
		DISK_NAMES.put("11", 2266);
		DISK_NAMES.put("WAIT", 2267);
	}

	public final List<String> effects = new ArrayList<String>();
	public float pitch = 1.0f, volume = 1.0f;
	public int range;

	public void parseEffect(String text) {
		text = text.toUpperCase(Locale.ENGLISH).replace(' ', '_');
		text = text.replace("MUSIC", "RECORD");
		if (text.equals("LINK")) {
			this.effects.add("SMOKE");
			this.effects.add("EXTINGUISH");
			return;
		}
		this.effects.add(text);
	}

	private String trimSpace(String text) {
		return text.substring(StringUtil.getSuccessiveCharCount(text, '_'));
	}

	private void play(Location location, org.bukkit.Effect effect, int data) {
		try {
			location.getWorld().playEffect(location, effect, data);
		} catch (Throwable t) {
		}
	}

	public void play(Location location) {
		for (String name : effects) {
			if (name.startsWith("SMOKE")) {
				name = trimSpace(name.substring(5));
				Integer data = null;
				if (name.length() >= 2) {
					data = DIR_NAMES.get(name.substring(0, 2));
				}
				if (data == null && name.length() >= 1) {
					data = DIR_NAMES.get(name.substring(0, 1));
				}
				if (data == null) {
					try {
						data = ParseUtil.parseInt(name, null);
					} catch (NumberFormatException ex) {}
				}
				if (data == null) {
					data = 0;
				}
				if (data == 4) {
					play(location.clone().add(0.0, 0.5, 0.0), org.bukkit.Effect.SMOKE, data);
				} else {
					play(location, org.bukkit.Effect.SMOKE, data);
				}
				continue;
			} else if (name.startsWith("RECORD")) {
				name = trimSpace(name.substring(6));
				if (name.startsWith("PLAY")) {
					name = trimSpace(name.substring(4));
				}
				Integer data = DISK_NAMES.get(name);
				if (data == null) {
					try {
						data = ParseUtil.parseInt(name, null);
					} catch (NumberFormatException ex) {}
				}
				if (data == null) {
					data = 2257;
				}
				play(location, org.bukkit.Effect.RECORD_PLAY, data);
				continue;
			}
			org.bukkit.Effect effect = ParseUtil.parseEnum(org.bukkit.Effect.class, name, null);
			if (effect != null) {
				play(location, effect, 0);
				continue;
			}
			Sound sound = ParseUtil.parseEnum(Sound.class, name, null);
			if (sound != null) {
				location.getWorld().playSound(location, sound, volume, pitch);
				continue;
			}
		}
	}
}
