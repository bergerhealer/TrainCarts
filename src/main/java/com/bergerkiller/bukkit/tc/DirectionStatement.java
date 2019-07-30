package com.bergerkiller.bukkit.tc;

import java.util.Locale;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.statements.Statement;

public class DirectionStatement {
    public String direction;
    public String text;
    public Integer number;

    public DirectionStatement(String text, String defaultDirection) {
        int idx = text.indexOf(':');
        if (idx == -1) {
            this.text = text;
            this.direction = defaultDirection;
        } else {
            this.text = text.substring(idx + 1);
            this.direction = text.substring(0, idx);
        }
        if (this.text.isEmpty()) {
            this.text = "default";
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

    /**
     * Gets whether this statement is a default rule.
     * This means this direction should be chosen if no other statements match.
     * 
     * @return True if this is a default rule
     */
    public boolean isDefault() {
        String str = this.text.toLowerCase(Locale.ENGLISH);
        return str.equals("def") || str.equals("default");
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
