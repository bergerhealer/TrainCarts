package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Moves a player (in first person) to a target position using velocity updates.
 * Velocity packets can be 'missed' if two of them are received in the same tick.
 * To mitigate this, it tracks incoming player input packets to detect these
 * missed velocity packets and adjust accordingly.
 */
public abstract class PlayerMovementController {
    private static final boolean HAS_INPUT_PACKET = Common.evaluateMCVersion(">=", "1.21.2");

    protected final AttachmentViewer viewer;
    protected final Player player;
    private boolean syncAsArmorstand = true;
    private Vector lastSyncPos = null;
    protected volatile boolean translateVehicleSteer = false;
    protected boolean isFlightForced = false;

    public static PlayerMovementController create(AttachmentViewer viewer) {
        if (HAS_INPUT_PACKET && viewer.evaluateGameVersion(">=", "1.21.2")) {
            return new PlayerMovementControllerPredictedModern(viewer);
        } else {
            return new PlayerMovementControllerPredictedLegacy(viewer);
        }
    }

    protected PlayerMovementController(AttachmentViewer viewer) {
        this.viewer = viewer;
        this.player = viewer.getPlayer();
    }

    /**
     * Sets whether position updates are synchronized as if the player is an Armorstand entity.
     * This makes it so that the player moves in sync with surrounding armor stand entities.
     * This is true by default.
     *
     * @param sync Whether to sync as armorstand. True by default.
     */
    public void setSyncAsArmorstand(boolean sync) {
        this.syncAsArmorstand = sync;
        if (!sync) {
            lastSyncPos = null;
        }
    }

    /**
     * Whether to translate player input into vehicle steering packets. This makes it so that
     * player input while standing is still handled by the server as if the player is pressing
     * w/a/s/d etc. in a vehicle.
     *
     * @param translate True to translate as vehicle steer. False by default.
     */
    public void translateVehicleSteer(boolean translate) {
        translateVehicleSteer = translate;
    }

    public abstract HorizontalPlayerInput horizontalInput();

    public abstract VerticalPlayerInput verticalInput();

    public abstract void stop();

    public final void setPosition(Vector position) {
        // If synchronizing as armor stand, modify the position to be 3/4th
        if (syncAsArmorstand) {
            if (lastSyncPos == null) {
                lastSyncPos = position.clone();
            } else {
                lastSyncPos.add(position.clone().subtract(lastSyncPos).multiply(1.0 / 3.0));
            }
            position = lastSyncPos;
        }

        syncPosition(position);
    }

    protected abstract void syncPosition(Vector position);

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
}
