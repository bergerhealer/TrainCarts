package com.bergerkiller.bukkit.tc.attachments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatOrientation;
import com.bergerkiller.generated.com.mojang.authlib.GameProfileHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPlayerInfoHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutScoreboardTeamHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPlayerInfoHandle.EnumPlayerInfoActionHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPlayerInfoHandle.PlayerInfoDataHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

/**
 * Can spawn fake duplicates of existing players with certain attributes
 * applied to them. Each mode can only be spawned once for a player.
 */
public enum FakePlayerSpawner {
    /**
     * Spawns a normal vanilla player. Should be used when the original player
     * entity was destroyed for a viewer, and the real player needs to be re-spawned
     * again. No more than 1 normal player can be spawned at one time!
     */
    NORMAL(null, null, false),
    /**
     * Spawns a fake NPC player, without a nametag shown
     */
    NO_NAMETAG("DinnerBone", "BoredTCRiders", true),
    /**
     * Spawns a normal vanilla player without nametag showing, but makes the UUID
     * assigned to the player entity a random one each time so that multiple instances
     * can be spawned.
     */
    NO_NAMETAG_RANDOM("DinnerBone", "BoredTCRandoms", true),
    /**
     * Spawns a fake NPC player, without a nametag shown. If two identical
     * fake players need to be spawned, then this secondary mode can be used
     * for the second player. It is not possible to spawn two fake players
     * of the same mode.
     */
    NO_NAMETAG_SECONDARY("DinnarBone", "BoredTCRiderz", true),
    /**
     * Spawns a fake NPC player, without a nametag shown,
     * with an upside-down effect applied to it.
     */
    UPSIDEDOWN("Dinnerbone", "DizzyTCRiders", true);

    // Map<PlayerUUID, Map<ViewerUUID, Name>>
    // We need this to remove the previous entry from the tab list
    // TODO: We should listen for packets to automatically obtain this!
    // Right now external plugins can break it! Duplicate entries may occur.
    private static final Map<UUID, Map<UUID, ProfileState>> _profileStates = new HashMap<UUID, Map<UUID, ProfileState>>();

    // After this number of ticks temporary player list entries are removed from viewer's tab view
    private static final int TAB_LIST_CLEANUP_DELAY = 5;
    
    private final String _playerName;
    private final ChatText _teamName;
    private final boolean _hideNametag;
    private final Set<UUID> _teamSentPlayers = new HashSet<UUID>();

    private FakePlayerSpawner(String playerName, String teamName, boolean hideNametag) {
        this._playerName = playerName;
        this._teamName = ChatText.fromMessage(teamName);
        this._hideNametag = hideNametag;
    }

    public ChatText getPlayerName() {
        return ChatText.fromMessage(this._playerName);
    }

