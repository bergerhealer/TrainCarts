package com.bergerkiller.bukkit.tc.attachments.old;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutScoreboardTeamHandle;

/**
 * An instance of a fake team, which is also tracked who has the information sent.
 * This prevents client disconnects.
 */
public class FakeTeam {
    private final String name;
    private final String player;
    private final HashSet<UUID> viewers = new HashSet<UUID>();

    public FakeTeam(String teamName, String playerName) {
        this.name = teamName;
        this.player = playerName;
    }

    // called by onJoin to clean this up
    public void remove(Player viewer) {
        this.viewers.remove(viewer.getUniqueId());
    }

    public void send(Player viewer) {
        if (!this.viewers.add(viewer.getUniqueId())) {
            return;
        }

        PacketPlayOutScoreboardTeamHandle teamPacket = PacketPlayOutScoreboardTeamHandle.T.newHandleNull();
        teamPacket.setName(this.name);
        teamPacket.setDisplayName(ChatText.fromMessage(this.name));
        teamPacket.setPrefix(ChatText.fromMessage(""));
        teamPacket.setSuffix(ChatText.fromMessage(""));
        teamPacket.setVisibility("never");
        teamPacket.setCollisionRule("never");
        teamPacket.setMode(0x0);
        teamPacket.setFriendlyFire(0x3);
        teamPacket.setPlayers(new ArrayList<String>(Collections.singleton(this.player)));
        teamPacket.setColor(ChatColor.RESET);
        PacketUtil.sendPacket(viewer, teamPacket);
    }
}
