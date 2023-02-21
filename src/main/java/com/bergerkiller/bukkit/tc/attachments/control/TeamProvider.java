package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutScoreboardTeamHandle;
import com.bergerkiller.mountiplex.reflection.util.UniqueHash;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Generates scoreboard teams and manages the assigning of these teams to groups
 * of entities. The name for the team is automatically generated. Three further
 * Team implementations are available: glow colors, disabling collision and
 * a disabled team which removes an entity from all teams when assigned.
 */
public class TeamProvider {
    private final TrainCarts plugin;
    private final UniqueHash teamIdHash = new UniqueHash();
    private Map<Player, ViewerState> viewerStates = new HashMap<>();
    private Set<ViewerState.ViewedTeam> pendingTeamUpdates = new HashSet<>();
    private final Task updateTask;
    private final Team disabledTeam = new Team() {
        @Override
        public void join(Player viewer, Iterable<UUID> entityUUIDs) {
            reset(viewer, entityUUIDs);
        }

        @Override
        public void join(AttachmentViewer viewer, Iterable<UUID> entityUUIDs) {
            reset(viewer, entityUUIDs);
        }

        @Override
        public void join(Player viewer, UUID entityUUID) {
            reset(viewer, entityUUID);
        }

        @Override
        public void join(AttachmentViewer viewer, UUID entityUUID) {
            reset(viewer, entityUUID);
        }
    };
    private final Team noCollisionTeam = buildTeam()
            .visibility(false)
            .collision(false)
            .rememberEntities(false)
            .build();

    // Some sub-implementations for unique teams
    private final GlowColorTeamProvider glowColors;

    public TeamProvider(TrainCarts plugin) {
        this.plugin = plugin;
        this.updateTask = new Task(plugin) {
            @Override
            public void run() {
                // Process all the teams with entities to remove from the team (or teams to remove)
                for (Iterator<ViewerState.ViewedTeam> iter = pendingTeamUpdates.iterator(); iter.hasNext();) {
                    if (!iter.next().update()) {
                        iter.remove();
                    }
                }

                // Process all the teams with entities to add to the team (or teams to create)
                pendingTeamUpdates.forEach(ViewerState.ViewedTeam::assignEntities);
                pendingTeamUpdates.clear();
            }
        };
        this.glowColors = new GlowColorTeamProvider(this);
    }

    /**
     * Gets a glow color team provider. This tracks several unique teams specifically
     * for changing the glowing color effect of entities.
     *
     * @return glow color team provider
     */
    public GlowColorTeamProvider glowColors() {
        return this.glowColors;
    }

    /**
     * A special Team constant which, if players join it, means they get unassigned
     * from any other team they were previously on
     *
     * @return disabled team
     * @see #reset(Player, UUID) 
     */
    public Team disabledTeam() {
        return disabledTeam;
    }

