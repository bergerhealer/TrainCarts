package com.bergerkiller.bukkit.tc.utils.tab;

import org.bukkit.entity.Player;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.team.TeamManager;

public class TabNameTagHiderImpl implements TabNameTagHider {
    private final TabPlayer player;
    private final TeamManager teamManager;
    private boolean needToRestoreNametag = false;

    private TabNameTagHiderImpl(TabPlayer player) {
        this.player = player;
        this.teamManager = TabAPI.getInstance().getTeamManager();
    }

    @Override
    public void hide() {
        if (!teamManager.hasHiddenNametag(player)) {
            teamManager.hideNametag(player);
            needToRestoreNametag = true;
        }
    }

    @Override
    public void show() {
        if (needToRestoreNametag) {
            needToRestoreNametag = false;
            teamManager.showNametag(player);
        }
    }

    public static TabNameTagHiderImpl ofPlayer(Player player) {
        return new TabNameTagHiderImpl(TabAPI.getInstance().getPlayer(player.getUniqueId()));
    }
}