    /**
     * (Re)spawns the player for a viewer with this profile name modifier applied
     * 
     * @param viewer Player to spawn all this for
     * @param player The player whose metadata to use to define the player's appearance
     * @param entityId the Id for the newly spawned entity
     * @param metaFunction Applies changes to the metadata of the spawned player
     */
    public void spawnPlayer(Player viewer, Player player, int entityId, boolean fakeFlipPitch, SeatOrientation orientation, Consumer<DataWatcher> metaFunction) {
        EntityHandle playerHandle = EntityHandle.fromBukkit(player);

        // Calculate yaw/pitch/head-yaw
        final float yaw, pitch, headRot;
        {
            float pitch_tmp, headRot_tmp;

            if (orientation == null) {
                yaw = playerHandle.getYaw();
                pitch_tmp = playerHandle.getPitch();
                headRot_tmp = playerHandle.getHeadRotation();
                if (this == UPSIDEDOWN) {
                    pitch_tmp = -pitch_tmp;
                    headRot_tmp = -headRot_tmp + 2.0f * yaw;
                }
            } else {
                yaw = orientation.getPassengerYaw();
                pitch_tmp = orientation.getPassengerPitch();
                headRot_tmp = orientation.getPassengerHeadYaw();
            }

            // If fake pitch flip is used, then make the pitch the opposite, right at the edge
            if (fakeFlipPitch) {
                if (pitch_tmp >= 180.0f) {
                    pitch_tmp = 179.0f;
                } else {
                    pitch_tmp = 181.0f;
                }
            }

            pitch = pitch_tmp;
            headRot = headRot_tmp;
        }

        spawnPlayerSimple(viewer, player, entityId, fakePlayerSpawnPacket -> {
            fakePlayerSpawnPacket.setPosX(playerHandle.getLocX());
            fakePlayerSpawnPacket.setPosY(playerHandle.getLocY());
            fakePlayerSpawnPacket.setPosZ(playerHandle.getLocZ());
            fakePlayerSpawnPacket.setYaw(yaw);
            fakePlayerSpawnPacket.setPitch(pitch);
        }, metaFunction);

        // Also synchronize the head rotation for this player
        CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
        headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, entityId);
        headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, headRot);
        PacketUtil.sendPacket(viewer, headPacket);
    }

    /**
     * (Re)spawns the player for a viewer with this profile name modifier applied.
     * Does not do any special positioning calculations and leaves that up to the caller
     * using the applier function.
     * 
     * @param viewer Player to spawn all this for
     * @param player The player whose metadata to use to define the player's appearance
     * @param entityId the Id for the newly spawned entity
     * @param applier Applies other options to the created spawn packet
     * @param metaFunction Applies changes to the metadata of the spawned player
     */
    public void spawnPlayerSimple(Player viewer, Player player, int entityId, Consumer<PacketPlayOutNamedEntitySpawnHandle> applier, Consumer<DataWatcher> metaFunction) {
        // Send list info before spawning
        ProfileState state = getProfileState(player, viewer);
        UUID uuidOfFakePlayer = this.sendPlayerProfileInfo(viewer, player, state);

        // Spawn in a fake player with the same UUID
        // Thanks to the earlier profile name change, the player will spawn upside-down
        PacketPlayOutNamedEntitySpawnHandle fakePlayerSpawnPacket = PacketPlayOutNamedEntitySpawnHandle.T.newHandleNull();
        fakePlayerSpawnPacket.setEntityId(entityId);
        fakePlayerSpawnPacket.setEntityUUID(uuidOfFakePlayer);
        applier.accept(fakePlayerSpawnPacket);

        // Copy data watcher data from the original player
        // Make the spawned player invisible initially
        // This reduces the glitchy effects before the player is mounted
        DataWatcher metaData = EntityUtil.getDataWatcher(player).clone();
        //setMetaVisibility(metaData, false);
        metaFunction.accept(metaData);
        PacketUtil.sendNamedEntitySpawnPacket(viewer, fakePlayerSpawnPacket, metaData);
    }

    private UUID sendPlayerProfileInfo(Player viewer, Player player, ProfileState state) {
        // For normal players there is nothing to do - those tab list entries aren't modified
        if (this == NORMAL) {
            return player.getUniqueId();
        }

        // Send a tab list entry for this new (fake) player to be spawned
        final UUID uuid = (this == NO_NAMETAG_RANDOM) ? generateNPCUUID()
                : ((this == NO_NAMETAG_SECONDARY) ? state.npcUUID2 : state.npcUUID);
        {
            GameProfileHandle newFakeGameProfile = GameProfileHandle.createNew(uuid, this._playerName);
            newFakeGameProfile.setAllProperties(GameProfileHandle.getForPlayer(player));
            PacketPlayOutPlayerInfoHandle newInfoPacket = PacketPlayOutPlayerInfoHandle.createNew();
            newInfoPacket.setAction(EnumPlayerInfoActionHandle.ADD_PLAYER);
            PlayerInfoDataHandle playerInfo = PlayerInfoDataHandle.createNew(
                    newInfoPacket,
                    newFakeGameProfile,
                    50,
                    GameMode.CREATIVE,
                    ChatText.fromMessage(player.getPlayerListName()) //TODO: Use components
            );
            newInfoPacket.getPlayers().add(playerInfo);
            PacketUtil.sendPacket(viewer, newInfoPacket);
        }

        // Send scoreboard team to hide the nametag
        // Only do this once to prevent a client disconnect (duplicate team)
        if (this._hideNametag && this._teamName != null && this._teamSentPlayers.add(viewer.getUniqueId())) {
            PacketPlayOutScoreboardTeamHandle teamPacket = PacketPlayOutScoreboardTeamHandle.createNew();
            teamPacket.setMethod(PacketPlayOutScoreboardTeamHandle.METHOD_ADD);
            teamPacket.setName(this._teamName.getMessage());
            teamPacket.setDisplayName(this._teamName);
            teamPacket.setPrefix(ChatText.fromMessage(""));
            teamPacket.setSuffix(ChatText.fromMessage(""));
            teamPacket.setVisibility("never");
            teamPacket.setCollisionRule("never");
            teamPacket.setTeamOptionFlags(0x3);
            teamPacket.setPlayers(new ArrayList<String>(Collections.singleton(this._playerName)));
            teamPacket.setColor(ChatColor.RESET);
            PacketUtil.sendPacket(viewer, teamPacket);
        }

        // A few ticks delayed, instantly remove the tab player list entry again
        // We cannot remove it right after spawning - then the player skin doesn't get loaded
        state.scheduleCleanupTask(viewer, this._playerName, uuid);

        return uuid;
    }

    private final ProfileState getProfileState(Player player, Player viewer) {
        return _profileStates.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<UUID, ProfileState>(1))
                .computeIfAbsent(viewer.getUniqueId(), uuid -> new ProfileState(player.getName(), uuid));
    }

    /**
     * Called when a viewer leaves the server, and sent team/player list information is presumed reset
     * 
     * @param viewer
     */
    public static void onViewerQuit(Player viewer) {
        _profileStates.remove(viewer.getUniqueId());
        for (FakePlayerSpawner modifier : values()) {
            modifier._teamSentPlayers.remove(viewer.getUniqueId());
        }
    }

    /**
     * Runs all pending tasks to remove previously added tab list entries for fake players.
     * Should be called on shutdown to prevent those kind of entries sticking behind.
     */
    public static void runAndClearCleanupTasks() {
        _profileStates.values().stream()
            .flatMap(e -> e.values().stream())
            .forEach(ProfileState::runAndClearCleanupTasks);
    }

    /**
     * Generates a unique ID for an NPC, based off of the v2 uuid pattern
     * which Minecraft uses for NPC uuids.
     *
     * @return npc uuid
     */
    private static UUID generateNPCUUID() {
        UUID uuid = UUID.randomUUID();
        return new UUID((uuid.getMostSignificantBits() & ~0xF000L) | 0x2000L, uuid.getLeastSignificantBits());
    }

    private static class ProfileState {
        public final UUID npcUUID;
        public final UUID npcUUID2;
        public final List<CleanupPlayerListEntryTask> pendingCleanup;

        public ProfileState(String playerName, UUID uuid) {
            this.npcUUID = generateNPCUUID();
            this.npcUUID2 = generateNPCUUID();
            this.pendingCleanup = new ArrayList<>();
        }

        public void scheduleCleanupTask(Player viewer, String playerName, UUID playerUUID) {
            // Cancel any previous task
            Iterator<CleanupPlayerListEntryTask> iter = this.pendingCleanup.iterator();
            while (iter.hasNext()) {
                CleanupPlayerListEntryTask task = iter.next();
                if (task.playerName.equals(playerName) && task.playerUUID.equals(playerUUID)) {
                    task.stop();
                    iter.remove();
                }
            }

            // Schedule a new one
            CleanupPlayerListEntryTask task = new CleanupPlayerListEntryTask(TrainCarts.plugin, this, viewer, playerName, playerUUID);
            this.pendingCleanup.add(task);
            task.start(TAB_LIST_CLEANUP_DELAY);
        }

        /**
         * Executed on plugin shutdown to avoid a glitched out player list state
         */
        public void runAndClearCleanupTasks() {
            if (!pendingCleanup.isEmpty()) {
                List<CleanupPlayerListEntryTask> all = new ArrayList<>(pendingCleanup);
                pendingCleanup.clear();
                for (CleanupPlayerListEntryTask task : all) {
                    task.run();
                }
            }
        }
    }

    /**
     * Task run with a delay to remove temporarily added player list entries
     */
    private static class CleanupPlayerListEntryTask extends Task {
        private final ProfileState state;
        private final Player viewer;
        public final String playerName;
        public final UUID playerUUID;

        public CleanupPlayerListEntryTask(JavaPlugin plugin, ProfileState state, Player viewer, String playerName, UUID playerUUID) {
            super(plugin);
            this.state = state;
            this.viewer = viewer;
            this.playerName = playerName;
            this.playerUUID = playerUUID;
        }

        @Override
        public void run() {
            try {
                if (this.viewer.isOnline()) {
                    GameProfileHandle oldFakeGameProfile = GameProfileHandle.createNew(this.playerUUID, this.playerName);
                    PacketPlayOutPlayerInfoHandle oldInfoPacket = PacketPlayOutPlayerInfoHandle.createNew();
                    oldInfoPacket.setAction(EnumPlayerInfoActionHandle.REMOVE_PLAYER);
                    PlayerInfoDataHandle oldPlayerInfo = PlayerInfoDataHandle.createNew(
                            oldInfoPacket,
                            oldFakeGameProfile,
                            50,
                            GameMode.CREATIVE,
                            ChatText.fromMessage("")
                    );
                    oldInfoPacket.getPlayers().add(oldPlayerInfo);
                    PacketUtil.sendPacket(this.viewer, oldInfoPacket);
                }
            } finally {
                // Cleanup ourselves from the list
                this.state.pendingCleanup.remove(this);
            }
        }
    }
}
