package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer.MovementController.OrientationMode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentPlatform;
import com.bergerkiller.bukkit.tc.controller.player.network.PlayerPacketListener;
import com.bergerkiller.bukkit.tc.controller.player.pmc.PlayerMovementController;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerPositionPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundMovePlayerPacketHandle;
import com.bergerkiller.generated.net.minecraft.server.level.ServerPlayerHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Keeps track of the collision surface created to represent various
 * {@link CollisionSurface} instances. This tracker is per player.<br>
 * <br>
 * For moving surfaces, keeps players on these surfaces using the player movement
 * controller. For non-moving surfaces, spawns shulkers so the player can walk on them/
 * can't walk through walls. There is also some logic for the interaction between
 * the two (jumping from surface onto shulkers).
 */
public class CollisionSurfaceTracker {
    /** How many ticks of no movement until a surface is considered "not moving" */
    private static final int TICKS_UNTIL_NOT_MOVING = 2;
    /** Maximum steepness (degrees) at which player input is still respected. Beyond this angle input is disabled. */
    private static final double MAX_INPUT_ANGLE = 80.0;
    /** Angle (degrees) at which gravity-based sliding starts scaling from 0 (at this angle) to full at 90 degrees. */
    private static final double GRAVITY_START_ANGLE = 50.0;
    /** Downward distance checked after a player dismounts to immediately pick up nearby surface control. */
    private static final double DISMOUNT_SURFACE_SUPPORT_DISTANCE = 1.0;
    /**
     * Maximum distance below the player's feet checked to immediately enter simulated mode
     * when transitioning from shulker-grid collision onto a moving/simulated surface.
     */
    private static final double SHULKER_HANDOFF_SURFACE_SUPPORT_DISTANCE = 1.0;
    /** Maximum per-tick downward movement to keep representing a surface with shulkers */
    private static final double SHULKER_MAX_DOWNWARD_SPEED = 0.0625;
    private static final boolean HAS_ENTITY_PUSHABLE_BY = Common.hasCapability("Common:EntityUtil:isPushableBy");
    private static final boolean HAS_PLAYER_COLLIDABLE = Common.evaluateMCVersion(">=", "1.9");
    /** When true, pressing sneak forces surface collisions off. Useful for testing. */
    private static final boolean SNEAKING_DISABLES_SURFACE_COLLISIONS = false;
    /** Whether to log when the player bounds penetrate surfaces unexpectedly (solver bugs) */
    private static final boolean VERIFY_LOG_PENETRATIONS = false;
    /** Distance within which packet-monitoring for moving surfaces is active */
    private static final double MOVING_SURFACE_MONITOR_RANGE = 10.0;
    private static final double MOVING_SURFACE_MONITOR_RANGE_SQUARED = MOVING_SURFACE_MONITOR_RANGE * MOVING_SURFACE_MONITOR_RANGE;
    /** Number of ticks after which no incoming position packets is treated as stale state */
    private static final int MOVING_SURFACE_PACKET_TIMEOUT_TICKS = 10;
    /** Maximum allowed packet-derived movement length before state is reset */
    private static final double MOVING_SURFACE_MAX_PACKET_DELTA = 3.0;
    private static final double MOVING_SURFACE_MAX_PACKET_DELTA_SQUARED =
            MOVING_SURFACE_MAX_PACKET_DELTA * MOVING_SURFACE_MAX_PACKET_DELTA;
    private static final int MAX_BLOCK_COLLISION_RESIMULATION_ATTEMPTS = 8;
    private static final int BLOCK_HANDOFF_RECAPTURE_COOLDOWN_TICKS = 6;
    private final AttachmentViewer viewer;
    private final int shulkerViewDistance;
    private final PlayerPusher playerPusher;
    private final ShulkerTracker shulkerCache;
    private final CollisionFloorTileGrid floorTiles;
    private final EnumMap<BlockFace, CollisionWallTileGrid> wallTiles;
    private final List<CollisionSurfaceImpl> surfaces = new ArrayList<>();
    private SimulatedPlayer simulatedPlayer = null;
    private int simulatedPlayerStartCooldownTicks = 0;
    private Boolean previousPlayerSeated = null;
    private Vector previousPlayerPosition = null;
    private final StepSoundTracker stepSoundTracker = new StepSoundTracker();
    private PlayerPacketListener<MovingSurfacePacketMonitor> movingSurfacePacketMonitor = null;
    private final AtomicReference<MovingSurfacePacketState> movingSurfacePacketState =
            new AtomicReference<>(MovingSurfacePacketState.reset());
    private final CollisionSurfaceLogger collisionSurfaceLogger;

    // Shared solver instance for this tracker class. Initialized with a logger that
    // routes messages to the plugin logger. Set disabled when not testing.
    private static final PlayerCollisionLogger PLAYER_COLLISION_LOGGER = PlayerCollisionLogger.DISABLED;
    private static final PlayerCollisionSolver PLAYER_COLLISION_SOLVER = new PlayerCollisionSolver(PLAYER_COLLISION_LOGGER);

    public CollisionSurfaceTracker(AttachmentViewer viewer, int shulkerViewDistance) {
        this.viewer = viewer;
        this.shulkerViewDistance = shulkerViewDistance;

        this.playerPusher = new PlayerPusher(viewer);
        this.shulkerCache = new ShulkerTracker();
        this.floorTiles = new CollisionFloorTileGrid(shulkerCache);
        this.wallTiles = new EnumMap<>(BlockFace.class);
        // Set to `new CollisionSurfaceLoggerImpl(viewer)` to enable surface tracker debug logging.
        //this.collisionSurfaceLogger = new CollisionSurfaceLoggerImpl(viewer);
        this.collisionSurfaceLogger = CollisionSurfaceLogger.DISABLED;
    }

    public CollisionSurface findSurfaceLookingAt() {
        Location fromLoc = viewer.getPlayer().getEyeLocation();
        Location toLoc = fromLoc.clone().add(fromLoc.getDirection().clone().multiply(100.0));
        AABBHandle from = AABBHandle.createNew(
                fromLoc.getX(), fromLoc.getY(), fromLoc.getZ(),
                fromLoc.getX(), fromLoc.getY(), fromLoc.getZ())
                .grow(0.1, 0.1, 0.1);
        AABBHandle to = AABBHandle.createNew(
                toLoc.getX(), toLoc.getY(), toLoc.getZ(),
                toLoc.getX(), toLoc.getY(), toLoc.getZ())
                .grow(0.1, 0.1, 0.1);

        // Compute all of the surface transitions (no movement)
        List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions = this.surfaces.stream()
                .map(s -> new OBBSurfaceTransition<>(s.shape, s.shape, s))
                .collect(Collectors.toList());

        PlayerCollisionSolver.Result<CollisionSurfaceImpl> solution = PLAYER_COLLISION_SOLVER.solveDetailed(transitions, new PlayerBoundsTransition(from, to));
        if (!solution.hasCollision()) {
            return CollisionSurface.DISABLED;
        }

        return solution.lastSurface;
    }

    public Vector getMovingSurfacePacketDelta() {
        return movingSurfacePacketState.get().delta.clone();
    }

    /**
     * Called every tick to update the display of shulkers for all surface shapes that
     * have been added. Also cleans up information of cleared collision surfaces
     * (that have not received further updates).
     */
    public void update() {
        // Update all surfaces, and if they are not moving, spawn in shulkers for them
        for (CollisionSurfaceImpl surface : surfaces) {
            surface.nextUpdate();

            if (surface.isShulkerGrid()) {
                surface.spawnOnShulkerGrid();
            }
        }

        // Compute all of the surface transitions
        List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions = this.surfaces.stream()
                .filter(CollisionSurfaceImpl::isSimulated)
                .map(s -> new OBBSurfaceTransition<>(s.prevShape, s.shape, s))
                .collect(Collectors.toList());

        ServerPlayerHandle playerHandle = ServerPlayerHandle.fromBukkit(viewer.getPlayer());
        AABBHandle bboxTo = playerHandle.getBoundingBox();
        boolean isSeated = viewer.getPlayer().getVehicle() != null;
        boolean wasSeated = previousPlayerSeated != null && previousPlayerSeated;
        previousPlayerSeated = isSeated;

        // Compute the Player OBB transition
        Vector position = viewer.getPlayer().getLocation().toVector();
        if (previousPlayerPosition == null) {
            previousPlayerPosition = position.clone();
        }
        Vector previousPosition = previousPlayerPosition.clone();
        Vector movement = position.clone().subtract(previousPlayerPosition);
        AABBHandle bboxFrom = AABBHandle.createNew(
                bboxTo.getMinX() - movement.getX(),
                bboxTo.getMinY() - movement.getY(),
                bboxTo.getMinZ() - movement.getZ(),
                bboxTo.getMaxX() - movement.getX(),
                bboxTo.getMaxY() - movement.getY(),
                bboxTo.getMaxZ() - movement.getZ()
        );
        PlayerBoundsTransition playerTransition = new PlayerBoundsTransition(bboxFrom, bboxTo);
        MathUtil.setVector(previousPlayerPosition, position);

        if (simulatedPlayerStartCooldownTicks > 0) {
            simulatedPlayerStartCooldownTicks--;
        }
        if (viewer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            if (simulatedPlayer != null) {
                stopSimulatedPlayer(null, false);
            }
            disableMovingSurfacePacketMonitor();
            return;
        }
        if (simulatedPlayer == null
                && simulatedPlayerStartCooldownTicks <= 0
                && !(SNEAKING_DISABLES_SURFACE_COLLISIONS && viewer.getPlayer().isSneaking())) {
            boolean started = false;
            if (wasSeated && !isSeated) {
                started = tryStartSimulatedPlayerAfterDismount(transitions, new PlayerBoundsState(bboxTo), previousPosition, movement);
            }
            if (!started && !isSeated) {
                started = tryStartSimulatedPlayerOnSupport(transitions, new PlayerBoundsState(bboxTo), previousPosition, movement);
            }
            if (started) {
                surfaces.forEach(CollisionSurfaceImpl::savePrevShape);
                disableMovingSurfacePacketMonitor();
                return;
            }
        }

        // This actually spawns/moves/destroys the shulkers applied in the previous step
        floorTiles.update();
        wallTiles.values().removeIf(wallTiles -> {
            wallTiles.update();
            return wallTiles.isEmpty();
        });
        shulkerCache.update(viewer, playerPusher);

        // If the surface the player was walking on turned into shulkers, exit simulation mode immediately
        if (simulatedPlayer != null && simulatedPlayer.lastSurface != null && !simulatedPlayer.lastSurface.isSimulated()) {
            stopSimulatedPlayer(simulatedPlayer.position);
        }

        surfaces.forEach(CollisionSurfaceImpl::savePrevShape);

        if (simulatedPlayer != null) {
            if (SNEAKING_DISABLES_SURFACE_COLLISIONS && viewer.getPlayer().isSneaking()) {
                stopSimulatedPlayer(viewer.getPlayer().getLocation().toVector());
                return;
            }
            updateSimulatedPlayer(transitions, new PlayerBoundsState(bboxTo));
            disableMovingSurfacePacketMonitor();
            return;
        }
        updateMovingSurfacePacketMonitor(position);

        // Sneak debug mode, pretend surfaces aren't there so player can be freed from the surfaces.
        if (SNEAKING_DISABLES_SURFACE_COLLISIONS && viewer.getPlayer().isSneaking()) {
            return;
        }

        PlayerCollisionSolver.Result<CollisionSurfaceImpl> solution = PLAYER_COLLISION_SOLVER.solveDetailed(
                transitions,
                playerTransition
        );

        if (!solution.state.equals(playerTransition.to)) {
            Vector solPos = new Vector(
                    (solution.state.bounds.getMinX() + solution.state.bounds.getMaxX()) / 2.0,
                    solution.state.bounds.getMinY(),
                    (solution.state.bounds.getMinZ() + solution.state.bounds.getMaxZ()) / 2.0);

            if (solution.lastCollisionMode == PlayerCollisionSolver.CollisionMode.FEET
                    && solution.lastSurface != null
                    && startSimulatedPlayer(solution.lastSurface, previousPosition, solPos, movement)) {
                return;
            }

            previousPlayerPosition.setX(solPos.getX());
            previousPlayerPosition.setY(solPos.getY());
            previousPlayerPosition.setZ(solPos.getZ());

            Vector correctedVelocity = removeIntoSurfaceVelocity(
                    viewer.getPlayer().getVelocity(),
                    solution.lastCollisionMode,
                    solution.lastSurfaceToState
            );
            viewer.getPlayer().setVelocity(correctedVelocity);

            ClientboundPlayerPositionPacketHandle packet = ClientboundPlayerPositionPacketHandle.createNew(
                    solPos.getX(), solPos.getY(), solPos.getZ(), 0.0f, 0.0f,
                    correctedVelocity.getX(), correctedVelocity.getY(), correctedVelocity.getZ(),
                    RelativeFlags.ABSOLUTE_POSITION.withRelativeRotation().withAbsoluteDelta());
            viewer.send(packet);
        }

        // While walking on top of a surface, keep track of the amount of distance the player moved horizontally
        // This is only for playing sounds on the shulker floor tile grid. There's separate tracking in simulation mode.
        if (viewer.getPlayer().isFlying()) {
            // No sound: player is either flying or is already walking on actual blocks (double step sound!)
            stepSoundTracker.noStep();
        } else {
            Vector pos = viewer.getPlayer().getLocation().toVector();
            CollisionSurfaceMaterial shulkerStep = floorTiles.getWalkingOn(pos.getX(), pos.getY(), pos.getZ());
            if (shulkerStep == null) {
                stepSoundTracker.noStep();
            } else if (stepSoundTracker.step(pos) && !viewer.getPlayer().isSneaking()) {
                shulkerStep.playStepSound(pos.toLocation(viewer.getPlayer().getWorld()));
            }
        }

        // Legacy active-surface / shulker switching code removed: movement is handled by the
        // simulated-player logic and the collision solver. We simply track the player's
        // actual position for purposes such as shulker spawning.
    }

