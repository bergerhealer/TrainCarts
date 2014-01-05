package com.bergerkiller.bukkit.tc.signactions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.GroupCreateEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import com.bergerkiller.bukkit.tc.utils.TrackWalkIterator;

public class SignActionSpawn extends SignAction {
	private static BlockMap<SpawnSign> spawnSigns = new BlockMap<SpawnSign>();
	private static HashMap<String, EntityType> minecartTypes = new HashMap<String, EntityType>();
	static {
		addSpawnType('m', EntityType.MINECART);
		addSpawnType('s', EntityType.MINECART_CHEST);
		addSpawnType('p', EntityType.MINECART_FURNACE);
		addSpawnType('h', EntityType.MINECART_HOPPER);
		addSpawnType('t', EntityType.MINECART_TNT);
		addSpawnType('e', EntityType.MINECART_MOB_SPAWNER);
	}
	
	private static HashMap<String, Permission> minecartPerms = new HashMap<String, Permission>();
	static {
		addPermType('m', Permission.SPAWNER_REGULAR);
		addPermType('s', Permission.SPAWNER_STORAGE);
		addPermType('p', Permission.SPAWNER_POWERED);
		addPermType('h', Permission.SPAWNER_HOPPER);
		addPermType('t', Permission.SPAWNER_TNT);
		addPermType('e', Permission.SPAWNER_SPAWNER);
	}
	
	
	public static void addPermType(char character, Permission perm) {
		minecartPerms.put(Character.toString(character).toLowerCase(Locale.ENGLISH), perm);
		minecartPerms.put(Character.toString(character).toUpperCase(Locale.ENGLISH), perm);
	}

	public static void addSpawnType(char character, EntityType type) {
		minecartTypes.put(Character.toString(character).toLowerCase(Locale.ENGLISH), type);
		minecartTypes.put(Character.toString(character).toUpperCase(Locale.ENGLISH), type);
	}

