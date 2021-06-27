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
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutScoreboardTeamHandle;

/**
 * Automatically generates scoreboard teams with a variety of colors and
 * assigns entities to them. The teams are automatically sent and updated
 * to the viewers of the entity. All this is done to change the color
 * of the glowing status effect.
 */
public class GlowColorTeamProvider {
    private Map<Player, ViewerState> viewerStates = new HashMap<>();
    private Set<ViewerState.Team> pendingTeamUpdates = new HashSet<ViewerState.Team>();
    private final Task updateTask;

    public GlowColorTeamProvider(TrainCarts plugin) {
        this.updateTask = new Task(plugin) {
            @Override
            public void run() {
                // Process all the teams with entities to remove from the team (or teams to remove)
                Iterator<ViewerState.Team> iter = pendingTeamUpdates.iterator();
                while (iter.hasNext()) {
                    ViewerState.Team team = iter.next();
                    if (team.entities.isEmpty()) {
                        // The entire team is empty. Just remove the team for this viewer if it was created
                        team.reset();
                    } else if (!team.pendingRemove.isEmpty()) {
                        // Remove the set of entities for this viewer
                        PacketPlayOutScoreboardTeamHandle packet = team.createPacket(0x4);
                        packet.setPlayers(team.pendingRemove);
                        team.pendingRemove = Collections.emptySet();
                        PacketUtil.sendPacket(team.state.viewer, packet);
                    }
                    if (team.pendingAdd.isEmpty()) {
                        team.pendingAdd = Collections.emptySet();
                        iter.remove();
                    }
                }

                // Process all the teams with entities to add to the team (or teams to create)
                iter = pendingTeamUpdates.iterator();
                while (iter.hasNext()) {
                    ViewerState.Team team = iter.next();
                    if (!team.teamCreated) {
                        team.teamCreated = true;

                        // We are sending all entities for a team for the first time. Create the team with these entities.
                        PacketPlayOutScoreboardTeamHandle packet = team.createPacket(0x0);
                        packet.setPlayers(team.pendingAdd);
                        team.pendingAdd = Collections.emptySet();
                        PacketUtil.sendPacket(team.state.viewer, packet);
                    } else {
                        // Add the set of entities for this viewer
                        PacketPlayOutScoreboardTeamHandle packet = team.createPacket(0x3);
                        packet.setPlayers(team.pendingAdd);
                        team.pendingAdd = Collections.emptySet();
                        PacketUtil.sendPacket(team.state.viewer, packet);
                    }
                }
                pendingTeamUpdates.clear();
            }
        };
    }

    /**
     * Enables the provider, initializing background services
     */
    public void enable() {
    }

    /**
     * Disables the provider, resetting state immediately
     */
    public void disable() {
        if (!this.pendingTeamUpdates.isEmpty()) {
            this.pendingTeamUpdates.clear();
            this.updateTask.stop();
        }
        for (ViewerState state : this.viewerStates.values()) {
            for (ViewerState.Team team : state.teams.values()) {
                team.reset();
            }
        }
        this.viewerStates.clear();
    }

    private void schedule(ViewerState.Team team) {
        if (this.pendingTeamUpdates.isEmpty()) {
            this.updateTask.start();
        }
        this.pendingTeamUpdates.add(team);
    }

