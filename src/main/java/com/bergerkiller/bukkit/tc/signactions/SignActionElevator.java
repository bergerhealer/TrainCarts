package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.SignActionHeader;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.BlockTimeoutMap;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

import java.util.Locale;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

public class SignActionElevator extends SignAction {
    public static BlockTimeoutMap ignoreTimes = new BlockTimeoutMap();

    public static boolean isElevator(Sign sign) {
        if (SignActionHeader.parseFromSign(sign).isValid()) {
            if (Util.getCleanLine(sign, 1).toLowerCase(Locale.ENGLISH).startsWith("elevator")) {
                return true;
            }
        }
        return false;
    }

    public static Block findElevator(Block from, BlockFace mode) {
        while ((from = Util.findRailsVertical(from, mode)) != null) {
            for (Block signblock : Util.getSignsFromRails(from)) {
                if (isElevator(BlockUtil.getSign(signblock))) {
                    return from;
                }
            }
        }
        return null;
    }

    public static Block findElevator(Block from, BlockFace mode, int elevatorCount) {
        while ((from = findElevator(from, mode)) != null) {
            if (--elevatorCount <= 0) {
                return from;
            }
        }
        return null;
    }

    public static BlockFace getSpawnDirection(Block destrail) {
        return getSpawnDirection(destrail, FaceUtil.getFaces(Util.getRailsRO(destrail).getDirection().getOppositeFace()));
    }

    public static BlockFace getSpawnDirection(Block destrail, BlockFace[] possible) {
        //find out which direction is best for this occasion
        BlockFace rval = possible[0];
        int dist = 0;
        int i = 0;
        for (BlockFace f : possible) {
            TrackIterator iter = new TrackIterator(destrail, f);
            final int lim = 4;
            for (i = 0; i < lim && iter.hasNext(); i++) iter.next();
            if (i > dist) {
                rval = f;
                dist = i;
            }
        }
        return rval;
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("elevator");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (info.getMode() == SignActionMode.NONE || !info.hasRailedMember() || !info.isPowered()) {
            return;
        }
        if (!info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_CHANGE)) {
            return;
        }
        // Is it allowed?
        if (ignoreTimes.isMarked(info.getRails(), 1000)) {
            return;
        }

        // Where to go?
        boolean forced = false;
        BlockFace mode = BlockFace.UP;
        if (info.isLine(2, "down")) {
            mode = BlockFace.DOWN;
            forced = true;
        } else if (info.isLine(2, "up")) {
            forced = true;
        }

        // Possible amounts to skip?
        int elevatorCount = ParseUtil.parseInt(info.getLine(2), 1);
        Block dest = findElevator(info.getRails(), mode, elevatorCount);
        if (!forced && dest == null) {
            dest = findElevator(info.getRails(), mode.getOppositeFace(), elevatorCount);
        }
        if (dest == null) {
            return;
        }
        ignoreTimes.mark(dest);

        // First, use the sign direction
        Sign destsign = null;
        for (Block signblock : Util.getSignsFromRails(dest)) {
            if (isElevator(destsign = BlockUtil.getSign(signblock))) {
                break;
            }
        }

        // Facing towards a rail direction?
        BlockFace[] startDirs = RailType.getType(dest).getPossibleDirections(dest);
        BlockFace launchDir = null;
        if (destsign != null) {
            BlockFace signdir = BlockUtil.getFacing(destsign.getBlock());
            if (startDirs[0] == signdir || startDirs[1] == signdir) {
                launchDir = signdir;
            }
        }
        if (launchDir == null) {
            // Find out which direction is best
            launchDir = getSpawnDirection(dest, startDirs);
        }

        // Teleport train
        info.getGroup().teleportAndGo(dest, launchDir);
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_ENTER)
                .setName("train elevator")
                .setDescription("teleport trains vertically")
                .setMinecraftWIKIHelp("Mods/TrainCarts/Signs/Elevator")
                .handle(event.getPlayer());
    }
}
