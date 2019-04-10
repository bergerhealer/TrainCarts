package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.conversion.type.HandleConversion;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.common.wrappers.UseAction;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;
import com.bergerkiller.generated.net.minecraft.server.EntityHumanHandle;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Temporary (???) packet listener to handle and cancel player SHIFT presses to cancel vehicle exit
 */
public class TCPacketListener implements PacketListener {

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Thread.dumpStack();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        CommonPacket packet = event.getPacket();
        if (event.getType() == PacketType.IN_ENTITY_ACTION) {
            Player player = event.getPlayer();
            String action = packet.read(PacketType.IN_ENTITY_ACTION.actionId).toString();
            if (player.getVehicle() == null && action.equals("START_SNEAKING")) {
                TCListener.markForUnmounting(player);
            }
        }
        if (event.getType() == PacketType.IN_STEER_VEHICLE && packet.read(PacketType.IN_STEER_VEHICLE.unmount)) {
            // Handle vehicle exit cancelling
            Player player = event.getPlayer();

            // Note: sometimes an unmount packet is sent before the player is actually inside a vehicle.
            // However, this could also be because of a virtual invisible entity being controlled by the player.
            // We will allow the unmount, but if it spawns a vehicle exit event later on, we must cancel that
            // event. This is a compromise so that other plugins can still freely eject the player, without
            // the player exit property blocking that behavior.
            if (player.getVehicle() == null) {
                TCListener.markForUnmounting(player);
            } else if (!TrainCarts.handlePlayerVehicleChange(player, null)) {
                packet.write(PacketType.IN_STEER_VEHICLE.unmount, false);
            }
        } else if (event.getType() == PacketType.IN_USE_ENTITY) {
            // When a player interacts with a virtual attachment, the main entity should receive the interaction
            int entityId = packet.read(PacketType.IN_USE_ENTITY.clickedEntityId);
            if (WorldUtil.getEntityById(event.getPlayer().getWorld(), entityId) != null) {
                return; // Is a valid Entity. Ignore it.
            }

            // Don't know how to deal with this one
            if (event.getPlayer().getGameMode().name().equals("SPECTATOR")) {
                return;
            }

            // Find all Minecart entities that are nearby the player
            Location eyeLoc = event.getPlayer().getEyeLocation();
            try (ImplicitlySharedSet<MinecartGroup> groups = MinecartGroupStore.getGroups().clone()) {
                for (MinecartGroup group : groups) {
                    if (group.getWorld() != eyeLoc.getWorld()) {
                        continue;
                    }

                    for (MinecartMember<?> member : group) {
                        EntityNetworkController<?> enc_raw = member.getEntity().getNetworkController();
                        if (!(enc_raw instanceof MinecartMemberNetwork)) {
                            continue;
                        }

                        MinecartMemberNetwork enc = (MinecartMemberNetwork) enc_raw;
                        if (!enc.getViewers().contains(event.getPlayer())) {
                            continue; // If not visible, don't loop through the model to check this
                        }
                        if (!enc.handleInteraction(entityId)) {
                            continue; // Id is not used in the model
                        }

                        // Rewrite the packet
                        UseAction useAction = packet.read(PacketType.IN_USE_ENTITY.useAction);
                        packet.write(PacketType.IN_USE_ENTITY.clickedEntityId, member.getEntity().getEntityId());
                        if (useAction == UseAction.INTERACT_AT) {
                            useAction = UseAction.INTERACT;
                            packet.write(PacketType.IN_USE_ENTITY.useAction, useAction);
                        }

                        // If nearby the player, allow standard interaction. Otherwise, do all of this ourselves.
                        // Minecraft enforces a 3 block radius when not having line of sight, assume this limit.
                        if (member.getEntity().loc.distanceSquared(eyeLoc) < (3.0 * 3.0)) {
                            return; // Allow
                        }

                        // Cancel the interaction and handle this ourselves.
                        if (useAction == UseAction.INTERACT) {
                            // Get hand used for interaction
                            HumanHand hand = PacketType.IN_USE_ENTITY.getHand(packet, event.getPlayer());
                            fakeInteraction(member, event.getPlayer(), hand);
                            event.setCancelled(true);
                        } else if (useAction == UseAction.ATTACK) {
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

    public static void fakeAttack(final MinecartMember<?> member, final Player player) {
        // Fix cross-thread access
        if (!CommonUtil.isMainThread()) {
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    fakeAttack(member, player);
                }
            });
            return;
        }

        Object playerHandleRaw = HandleConversion.toEntityHandle(player);
        EntityHumanHandle.createHandle(playerHandleRaw).attack(member.getEntity().getEntity());
    }

    public static void fakeInteraction(final MinecartMember<?> member, final Player player, final HumanHand hand) {
        // Fix cross-thread access
        if (!CommonUtil.isMainThread()) {
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    fakeInteraction(member, player, hand);
                }
            });
            return;
        }

        HumanHand mainHand = HumanHand.getMainHand(player);

        // Fire a Bukkit event first, as defined in PlayerConnection PacketPlayInUseEntity handler
        EquipmentSlot slot = EquipmentSlot.HAND;
        if (hand != mainHand) {
            // Needed in case it errors out for no reason on MC 1.8 or somesuch
            try {
                slot = EquipmentSlot.OFF_HAND;
            } catch (Throwable t) {}
        }
        PlayerInteractEntityEvent interactEvent = new PlayerInteractEntityEvent(player, member.getEntity().getEntity(), slot);
        if (CommonUtil.callEvent(interactEvent).isCancelled()) {
            return;
        }

        // Interact
        member.onInteractBy(player, hand);
    }

}
