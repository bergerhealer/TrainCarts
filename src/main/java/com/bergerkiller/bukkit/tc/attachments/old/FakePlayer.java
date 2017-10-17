package com.bergerkiller.bukkit.tc.attachments.old;

import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.com.mojang.authlib.GameProfileHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle.EnumPlayerInfoActionHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle.PlayerInfoDataHandle;

/**
 * Represents a fake player mounted in a Minecart
 */
public class FakePlayer {
    public int entityId = -1;
    public int lastYaw = Integer.MAX_VALUE;
    public int lastPitch = Integer.MAX_VALUE;
    public int lastHeadRot = Integer.MAX_VALUE;
    public boolean wasInvisible = false;
    private final HashMap<UUID, DisplayMode> viewerModes = new HashMap<UUID, DisplayMode>();

    public FakePlayer() {
        this.entityId = EntityUtil.getUniqueEntityId();
    }

    public DisplayMode getMode(Player viewer) {
        DisplayMode mode = viewerModes.get(viewer.getUniqueId());
        return (mode == null) ? DisplayMode.NONE : mode;
    }

    public void setMode(Player viewer, Player player, DisplayMode newMode) {
        DisplayMode oldMode = getMode(viewer);
        if (oldMode.equals(newMode)) {
            return;
        }

        String oldName = (oldMode == DisplayMode.NONE) ? player.getName() : oldMode.getPlayerName();
        String newName = (newMode == DisplayMode.NONE) ? player.getName() : newMode.getPlayerName();

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

        if (newMode != DisplayMode.NONE) {
            newMode.getTeam().send(viewer);
        }

        if (newMode == DisplayMode.NONE) {
            viewerModes.remove(viewer.getUniqueId());
        } else {
            viewerModes.put(viewer.getUniqueId(), newMode);
        }
    }

    public void destroy(Player viewer, Player player) {
        // Destroy the fake player
        PacketPlayOutEntityDestroyHandle destroyPacket = PacketPlayOutEntityDestroyHandle.createNew(new int[] {this.entityId});
        PacketUtil.sendPacket(viewer, destroyPacket);

        // Restore player in player list
        this.setMode(viewer, player, DisplayMode.NONE);
    }

    public void spawn(Player viewer, Player player) {
        Location pos = player.getLocation();

        // Create a named entity spawn packet
        PacketPlayOutNamedEntitySpawnHandle fakePlayerSpawnPacket = PacketPlayOutNamedEntitySpawnHandle.T.newHandleNull();
        fakePlayerSpawnPacket.setEntityId(this.entityId);
        fakePlayerSpawnPacket.setPosX(pos.getX());
        fakePlayerSpawnPacket.setPosY(pos.getY());
        fakePlayerSpawnPacket.setPosZ(pos.getZ());
        fakePlayerSpawnPacket.setYaw(pos.getYaw());
        fakePlayerSpawnPacket.setPitch(pos.getPitch());
        fakePlayerSpawnPacket.setEntityUUID(player.getUniqueId());

        // Copy data watcher data from the original player
        // Make the spawned player invisible initially
        DataWatcher metaData = EntityUtil.getDataWatcher(player).clone();
        setMetaVisibility(metaData, false);
        fakePlayerSpawnPacket.setDataWatcher(metaData);

        // Finally send the packet
        PacketUtil.sendPacket(viewer, fakePlayerSpawnPacket);
        wasInvisible = true;
    }

    public void syncRotation(Collection<Player> viewers, float yaw, float pitch, float headRot) {
        // Protocolify
        int protYaw = EntityTrackerEntryHandle.getProtocolRotation(yaw);
        int protPitch = EntityTrackerEntryHandle.getProtocolRotation(pitch);
        int protHeadRot = EntityTrackerEntryHandle.getProtocolRotation(headRot);

        if (protYaw != this.lastYaw || protPitch != this.lastPitch) {
            CommonPacket lookPacket = PacketType.OUT_ENTITY_LOOK.newInstance();
            lookPacket.write(PacketType.OUT_ENTITY_LOOK.entityId, this.entityId);
            lookPacket.write(PacketPlayOutEntityHandle.T.dyaw_raw.toFieldAccessor(), (byte) protYaw);
            lookPacket.write(PacketPlayOutEntityHandle.T.dpitch_raw.toFieldAccessor(), (byte) protPitch);
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, lookPacket);
            }
            this.lastYaw = protYaw;
            this.lastPitch = protPitch;
        }

        if (protHeadRot != this.lastHeadRot) {
            CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
            headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, this.entityId);
            headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, (byte) protHeadRot);
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, headPacket);
            }
            this.lastHeadRot = protHeadRot;
        }
    }

    public static void setMetaVisibility(DataWatcher metaData, boolean visible) {
        int value = metaData.get(EntityHandle.DATA_FLAGS);
        if (visible) {
            value &= ~EntityHandle.DATA_FLAG_INVISIBLE;
        } else {
            value |= EntityHandle.DATA_FLAG_INVISIBLE;
        }
        metaData.set(EntityHandle.DATA_FLAGS, (byte) value);
    }

    public static enum DisplayMode {
        NONE("", ""), NORMAL("DinnerBone", "BoredTCRiders"), UPSIDEDOWN("Dinnerbone", "DizzyTCRiders");

        private final String playerName;
        private final FakeTeam team;

        private DisplayMode(String playerName, String teamName) {
            this.playerName = playerName;
            this.team = new FakeTeam(teamName, playerName);
        }

        public String getPlayerName() {
            return this.playerName;
        }

        public FakeTeam getTeam() {
            return this.team;
        }
    }
}
