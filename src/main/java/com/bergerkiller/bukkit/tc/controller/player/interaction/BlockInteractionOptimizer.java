package com.bergerkiller.bukkit.tc.controller.player.interaction;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Keeps track of the interactions a player does with blocks, and if that click would normally
 * hit the base bounding box of an (invisible) train, fires off a train interaction instead.
 * This logic is becoming less important now that the HITBOX attachment exists, but keeping it
 * around for now.
 */
final class BlockInteractionOptimizer {
    public static final int ATTACK_SUPPRESS_DURATION = 500; // 500ms
    private final TrainCartsPlayerInteractionHandler handler;
    private final TrainCarts traincarts;
    private final Map<Player, Long> lastHitTime = new HashMap<Player, Long>();

    public BlockInteractionOptimizer(TrainCartsPlayerInteractionHandler handler, TrainCarts traincarts) {
        this.handler = handler;
        this.traincarts = traincarts;
    }

    public void handlePacketReceive(PacketReceiveEvent event) {
        // Ignore all this logic while the player is sneaking, so that interacting with blocks still works
        if (event.getPlayer().isSneaking()) {
            return;
        }

        // Ignore IN_BLOCK_DIG packets that are uninteresting to us
        if (event.getType() == PacketType.IN_PLAYER_ACTION) {
            String status = event.getPacket().read(PacketType.IN_PLAYER_ACTION.status).toString();
            if (!status.equals("START_DESTROY_BLOCK")) {
                return;
            }
        }

        // Store last hit when IN_USE_ITEM_ON/IN_ATTACK/IN_PLAYER_ACTION/IN_INTERACT is called,
        // to avoid IN_PUNCH being mislabeled as an attack() of the air.
        // When players interact with say, minecarts, they fire this packet followed by the arm swing packet
        // We don't want that arm swing to be misinterpreted as a destroy punch action
        if (
                event.getType() == PacketType.IN_USE_ITEM_ON
                || event.getType() == PacketType.IN_ATTACK
                || event.getType() == PacketType.IN_PLAYER_ACTION
                || event.getType() == PacketType.IN_INTERACT
        ) {
            suppressAttacksFor(event.getPlayer(), ATTACK_SUPPRESS_DURATION);
        }

        // Ignore punch packets that the client sends following other interactions (see above)
        if (event.getType() == PacketType.IN_PUNCH || event.getType() == PacketType.IN_SWING) {
            if (isAttackSuppressed(event.getPlayer())) {
                return;
            }
        }

        // When the player attacks the air or starts destroying a block (left-click), check if there is a train there
        // If there is, this is handled and the original packet is suppressed.
        if (event.getType() == PacketType.IN_PLAYER_ACTION || event.getType() == PacketType.IN_PUNCH || event.getType() == PacketType.IN_SWING) {
            MinecartMember<?> member =  getFromHitBoundingBoxTest(Util.getRealEyeLocation(event.getPlayer()));
            if (member != null) {
                event.setCancelled(true);
                handler.simulateDefaultAttack(member, event.getPlayer());

                // When packet is IN_PLAYER_ACTION, send a block change to correct the changed block
                if (event.getType() == PacketType.IN_PLAYER_ACTION) {
                    IntVector3 pos = event.getPacket().read(PacketType.IN_PLAYER_ACTION.position);
                    cancelBlockChanges(event.getPlayer(), pos);
                }
            }
            return;
        }

        // When the player uses an item or clicks an entity, check if there is a train there.
        // If there is, handle the train instead so the player can easily get in.
        if (event.getType() == PacketType.IN_USE_ITEM || event.getType() == PacketType.IN_USE_ITEM_ON) {
            MinecartMember<?> member =  getFromHitBoundingBoxTest(Util.getRealEyeLocation(event.getPlayer()));
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
                handler.simulateDefaultInteract(member, event.getPlayer(), hand, member.getEntity().getLocation().toVector());
            }
        }
        return;
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

    /**
     * Finds a Minecart by performing a hit test with the minecart's primary bounding box.
     * Does not look at the Minecart's attachments, only the main bounding box of the Minecart itself.
     *
     * @param eyeLocation
     * @return member hit, null if none found
     */
    private static MinecartMember<?> getFromHitBoundingBoxTest(Location eyeLocation) {
        MinecartMember<?> best = null;
        double best_dist = 4.5;
        for (MinecartGroup group : MinecartGroupStore.getGroups().cloneAsIterable()) {
            if (group.getWorld() != eyeLocation.getWorld()) {
                continue;
            }

            for (int i = 0; i < group.size(); i++) {
                MinecartMember<?> member;
                try {
                    member = group.get(i);
                } catch (IndexOutOfBoundsException ex) {
                    break;
                }

                double max_rad = 2.0 * ((double) member.getEntity().getWidth());
                double dist_sq = member.getEntity().loc.distanceSquared(eyeLocation);
                if (dist_sq > (max_rad * max_rad)) {
                    continue;
                }

                double dist_hit = member.getHitBox().hitTest(eyeLocation);
                if (dist_hit >= best_dist) {
                    continue;
                }

                best_dist = dist_hit;
                best = member;
            }
        }
        return best;
    }

    public void suppressAttacksFor(Player player, int durationMillis) {
        synchronized (lastHitTime) {
            if (lastHitTime.isEmpty()) {
                new HitTimeCleanTask(traincarts).start(1, 1);
            }
            lastHitTime.put(player, System.currentTimeMillis() + durationMillis);
        }
    }

    public boolean isAttackSuppressed(Player player) {
        synchronized (lastHitTime) {
            return lastHitTime.containsKey(player);
        }
    }

    private final class HitTimeCleanTask extends Task {

        public HitTimeCleanTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            synchronized (lastHitTime) {
                long timeout = System.currentTimeMillis();
                Iterator<Long> iter = lastHitTime.values().iterator();
                while (iter.hasNext()) {
                    if (timeout >= iter.next().longValue()) {
                        iter.remove();
                    }
                }
                if (lastHitTime.isEmpty()) {
                    this.stop();
                }
            }
        }
    }
}
