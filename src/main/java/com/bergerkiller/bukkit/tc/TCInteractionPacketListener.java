package com.bergerkiller.bukkit.tc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.Task;
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
public class TCInteractionPacketListener implements PacketListener {
    private final Map<Player, Long> lastHitTime = new HashMap<Player, Long>();

    public static final PacketType[] TYPES = {
            PacketType.IN_USE_ITEM,
            PacketType.IN_BLOCK_PLACE,
            PacketType.IN_ENTITY_ANIMATION,
            PacketType.IN_BLOCK_DIG
    };

    private void storeHit(Player player) {
        if (lastHitTime.isEmpty()) {
            new HitTimeCleanTask(TrainCarts.plugin).start(1, 1);
        }
        lastHitTime.put(player, System.currentTimeMillis());
    }

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
        if (event.getType() == PacketType.IN_USE_ITEM) {
            storeHit(event.getPlayer());
        }

        // The arm swing animation fires multiple times in rapid succession; cancel those
        if (event.getType() == PacketType.IN_ENTITY_ANIMATION && lastHitTime.containsKey(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        // Ignore IN_BLOCK_DIG packets that are uninteresting to us
        if (event.getType() == PacketType.IN_BLOCK_DIG) {
            String status = event.getPacket().read(PacketType.IN_BLOCK_DIG.status).toString();
            if (!status.equals("START_DESTROY_BLOCK")) {
                return;
            }
        }

        // For BLOCK_DIG and arm swings we check if clicking on a player, handling the entity attack() event
        // When we cancel a BLOCK_DIG event, be sure to send a block update of the block being dug
        if (event.getType() == PacketType.IN_BLOCK_DIG || event.getType() == PacketType.IN_ENTITY_ANIMATION) {
            MinecartMember<?> member = MinecartMemberStore.getFromHitTest(event.getPlayer().getEyeLocation());
            if (member != null) {
                event.setCancelled(true);
                storeHit(event.getPlayer());
                TCPacketListener.fakeAttack(member, event.getPlayer());

                // When packet is IN_BLOCK_DIG, send a block change to correct the changed block
                if (event.getType() == PacketType.IN_BLOCK_DIG) {
                    IntVector3 pos = event.getPacket().read(PacketType.IN_BLOCK_DIG.position);
                    cancelBlockChanges(event.getPlayer(), pos);
                }
            }
            return;
        }

        // IN_BLOCK_PLACE occurs when the player right-clicks the air while holding an item
        // USE_ITEM occurs when the player right-clicks a block
        // In both cases, when clicking on a cart, cancel the event and perform interaction
        if (event.getType() == PacketType.IN_BLOCK_PLACE || event.getType() == PacketType.IN_USE_ITEM) {
            MinecartMember<?> member = MinecartMemberStore.getFromHitTest(event.getPlayer().getEyeLocation());
            if (member != null) {
                HumanHand hand;
                if (event.getType() == PacketType.IN_BLOCK_PLACE) {
                    hand = PacketType.IN_BLOCK_PLACE.getHand(event.getPacket(), event.getPlayer());
                } else {
                    hand = PacketType.IN_USE_ITEM.getHand(event.getPacket(), event.getPlayer());

                    // Cancel any block changes by re-sending the block clicked (and the block at the face)
                    IntVector3 pos = event.getPacket().read(PacketType.IN_USE_ITEM.position);
                    BlockFace dir =  event.getPacket().read(PacketType.IN_USE_ITEM.direction);
                    cancelBlockChanges(event.getPlayer(), pos);
                    cancelBlockChanges(event.getPlayer(), pos.add(dir));
                }

                event.setCancelled(true);
                TCPacketListener.fakeInteraction(member, event.getPlayer(), hand);
            }

            return;
        }
    }

    private final class HitTimeCleanTask extends Task {

        public HitTimeCleanTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            long timeout = System.currentTimeMillis() - 150;
            Iterator<Long> iter = lastHitTime.values().iterator();
            while (iter.hasNext()) {
                if (iter.next().longValue() >= timeout) {
                    iter.remove();
                }
            }
            if (lastHitTime.isEmpty()) {
                this.stop();
            }
        }

    }
}
