package com.bergerkiller.bukkit.tc.utils;

import java.util.logging.Level;

import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
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
public class PlayerVelocityController {
    private static final double INPUT_MOTION = (double) (0.5F * 0.98F);
    private static final double INPUT_MOTION_DIAG = 0.3535533905932737622; // = sqrt(0.25*0.25 + 0.25*0.25) because of normalization
    private static final double MIN_MOTION = 0.003; // Any motion below this does not result in a position update on the client
    private static final MovementFrictionUpdate FRICTION_UPDATE = new MovementFrictionUpdate();
    private final Player player;
    private PositionTracker tracker = null;
    private final SentPositionChain sentPositions = new SentPositionChain();
    private boolean isSynchronized = false;
    private boolean syncAsArmorstand = true;
    private boolean isFlightForced = false;
    private Vector lastSyncPos = null;
    private volatile boolean translateVehicleSteer = false;

    public PlayerVelocityController(Player player) {
        this.player = player;
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
        this.translateVehicleSteer = translate;
    }

    public synchronized HorizontalPlayerInput horizontalInput() {
        PositionTracker tracker = this.tracker;
        return (tracker == null) ? HorizontalPlayerInput.NONE : tracker.input.horizontalInput;
    }

    public synchronized VerticalPlayerInput verticalInput() {
        PositionTracker tracker = this.tracker;
        return (tracker == null) ? VerticalPlayerInput.NONE : tracker.input.verticalInput;
    }

    public synchronized void stop() {
        if (tracker != null) {
            TrainCarts.plugin.unregister(tracker);
        }
        if (isFlightForced) {
            player.setAllowFlight(false);
            isFlightForced = false;
        }
    }

    public synchronized void setPosition(Vector position) {
        // If synchronizing as armor stand, modify the position to be 3/4th
        if (syncAsArmorstand) {
            if (lastSyncPos == null) {
                lastSyncPos = position.clone();
            } else {
                lastSyncPos.add(position.clone().subtract(lastSyncPos).multiply(1.0 / 3.0));
            }
            position = lastSyncPos;
        }

        if (tracker == null) {
            tracker = new PositionTracker(player);
            TrainCarts.plugin.register(tracker, PacketType.IN_POSITION, PacketType.IN_POSITION_LOOK, PacketType.IN_ABILITIES);
        }

        // Important player is set to fly mode regularly
        if (!player.isFlying()) {
            if (!player.getAllowFlight()) {
                isFlightForced = true;
                player.setAllowFlight(true);
            }

            player.setFlying(true);
        }

        // If too many packets remain unacknowledged (>2s) in the chain, reset
        if (sentPositions.size() > 40) {
            sentPositions.clear();
            isSynchronized = false;
        }

        // For debug
        //Vector previousPosition = sentPositions.getCurrentPosition().clone();

        if (isSynchronized) {
            // Perform a relative velocity update

            // Compute velocity, adjust for natural slowdown rate on the client
            // If too small, set to 0, as the client will just ignore it otherwise
            // which would cause a desync.
            Vector diff = position.clone().subtract(sentPositions.getCurrentPosition());
            if (Math.abs(diff.getX()) < MIN_MOTION) {
                diff.setX(0.0);
            }
            if (Math.abs(diff.getY()) < MIN_MOTION) {
                diff.setY(0.0);
            }
            if (Math.abs(diff.getZ()) < MIN_MOTION) {
                diff.setZ(0.0);
            }

            PacketPlayOutEntityVelocityHandle p = PacketPlayOutEntityVelocityHandle.createNew(player.getEntityId(),
                    diff.getX(), diff.getY(), diff.getZ());
            PacketUtil.sendPacket(player, p);

            sentPositions.add(new SentMotionUpdate(new Vector(p.getMotX(), p.getMotY(), p.getMotZ())));
        } else {
            // Reset velocity to 0
            PacketPlayOutEntityVelocityHandle p2 = PacketPlayOutEntityVelocityHandle.createNew(player.getEntityId(),
                    0.0, 0.0, 0.0);
            PacketUtil.sendPacket(player, p2);

            // Force an absolute update to bring the client into a known good state
            PacketUtil.sendPacket(player, PacketPlayOutPositionHandle.createNew(
                    position.getX(), position.getY(), position.getZ(), 0.0f, 0.0f,
                    RelativeFlags.ABSOLUTE_POSITION.withRelativeRotation()));

            sentPositions.add(new SentAbsoluteUpdate(position.clone()));
        }

        // Logging for debug
        /*
        {
            SentPositionUpdate update = sentPositions.getLast();
            Vector motion = update.getMotion(previousPosition);
            update.apply(previousPosition, new Vector(), new Vector(), new Vector());

            log("SEND " + update.getClass().getSimpleName() + " // " + previousPosition + "  //  " + motion);
        }
        */
    }

