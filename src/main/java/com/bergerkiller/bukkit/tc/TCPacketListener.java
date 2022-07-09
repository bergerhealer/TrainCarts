package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.conversion.type.HandleConversion;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayInUseEntityHandle;
import com.bergerkiller.generated.net.minecraft.world.EnumHandHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.player.EntityHumanHandle;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Temporary (???) packet listener to handle and cancel player SHIFT presses to cancel vehicle exit
 */
class TCPacketListener implements PacketListener {
    public static final int ATTACK_SUPPRESS_DURATION = 250; // 250ms
    public static final PacketType[] LISTENED_TYPES = new PacketType[]{
            PacketType.IN_STEER_VEHICLE, PacketType.IN_USE_ENTITY, PacketType.IN_ENTITY_ACTION,
            PacketType.IN_POSITION, PacketType.IN_POSITION_LOOK
    };

    private final Map<Player, Long> lastHitTime = new HashMap<Player, Long>();

    public static void fakeAttack(final MinecartMember<?> member, final Player player) {
        // Fix cross-thread access
        if (!CommonUtil.isMainThread()) {
            CommonUtil.nextTick(() -> fakeAttack(member, player));
            return;
        }

        // If member is unloaded or was despawned during this time, it's no longer valid
        if (member == null || member.isUnloaded() || player == null || !player.isOnline()) return;

        EntityHumanHandle.createHandle(HandleConversion.toEntityHandle(player)).attack(member.getEntity().getEntity());
    }

    public void suppressAttacksFor(Player player, int durationMillis) {
        synchronized (lastHitTime) {
            if (lastHitTime.isEmpty()) new HitTimeCleanTask(TrainCarts.plugin).start(1, 1);
            lastHitTime.put(player, System.currentTimeMillis() + durationMillis);
        }
    }

