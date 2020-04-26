package com.bergerkiller.bukkit.tc.pathfinding;

import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.common.config.FileConfiguration;

/**
 * Saved destination route manager. Is used to save sequences of destinations
 * to file so they can be quickly applied to trains or minecarts.
 */
public class RouteManager {
    private final FileConfiguration config;
    private boolean changed;

    public RouteManager(String configFileName) {
        this.config = new FileConfiguration(configFileName);
        this.changed = false;
    }

    /**
     * Loads the routes from disk, discarding any routes previously stored in memory
     */
    public void load() {
        this.config.load();
        this.config.setHeader("This file stores lists of destinations that can be set as a route on trains or carts");
        if (!this.config.exists()) {
            this.config.save();
        }
        this.changed = false;
    }

    /**
     * Saves (or autosaves) the file to disk
     * 
     * @param autosave Whether this is an autosave, or forced save
     */
    public void save(boolean autosave) {
        if (!this.changed || !autosave) {
            this.changed = false;
            this.config.save();
        }
    }

    /**
     * Looks up a route by name
     * 
     * @param name route name
     * @return route, is an empty list of the route does not exist
     */
    public List<String> findRoute(String name) {
        if (this.config.contains(name)) {
            return Collections.unmodifiableList(this.config.getList(name, String.class));
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Updates a list of destinations for a route
     * 
     * @param name route name
     * @param route to set, if empty or null removes the route entirely
     */
    public void storeRoute(String name, List<String> route) {
        if (route == null || route.isEmpty()) {
            this.config.remove(name);
        } else {
            this.config.set(name, route);
        }
        this.changed = true;
    }
}