    /**
     * Assigns a color to the entity. If the entity was already part of a team
     * with a different color, the entity is re-assigned to another team.
     * 
     * @param viewer
     * @param entityUUID
     * @param color
     */
    public void update(Player viewer, UUID entityUUID, ChatColor color) {
        // For chat color white, we don't need a team, just remove from any previous teams
        if (color == ChatColor.WHITE) {
            this.reset(viewer, entityUUID);
            return;
        }

        ViewerState state = this.viewerStates.get(viewer);
        if (state == null) {
            state = new ViewerState(this, viewer);
            this.viewerStates.put(viewer, state);
        }
        for (ViewerState.Team team : state.teams.values()) {
            if (team.color == color) {
                // If already stored for the desired team, do nothing
                if (team.entities.contains(entityUUID)) {
                    return;
                }
            } else if (team.removeEntity(entityUUID)) {
                // Removed from previous team
                break;
            }
        }
        ViewerState.Team newTeam = state.getTeam(color);
        if (newTeam != null) {
            newTeam.addEntity(entityUUID);
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
    public void reset(Player viewer, UUID entityUUID) {
        ViewerState state = this.viewerStates.get(viewer);
        if (state != null) {
            for (ViewerState.Team team : state.teams.values()) {
                if (team.removeEntity(entityUUID)) {
                    break;
                }
            }
        }
    }

    /**
     * Resets all active states for the viewer, removing any teams that were added previously
     * 
     * @param viewer
     */
    public void reset(Player viewer) {
        ViewerState state = this.viewerStates.remove(viewer);
        if (state != null) {
            for (ViewerState.Team team : state.teams.values()) {
                team.reset();
            }
        }
    }

    private static final class ViewerState {
        private final GlowColorTeamProvider provider;
        private final Player viewer;
        private final EnumMap<ChatColor, Team> teams;

        public ViewerState(GlowColorTeamProvider provider, Player viewer) {
            this.provider = provider;
            this.viewer = viewer;
            this.teams = new EnumMap<ChatColor, Team>(ChatColor.class);
        }

        public Team getTeam(ChatColor color) {
            Team team = this.teams.get(color);
            if (team == null) {
                team = new Team(this, color);
                this.teams.put(color, team);
            }
            return team;
        }

        private static final class Team {
            private final ViewerState state;
            public final String name;
            public final ChatText prefix;
            public final ChatColor color;
            public final Set<UUID> entities = new HashSet<>();
            private Set<String> pendingAdd = Collections.emptySet();
            private Set<String> pendingRemove = Collections.emptySet();
            private boolean teamCreated;

            public Team(ViewerState state, ChatColor color) {
                this.state = state;
                this.name = "tcglowcolor" + color.ordinal();
                this.prefix = ChatText.fromChatColor(color);
                this.color = color;
                this.teamCreated = false;
            }

            public boolean addEntity(UUID entityUUID) {
                if (this.entities.add(entityUUID)) {
                    String entityUUIDStr = entityUUID.toString();
                    if (!this.pendingRemove.isEmpty() && this.pendingRemove.remove(entityUUIDStr)) {
                        return true; // It was never removed in the first place
                    }

                    // Add to the 'to add' set
                    if (this.pendingAdd.isEmpty()) {
                        this.pendingAdd = new HashSet<String>();
                    }
                    this.pendingAdd.add(entityUUIDStr);
                    this.state.provider.schedule(this);
                    return true;
                }
                return false;
            }

            public boolean removeEntity(UUID entityUUID) {
                if (this.entities.remove(entityUUID)) {
                    String entityUUIDStr = entityUUID.toString();
                    if (!this.pendingAdd.isEmpty() && this.pendingAdd.remove(entityUUIDStr)) {
                        return true; // It was never added in the first place
                    }

                    // Add to the 'to remove' set
                    if (this.pendingRemove.isEmpty()) {
                        this.pendingRemove = new HashSet<String>();
                    }
                    this.pendingRemove.add(entityUUIDStr);
                    this.state.provider.schedule(this);
                    return true;
                }
                return false;
            }

            public void reset() {
                if (this.teamCreated) {
                    this.teamCreated = false;
                    this.pendingRemove = Collections.emptySet();
                    this.pendingAdd = Collections.emptySet();
                    this.entities.clear();
                    PacketUtil.sendPacket(this.state.viewer, this.createPacket(0x1));
                }
            }

            private PacketPlayOutScoreboardTeamHandle createPacket(int mode) {
                PacketPlayOutScoreboardTeamHandle packet = PacketPlayOutScoreboardTeamHandle.createNew();
                packet.setName(this.name);
                packet.setMode(mode);
                if (mode == 0) {
                    packet.setFriendlyFire(3);
                    packet.setVisibility("always");
                    packet.setCollisionRule("always");
                    packet.setPrefix(this.prefix);
                    packet.setColor(this.color);
                }
                return packet;
            }
        }
    }
}
