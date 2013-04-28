package com.bergerkiller.bukkit.tc.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;

import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet.LongIterator;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class OfflineGroupManager {
	public static Long lastUnloadChunk = null;
	private static boolean chunkLoadReq = false;
	private static boolean isRefreshingGroups = false;
	private static Set<String> containedTrains = new HashSet<String>();
	private static HashSet<UUID> containedMinecarts = new HashSet<UUID>();
	private static Map<UUID, OfflineGroupManager> managers = new HashMap<UUID, OfflineGroupManager>();
	private OfflineGroupMap groupmap = new OfflineGroupMap();

	public static OfflineGroupManager get(UUID uuid) {
		OfflineGroupManager rval = managers.get(uuid);
		if (rval == null) {
			rval = new OfflineGroupManager();
			managers.put(uuid, rval);
		}
		return rval;
	}

	public static OfflineGroupManager get(World world) {
		return get(world.getUID());
	}

	public static void loadChunk(Chunk chunk) {
		chunkLoadReq = true;
		// Ignore chunk loads while refreshing
		if (isRefreshingGroups) {
			return;
		}
		synchronized (managers) {
			OfflineGroupManager man = managers.get(chunk.getWorld().getUID());
			if (man != null) {
				if (man.groupmap.isEmpty()) {
					managers.remove(chunk.getWorld().getUID());
				} else {
					Set<OfflineGroup> groups = man.groupmap.remove(chunk);
					if (groups != null) {
						for (OfflineGroup group : groups) {
							if (group.testFullyLoaded()) {
								//a participant to be restored
								if (group.updateLoadedChunks(chunk.getWorld())) {
									man.restoreGroup(group, chunk.getWorld());
								} else {
									//add it again
									man.groupmap.add(group);
								}
							}
						}
					}
				}
			}
		}
	}

	public static void unloadChunk(Chunk chunk) {
		synchronized (managers) {
			OfflineGroupManager man = managers.get(chunk.getWorld().getUID());
			if (man != null) {
				if (man.groupmap.isEmpty()) {
					managers.remove(chunk.getWorld().getUID());
				} else {
					Set<OfflineGroup> groupset = man.groupmap.get(chunk);
					if (groupset != null) {
						for (OfflineGroup group : groupset) {
							group.loadedChunks.remove(MathUtil.longHashToLong(chunk.getX(), chunk.getZ()));
						}
					}
				}
			}
		}
	}

	public static void refresh() {
		for (World world : WorldUtil.getWorlds()) {
			refresh(world);
		}
	}

	public static void refresh(World world) {
		synchronized (managers) {
			OfflineGroupManager man = managers.get(world.getUID());
			if (man != null) {
				if (man.groupmap.isEmpty()) {
					managers.remove(world.getUID());
				} else {
					man.refreshGroups(world);
				}
			}
		}
	}

	public void refreshGroups(World world) {
		// While refreshing, ignore incoming Chunk Load events
		// We do not want the group map to change concurrently!
		isRefreshingGroups = true;
		List<OfflineGroup> groupsBuffer = new ArrayList<OfflineGroup>(this.groupmap.size());
		try {
			// Keep refreshing until no new chunks are being loaded
			// Why? Keepchunksloaded trains can cause other trains to load
			do {
				chunkLoadReq = false;

				// Go by all groups and try to restore them
				groupsBuffer.clear();
				groupsBuffer.addAll(this.groupmap.values());
				for (OfflineGroup group : groupsBuffer) {
					if (checkChunks(group, world)) {
						restoreGroup(group, world);
					}
				}
			} while (chunkLoadReq);
		} catch (Throwable t) {
			t.printStackTrace();
		}
		isRefreshingGroups = false;
	}

	/**
	 * Checks whether the chunks of a group can be loaded, or loads these
	 * chunks when keepChunksLoaded is set
	 * 
	 * @param group to check the chunks of
	 * @param world the group is in
	 * @return True if all the chunks of the group are (now) loaded, False if not
	 */
	private static boolean checkChunks(OfflineGroup group, World world) {
		// Check whether all the chunks are loaded for this group
		if (group.updateLoadedChunks(world)) {
			return true;
		}
		// Keep chunks loaded property
		if (!TrainProperties.get(group.name).isKeepingChunksLoaded()) {
			return false;
		}
		if (TrainCarts.keepChunksLoadedOnlyWhenMoving && !group.isMoving()) {
			return false;
		}
		// Load nearby chunks
		LongIterator iter = group.chunks.longIterator();
		long chunk;
		while (iter.hasNext()) {
			chunk = iter.next();
			world.getChunkAt(MathUtil.longHashMsw(chunk), MathUtil.longHashLsw(chunk));
		}
		return true;
	}

	private void restoreGroup(OfflineGroup group, World world) {
		containedTrains.remove(group.name);
		groupmap.remove(group);
		for (OfflineMember wm : group.members) {
			containedMinecarts.remove(wm.entityUID);
		}
		group.create(world);
	}

	/*
	 * Train removal
	 */
	public static int destroyAll(World world) {
		// Ignore worlds that are disabled
		if (TrainCarts.isWorldDisabled(world)) {
			return 0;
		}
		int count = 0;
		// Remove groups
		for (MinecartGroup g : MinecartGroup.getGroups()) {
			if (g.getWorld() == world) {
				if (!g.isEmpty()) {
					count++;
				}
				g.destroy();
			}
		}
		// Remove remaining offline groups
		synchronized (managers) {
			OfflineGroupManager man = managers.remove(world.getUID());
			if (man != null) {
				for (OfflineGroup wg : man.groupmap) {
					count++;
					containedTrains.remove(wg.name);
					TrainProperties.remove(wg.name);
					for (OfflineMember wm : wg.members) {
						containedMinecarts.remove(wm.entityUID);
						// Load the chunk this minecart is in and remove it
						// We already de-linked the group map, so no worry for replacements
						Chunk chunk = world.getChunkAt(wm.cx, wm.cz);
						Iterator<Entity> iter = WorldUtil.getEntities(chunk).iterator();
						while (iter.hasNext()) {
							Entity next = iter.next();
							if (next.getUniqueId().equals(wm.entityUID)) {
								next.remove();
							}
						}
					}
				}
			}
		}
		// Remove (bugged) Minecarts
		destroyMinecarts(world);
		removeBuggedMinecarts(world);
		return count;
	}
	public static int destroyAll() {
		// The below three storage points can be safely cleared
		// Disabled worlds don't store anything in them anyway
		TrainProperties.clearAll();
		containedTrains.clear();
		containedMinecarts.clear();
		int count = 0;
		for (World world : WorldUtil.getWorlds()) {
			count += destroyAll(world);
		}
		return count;
	}

	private static void destroyMinecarts(World world) {
		for (Entity e : WorldUtil.getEntities(world)) {
			if (e instanceof Minecart) {
				e.remove();
			}
		}
		for (Chunk chunk : WorldUtil.getChunks(world)) {
			for (Entity e : WorldUtil.getEntities(chunk)) {
				if (e instanceof Minecart) {
					e.remove();
				}
			}
		}
	}

	/**
	 * Gets rid of all Minecarts that are stored in the chunk, but not in the World,
	 * resolving collision problems. (this should really never happen, but it is there just in case)
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void removeBuggedMinecarts(World world) {
		Set<org.bukkit.entity.Entity> toRemove = new HashSet<org.bukkit.entity.Entity>();
		Set worldentities = new HashSet(WorldUtil.getEntities(world));
		for (Chunk chunk : WorldUtil.getChunks(world)) {
			// Remove entities that are falsely added
			Iterator<org.bukkit.entity.Entity> iter = WorldUtil.getEntities(chunk).iterator();
			while (iter.hasNext()) {
				org.bukkit.entity.Entity e = iter.next();
				if (!worldentities.contains(e)) {
					iter.remove();
					toRemove.add(e);
				}
			}
			// Remove them from other locations
			for (org.bukkit.entity.Entity e : toRemove) {
				WorldUtil.removeEntity(e);
			}
			toRemove.clear();
		}
	}
	
	public static void deinit() {
		managers.clear();
		containedMinecarts.clear();
		containedTrains.clear();
	}

	/**
	 * Loads the buffered groups from file
	 * @param filename - The groupdata file to read from
	 */
	public static void init(String filename) {
		synchronized (managers) {
			deinit();
			new DataReader(filename) {
				public void read(DataInputStream stream) throws IOException {
					int totalgroups = 0;
					int totalmembers = 0;
					final int worldcount = stream.readInt();
					for (int worldIdx = 0; worldIdx < worldcount; worldIdx++) {
						UUID worldUID = StreamUtil.readUUID(stream);
						final int groupcount = stream.readInt();
						OfflineGroupManager man = get(worldUID);

						// Read all the groups contained
						for (int groupIdx = 0; groupIdx < groupcount; groupIdx++) {
							OfflineGroup wg = OfflineGroup.readFrom(stream);
							wg.worldUUID = worldUID;

							// Register the new offline group within (this) Manager
							for (OfflineMember wm : wg.members) {
								containedMinecarts.add(wm.entityUID);
							}
							man.groupmap.add(wg);
							containedTrains.add(wg.name);
							totalmembers += wg.members.length;
						}
						totalgroups += groupcount;
					}
					String msg = totalgroups + " Train";
					if (totalgroups == 1) msg += " has"; else msg += "s have";
					msg += " been loaded in " + worldcount + " world";
					if (worldcount != 1) msg += "s";
					msg += ". (" + totalmembers + " Minecart";
					if (totalmembers != 1) msg += "s";
					msg += ")";
					TrainCarts.plugin.log(Level.INFO, msg);
				}
			}.read();
		}
	}

	/**
	 * Saves the buffered groups to file
	 * @param filename - The groupdata file to write to
	 */
	public static void save(String filename) {
		synchronized (managers) {
			new DataWriter(filename) {
				public void write(DataOutputStream stream) throws IOException {
					//clear empty worlds
					Iterator<OfflineGroupManager> iter = managers.values().iterator();
					while (iter.hasNext()) {
						if (iter.next().groupmap.isEmpty()) {
							iter.remove();
						}
					}

					//Write it
					stream.writeInt(managers.size());
					for (Map.Entry<UUID, OfflineGroupManager> entry : managers.entrySet()) {
						StreamUtil.writeUUID(stream, entry.getKey());

						stream.writeInt(entry.getValue().groupmap.size());
						for (OfflineGroup wg : entry.getValue().groupmap) wg.writeTo(stream);
					}
				}
			}.write();
		}
	}

	/**
	 * Stores the information of a group in this offline storage system
	 * 
	 * @param group to store
	 */
	public static void storeGroup(MinecartGroup group) {
		if (group == null || !group.isValid()) {
			return;
		}
		final World world = group.getWorld();
		if (world == null) {
			return;
		}
		synchronized (managers) {
			for (MinecartMember<?> mm : group) {
				containedMinecarts.add(mm.getEntity().getUniqueId());
			}
			OfflineGroup wg = new OfflineGroup(group);
			wg.updateLoadedChunks(world);
			get(world).groupmap.add(wg);
			containedTrains.add(wg.name);
		}
	}

	/**
	 * Check if a minecart is in an offline group<br>
	 * Used to check if a minecart can be linked
	 * 
	 * @param uniqueId of the Minecart
	 */
	public static boolean containsMinecart(UUID uniqueId) {
		return containedMinecarts.contains(uniqueId);
	}

	public static int getStoredCount() {
		return containedTrains.size();
	}

	public static boolean contains(String trainname) {
		return containedTrains.contains(trainname);
	}

	public static void rename(String oldtrainname, String newtrainname) {
		synchronized (managers) {
			for (OfflineGroupManager man : managers.values()) {
				for (OfflineGroup group : man.groupmap) {
					if (group.name.equals(oldtrainname)) {
						group.name = newtrainname;
						containedTrains.remove(oldtrainname);
						containedTrains.add(newtrainname);
						return;
					}
				}
			}
		}
	}

	public static void removeMember(UUID memberUUID) {
		synchronized (managers) {
			if (containedMinecarts.remove(memberUUID)) {
				for (OfflineGroupManager manager : managers.values()) {
					if (manager.groupmap.removeCart(memberUUID)) {
						break;
					}
				}
			}
		}
	}

	public static void removeGroup(String groupName) {
		synchronized (managers) {
			containedTrains.remove(groupName);
			for (OfflineGroupManager manager : managers.values()) {
				OfflineGroup group = manager.groupmap.remove(groupName);
				if (group != null) {
					for (OfflineMember member : group.members) {
						containedMinecarts.remove(member.entityUID);
					}
					break;
				}
			}
		}
	}

	public static OfflineGroup findGroup(String groupName) {
		synchronized (managers) {
			for (OfflineGroupManager manager : managers.values()) {
				for (OfflineGroup group : manager.groupmap.values()) {
					if (group.name.equals(groupName)) {
						return group;
					}
				}
			}
		}
		return null;
	}

	public static OfflineMember findMember(String groupName, UUID uuid) {
		OfflineGroup group = findGroup(groupName);
		if (group != null) {
			for (OfflineMember member : group.members) {
				if (member.entityUID.equals(uuid)) {
					return member;
				}
			}
		}
		return null;
	}
}
