package com.bergerkiller.bukkit.tc;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.Utils.ChunkUtil;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;

public class GroupManager {
	
	private static HashMap<UUID, ArrayList<WorldGroup>> hiddengroups = new HashMap<UUID, ArrayList<WorldGroup>>();
	private static ArrayList<WorldGroup> getGroups(World w) {
		ArrayList<WorldGroup> rval = hiddengroups.get(w.getUID());
		if (rval == null) {
			rval = new ArrayList<WorldGroup>();
			hiddengroups.put(w.getUID(), rval);
		}
		return rval;
	}
	
	/*
	 * Train removal
	 */
	public static void removeAll(World world, boolean destroy) {
		getGroups(world).clear();
		for (MinecartGroup g : MinecartGroup.getGroups()) {
			if (g.getWorld() == world) {
				if (destroy) {
					g.destroy();
				} else {
					g.remove();
				}
			}
		}
		if (destroy) destroyMinecarts(world);
	}
	public static void removeAll(boolean destroy) {
		hiddengroups.clear();
		for (MinecartGroup g : MinecartGroup.getGroups()) {
			if (destroy) {
				g.destroy();
			} else {
				g.remove();
			}
		}
		if (destroy) {
			for (World world : Bukkit.getServer().getWorlds()) {
				destroyMinecarts(world);
			}
		}
	}
	public static void destroyMinecarts(World world) {
		for (Entity e : world.getEntities()) {
			if (!e.isDead()) {
				if (e instanceof Minecart) e.remove();
			}
		}
	}
	
	/**
	 * Re-groups the minecarts in the buffered group in the real-time world
	 * @param w - The world to look into
	 * @param wg - The world group to read from
	 */
	private static void restoreGroup(World w, WorldGroup wg) {
		Minecart[] minecarts = wg.getMinecarts(w);
		if (minecarts.length != 0) {
			MinecartGroup.create(minecarts).setName(wg.name);
		}
		getGroups(w).remove(wg);
	}
		
	/**
	 * Loads the buffered groups from file
	 * @param filename - The groupdata file to read from
	 */
	public static void loadGroups(String filename) {
		hiddengroups.clear();
		try {
			DataInputStream stream = new DataInputStream(new FileInputStream(filename));
			try {
				int totalgroups = 0;
				int totalmembers = 0;
				int worldcount = stream.readInt();
				for (int i = 0; i < worldcount; i++) {
					UUID worldUID = new UUID(stream.readLong(), stream.readLong());
					int groupcount = stream.readInt();
					ArrayList<WorldGroup> groups = new ArrayList<WorldGroup>(groupcount);
					for (int j = 0; j < groupcount; j++) {
						WorldGroup wg = WorldGroup.readFrom(stream);
						groups.add(wg);
						totalmembers += wg.members.length;
					}
					hiddengroups.put(worldUID, groups);
					totalgroups += groupcount;
				}
				String msg = totalgroups + " Train";
				if (totalgroups == 1) msg += " has"; else msg += "s have";
				msg += " been loaded in " + worldcount + " world";
				if (worldcount != 1) msg += "s";
				msg += ". (" + totalmembers + " Minecart";
				if (totalmembers != 1) msg += "s";
				msg += ")";
				Util.log(Level.INFO, msg);
			} catch (IOException ex) {
				Util.log(Level.WARNING, "An IO exception occured while reading groups!");
				ex.printStackTrace();
			} catch (Exception ex) {
				Util.log(Level.WARNING, "A general exception occured while reading groups!");
				ex.printStackTrace();
			} finally {
				stream.close();
			}
		} catch (FileNotFoundException ex) {
			//nothing, we allow non-existence of groups
		} catch (Exception ex) {
			Util.log(Level.WARNING, "An exception occured at the end while reading groups!");
			ex.printStackTrace();
		}
	}
	
	/**
	 * Saves the buffered groups to file
	 * @param filename - The groupdata file to write to
	 */
	public static void saveGroups(String filename) {
		try {
			File f = new File(filename);
			if (f.exists()) f.delete();
			DataOutputStream stream = new DataOutputStream(new FileOutputStream(filename));
			try {
				//Remove 'empty' worlds from the set (general cleanup)
				for (UUID worldUID : hiddengroups.keySet().toArray(new UUID[0])) {
					ArrayList<WorldGroup> groups = hiddengroups.get(worldUID);
					if (groups == null || groups.size() == 0) {
						hiddengroups.remove(worldUID);
					}
				}
				
				//Write it
				stream.writeInt(hiddengroups.size());
				for (UUID worldUID : hiddengroups.keySet()) {
					ArrayList<WorldGroup> groups = hiddengroups.get(worldUID);
					stream.writeLong(worldUID.getMostSignificantBits());
					stream.writeLong(worldUID.getLeastSignificantBits());
					stream.writeInt(groups.size());
					for (WorldGroup wg : groups) wg.writeTo(stream);
				}
			} catch (IOException ex) {
				Util.log(Level.WARNING, "An IO exception occured while reading groups!");
				ex.printStackTrace();
			} catch (Exception ex) {
				Util.log(Level.WARNING, "A general exception occured while reading groups!");
				ex.printStackTrace();
			} finally {
				stream.close();
			}
		} catch (FileNotFoundException ex) {
			Util.log(Level.WARNING, "Failed to write to the groups save file!");
			ex.printStackTrace();
		} catch (Exception ex) {
			Util.log(Level.WARNING, "An exception occured at the end while reading groups!");
			ex.printStackTrace();
		}
	}
	