    private void updateMovingSurfacePacketMonitor(Vector playerPosition) {
        if (viewer.getPlayer().getVehicle() != null) {
            disableMovingSurfacePacketMonitor();
            return;
        }

        boolean hasMovingSurfaceNearby = surfaces.stream().anyMatch(surface ->
                surface.isSimulated() &&
                surface.shape != null &&
                surface.shape.getPosition().distanceSquared(playerPosition) <= MOVING_SURFACE_MONITOR_RANGE_SQUARED
        );

        if (hasMovingSurfaceNearby) {
            if (movingSurfacePacketMonitor == null) {
                movingSurfacePacketMonitor = viewer.createPacketListener(
                        new MovingSurfacePacketMonitor(),
                        PacketType.IN_POSITION,
                        PacketType.IN_POSITION_LOOK
                );
            }
            if (!movingSurfacePacketMonitor.isEnabled()) {
                movingSurfacePacketMonitor.enable();
            }
            movingSurfacePacketMonitor.getListener().resetIfTimedOut();
        } else if (movingSurfacePacketMonitor != null) {
            disableMovingSurfacePacketMonitor();
        }
    }

    private void disableMovingSurfacePacketMonitor() {
        if (movingSurfacePacketMonitor != null) {
            movingSurfacePacketMonitor.getListener().reset();
            movingSurfacePacketMonitor.disable();
        }
    }

    private final class MovingSurfacePacketMonitor implements PacketListener {
        public void reset() {
            movingSurfacePacketState.set(MovingSurfacePacketState.reset());
        }

        public void resetIfTimedOut() {
            MovingSurfacePacketState state = movingSurfacePacketState.get();
            if (state.lastPacketTick != Integer.MIN_VALUE &&
                    (CommonUtil.getServerTicks() - state.lastPacketTick) > MOVING_SURFACE_PACKET_TIMEOUT_TICKS) {
                reset();
            }
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getType() == PacketType.IN_POSITION || event.getType() == PacketType.IN_POSITION_LOOK) {
                int tick = CommonUtil.getServerTicks();
                MovingSurfacePacketState previousState = movingSurfacePacketState.get();
                ServerboundMovePlayerPacketHandle packet = ServerboundMovePlayerPacketHandle.createHandle(event.getPacket().getHandle());
                Vector current = new Vector(packet.getX(), packet.getY(), packet.getZ());
                Vector previous = previousState.lastKnownPosition;

                Vector delta;
                if (previous == null) {
                    delta = new Vector();
                } else {
                    delta = current.clone().subtract(previous);
                }

                if (delta.lengthSquared() > MOVING_SURFACE_MAX_PACKET_DELTA_SQUARED) {
                    reset();
                    return;
                }

                movingSurfacePacketState.set(new MovingSurfacePacketState(current, tick, delta));
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
        }
    }

    private static final class MovingSurfacePacketState {
        final Vector lastKnownPosition;
        final int lastPacketTick;
        final Vector delta;

        static MovingSurfacePacketState reset() {
            return new MovingSurfacePacketState(null, Integer.MIN_VALUE, new Vector());
        }

        MovingSurfacePacketState(Vector lastKnownPosition, int lastPacketTick, Vector delta) {
            this.lastKnownPosition = (lastKnownPosition == null) ? null : lastKnownPosition.clone();
            this.lastPacketTick = lastPacketTick;
            this.delta = delta.clone();
        }
    }

    private boolean startSimulatedPlayer(CollisionSurfaceImpl surface, Vector lastPosition, Vector position, Vector velocity) {
        Vector initialVelocity = computeInitialSimulatedVelocity(surface, velocity);
        AttachmentViewer.MovementController controller = viewer.controlMovement(
                AttachmentViewer.MovementController.Options.create()
                        .initialMotion(initialVelocity)
        );
        SimulatedPlayer simulatedPlayer = new SimulatedPlayer(
                viewer,
                controller,
                lastPosition.clone(),
                position.clone(),
                initialVelocity,
                surface,
                false
        );
        simulatedPlayer.viewLockNeedsReset = surface != null && surface.viewLock;
        InitialSimulationCollisionContext collisionContext = new InitialSimulationCollisionContext(simulatedPlayer);
        collisionContext.configure(position.getY(), simulatedPlayer.flying, simulatedPlayer.velocity.getY());

        if (!controller.update(
                position,
                getViewLockOrientationMode(surface, simulatedPlayer),
                getViewLockOrientation(surface),
                collisionContext.createBlockCollisionHandler()
        )) {
            controller.stop();
            return false;
        }

        simulatedPlayer.lastJumpInput = controller.getInput().jumping();
        simulatedPlayer.viewLockNeedsReset = false;
        this.simulatedPlayer = simulatedPlayer;
        disableMovingSurfacePacketMonitor();
        return true;
    }

    private Vector computeInitialSimulatedVelocity(CollisionSurfaceImpl surface, Vector fallbackVelocity) {
        Vector initialVelocity = fallbackVelocity.clone();
        if (surface == null || surface.shape == null) {
            return initialVelocity;
        }

        OBBSurfaceState surfaceState = new OBBSurfaceState(surface.shape);
        Vector packetDeltaVelocity = surfaceState.projectOntoNormalPlane(getMovingSurfacePacketDelta());
        if (packetDeltaVelocity.lengthSquared() > 1e-12) {
            return packetDeltaVelocity;
        }

        return surfaceState.projectOntoNormalPlane(initialVelocity);
    }

    private boolean tryStartSimulatedPlayerAfterDismount(
            List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions,
            PlayerBoundsState currentBounds,
            Vector previousPosition,
            Vector velocity
    ) {
        PlayerBoundsState loweredBounds = currentBounds.translate(0.0, -DISMOUNT_SURFACE_SUPPORT_DISTANCE, 0.0);
        PlayerCollisionSolver.Result<CollisionSurfaceImpl> support = PLAYER_COLLISION_SOLVER.solveDetailed(
                transitions,
                new PlayerBoundsTransition(currentBounds, loweredBounds)
        );
        if (support.lastCollisionMode != PlayerCollisionSolver.CollisionMode.FEET || support.lastSurface == null) {
            return false;
        }

        Vector solPos = support.state.feetPosition();
        return startSimulatedPlayer(support.lastSurface, previousPosition, solPos, velocity);
    }

    private boolean tryStartSimulatedPlayerOnSupport(
            List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions,
            PlayerBoundsState currentBounds,
            Vector previousPosition,
            Vector velocity
    ) {
        PlayerBoundsState loweredBounds = currentBounds.translate(0.0, -SHULKER_HANDOFF_SURFACE_SUPPORT_DISTANCE, 0.0);
        PlayerCollisionSolver.Result<CollisionSurfaceImpl> support = PLAYER_COLLISION_SOLVER.solveDetailed(
                transitions,
                new PlayerBoundsTransition(currentBounds, loweredBounds)
        );
        if (support.lastCollisionMode != PlayerCollisionSolver.CollisionMode.FEET || support.lastSurface == null) {
            return false;
        }

        CollisionSurfaceImpl supportSurface = toSurfaceImpl(support.lastSurface);
        if (supportSurface == null
                || !supportSurface.usesShulkerGridWhenStationary()
                || !supportSurface.didStartMovingThisTick()) {
            return false;
        }

        Vector solPos = support.state.feetPosition();
        Vector pinnedFromPos = null;
        Vector transformedToPos = null;
        OBBSurfaceTransition<CollisionSurfaceImpl> transition = findTransition(transitions, supportSurface);
        if (transition != null) {
            // Handoff dance: pin on the old (from) surface at the player's previous-tick location,
            // then carry that contact point through to the new (to) surface state.
            Vector actualFeet = currentBounds.feetPosition();
            PlayerBoundsState oldBounds = currentBounds.translate(
                    previousPosition.getX() - actualFeet.getX(),
                    previousPosition.getY() - actualFeet.getY(),
                    previousPosition.getZ() - actualFeet.getZ()
            );
            PlayerBoundsState oldBoundsPinned = transition.from.placeBoundsOnSurface(oldBounds);
            pinnedFromPos = oldBoundsPinned.feetPosition();

            Vector local = transition.from.worldToLocal(pinnedFromPos, new Vector());
            local.setY(0.0);
            transformedToPos = transition.to.localToWorld(local, new Vector());

            PlayerBoundsState movedBounds = oldBoundsPinned.translate(
                    transformedToPos.getX() - pinnedFromPos.getX(),
                    transformedToPos.getY() - pinnedFromPos.getY(),
                    transformedToPos.getZ() - pinnedFromPos.getZ()
            );
            solPos = transition.to.placeBoundsOnSurface(movedBounds).feetPosition();
        }

        return startSimulatedPlayer(support.lastSurface, previousPosition, solPos, velocity);
    }

