package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PlayerGameInfo;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.controller.global.PacketQueue;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import org.bukkit.entity.Player;

/**
 * Implementation of {@link AttachmentViewer} by TrainCarts itself.
 * Has optimizations for various APIs.<br>
 * <br>
 * The TrainCartsAttachmentViewer can be safely used as a key in hashmaps / hashsets.
 * Two viewer instances for the same player will be considered equal.
 */
public final class TrainCartsAttachmentViewer implements AttachmentViewer {
    private final TrainCarts plugin;
    private final Player player;
    private final VehicleMountController vmc; // cached
    private final PlayerGameInfo playerGameInfo;
    private final double armorStandButtOffset;
    private final boolean supportsDisplayEntityLocationInterpolation;
    private final boolean supportsDisplayEntities;
    // Components
    private final PacketQueue packetQueue;

    TrainCartsAttachmentViewer(TrainCarts plugin, Player player, PlayerGameInfo playerGameInfo, PacketQueue packetQueue) {
        this.plugin = plugin;
        this.player = player;
        this.vmc = PlayerUtil.getVehicleMountController(player);
        this.playerGameInfo = playerGameInfo;
        this.armorStandButtOffset = AttachmentViewer.super.getArmorStandButtOffset();
        this.supportsDisplayEntityLocationInterpolation = CommonCapabilities.HAS_DISPLAY_ENTITY_LOCATION_INTERPOLATION
                && playerGameInfo.evaluateVersion(">=", "1.20.2");
        this.supportsDisplayEntities = CommonCapabilities.HAS_DISPLAY_ENTITY
                && evaluateGameVersion(">=", "1.19.4");
        this.packetQueue = packetQueue;
    }

    PacketQueue getPacketQueue() {
        return packetQueue;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return plugin;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean evaluateGameVersion(String operand, String rightSide) {
        return playerGameInfo.evaluateVersion(operand, rightSide);
    }

    @Override
    public boolean supportsDisplayEntityLocationInterpolation() {
        return supportsDisplayEntityLocationInterpolation;
    }

    @Override
    public boolean supportsDisplayEntities() {
        return supportsDisplayEntities;
    }

    @Override
    public double getArmorStandButtOffset() {
        return armorStandButtOffset;
    }

    @Override
    public VehicleMountController getVehicleMountController() {
        return vmc;
    }

    @Override
    public me.m56738.smoothcoasters.api.NetworkInterface getSmoothCoastersNetwork() {
        return packetQueue;
    }

    @Override
    public void send(PacketHandle packet) {
        getPacketQueue().send(packet);
    }

    @Override
    public void send(CommonPacket packet) {
        getPacketQueue().send(packet);
    }

    @Override
    public void sendSilent(CommonPacket packet) {
        getPacketQueue().sendSilent(packet);
    }

    @Override
    public void sendSilent(PacketHandle packet) {
        getPacketQueue().sendSilent(packet);
    }

    @Override
    public int hashCode() {
        return player.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof AttachmentViewer) {
            return this.player == ((AttachmentViewer) o).getPlayer();
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "TCAttachmentViewer{player=" + player.getName() + "}";
    }
}
