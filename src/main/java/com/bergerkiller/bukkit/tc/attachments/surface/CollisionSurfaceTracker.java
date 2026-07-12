package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerPositionPacketHandle;
import com.bergerkiller.generated.net.minecraft.server.level.ServerPlayerHandle;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;
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
    private static final double SIMULATED_WALK_ACCELERATION = 0.1;
    private static final double SIMULATED_AIR_MOVEMENT_SPEED = 0.06;
    static final double SIMULATED_WALK_VELOCITY_DAMPING = 0.91 * 0.6;
    private static final double SIMULATED_GRAVITY = 0.15;
    private static final double SIMULATED_JUMP_IMPULSE = 1.2;
    /** Minimum speed under which the player's simulated velocity is considered zero */
    private static double MINIMUM_PLAYER_SPEED = 0.003;
    /** Maximum steepness (degrees) at which player input is still respected. Beyond this angle input is disabled. */
    private static final double MAX_INPUT_ANGLE = 80.0;
    /** Angle (degrees) at which gravity-based sliding starts scaling from 0 (at this angle) to full at 90 degrees. */
    private static final double GRAVITY_START_ANGLE = 50.0;
    private final AttachmentViewer viewer;
    private final int shulkerViewDistance;
    private final PlayerPusher playerPusher;
    private final ShulkerTracker shulkerCache;
    private final CollisionFloorTileGrid floorTiles;
    private final EnumMap<BlockFace, CollisionWallTileGrid> wallTiles;
    private final List<CollisionSurfaceTrackerImpl> surfaces = new ArrayList<>();
    private SimulatedPlayer simulatedPlayer = null;
    private AttachmentViewer.MovementController pmc = null;
    private Vector previousPlayerPosition = null;

    // Shared solver instance for this tracker class. Initialized with a logger that
    // routes messages to the plugin logger (same behavior as the previous
    // PlayerCollisionSolver.setGlobalLog(...) call in the constructor).
    private static final PlayerCollisionLogger PLAYER_COLLISION_LOGGER = new PlayerCollisionLogger() {
        private final java.util.logging.Logger L = (TrainCarts.plugin != null) ? TrainCarts.plugin.getLogger() : null;
        @Override public void info(String msg) { if (L != null) L.info(msg); }
        @Override public void warn(String msg) { if (L != null) L.warning(msg); }
        @Override public void debug(String msg) { if (L != null) L.info(msg); }
        @Override public boolean isEnabled() { return false; }
        @Override public void debugCandidate(OBBSurfaceTransition<?> st, double bestTheta, boolean isVertical, boolean feetCrossed, boolean headCrossed, Vector[] fromCorners, Vector[] toCorners) {}
        @Override public void debugFeetClamp(OBBSurfaceTransition<?> st, double dy, double theta) {}
        @Override public void debugHeadClamp(OBBSurfaceTransition<?> st, double dy, double theta) {}
        @Override public void debugWallClamp(OBBSurfaceTransition<?> st, double dx, double dy, double dz) {}
        @Override public void debugMultiSurfacePick(double bestScore, OBBSurfaceState lastSurfaceFromState) {}
        @Override public void debugFeetLog(AABBHandle aabb, OBBSurfaceState surf, double y, double minY) {}
        @Override public void debugHeadLog(AABBHandle aabb, OBBSurfaceState surf, double y, double maxY) {}
    };
    private static final PlayerCollisionSolver PLAYER_COLLISION_SOLVER = new PlayerCollisionSolver(PLAYER_COLLISION_LOGGER);

    public CollisionSurfaceTracker(AttachmentViewer viewer, int shulkerViewDistance) {
        this.viewer = viewer;
        this.shulkerViewDistance = shulkerViewDistance;

        this.playerPusher = new PlayerPusher(viewer);
        this.shulkerCache = new ShulkerTracker();
        this.floorTiles = new CollisionFloorTileGrid(shulkerCache);
        this.wallTiles = new EnumMap<>(BlockFace.class);
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
        List<OBBSurfaceTransition<CollisionSurfaceTrackerImpl>> transitions = this.surfaces.stream()
                .map(s -> new OBBSurfaceTransition<>(s.shape, s.shape, s))
                .collect(Collectors.toList());

        PlayerCollisionSolver.Result<CollisionSurfaceTrackerImpl> solution = PLAYER_COLLISION_SOLVER.solveDetailed(transitions, new PlayerTransition(from, to));
        if (!solution.hasCollision()) {
            return CollisionSurface.DISABLED;
        }

        return solution.lastSurface;
    }

    /**
     * Called every tick to update the display of shulkers for all surface shapes that
     * have been added. Also cleans up information of cleared collision surfaces
     * (that have not received further updates).
     */
    public void update() {
        // Update all surfaces, and if they are not moving, spawn in shulkers for them
        for (CollisionSurfaceTrackerImpl surface : surfaces) {
            surface.nextUpdate();

            if (!surface.isSimulated()) {
                surface.spawnShulkers();
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
            stopSimulatedPlayer(null);
        }

        // Compute all of the surface transitions
        List<OBBSurfaceTransition<CollisionSurfaceTrackerImpl>> transitions = this.surfaces.stream()
                .filter(CollisionSurfaceTrackerImpl::isSimulated)
                .map(s -> new OBBSurfaceTransition<>(s.prevShape, s.shape, s))
                .collect(Collectors.toList());

        surfaces.forEach(CollisionSurfaceTrackerImpl::savePrevShape);

        ServerPlayerHandle playerHandle = ServerPlayerHandle.fromBukkit(viewer.getPlayer());
        AABBHandle bboxTo = playerHandle.getBoundingBox();
        if (simulatedPlayer != null) {
            if (viewer.getPlayer().isSneaking()) {
                stopSimulatedPlayer(viewer.getPlayer().getLocation().toVector());
                return;
            }
            updateSimulatedPlayer(transitions, bboxTo);
            return;
        }

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
        PlayerTransition playerTransition = new PlayerTransition(bboxFrom, bboxTo);

        PlayerCollisionSolver.Result<CollisionSurfaceTrackerImpl> solution = PLAYER_COLLISION_SOLVER.solveDetailed(
                transitions,
                playerTransition
        );

        // If the solver modifies the player bounds drastically (in Y), emit a repro snippet
        // only for the surface that triggered the adjustment (if any). We consider either the
        // minY or maxY changing by more than 50 units to be drastic.
        try {
            double deltaMinY = Math.abs(solution.bounds.getMinY() - playerTransition.to.bounds.getMinY());
            double deltaMaxY = Math.abs(solution.bounds.getMaxY() - playerTransition.to.bounds.getMaxY());
            if (deltaMinY > 10.0 || deltaMaxY > 10.0) {
                TrainCarts.plugin.getLogger().info("Big jump detected, new bounds: " + solution.bounds);
                logReproSnippet(solution.involvedTransitions, playerTransition.from.bounds, playerTransition.to.bounds);
            }
        } catch (Throwable t) {
            // Be defensive: don't let logging failures break update
        }

        MathUtil.setVector(previousPlayerPosition, position);

        if (!solution.bounds.equals(playerTransition.to.bounds)) {
            Vector solPos = new Vector(
                    (solution.bounds.getMinX() + solution.bounds.getMaxX()) / 2.0,
                    solution.bounds.getMinY(),
                    (solution.bounds.getMinZ() + solution.bounds.getMaxZ()) / 2.0);
            CommonUtil.broadcast("Player passed through [" + solPos + "] mode=" + solution.lastCollisionMode);

            /*
            TrainCarts.plugin.getLogger().info("Collision solver adjusted player for viewer=" + viewer.getPlayer().getName()
                    + " mode=" + solution.lastCollisionMode
                    + " surface=" + solution.lastSurface);
            TrainCarts.plugin.getLogger().info("Player position from=" + previousPosition + " to=" + position + " movement=" + movement);
            TrainCarts.plugin.getLogger().info("Player bbox from=" + bboxFrom + " to=" + bboxTo + " solved=" + solution.bounds);
            if (solution.lastSurfaceFromState != null && solution.lastSurfaceToState != null) {
                TrainCarts.plugin.getLogger().info("Surface transition fromPos=" + solution.lastSurfaceFromState.center
                        + " toPos=" + solution.lastSurfaceToState.center
                        + " fromHalfSize=" + solution.lastSurfaceFromState.halfSize
                        + " toHalfSize=" + solution.lastSurfaceToState.halfSize);
                TrainCarts.plugin.getLogger().info("Surface transition fromNormal=" + solution.lastSurfaceFromState.normal
                        + " toNormal=" + solution.lastSurfaceToState.normal);
            }
             */

                if (!viewer.getPlayer().isSneaking()) {
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
        }

        // Legacy active-surface / shulker switching code removed: movement is handled by the
        // simulated-player logic and the collision solver. We simply track the player's
        // actual position for purposes such as shulker spawning.
    }

    private boolean startSimulatedPlayer(CollisionSurfaceTrackerImpl surface, Vector lastPosition, Vector position, Vector velocity) {
        AttachmentViewer.MovementController controller = viewer.controlMovement();
        SimulatedPlayer simulatedPlayer = new SimulatedPlayer(
                viewer,
                controller,
                lastPosition.clone(),
                position.clone(),
                velocity.clone(),
                surface,
                false
        );

        if (!controller.update(position, true)) {
            controller.stop();
            return false;
        }

        simulatedPlayer.lastJumpInput = controller.getInput().jumping();
        this.simulatedPlayer = simulatedPlayer;
        this.pmc = controller;
        return true;
    }


    private void stopSimulatedPlayer(Vector trackingPosition) {
        if (this.simulatedPlayer != null && this.simulatedPlayer.pmc != null && !this.simulatedPlayer.pmc.hasStopped()) {
            this.simulatedPlayer.pmc.stop();
        }
        this.simulatedPlayer = null;
        this.pmc = null;
        if (trackingPosition != null) {
            if (this.previousPlayerPosition == null) {
                this.previousPlayerPosition = trackingPosition.clone();
            } else {
                MathUtil.setVector(this.previousPlayerPosition, trackingPosition);
            }
        }
    }

    private void updateSimulatedPlayer(List<OBBSurfaceTransition<CollisionSurfaceTrackerImpl>> transitions, AABBHandle actualBounds) {
        SimulatedPlayer simulatedPlayer = this.simulatedPlayer;
        if (simulatedPlayer == null) {
            return;
        }
        if (simulatedPlayer.pmc == null || simulatedPlayer.pmc.hasStopped()) {
            stopSimulatedPlayer(simulatedPlayer.position);
            return;
        }

        Vector currentPosition = simulatedPlayer.position.clone();
        Vector nextPosition = currentPosition.clone();
        Vector surfaceCarryVelocity = new Vector(); // carry velocity of the surface the player is on; zero when not on a surface
        CollisionSurfaceTrackerImpl currentSurface = toSurfaceImpl(simulatedPlayer.lastSurface);
        OBBSurfaceTransition<CollisionSurfaceTrackerImpl> activeTransition = null; // transition for the surface the player is standing on (null if flying/no surface)
        AttachmentViewer.Input input = simulatedPlayer.pmc.getInput();

        boolean justWalkedOffSurface = false;

        if (simulatedPlayer.flying) {
            // Simulate flying / falling off surfaces
            simulatedPlayer.velocity.add(createAirMovementAdjustment(simulatedPlayer.viewer.getPlayer().getEyeLocation().getYaw(), input));
            nextPosition.add(simulatedPlayer.velocity);
            simulatedPlayer.velocity.setY(simulatedPlayer.velocity.getY() - SIMULATED_GRAVITY);
        } else if (currentSurface != null) {
            OBBSurfaceTransition<CollisionSurfaceTrackerImpl> transition = findTransition(transitions, currentSurface);
            if (transition != null) {
                activeTransition = transition;
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
                simulatedPlayer.velocity = updateWalkingVelocity(simulatedPlayer.velocity, walkInput.acceleration, transition.to);

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
                    simulatedPlayer.velocity = computeJumpVelocity(simulatedPlayer.velocity.add(surfaceCarryVelocity));
                    surfaceCarryVelocity = new Vector(); // carry already folded into velocity; don't apply to nextPosition again
                } else if (gravityFactor <= 0.0) {
                    if (disableInput && simulatedPlayer.velocity.getY() > 0.0) {
                        simulatedPlayer.velocity.setY(0.0);
                    }
                } else {
                    // Apply sliding downward along the surface plane proportional to steepness.
                    // Compute downhill direction by projecting world-down onto the surface plane.
                    Vector downhillDir = projectOntoPlane(new Vector(0.0, -1.0, 0.0), transition.to.groundNormal);
                    if (downhillDir.lengthSquared() < 1e-12) {
                        // Fallback: if projection is nearly zero (surface almost vertical), slide along -zAxis
                        downhillDir = transition.to.zAxis.clone().multiply(-1.0);
                    }
                    downhillDir.normalize();
                    Vector downhillVel = downhillDir.clone().multiply(SIMULATED_GRAVITY * gravityFactor);

                    // Carry is applied to nextPosition separately; velocity is pure player movement (downhill slide)
                    simulatedPlayer.velocity = downhillVel.clone();
                }

                // Advance position: first carry the player with the moving surface, then add player's own velocity.
                // For jump/walk-off the carry was already folded into velocity above; surfaceCarryVelocity is zeroed.
                nextPosition.add(surfaceCarryVelocity);
                nextPosition.add(simulatedPlayer.velocity);

                // Detect when the player walks/falls off the surface, and toggle flying on right away
                if (!simulatedPlayer.flying && !transition.hasSurfaceSupport(actualBounds, nextPosition, SIMULATED_GRAVITY, PLAYER_COLLISION_SOLVER)) {
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

        Vector actualFeetPosition = PlayerState.feetPosition(actualBounds);
        AABBHandle bboxFrom = actualBounds.translate(
                currentPosition.getX() - actualFeetPosition.getX(),
                currentPosition.getY() - actualFeetPosition.getY(),
                currentPosition.getZ() - actualFeetPosition.getZ());
        AABBHandle bboxTo = bboxFrom.translate(
                nextPosition.getX() - currentPosition.getX(),
                nextPosition.getY() - currentPosition.getY(),
                nextPosition.getZ() - currentPosition.getZ());

        // When the player is grounded, snap bboxTo vertically onto the surface plane.
        // This eliminates AABB-corner vs. sloped-surface penetration before the solver runs,
        // so the solver no longer sees a false FEET collision from the walking surface itself.
        if (!simulatedPlayer.flying && activeTransition != null) {
            AABBHandle bboxToSnapped = activeTransition.to.placeBoundsOnSurface(bboxTo);
            if (!bboxToSnapped.equals(bboxTo)) {
                bboxTo = bboxToSnapped;
                nextPosition = PlayerState.feetPosition(bboxTo);
            }
        }

        PlayerCollisionSolver.Result<CollisionSurfaceTrackerImpl> solution = PLAYER_COLLISION_SOLVER.solveDetailed(
                transitions,
                new PlayerTransition(bboxFrom, bboxTo)
        );

        boolean hasCornerPassedThrough = false;
        for (OBBSurfaceTransition<CollisionSurfaceTrackerImpl> surface : transitions) {
            if (surface.hasCornerPassedThrough(new PlayerTransition(bboxFrom, solution.bounds))) {
                if (!solution.involvedTransitions.contains(surface)) {
                    solution.involvedTransitions.add(surface);
                }
                TrainCarts.plugin.getLogger().info("CORNER PASSED THROUGH [" + CommonUtil.getServerTicks() + "] surface=" + surface.source);
                TrainCarts.plugin.getLogger().info("SOLUTION BOUNDS: " + solution.bounds);
                hasCornerPassedThrough = true;
            } else if ( surface.to.isClippingThrough(new PlayerState(solution.bounds))) {
                if (!solution.involvedTransitions.contains(surface)) {
                    solution.involvedTransitions.add(surface);
                }
                TrainCarts.plugin.getLogger().info("SURFACE CLIPPING [" + CommonUtil.getServerTicks() + "] surface=" + surface.source);
                hasCornerPassedThrough = true;
            }
        }
        if (hasCornerPassedThrough) {
            TrainCarts.plugin.getLogger().info("DEBUG OUTPUT:\n" + solution.printDebugTest());

            if (simulatedPlayer.lastDebugState != null) {
                StringBuilder str = new StringBuilder();
                simulatedPlayer.lastDebugState.printDebugCreate(str, "");
                TrainCarts.plugin.getLogger().info("PREV STATE:\n" + str.toString());
            }
        }

        boolean hasSurfaceCollision = !solution.bounds.equals(bboxTo);
        if (!hasSurfaceCollision && justWalkedOffSurface) {
            solution = PLAYER_COLLISION_SOLVER.solveBelowFeetDetailed(transitions, bboxTo);
            hasSurfaceCollision = solution.hasCollision();
        }
        if (hasSurfaceCollision) {
            nextPosition = PlayerState.feetPosition(solution.bounds);
            if (solution.lastSurface != null && isFloorLikeLanding(solution)) {
                simulatedPlayer.lastSurface = solution.lastSurface;
                simulatedPlayer.flying = false;
                simulatedPlayer.velocity.setX(0.0);
                simulatedPlayer.velocity.setY(0.0);
                simulatedPlayer.velocity.setZ(0.0);
            } else if (!simulatedPlayer.flying) {
                simulatedPlayer.velocity = removeIntoSurfaceVelocity(
                        simulatedPlayer.velocity,
                        solution.lastCollisionMode,
                        solution.lastSurfaceToState
                );
            } else {
                /*
                simulatedPlayer.velocity = removeIntoSurfaceVelocity(
                        simulatedPlayer.velocity,
                        solution.lastCollisionMode,
                        solution.lastSurfaceToState
                );
                 */
            }
        }

        if (!simulatedPlayer.pmc.update(nextPosition, true)) {
            stopSimulatedPlayer(currentPosition);
            return;
        }

        simulatedPlayer.lastPosition = currentPosition;
        simulatedPlayer.lastDebugState = new PlayerState(solution.bounds);
        simulatedPlayer.position = nextPosition;
        simulatedPlayer.lastJumpInput = input.jumping();
    }

    private WalkInput createWalkInput(SimulatedPlayer simulatedPlayer, OBBSurfaceState surface, AttachmentViewer.Input input) {
        if (!input.hasWalkInput()) {
            return WalkInput.NONE;
        }

        // Use an upward-facing normal for input basis so that upside-down surfaces
        // don't invert left/right controls. The projection onto the plane is sign
        // independent, but the cross product for left-hand direction depends on
        // the sign of the normal. Use a canonical 'up' vector here.
        Vector forward = projectOntoPlane(createHorizontalDirectionFromYaw(simulatedPlayer.viewer.getPlayer().getEyeLocation().getYaw()), surface.groundNormal);
        if (forward.lengthSquared() < 1e-20) {
            forward = surface.zAxis.clone();
        } else {
            forward.normalize();
        }

        Vector left = cross(surface.groundNormal, forward);
        if (left.lengthSquared() < 1e-20) {
            left = surface.xAxis.clone().multiply(-1.0);
        } else {
            left.normalize();
        }

        Vector acceleration = new Vector();
        double forwardInput = input.forwardsSigNum();
        double sidewaysInput = input.sidewaysSigNum();
        if (forwardInput != 0.0) {
            acceleration.add(forward.clone().multiply(SIMULATED_WALK_ACCELERATION * forwardInput));
        }
        if (sidewaysInput != 0.0) {
            acceleration.add(left.clone().multiply(SIMULATED_WALK_ACCELERATION * sidewaysInput));
        }
        if (acceleration.lengthSquared() < 1e-20) {
            return WalkInput.NONE;
        }

        if (Math.abs(acceleration.getY()) <= 1e-3) {
            // No uphill component
            acceleration = projectOntoPlane(acceleration, surface.normal);
            return new WalkInput(acceleration, false, 0.0);
        }

        Vector direction = acceleration.clone().normalize();
        double angleDeg = Math.toDegrees(Math.asin(direction.getY()));
        if (angleDeg >= MAX_INPUT_ANGLE) {
            return new WalkInput(new Vector(), true, angleDeg);
        }

        double factor = computeUphillWalkSpeedFactor(angleDeg);
        acceleration.multiply(Math.max(0.0, factor));
        // Ensure walk input does not add any component along the surface normal
        acceleration = projectOntoPlane(acceleration, surface.normal);
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


    static Vector updateWalkingVelocity(Vector velocity, Vector acceleration, OBBSurfaceState surface) {
        // Apply damping to the existing velocity first, then add acceleration.
        Vector result = velocity.clone();
        result.multiply(SIMULATED_WALK_VELOCITY_DAMPING);
        result.add(acceleration);
        result = projectOntoPlane(result, surface.normal);
        // If the resulting velocity is extremely small, treat it as zero. Use the
        // configured minimum player speed threshold so this behavior is centralized.
        if (result.lengthSquared() < (MINIMUM_PLAYER_SPEED * MINIMUM_PLAYER_SPEED)) {
            return new Vector();
        }
        return result;
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

    static Vector computeJumpVelocity(Vector velocity) {
        Vector result = velocity.clone();
        result.setY(result.getY() + SIMULATED_JUMP_IMPULSE);
        return result;
    }

    static Vector createAirMovementAdjustment(float eyeYaw, AttachmentViewer.Input input) {
        if (input == null || !input.hasWalkInput()) {
            return new Vector();
        }

        Vector forward = createHorizontalDirectionFromYaw(eyeYaw);

        Vector left = cross(new Vector(0.0, 1.0, 0.0), forward);
        if (left.lengthSquared() < 1e-20) {
            left.setX(1.0);
            left.setY(0.0);
            left.setZ(0.0);
        } else {
            left.normalize();
        }

        Vector adjustment = new Vector();
        double forwardInput = input.forwardsSigNum();
        double sidewaysInput = input.sidewaysSigNum();
        if (forwardInput != 0.0) {
            adjustment.add(forward.multiply(SIMULATED_AIR_MOVEMENT_SPEED * forwardInput));
        }
        if (sidewaysInput != 0.0) {
            adjustment.add(left.multiply(SIMULATED_AIR_MOVEMENT_SPEED * sidewaysInput));
        }
        adjustment.setY(0.0);
        return adjustment;
    }

    static Vector createHorizontalDirectionFromYaw(float yaw) {
        double yawRad = Math.toRadians(yaw);
        return new Vector(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
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

    private static Vector projectOntoPlane(Vector vector, Vector normal) {
        Vector result = vector.clone();
        double dot = result.dot(normal);
        result.subtract(normal.clone().multiply(dot));
        return result;
    }

    private static Vector cross(Vector a, Vector b) {
        return new Vector(
                a.getY() * b.getZ() - a.getZ() * b.getY(),
                a.getZ() * b.getX() - a.getX() * b.getZ(),
                a.getX() * b.getY() - a.getY() * b.getX()
        );
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

    private static CollisionSurfaceTrackerImpl toSurfaceImpl(CollisionSurface surface) {
        return (surface instanceof CollisionSurfaceTrackerImpl) ? (CollisionSurfaceTrackerImpl) surface : null;
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

    private static OBBSurfaceTransition<CollisionSurfaceTrackerImpl> findTransition(List<OBBSurfaceTransition<CollisionSurfaceTrackerImpl>> transitions, CollisionSurfaceTrackerImpl surface) {
        for (OBBSurfaceTransition<CollisionSurfaceTrackerImpl> transition : transitions) {
            if (transition.source == surface) {
                return transition;
            }
        }
        return null;
    }

    // Emit a small reproducible snippet to the server log for debugging solver issues.
    // All lines are combined into a single log entry separated by newlines to make
    // copy/pasting into a unit test easier.
    private static <T> void logReproSnippet(List<OBBSurfaceTransition<T>> transitions, AABBHandle playerFrom, AABBHandle playerTo) {
        try {
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
        } catch (Throwable ignored) {
            // Don't let logging break anything
        }
    }

    /**
     * Creates a new CollisionSurface. Until further add methods are called on the surface,
     * nothing else is stored or tracked.
     *
     * @return CollisionSurface
     */
    public CollisionSurface createSurface() {
        return new CollisionSurfaceTrackerImpl();
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

    // Legacy active-surface helpers removed: simulated-player logic supersedes these.

    /**
     * A single collision surface spawned in using the API for this viewer
     */
    private class CollisionSurfaceTrackerImpl implements CollisionSurface {
        private int updateCounter = 0;
        private String debugName = null;
        private OrientedBoundingBox shape;
        private OrientedBoundingBox prevShape;
        private int moveDetectedAtCount = 0;
        private boolean useShulkersWhenNotMoving = true;
        private boolean isMoving = false;

        @Override
        public boolean isSimulated() {
            return isMoving || !useShulkersWhenNotMoving;
        }

        @Override
        public void setUseShulkers(boolean useShulkers) {
            useShulkersWhenNotMoving = useShulkers;
        }

        @Override
        public String toString() {
            return "CollisionSurfaceTrackerImpl{moving=" + isMoving
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
                        // Moving downwards
                        this.shape.setPosition(newPos);
                        return;
                    }
                }
            }

            // Shape changed position/orientation/size in a way that acts as a moving surface
            // This despawns shulkers / keeps shulkers de-spawned and switches to movement control
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

        public void spawnShulkers() {
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
