package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.PlayerAbilities;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInAbilitiesHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInFlyingHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInSteerVehicleHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutAbilitiesHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityPlayerHandle;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicReference;

/**
 * After Minecraft 1.21.2 the player sends their input to the server, as well as a
 * tick-end packet to synchronize everything. This makes it very easy to keep track
 * of the synchronization, as we do not need to figure out the inputs from the
 * position inputs that come in.
 */
class PlayerMovementControllerPredictedModern extends PlayerMovementControllerPredicted {
    protected final AtomicReference<Vector> lastPosition = new AtomicReference<>(null);
    private Vector lastSentPosition = null;
    private final Vector lastSentMotion = new Vector();

    protected PlayerMovementControllerPredictedModern(ControllerType type, AttachmentViewer viewer) {
        super(type, viewer);
    }

    private static final RelativeFlags FLAGS_ONLY_MOVE = RelativeFlags.RELATIVE_POSITION_ROTATION
            .withAbsoluteDeltaX()
            .withAbsoluteDeltaY()
            .withAbsoluteDeltaZ()
            .withRelativeDeltaRotation();
    private static final RelativeFlags FLAGS_RESET = RelativeFlags.ABSOLUTE_POSITION
            .withAbsoluteDeltaX()
            .withAbsoluteDeltaY()
            .withAbsoluteDeltaZ()
            .withRelativeDeltaRotation()
            .withRelativeRotation();

    @Override
    protected void sendPosition(Vector position) {
        lastPosition.set(position);
    }