	/**
	 * A class containing an array of Minecart Members
	 * Also adds functions to handle multiple members at once
	 * Also adds functions to write and load from/to file
	 */
	private static class WorldGroup {
		public WorldGroup() {}
		public WorldGroup(MinecartGroup group) {
			this.members = new WorldMember[group.size()];
			for (int i = 0;i < members.length;i++) {
				this.members[i] = new WorldMember(group.getMember(i));
			}
			this.name = group.getName();
		}
		public WorldMember[] members;
		public String name;
		
		/**
		 * Tries to find all Minecarts based on their UID
		 * @param w
		 * @return An array of Minecarts
		 */
		public Minecart[] getMinecarts(World w) {
			ArrayList<Minecart> rval = new ArrayList<Minecart>();
			for (WorldMember member : members) {
				Minecart m = EntityUtil.getMinecart(w, member.entityUID);
				if (m != null) {
					m.setVelocity(new Vector(member.motX, 0, member.motZ));
					rval.add(m);
				}
			}
			return rval.toArray(new Minecart[0]);
		}
		
		/**
		 * Checks if this groups is fully in loaded chunks
		 * @param w - The world to look in
		 * @return If this group is fully in loaded chunks
		 */
		public boolean isInLoadedChunks(World w) {
			for (WorldMember wm : members) {
				if (!wm.isInLoadedChunk(w)) return false;
			}
			return true;
		}
		
		/*
		 * Read and write functions used internally
		 */
		public void writeTo(DataOutputStream stream) throws IOException {
			stream.writeInt(members.length);
			for (WorldMember member : members) member.writeTo(stream);
			stream.writeUTF(this.name);
		}
		public static WorldGroup readFrom(DataInputStream stream) throws IOException {
			WorldGroup wg = new WorldGroup();
			wg.members = new WorldMember[stream.readInt()];
			for (int i = 0;i < wg.members.length;i++) {
				wg.members[i] = WorldMember.readFrom(stream);
			}
			wg.name = stream.readUTF();
			return wg;
		}
	}
	/**
	 * Contains the information to get and restore a Minecart
	 */
	private static class WorldMember {
		public WorldMember() {}
		public WorldMember(MinecartMember instance) {
			this.motX = instance.motX;
			this.motZ = instance.motZ;
			this.entityUID = instance.uniqueId;
			this.cx = ((int) Math.floor(instance.lastX)) >> 4;
			this.cz = ((int) Math.floor(instance.lastZ)) >> 4;
		}
		public double motX, motZ;
		public UUID entityUID;
		private int cx, cz;
		public boolean isInLoadedChunk(World w) {
			return !ChunkUtil.addNearChunks(null, w, cx, cz, 2, false, true);
		}
		public void writeTo(DataOutputStream stream) throws IOException {
			stream.writeLong(entityUID.getMostSignificantBits());
			stream.writeLong(entityUID.getLeastSignificantBits());
			stream.writeDouble(motX);
			stream.writeDouble(motZ);
			stream.writeInt(cx);
			stream.writeInt(cz);
		}
		public static WorldMember readFrom(DataInputStream stream) throws IOException {
			WorldMember wm = new WorldMember();
			wm.entityUID = new UUID(stream.readLong(), stream.readLong());
			wm.motX = stream.readDouble();
			wm.motZ = stream.readDouble();	
			wm.cx = stream.readInt();
			wm.cz = stream.readInt();
			return wm;
		}
	}
	
	/*
	 * Refreshes a world or all loaded worlds
	 * This means restoring groups in loaded chunks
	 */
	public static void refresh() {
		for (World w : Bukkit.getServer().getWorlds()) {
			refresh(w);
		}
	}
	public static void refresh(World w) {
		for (WorldGroup g : getGroups(w)) {
			if (g.isInLoadedChunks(w)) {
				restoreGroup(w, g);
				refresh(w);
				break;
			}
		}
	}
	
	/**
	 * Buffers the group and unlinks the members
	 * @param group - The group to buffer
	 */
	public static void hideGroup(MinecartGroup group) {
		getGroups(group.getWorld()).add(new WorldGroup(group));
		MinecartGroup.unload(group);
	}

	/**
	 * Check if this minecart is in a buffered group
	 * Used to check if a minecart can be linked
	 * @param m - The minecart to check
	 */
	public static boolean wasInGroup(Minecart m) {
		for (WorldGroup wg : getGroups(m.getWorld())) {
			for (WorldMember wm : wg.members) {
				if (wm.entityUID.equals(m.getUniqueId())) return true;
			}
		}
		return false;
	}

	public static boolean contains(String trainname) {
		if (MinecartGroup.get(trainname) != null) {
			return true;
		}
		for (ArrayList<WorldGroup> list : hiddengroups.values()) {
			for (WorldGroup group : list) {
				if (group.name.equals(trainname)) {
					return true;
				}
			}
		}
		return false;
	}
	public static void rename(String oldtrainname, String newtrainname) {
		MinecartGroup.rename(oldtrainname, newtrainname);
		for (ArrayList<WorldGroup> list : hiddengroups.values()) {
			for (WorldGroup group : list) {
				if (group.name.equals(oldtrainname)) {
					group.name = newtrainname;
				}
			}
		}
	}
}
