package com.bergerkiller.bukkit.tc;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;

/**
 * Listens to block interaction packets and translates them into packets
 * interacting with minecarts that are in those positions.
 */
class TCInteractionPacketListener implements PacketListener {
    private final TCPacketListener mainPacketListener;

    TCInteractionPacketListener(TCPacketListener mainPacketListener) {
        this.mainPacketListener = mainPacketListener;
    }

    public static final PacketType[] TYPES = {
            PacketType.IN_USE_ITEM,
            PacketType.IN_USE_ITEM_ON,
            PacketType.IN_SWING,
            PacketType.IN_PLAYER_ACTION
    };

    private void cancelBlockChanges(Player player, IntVector3 pos) {
        // Check chunk is loaded in this area to protect against rogue client data
        if (WorldUtil.isLoaded(player.getWorld(), pos.x, pos.y, pos.z)) {
            CommonPacket bcPacket = PacketType.OUT_BLOCK_CHANGE.newInstance();
            bcPacket.write(PacketType.OUT_BLOCK_CHANGE.position, pos);
            bcPacket.write(PacketType.OUT_BLOCK_CHANGE.blockData, WorldUtil.getBlockData(player.getWorld(), pos));
            PacketUtil.sendPacket(player, bcPacket);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // If disabled, do nothing
        if (!TCConfig.optimizeInteraction) {
            return;
        }

        // Ignore all this logic while the player is sneaking, so that interacting with blocks still works
        if (event.getPlayer().isSneaking()) {
            return;
        }

        // Store last hit when USE_ITEM is called, to avoid ENTITY_ANIMATION attack() being called incorrectly
        if (event.getType() == PacketType.IN_USE_ITEM_ON) {
            mainPacketListener.suppressAttacksFor(event.getPlayer(), TCPacketListener.ATTACK_SUPPRESS_DURATION);
        }

        // Ignore IN_BLOCK_DIG packets that are uninteresting to us
        if (event.getType() == PacketType.IN_PLAYER_ACTION) {
            String status = event.getPacket().read(PacketType.IN_PLAYER_ACTION.status).toString();
            if (!status.equals("START_DESTROY_BLOCK")) {
                return;
            }
        }

        // For BLOCK_DIG and arm swings we check if clicking on a player, handling the entity attack() event
        // When we cancel a BLOCK_DIG event, be sure to send a block update of the block being dug
        // For arm animation, make sure it is that of the player's off hand.
        boolean isAttackClick = false;
        if (event.getType() == PacketType.IN_PLAYER_ACTION) {
            isAttackClick = true;
        } else if (event.getType() == PacketType.IN_SWING) {
            HumanHand hand = PacketType.IN_SWING.getHand(event.getPacket(), event.getPlayer());
            if (hand == HumanHand.getOffHand(event.getPlayer())) {
                if (mainPacketListener.isAttackSuppressed(event.getPlayer())) {
                    event.setCancelled(true);
                    return;
                }
                isAttackClick = true;
            }
        }
        if (isAttackClick) {
            MinecartMember<?> member = MinecartMemberStore.getFromHitTest(Util.getRealEyeLocation(event.getPlayer()));
            if (member != null) {
                event.setCancelled(true);
                TCPacketListener.fakeAttack(member, event.getPlayer());

                // When packet is IN_BLOCK_DIG, send a block change to correct the changed block
                if (event.getType() == PacketType.IN_PLAYER_ACTION) {
                    IntVector3 pos = event.getPacket().read(PacketType.IN_PLAYER_ACTION.position);
                    cancelBlockChanges(event.getPlayer(), pos);
                }
            }
            return;
        }

        // IN_BLOCK_PLACE occurs when the player right-clicks the air while holding an item
        // USE_ITEM occurs when the player right-clicks a block
        // In both cases, when clicking on a cart, cancel the event and perform interaction
        if (event.getType() == PacketType.IN_USE_ITEM || event.getType() == PacketType.IN_USE_ITEM_ON) {
            MinecartMember<?> member = MinecartMemberStore.getFromHitTest(event.getPlayer().getEyeLocation());
            if (member != null) {
                HumanHand hand;
                if (event.getType() == PacketType.IN_USE_ITEM) {
                    hand = PacketType.IN_USE_ITEM.getHand(event.getPacket(), event.getPlayer());
                } else {
                    hand = PacketType.IN_USE_ITEM_ON.getHand(event.getPacket(), event.getPlayer());

                    // Cancel any block changes by re-sending the block clicked (and the block at the face)
                    IntVector3 pos = event.getPacket().read(PacketType.IN_USE_ITEM_ON.position);
                    BlockFace dir =  event.getPacket().read(PacketType.IN_USE_ITEM_ON.direction);
                    cancelBlockChanges(event.getPlayer(), pos);
                    cancelBlockChanges(event.getPlayer(), pos.add(dir));
                }

                event.setCancelled(true);
                this.mainPacketListener.fakeInteraction(member, event.getPlayer(), hand, member.getEntity().getLocation().toVector());
            }

            return;
        }
    }
}
