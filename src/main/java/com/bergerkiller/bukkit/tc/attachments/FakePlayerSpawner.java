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
import com.bergerkiller.bukkit.tc.Util;
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
    private static final Map<UUID, ProfileState> _dummyProfileStates = new HashMap<>();
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
     * @param player The player whose metadata to use to define the player's appearance.
     *               Specify <i>null</i> to spawn a dummy player with a random UUID.
     * @param entityId the Id for the newly spawned entity
     * @param position Position and orientation of where to spawn the player
     * @param metaFunction Applies changes to the metadata of the spawned player
     */
    public void spawnPlayer(Player viewer, Player player, int entityId, FakePlayerPosition position, Consumer<DataWatcher> metaFunction) {
        spawnPlayerSimple(viewer, player, entityId, fakePlayerSpawnPacket -> {
            fakePlayerSpawnPacket.setPosX(position.getX());
            fakePlayerSpawnPacket.setPosY(position.getY());
            fakePlayerSpawnPacket.setPosZ(position.getZ());
            fakePlayerSpawnPacket.setYaw(position.getYaw());
            fakePlayerSpawnPacket.setPitch(position.getPitch());
        }, metaFunction);

        // Also synchronize the head rotation for this player
        CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
        headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, entityId);
        headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, position.getHeadYaw());
        PacketUtil.sendPacket(viewer, headPacket);
    }

    /**
     * (Re)spawns the player for a viewer with this profile name modifier applied.
     * Does not do any special positioning calculations and leaves that up to the caller
     * using the applier function.
     * 
     * @param viewer Player to spawn all this for
     * @param player The player whose metadata to use to define the player's appearance.
     *               Specify <i>null</i> to spawn a dummy player with a random UUID.
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
        DataWatcher metaData = (player == null) ? new DataWatcher() : EntityUtil.getDataWatcher(player).clone();
        //setMetaVisibility(metaData, false);
        metaFunction.accept(metaData);
        PacketUtil.sendNamedEntitySpawnPacket(viewer, fakePlayerSpawnPacket, metaData);
    }

    private UUID sendPlayerProfileInfo(Player viewer, Player player, ProfileState state) {
        // For normal players there is nothing to do - those tab list entries aren't modified
        if (this == NORMAL && player != null) {
            return player.getUniqueId();
        }

        // Send a tab list entry for this new (fake) player to be spawned
        final UUID uuid = state.getUUID(this);
        {
            GameProfileHandle newFakeGameProfile = GameProfileHandle.createNew(uuid, this._playerName);
            ChatText playerListName;
            if (player == null) {
                // Send game profile information of a dummy player
                // TODO: Dummy player texture?
                playerListName = ChatText.fromMessage("Dummy");
            } else {
                // Send game profile information of the online player
                playerListName = ChatText.fromMessage(player.getPlayerListName()); //TODO: Use components
                newFakeGameProfile.setAllProperties(GameProfileHandle.getForPlayer(player));
            }
            PacketPlayOutPlayerInfoHandle newInfoPacket = PacketPlayOutPlayerInfoHandle.createNew();
            newInfoPacket.setAction(EnumPlayerInfoActionHandle.ADD_PLAYER);
            PlayerInfoDataHandle playerInfo = PlayerInfoDataHandle.createNew(
                    newInfoPacket,
                    newFakeGameProfile,
                    50,
                    GameMode.CREATIVE,
                    playerListName
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
        if (player == null) {
            return _dummyProfileStates.computeIfAbsent(viewer.getUniqueId(), uuid -> new ProfileState(true));
        }
        return _profileStates.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<UUID, ProfileState>(1))
                .computeIfAbsent(viewer.getUniqueId(), uuid -> new ProfileState(false));
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
        _dummyProfileStates.values().forEach(ProfileState::runAndClearCleanupTasks);
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
        private final UUID npcUUID;
        private final UUID npcUUID2;
        public final List<CleanupPlayerListEntryTask> pendingCleanup;

        public ProfileState(boolean dummy) {
            this.npcUUID = dummy ? null : generateNPCUUID();
            this.npcUUID2 = dummy ? null : generateNPCUUID();
            this.pendingCleanup = new ArrayList<>();
        }

        public UUID getUUID(FakePlayerSpawner type) {
            return (type == NO_NAMETAG_RANDOM || npcUUID == null) ? generateNPCUUID()
                    : ((type == NO_NAMETAG_SECONDARY) ? npcUUID2 : npcUUID);
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
            task.start(TAB_LIST_CLEANUP_DELAY, 1);
        }

        /**
         * Executed on plugin shutdown to avoid a glitched out player list state
         */
        public void runAndClearCleanupTasks() {
            if (!pendingCleanup.isEmpty()) {
                List<CleanupPlayerListEntryTask> all = new ArrayList<>(pendingCleanup);
                pendingCleanup.clear();
                for (CleanupPlayerListEntryTask task : all) {
                    task.finish();
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
        private final long runWhen;
        public final String playerName;
        public final UUID playerUUID;

        public CleanupPlayerListEntryTask(JavaPlugin plugin, ProfileState state, Player viewer, String playerName, UUID playerUUID) {
            super(plugin);
            this.state = state;
            this.viewer = viewer;
            this.playerName = playerName;
            this.playerUUID = playerUUID;
            this.runWhen = System.currentTimeMillis() + TAB_LIST_CLEANUP_DELAY * 50; // game client ticks
        }

        @Override
        public void run() {
            if (System.currentTimeMillis() >= runWhen) {
                finish();
            }
        }

        public void finish() {
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
                stop();
                this.state.pendingCleanup.remove(this);
            }
        }
    }

    /**
     * Provides information (at spawn) of the heat/body rotation of a Player
     */
    public static class FakePlayerPosition {
        private final double x, y, z;
        private final float yaw, pitch, headyaw;

        private FakePlayerPosition(double x, double y, double z, float yaw, float pitch, float headyaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.headyaw = headyaw;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public float getHeadYaw() { return headyaw; }

        public FakePlayerPosition atOppositePitchBoundary() {
            return create(x, y, z, yaw, Util.atOppositeRotationGlitchBoundary(pitch), headyaw);
        }

        public static FakePlayerPosition ofPlayer(Player player) {
            EntityHandle playerHandle = EntityHandle.fromBukkit(player);
            return new FakePlayerPosition(
                    playerHandle.getLocX(), playerHandle.getLocY(), playerHandle.getLocZ(),
                    playerHandle.getYaw(),
                    playerHandle.getPitch(), playerHandle.getHeadRotation());
        }

        public static FakePlayerPosition ofPlayer(double x, double y, double z, Player player) {
            EntityHandle playerHandle = EntityHandle.fromBukkit(player);
            return new FakePlayerPosition(
                    x, y, z,
                    playerHandle.getYaw(),
                    playerHandle.getPitch(), playerHandle.getHeadRotation());
        }

        public static FakePlayerPosition ofPlayerUpsideDown(double x, double y, double z, Player player) {
            EntityHandle playerHandle = EntityHandle.fromBukkit(player);
            float yaw = playerHandle.getYaw();
            return new FakePlayerPosition(
                    x, y, z,
                    yaw,
                    -playerHandle.getPitch(),
                    -playerHandle.getHeadRotation() + 2.0f * yaw);
        }

        public static FakePlayerPosition create(double x, double y, double z, float yaw, float pitch, float headyaw) {
            return new FakePlayerPosition(x, y, z, yaw, pitch, headyaw);
        }
    }
}
