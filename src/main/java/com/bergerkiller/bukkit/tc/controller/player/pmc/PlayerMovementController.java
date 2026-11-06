package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundPlayerPositionPacketHandle;
import com.bergerkiller.generated.net.minecraft.server.level.ServerPlayerHandle;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Moves a player (in first person) to a target position using velocity updates.
 * Velocity packets can be 'missed' if two of them are received in the same tick.
 * To mitigate this, it tracks incoming player input packets to detect these
 * missed velocity packets and adjust accordingly.
 */
public abstract class PlayerMovementController implements AttachmentViewer.MovementController, PacketListener {
    /**
     * Enable to diagnose desynchronization issues ingame.
     * Use /debugvar testcase true to generate test cases on first player input.
     */
    protected static final boolean DEBUG_MODE = false;

    private final ControllerType type;
    protected final AttachmentViewer viewer;
    protected final Player player;
    private boolean syncAsArmorstand = true;
    private Vector initialMotion = null;
    private Vector prevPos = null;
    private Vector lastSyncPos = null;
    private Quaternion lastAbsoluteOrientation = null;
    protected volatile boolean translateVehicleSteer = false;
    protected boolean isFlightForced = false;
    private Boolean wasFlyingBeforeControl = null;
    private boolean stopped = false;

    protected PlayerMovementController(ControllerType type, AttachmentViewer viewer) {
        this.type = type;
        this.viewer = viewer;
        this.player = viewer.getPlayer();
    }

    /**
     * Gets the Type of movement controller this is. Internal use.
     *
     * @return PlayerMovementController Type
     */
    public final ControllerType getType() {
        return type;
    }

    public void setOptions(AttachmentViewer.MovementController.Options options) {
        this.translateVehicleSteer = options.isPreserveInput();
        this.syncAsArmorstand = options.isSyncAsArmorStand();
        this.initialMotion = options.getInitialMotion();
        if (!this.syncAsArmorstand) {
            lastSyncPos = null;
        }
    }

    protected final Vector getInitialMotion() {
        return (initialMotion == null) ? new Vector() : initialMotion.clone();
    }

    /**
     * Stops this movement controller. It will stop refreshing player positions,
     * and the player will regain control like normal.
     */
    @Override
    public final void stop() {
        stopped = true;
        lastAbsoluteOrientation = null;
        onStopped();
        type.remove(this);
        setFlightForced(false);
        if (wasFlyingBeforeControl != null) {
            player.setFlying(player.getAllowFlight() && wasFlyingBeforeControl);
            wasFlyingBeforeControl = null;
        }
    }

    @Override
    public final boolean hasStopped() {
        return stopped;
    }