    public boolean isAttackSuppressed(Player player) {
        synchronized (lastHitTime) {
            return lastHitTime.containsKey(player);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Thread.dumpStack();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        CommonPacket packet = event.getPacket();
        Player player = event.getPlayer();

        // Note: sometimes an unmount packet is sent before the player is actually inside a vehicle.
        // However, this could also be because of a virtual invisible entity being controlled by the player.
        // We will allow the unmount, but if it spawns a vehicle exit event later on, we must cancel that
        // event. This is a compromise so that other plugins can still freely eject the player, without
        // the player exit property blocking that behavior.
        if (event.getType() == PacketType.IN_ENTITY_ACTION) {
            String action = ((Enum<?>) packet.read(PacketType.IN_ENTITY_ACTION.action)).name();
            if (action.equals("START_SNEAKING") || action.equals("PRESS_SHIFT_KEY")) {
                // Player wants to exit, if inside a vehicle
                if (player.getVehicle() == null) TCSeatChangeListener.markForUnmounting(player);
                else // Cancel it!
                    if (!TrainCarts.handlePlayerVehicleChange(player, null)) event.setCancelled(true);
            }
        }
        if (event.getType() == PacketType.IN_STEER_VEHICLE && packet.read(PacketType.IN_STEER_VEHICLE.unmount)) {
            // Handle vehicle exit cancelling
            if (player.getVehicle() == null) TCSeatChangeListener.markForUnmounting(player);
            else if (!TrainCarts.handlePlayerVehicleChange(player, null))
                packet.write(PacketType.IN_STEER_VEHICLE.unmount, false);
        }

        if (event.getType() == PacketType.IN_USE_ENTITY) {
            PacketPlayInUseEntityHandle packet_use = PacketPlayInUseEntityHandle.createHandle(event.getPacket().getHandle());

            // Since 1.16 this packet has a sneaking property
            // If we're inside a vehicle, disable it
            if (packet_use.isUsingSecondaryAction())
                if (player.getVehicle() == null) TCSeatChangeListener.markForUnmounting(player);
                else if (!TrainCarts.handlePlayerVehicleChange(player, null)) packet_use.setUsingSecondaryAction(false);

            // When a player interacts with a virtual attachment, the main entity should receive the interaction
            int entityId = packet_use.getUsedEntityId();
            if (WorldUtil.getEntityById(event.getPlayer().getWorld(), entityId) != null)
                return; // Is a valid Entity. Ignore it.

            // Don't know how to deal with this one
            if (event.getPlayer().getGameMode().name().equals("SPECTATOR")) return;

            // Find all Minecart entities that are nearby the player
            final Location eyeLoc = event.getPlayer().getEyeLocation();
            try (ImplicitlySharedSet<MinecartGroup> groups = MinecartGroupStore.getGroups().clone()) {
                for (MinecartGroup group : groups) {
                    if (group.getWorld() != eyeLoc.getWorld()) continue;

                    for (MinecartMember<?> member : group) {
                        if (!member.getAttachments().isViewer(event.getPlayer()))
                            continue; // If not visible, don't loop through the model to check this
                        if (!member.getAttachments().isAttachment(entityId)) continue; // Id is not used in the model

                        // UseAction INTERACT_AT fires for all entities, including Armorstands
                        // The INTERACT only fires for interactable entities, like Minecarts
                        // Since INTERACT_AT also fires for Minecarts, it is easier to ignore INTERACT
                        // and do all handling using INTERACT_AT.
                        if (packet_use.isInteract()) {
                            event.setCancelled(true);
                            return;
                        }

                        // If nearby the player, allow standard interaction. Otherwise, do all of this ourselves.
                        // Minecraft enforces a 3 block radius when not having line of sight, assume this limit.
                        if (member.getEntity().loc.distanceSquared(eyeLoc) < (3.0 * 3.0)) {

                            // For some reason this is needed, though.
                            if (packet_use.isInteractAt()) {
                                HumanHand hand = packet_use.getInteractHand(event.getPlayer());
                                packet_use.setInteract(event.getPlayer(), hand);
                            }

                            // Must track this to cancel superfluous LEFT clicks that happen later
                            if (packet_use.isInteract() || packet_use.isInteractAt())
                                this.suppressAttacksFor(event.getPlayer(), ATTACK_SUPPRESS_DURATION);

                            // Rewrite the packet
                            packet_use.setUsedEntityId(member.getEntity().getEntityId());
                            return; // Allow
                        }

                        // Cancel the interaction and handle this ourselves.
                        if (packet_use.isInteract() || packet_use.isInteractAt()) {
                            // Get hand used for interaction
                            fakeInteraction(member, event.getPlayer(), packet_use.getInteractHand(event.getPlayer()));
                            event.setCancelled(true);
                        } else if (packet_use.isAttack()) {
                            // Attack
                            fakeAttack(member, event.getPlayer());
                            event.setCancelled(true);
                        }
                        return;
                    }
                }
            }
        }
    }

    public void fakeInteraction(final MinecartMember<?> member, final Player player, final HumanHand hand) {
        this.suppressAttacksFor(player, ATTACK_SUPPRESS_DURATION);

        // Fix cross-thread access
        if (!CommonUtil.isMainThread()) {
            CommonUtil.nextTick(() -> fakeInteraction(member, player, hand));
            return;
        }

        // If member is unloaded or was despawned during this time, it's no longer valid
        if (member == null || member.isUnloaded() || player == null || !player.isOnline()) {
            return;
        }

        // Pre-1.9
        if (EnumHandHandle.T.isAvailable()) {
            // Fire a Bukkit event first, as defined in PlayerConnection PacketPlayInUseEntity handler
            EquipmentSlot slot = EquipmentSlot.HAND;
            if (hand != HumanHand.getMainHand(player)) {
                // Needed in case it errors out for no reason on MC 1.8 or somesuch
                try {
                    slot = EquipmentSlot.OFF_HAND;
                } catch (Throwable ignored) {
                }
            }

            // Post-1.9: EquipmentSlot parameter
            if (CommonUtil.callEvent(new PlayerInteractEntityEvent(player, member.getEntity().getEntity(), slot))
                    .isCancelled()) return;
        } else if (CommonUtil.callEvent(new PlayerInteractEntityEvent(player, member.getEntity().getEntity())).isCancelled())
            return;

        // Interact
        member.onInteractBy(player, hand);
    }

    private final class HitTimeCleanTask extends Task {

        public HitTimeCleanTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            synchronized (lastHitTime) {
                final long timeout = System.currentTimeMillis();
                lastHitTime.values().removeIf(aLong -> timeout >= aLong);
                if (lastHitTime.isEmpty()) this.stop();
            }
        }
    }
}