    private synchronized void receiveInput(PlayerPositionInput input) {
        // Try various types of player input
        // If the horizontal axis match, checks against the vertical input modes as well
        for (HorizontalPlayerInput hor : input.horizontalInput.getNextLikelyInputs()) {
            ConsumeResult result = sentPositions.tryConsumeHorizontalInput(input, hor);
            if (result != ConsumeResult.FAILED) {
                isSynchronized = result.isSynchronized();

                // [Debug] Send message to player with the current input
                /*
                if (input.horizontalInput != HorizontalPlayerInput.NONE || input.verticalInput != VerticalPlayerInput.NONE) {
                    if (input.horizontalInput == HorizontalPlayerInput.NONE) {
                        player.sendMessage("INPUT: " + input.verticalInput);
                    } else if (input.verticalInput == VerticalPlayerInput.NONE) {
                        player.sendMessage("INPUT: " + input.horizontalInput);
                    } else {
                        player.sendMessage("INPUT: " + input.horizontalInput + " + " + input.verticalInput);
                    }
                }
                */
                return;
            }
        }

        /*
        log("[FORWARD] " + input.currForward);
        log("[PREVIOUS] " + strVec(input.lastPosition));
        log("[BORKED] " + strVec(input.currPosition));
        log("[MOTION] " + strVec(input.lastMotion));

        String str = "Updates in flight predictions:";
        for (SentPositionUpdate p = sentPositions.next; p != null; p = p.next) {
            str += "\n" + p.debugPrediction(input);
        }
        str += "\n" + FRICTION_UPDATE.debugPrediction(input);
        log(str);
        */

        // As an absolute fallback
        input.setLastMotionUsingPositionChanges();

        // Assume that player input happened. Send absolute updates only until we're back in sync.
        if (isSynchronized) {
            sentPositions.clear(); // All garbage now
        }
        isSynchronized = false;
    }

    @SuppressWarnings("unused")
    private static void log(String msg) {
        TrainCarts.plugin.getLogger().log(Level.INFO, msg);
    }

    private static boolean isVectorExactlyEqual(Vector v0, Vector v1) {
        return v0.getX() == v1.getX() && v0.getY() == v1.getY() && v0.getZ() == v1.getZ();
    }

    private static String strVec(Vector v) {
        return v.getX() + " // " + v.getY() + " // " + v.getZ();
    }

    private class PositionTracker implements PacketListener {
        private final PlayerPositionInput input;
        private boolean lastPositionWasLook = false;

