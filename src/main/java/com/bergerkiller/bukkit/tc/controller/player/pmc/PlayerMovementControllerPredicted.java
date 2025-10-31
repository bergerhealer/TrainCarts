package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

    /** Last position requested through the movement controller API */
    protected final AtomicReference<RequestedPosition> lastRequestedPosition = new AtomicReference<>(null);
    /** Keeps track of the position and other inputs received from the client */
    protected final PlayerPositionInput input;

    protected PlayerMovementControllerPredicted(ControllerType type, AttachmentViewer viewer) {
        super(type, viewer);
        this.input = new PlayerPositionInput(viewer);
        if (DEBUG_MODE) {
            // Make command available
            DebugUtil.getBooleanValue("testcase", false);
        }
    }

    @Override
    public AttachmentViewer.Input getInput() {
        return input.lastInput.input;
    }

    private RequestedPosition computeNextRequestedPosition(Vector position, Quaternion orientation) {
        RequestedPosition lastRequestedPositionSync = this.lastRequestedPosition.get();
        int serverTicks = CommonUtil.getServerTicks();
        if (lastRequestedPositionSync != null && (serverTicks - lastRequestedPositionSync.serverTicks) < 20) {
            // Compute motion based on how many ticks have elapsed or reuse previous motion
            Vector motion;
            if (lastRequestedPositionSync.serverTicks == serverTicks) {
                motion = lastRequestedPositionSync.motion;
            } else {
                motion = position.clone().subtract(lastRequestedPositionSync.position);
                double fact = 1.0 / (serverTicks - lastRequestedPositionSync.serverTicks);
                motion.multiply(fact);
            }

            // Not above 16 blocks/tick
            if (motion.lengthSquared() < (16 * 16)) {
                if (orientation == null) {
                    orientation = lastRequestedPositionSync.orientation;
                }
                return new RequestedPosition(position, motion, orientation, serverTicks);
            }
        }

        // Completely new position / resync
        return new RequestedPosition(position, new Vector(), orientation, serverTicks);
    }

    @Override
    protected final void syncPosition(Vector position, Quaternion orientation) {
        // Place the new position + orientation for consumption
        // If orientation is never specified, it will remain null
        this.lastRequestedPosition.set(computeNextRequestedPosition(position, orientation));

        // TODO: Fake receive() the player position packet, so the server right away positions
        // the player here as if the player sent it. This way laggy players don't appear to lag
        // behind to other players.

        // For debug
        //Vector previousPosition = sentPositions.getCurrentPosition().clone();

        if (DEBUG_MODE && !isSynchronized) {
            player.sendMessage("DESYNC DETECTED!");
        }
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

    /**
     * Position (and orientation) that was last requested through the Movement Controller API.
     * This is updated on the main thread. The packet-receiving netty thread attempts
     * to synchronize towards this position.
     */
    protected static final class RequestedPosition {
        public final Vector position;
        public final Vector motion;
        public final Quaternion orientation;
        public final int serverTicks;
        private final AtomicBoolean consumed = new AtomicBoolean(false);

        public RequestedPosition(Vector position, Vector motion, Quaternion orientation, int serverTicks) {
            this.position = position;
            this.motion = motion;
            this.orientation = orientation;
            this.serverTicks = serverTicks;
        }

        public boolean tryConsume() {
            return consumed.compareAndSet(false, true);
        }
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
        public ComposedInput lastInput;
        public ComposedInput currInput;

        /** When player presses both forward and left for example, this factor is used */
        public final double diagonalSpeedFactor;
        /** When player presses jump or sneak, this is the speed at which the player flies vertically */
        public final float verticalSpeedFactor;

        public PlayerPositionInput(AttachmentViewer viewer) {
            this.player = viewer.getPlayer();
            this.lastPosition = player.getLocation().toVector();
            this.lastMotion = player.getVelocity();
            this.currPosition = player.getLocation().toVector();
            this.currYaw = player.getEyeLocation().getYaw();
            this.currForward = ForwardMotion.get(currYaw);
            this.currSpeed = 0.5F * player.getFlySpeed();
            this.lastInput = ComposedInput.NONE;
            this.currInput = ComposedInput.NONE;

            // I have NO idea why the client is inconsistent like that
            if (viewer.evaluateGameVersion(">=", "1.21.8")) {
                this.diagonalSpeedFactor = 0.7071067094802855;
            } else {
                this.diagonalSpeedFactor = 0.7071067811865475244;
            }

            this.verticalSpeedFactor = 3.0f;
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
            lastInput = currInput;
        }

        public Vector getInputMotion(AttachmentViewer.Input input) {
            double horSpeedDbl = (double) currSpeed;
            double verSpeedDbl = (double) (currSpeed * verticalSpeedFactor);

            if (input.hasDiagonalWalkInput()) {
                horSpeedDbl *= diagonalSpeedFactor;
            } else {
                horSpeedDbl *= 0.98F;
            }

            if (input.sprinting() && input.forwards() && !input.backwards()) {
                horSpeedDbl *= 2.0;
            }

            double left = input.sidewaysSigNum() * horSpeedDbl; // left/right
            double forward = input.forwardsSigNum() * horSpeedDbl; // forward/backward
            double vertical = input.verticalSigNum() * verSpeedDbl; // jump/sneak

            return new Vector(
                    forward * currForward.dx + left * currForward.dz,
                    vertical,
                    forward * currForward.dz - left * currForward.dx);
        }
    }

    /**
     * The state of the client. Keeps track of what the client position and motion
     * values are assuming the updates have been applied the way they are.
     */
    protected static final class PlayerClientState {
        /** Input control state of the player. The inputMotion field is set to this input */
        public ComposedInput input = ComposedInput.NONE;
        /** Input motion added as a result of the player pressing movement controls */
        public final Vector inputMotion = new Vector();
        /** Last motion from {@link PlayerPositionInput}, immutable */
        public final Vector lastMotion;
        /** Motion of the player */
        public final Vector motion = new Vector();
        /** Position of the player */
        public final Vector position = new Vector();
        /** Position of the player + motion */
        public final Vector positionAfterMotion = new Vector();

        public PlayerClientState(Vector lastMotion) {
            this.lastMotion = lastMotion;
        }

        public void setTo(PlayerClientState state) {
            this.input = state.input;
            MathUtil.setVector(this.inputMotion, state.inputMotion);
            MathUtil.setVector(this.lastMotion, state.lastMotion);
            MathUtil.setVector(this.motion, state.motion);
            MathUtil.setVector(this.position, state.position);
            MathUtil.setVector(this.positionAfterMotion, state.positionAfterMotion);
        }

        public boolean isCorrect(Vector currentPosition) {
            return isVectorExactlyEqual(positionAfterMotion, currentPosition);
        }
    }

    protected static class SentPositionChainLink {
        protected SentPositionUpdate next = null;
    }

    /**
     * A type of update sent to the client to change the position of the player in some way
     */
    protected static abstract class SentPositionUpdate extends SentPositionChainLink {
        /**
         * Applies this position update to the client state.
         * Must be further implemented.
         *
         * @param state PlayerClientState
         */
        protected abstract void apply(PlayerClientState state);

        /**
         * Applies this position update to the client state
         *
         * @param state PlayerClientState
         */
        public final void applyFull(PlayerClientState state) {
            this.apply(state);

            // Update positionAfterMotion
            MathUtil.setVector(state.positionAfterMotion, state.position);
            state.positionAfterMotion.add(state.motion);
        }

        /**
         * Tries to detect this position update having happened.
         * Also detects a vertical player input, and which one, by looking for the different
         * vertical permutations.
         *
         * @param input Player input history
         * @param horizontalInput Current horizontal input being checked. Additional motion is set to this.
         * @param sprinting Current sprinting state input being checked. Additional motion is set using this.
         * @param state PlayerClientState. Is updated if input is successfully tested
         * @return The position update that was detected. This is the one the client is presumed to be
         *         in right now. Returns null if none could be identified.
         */
        public SentPositionUpdate findHorizontalInput(
                PlayerPositionInput input,
                HorizontalPlayerInput horizontalInput,
                boolean sprinting,
                PlayerClientState state
        ) {
            for (VerticalPlayerInput verticalInput : new VerticalPlayerInput[] {
                    VerticalPlayerInput.NONE, VerticalPlayerInput.JUMP, VerticalPlayerInput.SNEAK
            }) {
                ComposedInput controlInput = new ComposedInput(horizontalInput, verticalInput, sprinting);
                SentPositionUpdate u = findInput(input, controlInput, state);
                if (u != null) {
                    return u;
                }
            }
            return null; // Not identified
        }

        /**
         * Tries to detect the position update having happened, with the assumed
         * horizontal and vertical player input specified. This tests this position,
         * and the next one in the chain, assuming all of them got applied in the same
         * client tick.
         *
         * @param input Player input history
         * @param controlInput Current player control input being checked. Additional motion is set using this.
         * @param state PlayerClientState. Is updated if input is successfully tested
         * @return The position update that was detected. This is the one the client is presumed to be
         *         in right now. Returns null if none could be identified.
         */
        public SentPositionUpdate findInput(
                PlayerPositionInput input,
                ComposedInput controlInput,
                PlayerClientState state
        ) {
            // Seed the client state using the presumed initial client input state
            state.input = controlInput;
            MathUtil.setVector(state.position, input.lastPosition);
            MathUtil.setVector(state.inputMotion, input.getInputMotion(controlInput.input));

            // Try to apply this update, and the next ones, and check if one of the states match
            for (SentPositionUpdate u = this; u != null; u = u.next) {
                u.applyFull(state);
                if (state.isCorrect(input.currPosition)) {
                    MathUtil.setVector(input.lastMotion, state.motion);
                    return u;
                }
            }
            return null; // Not identified
        }
    }

    protected static final class SentPositionChain extends SentPositionChainLink {
        /** The client state after all pending position updates have been applied */
        private final PlayerClientState lastClientState = new PlayerClientState(new Vector());
        private SentPositionChainLink last = this;
        private int count = 0;

        public void calcLastClientState(PlayerClientState startState) {
            lastClientState.setTo(startState);
            for (SentPositionUpdate u = next; u != null; u = u.next) {
                // Pretend that the previous tick's motion has been fully applied before this update
                MathUtil.setVector(lastClientState.position, lastClientState.positionAfterMotion);

                // Now apply the update on top of that + keep track of the positionAfterMotion
                u.applyFull(lastClientState);
            }
        }

        public Vector getCurrentPosition() {
            return lastClientState.positionAfterMotion;
        }

        public int size() {
            return count;
        }

        @SuppressWarnings("unused")
        public void appendDebugNextPredictions(StringBuilder str, PlayerPositionInput input, ComposedInput controlInput) {
            // Seed it with the last position and motion we've synchronized
            PlayerClientState state = new PlayerClientState(input.lastMotion);
            state.input = controlInput;
            MathUtil.setVector(state.position, input.lastPosition);
            MathUtil.setVector(state.inputMotion, input.getInputMotion(controlInput.input));

            for (SentPositionUpdate u = this.next; u != null; u = u.next) {
                u.applyFull(state);
                appendDebugStr(str, u, state, input.currPosition);
            }

            FRICTION_UPDATE.applyFull(state);
            appendDebugStr(str, FRICTION_UPDATE, state, input.currPosition);
        }

        private static void appendDebugStr(StringBuilder str, SentPositionUpdate update, PlayerClientState state, Vector currPosition) {
            str.append("\n    ").append(update.getClass().getSimpleName()).append(":\n");
            str.append("            Actual ").append(strVec(currPosition)).append("\n");
            str.append("         Predicted ").append(strVec(state.positionAfterMotion));
        }

        public ConsumeResult tryConsumeExactInput(
                final PlayerPositionInput input,
                final ComposedInput controlInput
        ) {
            PlayerClientState state = new PlayerClientState(input.lastMotion);

            // Go by all updates that were sent, and try to apply them
            // It's possible one or more of them got merged together because they were
            // received in the same tick. If so, we discard those as processed
            SentPositionUpdate curr = next;
            if (curr != null) {
                SentPositionUpdate foundUpdate = curr.findInput(input, controlInput, state);
                if (foundUpdate != null) {
                    setStart(foundUpdate.next);
                    calcLastClientState(state);
                    //input.player.sendMessage("Consumed: FIRST");
                    return ConsumeResult.OK;
                }
            }

            // None matches, perhaps an update was skipped entirely
            // In that case, the previous motion continues unhindered with a slowdown value
            if (FRICTION_UPDATE.findInput(input, controlInput, state) != null) {
                calcLastClientState(state);
                //input.player.sendMessage("Consumed: Friction/No Update");
                return ConsumeResult.OK;
            }

            return ConsumeResult.FAILED;
        }

        public ConsumeResult tryConsumeHorizontalInput(
                final PlayerPositionInput input,
                final PlayerClientState state,
                final HorizontalPlayerInput horizontalInput,
                final boolean sprinting
        ) {
            // Go by all updates that were sent, and try to apply them
            // It's possible one or more of them got merged together because they were
            // received in the same tick. If so, we discard those and adjust currentPosition
            // to take this into account
            SentPositionUpdate curr = next;
            if (curr != null) {
                // Ideally, the very first update matches, in which case we're all in sync.
                // In that case, currentPosition remains valid and no need to recalculate that
                SentPositionUpdate foundUpdate = curr.findHorizontalInput(input, horizontalInput, sprinting, state);
                if (foundUpdate != null) {
                    setStart(foundUpdate.next);
                    calcLastClientState(state);
                    //input.player.sendMessage("Consumed: FIRST");
                    return ConsumeResult.OK;
                }
            }

            // None matches, perhaps an update was skipped entirely
            // In that case, the previous motion continues unhindered with a slowdown value
            if (FRICTION_UPDATE.findHorizontalInput(input, horizontalInput, sprinting, state) != null) {
                calcLastClientState(state);
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
            update.applyFull(lastClientState);
        }
    }

    protected static final class SentMotionUpdate extends SentPositionUpdate {
        private final Vector motion;

        public SentMotionUpdate(Vector motion) {
            this.motion = motion;
        }

        @Override
        protected void apply(PlayerClientState state) {
            MathUtil.setVector(state.motion, motion);
            state.motion.add(state.inputMotion);

            if (Math.abs(state.motion.getY()) < MIN_MOTION) {
                state.motion.setY(0.0);
            }
        }
    }

    protected static final class SentAbsoluteUpdate extends SentPositionUpdate {
        private final Vector position;

        public SentAbsoluteUpdate(Vector position) {
            this.position = position;
        }

        @Override
        protected void apply(PlayerClientState state) {
            // Absolute updates reset velocity, but player could provide input to counteract it
            MathUtil.setVector(state.motion, state.inputMotion);
            MathUtil.setVector(state.position, this.position);
        }
    }

    protected static final class MovementFrictionUpdate extends SentPositionUpdate {

        @Override
        protected void apply(PlayerClientState state) {
            Vector outMotion = state.motion;
            Vector lastMotion = state.lastMotion;

            outMotion.setX(lastMotion.getX() * (double) 0.91F);
            outMotion.setY(lastMotion.getY() * (double) 0.6);
            outMotion.setZ(lastMotion.getZ() * (double) 0.91F);
            if (Math.abs(outMotion.getX()) < MIN_MOTION) {
                outMotion.setX(0.0);
            }
            if (Math.abs(outMotion.getZ()) < MIN_MOTION) {
                outMotion.setZ(0.0);
            }
            outMotion.add(state.inputMotion);
            if (Math.abs(outMotion.getY()) < MIN_MOTION) {
                outMotion.setY(0.0);
            }
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
