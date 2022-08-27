package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexSignMetadata;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import net.md_5.bungee.api.ChatColor;

/**
 * Defines zones where only one train is allowed at one time.
 * Trains that enter it after another train has, will be blocked until the zone is cleared.
 * The second line defines the box radius (x/y/z) around the sign blocking trains.
 * One value: cube, two values: dx+dz/dy, three x/y/z
 */
public class SignActionMutex extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("mutex", "smartmutex", "smutex");
    }

    @Override
    public void execute(SignActionEvent info) {
    }

    @Override
    public boolean canSupportFakeSign(SignActionEvent info) {
        return false;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        // Check mutex zone isnt too big
        {
            MutexSignMetadata meta = MutexSignMetadata.fromSign(event);
            IntVector3 dim = meta.end.subtract(meta.start);
            if (dim.x > TCConfig.maxMutexSize || dim.y > TCConfig.maxMutexSize || dim.z > TCConfig.maxMutexSize) {
                event.getPlayer().sendMessage(ChatColor.RED + "Mutex zone is too large! Maximum size is " + TCConfig.maxMutexSize);
                return false;
            }
        }

        if (event.isType("smartmutex", "smutex")) {
            return SignBuildOptions.create()
                    .setPermission(Permission.BUILD_MUTEX)
                    .setName("smart mutex zone")
                    .setDescription("prevent more than one train occupying the same rail blocks within a zone")
                    .setTraincartsWIKIHelp("TrainCarts/Signs/Mutex")
                    .handle(event.getPlayer());
        } else {
            return SignBuildOptions.create()
                    .setPermission(Permission.BUILD_MUTEX)
                    .setName("mutex zone")
                    .setDescription("prevent more than one train entering a zone")
                    .setTraincartsWIKIHelp("TrainCarts/Signs/Mutex")
                    .handle(event.getPlayer());
        }
    }

    @Override
    public void loadedChanged(SignActionEvent info, boolean loaded) {
        // Note: we ignore unloading, it stays active even while the sign chunk isn't loaded
        // Removal occurs when the offline sign metadata store signals the sign is gone
        if (loaded) {
            info.getTrainCarts().getOfflineSigns().computeIfAbsent(info.getSign(), MutexSignMetadata.class,
                    offline -> MutexSignMetadata.fromSign(info));
        }
    }
}
