package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.direction.RailEnterDirection;

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
        // Make use of the event to decide this if we can
        // If member is null it could be overridden and still be available (routing)
        RailState enterState = null;
        if (member == null || event.getMember() == member) {
            enterState = event.getCartEnterState();
        }

        // Fallback: calculate rail state of the member and use that blindly
        if (member != null && enterState == null) {
            enterState = member.getRailTracker().getState();
            if (enterState != null && enterState.railPiece().isNone()) {
                enterState = null;
            }
        }

        // No known enter state, strange.
        if (enterState == null) {
            return false;
        }

        // Parse input text into valid directions
        // Then match them against the enter state
        BlockFace forwardDirection = event.getFacing().getOppositeFace();
        for (String directionName : directionNames) {
            for (RailEnterDirection dir : RailEnterDirection.parseAll(event.getRailPiece(), forwardDirection, directionName)) {
                if (dir.match(enterState)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean hasRequiredContext(MinecartMember<?> member, MinecartGroup group, SignActionEvent event) {
        // Must have a sign to know how to parse directions such as left/right, or for enter state
        if (event == null) {
            return false;
        }

        // Must have member/train, or know the enter state from the event itself (overridden)
        if (member == null && group == null && event.getCartEnterState() == null) {
            return false;
        }

        return true;
    }

    @Override
    public boolean requiredEvent() {
        return true;
    }

    @Override
    public boolean isConstant() {
        return true;
    }
}
