package com.bergerkiller.bukkit.tc;

import org.bukkit.World;

public class SimpleChunk {
	public World world;
	public int chunkX;
	public int chunkZ;
	
	public boolean isLoaded() {
		return world.isChunkLoaded(chunkX, chunkZ);
	}
	public void load() {
		world.loadChunk(chunkX, chunkZ);
	}
	public void unload() {
		world.unloadChunk(chunkX, chunkZ);
	}
	
}