    private void stopSimulatedPlayer(Vector trackingPosition) {
        stopSimulatedPlayer(trackingPosition, true);
    }

    private void stopSimulatedPlayer(Vector trackingPosition, boolean applyStopSyncCorrection) {
        SimulatedPlayer previousSimulatedPlayer = this.simulatedPlayer;
        if (previousSimulatedPlayer != null && previousSimulatedPlayer.pmc != null && !previousSimulatedPlayer.pmc.hasStopped()) {
            previousSimulatedPlayer.pmc.stop();
        }
        this.simulatedPlayer = null;
        // When the player is flying (jumped/walked off the moving surface), avoid applying
        // the stop sync correction, as that can force the player into landed terrain.
        if (applyStopSyncCorrection && trackingPosition != null && previousSimulatedPlayer != null && !previousSimulatedPlayer.flying) {
            ServerPlayerHandle playerHandle = ServerPlayerHandle.fromBukkit(viewer.getPlayer());
            playerHandle.setPositionRotation(
                    trackingPosition.getX(),
                    trackingPosition.getY(),
                    trackingPosition.getZ(),
                    viewer.getPlayer().getEyeLocation().getYaw(),
                    viewer.getPlayer().getEyeLocation().getPitch()
            );
            playerHandle.setFallDistance(0.0f);
            viewer.getPlayer().setVelocity(new Vector(0.0, PlayerPusher.PUSH_UP_VELOCITY, 0.0));
            viewer.send(ClientboundPlayerPositionPacketHandle.createNew(
                    trackingPosition.getX(), trackingPosition.getY(), trackingPosition.getZ(), 0.0f, 0.0f,
                    0.0, PlayerPusher.PUSH_UP_VELOCITY, 0.0,
                    RelativeFlags.ABSOLUTE_POSITION.withRelativeRotation().withAbsoluteDelta()));
            if (this.previousPlayerPosition == null) {
                this.previousPlayerPosition = trackingPosition.clone();
            } else {
                MathUtil.setVector(this.previousPlayerPosition, trackingPosition);
            }
        }
    }

