package com.bergerkiller.bukkit.tc.Utils;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.tc.SimpleChunk;


public class ChunkUtil {
	
	public static int toChunk(double loc) {
		return ((int) loc) >> 4;
	}
	public static boolean getChunkSafe(Entity e) {
		return getChunkSafe(e.getLocation());
	}
	public static boolean getChunkSafe(Location loc) {
		return getChunkSafe(loc.getWorld(), toChunk(loc.getX()), toChunk(loc.getZ()));
	}
	public static boolean getChunkSafe(net.minecraft.server.Entity e) {
		return getChunkSafe(e.world.getWorld(), toChunk(e.lastX), toChunk(e.lastZ));
	}
	public static boolean getChunkSafe(World w, int chunkX, int chunkZ) {
		if (!w.isChunkLoaded(chunkX, chunkZ)) return false;
		if (!w.isChunkLoaded(chunkX + 1, chunkZ)) return false;
		if (!w.isChunkLoaded(chunkX - 1, chunkZ)) return false;
		if (!w.isChunkLoaded(chunkX, chunkZ + 1)) return false;
		if (!w.isChunkLoaded(chunkX, chunkZ - 1)) return false;
		return true;
	}	
	public static void addNearChunks(ArrayList<SimpleChunk> rval, World w, int chunkX, int chunkZ, int radius, boolean addloaded, boolean addunloaded) {
		for (int dx = -radius; dx <= radius * 2;dx++) {
			for (int dz = -radius; dz <= radius * 2;dz++) {
				SimpleChunk c = new SimpleChunk();
				c.world = w;
				c.chunkX = chunkX + dx;
				c.chunkZ = chunkZ + dz;
				if ((addloaded && c.isLoaded()) || (addunloaded && !c.isLoaded())) {
					rval.add(c);
				}
			}
		}
	}
	
}
