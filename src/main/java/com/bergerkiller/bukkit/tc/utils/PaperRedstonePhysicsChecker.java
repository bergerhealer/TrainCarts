package com.bergerkiller.bukkit.tc.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.generated.net.minecraft.world.level.WorldHandle;

/**
 * When 'fire-physics-event-for-redstone' is set to 'false' in the paper.yml of PaperSpigot for a particular
 * world where Traincarts trains are used, this class prints a warning (once)
 */
public class PaperRedstonePhysicsChecker {
    private static final Set<UUID> _checked = new HashSet<UUID>();

    /**
     * See class description
     */
    public static void check(World world) {
        if (!Common.IS_PAPERSPIGOT_SERVER || !_checked.add(world.getUID())) {
            return; // not relevant
        }

        // Retrieve paper world configuration
        // Permit silent failure because whatever...
        try {
            Object worldHandle = WorldHandle.fromBukkit(world).getRaw();
            java.lang.reflect.Field worldConfigField = worldHandle.getClass().getField("paperConfig");
            Object worldConfig = worldConfigField.get(worldHandle);
            java.lang.reflect.Field propertyField = worldConfig.getClass().getField("firePhysicsEventForRedstone");
            boolean property = propertyField.getBoolean(worldConfig);
            if (!property) {
                TrainCarts.plugin.log(Level.WARNING, "Traincarts is used on a world that has 'fire-physics-event-for-redstone' set to 'false' in paper.yml");
                TrainCarts.plugin.log(Level.WARNING, "This may cause some Traincarts signs to malfunction on world: '" + world.getName() + "'");
            }
        } catch (Throwable t) {
        }
    }
}
