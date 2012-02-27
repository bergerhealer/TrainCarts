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

import net.minecraft.server.ChunkProviderServer;
import net.minecraft.server.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.util.LongHashtable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;

import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.TrainProperties;

public class WorldGroupManager {
	private static boolean chunkLoadReq = false;
	private static boolean ignoreChunkLoad = false;
	private static Set<String> containedTrains = new HashSet<String>();
	private static HashSet<UUID> hiddenMinecarts = new HashSet<UUID>();
	private static Map<UUID, WorldGroupManager> managers = new HashMap<UUID, WorldGroupManager>();
	public static WorldGroupManager get(UUID uuid) {
		WorldGroupManager rval = managers.get(uuid);
		if (rval == null) {
			rval = new WorldGroupManager();
			managers.put(uuid, rval);
		}
		return rval;
	}
	public static WorldGroupManager get(World world) {
		return get(world.getUID());
	}		
	public static void loadChunk(Chunk chunk) {
		chunkLoadReq = true;
		if (ignoreChunkLoad) return;
		synchronized (managers) {
			WorldGroupManager man = managers.get(chunk.getWorld().getUID());
			if (man != null) {
				if (man.groups.isEmpty()) {
					managers.remove(chunk.getWorld().getUID());
				} else {
					man.loadChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
				}
			}
		}
	}
	public static void unloadChunk(Chunk chunk) {
		synchronized (managers) {
			WorldGroupManager man = managers.get(chunk.getWorld().getUID());
			if (man != null) {
				if (man.groups.isEmpty()) {
					managers.remove(chunk.getWorld().getUID());
				} else {
					man.unloadChunk(chunk.getX(), chunk.getZ());
				}
			}
		}
	}

	private Set<WorldGroup> groups = new HashSet<WorldGroup>();
	private LongHashtable<Set<WorldGroup>> groupmap = new LongHashtable<Set<WorldGroup>>();

	private void remove(long chunk, WorldGroup group) {
		Set<WorldGroup> groups = this.groupmap.get(chunk);
		if (groups != null && groups.remove(group) & groups.isEmpty()) {
			this.groupmap.remove(chunk);
		}
	}
	public void remove(WorldGroup group) {
		containedTrains.remove(group.name);
		if (this.groups.remove(group)) {
			for (long chunk : group.chunks) {
				this.remove(chunk, group);
			}
		}
	}
	public void add(long chunk, WorldGroup group) {
		Set<WorldGroup> groups = this.groupmap.get(chunk);
		if (groups == null) {
			groups = new HashSet<WorldGroup>();
			this.groupmap.put(chunk, groups);
		}
		groups.add(group);
	}
	public void add(WorldGroup group, World world) {
		ChunkProviderServer cps = WorldUtil.getNative(world).chunkProviderServer;
		group.chunkCounter = 0;
		for (long chunk : group.chunks) {
			this.add(chunk, group);
			if (cps.chunks.containsKey(chunk)) {
				group.chunkCounter++;
			}
		}
		this.groups.add(group);
		containedTrains.add(group.name);
	}
	public void add(WorldGroup group) {
		for (long chunk : group.chunks) {
			this.add(chunk, group);
		}
		this.groups.add(group);
		containedTrains.add(group.name);
	}

	public void unloadChunk(int x, int z) {
		Set<WorldGroup> groupset = this.groupmap.get(x, z);
		if (groupset != null) {
			for (WorldGroup group : groupset) {
				group.chunkCounter--;
			}
		}
	}
	public void loadChunk(World world, int x, int z) {
		Set<WorldGroup> groupset = groupmap.get(x, z);
		if (groupset != null) {
			List<WorldGroup> groups = new ArrayList<WorldGroup>(groupset);
			groupset.clear();
			//==============================================
			for (WorldGroup group : groups) {
				group.chunkCounter++;
				if (group.testFullyLoaded()) {
					//a participant to be restored
					if (group.updateLoadedChunks(world)) {
						this.remove(group);
						restoreGroup(group, world);
					} else {
						//sync
						this.add(group, world);
						if (group.testFullyLoaded()) {
							//We don't trust this group, just remove it...
							this.remove(group);
						}
					}
				}
			}
			if (groupset.isEmpty()) {
				this.groupmap.remove(x, z);
			}
		}
	}

