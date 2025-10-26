package com.bergerkiller.bukkit.tc.controller.player;

import java.util.logging.Level;

import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.PlayerAbilities;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInAbilitiesHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInFlyingHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInSteerVehicleHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutAbilitiesHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityVelocityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityPlayerHandle;

/**
 * Moves a player (in first person) to a target position using velocity updates.
 * Velocity packets can be 'missed' if two of them are received in the same tick.
 * To mitigate this, it tracks incoming player input packets to detect these
 * missed velocity packets and adjust accordingly.
 */
public abstract class PlayerMovementController {
    private static final double INPUT_MOTION = (double) (0.5F * 0.98F);
    private static final double INPUT_MOTION_DIAG = 0.3535533905932737622; // = sqrt(0.25*0.25 + 0.25*0.25) because of normalization

    protected final Player player;

    public static PlayerMovementController create(AttachmentViewer viewer) {
        return new PlayerMovementControllerLegacy(viewer.getPlayer());
    }

    protected PlayerMovementController(Player player) {
        this.player = player;
    }

    /**
     * Sets whether position updates are synchronized as if the player is an Armorstand entity.
     * This makes it so that the player moves in sync with surrounding armor stand entities.
     * This is true by default.
     *
     * @param sync Whether to sync as armorstand. True by default.
     */
    public abstract void setSyncAsArmorstand(boolean sync);

    /**
     * Whether to translate player input into vehicle steering packets. This makes it so that
     * player input while standing is still handled by the server as if the player is pressing
     * w/a/s/d etc. in a vehicle.
     *
     * @param translate True to translate as vehicle steer. False by default.
     */
    public abstract void translateVehicleSteer(boolean translate);

    public abstract HorizontalPlayerInput horizontalInput();

    public abstract VerticalPlayerInput verticalInput();

    public abstract void stop();

    public abstract void setPosition(Vector position);

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
            return 0.5 * (double) (speed * this.yya);
        }
    }

    /**
     * A type of horizontal (WASD) input the player can provide
     */
    public static enum HorizontalPlayerInput {

        NONE(0.0f, 0.0f),
        FORWARDS(0.0f, INPUT_MOTION),
        BACKWARDS(0.0f, -INPUT_MOTION),
        LEFT(INPUT_MOTION, 0.0f),
        RIGHT(-INPUT_MOTION, 0.0f),
        FORWARDS_LEFT(INPUT_MOTION_DIAG, INPUT_MOTION_DIAG),
        FORWARDS_RIGHT(-INPUT_MOTION_DIAG, INPUT_MOTION_DIAG),
        BACKWARDS_LEFT(INPUT_MOTION_DIAG, -INPUT_MOTION_DIAG),
        BACKWARDS_RIGHT(-INPUT_MOTION_DIAG, -INPUT_MOTION_DIAG);

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

        private final double xxa, zza;
        private HorizontalPlayerInput[] next;

        private HorizontalPlayerInput(double xxa, double zza) {
            this.xxa = xxa;
            this.zza = zza;
        }

        public float forwardsSteerInput() {
            return (float) zza;
        }

        public boolean forwards() {
            return zza > 0.0;
        }

        public boolean backwards() {
            return zza < 0.0;
        }

        public float sidewaysSteerInput() {
            return (float) xxa;
        }

        public boolean left() {
            return xxa > 0.0;
        }

        public boolean right() {
            return xxa < 0.0;
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

        public Vector getMotion(ForwardMotion forward, float speed) {
            double speedDbl = (double) speed;
            double xxa = this.xxa * speedDbl; // left/right
            double zza = this.zza * speedDbl; // forward/backward

            return new Vector(zza * forward.dx + xxa * forward.dz,
                              0.0,
                              zza * forward.dz - xxa * forward.dx);
        }
    }
}