        public PositionTracker(Player player) {
            this.input = new PlayerPositionInput(player);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPlayer() != player) {
                return;
            }
            if (event.getType() == PacketType.IN_POSITION || event.getType() == PacketType.IN_POSITION_LOOK) {
                PacketPlayInFlyingHandle p = PacketPlayInFlyingHandle.createHandle(event.getPacket().getHandle());
                synchronized (PlayerVelocityController.this) {
                    PlayerPositionInput input = this.input;
                    MathUtil.setVector(input.currPosition, p.getX(), p.getY(), p.getZ());

                    if (event.getType() == PacketType.IN_POSITION_LOOK) {
                        input.currForward = ForwardMotion.get(p.getYaw());
                        lastPositionWasLook = true;
                    } else if (lastPositionWasLook) {
                        // Sometimes a position packet is sent after a look with the same position
                        // Ignore those.
                        lastPositionWasLook = false; // Reset
                        if (isVectorExactlyEqual(input.lastPosition, input.currPosition)) {
                            return;
                        }
                    }

                    receiveInput(input);
                    input.updateLast();

                    if (translateVehicleSteer) {
                        PacketPlayInSteerVehicleHandle steer = PacketPlayInSteerVehicleHandle.createNew(
                                input.horizontalInput.left(),
                                input.horizontalInput.right(),
                                input.horizontalInput.forwards(),
                                input.horizontalInput.backwards(),
                                input.verticalInput == VerticalPlayerInput.JUMP,
                                input.verticalInput == VerticalPlayerInput.SNEAK,
                                false);

                        PacketUtil.receivePacket(player, steer);
                    }
                }
            } else if (event.getType() == PacketType.IN_ABILITIES) {
                PacketPlayInAbilitiesHandle p = PacketPlayInAbilitiesHandle.createHandle(event.getPacket().getHandle());
                if (!p.isFlying()) {
                    event.setCancelled(true);
                    PlayerAbilities pa = EntityPlayerHandle.fromBukkit(event.getPlayer()).getAbilities();
                    PacketPlayOutAbilitiesHandle pp = PacketPlayOutAbilitiesHandle.createNew(pa);
                    PacketUtil.queuePacket(event.getPlayer(), pp);
                }
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
        }
    }

    private static final class PlayerPositionInput {
        @SuppressWarnings("unused")
        public final Player player;
        public final Vector lastPosition;
        public final Vector lastMotion;
        public final Vector currPosition;
        public ForwardMotion currForward;
        public float currSpeed;
        public HorizontalPlayerInput horizontalInput;
        public VerticalPlayerInput verticalInput;

        public PlayerPositionInput(Player player) {
            this.player = player;
            this.lastPosition = player.getLocation().toVector();
            this.lastMotion = player.getVelocity();
            this.currPosition = player.getLocation().toVector();
            this.currForward = ForwardMotion.get(player.getEyeLocation().getYaw());
            this.currSpeed = 0.1f; //TODO: Variable
            this.horizontalInput = HorizontalPlayerInput.NONE;
            this.verticalInput = VerticalPlayerInput.NONE;
        }

        public void setLastMotionUsingPositionChanges() {
            lastMotion.setX(currPosition.getX() - lastPosition.getX());
            lastMotion.setY(currPosition.getY() - lastPosition.getY());
            lastMotion.setZ(currPosition.getZ() - lastPosition.getZ());
        }

