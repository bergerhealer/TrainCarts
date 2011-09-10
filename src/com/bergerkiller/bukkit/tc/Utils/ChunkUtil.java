package com.bergerkiller.bukkit.tc.Utils;

import java.util.ArrayList;

import org.bukkit.World;

import com.bergerkiller.bukkit.tc.SimpleChunk;


public class ChunkUtil {
	
	public static int toChunk(double loc) {
		return ((int) loc) >> 4;
	}
	public static boolean addNearChunks(ArrayList<SimpleChunk> rval, World w, int chunkX, int chunkZ, int radius, boolean addloaded, boolean addunloaded) {
		boolean added = false;
		for (int dx = -radius; dx <= radius * 2;dx++) {
			for (int dz = -radius; dz <= radius * 2;dz++) {
				SimpleChunk c = new SimpleChunk();
				c.world = w;
				c.chunkX = chunkX + dx;
				c.chunkZ = chunkZ + dz;
				if ((addloaded && c.isLoaded()) || (addunloaded && !c.isLoaded())) {
					if (rval != null) {
						rval.add(c);
					}
					added = true;
				}
			}
		}
		return added;
	}
	
}