    @Override
    public final boolean update(
            Vector position,
            OrientationMode orientationMode,
            Quaternion orientation,
            Consumer<BlockCollisionEvent> onBlockCollision
    ) {
        if (stopped) {
            return false;
        }

        OrientationUpdate orientationUpdate = null;
        if (orientationMode != OrientationMode.NONE && orientation != null) {
            synchronized (this) {
                if (orientationMode == OrientationMode.ABSOLUTE_RESET) {
                    lastAbsoluteOrientation = orientation;
                    orientationUpdate = new OrientationUpdate(orientationMode, orientation, new Quaternion());
                } else if (orientationMode == OrientationMode.ABSOLUTE || orientationMode == OrientationMode.ABSOLUTE_LOOK_AT) {
                    Quaternion delta;
                    if (lastAbsoluteOrientation != null) {
                        delta = Quaternion.diff(lastAbsoluteOrientation, orientation);
                    } else {
                        delta = new Quaternion();
                    }
                    lastAbsoluteOrientation = orientation.clone();
                    orientationUpdate = new OrientationUpdate(orientationMode, lastAbsoluteOrientation, delta);
                } else if (orientationMode == OrientationMode.RELATIVE) {
                    if (lastAbsoluteOrientation == null) {
                        lastAbsoluteOrientation = new Quaternion();
                    }
                    lastAbsoluteOrientation = lastAbsoluteOrientation.clone();
                    lastAbsoluteOrientation.multiply(orientation);
                    orientationUpdate = new OrientationUpdate(orientationMode, lastAbsoluteOrientation, orientation.clone());
                }
            }
        }

        // Remember the original flight state of the player before we took over
        if (wasFlyingBeforeControl == null) {
            wasFlyingBeforeControl = player.isFlying();
        }

        // Important player is set to fly mode regularly
        setFlightForced(true);

        // Block collisions are swept along the actual (simulated) target path. Only the
        // position that is synchronized to the player is interpolated like an armorstand.
        // Sweeping the interpolated position instead lags behind the target and lets the
        // collision check run at a different height/position than the simulation.
        final Vector targetPosition = position.clone();

        // Ensure not null
        if (prevPos == null) {
            prevPos = player.getLocation().toVector();
        }

        BlockCollisionEvent collisionEvent = handleBlockCollisions(prevPos, targetPosition, onBlockCollision);

        if (collisionEvent != null) {
            if (collisionEvent.isStopSimulationRequested()) {
                prevPos = null; // If restarting, use player position again instead of prevPos
                return false;
            }
            if (collisionEvent.isCollisionHandlingCancelled()) {
                // Surface simulation requested a retry with additional constraints.
                // Do not mutate tracking state or send any sync yet; a later pass
                // in the same tick will sweep again from the same start position and
                // submit the final resolved position.
                return true;
            }
            Vector collisionPosition = collisionEvent.getCollisionPosition();
            MathUtil.setVector(prevPos, collisionPosition);
            if (syncAsArmorstand) {
                // Wall clamps do not teleport the player. The simulation already adopted the
                // clamped position, so just keep gliding towards it like during normal movement.
                if (lastSyncPos == null) {
                    lastSyncPos = collisionPosition.clone();
                } else {
                    lastSyncPos.add(collisionPosition.clone().subtract(lastSyncPos).multiply(1.0 / 3.0));
                }
                position = lastSyncPos;
            } else {
                position = collisionPosition;
            }
        } else {
            MathUtil.setVector(prevPos, targetPosition);
            if (syncAsArmorstand) {
                // If synchronizing as armor stand, move 1/3rd towards the target
                if (lastSyncPos == null) {
                    lastSyncPos = targetPosition.clone();
                } else {
                    lastSyncPos.add(targetPosition.clone().subtract(lastSyncPos).multiply(1.0 / 3.0));
                }
                position = lastSyncPos;
            }
        }

        syncPosition(position, orientationUpdate);
        return true;
    }

    protected abstract void syncPosition(Vector position, OrientationUpdate orientationUpdate);

    /**
     * Called when this movement controller is stopped.
     * Can be overridden to release internal resources.
     */
    protected void onStopped() {
    }

    protected void setFlightForced(boolean forced) {
        if (forced) {
            // Important player is set to fly mode regularly
            if (!player.isFlying()) {
                if (!player.getAllowFlight()) {
                    isFlightForced = true;
                    player.setAllowFlight(true);
                }

                player.setFlying(true);
            }
        } else if (isFlightForced) {
            isFlightForced = false;
            player.setAllowFlight(false);
        }
    }

