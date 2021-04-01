package com.bergerkiller.bukkit.tc.statements;

import java.util.List;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementPassenger extends Statement {

    @Override
    public boolean match(String text) {
        return text.startsWith("passenger") || text.startsWith("player");
    }

    @Override
    public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
        return text.toLowerCase().startsWith("player") ? member.getEntity().hasPlayerPassenger() : member.getEntity().hasPassenger();
    }

    @Override
    public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
        int count = 0;
        boolean playermode = text.toLowerCase().startsWith("player");
        for (MinecartMember<?> member : group) {
            if (playermode) {
                count += member.getEntity().getPlayerPassengers().size();
            } else {
                count += member.getEntity().getPassengers().size();
            }
        }
        return Util.evaluate(count, text);
    }

    @Override
    public boolean matchArray(String text) {
        return text.equals("p");
    }

    @Override
    public boolean handleArray(MinecartMember<?> member, String[] names, SignActionEvent event) {
        List<Player> playerPassengers = member.getEntity().getPlayerPassengers();
        if (!playerPassengers.isEmpty()) {
            for (Player player : playerPassengers) {
                String pname = player.getName();
                for (String name : names) {
                    if (Util.matchText(pname, name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public int priority() {
        // Make sure it is matched after other statements, because otherwise
        // it starts matching all statements that start with 'player'.
        return -1;
    }
}
