package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.block.BlockFace;

public class StatementDirection extends Statement {

    @Override
    public boolean match(String text) {
        return false;
    }

    @Override
    public boolean matchArray(String text) {
        return text.equals("ed");
    }

    @Override
    public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
        return false;
    }

    @Override
    public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
        return false;
    }

    @Override
    public boolean handleArray(MinecartGroup group, String[] directionNames, SignActionEvent event) {
        if (event.getGroup() == group) {
            return handleArray(event.getMember(), directionNames, event);
        } else {
            return handleArray(group.head(), directionNames, event);
        }
    }

    @Override
    public boolean handleArray(MinecartMember<?> member, String[] directionNames, SignActionEvent event) {
        // Get faces to match
        List<BlockFace> faces;
        if (directionNames.length == 0) {
            return false;
        } else if (directionNames.length == 1) {
            faces = Arrays.asList(Direction.parseAll(directionNames[0], event.getFacing().getOppositeFace()));
        } else {
            faces = new ArrayList<BlockFace>();
            for (String directionName : directionNames) {
                faces.addAll(Arrays.asList(Direction.parseAll(directionName, event.getFacing().getOppositeFace())));
            }
        }

        // Find movement vector of Minecart
        BlockFace enterFace;
        if (event.getMember() == member) {
            enterFace = event.getCartEnterFace();
        } else {
            enterFace = Util.vecToFace(member.getEntity().getVelocity(), false);
        }

        // Check if faces contains the current movement direction of the Minecart
        return LogicUtil.contains(enterFace, faces);
    }
}
