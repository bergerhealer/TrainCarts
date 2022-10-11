package com.bergerkiller.bukkit.tc.signactions.mutex;

import java.util.UUID;

import org.bukkit.Location;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class MutexSignMetadata {
    public final MutexZoneSlotType type;
    public final String name;
    public final IntVector3 start;
    public final IntVector3 end;
    public final String statement;

    public MutexSignMetadata(MutexZoneSlotType type, String name, IntVector3 start, IntVector3 end, String statement) {
        if (name == null) {
            throw new IllegalArgumentException("Name is null");
        }
        this.type = type;
        this.name = name;
        this.start = start;
        this.end = end;
        this.statement = statement;
    }

    public static MutexSignMetadata fromSign(SignActionEvent info) {
        // mutex dx/dy/dz
        // mutex (dx+dz)/dy
        // mutex (dx+dy+dz)
        int dx = 1;
        int dy = 2;
        int dz = 1;
        String coords = info.getLine(1);
        int firstSpace = coords.indexOf(' ');
        if (firstSpace != -1) {
            coords = coords.substring(firstSpace).trim();
            if (!coords.isEmpty()) {
                String[] parts = coords.split("/");
                if (parts.length >= 3) {
                    dx = ParseUtil.parseInt(parts[0], dx);
                    dy = ParseUtil.parseInt(parts[1], dy);
                    dz = ParseUtil.parseInt(parts[2], dz);
                } else if (parts.length >= 2) {
                    dx = dz = ParseUtil.parseInt(parts[0], dx);
                    dy = ParseUtil.parseInt(parts[1], dy);
                } else if (parts.length >= 1) {
                    dx = dy = dz = ParseUtil.parseInt(parts[0], dx);
                }
            }
        }

        // uuid of world this sign is on
        UUID worldUUID = info.getWorld().getUID();

        // third line can contain a unique name to combine multiple signs together
        // when left empty, it is an anonymous slot (only this sign)
        // when something is on it, prepend world UUID so that it is unique for this world
        String name = info.getLine(2).trim();
        if (!name.isEmpty()) {
            name = worldUUID.toString() + "_" + name;
        }

        // Fourth line is the statement, if any (statements?)
        String statement = info.getLine(3).trim();

        IntVector3 block = getPosition(info);
        IntVector3 start = block.subtract(dx, dy, dz);
        IntVector3 end = block.add(dx, dy, dz);

        MutexZoneSlotType type = info.isType("smartmutex", "smutex")
                ? MutexZoneSlotType.SMART : MutexZoneSlotType.NORMAL;

        return new MutexSignMetadata(type, name, start, end, statement);
    }

    private static IntVector3 getPosition(SignActionEvent info) {
        Location middlePos = info.getCenterLocation();
        if (middlePos != null) {
            return new IntVector3(middlePos);
        } else {
            return new IntVector3(info.getBlock());
        }
    }
}
