package com.bergerkiller.bukkit.tc.signactions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.BlockMap;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.ArrivalSigns;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.GroupCreateEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bergerkiller.bukkit.tc.utils.TrackWalkIterator;

public class SignActionSpawn extends SignAction {

	public static boolean isValid(SignActionEvent event) {
		return event != null && event.getMode() != SignActionMode.NONE && event.isType("spawn");
	}

	private static BlockMap<SpawnSign> spawnSigns = new BlockMap<SpawnSign>();

	public static void remove(Block signBlock) {
		SpawnSign sign = spawnSigns.remove(signBlock);
		if (sign != null) {
			sign.remove(signBlock);
		}
	}

	public static void spawn(SignActionEvent info) {
		if (info.isTrainSign() || info.isCartSign()) {
			if (isValid(info) && info.isPowered()) {
				if (!info.hasRails()) return;
				int idx = info.getLine(1).lastIndexOf(" ");
				double force = 0.0;
				if (idx != -1) {
					force = ParseUtil.parseDouble(info.getLine(1).substring(idx + 1), 0.0);
				}

				//Get the cart types to spawn
				ArrayList<Integer> types = new ArrayList<Integer>();
				for (char cart : (info.getLine(2) + info.getLine(3)).toCharArray()) {
					if (cart == 'm' || cart == 'M') {
						types.add(0);
					} else if (cart == 's' || cart == 'S') {
						types.add(1);
					} else if (cart == 'p' || cart == 'P') {
						types.add(2);
					}
				}

				if (types.isEmpty()) return;
				for (BlockFace direction : info.getWatchedDirections()) {
					direction = direction.getOppositeFace();

					Location[] locs = new Location[types.size()];
					TrackWalkIterator iter = new TrackWalkIterator(info.getRailLocation(), direction);
					boolean occupied = false;
					for (int i = 0; i < types.size(); i++) {
						if (!iter.hasNext()) return;
						locs[i] = iter.next();
						//not taken?
						if (MinecartMember.getAt(locs[i]) != null) {
							occupied = true;
							break;
						}
					}
					if (occupied) {
						continue;
					}
					
					// Prepare chunks
					for (Location loc : locs) {
						WorldUtil.loadChunks(loc, 2);
					}

					//Spawn
					MinecartGroup group = MinecartGroup.create();
					for (int i = 0; i < locs.length; i++) {
						MinecartMember mm = MinecartMember.spawn(locs[i], types.get(i));
						group.add(mm);
						if (force != 0 && i == 0) {
							mm.addActionLaunch(direction, 2, force);
						}
					}
					GroupCreateEvent.call(group);
				}
			}
		}
	}

	public static long getSpawnTime(SignActionEvent event) {
		String line = event.getLine(1).toLowerCase();
		if (line.startsWith("spawn ")) {
			String[] bits = line.substring(6).split(" ");
			if (bits.length > 0) {
				if (bits.length > 1 || bits[0].contains(":")) {
					return Util.parseTime(bits[0]);
				}
			}
		}
		return 0;
	}

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON)) {
			spawn(info);
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (isValid(event)) {
			if (handleBuild(event, Permission.BUILD_SPAWNER, "train spawner", "spawn trains on the tracks above when powered by redstone")) {
				long interval = getSpawnTime(event);
				if (interval > 0) {
					event.getPlayer().sendMessage(ChatColor.YELLOW + "This spawner will automatically spawn trains every " + ArrivalSigns.getTimeString(interval) + " while powered");
					SpawnSign sign = new SpawnSign(event.getBlock(), interval);
					spawnSigns.put(event.getBlock(), sign);
					sign.start();
				}
				return true;
			}
		}
		return false;
	}

	public static void init(String filename) {
		spawnSigns.clear();
		new DataReader(filename) {
			public void read(DataInputStream stream) throws IOException {
				int count = stream.readInt();
				for (;count > 0; --count) {
					SpawnSign sign = SpawnSign.read(stream);
					spawnSigns.put(sign.getWorldName(), sign.getLocation(), sign);
					sign.start();
				}
			}
		}.read();
	}

	public static void deinit(String filename) {
		new DataWriter(filename) {
			public void write(DataOutputStream stream) throws IOException {
				stream.writeInt(spawnSigns.size());
				for (SpawnSign sign : spawnSigns.values()) {
					sign.write(stream);
					sign.stop();
				}
			}
		}.write();
	}
}
