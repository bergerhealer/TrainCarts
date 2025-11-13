package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PlayerGameInfo;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.surface.CollisionSurface;
import com.bergerkiller.bukkit.tc.attachments.surface.CollisionSurfaceTracker;
import com.bergerkiller.bukkit.tc.controller.player.network.PacketQueue;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerClientSynchronizer;
import com.bergerkiller.bukkit.tc.controller.player.pmc.PlayerMovementController;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicReference;

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
    private final boolean supportRelativeRotationUpdate;
    // Components
    private final PacketQueue packetQueue;
    private final PlayerClientSynchronizer playerClientSynchronizer;
    private final Object activeMovementControllerLock;
    private volatile MovementControllerTicket activeMovementController;
    CollisionSurfaceTracker collisionSurfaceTracker;

    TrainCartsAttachmentViewer(TrainCarts plugin, Player player, PlayerGameInfo playerGameInfo, PacketQueue packetQueue) {
        this.plugin = plugin;
        this.player = player;
        this.vmc = PlayerUtil.getVehicleMountController(player);
        this.playerGameInfo = playerGameInfo;
        this.armorStandButtOffset = AttachmentViewer.super.getArmorStandButtOffset();
        this.supportsDisplayEntityLocationInterpolation = AttachmentViewer.super.supportsDisplayEntityLocationInterpolation();
        this.supportsDisplayEntities = AttachmentViewer.super.supportsDisplayEntities();
        this.supportRelativeRotationUpdate = AttachmentViewer.super.supportRelativeRotationUpdate();
        this.packetQueue = packetQueue;
        this.playerClientSynchronizer = plugin.getPlayerClientSynchronizerProvider().forViewer(this);
        this.activeMovementControllerLock = new MovementControllerTicket();
        this.activeMovementController = new MovementControllerTicket();
        this.collisionSurfaceTracker = null;
    }

    PacketQueue getPacketQueue() {
        return packetQueue;
    }

    @Override
    public PlayerClientSynchronizer getClientSynchronizer() {
        return playerClientSynchronizer;
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
    public boolean supportRelativeRotationUpdate() {
        return supportRelativeRotationUpdate;
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
    public MovementController controlMovement(MovementController.Options options) {
        synchronized (activeMovementControllerLock) {
            MovementController prev = activeMovementController.controller.getAndSet(MovementController.DISABLED);

            MovementControllerTicket ticket = new MovementControllerTicket();
            PlayerMovementController controller;
            if (prev instanceof PlayerMovementController) {
                controller = (PlayerMovementController) prev;
            } else if (isConnected()) {
                controller = PlayerMovementController.ControllerType.forViewer(this).create(this);
            } else {
                return MovementController.DISABLED;
            }
            controller.setOptions(options);
            ticket.controller.set(controller);
            activeMovementController = ticket;
            return ticket;
        }
    }

    @Override
    public void stopControllingMovement() {
        final MovementController prev;
        synchronized (activeMovementControllerLock) {
            prev = activeMovementController.controller.getAndSet(MovementController.DISABLED);
        }
        prev.stop();
    }

    @Override
    public CollisionSurface createCollisionSurface() {
        CollisionSurfaceTracker collisionSurfaceTracker = this.collisionSurfaceTracker;
        if (collisionSurfaceTracker == null) {
            if (!isConnected()) {
                return CollisionSurface.DISABLED;
            }
            this.collisionSurfaceTracker = collisionSurfaceTracker = new CollisionSurfaceTracker(this);
        }
        return collisionSurfaceTracker.createSurface();
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

    /**
     * Wraps the actual MovementController to represent a single control session.
     * Further calls to control movement will disable previous ones.
     */
    private static final class MovementControllerTicket implements MovementController {
        private final AtomicReference<MovementController> controller = new AtomicReference<>(MovementController.DISABLED);

        @Override
        public void stop() {
            MovementController oldController = controller.getAndSet(MovementController.DISABLED);
            oldController.stop();
        }

        @Override
        public boolean hasStopped() {
            return controller.get().hasStopped();
        }

        @Override
        public Input getInput() {
            return controller.get().getInput();
        }

        @Override
        public void update(Vector position, Quaternion orientation) {
            controller.get().update(position, orientation);
        }
    }
}
