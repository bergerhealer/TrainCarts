package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexSignMetadata;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

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
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_MUTEX)
                .setName("mutex zone")
                .setDescription("prevent more than one train entering a zone")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Mutex")
                .handle(event.getPlayer());
    }

    @Override
    public void loadedChanged(SignActionEvent info, boolean loaded) {
        // Note: we ignore unloading, it stays active even while the sign chunk isn't loaded
        // Removal occurs when the offline sign metadata store signals the sign is gone
        if (loaded) {
            TrainCarts.plugin.getOfflineSigns().computeIfAbsent(info.getSign(), MutexSignMetadata.class,
                    offline -> MutexSignMetadata.fromSign(info));
        }
    }
}
