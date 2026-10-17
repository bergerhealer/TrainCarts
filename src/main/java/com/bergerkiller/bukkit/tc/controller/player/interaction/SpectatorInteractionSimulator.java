package com.bergerkiller.bukkit.tc.controller.player.interaction;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.block.BlockRayTrace;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.common.wrappers.HumanHandRole;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.player.SpectatedFakePlayer;
import com.bergerkiller.bukkit.tc.events.attachment.AttachmentInteractEvent;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundSwingAnimationPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundAttackPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundInteractPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerActionPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPunchPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundUseItemOnPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundUseItemPacketHandle;
import com.bergerkiller.generated.net.minecraft.world.phys.BlockHitResultHandle;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Simulates player interactions while a player is viewing themselves through spectator mode.
 * Performs its own ray-tracing for clicks and fires off interactions for blocks, entities and trains.
 * Also repairs the lack of sound from clicking on blocks.
 */
public class SpectatorInteractionSimulator {
    private final SpectatedFakePlayer fakePlayer;
    private final AttachmentViewer player;
    private boolean ignoreSpectatorFilter = false;

    public SpectatorInteractionSimulator(SpectatedFakePlayer fakePlayer, AttachmentViewer player) {
        this.fakePlayer = fakePlayer;
        this.player = player;
    }

    /**
     * Called while the client is in spectator mode to handle packets sent by the player. This handles the rewriting
     * of those interaction packets into one that logically comes from the actual player, that the server can
     * accept. Without this, the server would just receive click interactions with ghost entities or worse - the
     * player itself, triggering a client disconnect.
     *
     * @param event PacketReceiveEvent
     * @see TrainCartsPlayerInteractionHandler#LISTENED_TYPES Packet types that are received here
     */
    public void handleReceivedPacket(PacketReceiveEvent event) {
        if (ignoreSpectatorFilter) {
            return;
        }

        final PacketType type = event.getType();

        // Ignore the off-hand interact packet which always fires along with the main one
        if (type == PacketType.IN_INTERACT) {
            ServerboundInteractPacketHandle packet = ServerboundInteractPacketHandle.createHandle(event.getPacket().getHandle());
            if (packet.getHandRole() == HumanHandRole.OFF) {
                event.setCancelled(true);
                return;
            }
        }

        // Ignore use item / use item on coming from the client, they're not reliable
        if (type == PacketType.IN_USE_ITEM || type == PacketType.IN_USE_ITEM_ON) {
            event.setCancelled(true);
            return;
        }

        // Interaction packets are almost always garbage. They click the fake player, or the player itself, because
        // it sits in the player's view. We will always have to do our own ray-trace to correct it.
        if (type == PacketType.IN_INTERACT || type == PacketType.IN_ATTACK) {
            // Perform a ray-trace to see if any actual blocks or entities were interacted with. Must be done on the main thread.
            if (CommonUtil.isMainThread()) {
                PacketHandle rewrittenPacket = fakeInteraction(type == PacketType.IN_ATTACK);
                if (rewrittenPacket != null) {
                    event.setPacket(rewrittenPacket);
                } else {
                    event.setCancelled(true);
                }
            } else {
                // Abort original packet (always)
                event.setCancelled(true);
                CommonUtil.nextTick(() -> {
                    PacketHandle rewrittenPacket = fakeInteraction(type == PacketType.IN_ATTACK);
                    if (rewrittenPacket != null) {
                        receivePacketWithoutSuppress(rewrittenPacket);
                    }
                });
            }
        }

        // Note: punch (or arm swing) packet also occurs, but it doesn't contain any context to make it
        // dangerous to receive as-is. Just means other players see the arm animation, which is fine.
    }

    private void receivePacketWithoutSuppress(PacketHandle packet) {
        ignoreSpectatorFilter = true;
        PacketUtil.receivePacket(player.getPlayer(), packet);
        ignoreSpectatorFilter = false;
    }

    private BlockRayTrace.HitResult rayTraceWithEntities(BlockRayTrace rayTrace) {
        // Only check for entities that isn't the player being controlled, and isn't another train
        // We already do our own hit-test for the trains, so we don't want those being counted double with
        // an invalid server ghost.
        return rayTrace.rayTrace(true, entity -> entity != player.getPlayer() && MinecartMemberStore.getFromEntity(entity) == null);
    }

    private void sendArmSwingPacket1P(HumanHandRole handRole) {
        player.send(ClientboundSwingAnimationPacketHandle.createNewWhack(player.getEntityId(), handRole));
    }

    private void sendArmSwingPacket3P(HumanHandRole handRole) {
        player.send(ClientboundSwingAnimationPacketHandle.createNewWhack(fakePlayer.getFakePlayer().getEntityId(), handRole));
    }

    private PacketHandle createBreakBlockPacket(IntVector3 position, BlockFace hitFace) {
        return ServerboundPlayerActionPacketHandle.createNew(
                position, hitFace,
                ServerboundPlayerActionPacketHandle.ActionHandle.START_DESTROY_BLOCK,
                0
        );
    }

    private PacketHandle createAirPunchPacket(HumanHandRole handRole) {
        return ServerboundPunchPacketHandle.createNew(handRole);
    }

