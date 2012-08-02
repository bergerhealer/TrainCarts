package com.bergerkiller.bukkit.tc.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import net.minecraft.server.ChunkCoordIntPair;
import net.minecraft.server.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;

import com.bergerkiller.bukkit.common.Operation;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class OfflineGroupManager {
	private static boolean chunkLoadReq = false;
	private static boolean ignoreChunkLoad = false;
	private static Set<String> containedTrains = new HashSet<String>();
	private static HashSet<UUID> hiddenMinecarts = new HashSet<UUID>();
	private static Map<UUID, OfflineGroupManager> managers = new HashMap<UUID, OfflineGroupManager>();
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
		if (ignoreChunkLoad) return;
		synchronized (managers) {
			OfflineGroupManager man = managers.get(chunk.getWorld().getUID());
			if (man != null) {
				if (man.groupmap.isEmpty()) {
					managers.remove(chunk.getWorld().getUID());
				} else {
					Set<OfflineGroup> groups = man.groupmap.remove(chunk);
					if (groups != null) {
						for (OfflineGroup group : groups) {
							if (group.chunkCounter == 0) {
								// First chunk being loaded, verify?
								group.updateLoadedChunks(chunk.getWorld());
							} else {
								group.chunkCounter++;
								if (group.chunkCounter == group.chunks.size() - 1) {
									// Just in case we missed a chunk, refresh
									group.updateLoadedChunks(chunk.getWorld());
								}
							}
							if (group.testFullyLoaded()) {
								//a participant to be restored
								if (group.updateLoadedChunks(chunk.getWorld())) {
									man.groupmap.remove(group);
									containedTrains.remove(group.name);
									restoreGroup(group, chunk.getWorld());
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
							group.chunkCounter--;
						}
					}
				}
			}
		}
	}

	private OfflineGroupMap groupmap = new OfflineGroupMap();

	public static void refresh() {
		for (WorldServer world : WorldUtil.getWorlds()) {
			refresh(world.getWorld());
		}
	}
	public static void refresh(World world) {
		synchronized (managers) {
			OfflineGroupManager man = managers.get(world.getUID());
			if (man != null) {
				if (man.groupmap.isEmpty()) {
					managers.remove(world.getUID());
				} else {
					ignoreChunkLoad = true;
					man.refreshGroups(world);
					ignoreChunkLoad = false;
				}
			}
		}
	}

	public void refreshGroups(World world) {
		chunkLoadReq = false;
		try {
			Iterator<OfflineGroup> iter = this.groupmap.values().iterator();
			while (iter.hasNext()) {
				OfflineGroup wg = iter.next();
				if (checkChunks(wg, world)) {
					containedTrains.remove(wg.name);
					this.groupmap.remove(wg, true);
					iter.remove();
					restoreGroup(wg, world);
				}
			}
			if (chunkLoadReq) {
				this.refreshGroups(world);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static boolean checkChunks(OfflineGroup g, World world) {
		if (g.updateLoadedChunks(world)) {
		} else if (TrainProperties.get(g.name).isKeepingChunksLoaded()) {
			if (TrainCarts.keepChunksLoadedOnlyWhenMoving) {
				boolean ismoving = false;
				for (OfflineMember wm : g.members) {
					if (Math.abs(wm.motX) > 0.001) {
						ismoving = true;
						break;
					}
					if (Math.abs(wm.motZ) > 0.001) {
						ismoving = true;
						break;
					}
				}
				if (!ismoving) return false;
			}
			//load nearby chunks
			for (OfflineMember wm : g.members) {
				WorldUtil.loadChunks(world, wm.cx, wm.cz, 2);
			}
		} else {
			return false;
		}
		return true;
	}
	private static void restoreGroup(OfflineGroup g, World world) {
		for (OfflineMember wm : g.members) {
			hiddenMinecarts.remove(wm.entityUID);
		}
		g.create(world);
	}

	/*
	 * Train removal
	 */
	public static int destroyAll(World world) {
		synchronized (managers) {
			OfflineGroupManager man = managers.remove(world.getUID());
			if (man != null) {
				for (OfflineGroup wg : man.groupmap) {
					containedTrains.remove(wg.name);
					for (OfflineMember wm : wg.members) {
						hiddenMinecarts.remove(wm.entityUID);
					}
				}
			}
		}
		int count = 0;
		for (MinecartGroup g : MinecartGroup.getGroups()) {
			if (g.getWorld() == world) {
				if (!g.isEmpty()) count++;
				g.destroy();
			}
		}
		destroyMinecarts(world);
		removeBuggedMinecarts();
		return count;
	}
	public static int destroyAll() {
		int count = 0;
		synchronized (managers) {
			managers.clear();
			containedTrains.clear();
			hiddenMinecarts.clear();
		}
		for (MinecartGroup g : MinecartGroup.getGroups()) {
			if (!g.isEmpty()) count++;
			g.destroy();
		}
		for (World world : Bukkit.getServer().getWorlds()) {
			destroyMinecarts(world);
		}
		removeBuggedMinecarts();
		TrainProperties.clearAll();
		return count;
	}
	public static void destroyMinecarts(World world) {
		for (Entity e : world.getEntities()) {
			if (!e.isDead()) {
				if (e instanceof Minecart) e.remove();
			}
		}
	}

	/**
	 * Gets rid of all Minecraft that are stored in the chunk, but not in the World,
	 * resolving collision problems. (this should really never happen, but it is there just in case)
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void removeBuggedMinecarts() {
		new Operation() {
			private Set<net.minecraft.server.Entity> toRemove;
			private Set worldentities;
			@Override
			public void run() {
				this.worldentities = new HashSet();
				this.toRemove = new HashSet<net.minecraft.server.Entity>();
				this.doWorlds();
			}
			@Override
			public void handle(WorldServer world) {
				this.worldentities.clear();
				this.worldentities.addAll(world.entityList);
				this.doChunks(world);
			}
			@Override
			public void handle(net.minecraft.server.Chunk chunk) {
				this.doEntities(chunk);
				if (!this.toRemove.isEmpty()) {
					for (List list : chunk.entitySlices) {
						list.removeAll(this.toRemove);
					}
				}
				for (net.minecraft.server.Entity e : this.toRemove) {
					e.world.removeEntity(e);
					WorldUtil.getTracker(e.world).untrackEntity(e);
				}
				this.toRemove.clear();
			}
			@Override
			public void handle(net.minecraft.server.Entity entity) {
				if (!this.worldentities.contains(entity)) {
					toRemove.add(entity);
				}
			}
		};
	}
	
	private static void deinit() {
		managers.clear();
		hiddenMinecarts.clear();
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
					int worldcount = stream.readInt();
					for (int i = 0; i < worldcount; i++) {
						UUID worldUID = StreamUtil.readUUID(stream);
						int groupcount = stream.readInt();
						OfflineGroupManager man = get(worldUID);
						for (int j = 0; j < groupcount; j++) {
							OfflineGroup wg = OfflineGroup.readFrom(stream);
							wg.worldUUID = worldUID;
							for (OfflineMember wm : wg.members) hiddenMinecarts.add(wm.entityUID);
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
			TrainCarts.plugin.log(Level.INFO, "Loading chunks near trains...");
			// Obtain all the chunks that have to be loaded
			for (World world : Bukkit.getWorlds()) {
				initChunks(world);
			}
		}
	}

	/**
	 * Loads all the chunks near keep-chunks-loaded trains
	 * 
	 * @param World to load
	 */
	public static void initChunks(World world) {
		OfflineGroupManager man = get(world);
		Set<ChunkCoordIntPair> loaded = new HashSet<ChunkCoordIntPair>();
		for (OfflineGroup group : man.groupmap) {
			TrainProperties prop = TrainProperties.get(group.name);
			if (prop.isKeepingChunksLoaded()) {
				for (OfflineMember wm : group.members) {
					for (int x = wm.cx - 2; x <= wm.cx + 2; x++) {
						for (int z = wm.cz - 2; z <= wm.cz + 2; z++) {
							loaded.add(new ChunkCoordIntPair(x, z));
						}
					}
				}
			}
		}
		for (ChunkCoordIntPair coord : loaded) {
			world.getChunkAt(coord.x, coord.z);
		}
	}

	/**
	 * Saves the buffered groups to file
	 * @param filename - The groupdata file to write to
	 */
	public static void deinit(String filename) {
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
			deinit();
		}
	}

	/**
	 * Buffers the group and unlinks the members
	 * @param group - The group to buffer
	 */
	public static void hideGroup(MinecartGroup group) {
		if (group == null || !group.isValid()) return;
		World world = group.getWorld();
		if (world == null) return;
		synchronized (managers) {
			for (MinecartMember mm : group) {
				hiddenMinecarts.add(mm.uniqueId);
			}
			//==== add =====
			OfflineGroup wg = new OfflineGroup(group);
			wg.updateLoadedChunks(world);
			get(world).groupmap.add(wg);
			containedTrains.add(wg.name);
			//==============
			group.unload();
		}
	}
	public static void hideGroup(Object member) {
		MinecartMember mm = MinecartMember.get(member);
		if (mm != null && !mm.dead) hideGroup(mm.getGroup());
	}

	/**
	 * Check if this minecart is in a buffered group
	 * Used to check if a minecart can be linked
	 * @param m - The minecart to check
	 */
	public static boolean wasInGroup(Entity minecartentity) {
		return wasInGroup(minecartentity.getUniqueId());
	}
	public static boolean wasInGroup(UUID minecartUniqueID) {
		return hiddenMinecarts.contains(minecartUniqueID);
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