        public void updateLast() {
            MathUtil.setVector(lastPosition, currPosition);
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
     * A type of update sent to the client to change the position of the player in some way
     */
    private static abstract class SentPositionUpdate {
        protected SentPositionUpdate next = null;

        /**
         * Applies this position update to a position vector
         *
         * @param position Position vector (is updated)
         * @param additionalMotion Additional motion input provided by the Player
         * @param lastMotion Motion from previous client update
         * @param outMotion Predicted motion of the player is written to this vector.
         *                  This includes the additional motion, if any
         */
        public abstract void apply(Vector position, Vector additionalMotion,
                                   Vector lastMotion, Vector outMotion);

        /**
         * Gets the motion vector caused by this position update
         *
         * @param previousPosition Previous position before this update
         * @return Motion vector
         */
        public abstract Vector getMotion(Vector previousPosition);

        @SuppressWarnings("unused")
        public String debugPrediction(PlayerPositionInput input) {
            Vector newPosition = input.lastPosition.clone();
            Vector additionalMotion = input.horizontalInput.getMotion(input.currForward, input.currSpeed);
            additionalMotion.setY(input.verticalInput.getMotion(input.currSpeed));
            Vector outMotion = new Vector();
            this.apply(newPosition, additionalMotion, input.lastMotion, outMotion);

            return "    " + this.getClass().getSimpleName() + ": " + this.getMotion(input.lastPosition) + "\n" +
                   "         Actual " + strVec(input.currPosition) + "\n" +
                   "         Predicted " + strVec(newPosition);
        }

        /**
         * Tries to detect this position update having happened.
         * Also detects a vertical player input, if detected.
         *
         * @param input Player input history
         * @param horizontalInput Current horizontal input being checked. Additional motion is set to this.
         * @param additionalMotion Additional motion from player input. The y-motion is updated.
         * @param outMotion Output motion of the player is written here
         * @param tmp A temporary vector filled with information important for computation
         * @return True if this update was detected as having been consumed just now
         */
        public boolean detectAsInput(PlayerPositionInput input, HorizontalPlayerInput horizontalInput,
                                     Vector additionalMotion, Vector outMotion, Vector tmp) {
            // First try with the last-known vertical input of the player
            additionalMotion.setY(input.verticalInput.getMotion(input.currSpeed));
            MathUtil.setVector(tmp, input.lastPosition);
            this.apply(tmp, additionalMotion, input.lastMotion, outMotion);

            // X and Z must match, otherwise the provided horizontal input doesn't match at all
            Vector currPos = input.currPosition;
            if (tmp.getX() != currPos.getX() || tmp.getZ() != currPos.getZ()) {
                return false;
            }

            // If Y is different, maybe a different type of input was used, so do try those.
            if (tmp.getY() != currPos.getY()) {
                boolean foundMatchingVerticalInput = false;
                for (VerticalPlayerInput vertInput : VerticalPlayerInput.values()) {
                    if (vertInput == input.verticalInput) {
                        continue; // Already checked
                    }

                    additionalMotion.setY(vertInput.getMotion(input.currSpeed));
                    MathUtil.setVector(tmp, input.lastPosition);
                    this.apply(tmp, additionalMotion, input.lastMotion, outMotion);
                    if (tmp.getY() == currPos.getY()) {
                        input.verticalInput = vertInput;
                        foundMatchingVerticalInput = true;
                        break;
                    }
                }
                if (!foundMatchingVerticalInput) {
                    return false;
                }
            }

            input.horizontalInput = horizontalInput;
            MathUtil.setVector(input.lastMotion, outMotion);
            return true;
        }
    }

    private static final class SentPositionChain extends SentPositionUpdate {
        private final Vector currentPosition = new Vector();
        private SentPositionUpdate last = this;
        private int count = 0;

        public void calcCurrentPosition(Vector startPosition, Vector additionalMotion) {
            MathUtil.setVector(currentPosition, startPosition);
            this.apply(currentPosition, additionalMotion, new Vector(), new Vector());
        }

        public Vector getCurrentPosition() {
            return currentPosition;
        }

        @SuppressWarnings("unused")
        public SentPositionUpdate getLast() {
            return last;
        }

        public int size() {
            return count;
        }

        @Override
        public void apply(Vector position, Vector additionalMotion, Vector lastMotion, Vector outMotion) {
            for (SentPositionUpdate u = next; u != null; u = u.next) {
                u.apply(position, additionalMotion, lastMotion, outMotion);
            }
        }

        @Override
        public Vector getMotion(Vector previousPosition) {
            return new Vector();
        }

        public ConsumeResult tryConsumeHorizontalInput(PlayerPositionInput input, HorizontalPlayerInput horizontalInput) {
            Vector additionalMotion = horizontalInput.getMotion(input.currForward, input.currSpeed);
            Vector outMotion = new Vector();
            Vector tmp = new Vector();

            // Go by all updates that were sent, and try to apply them
            // It's possible one or more of them got merged together because they were
            // received in the same tick. If so, we discard those and adjust currentPosition
            // to take this into account
            SentPositionUpdate curr = next;
            if (curr != null) {
                // Ideally, the very first update matches, in which case we're all in sync.
                // In that case, currentPosition remains valid and no need to recalculate that
                if (curr.detectAsInput(input, horizontalInput, additionalMotion, outMotion, tmp)) {
                    setStart(curr.next);
                    if (additionalMotion.getX() != 0.0 || additionalMotion.getY() != 0.0 || additionalMotion.getZ() != 0.0) {
                        calcCurrentPosition(input.currPosition, additionalMotion);
                    }
                    //input.player.sendMessage("Consumed: FIRST");
                    return ConsumeResult.OK;
                }

                // Try the other updates
                // If we find one matching, we lost the updates we sent before that point
                // By updating the current position, next tick cycle of position updates
                // will correct for it
                int n = 1;
                while (curr.next != null) {
                    curr = curr.next;
                    if (curr.detectAsInput(input, horizontalInput, additionalMotion, outMotion, tmp)) {
                        setStart(curr.next);
                        calcCurrentPosition(input.currPosition, additionalMotion);
                        //input.player.sendMessage("Consumed: #" + n);
                        return n > 5 ? ConsumeResult.LARGE_PACKET_DROP : ConsumeResult.OK;
                    }
                    n++;
                }
            }

            // None matches, perhaps an update was skipped entirely
            // In that case, the previous motion continues unhindered with a slowdown value
            if (FRICTION_UPDATE.detectAsInput(input, horizontalInput, additionalMotion, outMotion, tmp)) {
                calcCurrentPosition(input.currPosition, additionalMotion);
                //input.player.sendMessage("Consumed: Friction/No Update");
                return ConsumeResult.OK;
            }

            return ConsumeResult.FAILED;
        }

        public void setStart(SentPositionUpdate update) {
            this.next = update;
            if (update == null) {
                this.last = this;
                this.count = 0;
            } else {
                int new_count = 1;
                SentPositionUpdate new_last = update;
                while (new_last.next != null) {
                    new_last = new_last.next;
                    ++new_count;
                }
                this.last = new_last;
                this.count = new_count;
            }
        }

        public void clear() {
            this.next = null;
            this.last = this;
            this.count = 0;
        }

        public void add(SentPositionUpdate update) {
            this.last.next = update;
            this.last = update;
            this.count++;
            update.apply(this.currentPosition, new Vector(), new Vector(), new Vector());
        }
    }

    private static final class SentMotionUpdate extends SentPositionUpdate {
        private final Vector motion;

        public SentMotionUpdate(Vector motion) {
            this.motion = motion;
        }

        @Override
        public void apply(Vector position, Vector additionalMotion, Vector lastMotion, Vector outMotion) {
            MathUtil.setVector(outMotion, motion);
            outMotion.add(additionalMotion);

            if (Math.abs(outMotion.getY()) < MIN_MOTION) {
                outMotion.setY(0.0);
            }

            position.add(outMotion);
        }

        @Override
        public Vector getMotion(Vector previousPosition) {
            return motion.clone();
        }
    }

    private static final class SentAbsoluteUpdate extends SentPositionUpdate {
        private final Vector position;

        public SentAbsoluteUpdate(Vector position) {
            this.position = position;
        }

        @Override
        public void apply(Vector position, Vector additionalMotion, Vector lastMotion, Vector outMotion) {
            // Absolute updates use zero velocity, but player could provide input to counteract it
            MathUtil.setVector(outMotion, additionalMotion);

            MathUtil.setVector(position, this.position);
            position.add(additionalMotion);
        }

        @Override
        public Vector getMotion(Vector previousPosition) {
            return position.clone().subtract(previousPosition);
        }
    }

    private static final class MovementFrictionUpdate extends SentPositionUpdate {

        @Override
        public void apply(Vector position, Vector additionalMotion, Vector lastMotion, Vector outMotion) {
            outMotion.setX(lastMotion.getX() * (double) 0.91F);
            outMotion.setY(lastMotion.getY() * (double) 0.6);
            outMotion.setZ(lastMotion.getZ() * (double) 0.91F);
            if (Math.abs(outMotion.getX()) < MIN_MOTION) {
                outMotion.setX(0.0);
            }
            if (Math.abs(outMotion.getZ()) < MIN_MOTION) {
                outMotion.setZ(0.0);
            }
            outMotion.add(additionalMotion);
            if (Math.abs(outMotion.getY()) < MIN_MOTION) {
                outMotion.setY(0.0);
            }
            position.add(outMotion);
        }

        @Override
        public Vector getMotion(Vector previousPosition) {
            return new Vector(); // Unused
        }
    }

    private static enum ConsumeResult {
        FAILED(false),
        OK(true),
        LARGE_PACKET_DROP(false);

        private final boolean isSynchronized;

        private ConsumeResult(boolean isSynchronized) {
            this.isSynchronized = isSynchronized;
        }

        public boolean isSynchronized() {
            return isSynchronized;
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
