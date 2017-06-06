package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.statements.Statement;
import org.bukkit.block.BlockFace;

import java.util.Locale;

public class DirectionStatement {
    public Direction direction;
    public String text;
    public Integer number;

    public DirectionStatement(String text, BlockFace cartDirection) {
        this(text, BlockFace.SELF, Direction.NONE);
    }

    public DirectionStatement(String text, BlockFace cartDirection, Direction alternative) {
        int idx = text.indexOf(':');
        if (idx == -1) {
            this.text = text;
            this.direction = alternative;
        } else {
            this.text = text.substring(idx + 1);
            // Parse Direction from String text
            final String dirText = text.substring(0, idx).toLowerCase(Locale.ENGLISH);
            if (LogicUtil.contains(dirText, "c", "continue")) {
                this.direction = Direction.fromFace(cartDirection);
            } else if (LogicUtil.contains(dirText, "i", "rev", "reverse", "inverse")) {
                this.direction = Direction.fromFace(cartDirection.getOppositeFace());
            } else {
                this.direction = Direction.parse(dirText);
            }
            // If direction parsing fails, resolve back to alternative text and direction
            if (this.direction == Direction.NONE) {
                this.text = text;
                this.direction = alternative;
            }
        }
        // Number (counter) statements
        try {
            this.number = Integer.parseInt(this.text);
        } catch (NumberFormatException ex) {
            this.number = null;
        }
    }

    public boolean has(SignActionEvent event, MinecartMember<?> member) {
        return Statement.has(member, this.text, event);
    }

    public boolean has(SignActionEvent event, MinecartGroup group) {
        return Statement.has(group, this.text, event);
    }

    public boolean hasNumber() {
        return this.number != null;
    }

    @Override
    public String toString() {
        if (this.number != null) {
            return "{direction=" + this.direction + " every " + this.number.intValue() + "}";
        } else {
            return "{direction=" + this.direction + " when " + this.text + "}";
        }
    }
}