	@Override
	public boolean match(SignActionEvent info) {
		return isValid(info);
	}

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON) && getSpawnTime(info) == 0) {
			spawn(info);
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (hasCartPerms(event) && (handleBuild(event, Permission.BUILD_SPAWNER, "train spawner", "spawn trains on the tracks above when powered by redstone"))) {
			long interval = getSpawnTime(event);
			if (interval > 0 && (Permission.SPAWNER_AUTOMATIC.handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to use automatic signs"))) {
				event.getPlayer().sendMessage(ChatColor.YELLOW + "This spawner will automatically spawn trains every " + Util.getTimeString(interval) + " while powered");
				SpawnSign sign = new SpawnSign(event.getBlock(), interval);
				spawnSigns.put(event.getBlock(), sign);
				sign.start();
			}
			return true;
		}
		return false;
	}

	@Override
	public void destroy(SignActionEvent info) {
		remove(info.getBlock());
	}

	public static void remove(Block signBlock) {
		SpawnSign sign = spawnSigns.remove(signBlock);
		if (sign != null) {
			sign.remove(signBlock);
		}
	}

	public static boolean isValid(SignActionEvent event) {
		return event != null && event.getMode() != SignActionMode.NONE && event.isType("spawn");
	}

	private static String[] getArgs(SignActionEvent event) {
		final String line = event.getLine(1).toLowerCase(Locale.ENGLISH);
		final int idx = line.indexOf(' ');
		if (idx == -1) {
			return StringUtil.EMPTY_ARRAY;
		}
		return line.substring(idx + 1).split(" ");
	}

	public static double getSpawnForce(SignActionEvent event) {
		String[] bits = getArgs(event);
		if (bits.length >= 2) {
			// Choose
			if (!bits[0].contains(":")) {
				return ParseUtil.parseDouble(bits[0], 0.0);
			} else {
				return ParseUtil.parseDouble(bits[1], 0.0);
			}
		} else if (bits.length >= 1 && !bits[0].contains(":")) {
			return ParseUtil.parseDouble(bits[0], 0.0);
		}
		return 0.0;
	}

	public static long getSpawnTime(SignActionEvent event) {
		String[] bits = getArgs(event);
		if (bits.length >= 2) {
			// Choose
			if (bits[1].contains(":")) {
				return ParseUtil.parseTime(bits[1]);
			} else {
				return ParseUtil.parseTime(bits[0]);
			}
		} else if (bits.length >= 1 && bits[0].contains(":")) {
			return ParseUtil.parseTime(bits[0]);
		}
		return 0;
		
	}


	
	public static boolean hasCartPerms(SignChangeActionEvent event){
		Permission perm;
		for (char cart : (event.getLine(2) + event.getLine(3)).toCharArray()) {
			perm = minecartPerms.get(Character.toString(cart));
			if (perm.handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to create minecarts of this type")){
				continue;
			}
			else{
				return false;
			}
		}
		return true;
	}

	public static void spawn(SignActionEvent info) {
		if ((info.isTrainSign() || info.isCartSign()) && isValid(info) && info.isPowered() && info.hasRails()) {
			final double spawnForce = getSpawnForce(info);

			//Get the cart types to spawn
			ArrayList<EntityType> types = new ArrayList<EntityType>();
			StringBuilder amountBuilder = new StringBuilder();
			EntityType type;
			for (char cart : (info.getLine(2) + info.getLine(3)).toCharArray()) {
				type = minecartTypes.get(Character.toString(cart));
				if (type != null) {
					if (amountBuilder.length() > 0) {
						int amount = ParseUtil.parseInt(amountBuilder.toString(), 1);
						amountBuilder.setLength(0);
						for (int i = 0; i < amount ; i++) {
							types.add(type);
						}
					} else {
						types.add(type);
					}
				} else if (Character.isDigit(cart)) {
					amountBuilder.append(cart);
				}
			}

			if (types.isEmpty()) {
				return;
			}
			Location[] locs = new Location[types.size()];
			BlockFace spawnDirection = null;
			if (types.size() == 1) {
				// Single-minecart spawning logic
				locs[0] = info.getCenterLocation();
				if (MinecartMemberStore.getAt(locs[0]) == null) {
					for (BlockFace direction : info.getWatchedDirections()) {
						direction = direction.getOppositeFace();
						spawnDirection = direction;
						TrackIterator iter = new TrackIterator(info.getRails(), direction);
						// Ignore the starting block
						iter.next();
						// Next block available?
						if (iter.hasNext()) {
							break;
						}
					}
				}
			} else {
				// Multiple-minecart spawning logic
				for (BlockFace direction : info.getWatchedDirections()) {
					direction = direction.getOppositeFace();
					TrackWalkIterator iter = new TrackWalkIterator(info.getCenterLocation(), direction);
					boolean occupied = false;
					for (int i = 0; i < types.size(); i++) {
						if (!iter.hasNext()) {
							occupied = true;
							break;
						}
						locs[i] = iter.next();
						//not taken?
						if (MinecartMemberStore.getAt(locs[i]) != null) {
							occupied = true;
							break;
						}
					}
					if (!occupied) {
						spawnDirection = direction;
						break;
					}
				}
			}
			// Check for failure
			if (spawnDirection == null) {
				return;
			}

			// Prepare chunks
			for (Location loc : locs) {
				WorldUtil.loadChunks(loc, 2);
			}

			//Spawn
			if (spawnDirection != null) {
				MinecartGroup group = MinecartGroup.create();
				for (int i = 0; i < locs.length; i++) {
					MinecartMember<?> mm = MinecartMemberStore.spawn(locs[i], types.get(i));
					group.add(mm);
					if (spawnForce != 0 && i == 0) {
						mm.getActions().addActionLaunch(spawnDirection, 2, spawnForce);
					}
				}
				group.updateDirection();
				group.getProperties().setDefault("spawner");
				GroupCreateEvent.call(group);
			}
		}
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

	public static void deinit() {
		for (SpawnSign sign : spawnSigns.values()) {
			sign.stop();
		}
	}

	public static void save(String filename) {
		new DataWriter(filename) {
			public void write(DataOutputStream stream) throws IOException {
				stream.writeInt(spawnSigns.size());
				for (SpawnSign sign : spawnSigns.values()) {
					sign.write(stream);
				}
			}
		}.write();
	}
}
