package com.bergerkiller.bukkit.tc.Listeners;

import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldListener;

import com.bergerkiller.bukkit.tc.GroupManager;
import com.bergerkiller.bukkit.tc.MinecartGroup;

public class TCWorldListener extends WorldListener {

	@Override
	public void onChunkUnload(ChunkUnloadEvent event) {
		if (!event.isCancelled()) {
			boolean hastounload = false;
			for (MinecartGroup mg : MinecartGroup.getGroups()) {
				if (mg.isInChunk(event.getChunk())) {
					if (mg.canUnload()) {
						hastounload = true;
					} else {
						event.setCancelled(true);
						return;
					}
				}
			}
			if (hastounload) {
				for (MinecartGroup mg : MinecartGroup.getGroups()) {
					if (mg.isInChunk(event.getChunk())) {
						GroupManager.hideGroup(mg);
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
