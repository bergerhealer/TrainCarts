package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutScoreboardTeamHandle;

/**
 * Automatically generates scoreboard teams with a variety of colors and
 * assigns entities to them. The teams are automatically sent and updated
 * to the viewers of the entity. All this is done to change the color
 * of the glowing status effect.
 */
public class GlowColorTeamProvider {
    private final TeamProvider teamProvider;
    private final Map<ChatColor, TeamProvider.Team> teamsByColor = new EnumMap<>(ChatColor.class);

    public GlowColorTeamProvider(TeamProvider teamProvider) {
        this.teamProvider = teamProvider;
        for (ChatColor color : ChatColor.values()) {
            if (color == ChatColor.WHITE) {
                teamsByColor.put(color, teamProvider.disabledTeam());
            } else if (color.isColor()) {
                teamsByColor.put(color, teamProvider.buildTeam()
                        .color(color)
                        .prefix(ChatText.fromChatColor(color))
                        .build());
            }
        }
    }

    /**
     * Assigns a color to the entity. If the entity was already part of a team
     * with a different color, the entity is re-assigned to another team.
     * 
     * @param viewer
     * @param entityUUID
     * @param color
     * @deprecated Should use {@link #update(AttachmentViewer, UUID, ChatColor)} instead
     */
    @Deprecated
    public void update(Player viewer, UUID entityUUID, ChatColor color) {
        update(AttachmentViewer.fallback(viewer), entityUUID, color);
    }

    /**
     * Assigns a color to the entity. If the entity was already part of a team
     * with a different color, the entity is re-assigned to another team.
     * 
     * @param viewer
     * @param entityUUID
     * @param color
     */
    public void update(AttachmentViewer viewer, UUID entityUUID, ChatColor color) {
        if (color == null) {
            reset(viewer, entityUUID);
        } else {
            TeamProvider.Team team = teamsByColor.get(color);
            if (team != null) {
                team.join(viewer, entityUUID);
            }
        }
    }

    /**
     * Cleans up state when an entity that was possibly assigned a team is
     * destroyed for a viewer, is no longer using the glow effect, or
     * desires a white (default) glow effect
     * 
     * @param viewer
     * @param entityUUID
     */
    public void reset(AttachmentViewer viewer, UUID entityUUID) {
        teamProvider.reset(viewer, entityUUID);
    }

    /**
     * Cleans up state when an entity that was possibly assigned a team is
     * destroyed for a viewer, is no longer using the glow effect, or
     * desires a white (default) glow effect
     * 
     * @param viewer
     * @param entityUUID
     */
    public void reset(Player viewer, UUID entityUUID) {
        teamProvider.reset(viewer, entityUUID);
    }

    /**
     * Resets all active states for the viewer, removing any teams that were added previously
     * 
     * @param viewer
     */
    public void reset(Player viewer) {
        teamProvider.reset(viewer);
    }
}