    /**
     * Performs a sweep test to detect block collisions from one position to another.
     * When a collision occurs, sends a correction packet to clamp movement at the
     * collision point and fires the collision callback.
     *
     * @param from Previous position
     * @param to New position
     * @param onBlockCollision Callback fired when a block collision occurs
     * @return Collision event, or null if no collision
     */
    protected BlockCollisionEvent handleBlockCollisions(
            Vector from,
            Vector to,
            Consumer<BlockCollisionEvent> onBlockCollision
    ) {
        if (from.equals(to)) {
            return null;
        }

        AABBHandle playerBBOX = ServerPlayerHandle.fromBukkit(player).getBoundingBox();

        // Make the player bbox relative to itself
        playerBBOX = AABBHandle.createNew(
                -0.5 * (playerBBOX.getMaxX() - playerBBOX.getMinX()),
                0.0,
                -0.5 * (playerBBOX.getMaxZ() - playerBBOX.getMinZ()),
                0.5 * (playerBBOX.getMaxX() - playerBBOX.getMinX()),
                playerBBOX.getMaxY() - playerBBOX.getMinY(),
                0.5 * (playerBBOX.getMaxZ() - playerBBOX.getMinZ())
        );

        // Compute from/to bounding boxes
        AABBHandle fromBbox = playerBBOX.translate(from.getX(), from.getY(), from.getZ());
        AABBHandle toBbox = playerBBOX.translate(to.getX(), to.getY(), to.getZ());

        // Sweep to find collisions
        SweptAABB.CollisionResult result = SweptAABB.findFirstBlockCollision(fromBbox, toBbox, (x, y, z) -> {
            AABBHandle bbox = BlockUtil.getBoundingBox(player.getWorld().getBlockAt(x, y, z));
            if (bbox != null) {
                return bbox.translate(x, y, z);
            } else {
                return null;
            }
        }, viewer);
        if (result == null) {
            return null;
        }

        // Collided with a block, put the player there and apply remainder movement as velocity
        Vector delta = to.clone().subtract(from);
        Vector pos = from.clone().add(delta.clone().multiply(result.theta));
        Vector velocity = delta.clone().multiply(1.0 - result.theta);
        BlockCollisionEvent collisionEvent = new BlockCollisionEvent(
                result.block,
                result.blockX,
                result.blockY,
                result.blockZ,
                result.theta,
                result.face,
                findBlockAbove(result),
                from,
                to,
                pos,
                velocity
        );
        if (onBlockCollision != null) {
            onBlockCollision.accept(collisionEvent);
        }

        ClientboundPlayerPositionPacketHandle packet = ClientboundPlayerPositionPacketHandle.createNew(
                pos.getX(), pos.getY(), pos.getZ(), 0.0f, 0.0f,
                velocity.getX(), velocity.getY(), velocity.getZ(),
                RelativeFlags.ABSOLUTE_POSITION.withAbsoluteDelta().withRelativeRotation());

        // This must be in a synchronized block so that we do not send further packets accidentally that
        // undo our teleport sync.
        synchronized (this) {
            if (collisionEvent.isStopSimulationRequested()) {
                stop();
                viewer.send(packet);
            } else if (!collisionEvent.isCollisionHandlingCancelled()) {
                viewer.send(packet);
            }
        }
        if (DEBUG_MODE) {
            if (collisionEvent.isStopSimulationRequested()) {
                player.sendMessage("Collided with block (stopping)");
            } else if (collisionEvent.isCollisionHandlingCancelled()) {
                player.sendMessage("Collided with block (callback cancelled)");
            } else {
                player.sendMessage("Collided with block (clamped)");
            }
        }
        return collisionEvent;
    }

