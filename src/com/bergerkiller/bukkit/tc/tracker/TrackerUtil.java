package com.bergerkiller.bukkit.tc.tracker;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.logging.Level;

import com.bergerkiller.bukkit.sl.Util;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityTracker;
import net.minecraft.server.EntityTrackerEntry;
import net.minecraft.server.IntHashMap;
import net.minecraft.server.WorldServer;

public class TrackerUtil {
	
	private static Field trackerSet = null;
	private static Field trackerMap = null;
	public static EntityTracker getWorldTracker(Entity entity) {
		return ((WorldServer) entity.world).tracker;
	}
	
	public static void failFields(Throwable t) {
		trackerMap = null;
		trackerSet = null;
		Util.log(Level.SEVERE, "Failed to initialize the entity tracker replacement:");
		t.printStackTrace();
		Util.log(Level.INFO, "Trains will not move smooth!");
	}
	public static void initFields() {
		try {
			trackerMap = EntityTracker.class.getDeclaredField("trackedEntities");
			trackerSet = EntityTracker.class.getDeclaredField("a");
			trackerMap.setAccessible(true);
			trackerSet.setAccessible(true);
		} catch (Throwable t) {
			failFields(t);
		}
	}
	public static IntHashMap getMap(EntityTracker tracker) {
		if (trackerMap == null) return null;
		try {
			return (IntHashMap) trackerMap.get(tracker);
		} catch (Throwable t) {
			failFields(t);
			return null;
		}
	}
	@SuppressWarnings("rawtypes")
	public static Set getSet(EntityTracker tracker) {
		if (trackerSet == null) return null;
		try {
			return (Set) trackerSet.get(tracker);
		} catch (Throwable t) {
			failFields(t);
			return null;
		}
	}
	
	public static EntityTrackerEntry getTracker(Entity entity) {
		EntityTracker tracker = getWorldTracker(entity);
		IntHashMap map = getMap(tracker);
		if (map != null) {
			synchronized (tracker) {
				return (EntityTrackerEntry) map.a(entity.id);
			}
		}
		return null;
	}	
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void setTracker(Entity entity, EntityTrackerEntry trackerentry) {
		EntityTracker tracker = getWorldTracker(entity);
		Set set = getSet(tracker);
		if (set == null) return;
		IntHashMap map = getMap(tracker);
		if (map == null) return;
		//==================================
		synchronized (tracker) {
			set.add(trackerentry);
			map.a(entity.id, trackerentry);
			trackerentry.scanPlayers(entity.world.players);
		}
	}

}
