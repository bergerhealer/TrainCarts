package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.ParseUtil;

public class Effect {
	private static final Map<String, BlockFace> DIR_NAMES = new HashMap<String, BlockFace>();
	private static final BlockFace[] SMOKE_DIRS;
	static {
		SMOKE_DIRS = new BlockFace[9];
		SMOKE_DIRS[0] = BlockFace.SOUTH_EAST;
		SMOKE_DIRS[1] = BlockFace.SOUTH;
		SMOKE_DIRS[2] = BlockFace.SOUTH_WEST;
		SMOKE_DIRS[3] = BlockFace.EAST;
		SMOKE_DIRS[4] = BlockFace.UP;
		SMOKE_DIRS[5] = BlockFace.WEST;
		SMOKE_DIRS[6] = BlockFace.NORTH_EAST;
		SMOKE_DIRS[7] = BlockFace.NORTH;
		SMOKE_DIRS[8] = BlockFace.NORTH_WEST;
		DIR_NAMES.put("u", BlockFace.UP);
		DIR_NAMES.put("m", BlockFace.UP);
		DIR_NAMES.put("n", BlockFace.NORTH);
		DIR_NAMES.put("e", BlockFace.EAST);
		DIR_NAMES.put("s", BlockFace.SOUTH);
		DIR_NAMES.put("w", BlockFace.WEST);
		DIR_NAMES.put("ne", BlockFace.NORTH_EAST);
		DIR_NAMES.put("se", BlockFace.SOUTH_EAST);
		DIR_NAMES.put("nw", BlockFace.NORTH_WEST);
		DIR_NAMES.put("sw", BlockFace.SOUTH_WEST);
	}

	public final List<String> effects = new ArrayList<String>();
	public float pitch = 1.0f, volume = 1.0f;
	public int range;

	public void parseEffect(String text) {
		text = text.toUpperCase().replace(' ', '_');
		if (text.equals("LINK")) {
			this.effects.add("SMOKE");
			this.effects.add("EXTINGUISH");
			return;
		}
		this.effects.add(text);
	}

	public void play(Location location) {
		World world = location.getWorld();
		for (String name : effects) {
			if (name.startsWith("SMOKE")) {
				name = name.substring(5);
				BlockFace data = null;
				if (name.length() >= 2) {
					data = DIR_NAMES.get(name.substring(0, 2));
				}
				if (data != null && name.length() >= 1) {
					data = DIR_NAMES.get(name.substring(0, 1));
				}
				if (data == null) {
					try {
						data = SMOKE_DIRS[Integer.parseInt(name) % SMOKE_DIRS.length];
					} catch (NumberFormatException ex) {}
				}
				if (data == null) {
					data = BlockFace.SOUTH_EAST;
				}
				if (data == BlockFace.UP) {
					location = location.add(0.0, 0.5, 0.0);
				}
				world.playEffect(location, org.bukkit.Effect.SMOKE, data);
				continue;
			}
			org.bukkit.Effect effect = ParseUtil.parseEnum(org.bukkit.Effect.class, name, null);
			if (effect != null) {
				world.playEffect(location, effect, 0);
				continue;
			}
			Sound sound = ParseUtil.parseEnum(Sound.class, name, null);
			if (sound != null) {
				world.playSound(location, sound, volume, pitch);
				continue;
			}
		}
	}
}