	public static void refresh() {
		for (WorldServer world : WorldUtil.getWorlds()) {
			refresh(world.getWorld());
		}
	}
	public static void refresh(World world) {
		synchronized (managers) {
			WorldGroupManager man = managers.get(world.getUID());
			if (man != null) {
				if (man.groups.isEmpty()) {
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
			Iterator<WorldGroup> iter = this.groups.iterator();
			while (iter.hasNext()) {
				WorldGroup wg = iter.next();
				if (checkChunks(wg, world)) {
					restoreGroup(wg, world);
					for (long chunk : wg.chunks) {
						this.remove(chunk, wg);
					}
					iter.remove();
				}
			}
			if (chunkLoadReq) {
				this.refreshGroups(world);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static boolean checkChunks(WorldGroup g, World world) {
		if (g.updateLoadedChunks(world)) {
		} else if (TrainProperties.get(g.name).keepChunksLoaded) {
			if (TrainCarts.keepChunksLoadedOnlyWhenMoving) {
				boolean ismoving = false;
				for (WorldMember wm : g.members) {
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
			for (WorldMember wm : g.members) {
				WorldUtil.loadChunks(world, wm.cx, wm.cz, 2);
			}
		} else {
			return false;
		}
		return true;
	}
	private static void restoreGroup(WorldGroup g, World world) {
		for (WorldMember wm : g.members) {
			hiddenMinecarts.remove(wm.entityUID);
		}
		MinecartGroup.create(g.name, g.getMinecarts(world));
	}


	/*
	 * Train removal
	 */
	public static int destroyAll(World world) {
		synchronized (managers) {
			WorldGroupManager man = managers.remove(world.getUID());
			if (man != null) {
				for (WorldGroup wg : man.groups) {
					containedTrains.remove(wg.name);
					for (WorldMember wm : wg.members) {
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
		return count;
	}
	public static void destroyMinecarts(World world) {
		for (Entity e : world.getEntities()) {
			if (!e.isDead()) {
				if (e instanceof Minecart) e.remove();
			}
		}
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
						WorldGroupManager man = get(worldUID);
						for (int j = 0; j < groupcount; j++) {
							WorldGroup wg = WorldGroup.readFrom(stream);
							for (WorldMember wm : wg.members) hiddenMinecarts.add(wm.entityUID);
							man.add(wg);
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
	public static void deinit(String filename) {
		synchronized (managers) {
			new DataWriter(filename) {
				public void write(DataOutputStream stream) throws IOException {
					//clear empty worlds
					Iterator<WorldGroupManager> iter = managers.values().iterator();
					while (iter.hasNext()) {
						if (iter.next().groups.isEmpty()) {
							iter.remove();
						}
					}

					//Write it
					stream.writeInt(managers.size());
					for (Map.Entry<UUID, WorldGroupManager> entry : managers.entrySet()) {
						StreamUtil.writeUUID(stream, entry.getKey());

						stream.writeInt(entry.getValue().groups.size());
						for (WorldGroup wg : entry.getValue().groups) wg.writeTo(stream);
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
			get(world).add(new WorldGroup(group), world);
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
		if (MinecartGroup.get(trainname) != null) {
			return true;
		}
		return containedTrains.contains(trainname);
	}
	public static void rename(String oldtrainname, String newtrainname) {
		MinecartGroup.rename(oldtrainname, newtrainname);
		synchronized (managers) {
			for (WorldGroupManager man : managers.values()) {
				for (WorldGroup group : man.groups) {
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

}
