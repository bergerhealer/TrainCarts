package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;

/**
 * Defines zones where only one train is allowed at one time.
 * Trains that enter it after another train has, will be blocked until the zone is cleared.
 * The second line defines the box radius (x/y/z) around the sign blocking trains.
 * One value: cube, two values: dx+dz/dy, three x/y/z
 */
public class SignActionMutex extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("mutex");
    }

    @Override
    public void execute(SignActionEvent info) {
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return handleBuild(event, Permission.BUILD_MUTEX, "mutex zone", "prevent more than one train entering a zone");
    }

    @Override
    public void loadedChanged(SignActionEvent info, boolean loaded) {
        if (loaded) {
            MutexZoneCache.addMutexSign(info);
        } else {
            MutexZoneCache.removeMutexSign(info);
        }
    }
}
