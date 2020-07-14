package com.bergerkiller.bukkit.tc.attachments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.control.seat.SeatOrientation;
import com.bergerkiller.generated.com.mojang.authlib.GameProfileHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutScoreboardTeamHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle.EnumPlayerInfoActionHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle.PlayerInfoDataHandle;

/**
 * Helper class to change the name of a Player.
 * This is used to switch players between upside-down and normal display modes.
 */
public enum ProfileNameModifier {
    NORMAL(null, null),
    NO_NAMETAG("DinnerBone", "BoredTCRiders"),
    UPSIDEDOWN("Dinnerbone", "DizzyTCRiders");

    // Map<PlayerUUID, Map<ViewerUUID, Name>>
    // We need this to remove the previous entry from the tab list
    // TODO: We should listen for packets to automatically obtain this!
    // Right now external plugins can break it! Duplicate entries may occur.
    private static final Map<UUID, Map<UUID, String>> _lastSetNames = new HashMap<UUID, Map<UUID, String>>();

    private final String _playerName;
    private final ChatText _teamName;
    private final Set<UUID> _teamSentPlayers = new HashSet<UUID>();

    private ProfileNameModifier(String playerName, String teamName) {
        this._playerName = playerName;
        this._teamName = ChatText.fromMessage(teamName);
    }

    public ChatText getPlayerName() {
        return ChatText.fromMessage(this._playerName);
    }

    /**
     * (Re)spawns the player for a viewer with this profile name modifier applied
     * 
     * @param viewer
     * @param player
     * @param entityId for the spawned player entity
     */
    public void spawnPlayer(Player viewer, Player player, int entityId, boolean fakeFlipPitch, SeatOrientation orientation, Consumer<DataWatcher> metaFunction) {
        // Send list info before spawning
        this.sendListInfo(viewer, player);

        EntityHandle playerHandle = EntityHandle.fromBukkit(player);

        // Calculate yaw/pitch/head-yaw
        float yaw, pitch, headRot;
        if (orientation == null) {
            yaw = playerHandle.getYaw();
            pitch = playerHandle.getPitch();
            headRot = playerHandle.getHeadRotation();
            if (this == UPSIDEDOWN) {
                pitch = -pitch;
                headRot = -headRot + 2.0f * yaw;
            }
        } else {
            yaw = orientation.getPassengerYaw();
            pitch = orientation.getPassengerPitch();
            headRot = orientation.getPassengerHeadYaw();
        }

        // If fake pitch flip is used, then make the pitch the opposite, right at the edge
        if (fakeFlipPitch) {
            if (pitch >= 180.0f) {
                pitch = 179.0f;
            } else {
                pitch = 181.0f;
            }
        }

        // Spawn in a fake player with the same UUID
        // Thanks to the earlier profile name change, the player will spawn upside-down
        PacketPlayOutNamedEntitySpawnHandle fakePlayerSpawnPacket = PacketPlayOutNamedEntitySpawnHandle.T.newHandleNull();
        fakePlayerSpawnPacket.setEntityId(entityId);
        fakePlayerSpawnPacket.setPosX(playerHandle.getLocX());
        fakePlayerSpawnPacket.setPosY(playerHandle.getLocY());
        fakePlayerSpawnPacket.setPosZ(playerHandle.getLocZ());
        fakePlayerSpawnPacket.setYaw(yaw);
        fakePlayerSpawnPacket.setPitch(pitch);
        fakePlayerSpawnPacket.setEntityUUID(player.getUniqueId());

        // Copy data watcher data from the original player
        // Make the spawned player invisible initially
        // This reduces the glitchy effects before the player is mounted
        DataWatcher metaData = EntityUtil.getDataWatcher(player).clone();
        //setMetaVisibility(metaData, false);
        metaFunction.accept(metaData);
        PacketUtil.sendNamedEntitySpawnPacket(viewer, fakePlayerSpawnPacket, metaData);

        // Also synchronize the head rotation for this player
        CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
        headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, entityId);
        headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, headRot);
        PacketUtil.sendPacket(viewer, headPacket);
    }
    
    /**
     * Resends the player profile to the viewer
     * 
     * @param viewer
     * @param player
     */
    public void sendListInfo(Player viewer, Player player) {
        // Get previous name set for the player to the viewer
        Map<UUID, String> lastNames = getLastNames(player);
        String oldName = lastNames.get(viewer.getUniqueId());
        if (oldName == null) {
            oldName = player.getName();
        }

        // Get the new name that should be set
        String newName = this._playerName;
        if (newName == null) {
            newName = player.getName();
        }

        // If name did not change, do nothing
        if (oldName.equals(newName)) {
            return;
        }

        // Remove tab view entry of the old player name
        GameProfileHandle oldFakeGameProfile = GameProfileHandle.createNew(player.getUniqueId(), oldName);
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
        PacketUtil.sendPacket(viewer, oldInfoPacket);

        // Add tab view entry of the new player name
        GameProfileHandle newFakeGameProfile = GameProfileHandle.createNew(player.getUniqueId(), newName);
        newFakeGameProfile.setAllProperties(GameProfileHandle.getForPlayer(player));
        PacketPlayOutPlayerInfoHandle newInfoPacket = PacketPlayOutPlayerInfoHandle.createNew();
        newInfoPacket.setAction(EnumPlayerInfoActionHandle.ADD_PLAYER);
        PlayerInfoDataHandle playerInfo = PlayerInfoDataHandle.createNew(
                newInfoPacket,
                newFakeGameProfile,
                50,
                GameMode.CREATIVE,
                ChatText.fromMessage(player.getPlayerListName())
        );
        newInfoPacket.getPlayers().add(playerInfo);
        PacketUtil.sendPacket(viewer, newInfoPacket);

        // Store the new name in the mapping
        lastNames.put(viewer.getUniqueId(), newName);

        // Send scoreboard team to hide the nametag
        // Only do this once to prevent a client disconnect (duplicate team)
        if (this._teamName != null && this._teamSentPlayers.add(viewer.getUniqueId())) {
            PacketPlayOutScoreboardTeamHandle teamPacket = PacketPlayOutScoreboardTeamHandle.T.newHandleNull();
            teamPacket.setName(this._teamName.getMessage());
            teamPacket.setDisplayName(this._teamName);
            teamPacket.setPrefix(ChatText.fromMessage(""));
            teamPacket.setSuffix(ChatText.fromMessage(""));
            teamPacket.setVisibility("never");
            teamPacket.setCollisionRule("never");
            teamPacket.setMode(0x0);
            teamPacket.setFriendlyFire(0x3);
            teamPacket.setPlayers(new ArrayList<String>(Collections.singleton(this._playerName)));
            teamPacket.setColor(ChatColor.RESET);
            PacketUtil.sendPacket(viewer, teamPacket);
        }
    }

    private final Map<UUID, String> getLastNames(Player player) {
        Map<UUID, String> result = _lastSetNames.get(player.getUniqueId());
        if (result == null) {
            result = new HashMap<UUID, String>(1);
            _lastSetNames.put(player.getUniqueId(), result);
        }
        return result;
    }

    /**
     * Called when a viewer leaves the server, and sent team/player list information is presumed reset
     * 
     * @param viewer
     */
    public static void onViewerQuit(Player viewer) {
        _lastSetNames.remove(viewer.getUniqueId());
        for (ProfileNameModifier modifier : values()) {
            modifier._teamSentPlayers.remove(viewer.getUniqueId());
        }
    }
}