    private void updateSimulatedPlayer(List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions, final PlayerBoundsState actualBounds) {
        SimulatedPlayer simulatedPlayer = this.simulatedPlayer;
        if (simulatedPlayer == null) {
            return;
        }
        if (viewer.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            stopSimulatedPlayer(null, false);
            return;
        }
        if (simulatedPlayer.pmc == null || simulatedPlayer.pmc.hasStopped() || viewer.getPlayer().getVehicle() != null) {
            stopSimulatedPlayer(simulatedPlayer.position);
            return;
        }

        Vector currentPosition = simulatedPlayer.position.clone();
        Vector nextPosition = currentPosition.clone();
        Vector entityPush = computeNearbyEntityPush(simulatedPlayer, actualBounds);
        Vector surfaceCarryVelocity = new Vector(); // carry velocity of the surface the player is on; zero when not on a surface
        CollisionSurfaceImpl currentSurface = toSurfaceImpl(simulatedPlayer.lastSurface);
        OBBSurfaceTransition<CollisionSurfaceImpl> activeTransition = null; // transition for the surface the player is standing on (null if flying/no surface)
        AttachmentViewer.Input input = simulatedPlayer.pmc.getInput();

        boolean justWalkedOffSurface = false;

        if (simulatedPlayer.flying) {
            // Simulate flying / falling off surfaces
            WalkInput walkInput = createWalkInput(simulatedPlayer, null, input);
            simulatedPlayer.applyWalkingVelocity(walkInput.acceleration);
            simulatedPlayer.velocity.add(entityPush);
            nextPosition.add(simulatedPlayer.velocity);
        } else if (currentSurface != null) {
            OBBSurfaceTransition<CollisionSurfaceImpl> transition = findTransition(transitions, currentSurface);
            if (transition != null) {
                activeTransition = transition;
                stepSoundTracker.move(createTransitionTransform(transition));
                // Move the simulated player state along with the surface the player is standing on.
                // Compute carry velocity (how much the surface itself moved) and transform the
                // player's stored velocity into the new surface orientation. The player's feet are
                // NOT pinned to the surface; the solver handles that via collision detection.
                Vector local = transition.from.worldToLocal(currentPosition, new Vector());
                local.setY(0.0);
                surfaceCarryVelocity = computeSurfaceCarryVelocity(transition, local);
                simulatedPlayer.velocity = transition.transformMovement(simulatedPlayer.velocity);

                // Compute walk input & horizontal walking acceleration
                WalkInput walkInput = createWalkInput(simulatedPlayer, transition.to, input);
                simulatedPlayer.applyWalkingVelocity(walkInput.acceleration);
                simulatedPlayer.velocity = transition.to.projectOntoNormalPlane(simulatedPlayer.velocity);

                // Compute surface steepness (0 = flat, 90 = vertical). Use acos(|normal.y|).
                // Treat inverted normals (normal.y < 0) as the same surface orientation for
                // walking input decisions: the solver already handles inverted normals when
                // detecting crossings, but the simulated-player logic should base input
                // enabling/disable decisions on an upward-facing normal. Use abs(normal.y)
                // so upside-down but flat surfaces are considered flat for input purposes.
                double surfaceAngleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, Math.abs(transition.to.normal.getY())))));
                boolean inputTooSteep = walkInput.tooSteep;

                // Determine the angle we should base gravity on: prefer attempted uphill input when present,
                // otherwise use the surface steepness.
                double gravityBasisAngle = inputTooSteep ? walkInput.uphillAngleDeg : surfaceAngleDeg;

                // Compute gravity factor based on GRAVITY_START_ANGLE -> 90 degrees mapping.
                double gravityFactor = (gravityBasisAngle - GRAVITY_START_ANGLE) / (90.0 - GRAVITY_START_ANGLE);
                gravityFactor = Math.max(0.0, Math.min(1.0, gravityFactor));

                // If input is beyond the MAX_INPUT_ANGLE, treat walk input as disabled (createWalkInput already reports tooSteep)
                boolean disableInput = surfaceAngleDeg >= MAX_INPUT_ANGLE || inputTooSteep && walkInput.uphillAngleDeg >= MAX_INPUT_ANGLE;

                // Remove any uphill component relative to the surface normal so walking cannot add upward momentum
                if (disableInput || gravityFactor > 0.0) {
                    simulatedPlayer.velocity = removeIntoSurfaceVelocity(simulatedPlayer.velocity,
                            PlayerCollisionSolver.CollisionMode.HEAD,
                            transition.to);
                }

                // If gravity factor is zero (below gravity start), but input is disabled, clamp upward momentum.
                if (shouldStartJump(simulatedPlayer.lastJumpInput, input)) {
                    // When jumping, transfer carry momentum into velocity immediately since the player
                    // is leaving the surface and carry will no longer be applied next tick.
                    simulatedPlayer.flying = true;
                    simulatedPlayer.velocity.add(surfaceCarryVelocity);
                    simulatedPlayer.applyJumpImpulse();
                    surfaceCarryVelocity = new Vector(); // carry already folded into velocity; don't apply to nextPosition again
                } else if (gravityFactor <= 0.0) {
                    if (disableInput && simulatedPlayer.velocity.getY() > 0.0) {
                        simulatedPlayer.velocity.setY(0.0);
                    }
                } else {
                    // Apply sliding downward along the surface plane proportional to steepness.
                    // Compute downhill direction by projecting world-down onto the surface plane.
                    Vector downhillDir = transition.to.projectOntoGroundPlane(new Vector(0.0, -1.0, 0.0));
                    if (downhillDir.lengthSquared() < 1e-12) {
                        // Fallback: if projection is nearly zero (surface almost vertical), slide along -zAxis
                        downhillDir = transition.to.zAxis.clone().multiply(-1.0);
                    }
                    downhillDir.normalize();
                    Vector downhillVel = downhillDir.clone().multiply(SimulatedPlayer.SIMULATED_GRAVITY * gravityFactor);

                    // Carry is applied to nextPosition separately; velocity is pure player movement (downhill slide)
                    simulatedPlayer.velocity = downhillVel.clone();
                }

                simulatedPlayer.velocity.add(entityPush);

                // Advance position: first carry the player with the moving surface, then add player's own velocity.
                // For jump/walk-off the carry was already folded into velocity above; surfaceCarryVelocity is zeroed.
                nextPosition.add(surfaceCarryVelocity);
                nextPosition.add(simulatedPlayer.velocity);

                // Detect when the player walks/falls off the surface, and toggle flying on right away
                if (!simulatedPlayer.flying &&
                        !PLAYER_COLLISION_SOLVER.hasSurfaceSupport(
                                transition, actualBounds, nextPosition, SimulatedPlayer.SIMULATED_GRAVITY
                        )
                ) {
                    simulatedPlayer.flying = true;
                    // Transfer carry into the flying velocity so the player inherits the surface's momentum
                    simulatedPlayer.velocity.add(surfaceCarryVelocity);
                    surfaceCarryVelocity = new Vector(); // already in velocity; don't double-count
                    simulatedPlayer.velocity.setY(simulatedPlayer.velocity.getY() - 1e-3);
                    nextPosition.setY(nextPosition.getY() - 1e-3);
                    justWalkedOffSurface = true;
                }
            }

            // If still on the surface, mark the active floor transition so the solver skips
            // the interpolated crossing detection for it. This prevents false FEET collisions
            // caused by the surface rotating (animation) while the player walks on it.
            // The solver's dedicated floor-clamp pass still prevents actual clipping.
            if (!simulatedPlayer.flying) {
                if (activeTransition != null) {
                    activeTransition.isFloor = true;
                }
            }
        }

        Vector actualFeetPosition = actualBounds.feetPosition();
        PlayerBoundsState bboxFrom = actualBounds.translate(
                currentPosition.getX() - actualFeetPosition.getX(),
                currentPosition.getY() - actualFeetPosition.getY(),
                currentPosition.getZ() - actualFeetPosition.getZ());
        PlayerBoundsState bboxTo = bboxFrom.translate(
                nextPosition.getX() - currentPosition.getX(),
                nextPosition.getY() - currentPosition.getY(),
                nextPosition.getZ() - currentPosition.getZ());

        // When the player is grounded, snap bboxTo vertically onto the surface plane.
        // This eliminates AABB-corner vs. sloped-surface penetration before the solver runs,
        // so the solver no longer sees a false FEET collision from the walking surface itself.
        if (!simulatedPlayer.flying && activeTransition != null) {
            PlayerBoundsState bboxToSnapped = activeTransition.to.placeBoundsOnSurface(bboxTo);
            if (!bboxToSnapped.equals(bboxTo)) {
                bboxTo = bboxToSnapped;
                nextPosition = bboxTo.feetPosition();
            }
        }

        SimulatedCollisionPassContext passContext = new SimulatedCollisionPassContext(
                simulatedPlayer,
                transitions,
                bboxFrom,
                bboxTo,
                justWalkedOffSurface,
                activeTransition,
                currentPosition,
                nextPosition
        );

        for (int attempt = 0; attempt < MAX_BLOCK_COLLISION_RESIMULATION_ATTEMPTS; attempt++) {
            SimulatedCollisionPassResult passResult = passContext.runSimulatedCollisionPass();
            if (passResult.stopped) {
                return;
            }

            passContext.nextPositionAfterPass = passResult.nextPosition;
            passContext.solution = passResult.solution;
            if (!passResult.needsResimulation) {
                break;
            }
        }
        nextPosition = passContext.nextPositionAfterPass;

        if (simulatedPlayer.flying) {
            stepSoundTracker.noStep();
        } else {
            CollisionSurfaceImpl steppedOnSurface = toSurfaceImpl(simulatedPlayer.lastSurface);
            if (steppedOnSurface == null) {
                stepSoundTracker.noStep();
            } else if (stepSoundTracker.step(nextPosition) && !simulatedPlayer.isSneaking()) {
                steppedOnSurface.getMaterial().playStepSound(nextPosition.toLocation(viewer.getPlayer().getWorld()));
            }
        }

        simulatedPlayer.lastPosition = currentPosition;
        if (passContext.solution != null) {
            simulatedPlayer.lastDebugState = passContext.solution.state;
        }
        simulatedPlayer.position = nextPosition;
        simulatedPlayer.lastJumpInput = input.jumping();
    }

    private Vector computeNearbyEntityPush(SimulatedPlayer simulatedPlayer, PlayerBoundsState actualBounds) {
        Player player = viewer.getPlayer();
        EntityHandle playerHandle = EntityHandle.fromBukkit(player);
        Vector push = new Vector();
        if (playerHandle.isNoclip() || playerHandle.isVehicle() || player.isDead()
                || (HAS_PLAYER_COLLIDABLE && !isPlayerCollidable(player))) {
            return push;
        }

        Vector feet = actualBounds.feetPosition();
        AABBHandle bounds = actualBounds.bounds.translate(
                simulatedPlayer.position.getX() - feet.getX(),
                simulatedPlayer.position.getY() - feet.getY(),
                simulatedPlayer.position.getZ() - feet.getZ());
        List<Entity> nearby = WorldUtil.getEntities(player.getWorld(), player,
                bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(),
                bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ());
        for (Entity entity : nearby) {
            if (entity instanceof Minecart) {
                continue;
            }
            EntityHandle other = EntityHandle.fromBukkit(entity);
            if (other.isNoclip() || playerHandle.isInSameVehicle(other)
                    || !Util.intersects(bounds, other.getBoundingBox())
                    || (HAS_ENTITY_PUSHABLE_BY && !isPushableBy(player, entity))) {
                continue;
            }
            addEntityPush(push, simulatedPlayer.position.getX() - other.getLocX(),
                    simulatedPlayer.position.getZ() - other.getLocZ());
        }
        return push;
    }

    private static boolean isPushableBy(Entity pusher, Entity candidate) {
        return EntityUtil.isPushableBy(pusher, candidate);
    }

    private static boolean isPlayerCollidable(Player player) {
        return player.isCollidable();
    }

    static void addEntityPush(Vector velocity, double dx, double dz) {
        double distance = Math.max(Math.abs(dx), Math.abs(dz));
        if (distance >= (double) 0.01F) {
            double root = Math.sqrt(distance);
            double scale = Math.min(1.0, 1.0 / root) / root * (double) 0.05F;
            velocity.setX(velocity.getX() + dx * scale);
            velocity.setZ(velocity.getZ() + dz * scale);
        }
    }

    private WalkInput createWalkInput(SimulatedPlayer simulatedPlayer, OBBSurfaceState surface, AttachmentViewer.Input input) {
        if (!input.hasWalkInput()) {
            return WalkInput.NONE;
        }

        Vector forward, left;

        if (surface != null) {
            // Use an upward-facing normal for input basis so that upside-down surfaces
            // don't invert left/right controls. The projection onto the plane is sign
            // independent, but the cross product for left-hand direction depends on
            // the sign of the normal. Use a canonical 'up' vector here.
            forward = surface.projectOntoGroundPlane(simulatedPlayer.getPlayerForward());
            if (forward.lengthSquared() < 1e-20) {
                forward = surface.zAxis.clone();
            } else {
                forward.normalize();
            }

            left = surface.groundNormal.getCrossProduct(forward);
            if (left.lengthSquared() < 1e-20) {
                left = surface.xAxis.clone().multiply(-1.0);
            } else {
                left.normalize();
            }
        } else {
            // Air movement always points where the player is looking
            forward = simulatedPlayer.getPlayerForward();
            // Note: crossProduct mutates the input vector, getCrossProduct produces a new vector
            left = new Vector(0.0, 1.0, 0.0).crossProduct(forward);
        }

        Vector acceleration = new Vector();
        double forwardInput = input.forwardsSigNum();
        double sidewaysInput = input.sidewaysSigNum();
        if (forwardInput != 0.0) {
            acceleration.add(forward.clone().multiply(forwardInput));
        }
        if (sidewaysInput != 0.0) {
            acceleration.add(left.clone().multiply(sidewaysInput));
        }

        // Normalize the movement vector, return no input if 0
        // This makes it so that forward + left turns into a diagonal movement vector,
        // without making the movement faster.
        final double accelerationLS = acceleration.lengthSquared();
        if (accelerationLS< 1e-20) {
            return WalkInput.NONE;
        } else {
            acceleration.multiply(1.0 / Math.sqrt(accelerationLS));
        }

        acceleration.multiply(SimulatedPlayer.SIMULATED_WALK_ACCELERATION);

        if (input.sneaking()) {
            acceleration.multiply(0.3);
        } else if (input.sprinting()) {
            acceleration.multiply(1.3);
        }

        double angleDeg = 0.0;
        if (surface != null) {
            if (Math.abs(acceleration.getY()) <= 1e-3) {
                // No uphill component
                acceleration = surface.projectOntoNormalPlane(acceleration);
                return new WalkInput(acceleration, false, 0.0);
            }

            Vector direction = acceleration.clone().normalize();
            angleDeg = Math.toDegrees(Math.asin(direction.getY()));
            if (angleDeg >= MAX_INPUT_ANGLE) {
                return new WalkInput(new Vector(), true, angleDeg);
            }

            double factor = computeUphillWalkSpeedFactor(angleDeg);
            acceleration.multiply(Math.max(0.0, factor));
            // Ensure walk input does not add any component along the surface normal
            acceleration = surface.projectOntoNormalPlane(acceleration);
        }

        return new WalkInput(acceleration, false, angleDeg);
    }

    static double computeUphillWalkSpeedFactor(double angleDeg) {
        // New behavior: do not reduce walk speed based on slope angle. Input remains fully effective
        // until MAX_INPUT_ANGLE, at which point input is disabled.
        if (angleDeg < MAX_INPUT_ANGLE) {
            return 1.0;
        }
        return 0.0;
    }

    static <T> Vector computeSurfaceCarryVelocity(OBBSurfaceTransition<T> surface, Vector surfaceLocalPosition) {
        Vector localPosition = surfaceLocalPosition.clone();
        localPosition.setY(0.0);
        Vector before = surface.from.localToWorld(localPosition, new Vector());
        Vector after = surface.to.localToWorld(localPosition, new Vector());
        return after.subtract(before);
    }

    static boolean isUphillVelocityTooSteep(Vector velocity) {
        if (velocity.lengthSquared() < 1e-20 || velocity.getY() <= 0.0) {
            return false;
        }

        Vector direction = velocity.clone().normalize();
        double angleDeg = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, direction.getY()))));
        return angleDeg >= MAX_INPUT_ANGLE;
    }

    static boolean shouldStartJump(boolean lastJumpInput, AttachmentViewer.Input input) {
        return input.jumping() && !input.sneaking() && !lastJumpInput;
    }

    static Vector removeIntoSurfaceVelocity(Vector velocity, PlayerCollisionSolver.CollisionMode mode, OBBSurfaceState surface) {
        if (velocity == null) {
            return new Vector();
        }
        if (surface == null || mode == null || mode == PlayerCollisionSolver.CollisionMode.NONE) {
            return velocity.clone();
        }

        Vector result = velocity.clone();
        double dot = result.dot(surface.normal);
        switch (mode) {
            case FEET:
                if (dot < 0.0) {
                    result.subtract(surface.normal.clone().multiply(dot));
                }
                break;
            case HEAD:
                if (dot > 0.0) {
                    result.subtract(surface.normal.clone().multiply(dot));
                }
                break;
            case WALL:
                result.subtract(surface.normal.clone().multiply(dot));
                break;
            default:
                break;
        }
        return result;
    }

    private static final class WalkInput {
        static final WalkInput NONE = new WalkInput(new Vector(), false, 0.0);

        final Vector acceleration;
        final boolean tooSteep;
        final double uphillAngleDeg;

        WalkInput(Vector acceleration, boolean tooSteep, double uphillAngleDeg) {
            this.acceleration = acceleration;
            this.tooSteep = tooSteep;
            this.uphillAngleDeg = uphillAngleDeg;
        }
    }

    /**
     * Common state and collision policy for PMC block-collision callbacks in simulation mode.
     */
    private abstract class SimulatedBlockCollisionContext {
        protected final SimulatedPlayer simulatedPlayer;
        protected CollidedSolidFaceSet collidedSolidBlockFaces;
        protected boolean canResimulate;
        protected double simulationFeetY;
        protected boolean simulationFlying;
        protected double simulationVelocityY;
        protected Vector stopSimulationPosition;
        protected boolean needsResimulation;
        protected PlayerMovementController.BlockCollisionEvent collisionEvent;

        protected SimulatedBlockCollisionContext(SimulatedPlayer simulatedPlayer) {
            this.simulatedPlayer = simulatedPlayer;
        }

        protected void configure(
                CollidedSolidFaceSet collidedSolidBlockFaces,
                boolean canResimulate,
                double simulationFeetY,
                boolean simulationFlying,
                double simulationVelocityY
        ) {
            this.collidedSolidBlockFaces = collidedSolidBlockFaces;
            this.canResimulate = canResimulate;
            this.simulationFeetY = simulationFeetY;
            this.simulationFlying = simulationFlying;
            this.simulationVelocityY = simulationVelocityY;
            this.stopSimulationPosition = null;
            this.needsResimulation = false;
            this.collisionEvent = null;
        }

        protected void configure(double simulationFeetY, boolean simulationFlying, double simulationVelocityY) {
            configure(null, false, simulationFeetY, simulationFlying, simulationVelocityY);
        }

        public Consumer<PlayerMovementController.BlockCollisionEvent> createBlockCollisionHandler() {
            return event -> {
                collisionEvent = event;

                AABBHandle blockBounds = event.getBlockBounds();
                if (blockBounds == null) {
                    return;
                }

                double feetY = event.getCollisionPosition().getY();
                double stopThreshold = simulationFlying ? simulationFeetY : (simulationFeetY + 0.5);
                collisionSurfaceLogger.debugBlockCollisionEvent(
                        event.getFace(),
                        event.getTheta(),
                        feetY,
                        blockBounds,
                        stopThreshold,
                        simulationFeetY,
                        simulationFlying
                );
                boolean shouldTryStop;
                if (simulationFlying) {
                    // Flying/airborne: only treat true top-face collisions as landings.
                    // This avoids side-wall contacts falsely promoting to "landed",
                    // while still allowing proper handoff when descending onto block tops.
                    shouldTryStop = (event.getFace() == BlockFace.UP) && (blockBounds.getMaxY() <= (feetY + 1e-10));
                } else {
                    // Grounded: keep 0.5 step-up allowance relative to the simulated feet Y.
                    shouldTryStop = (blockBounds.getMaxY() <= (stopThreshold + 1e-10));

                    // Side-face contact starting at theta ~= 0 is flush wall contact, not a
                    // true "step onto block top" event. Do not hand off simulation for this.
                    if (shouldTryStop
                            && !FaceUtil.isVertical(event.getFace())
                            && event.getTheta() <= 1e-9) {
                        shouldTryStop = false;
                        collisionSurfaceLogger.debug("event side-face theta0 -> no stopSimulation");
                    }

                    // Stepping up onto a block that has something solid above it is obstructed
                    if (shouldTryStop && event.getBlockAboveBounds() != null
                            && blockBounds.getMaxY() > simulationFeetY + 1e-6) {
                        shouldTryStop = false;
                        collisionSurfaceLogger.debug("event step-up obstructed by block above");
                    }
                }
                if (shouldTryStop) {
                    Vector stopPosition = event.getCollisionPosition();
                    stopPosition.setY(blockBounds.getMaxY());
                    boolean canStand = canStandAt(simulatedPlayer, stopPosition, blockBounds);
                    collisionSurfaceLogger.debugDefer(() -> "event topCandidate stopPos=" + stopPosition + " canStand=" + canStand);
                    if (canStand) {
                        event.stopSimulation();
                        stopSimulationPosition = stopPosition;
                        collisionSurfaceLogger.debug("event -> stopSimulation");
                        return;
                    }

                    // While airborne, a top-face hit at theta ~= 0 can be a degenerate
                    // "starting from stale top contact" collision. Cancel it once and
                    // retry from the updated PMC position so movement can continue.
                    if (simulationFlying
                            && simulationVelocityY >= -1e-9
                            && event.getTheta() <= 1e-9
                            && canResimulate) {
                        event.cancelCollisionHandling();
                        needsResimulation = true;
                        collisionSurfaceLogger.debug("event topCandidate theta0 -> cancel+resim");
                        return;
                    }
                }

                if (collidedSolidBlockFaces != null && canResimulate) {
                    if (!FaceUtil.isVertical(event.getFace())) {
                        if (simulationFlying) {
                            // In airborne/jump mode, let PMC's own swept collision clamp side hits.
                            // Exception: theta ~= 0 means sweep starts in immediate contact; retry
                            // with a synthetic side-face so we preserve the wall constraint while
                            // re-anchoring movement state.
                            boolean addedFace = (event.getTheta() <= 1e-9)
                                    && collidedSolidBlockFaces.addIfAbsent(blockBounds, event.getFace());
                            if (event.getTheta() <= 1e-9 && addedFace) {
                                event.cancelCollisionHandling();
                                needsResimulation = true;
                                collisionSurfaceLogger.debugDefer(() -> "event side-face theta0 while flying -> cancel+resim syntheticBlocks="
                                        + collidedSolidBlockFaces.size());
                            } else {
                                collisionSurfaceLogger.debug("event side-face while flying -> keep PMC handling");
                            }
                        } else {
                            if (collidedSolidBlockFaces.addIfAbsent(blockBounds, event.getFace())) {
                                event.cancelCollisionHandling();
                                needsResimulation = true;
                                collisionSurfaceLogger.debugDefer(() -> "event -> cancel+resim syntheticBlocks=" + collidedSolidBlockFaces.size());
                            } else {
                                collisionSurfaceLogger.debug("event duplicate synthetic block -> keep PMC handling");
                            }
                        }
                    } else {
                        collisionSurfaceLogger.debug("event non-side face -> keep PMC handling");
                    }
                }
            };
        }
    }

    private final class InitialSimulationCollisionContext extends SimulatedBlockCollisionContext {
        InitialSimulationCollisionContext(SimulatedPlayer simulatedPlayer) {
            super(simulatedPlayer);
        }
    }

    /**
     * Mutable state for retrying a single simulated-player movement update against
     * dynamic surfaces plus an accumulated set of collided static block faces.
     * <p>
     * Captures the baseline simulated player state once, then runs one or more
     * pass attempts from that baseline while adding newly discovered solid block
     * collisions to {@link #collidedSolidBlockFaces}. This avoids ping-ponging
     * between multiple wall blocks and keeps all retry-related side effects scoped.
     */
    private final class SimulatedCollisionPassContext extends SimulatedBlockCollisionContext {
        final List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions;
        final PlayerBoundsState bboxFrom;
        final PlayerBoundsState bboxTo;
        final boolean justWalkedOffSurface;
        final OBBSurfaceTransition<CollisionSurfaceImpl> activeTransition;
        final Vector currentPosition;
        final Vector baseNextPosition;
        final Vector baseVelocity;
        final CollisionSurface baseLastSurface;
        final boolean baseFlying;
        final boolean baseViewLockNeedsReset;
        final CollidedSolidFaceSet collidedSolidBlockFaces = new CollidedSolidFaceSet();
        Vector nextPositionAfterPass;
        PlayerCollisionSolver.Result<CollisionSurfaceImpl> solution = null;
        int attemptCounter = 0;

        SimulatedCollisionPassContext(
                SimulatedPlayer simulatedPlayer,
                List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions,
                PlayerBoundsState bboxFrom,
                PlayerBoundsState bboxTo,
                boolean justWalkedOffSurface,
                OBBSurfaceTransition<CollisionSurfaceImpl> activeTransition,
                Vector currentPosition,
                Vector nextPosition
        ) {
            super(simulatedPlayer);
            this.transitions = transitions;
            this.bboxFrom = bboxFrom;
            this.bboxTo = bboxTo;
            this.justWalkedOffSurface = justWalkedOffSurface;
            this.activeTransition = activeTransition;
            this.currentPosition = currentPosition;
            this.baseNextPosition = nextPosition.clone();
            this.baseVelocity = simulatedPlayer.velocity.clone();
            this.baseLastSurface = simulatedPlayer.lastSurface;
            this.baseFlying = simulatedPlayer.flying;
            this.baseViewLockNeedsReset = simulatedPlayer.viewLockNeedsReset;
            this.nextPositionAfterPass = this.baseNextPosition.clone();
        }

        SimulatedCollisionPassResult runSimulatedCollisionPass() {
            final int attempt = ++attemptCounter;
            Vector nextPosition = baseNextPosition.clone();
            simulatedPlayer.velocity = baseVelocity.clone();
            simulatedPlayer.lastSurface = baseLastSurface;
            simulatedPlayer.flying = baseFlying;
            simulatedPlayer.viewLockNeedsReset = baseViewLockNeedsReset;
            collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt
                    + " start baseNext=" + baseNextPosition
                    + " baseVel=" + baseVelocity
                    + " baseFlying=" + baseFlying
                    + " syntheticBlocks=" + collidedSolidBlockFaces.size());

            List<OBBSurfaceTransition<CollisionSurfaceImpl>> solverTransitions =
                    collidedSolidBlockFaces.withTransitions(transitions);

            PlayerCollisionSolver.Result<CollisionSurfaceImpl> solution = PLAYER_COLLISION_SOLVER.solveDetailed(
                    solverTransitions,
                    new PlayerBoundsTransition(bboxFrom, bboxTo)
            );

            if (VERIFY_LOG_PENETRATIONS) {
                boolean hasCornerPassedThrough = false;
                final PlayerCollisionSolver.Result<CollisionSurfaceImpl> verifyResult = solution;
                for (OBBSurfaceTransition<CollisionSurfaceImpl> surface : solverTransitions) {
                    if (surface.hasCornerPassedThrough(new PlayerBoundsTransition(bboxFrom, verifyResult.state))) {
                        if (!verifyResult.involvedTransitions.contains(surface)) {
                            verifyResult.involvedTransitions.add(surface);
                        }
                        collisionSurfaceLogger.debugDefer(() -> "verify corner-passed-through surface=" + surface.source);
                        collisionSurfaceLogger.debugDefer(() -> "verify solution-bounds=" + verifyResult.state.bounds);
                        hasCornerPassedThrough = true;
                    } else if (surface.to.isClippingThrough(new PlayerBoundsState(verifyResult.state.bounds))) {
                        if (!verifyResult.involvedTransitions.contains(surface)) {
                            verifyResult.involvedTransitions.add(surface);
                        }
                        collisionSurfaceLogger.debugDefer(() -> "verify surface-clipping surface=" + surface.source);
                        hasCornerPassedThrough = true;
                    }
                }
                if (hasCornerPassedThrough) {
                    collisionSurfaceLogger.debugDefer(() -> "verify solver-debug-output:\n" + verifyResult.printDebugTest());

                    if (simulatedPlayer.lastDebugState != null) {
                        StringBuilder str = new StringBuilder();
                        simulatedPlayer.lastDebugState.printDebugCreate(str, "");
                        collisionSurfaceLogger.debug("verify prev-state:\n" + str);
                    }
                }
            }

            boolean hasSurfaceCollision = !solution.state.equals(bboxTo);
            if (!hasSurfaceCollision && justWalkedOffSurface) {
                solution = PLAYER_COLLISION_SOLVER.solveBelowFeetDetailed(solverTransitions, bboxTo);
                hasSurfaceCollision = solution.hasCollision();
            }
            final boolean hasSurfaceCollisionFinal = hasSurfaceCollision;
            final PlayerCollisionSolver.Result<CollisionSurfaceImpl> solvedResult = solution;
            collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt
                    + " solver hasSurfaceCollision=" + hasSurfaceCollisionFinal
                    + " mode=" + solvedResult.lastCollisionMode
                    + " lastSurfaceType=" + ((solvedResult.lastSurface == null) ? "null" : solvedResult.lastSurface.getClass().getSimpleName())
                    + " solvedFeetY=" + solvedResult.state.feetPosition().getY()
                    + " desiredFeetY=" + bboxTo.feetPosition().getY());
            if (hasSurfaceCollision) {
                nextPosition = solution.state.feetPosition();
                double desiredFeetY = bboxTo.feetPosition().getY();
                CollisionSurfaceImpl collisionSurface = toSurfaceImpl(solution.lastSurface);
                if (collisionSurface == null && nextPosition.getY() > (desiredFeetY + 1e-10)) {
                    // If the solver did not resolve against a real moving surface, never
                    // allow the collision response to step the simulated player upward.
                    // This includes synthetic block constraints and null-surface FEET responses.
                    final double oldY = nextPosition.getY();
                    final PlayerCollisionSolver.Result<CollisionSurfaceImpl> nonSurfaceUpClampResult = solution;
                    collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt + " nonSurfaceUpClamp oldY=" + oldY + " -> " + desiredFeetY
                            + " mode=" + nonSurfaceUpClampResult.lastCollisionMode
                            + " lastSurfaceType=" + ((nonSurfaceUpClampResult.lastSurface == null) ? "null" : nonSurfaceUpClampResult.lastSurface.getClass().getSimpleName()));
                    nextPosition.setY(desiredFeetY);
                }

                boolean wasFlying = simulatedPlayer.flying;
                if (collisionSurface != null && isFloorLikeLanding(solution)) {
                    CollisionSurfaceImpl landedSurface = collisionSurface;
                    boolean surfaceChanged = simulatedPlayer.lastSurface != solution.lastSurface;
                    collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt + " landing on real surface surfaceChanged=" + surfaceChanged
                            + " flyingBefore=" + wasFlying);
                    simulatedPlayer.lastSurface = solution.lastSurface;
                    simulatedPlayer.flying = false;
                    simulatedPlayer.velocity.setX(0.0);
                    simulatedPlayer.velocity.setY(0.0);
                    simulatedPlayer.velocity.setZ(0.0);
                    simulatedPlayer.viewLockNeedsReset = landedSurface != null
                            && landedSurface.viewLock
                            && (wasFlying || surfaceChanged);
                } else if (!simulatedPlayer.flying) {
                    final PlayerCollisionSolver.CollisionMode collisionMode = solution.lastCollisionMode;
                    collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt + " removeIntoSurfaceVelocity mode=" + collisionMode);
                    simulatedPlayer.velocity = removeIntoSurfaceVelocity(
                            simulatedPlayer.velocity,
                            solution.lastCollisionMode,
                            solution.lastSurfaceToState
                    );
                }
            }

            simulatedPlayer.applyBlockSpeedFactor((activeTransition == null) ? null : activeTransition.to);
            if (simulatedPlayer.flying) {
                simulatedPlayer.applyGravity();
            }
            simulatedPlayer.applyDrag();
            simulatedPlayer.applyMinimumSpeed();

            CollisionSurfaceImpl currentSurface = toSurfaceImpl(simulatedPlayer.lastSurface);
            OrientationMode orientationMode = getViewLockOrientationMode(currentSurface, simulatedPlayer);
            Quaternion orientation = getViewLockOrientation(currentSurface);
            // The last pass must be accepted as-is, otherwise nothing is ever synced
            configure(
                    (attempt < MAX_BLOCK_COLLISION_RESIMULATION_ATTEMPTS) ? collidedSolidBlockFaces : null,
                    attempt < MAX_BLOCK_COLLISION_RESIMULATION_ATTEMPTS,
                    nextPosition.getY(),
                    simulatedPlayer.flying,
                    simulatedPlayer.velocity.getY()
            );
            if (!simulatedPlayer.pmc.update(
                    nextPosition,
                    orientationMode,
                    orientation,
                    createBlockCollisionHandler()
            )) {
                Vector stopPosition = stopSimulationPosition;
                if (stopPosition != null) {
                    simulatedPlayerStartCooldownTicks = BLOCK_HANDOFF_RECAPTURE_COOLDOWN_TICKS;
                    collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt + " block-handoff cooldown=" + simulatedPlayerStartCooldownTicks);
                }
                collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt + " pmc stopped stopPos=" + stopPosition);
                stopSimulatedPlayer(stopPosition == null ? currentPosition : stopPosition);
                return SimulatedCollisionPassResult.stopped();
            }
            if (orientationMode != OrientationMode.NONE) {
                simulatedPlayer.viewLockNeedsReset = false;
            }
            boolean resim = needsResimulation;
            PlayerMovementController.BlockCollisionEvent clampEvent = collisionEvent;
            if (clampEvent != null && !resim
                    && !clampEvent.isCollisionHandlingCancelled() && !clampEvent.isStopSimulationRequested()) {
                // The PMC clamped the player against a block. Continue the simulation from
                // the clamped position and drop the velocity going into the block, otherwise
                // the simulated position runs away from the position the player is at.
                Vector clamped = clampEvent.getCollisionPosition().clone();
                final Vector nextPositionBeforeClamp = nextPosition;
                collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt + " adopting PMC clamp " + clamped + " (was " + nextPositionBeforeClamp + ")");
                nextPosition = clamped;
                switch (clampEvent.getFace()) {
                    case EAST: case WEST: simulatedPlayer.velocity.setX(0.0); break;
                    case NORTH: case SOUTH: simulatedPlayer.velocity.setZ(0.0); break;
                    case UP: if (simulatedPlayer.velocity.getY() < 0.0) simulatedPlayer.velocity.setY(0.0); break;
                    case DOWN: if (simulatedPlayer.velocity.getY() > 0.0) simulatedPlayer.velocity.setY(0.0); break;
                    default: break;
                }
            }
            final Vector appliedNextPosition = nextPosition;
            final boolean resimFinal = resim;
            collisionSurfaceLogger.debugDefer(() -> "pass#" + attempt + " pmc applied nextPos=" + appliedNextPosition
                    + " resim=" + resimFinal + " syntheticBlocks=" + collidedSolidBlockFaces.size());
            return new SimulatedCollisionPassResult(nextPosition, solution, resim, false);
        }
    }

    /**
     * Outcome of one {@link SimulatedCollisionPassContext#runSimulatedCollisionPass()}
     * attempt.
     * <ul>
     * <li>{@link #nextPosition}: position accepted for this pass</li>
     * <li>{@link #solution}: solver result used for debug/landing state updates</li>
     * <li>{@link #needsResimulation}: whether another pass should be run with newly
     *     accumulated block collision surfaces</li>
     * <li>{@link #stopped}: whether movement control was stopped during PMC update</li>
     * </ul>
     */
    private static final class SimulatedCollisionPassResult {
        final Vector nextPosition;
        final PlayerCollisionSolver.Result<CollisionSurfaceImpl> solution;
        final boolean needsResimulation;
        final boolean stopped;

        SimulatedCollisionPassResult(
                Vector nextPosition,
                PlayerCollisionSolver.Result<CollisionSurfaceImpl> solution,
                boolean needsResimulation,
                boolean stopped
        ) {
            this.nextPosition = nextPosition;
            this.solution = solution;
            this.needsResimulation = needsResimulation;
            this.stopped = stopped;
        }

        static SimulatedCollisionPassResult stopped() {
            return new SimulatedCollisionPassResult(new Vector(), null, false, true);
        }
    }

    private static CollisionSurfaceImpl toSurfaceImpl(CollisionSurface surface) {
        return (surface instanceof CollisionSurfaceImpl) ? (CollisionSurfaceImpl) surface : null;
    }

    private static OrientationMode getViewLockOrientationMode(CollisionSurfaceImpl surface, SimulatedPlayer simulatedPlayer) {
        if (simulatedPlayer.flying || surface == null || !surface.viewLock || surface.getShape() == null) {
            return OrientationMode.NONE;
        }
        return simulatedPlayer.viewLockNeedsReset ? OrientationMode.ABSOLUTE_RESET : OrientationMode.ABSOLUTE;
    }

    private static Quaternion getViewLockOrientation(CollisionSurfaceImpl surface) {
        return (surface == null || surface.getShape() == null) ? null : surface.getShape().getOrientation().clone();
    }

    private static boolean canStandAt(SimulatedPlayer simulatedPlayer, Vector feetPosition, AABBHandle supportBlockBounds) {
        AABBHandle playerBboxNow = ServerPlayerHandle.fromBukkit(simulatedPlayer.viewer.getPlayer()).getBoundingBox();
        double sizeX = playerBboxNow.getMaxX() - playerBboxNow.getMinX();
        double sizeY = playerBboxNow.getMaxY() - playerBboxNow.getMinY();
        double sizeZ = playerBboxNow.getMaxZ() - playerBboxNow.getMinZ();
        double halfSizeX = 0.5 * sizeX;
        double halfSizeZ = 0.5 * sizeZ;

        AABBHandle standingBounds = AABBHandle.createNew(
                feetPosition.getX() - halfSizeX,
                feetPosition.getY(),
                feetPosition.getZ() - halfSizeZ,
                feetPosition.getX() + halfSizeX,
                feetPosition.getY() + sizeY,
                feetPosition.getZ() + halfSizeZ
        );

        int minX = MathUtil.floor(standingBounds.getMinX());
        int minY = MathUtil.floor(standingBounds.getMinY());
        int minZ = MathUtil.floor(standingBounds.getMinZ());
        int maxX = MathUtil.ceil(standingBounds.getMaxX());
        int maxY = MathUtil.ceil(standingBounds.getMaxY());
        int maxZ = MathUtil.ceil(standingBounds.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    AABBHandle blockBounds = BlockUtil.getBoundingBox(simulatedPlayer.viewer.getPlayer().getWorld().getBlockAt(x, y, z));
                    if (blockBounds == null) {
                        continue;
                    }
                    blockBounds = blockBounds.translate(x, y, z);
                    if (Util.sameAABB(blockBounds, supportBlockBounds)) {
                        continue;
                    }
                    if (Util.intersects(standingBounds, blockBounds)) {
                        return false;
                    }
                }
            }
        }

        final boolean[] blockedByStationary = new boolean[] { false };
        simulatedPlayer.viewer.forAllStationaryCollisionElements(minX, minY, minZ, maxX, maxY, maxZ, element -> {
            if (!blockedByStationary[0] && Util.intersects(standingBounds, element.getBoundingBox())) {
                blockedByStationary[0] = true;
            }
        });
        return !blockedByStationary[0];
    }

    /**
     * Tracks a set of solid faces that were encountered during movement processing.
     */
    private static final class CollidedSolidFaceSet {
        private final List<CollidedSolidFace> values = new ArrayList<>();

        public boolean addIfAbsent(AABBHandle candidateBounds, BlockFace candidateFace) {
            for (CollidedSolidFace existing : values) {
                if (existing.face == candidateFace && Util.sameAABB(existing.bounds, candidateBounds)) {
                    return false;
                }
            }
            values.add(new CollidedSolidFace(candidateBounds, candidateFace));
            return true;
        }

        public int size() {
            return values.size();
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public List<OBBSurfaceTransition<CollisionSurfaceImpl>> withTransitions(
                List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions
        ) {
            if (values.isEmpty()) {
                return transitions;
            }

            List<OBBSurfaceTransition<CollisionSurfaceImpl>> result = new ArrayList<>(
                    transitions.size() + values.size()
            );
            result.addAll(transitions);
            for (CollidedSolidFace face : values) {
                // Only side faces; top/bottom faces are handled by the PMC
                if (face.face.getModY() == 0) {
                    result.add(OBBSurfaceTransition.createStaticFace(face.bounds, face.face));
                }
            }
            return result;
        }
    }

    /**
     * A single face of a block, shulker box or otherwise that a player collided with during movement
     * handling.
     */
    private static final class CollidedSolidFace {
        final AABBHandle bounds;
        final BlockFace face;

        CollidedSolidFace(AABBHandle bounds, BlockFace face) {
            this.bounds = bounds;
            this.face = face;
        }
    }

    /**
     * Returns true when a solver result should cause the simulated player to "land"
     * on its reported surface (transition to grounded/walking mode). This is true for:<ul>
     * <li>{@link PlayerCollisionSolver.CollisionMode#FEET} – player's feet hit the surface from above</li>
     * <li>{@link PlayerCollisionSolver.CollisionMode#WALL} – only when the surface is floor-like
     *     (groundNormal.Y above a minimum threshold). This covers sloped floors where the solver
     *     classifies sideways/downhill contact as WALL, while correctly excluding truly vertical
     *     walls whose groundNormal.Y is near zero. A vertical wall must never be treated as a
     *     landing surface because the surface-snap and gravity code are designed for floors and
     *     produce extreme results (velocity spikes, stuck player) on near-vertical planes.</li>
     * </ul>
     */
    private static boolean isFloorLikeLanding(PlayerCollisionSolver.Result<?> solution) {
        switch (solution.lastCollisionMode) {
            case FEET:
                return true;
            case WALL:
                // Only treat a WALL collision as a landing when the surface is actually
                // floor-like (has meaningful vertical support). groundNormal.Y is always
                // positive (flipped if needed) so > 0.1 means the surface is at most ~84°
                // from horizontal. Vertical walls have groundNormal.Y ≈ 0 and are excluded.
                return solution.lastSurfaceToState != null
                        && solution.lastSurfaceToState.groundNormal.getY() > 0.1;
            default:
                return false;
        }
    }

    private static OBBSurfaceTransition<CollisionSurfaceImpl> findTransition(List<OBBSurfaceTransition<CollisionSurfaceImpl>> transitions, CollisionSurfaceImpl surface) {
        for (OBBSurfaceTransition<CollisionSurfaceImpl> transition : transitions) {
            if (transition.source == surface) {
                return transition;
            }
        }
        return null;
    }

    private static Matrix4x4 createTransitionTransform(OBBSurfaceTransition<?> transition) {
        Matrix4x4 fromTransform = new Matrix4x4();
        fromTransform.translate(transition.from.center);
        fromTransform.rotate(transition.from.orientation);
        fromTransform.invert();

        Matrix4x4 toTransform = new Matrix4x4();
        toTransform.translate(transition.to.center);
        toTransform.rotate(transition.to.orientation);
        toTransform.multiply(fromTransform);
        return toTransform;
    }

    // Emit a small reproducible snippet to the server log for debugging solver issues.
    // All lines are combined into a single log entry separated by newlines to make
    // copy/pasting into a unit test easier.
    private static <T> void logReproSnippet(List<OBBSurfaceTransition<T>> transitions, AABBHandle playerFrom, AABBHandle playerTo) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Collision solver repro snippet ---\n");

        if (transitions.size() == 1) {
            sb.append("OBBSurfaceTransition<String> transition = ");
            transitions.get(0).printDebugCreate(sb, "");
            sb.append(";\n");
        } else {
            sb.append("List<OBBSurfaceTransition<String>> transitions = Arrays.asList(\n");
            boolean first = true;
            for (OBBSurfaceTransition<T> t : transitions) {
                if (first) {
                    first = false;
                } else {
                    sb.append(",\n");
                }
                t.printDebugCreate(sb, "    ");
            }
            sb.append("\n);\n");
        }

        sb.append("AABBHandle playerFrom = makeAABB(")
                .append(playerFrom.getMinX()).append(", ").append(playerFrom.getMinY()).append(", ").append(playerFrom.getMinZ()).append(", ")
                .append(playerFrom.getMaxX()).append(", ").append(playerFrom.getMaxY()).append(", ").append(playerFrom.getMaxZ()).append(");\n");
        sb.append("AABBHandle playerTo   = makeAABB(")
                .append(playerTo.getMinX()).append(", ").append(playerTo.getMinY()).append(", ").append(playerTo.getMinZ()).append(", ")
                .append(playerTo.getMaxX()).append(", ").append(playerTo.getMaxY()).append(", ").append(playerTo.getMaxZ()).append(");\n");

        TrainCarts.plugin.getLogger().info(sb.toString());
    }

    /**
     * Creates a new CollisionSurface. Until further add methods are called on the surface,
     * nothing else is stored or tracked.
     *
     * @return CollisionSurface
     */
    public CollisionSurface createSurface() {
        return new CollisionSurfaceImpl();
    }

    /**
     * Iterates all stationary collision surfaces using a range of absolute block coordinates
     * that intersect them.
     *
     * @param minX Minimum x coordinate (inclusive)
     * @param minY Minimum y coordinate (inclusive)
     * @param minZ Minimum z coordinate (inclusive)
     * @param maxX Maximum x coordinate (inclusive)
     * @param maxY Maximum y coordinate (inclusive)
     * @param maxZ Maximum z coordinate (inclusive)
     * @param action to perform on each shulker that is within the specified bounds
     */
    public void forAllStationaryElements(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Consumer<StationaryCollisionElement> action) {
        floorTiles.forAllShulkers(minX, minY, minZ, maxX, maxY, maxZ, action);
        for (CollisionWallTileGrid wallTileGrid : wallTiles.values()) {
            wallTileGrid.forAllShulkers(minX, minY, minZ, maxX, maxY, maxZ, action);
        }
    }

    private CollisionWallTileGrid getWallTiles(BlockFace face) {
        return wallTiles.computeIfAbsent(face, f -> new CollisionWallTileGrid(shulkerCache, f));
    }

    /**
     * A single collision surface spawned in using the API for this viewer
     */
    private class CollisionSurfaceImpl implements CollisionSurface {
        private int updateCounter = 0;
        private String debugName = null;
        private CollisionSurfaceMaterial material = CollisionSurfaceMaterial.DEFAULT;
        private CartAttachmentPlatform.Color shulkerColor = CartAttachmentPlatform.Color.DEFAULT;
        private OrientedBoundingBox shape;
        private OrientedBoundingBox prevShape;
        private int moveDetectedAtCount = 0;
        private int startMovingAtCount = Integer.MIN_VALUE;
        private boolean useShulkersWhenNotMoving = true;
        private boolean viewLock = false;
        private boolean isMoving = false;
        private boolean simulationEnabled = true;

        @Override
        public void setSimulated(boolean simulated) {
            simulationEnabled = simulated;
        }

        @Override
        public void setUseShulkerGrid(boolean useShulkers) {
            useShulkersWhenNotMoving = useShulkers;
        }

        @Override
        public void setViewLock(boolean viewLock) {
            this.viewLock = viewLock;
        }

        @Override
        public CartAttachmentPlatform.Color getShulkerColor() {
            return shulkerColor;
        }

        @Override
        public void setShulkerColor(CartAttachmentPlatform.Color color) {
            this.shulkerColor = color;
        }

        @Override
        public CollisionSurfaceMaterial getMaterial() {
            return material;
        }

        @Override
        public void setMaterial(CollisionSurfaceMaterial material) {
            this.material = material;
        }

        @Override
        public boolean isSimulated() {
            return shape != null && simulationEnabled && (isMoving || !useShulkersWhenNotMoving);
        }

        @Override
        public boolean isShulkerGrid() {
            return shape != null && useShulkersWhenNotMoving && !isMoving;
        }

        @Override
        public String toString() {
            return "CollisionSurfaceImpl{moving=" + isMoving
                    + ", pos=" + ((shape == null) ? "null" : shape.getPosition())
                    + ", prevPos=" + ((prevShape == null) ? "null" : prevShape.getPosition())
                    + '}';
        }

        public void nextUpdate() {
            if (isMoving && (updateCounter - moveDetectedAtCount) >= TICKS_UNTIL_NOT_MOVING) {
                isMoving = false;
            }
            ++updateCounter;
        }

        public boolean didStartMovingThisTick() {
            return isMoving && startMovingAtCount == (updateCounter - 1);
        }

        public boolean usesShulkerGridWhenStationary() {
            return useShulkersWhenNotMoving;
        }

        @Override
        public int getUpdateCounter() {
            return updateCounter;
        }

        @Override
        public void remove() {
            setShape(null);
        }

        @Override
        public OrientedBoundingBox getShape() {
            return this.shape;
        }

        @Override
        public String getDebugName() {
            return this.debugName;
        }

        @Override
        public void setDebugName(String name) {
            this.debugName = name;
        }

        @Override
        public void setShape(OrientedBoundingBox shape) {
            // Handle removal/clear with null
            if (shape == null) {
                if (this.shape != null) {
                    surfaces.remove(this);
                    this.shape = null;
                    this.prevShape = null;
                    ++updateCounter;
                }
                return;
            }

            // When no shape was known before (=removed), add this surface to be updated
            // Do not yet spawn the shulkers, because we might find next tick that this shape is moving
            if (this.shape == null) {
                this.shape = new OrientedBoundingBox(shape.getPosition(), shape.getSize(), shape.getOrientation());
                this.savePrevShape();
                surfaces.add(this);

                // Assume initially not moving to allow shulkers to spawn in right away
                this.isMoving = false;
                return;
            }

            // Check if the shape only moved downwards (elevator style) and did not change orientation or size
            // In that case, it's fine to keep using shulkers to represent this surface.
            // Do not set moveDetectedAtCount in that case, to allow shulkers to be used.
            if (
                    this.shape.getOrientation().equals(shape.getOrientation()) &&
                    this.shape.getSize().equals(shape.getSize())
            ) {
                Vector oldPos = this.shape.getPosition();
                Vector newPos = shape.getPosition();
                if (newPos.getX() == oldPos.getX() && newPos.getZ() == oldPos.getZ()) {
                    if (newPos.getY() == oldPos.getY()) {
                        // No change at all. Ignore.
                        return;
                    } else if (newPos.getY() < oldPos.getY()) {
                        // Moving downwards: keep shulkers for small deltas, but if the surface drops
                        // too fast switch to simulated mode so players are carried down reliably.
                        double downwardSpeed = oldPos.getY() - newPos.getY();
                        if (downwardSpeed <= SHULKER_MAX_DOWNWARD_SPEED) {
                            this.shape.setPosition(newPos);
                            return;
                        }
                    }
                }
            }

            // Shape changed position/orientation/size in a way that acts as a moving surface
            // This despawns shulkers / keeps shulkers de-spawned and switches to movement control
            if (!this.isMoving) {
                this.startMovingAtCount = this.updateCounter;
            }
            this.moveDetectedAtCount = this.updateCounter;
            this.isMoving = true;
            this.shape.setPosition(shape.getPosition());
            this.shape.setOrientation(shape.getOrientation());
            this.shape.setSize(shape.getSize());
        }

        private void savePrevShape() {
            if (this.shape == null) {
                this.prevShape = null;
            } else {
                this.prevShape = new OrientedBoundingBox(this.shape.getPosition(), this.shape.getSize(), this.shape.getOrientation());
            }
        }

        public void spawnOnShulkerGrid() {
            Vector playerPos = viewer.getPlayer().getLocation().toVector();
            OBBSurfaceContext context = new OBBSurfaceContext(shape, playerPos, shulkerViewDistance);
            if (context.isFullyClipped) {
                return;
            }

            if (context.isWall) {
                // Steep surfaces are projected as a wall

                // Decide what block-axis we 'project' onto the surface to make the wall
                // If the player is behind the surface, invert the face direction we project
                BlockFace face;
                if (Math.abs(context.normal.getX()) > Math.abs(context.normal.getZ())) {
                    face = (context.normal.getX() > 0.0 != context.isBackSide) ? BlockFace.WEST : BlockFace.EAST;
                } else {
                    face = (context.normal.getZ() > 0.0 != context.isBackSide) ? BlockFace.NORTH : BlockFace.SOUTH;
                }

                applyWallSurface(context, face);
            } else if ((context.normal.getY() < 0.0) != context.isBackSide) {
                // Horizontal surfaces that are above the player are projected as ceilings
                applyWallSurface(context, BlockFace.UP);
            } else if (context.normal.getY() > 0.95) {
                // Horizontal surfaces that are below the player and are mostly flat
                // These only require a single shulker per block
                applyLevelSurface(context);
            } else if (Math.abs(context.normal.getX()) < 0.2 || Math.abs(context.normal.getZ()) < 0.2) {
                // An axis-aligned sloped surface, needs 2 shulkers per block to represent
                applyAlignedSlopedSurface(context);
            } else {
                // A diagonal sloped surface, requires 4 shulkers per block to represent
                applyDiagonalSlopedSurface(context);
            }
        }

        /**
         * Adds or updates a single shaped floor tile that is a part of this surface.
         * Only a single tile can exist per x/z coordinate.
         *
         * @param x X-block coordinate
         * @param z Z-block coordinate
         * @param shape CollisionFloorTileShape
         */
        public void addFloorTile(int x, int z, CollisionFloorTileShape shape) {
            floorTiles.addFloorTile(this, x, z, shape);
        }

        /**
         * Removes any shaped floor tile that was added at a particular x/z block
         * coordinate previously. Can be called to undo a previous added shape.
         * Practically not used, since you can just call clear() and add all shapes
         * after, which is a lot more reliable.
         *
         * @param x X-block coordinate
         * @param z Z-block coordinate
         */
        public void removeFloorTile(int x, int z) {
            floorTiles.removeFloorTile(this, x, z);
        }

        /**
         * Adds a full-block wall tile, facing into a particular direction.
         * The x/y are relative to the facing direction (horizontally y==y and x is the
         * other axis, vertically the x==x and y is z).
         *
         * @param face Face direction of the player looking at the wall (opposite of normal)
         * @param x Face-relative X tile
         * @param y Face-relative Y tile
         * @param value Axis value of the wall, depending on facing (the other axis)
         */
        public void addWallTile(BlockFace face, int x, int y, double value) {
            getWallTiles(face).addWallTile(this, x, y, value);
        }

        /**
         * Removes a previously added full-block wall tile, facing into a particular direction.
         * The x/y are relative to the facing direction (horizontally y==y and x is the
         * other axis, vertically the x==x and y is z).
         *
         * @param face Face direction of the player looking at the wall (opposite of normal)
         * @param x Face-relative X tile
         * @param y Face-relative Y tile
         */
        public void removeWallTile(BlockFace face, int x, int y) {
            getWallTiles(face).removeWallTile(this, x, y);
        }

        public void applyWallSurface(OBBSurfaceContext context, BlockFace face) {
            context.initProjector(face);

            CollisionWallTileGrid wallGrid = getWallTiles(face);

            // For all blocks orthogonal to this axis and vertical, project onto the surface
            // into the face direction and calculate the distance to the surface.
            if (FaceUtil.isAlongY(face)) {
                for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                    for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                        if (!context.project(
                                x + 0.5,
                                face == BlockFace.DOWN ? context.cuboid.max.y : context.cuboid.min.y,
                                z + 0.5)
                        ) {
                            continue;
                        }

                        wallGrid.addWallTile(this, x, z, context.projectedPos.getY() + 0.5 * face.getModY());
                    }
                }
            } else if (FaceUtil.isAlongX(face)) {
                for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                    for (int y = context.cuboid.min.y; y < context.cuboid.max.y; y++) {
                        if (!context.project(
                                face == BlockFace.EAST ? context.cuboid.max.x : context.cuboid.min.x,
                                y + 0.5,
                                z + 0.5)
                        ) {
                            continue;
                        }

                        wallGrid.addWallTile(this, z, y, context.projectedPos.getX() + 0.5 * face.getModX());
                    }
                }
            } else {
                for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                    for (int y = context.cuboid.min.y; y < context.cuboid.max.y; y++) {
                        if (!context.project(
                                x + 0.5,
                                y + 0.5,
                                face == BlockFace.SOUTH ? context.cuboid.max.z : context.cuboid.min.z)
                        ) {
                            continue;
                        }

                        wallGrid.addWallTile(this, x, y, context.projectedPos.getZ() + 0.5 * face.getModZ());
                    }
                }
            }
        }

        /**
         * Projects an axis-aligned sloped surface from above downwards onto the surface.
         *
         * @param context OBBSurfaceContext
         */
        public void applyAlignedSlopedSurface(OBBSurfaceContext context) {
            // Figure out the axis of alignment

            CollisionFloorTileShape.AlignedAxis axis = (Math.abs(context.normal.getX()) > Math.abs(context.normal.getZ()))
                    ? CollisionFloorTileShape.AlignedAxis.X : CollisionFloorTileShape.AlignedAxis.Z;

            context.initProjector(BlockFace.DOWN);

            final double maxY = context.planeMax.getY() - 0.5;

            // For all blocks on the horizontal place, compute the point on the surface
            for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                    double yp, yn;

                    if (context.project(x + 0.5 + axis.getDx(), context.cuboid.max.y, z + 0.5 + axis.getDz())) {
                        yp = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }
                    if (context.project(x + 0.5 - axis.getDx(), context.cuboid.max.y, z + 0.5 - axis.getDz())) {
                        yn = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    // Avoid shulkers spawning higher than the player, as that restricts the walkable surface
                    if (yp > maxY && yn > maxY) {
                        continue;
                    }

                    yn = Math.min(maxY, yn);
                    yp = Math.min(maxY, yp);

                    addFloorTile(x, z, new CollisionFloorTileShape.AlignedSlope(axis, yp, yn));
                }
            }
        }

        /**
         * Projects a diagonal sloped surface from above downwards onto the surface.
         *
         * @param context OBBSurfaceContext
         */
        public void applyDiagonalSlopedSurface(OBBSurfaceContext context) {
            context.initProjector(BlockFace.DOWN);

            double maxY = context.planeMax.getY() - 0.5;

            // For all blocks on the horizontal place, compute the point on the surface
            for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                    double y00, y01, y10, y11;

                    if (context.project(x + 0.25, context.cuboid.max.y, z + 0.25)) {
                        y00 = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    if (context.project(x + 0.25, context.cuboid.max.y, z + 0.75)) {
                        y01 = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    if (context.project(x + 0.75, context.cuboid.max.y, z + 0.25)) {
                        y10 = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    if (context.project(x + 0.75, context.cuboid.max.y, z + 0.75)) {
                        y11 = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    // Avoid shulkers spawning higher than the player, as that restricts the walkable surface
                    if (y00 > maxY && y01 > maxY && y10 > maxY && y11 > maxY) {
                        continue;
                    }

                    y00 = Math.min(maxY, y00);
                    y01 = Math.min(maxY, y01);
                    y10 = Math.min(maxY, y10);
                    y11 = Math.min(maxY, y11);

                    addFloorTile(x, z, new CollisionFloorTileShape.ComplexTile(y00, y01, y10, y11));
                }
            }
        }

        /**
         * Projects a level surface from above downwards onto the surface.
         *
         * @param context OBBSurfaceContext
         */
        public void applyLevelSurface(OBBSurfaceContext context) {
            context.initProjector(BlockFace.DOWN);

            double maxY = context.planeMax.getY() - 0.5;

            // For all blocks on the horizontal place, compute the point on the surface
            for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                    double y;
                    if (context.project(x + 0.5, context.cuboid.max.y, z + 0.5)) {
                        y = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    // Avoid shulkers spawning higher than the player, as that restricts the walkable surface
                    if (y > maxY) {
                        continue;
                    }

                    addFloorTile(x, z, new CollisionFloorTileShape.Level(y));
                }
            }
        }
    }
}
