package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.PlayerAbilities;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInAbilitiesHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInFlyingHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInSteerVehicleHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutAbilitiesHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityVelocityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityPlayerHandle;
import org.bukkit.util.Vector;

/**
 * Before Minecraft 1.21.2 there was no player input packet, and we had to
 * rely on motion prediction to detect the input the player sent. There was no tick_end
 * packet either, so synchronization occurs in handling the position packets
 * themselves as they come in.
 */
class PlayerMovementControllerPredictedLegacy extends PlayerMovementControllerPredicted {
    private boolean lastPositionWasLook = false;

    protected PlayerMovementControllerPredictedLegacy(ControllerType type, AttachmentViewer viewer) {
        super(type, viewer);
    }

    protected synchronized void sendPosition(Vector position) {
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
    }

    private synchronized void receiveInput(PlayerPositionInput input) {
        // Try various types of player input
        // If the horizontal axis match, checks against the vertical input modes as well
        for (HorizontalPlayerInput hor : input.lastHorizontalInput.getNextLikelyInputs()) {
            ConsumeResult result = sentPositions.tryConsumeHorizontalInput(input, hor);
            if (result != ConsumeResult.FAILED) {
                isSynchronized = result.isSynchronized();

                // [Debug] Send message to player with the current input
                /*
                if (input.lastHorizontalInput != HorizontalPlayerInput.NONE || input.lastVerticalInput != VerticalPlayerInput.NONE) {
                    if (input.lastHorizontalInput == HorizontalPlayerInput.NONE) {
                        player.sendMessage("INPUT: " + input.verticalInput);
                    } else if (input.lastVerticalInput == VerticalPlayerInput.NONE) {
                        player.sendMessage("INPUT: " + input.lastHorizontalInput);
                    } else {
                        player.sendMessage("INPUT: " + input.lastHorizontalInput + " + " + input.lastVerticalInput);
                    }
                }
                */
                return;
            }
        }

        if (DEBUG_MODE) {
            Vector additionalMotion = input.getInputMotion(composeInput(input.lastHorizontalInput, input.lastVerticalInput));

            log("[FORWARD] " + input.currForward);
            log("[PREVIOUS] " + strVec(input.lastPosition));
            log("[BORKED] " + strVec(input.currPosition));
            log("[MOTION] " + strVec(input.lastMotion));

            StringBuilder str = new StringBuilder();
            str.append("Updates in flight predictions:");
            sentPositions.appendDebugNextPredictions(str, input, additionalMotion);
            log(str.toString());
        }

        // As an absolute fallback
        input.setLastMotionUsingPositionChanges();

        // Assume that player input happened. Send absolute updates only until we're back in sync.
        if (isSynchronized) {
            sentPositions.clear(); // All garbage now
        }
        isSynchronized = false;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getType() == PacketType.IN_POSITION || event.getType() == PacketType.IN_POSITION_LOOK) {
            PacketPlayInFlyingHandle p = PacketPlayInFlyingHandle.createHandle(event.getPacket().getHandle());
            synchronized (PlayerMovementControllerPredictedLegacy.this) {
                PlayerPositionInput input = this.input;
                MathUtil.setVector(input.currPosition, p.getX(), p.getY(), p.getZ());

                if (event.getType() == PacketType.IN_POSITION_LOOK) {
                    input.updateYaw(p.getYaw());
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
                            input.lastHorizontalInput.left(),
                            input.lastHorizontalInput.right(),
                            input.lastHorizontalInput.forwards(),
                            input.lastHorizontalInput.backwards(),
                            input.lastVerticalInput == VerticalPlayerInput.JUMP,
                            input.lastVerticalInput == VerticalPlayerInput.SNEAK,
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