    /**
     * A team that has the collision set to never. Entities assigned to this team
     * will not collide with players. This Team will not track the entities that
     * have been added to it. It's primary use is to disable collision
     * of entities and to then forget about them.
     *
     * @return no-collision team
     */
    public Team noCollisionTeam() {
        return noCollisionTeam;
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
            for (ViewerState.ViewedTeam team : state.teams.values()) {
                team.reset();
            }
        }
        this.viewerStates.clear();
    }

    private void schedule(ViewerState.ViewedTeam viewedTeam) {
        if (this.pendingTeamUpdates.isEmpty()) {
            if (this.updateTask.getPlugin().isEnabled()) {
                this.updateTask.start();
            }
        }
        this.pendingTeamUpdates.add(viewedTeam);
    }

    /**
     * Starts building a new Team. The returned object can be configured, after
     * which with {@link TeamBuilder#build()} a new Team can be created.
     * This Team can then be used to assign entities to it, for player viewers.
     * Each Team is global to the server.
     *
     * @return new Team Builder
     */
    public TeamBuilder buildTeam() {
        return new TeamBuilder("ZZTCTeam" + this.teamIdHash.nextHex());
    }

    /**
     * Cleans up state when an entity that was possibly assigned a team is
     * destroyed for a viewer, is no longer using the glow effect, or
     * desires a white (default) glow effect. Resets multiple entities in
     * a row.
     *
     * @param viewer
     * @param entityUUIDs
     */
    public void reset(AttachmentViewer viewer, Iterable<UUID> entityUUIDs) {
        reset(viewer.getPlayer(), entityUUIDs);
    }

    /**
     * Cleans up state when an entity that was possibly assigned a team is
     * destroyed for a viewer, is no longer using the glow effect, or
     * desires a white (default) glow effect. Resets multiple entities in
     * a row.
     *
     * @param viewer
     * @param entityUUIDs
     */
    public void reset(Player viewer, Iterable<UUID> entityUUIDs) {
        ViewerState state = this.viewerStates.get(viewer);
        if (state != null) {
            for (ViewerState.ViewedTeam team : state.teams.values()) {
                for (UUID entityUUID : entityUUIDs) {
                    team.removeEntity(entityUUID);
                }
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
        reset(viewer.getPlayer(), entityUUID);
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
            for (ViewerState.ViewedTeam team : state.teams.values()) {
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
            for (ViewerState.ViewedTeam team : state.teams.values()) {
                team.reset();
            }
        }
    }

    /**
     * Builds the attributes of a new Team
     */
    public class TeamBuilder {
        private final String name;
        private ChatText prefix = ChatText.empty();
        private ChatText suffix = ChatText.empty();
        private ChatColor color = ChatColor.BLACK;
        private String visibility = "always";
        private String collision = "always";
        private boolean rememberEntities = true;

        private TeamBuilder(String name) {
            this.name = name;
        }

        public TeamBuilder prefix(ChatText prefix) {
            this.prefix = prefix;
            return this;
        }

        public TeamBuilder suffix(ChatText suffix) {
            this.suffix = suffix;
            return this;
        }

        public TeamBuilder color(ChatColor color) {
            this.color = color;
            return this;
        }

        public TeamBuilder visibility(boolean visible) {
            this.visibility = visible ? "always" : "never";
            return this;
        }

        public TeamBuilder collision(boolean enabled) {
            this.collision = enabled ? "always" : "never";
            return this;
        }

        public TeamBuilder rememberEntities(boolean remember) {
            this.rememberEntities = remember;
            return this;
        }

        /**
         * Builds the Team with the attributes set using this builder
         *
         * @return new Team
         */
        public Team build() {
            return new Team(this);
        }
    }

    /**
     * A team created by this team provider. Can be asked to let entities join
     * it.
     */
    public class Team {
        private final String name;
        private final ChatText displayName;
        private final ChatText prefix;
        private final ChatText suffix;
        private final ChatColor color;
        private final String visibility;
        private final String collision;
        private final boolean rememberEntities;

        // Disabled team only
        private Team() {
            this.name = null;
            this.displayName = null;
            this.prefix = null;
            this.suffix = null;
            this.color = null;
            this.visibility = null;
            this.collision = null;
            this.rememberEntities = false;
        }

        private Team(TeamBuilder opts) {
            this.name = opts.name;
            this.displayName = ChatText.fromMessage(opts.name);
            this.prefix = opts.prefix;
            this.suffix = opts.suffix;
            this.color = opts.color;
            this.visibility = opts.visibility;
            this.collision = opts.collision;
            this.rememberEntities = opts.rememberEntities;
        }

        /**
         * Makes a number of Entities join this team. If any of them were assigned
         * to a different team before, they leave those teams.
         *
         * @param viewer Player viewer to which the update should occur
         * @param entityUUIDs Iterable/List of entity UUIDs to assign
         */
        public void join(Player viewer, Iterable<UUID> entityUUIDs) {
            ViewerState state = viewerStates.computeIfAbsent(viewer.getPlayer(), ViewerState::new);
            state.assignTeamEntities(this, entityUUIDs);
        }

        /**
         * Makes a number of Entities join this team. If any of them were assigned
         * to a different team before, they leave those teams.
         *
         * @param viewer Player viewer to which the update should occur
         * @param entityUUIDs Iterable/List of entity UUIDs to assign
         */
        public void join(AttachmentViewer viewer, Iterable<UUID> entityUUIDs) {
            ViewerState state = viewerStates.computeIfAbsent(viewer.getPlayer(), p -> new ViewerState(viewer));
            state.assignTeamEntities(this, entityUUIDs);
        }

        /**
         * Makes an Entity join this team. If it was assigned to a different team
         * before, it leaves that team.
         *
         * @param viewer Player viewer to which the update should occur
         * @param entityUUID UUID of the Entity to join
         */
        public void join(Player viewer, UUID entityUUID) {
            ViewerState state = viewerStates.computeIfAbsent(viewer.getPlayer(), ViewerState::new);
            state.assignTeamEntity(this, entityUUID);
        }

        /**
         * Makes an Entity join this team. If it was assigned to a different team
         * before, it leaves that team.
         *
         * @param viewer Player viewer to which the update should occur
         * @param entityUUID UUID of the Entity to join
         */
        public void join(AttachmentViewer viewer, UUID entityUUID) {
            ViewerState state = viewerStates.computeIfAbsent(viewer.getPlayer(), p -> new ViewerState(viewer));
            state.assignTeamEntity(this, entityUUID);
        }

        private PacketPlayOutScoreboardTeamHandle createPacket(int method) {
            PacketPlayOutScoreboardTeamHandle packet = PacketPlayOutScoreboardTeamHandle.createNew();
            packet.setName(name);
            packet.setDisplayName(displayName);
            packet.setColor(color);
            packet.setPrefix(prefix);
            packet.setSuffix(suffix);
            packet.setMethod(method);
            packet.setVisibility(visibility);
            packet.setCollisionRule(collision);
            if (method == 0) {
                packet.setTeamOptionFlags(0x3);
            }
            return packet;
        }
    }

    private final class ViewerState {
        private final AttachmentViewer viewer;
        private final IdentityHashMap<Team, ViewedTeam> teams;

        public ViewerState(Player viewer) {
            this(plugin.getPacketQueueMap().getQueue(viewer));
        }

        public ViewerState(AttachmentViewer viewer) {
            this.viewer = viewer;
            this.teams = new IdentityHashMap<>();
        }

        public void assignTeamEntities(Team team, Iterable<UUID> entityUUIDs) {
            ViewedTeam foundViewedTeam = null;
            for (ViewedTeam viewedTeam : teams.values()) {
                if (viewedTeam.team == team) {
                    // Check whether all entity UUIDs are already contained for this team
                    Iterator<UUID> iter = entityUUIDs.iterator();
                    if (!iter.hasNext()) {
                        return;
                    }
                    while (viewedTeam.entities.contains(iter.next())) {
                        if (!iter.hasNext()) {
                            return;
                        }
                    }

                    // Keep for later
                    foundViewedTeam = viewedTeam;
                } else {
                    for (UUID uuid : entityUUIDs) {
                        viewedTeam.removeEntity(uuid);
                    }
                }
            }

            Iterator<UUID> iter = entityUUIDs.iterator();
            if (!iter.hasNext()) {
                return;
            }

            // Create a new team if needed
            if (foundViewedTeam == null) {
                foundViewedTeam = new ViewedTeam(team);
                this.teams.put(team, foundViewedTeam);
            }

            // Assign the entity UUIDs
            do {
                foundViewedTeam.addEntity(iter.next());
            } while (iter.hasNext());
        }

        public void assignTeamEntity(Team team, UUID entityUUID) {
            ViewedTeam foundViewedTeam = null;
            for (ViewedTeam viewedTeam : teams.values()) {
                if (viewedTeam.team == team) {
                    // If already stored for the desired team, do nothing
                    if (viewedTeam.entities.contains(entityUUID)) {
                        return;
                    } else {
                        foundViewedTeam = viewedTeam; // Keep for later
                    }
                } else if (viewedTeam.removeEntity(entityUUID)) {
                    // Removed from previous team
                    break;
                }
            }

            // Create a new team if needed
            if (foundViewedTeam == null) {
                foundViewedTeam = new ViewedTeam(team);
                this.teams.put(team, foundViewedTeam);
            }

            // Add the entity UUID
            foundViewedTeam.addEntity(entityUUID);
        }

        /**
         * The state of a single team according to a single viewer
         */
        public final class ViewedTeam {
            public final Team team;
            public final Set<UUID> entities = new HashSet<>();
            private Set<String> pendingAdd = Collections.emptySet();
            private Set<String> pendingRemove = Collections.emptySet();
            private boolean teamCreated;

            public ViewedTeam(Team team) {
                this.team = team;
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
                    schedule(this);
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
                    schedule(this);
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
                    viewer.send(team.createPacket(PacketPlayOutScoreboardTeamHandle.METHOD_REMOVE));
                }
            }

            public boolean update() {
                if (!team.rememberEntities) {
                    entities.clear();
                }

                if (team.rememberEntities && entities.isEmpty()) {
                    // The entire team is empty. Just remove the team for this viewer if it was created
                    reset();
                } else if (!pendingRemove.isEmpty()) {
                    // Remove the set of entities for this viewer
                    PacketPlayOutScoreboardTeamHandle packet = team.createPacket(PacketPlayOutScoreboardTeamHandle.METHOD_LEAVE);
                    packet.setPlayers(pendingRemove);
                    pendingRemove = Collections.emptySet();
                    viewer.send(packet);
                }
                if (pendingAdd.isEmpty()) {
                    pendingAdd = Collections.emptySet();
                    return false; // Don't proceed with assigning entities
                }
                return true; // Assign entities next
            }

            public void assignEntities() {
                if (!teamCreated) {
                    teamCreated = true;

                    // We are sending all entities for a team for the first time. Create the team with these entities.
                    PacketPlayOutScoreboardTeamHandle packet = team.createPacket(PacketPlayOutScoreboardTeamHandle.METHOD_ADD);
                    packet.setPlayers(pendingAdd);
                    pendingAdd = Collections.emptySet();
                    viewer.send(packet);
                } else {
                    // Add the set of entities for this viewer
                    PacketPlayOutScoreboardTeamHandle packet = team.createPacket(PacketPlayOutScoreboardTeamHandle.METHOD_JOIN);
                    packet.setPlayers(pendingAdd);
                    pendingAdd = Collections.emptySet();
                    viewer.send(packet);
                }
            }
        }
    }
}
