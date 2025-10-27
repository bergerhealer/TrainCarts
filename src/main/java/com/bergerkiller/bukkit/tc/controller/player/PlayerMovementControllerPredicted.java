package com.bergerkiller.bukkit.tc.controller.player;

import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.logging.Level;

/**
 * A player movement controller based upon client state prediction. Keeps track of a chain of state
 * changes the player has gone through, and uses that to detect which packets the client has
 * received from what we have sent. This is done by doing exact-double equality checks. This
 * minimizes the chance of misses.<br>
 * <br>
 * It is essential that the client is unmodded and has the same flight behavior as vanilla Minecraft.
 */
abstract class PlayerMovementControllerPredicted extends PlayerMovementController {
    /**
     * Enable to diagnose desynchronization issues ingame.
     * Use /debugvar testcase true to generate test cases on first player input.
     */
    protected static final boolean DEBUG_MODE = true;

    /** Any motion below this does not result in a position update on the client */
    protected static final double MIN_MOTION = 0.003;

    protected static final MovementFrictionUpdate FRICTION_UPDATE = new MovementFrictionUpdate();
    protected final SentPositionChain sentPositions = new SentPositionChain();
    protected boolean isSynchronized = false;
    protected PositionTracker tracker = null;

    protected PlayerMovementControllerPredicted(AttachmentViewer viewer) {
        super(viewer);
        if (DEBUG_MODE) {
            // Make command available
            DebugUtil.getBooleanValue("testcase", false);
        }
    }

    protected abstract void sendPosition(Vector position);

    protected abstract PositionTracker createTracker(AttachmentViewer viewer);

    @Override
    public synchronized HorizontalPlayerInput horizontalInput() {
        PositionTracker tracker = this.tracker;
        return (tracker == null) ? HorizontalPlayerInput.NONE : tracker.input.lastHorizontalInput;
    }

    @Override
    public synchronized VerticalPlayerInput verticalInput() {
        PositionTracker tracker = this.tracker;
        return (tracker == null) ? VerticalPlayerInput.NONE : tracker.input.lastVerticalInput;
    }

    @Override
    public synchronized void stop() {
        if (tracker != null) {
            viewer.getTrainCarts().unregister(tracker);
        }
        setFlightForced(false);
    }

