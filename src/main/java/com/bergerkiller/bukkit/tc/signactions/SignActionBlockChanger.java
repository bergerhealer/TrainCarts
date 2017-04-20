package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;

public class SignActionBlockChanger extends SignAction {
    private static final int BLOCK_OFFSET_NONE = Integer.MAX_VALUE;

    public static void setBlocks(Collection<MinecartMember<?>> members, String blocksText) {
        setBlocks(members, Util.getParsers(blocksText), BLOCK_OFFSET_NONE);
    }

    public static void setBlocks(Collection<MinecartMember<?>> members, ItemParser[] blocks, int blockOffset) {
        Iterator<MinecartMember<?>> iter = members.iterator();
        while (true) {
            for (ItemParser block : blocks) {
                final int amount = block.hasAmount() ? block.getAmount() : 1;
                for (int i = 0; i < amount; i++) {
                    if (!iter.hasNext()) {
                        return;
                    }
                    CommonMinecart<?> entity = iter.next().getEntity();
                    if (!block.hasType() && !block.hasData()) {
                        continue;
                    }
                    Material type = block.hasType() ? block.getType() : entity.getBlockType();
                    if (block.hasData()) {
                        entity.setBlock(type, block.getData());
                    } else {
                        entity.setBlock(type);
                    }

                    if (blockOffset != BLOCK_OFFSET_NONE) {
                        entity.setBlockOffset(blockOffset);
                    }
                }
            }
        }
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("blockchanger", "setblock", "changeblock");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isPowered()) {
            return;
        }

        ItemParser[] blocks = Util.getParsers(info.getLine(2), info.getLine(3));
        int blockOffset = ParseUtil.parseInt(info.getLine(1), BLOCK_OFFSET_NONE);
        if (info.isTrainSign() && info.hasGroup() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
            setBlocks(info.getGroup(), blocks, blockOffset);
        } else if (info.isCartSign() && info.hasMember() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
            List<MinecartMember<?>> tmp = new ArrayList<>(1);
            tmp.add(info.getMember());
            setBlocks(tmp, blocks, blockOffset);
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (MinecartGroup group : info.getRCTrainGroups()) {
                setBlocks(group, blocks, blockOffset);
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (event.isCartSign()) {
            return handleBuild(event, Permission.BUILD_BLOCKCHANGER, "cart block changer", "change the block displayed in a minecart");
        } else if (event.isTrainSign()) {
            return handleBuild(event, Permission.BUILD_BLOCKCHANGER, "train block changer", "change the blocks displayed in a train");
        } else if (event.isRCSign()) {
            return handleBuild(event, Permission.BUILD_BLOCKCHANGER, "train block changer", "change the blocks displayed in a train remotely");
        }
        return false;
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
