package com.bergerkiller.bukkit.tc;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldListener;

public class TCWorldListener extends WorldListener {

	@Override
	public void onChunkUnload(ChunkUnloadEvent event) {
		for (Entity e : event.getChunk().getEntities()) {
			if (e instanceof Minecart) {
				MinecartGroup.remove((Minecart) e);
			}
		}
	}
	
}
