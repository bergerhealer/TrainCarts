package com.bergerkiller.bukkit.tc;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldListener;

public class TCWorldListener extends WorldListener {

	@Override
	public void onChunkUnload(ChunkUnloadEvent event) {
		for (Entity e : event.getChunk().getEntities()) {
			if (e instanceof Minecart) {
				MinecartMember mm = MinecartMember.get((Minecart) e);
				if (mm != null) {
					if (mm.getGroup() != null) {
						GroupManager.hideGroup(mm.getGroup());
					} else {
						MinecartMember.undoReplacement(mm);
					}
				}
			}
		}
	}
	
	@Override
	public void onChunkLoad(ChunkLoadEvent event) {
		GroupManager.refresh(event.getWorld());
	}
	
}
