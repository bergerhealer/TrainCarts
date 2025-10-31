package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Moves a player (in first person) to a target position using velocity updates.
 * Velocity packets can be 'missed' if two of them are received in the same tick.
 * To mitigate this, it tracks incoming player input packets to detect these
 * missed velocity packets and adjust accordingly.
 */
public abstract class PlayerMovementController implements AttachmentViewer.MovementController, PacketListener {
    private final ControllerType type;
    protected final AttachmentViewer viewer;
    protected final Player player;
    private boolean syncAsArmorstand = true;
    private Vector lastSyncPos = null;
    protected volatile boolean translateVehicleSteer = false;
    protected boolean isFlightForced = false;
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
        if (!this.syncAsArmorstand) {
            lastSyncPos = null;
        }
    }

    /**
     * Stops this movement controller. It will stop refreshing player positions,
     * and the player will regain control like normal.
     */
    @Override
    public final void stop() {
        stopped = true;
        type.remove(this);
        setFlightForced(false);
    }

    @Override
    public final boolean hasStopped() {
        return stopped;
    }

    @Override
    public final void update(Vector position, Quaternion orientation) {
        if (stopped) {
            return;
        }

        // Important player is set to fly mode regularly
        setFlightForced(true);

        // If synchronizing as armor stand, modify the position to be 3/4th
        if (syncAsArmorstand) {
            if (lastSyncPos == null) {
                lastSyncPos = position.clone();
            } else {
                lastSyncPos.add(position.clone().subtract(lastSyncPos).multiply(1.0 / 3.0));
            }
            position = lastSyncPos;
        }

        syncPosition(position, orientation);
    }

    protected abstract void syncPosition(Vector position, Quaternion orientation);

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
                PacketType.IN_STEER_VEHICLE, PacketType.IN_ABILITIES, PacketType.IN_ENTITY_ACTION);

        /**
         * Selects the most appropriate controller type to use for a certain player viewer.
         *
         * @param viewer AttachmentViewer
         * @return ControllerType
         */
        public static ControllerType forViewer(AttachmentViewer viewer) {
            if (HAS_INPUT_PACKET && viewer.evaluateGameVersion(">=", "1.21.2")) {
                return MODERN;
            } else {
                return LEGACY;
            }
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
            for (PlayerMovementController controller : activeControllers) {
                if (controller.player == player) {
                    controller.onPacketReceive(event);
                }
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            final Player player = event.getPlayer();
            for (PlayerMovementController controller : activeControllers) {
                if (controller.player == player) {
                    controller.onPacketSend(event);
                }
            }
        }
    }
}