    @Override
    protected synchronized final void syncPosition(Vector position) {
        if (tracker == null) {
            tracker = createTracker(viewer);
            viewer.getTrainCarts().register(tracker, tracker.getPacketTypes());
        }

        // Important player is set to fly mode regularly
        setFlightForced(true);

        // If too many packets remain unacknowledged (>2s) in the chain, reset
        if (sentPositions.size() > 40) {
            sentPositions.clear();
            isSynchronized = false;
        }

        // For debug
        //Vector previousPosition = sentPositions.getCurrentPosition().clone();

        if (DEBUG_MODE && !isSynchronized) {
            player.sendMessage("DESYNC DETECTED!");
        }

        this.sendPosition(position);

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

    @SuppressWarnings("unused")
    protected static void log(String msg) {
        TrainCarts.plugin.getLogger().log(Level.INFO, msg);
    }

    protected static boolean isVectorExactlyEqual(Vector v0, Vector v1) {
        return v0.getX() == v1.getX() && v0.getY() == v1.getY() && v0.getZ() == v1.getZ();
    }

    protected static String strVec(Vector v) {
        return v.getX() + " // " + v.getY() + " // " + v.getZ();
    }

    protected static abstract class PositionTracker implements PacketListener {
        protected final PlayerPositionInput input;

        public PositionTracker(AttachmentViewer viewer) {
            this.input = new PlayerPositionInput(viewer);
        }

        public abstract PacketType[] getPacketTypes();
    }

    protected static final class PlayerPositionInput {
        @SuppressWarnings("unused")
        public final Player player;
        public final Vector lastPosition;
        public final Vector lastMotion;
        public final Vector currPosition;
        public float currYaw;
        public ForwardMotion currForward;
        public float currSpeed;
        public HorizontalPlayerInput lastHorizontalInput;
        public VerticalPlayerInput lastVerticalInput;
        public HorizontalPlayerInput currHorizontalInput;
        public VerticalPlayerInput currVerticalInput;

        /** When player presses both forward and left for example, this factor is used */
        public final double diagonalSpeedFactor;

        public PlayerPositionInput(AttachmentViewer viewer) {
            this.player = viewer.getPlayer();
            this.lastPosition = player.getLocation().toVector();
            this.lastMotion = player.getVelocity();
            this.currPosition = player.getLocation().toVector();
            this.currYaw = player.getEyeLocation().getYaw();
            this.currForward = ForwardMotion.get(currYaw);
            this.currSpeed = 0.5F * player.getFlySpeed();
            this.lastHorizontalInput = HorizontalPlayerInput.NONE;
            this.lastVerticalInput = VerticalPlayerInput.NONE;
            this.currHorizontalInput = HorizontalPlayerInput.NONE;
            this.currVerticalInput = VerticalPlayerInput.NONE;

            // I have NO idea why the client is inconsistent like that
            if (viewer.evaluateGameVersion(">=", "1.21.8")) {
                this.diagonalSpeedFactor = 0.7071067094802855;
            } else {
                this.diagonalSpeedFactor = 0.7071067811865475244;
            }
        }

        public void setLastMotionUsingPositionChanges() {
            lastMotion.setX(currPosition.getX() - lastPosition.getX());
            lastMotion.setY(currPosition.getY() - lastPosition.getY());
            lastMotion.setZ(currPosition.getZ() - lastPosition.getZ());
        }

        public void updateYaw(float yaw) {
            this.currYaw = yaw;
            this.currForward = ForwardMotion.get(yaw);
        }

        public void updateLast() {
            MathUtil.setVector(lastPosition, currPosition);
            lastHorizontalInput = currHorizontalInput;
            lastVerticalInput = currVerticalInput;
        }

        public Vector getPlayerInputMotion(HorizontalPlayerInput horizontalPlayerInput) {
            Vector additionalMotion = getPlayerHorizontalInputMotion(horizontalPlayerInput);
            additionalMotion.setY(lastVerticalInput.getMotion(currSpeed));
            return additionalMotion;
        }

        public Vector getPlayerHorizontalInputMotion(HorizontalPlayerInput input) {
            double speedDbl = (double) currSpeed;

            if (input.diagonal()) {
                speedDbl *= diagonalSpeedFactor;
            } else {
                speedDbl *= 0.98F;
            }

            double left = input.leftSteerInput() * speedDbl; // left/right
            double forward = input.forwardSteerInput() * speedDbl; // forward/backward

            return new Vector(
                    forward * currForward.dx + left * currForward.dz,
                    0.0,
                    forward * currForward.dz - left * currForward.dx);
        }
    }

    /**
     * A type of update sent to the client to change the position of the player in some way
     */
    protected static abstract class SentPositionUpdate {
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
        public String debugPrediction(PlayerPositionInput input, Vector additionalMotion) {
            Vector newPosition = input.lastPosition.clone();
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
            additionalMotion.setY(input.lastVerticalInput.getMotion(input.currSpeed));
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
                    if (vertInput == input.currVerticalInput) {
                        continue; // Already checked
                    }

                    additionalMotion.setY(vertInput.getMotion(input.currSpeed));
                    MathUtil.setVector(tmp, input.lastPosition);
                    this.apply(tmp, additionalMotion, input.lastMotion, outMotion);
                    if (tmp.getY() == currPos.getY()) {
                        input.currVerticalInput = vertInput;
                        foundMatchingVerticalInput = true;
                        break;
                    }
                }
                if (!foundMatchingVerticalInput) {
                    return false;
                }
            }

            input.currHorizontalInput = horizontalInput;
            MathUtil.setVector(input.lastMotion, outMotion);
            return true;
        }
    }

    protected static final class SentPositionChain extends SentPositionUpdate {
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

        public ConsumeResult tryConsumeHorizontalInput(
                final PlayerPositionInput input,
                final HorizontalPlayerInput horizontalInput,
                final Vector additionalMotion
        ) {
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

    protected static final class SentMotionUpdate extends SentPositionUpdate {
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

    protected static final class SentAbsoluteUpdate extends SentPositionUpdate {
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

    protected static final class MovementFrictionUpdate extends SentPositionUpdate {

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

    protected enum ConsumeResult {
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
}