    protected synchronized void schedulePosition(Vector position) {
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

            PacketUtil.sendPacket(player, PacketPlayOutPositionHandle.createNew(
                    0.0, 0.0, 0.0, 0.0f, 0.0f,
                    diff.getX(), diff.getY(), diff.getZ(),
                    FLAGS_ONLY_MOVE));

            sentPositions.add(new SentMotionUpdate(diff));
        } else {
            // Force an absolute update to bring the client into a known good state
            PacketUtil.sendPacket(player, PacketPlayOutPositionHandle.createNew(
                    position.getX(), position.getY(), position.getZ(), 0.0f, 0.0f,
                    FLAGS_RESET));

            sentPositions.add(new SentAbsoluteUpdate(position.clone()));
        }
    }

    private synchronized void receiveInput(PlayerPositionInput input) {
        if (DEBUG_MODE && DebugUtil.getBooleanValue("testcase", false)) {
            // Only process NO_INPUT so that any user input triggers a desync
            {
                Vector additionalMotion = input.getPlayerInputMotion(HorizontalPlayerInput.NONE);
                ConsumeResult result = sentPositions.tryConsumeHorizontalInput(input, HorizontalPlayerInput.NONE, additionalMotion);
                if (result != ConsumeResult.FAILED) {
                    isSynchronized = result.isSynchronized();
                    return;
                }
            }

            // Log desyncs as test case formats for the FlyPlayerInputTest
            if (input.lastHorizontalInput == HorizontalPlayerInput.NONE) {
                log("\n" +
                        "                new TestCase(\n" +
                        "                        \"NEW TEST CASE FOR " + input.currHorizontalInput + "\",\n" +
                        "                        PlayerMovementController.HorizontalPlayerInput." + input.currHorizontalInput + ",\n" +
                        "                        " + bukkitVec(input.lastPosition) + ",\n" +
                        "                        " + bukkitVec(input.currPosition) + ",\n" +
                        "                        " + bukkitVec(input.lastMotion) + ",\n" +
                        "                        " + input.currYaw + "f\n" +
                        "                )");
            }

        } else {
            // Try the last known input of the player, followed by the new inputs from the player
            {
                Vector additionalMotion = input.getPlayerInputMotion(input.lastHorizontalInput);
                ConsumeResult result = sentPositions.tryConsumeHorizontalInput(input, input.lastHorizontalInput, additionalMotion);
                if (result != ConsumeResult.FAILED) {
                    isSynchronized = result.isSynchronized();
                    return;
                }
            }
            {
                Vector additionalMotion = input.getPlayerInputMotion(HorizontalPlayerInput.NONE);
                ConsumeResult result = sentPositions.tryConsumeHorizontalInput(input, HorizontalPlayerInput.NONE, additionalMotion);
                if (result != ConsumeResult.FAILED) {
                    isSynchronized = result.isSynchronized();
                    return;
                }
            }
            if (input.lastHorizontalInput != input.currHorizontalInput) {
                Vector additionalMotion = input.getPlayerInputMotion(input.currHorizontalInput);
                ConsumeResult result = sentPositions.tryConsumeHorizontalInput(input, input.currHorizontalInput, additionalMotion);
                if (result != ConsumeResult.FAILED) {
                    isSynchronized = result.isSynchronized();
                    return;
                }
            }

            if (DEBUG_MODE) {
                log("[FORWARD] yaw=" + input.currYaw + " " + input.currForward);
                log("[PREVIOUS] " + strVec(input.lastPosition));
                log(" [CURRENT] " + strVec(input.currPosition));
                log("  [MOTION] " + strVec(input.lastMotion));

                Vector additionalMotion = input.getPlayerInputMotion(input.currHorizontalInput);
                if (input.currHorizontalInput != HorizontalPlayerInput.NONE) {
                    log("[CURR PLAYER SPEED] " + input.currSpeed);
                    log("[MOVEMENT] " + strVec(additionalMotion));
                }

                String str = "Updates in flight predictions:";
                for (SentPositionUpdate p = sentPositions.next; p != null; p = p.next) {
                    str += "\n" + p.debugPrediction(input, additionalMotion);
                }
                str += "\n" + FRICTION_UPDATE.debugPrediction(input, additionalMotion);
                log(str);
            }
        }

        // As an absolute fallback
        input.setLastMotionUsingPositionChanges();

        // Assume that player input happened. Send absolute updates only until we're back in sync.
        if (isSynchronized) {
            sentPositions.clear(); // All garbage now
        }
        isSynchronized = false;
    }

    private static String bukkitVec(Vector v) {
        return "new Vector(" + v.getX() + ", 0.0, " + v.getZ() + ")";
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getType() == PacketType.IN_POSITION || event.getType() == PacketType.IN_POSITION_LOOK) {
            // Update last-known position and look yaw of the player
            PacketPlayInFlyingHandle p = PacketPlayInFlyingHandle.createHandle(event.getPacket().getHandle());
            synchronized (PlayerMovementControllerPredictedModern.this) {
                PlayerPositionInput input = this.input;
                MathUtil.setVector(input.currPosition, p.getX(), p.getY(), p.getZ());

                if (event.getType() == PacketType.IN_POSITION_LOOK) {
                    input.updateYaw(p.getYaw());
                }
            }
        } else if (event.getType() == PacketType.IN_STEER_VEHICLE) {
            // Update last-known inputs from the player
            PacketPlayInSteerVehicleHandle packet = PacketPlayInSteerVehicleHandle.createHandle(event.getPacket().getHandle());
            synchronized (PlayerMovementControllerPredictedModern.this) {
                input.currHorizontalInput = HorizontalPlayerInput.fromSteer(
                        packet.isLeft(), packet.isRight(), packet.isForward(), packet.isBackward()
                );
                input.currVerticalInput = VerticalPlayerInput.fromSteer(
                        packet.isJump(), packet.isUnmount()
                );
            }
        } else if (event.getType() == PacketType.IN_ABILITIES) {
            // Ensure flight mode is kept active
            PacketPlayInAbilitiesHandle p = PacketPlayInAbilitiesHandle.createHandle(event.getPacket().getHandle());
            if (!p.isFlying()) {
                event.setCancelled(true);
                PlayerAbilities pa = EntityPlayerHandle.fromBukkit(event.getPlayer()).getAbilities();
                PacketPlayOutAbilitiesHandle pp = PacketPlayOutAbilitiesHandle.createNew(pa);
                PacketUtil.queuePacket(event.getPlayer(), pp);
            }
        } else if (event.getType() == PacketType.IN_CLIENT_TICK_END) {
            // Synchronize everything on every client tick
            receiveInput(input);
            input.updateLast();

            Vector next = lastPosition.getAndSet(null);
            if (next != null) {
                if (lastSentPosition != null) {
                    MathUtil.setVector(lastSentMotion, next);
                    lastSentMotion.subtract(lastSentPosition);
                    MathUtil.setVector(lastSentPosition, next);
                } else {
                    lastSentPosition = next.clone();
                }

                schedulePosition(next);
            } else if (lastSentPosition != null) {
                lastSentPosition.add(lastSentMotion);
                schedulePosition(lastSentPosition);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }
}
