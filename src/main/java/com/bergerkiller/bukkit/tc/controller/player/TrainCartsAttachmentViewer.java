package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.controller.VehicleMountController;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.protocol.PlayerGameInfo;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.surface.CollisionSurface;
import com.bergerkiller.bukkit.tc.attachments.surface.CollisionSurfaceTracker;
import com.bergerkiller.bukkit.tc.attachments.surface.StationaryCollisionElement;
import com.bergerkiller.bukkit.tc.controller.player.network.PacketQueue;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerClientSynchronizer;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerPacketListener;
import com.bergerkiller.bukkit.tc.controller.player.pmc.PlayerMovementController;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    private final Object viewRotationTrackingLock;
    private volatile ViewRotationTrackerImpl activeViewRotationTracker;
    private volatile ViewRotationTrackerSessionImpl activeViewRotationTrackerSession;
    private volatile PlayerPacketListener<ViewRotationPacketListener> viewRotationPacketListener;
    private final Object spectatedFakePlayerLock;
    private SpectatedFakePlayer spectatedFakePlayerActive;
    private volatile SpectatedFakePlayer spectatedFakePlayerClientActive;

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
        this.viewRotationTrackingLock = new Object();
        this.activeViewRotationTracker = null;
        this.activeViewRotationTrackerSession = null;
        this.viewRotationPacketListener = null;
        this.spectatedFakePlayerLock = new Object();
        this.spectatedFakePlayerActive = null;
        this.spectatedFakePlayerClientActive = null;
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
            if (prev instanceof PlayerMovementController && !prev.hasStopped()) {
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
    public CollisionSurface createCollisionSurface(int viewRange) {
        CollisionSurfaceTracker collisionSurfaceTracker = this.collisionSurfaceTracker;
        if (collisionSurfaceTracker == null) {
            if (!isConnected()) {
                return CollisionSurface.DISABLED;
            }
            this.collisionSurfaceTracker = collisionSurfaceTracker = new CollisionSurfaceTracker(this, viewRange);
        }
        return collisionSurfaceTracker.createSurface();
    }

    @Override
    public void forAllStationaryCollisionElements(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Consumer<StationaryCollisionElement> action) {
        CollisionSurfaceTracker collisionSurfaceTracker = this.collisionSurfaceTracker;
        if (collisionSurfaceTracker != null) {
            collisionSurfaceTracker.forAllStationaryElements(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    action
            );
        }
    }

    @Override
    public SpectatedFakePlayer createSpectatedFakePlayer() {
        return new SpectatedFakePlayer(this);
    }

    @Override
    public SpectatedFakePlayer createSpectatedFakePlayer(com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager manager) {
        return new SpectatedFakePlayer(this, manager);
    }

    @Override
    public SpectatedFakePlayerHead createSpectatedFakePlayerHead() {
        return new SpectatedFakePlayerHead(this);
    }

    @Override
    public SpectatedFakePlayerHead createSpectatedFakePlayerHead(com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager manager) {
        return new SpectatedFakePlayerHead(this, manager);
    }

    public SpectatedFakePlayer getClientActiveSpectatedFakePlayer() {
        return this.spectatedFakePlayerClientActive;
    }

    @Override
    public ViewRotationTracker startViewRotationTracking() {
        synchronized (viewRotationTrackingLock) {
            ViewRotationTrackerImpl tracker = activeViewRotationTracker;
            if (tracker != null && !tracker.requestUse()) {
                tracker = null;
            }
            if (tracker == null) {
                tracker = new ViewRotationTrackerImpl(this, new ViewRotationTrackerImpl.TrackerControl() {
                    @Override
                    public void enable(ViewRotationTrackerImpl tracker) {
                        enableViewRotationTracking(tracker);
                    }

                    @Override
                    public void stop(ViewRotationTrackerImpl tracker) {
                        stopViewRotationTracking(tracker);
                    }
                });
            }
            activeViewRotationTracker = tracker;
            ViewRotationTrackerSessionImpl session = new ViewRotationTrackerSessionImpl(tracker);
            activeViewRotationTrackerSession = session;
            tracker.setSession(session);
            if (viewRotationPacketListener == null) {
                viewRotationPacketListener = createPacketListener(new ViewRotationPacketListener(),
                        PacketType.IN_POSITION_LOOK, PacketType.IN_POSITION, PacketType.IN_LOOK);
            }
            return session;
        }
    }

    void onSpectatedFakePlayerCreated(SpectatedFakePlayer fakePlayer) {
        final SpectatedFakePlayer previous;
        synchronized (this.spectatedFakePlayerLock) {
            previous = (this.spectatedFakePlayerActive != fakePlayer) ? this.spectatedFakePlayerActive : null;
        }
        if (previous != null) {
            previous.stop();
        }
        synchronized (this.spectatedFakePlayerLock) {
            this.spectatedFakePlayerActive = fakePlayer;
        }
    }

    void onSpectatedFakePlayerActive(SpectatedFakePlayer fakePlayer, boolean active) {
        SpectatedFakePlayer previous = null;
        boolean scheduleClientActiveClear = false;
        synchronized (this.spectatedFakePlayerLock) {
            if (active) {
                previous = (this.spectatedFakePlayerActive != fakePlayer) ? this.spectatedFakePlayerActive : null;
                this.spectatedFakePlayerActive = fakePlayer;
                this.spectatedFakePlayerClientActive = fakePlayer;
            } else if (this.spectatedFakePlayerActive == fakePlayer) {
                this.spectatedFakePlayerActive = null;
                scheduleClientActiveClear = (this.spectatedFakePlayerClientActive == fakePlayer);
            }
        }

        if (previous != null) {
            previous.stop();
        }
        if (scheduleClientActiveClear) {
            this.getClientSynchronizer().synchronize(() -> {
                synchronized (this.spectatedFakePlayerLock) {
                    if (this.spectatedFakePlayerClientActive == fakePlayer) {
                        this.spectatedFakePlayerClientActive = null;
                    }
                }
            });
        }
    }

    void stopTrackedSpectatedFakePlayers() {
        final SpectatedFakePlayer activePlayer;
        synchronized (this.spectatedFakePlayerLock) {
            activePlayer = this.spectatedFakePlayerActive;
            this.spectatedFakePlayerActive = null;
            this.spectatedFakePlayerClientActive = null;
        }

        if (activePlayer != null) {
            activePlayer.stop();
        }
    }

    void stopViewRotationTracking() {
        synchronized (viewRotationTrackingLock) {
            if (activeViewRotationTracker != null) {
                activeViewRotationTracker.markStoppedByReplacement();
                activeViewRotationTracker = null;
                activeViewRotationTrackerSession = null;
            }
            if (viewRotationPacketListener != null) {
                viewRotationPacketListener.disable();
            }
        }
    }

    private void enableViewRotationTracking(ViewRotationTrackerImpl tracker) {
        synchronized (viewRotationTrackingLock) {
            if (tracker != activeViewRotationTracker) {
                return;
            }

            PlayerPacketListener<?> listener = viewRotationPacketListener;
            if (listener != null) {
                listener.enable();
            }
        }
    }

    private void stopViewRotationTracking(ViewRotationTrackerImpl tracker) {
        synchronized (viewRotationTrackingLock) {
            if (tracker != activeViewRotationTracker) {
                return;
            }
            activeViewRotationTracker = null;
            activeViewRotationTrackerSession = null;

            PlayerPacketListener<?> listener = viewRotationPacketListener;
            if (listener != null) {
                listener.disable();
            }
        }
    }

    private final class ViewRotationPacketListener implements PacketListener {
        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            ViewRotationTrackerImpl tracker = activeViewRotationTracker;
            if (tracker != null) {
                tracker.onPacketReceive(event);
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
        }
    }

    /**
     * Session-specific handle for a shared tracker core. Once a newer handle is created,
     * this old handle becomes a no-op so concurrent systems cannot interfere with each other.
     */
    private final class ViewRotationTrackerSessionImpl implements ViewRotationTracker, ViewRotationTrackerImpl.RotationSession {
        private final ViewRotationTrackerImpl delegate;
        private final Object stateLock = new Object();
        private Rotation pendingRotation = Rotation.ZERO;
        private Rotation lastRotation = null;

        ViewRotationTrackerSessionImpl(ViewRotationTrackerImpl delegate) {
            this.delegate = delegate;
        }

        private boolean isActiveSession() {
            return this == activeViewRotationTrackerSession && delegate == activeViewRotationTracker;
        }

        @Override
        public void setUsePitchAdjustment(boolean usePitchAdjustment) {
            if (isActiveSession()) {
                delegate.setUsePitchAdjustment(usePitchAdjustment);
            }
        }

        @Override
        public void setRotation(Rotation rotation) {
            if (isActiveSession()) {
                delegate.setRotation(rotation);
            }
        }

        @Override
        public void enable() {
            if (isActiveSession()) {
                delegate.enable();
            }
        }

        @Override
        public void stop() {
            if (isActiveSession()) {
                delegate.stop();
            }
        }

        @Override
        public Rotation readAndResetRotationChange() {
            if (!isActiveSession()) {
                return Rotation.ZERO;
            }
            synchronized (stateLock) {
                Rotation result = pendingRotation;
                pendingRotation = Rotation.ZERO;
                return result;
            }
        }

        @Override
        public Rotation getLastRotation() {
            if (!isActiveSession()) {
                return null;
            }
            synchronized (stateLock) {
                return lastRotation;
            }
        }

        @Override
        public void pushRotation(Rotation newRotation, Rotation changes) {
            if (!isActiveSession()) {
                return;
            }

            synchronized (stateLock) {
                if (pendingRotation == null) {
                    pendingRotation = changes;
                } else {
                    pendingRotation = Rotation.add(pendingRotation, changes);
                }
                lastRotation = newRotation;
            }
        }
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
        public boolean update(Vector position, Quaternion orientation, boolean stopOnCollision) {
            return controller.get().update(position, orientation, stopOnCollision);
        }

    }
}