    private AABBHandle findBlockAbove(SweptAABB.CollisionResult result) {
        AABBHandle bounds = result.block;
        double top = bounds.getMaxY();
        final double eps = 1e-4;
        int minX = MathUtil.floor(bounds.getMinX() + eps);
        int maxX = MathUtil.floor(bounds.getMaxX() - eps);
        int minZ = MathUtil.floor(bounds.getMinZ() + eps);
        int maxZ = MathUtil.floor(bounds.getMaxZ() - eps);
        int y = MathUtil.floor(top + eps);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                AABBHandle bbox = BlockUtil.getBoundingBox(player.getWorld().getBlockAt(x, y, z));
                if (bbox != null) {
                    bbox = bbox.translate(x, y, z);
                    if (overlapsAbove(bounds, bbox, top)) {
                        return bbox;
                    }
                }
            }
        }

        final AABBHandle[] found = new AABBHandle[1];
        viewer.forAllStationaryCollisionElements(minX, y, minZ, maxX, y, maxZ, element -> {
            AABBHandle bbox = element.getBoundingBox();
            if (found[0] == null && overlapsAbove(bounds, bbox, top)) {
                found[0] = bbox;
            }
        });
        return found[0];
    }

    // Candidate must overlap the bounds horizontally and rest on or above the top of the bounds
    private static boolean overlapsAbove(AABBHandle bounds, AABBHandle candidate, double top) {
        return candidate != bounds
                && candidate.getMinY() >= top - 1e-4
                && candidate.getMinX() < bounds.getMaxX() && candidate.getMaxX() > bounds.getMinX()
                && candidate.getMinZ() < bounds.getMaxZ() && candidate.getMaxZ() > bounds.getMinZ();
    }

    /**
     * Event fired when moving the player collided with a block or stationary collision element.
     * The callback can call {@link #stopSimulation()} to stop movement control.
     */
    public static final class BlockCollisionEvent {
        private final AABBHandle blockBounds;
        private final int blockX, blockY, blockZ;
        private final double theta;
        private final BlockFace face;
        private final AABBHandle blockAboveBounds;
        private final Vector fromPosition;
        private final Vector toPosition;
        private final Vector collisionPosition;
        private final Vector remainderVelocity;
        private boolean stopSimulationRequested;
        private boolean collisionHandlingCancelled;

        public BlockCollisionEvent(
                AABBHandle blockBounds,
                int blockX, int blockY, int blockZ,
                double theta,
                BlockFace face,
                AABBHandle blockAboveBounds,
                Vector fromPosition,
                Vector toPosition,
                Vector collisionPosition,
                Vector remainderVelocity
        ) {
            this.blockBounds = blockBounds;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.theta = theta;
            this.face = face;
            this.blockAboveBounds = blockAboveBounds;
            this.fromPosition = fromPosition.clone();
            this.toPosition = toPosition.clone();
            this.collisionPosition = collisionPosition.clone();
            this.remainderVelocity = remainderVelocity.clone();
            this.stopSimulationRequested = false;
            this.collisionHandlingCancelled = false;
        }

        public AABBHandle getBlockBounds() {
            return blockBounds;
        }

        public int getBlockX() {
            return blockX;
        }

        public int getBlockY() {
            return blockY;
        }

        public int getBlockZ() {
            return blockZ;
        }

        public double getTheta() {
            return theta;
        }

        /**
         * Gets the bounds of a solid block or stationary collision element sitting directly
         * on top of the collided block, or null if there is none.
         */
        public AABBHandle getBlockAboveBounds() {
            return blockAboveBounds;
        }

        public BlockFace getFace() {
            return face;
        }

        public Vector getFromPosition() {
            return fromPosition.clone();
        }

        public Vector getToPosition() {
            return toPosition.clone();
        }

        public Vector getCollisionPosition() {
            return collisionPosition.clone();
        }

        public Vector getRemainderVelocity() {
            return remainderVelocity.clone();
        }

        public boolean isStopSimulationRequested() {
            return stopSimulationRequested;
        }

        public void stopSimulation() {
            this.stopSimulationRequested = true;
        }

        public boolean isCollisionHandlingCancelled() {
            return collisionHandlingCancelled;
        }

        public void cancelCollisionHandling() {
            this.collisionHandlingCancelled = true;
        }
    }

    /**
     * Tracks the changes to the orientation input
     */
    protected static final class OrientationUpdate {
        public final OrientationMode mode;
        public final Quaternion absolute;
        public final Quaternion delta;

        public OrientationUpdate(OrientationMode mode, Quaternion absolute, Quaternion delta) {
            this.mode = mode;
            this.absolute = absolute;
            this.delta = delta;
        }
    }

    /**
     * Forward motion vector for all possible (float) yaw values.
     * The dx/dz can be flipped around to also get the sideways motion vectors.
     */
    public static final class ForwardMotion {
        private static final float[] SIN_TABLE;
        public static final float DEG_TO_RAD = ((float)Math.PI / 180F);
        private static final ForwardMotion[] BY_YAW;
        static {
            // Compute sin table as used by Minecraft up-front
            SIN_TABLE = new float[65536];
            for(int i = 0; i < SIN_TABLE.length; ++i) {
                SIN_TABLE[i] = (float)Math.sin((double)i * Math.PI * 2.0D / 65536.0D);
            }

            // Initialize forward motion by taking the sin and cos values of all input indices
            BY_YAW = new ForwardMotion[SIN_TABLE.length];
            for(int i = 0; i < BY_YAW.length; ++i) {
                BY_YAW[i] = new ForwardMotion(-SIN_TABLE[i], SIN_TABLE[(i + 16384) & '\uffff']);
            }
        }
        public final double dx;
        public final double dz;

        public ForwardMotion(float dx, float dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public static ForwardMotion get(float yaw) {
            float yaw_idx = (yaw * DEG_TO_RAD) * 10430.378F;
            int idx_sin = (int) yaw_idx & '\uffff';
            int idx_cos = (int) (yaw_idx + 16384.0F) & '\uffff';

            // Most common case: sin and cos share a common offset
            if (((idx_sin + 16384) & '\uffff') == idx_cos) {
                return BY_YAW[idx_sin];
            }

            // Weird edge case where sin and cos sit in different neighbouring indices of the table
            return new ForwardMotion(-SIN_TABLE[idx_sin], SIN_TABLE[idx_cos]);
        }

        @Override
        public String toString() {
            return "{dx=" +dx + ", dz=" + dz + "}";
        }
    }

    /**
     * Combines the AttachmentViewer Input API with the horizontal/vertical/sprint inputs that
     * the movement controller uses.
     */
    public static final class ComposedInput {
        public static final ComposedInput NONE = new ComposedInput(AttachmentViewer.Input.NONE);

        public final HorizontalPlayerInput horizontal;
        public final VerticalPlayerInput vertical;
        public final boolean sprinting;
        public final AttachmentViewer.Input input;

        public ComposedInput(AttachmentViewer.Input input) {
            this.horizontal = HorizontalPlayerInput.fromSteer(
                    input.left(), input.right(), input.forwards(), input.backwards()
            );
            this.vertical = VerticalPlayerInput.fromSteer(
                    input.jumping(), input.sneaking()
            );
            this.sprinting = input.sprinting();
            this.input = input;
        }

        public ComposedInput(
                final HorizontalPlayerInput horizontal,
                final VerticalPlayerInput vertical,
                final boolean sprinting
        ) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.sprinting = sprinting;
            this.input = AttachmentViewer.Input.of(
                    horizontal.left(),
                    horizontal.right(),
                    horizontal.forwards(),
                    horizontal.backwards(),
                    vertical == VerticalPlayerInput.JUMP,
                    vertical == VerticalPlayerInput.SNEAK,
                    sprinting);
        }

        public ComposedInput withSprinting(boolean sprinting) {
            return new ComposedInput(input.withSprinting(sprinting));
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof ComposedInput) {
                ComposedInput other = (ComposedInput) o;
                return this.input.equals(other.input);
            } else {
                return false;
            }
        }
    }

    /**
     * A type of vertical (Spacebar/Shift) input the player can provide
     */
    public static enum VerticalPlayerInput {
        NONE(0.0f),
        SNEAK(-3.0f),
        JUMP(3.0f);

        private final float yya;

        private VerticalPlayerInput(float yya) {
            this.yya = yya;
        }

        public double getMotion(float speed) {
            return (speed * this.yya);
        }

        public static VerticalPlayerInput fromSteer(boolean jump, boolean sneak) {
            if (jump == sneak) {
                return NONE;
            } else {
                return jump ? JUMP : SNEAK;
            }
        }
    }

    /**
     * A type of horizontal (WASD) input the player can provide
     */
    public static enum HorizontalPlayerInput {

        NONE(0.0, 0.0),
        FORWARDS(0.0, 1.0),
        BACKWARDS(0.0, -1.0),
        LEFT(1.0, 0.0f),
        RIGHT(-1.0, 0.0f),
        FORWARDS_LEFT(1.0, 1.0),
        FORWARDS_RIGHT(-1.0, 1.0),
        BACKWARDS_LEFT(1.0, -1.0),
        BACKWARDS_RIGHT(-1.0, -1.0);

        static {
            // This stores the most likely next horizontal input received given a current horizontal input
            // For example, we assume when pressing forward the user is more likely to press left/right+forward than backwards
            // This is purely a performance optimization and might eliminate ghost inputs in some situations by
            // matching the correct input early.
            //TODO: There's probably a cleaner way of doing this...
            NONE            .setNext( NONE, FORWARDS, LEFT, RIGHT, BACKWARDS, FORWARDS_LEFT, FORWARDS_RIGHT, BACKWARDS_LEFT, BACKWARDS_RIGHT );
            FORWARDS        .setNext( FORWARDS, NONE, FORWARDS_LEFT, FORWARDS_RIGHT, BACKWARDS, LEFT, RIGHT, BACKWARDS_LEFT, BACKWARDS_RIGHT );
            BACKWARDS       .setNext( BACKWARDS, NONE, BACKWARDS_LEFT, BACKWARDS_RIGHT, FORWARDS, LEFT, RIGHT, FORWARDS_LEFT, FORWARDS_RIGHT );
            LEFT            .setNext( LEFT, NONE, FORWARDS_LEFT, BACKWARDS_LEFT, RIGHT, FORWARDS, BACKWARDS, FORWARDS_RIGHT, BACKWARDS_RIGHT );
            RIGHT           .setNext( RIGHT, NONE, FORWARDS_RIGHT, BACKWARDS_RIGHT, LEFT, FORWARDS, BACKWARDS, FORWARDS_LEFT, BACKWARDS_LEFT );
            FORWARDS_LEFT   .setNext( FORWARDS_LEFT, FORWARDS, LEFT, NONE, FORWARDS_RIGHT, RIGHT, BACKWARDS, BACKWARDS_LEFT, BACKWARDS_RIGHT );
            FORWARDS_RIGHT  .setNext( FORWARDS_RIGHT, FORWARDS, RIGHT, NONE, FORWARDS_LEFT, LEFT, BACKWARDS, BACKWARDS_RIGHT, BACKWARDS_LEFT );
            BACKWARDS_LEFT  .setNext( BACKWARDS_LEFT, BACKWARDS, LEFT, NONE, BACKWARDS_RIGHT, FORWARDS, RIGHT, FORWARDS_LEFT, FORWARDS_RIGHT );
            BACKWARDS_RIGHT .setNext( BACKWARDS_RIGHT, BACKWARDS, RIGHT, NONE, BACKWARDS_LEFT, FORWARDS, LEFT, FORWARDS_RIGHT, FORWARDS_LEFT );
        }

        private final double left, forward;
        private final boolean is_diagonal;
        private HorizontalPlayerInput[] next;

        HorizontalPlayerInput(double left, double forward) {
            this.left = left;
            this.forward = forward;
            this.is_diagonal = (left != 0.0 && forward != 0.0);
        }

        public double forwardSteerInput() {
            return forward;
        }

        public double leftSteerInput() {
            return left;
        }

        public boolean forwards() {
            return forward > 0.0;
        }

        public boolean backwards() {
            return forward < 0.0;
        }

        public boolean left() {
            return left > 0.0;
        }

        public boolean right() {
            return left < 0.0;
        }

        /** When player pressed both the forward/backward and the left/right button */
        public boolean diagonal() {
            return is_diagonal;
        }

        // Set in c;init
        private void setNext(HorizontalPlayerInput... next) {
            this.next = next;
        }

        /**
         * Gets the next horizontal inputs to test against if this horizontal
         * input is the current one.
         *
         * @return next inputs
         */
        public HorizontalPlayerInput[] getNextLikelyInputs() {
            return this.next;
        }

        public static HorizontalPlayerInput fromSteer(boolean left, boolean right, boolean forwards, boolean backwards) {
            if (left && right) {
                left = right = false;
            }
            if (forwards && backwards) {
                forwards = backwards = false;
            }
            if (forwards) {
                if (left) {
                    return FORWARDS_LEFT;
                } else if (right) {
                    return FORWARDS_RIGHT;
                } else {
                    return FORWARDS;
                }
            } else if (backwards) {
                if (left) {
                    return BACKWARDS_LEFT;
                } else if (right) {
                    return BACKWARDS_RIGHT;
                } else {
                    return BACKWARDS;
                }
            } else if (left) {
                return LEFT;
            } else if (right) {
                return RIGHT;
            } else {
                return NONE;
            }
        }
    }

    /**
     * Type of PlayerMovementController implementation. Keeps track of the packet listener
     * used for instances of this type.
     */
    public static final class ControllerType implements PacketListener {
        private static final boolean HAS_INPUT_PACKET = Common.evaluateMCVersion(">=", "1.21.2");

        private final BiFunction<ControllerType, AttachmentViewer, ? extends PlayerMovementController> factory;
        private final PacketType[] listenedPacketTypes;
        private final List<PlayerMovementController> activeControllers = new ArrayList<>();
        private List<PlayerMovementController> activeControllersView = Collections.emptyList();

        // Type implementations
        public static final ControllerType LEGACY = new ControllerType(PlayerMovementControllerPredictedLegacy::new,
                PacketType.IN_POSITION, PacketType.IN_POSITION_LOOK, PacketType.IN_ABILITIES);
        public static final ControllerType MODERN = new ControllerType(PlayerMovementControllerPredictedModern::new,
                PacketType.IN_CLIENT_TICK_END,
                PacketType.IN_POSITION, PacketType.IN_POSITION_LOOK,
                PacketType.IN_STEER_VEHICLE, PacketType.IN_ABILITIES, PacketType.IN_PLAYER_COMMAND);
        public static final ControllerType SPECTATED = new ControllerType(PlayerMovementControllerSpectated::new,
                PacketType.IN_CLIENT_TICK_END, PacketType.IN_STEER_VEHICLE);

        /**
         * Selects the most appropriate controller type to use for a certain player viewer.
         *
         * @param viewer AttachmentViewer
         * @return ControllerType
         */
        public static ControllerType forViewer(AttachmentViewer viewer) {
            return SPECTATED;

            // Old predicted PMC logic
            // if (HAS_INPUT_PACKET && viewer.evaluateGameVersion(">=", "1.21.2")) {
            //     return MODERN;
            // } else {
            //     return LEGACY;
            // }
        }

        public ControllerType(BiFunction<ControllerType, AttachmentViewer, ? extends PlayerMovementController> factory, PacketType... listenedPacketTypes) {
            this.factory = factory;
            this.listenedPacketTypes = listenedPacketTypes;
        }

        public synchronized PlayerMovementController create(AttachmentViewer viewer) {
            PlayerMovementController controller = factory.apply(this, viewer);
            activeControllers.add(controller);
            activeControllersView = Collections.unmodifiableList(new ArrayList<>(activeControllers));
            if (activeControllers.size() == 1 && listenedPacketTypes.length > 0) {
                viewer.getTrainCarts().register(this, this.listenedPacketTypes);
            }
            return controller;
        }

        public synchronized void remove(PlayerMovementController controller) {
            if (activeControllers.remove(controller)) {
                activeControllersView = Collections.unmodifiableList(new ArrayList<>(activeControllers));
                if (activeControllers.isEmpty() && listenedPacketTypes.length > 0) {
                    controller.viewer.getTrainCarts().unregister(this);
                }
            }
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            final Player player = event.getPlayer();
            for (PlayerMovementController controller : activeControllersView) {
                if (controller.player == player) {
                    controller.onPacketReceive(event);
                }
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            final Player player = event.getPlayer();
            for (PlayerMovementController controller : activeControllersView) {
                if (controller.player == player) {
                    controller.onPacketSend(event);
                }
            }
        }
    }
}
