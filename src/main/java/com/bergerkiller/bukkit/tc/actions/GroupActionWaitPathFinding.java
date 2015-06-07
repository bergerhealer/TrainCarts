package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;

public class GroupActionWaitPathFinding extends GroupActionWaitForever {
    private final SignActionEvent info;
    private final PathNode from;
    private final String destination;
    private int failCounter = 0;

    public GroupActionWaitPathFinding(SignActionEvent info, PathNode from, String destination) {
        this.info = info;
        this.from = from;
        this.destination = destination;
    }

    @Override
    public boolean update() {
        if (PathProvider.isProcessing()) {
            if (this.failCounter++ == 20) {
                HashSet<Player> receivers = new HashSet<Player>();
                for (MinecartMember<?> member : this.getGroup()) {
                    // Editing
                    receivers.addAll(member.getProperties().getEditingPlayers());
                    // Occupants
                    if (member.getEntity().hasPlayerPassenger()) {
                        receivers.add(member.getEntity().getPlayerPassenger());
                    }
                }
                for (Player player : receivers) {
                    player.sendMessage(ChatColor.YELLOW + "Looking for a way to reach the destination...");
                }
            }
            return super.update();
        } else {
            // Switch the rails to the right direction
            PathConnection conn = this.from.findConnection(this.destination);
            if (conn != null) {
                this.info.setRailsTo(conn.direction);
            }
            return true;
        }
    }
}