    /**
     * Plays the full interaction flow for attack/interact. Does ray-tracing and then sends a packet
     * in the name of the client interacting with that block, entity or train. Always runs on main thread.
     *
     * @param isAttack True for attack, false for right-click interaction
     * @return Packet to be received by the client. Replaces the original packet.
     */
    private PacketHandle fakeInteraction(boolean isAttack) {
        // Could be this session ended early
        if (this.fakePlayer == null) {
            return null;
        }

        // Figure out what arm is doing the action. This is the main hand,
        // unless that hand is empty and the off hand is not.
        HumanHandRole hand = HumanHandRole.MAIN;
        ItemStack itemInMainHand = hand.getHeldItem(player.getPlayer());
        if (ItemUtil.isEmpty(itemInMainHand)) {
            ItemStack itemInOffHand = hand.opposite().getHeldItem(player.getPlayer());
            if (!ItemUtil.isEmpty(itemInOffHand)) {
                hand = HumanHandRole.OFF;
            }
        }

        // Perform a ray-trace to find the block or entity the player clicked
        // To find the ray orientation, use the 'camera' orientation last synchronized (which will match the look packets)
        VirtualEntity fakeEntity = this.fakePlayer.getFakePlayer();
        Vector syncPos = fakeEntity.getSyncPos();
        Location eye = new Location(this.player.getPlayer().getWorld(),
                syncPos.getX(), syncPos.getY() + DebugUtil.getDoubleValue("eye", VirtualEntity.PLAYER_STANDING_EYE_HEIGHT), syncPos.getZ(),
                fakeEntity.getSyncYaw(), fakeEntity.getSyncPitch());

        // Vanilla blocks and entities
        BlockRayTrace rayTrace = BlockRayTrace.fromEye(eye, 5.0);
        BlockRayTrace.HitResult hitResult;
        if (Common.hasCapability("BlockRayTrace:EntityRayTrace")) {
            hitResult = rayTraceWithEntities(rayTrace);
        } else {
            // Block-only fallback
            hitResult = rayTrace.rayTrace();
        }

        // TrainCarts trains (attachments!)
        MinecartMemberStore.HitTestResult trainHitResult = MinecartMemberStore.hitTest(eye, 5.0);

        // Avoid confusion when both have results
        if (trainHitResult != null && hitResult != null) {
            if (trainHitResult.distance < hitResult.getHitDistanceFromStart()) {
                hitResult = null;
            } else {
                trainHitResult = null;
            }
        }

        // Send a packet making the player animate their arms, as otherwise it looks weird
        // to the player. Only needed for right-click interaction in first person when clicking at things.
        // Left-click (attack) always animates in first person, but we do have to animate the fake player
        // with that as it is otherwise not visible in third-person.
        if (Common.hasCapability("Packets:ClientboundSwingAnimationPacket")) {
            if (!isAttack && (hitResult != null || trainHitResult != null)) {
                sendArmSwingPacket1P(hand);
            }
            if (isAttack ||  (hitResult != null || trainHitResult != null)) {
                sendArmSwingPacket3P(hand);
            }
        }

        // We handle train interaction ourselves, not through packets
        // Double-handling of this will just be more complicated. Plus, then we need to poke into
        // attachment internals to figure out what entity id to use for the clicked entity.
        if (trainHitResult != null) {
            AttachmentInteractEvent interactEvent = new AttachmentInteractEvent(
                    player.getPlayer(),
                    isAttack,
                    HumanHand.getMainHand(player.getPlayer()),
                    false,
                    -1, /* Is this a problem? */
                    trainHitResult.attachment,
                    trainHitResult.hitPosition);

            fakePlayer.getTrainCarts().getPlayerInteractionListener().handleInteractionWithAttachment(trainHitResult.member, interactEvent);
            return null;
        }

        if (hitResult == null) {
            // Interaction with air. Do we need to do something special?
            if (!isAttack) {
                return ServerboundUseItemPacketHandle.createNew(
                        hand,
                        System.currentTimeMillis(),
                        0,
                        eye.getYaw(), eye.getPitch());
            } else if (Common.hasCapability("Packets:SwingIsNowPunchPacket")) {
                return createAirPunchPacket(hand);
            } else {
                return null;
            }
        }

        if (isAttack) {
            // Left-click attack
            if (hitResult.isEntityHit()) {
                return ServerboundAttackPacketHandle.createNew(hitResult.getHitEntity().getEntityId());
            } else {
                if (Common.hasCapability("Packets:ServerboundPlayerActionPacket:Constructor")) {
                    return createBreakBlockPacket(IntVector3.coordinatesOf(hitResult.getHitBlock()), hitResult.getHitFace());
                }
            }
        } else {
            // Right-click interaction
            if (hitResult.isEntityHit()) {
                return ServerboundInteractPacketHandle.createNew(
                        hitResult.getHitEntity().getEntityId(),hand, hitResult.getAbsolutePosition());
            } else {
                onBlockInteract(hitResult.getHitBlock());

                BlockHitResultHandle blockHitResult = BlockHitResultHandle.createNew(
                        hitResult.getAbsolutePosition(),
                        hitResult.getHitFace(),
                        IntVector3.coordinatesOf(hitResult.getHitBlock()),
                        false);

                receivePacketWithoutSuppress(ServerboundUseItemOnPacketHandle.createNew(
                        hand,
                        blockHitResult,
                        0,
                        System.currentTimeMillis()));

                receivePacketWithoutSuppress(ServerboundUseItemPacketHandle.createNew(
                        hand,
                        System.currentTimeMillis(),
                        0,
                        eye.getYaw(), eye.getPitch()));
            }
        }
        return null;
    }

    /**
     * Called after the player initiated a right-click on a block. This handles some fallback logic for when
     * the client plays a sound when pressing on buttons for example. Otherwise the client hears no sound.
     *
     * @param block Block interacted with
     */
    private void onBlockInteract(Block block) {
        if (MaterialUtil.ISBUTTON.get(block)) {
            PlayerUtil.playSound(player.getPlayer(), SoundEffect.CLICK, "block", 0.3f, 0.6f);
        }
    }
}
