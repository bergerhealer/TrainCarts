package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathPredictEvent;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZonePath;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlotType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

/**
 * A special type of mutex that activates a temporary mutex zone for all the tracks
 * ahead of the train. This behaves similar to the waiter sign, but using the standard
 * mutex 'obstacle' logic instead.
 */
public class SignActionPathingMutex extends TrainCartsSignAction {

    public SignActionPathingMutex() {
        super("pmutex", "spmutex", "psmutex", "pathmutex", "pathingmutex");
    }

    @Override
    public void predictPathFinding(SignActionEvent info, PathPredictEvent prediction) {
        // Make sure sign is actually 'seen'
        if (!info.isEnterActivated() || !info.isPowered()) {
            return;
        }

        // Ensure sign is registered
        final MutexZonePath path = MutexZoneCache.getOrCreatePathingMutex(info.getTrackedSign(), prediction.group(),
            prediction.railState().positionOfflineBlock().getPosition(), opt -> loadOptions(info, opt));

        // Keep alive even before the train enters the pathing zone
        path.onUsed(prediction.group());

        // Include all future blocks into the pathing zone
        prediction.trackBlock((p, d) -> {
            p.railPath().forAllBlocks(p.railPiece().blockPosition(), path::addBlock);
            return true;
        }, path, path.getMaxDistance());
    }

    private MutexZonePath.OptionsBuilder loadOptions(SignActionEvent info, MutexZonePath.OptionsBuilder opt) {
        // Smart or normal mutex mode of operation?
        opt.type(info.isType("spmutex", "psmutex")
                ? MutexZoneSlotType.SMART : MutexZoneSlotType.NORMAL);

        // Second line (after 'pathingmutex' or 'pmutex') may contain the distance and width
        // of the path turned into a mutex zone.
        String options = info.getLine(1);
        int firstSpace = options.indexOf(' ');
        if (firstSpace != -1) {
            boolean hasDistance = false;
            for (String part : options.substring(firstSpace + 1).split(" ")) {
                if (part.isEmpty()) {
                    continue;
                }
                if (!hasDistance) {
                    opt.maxDistance(ParseUtil.parseDouble(part, opt.maxDistance()));
                    hasDistance = true;
                } else {
                    opt.spacing(ParseUtil.parseDouble(part, opt.spacing()));
                }
            }
        }

        // Third line can contain a unique name to combine multiple signs together
        // when left empty, it is an anonymous slot (only this sign)
        // when something is on it, prepend world UUID so that it is unique for this world
        String name = info.getLine(2).trim();
        if (!name.isEmpty()) {
            name = info.getWorld().getUID().toString() + "_" + name;
        }
        opt.name(name);

        // Fourth line is the statement, if any (statements?)
        opt.statement(info.getLine(3).trim());

        return opt;
    }

    @Override
    public void execute(SignActionEvent info) {
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_MUTEX)
                .setName("pathing mutex zone")
                .setDescription("prevent more than one train entering a stretch of track ahead")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Mutex")
                .handle(event);
    }
}
