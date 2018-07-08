package com.bergerkiller.bukkit.tc;

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
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHumanHandle;

import java.lang.reflect.Method;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Temporary (???) packet listener to handle and cancel player SHIFT presses to cancel vehicle exit
 */
public class TCPacketListener implements PacketListener {
    private static boolean HAS_ATTACK_METHOD = true; // Added in later version of BKC

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Thread.dumpStack();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        CommonPacket packet = event.getPacket();
        if (event.getType() == PacketType.IN_STEER_VEHICLE && packet.read(PacketType.IN_STEER_VEHICLE.unmount)) {
            // Handle vehicle exit cancelling
            Player player = event.getPlayer();
            if (!TrainCarts.handlePlayerVehicleChange(player, null)) {
                packet.write(PacketType.IN_STEER_VEHICLE.unmount, false);
            }
        } else if (event.getType() == PacketType.IN_USE_ENTITY) {
            // When a player interacts with a virtual attachment, the main entity should receive the interaction
            int entityId = packet.read(PacketType.IN_USE_ENTITY.clickedEntityId);
            if (WorldUtil.getEntityById(event.getPlayer().getWorld(), entityId) != null) {
                return; // Is a valid Entity. Ignore it.
            }

            // Find all Minecart entities that are nearby the player
            Location eyeLoc = event.getPlayer().getEyeLocation();
            for (MinecartGroup group : MinecartGroupStore.getGroups()) {
                if (group.getWorld() != eyeLoc.getWorld()) {
                    continue;
                }

                for (MinecartMember<?> member : group) {
                    double max = 5.0 + 2.0 * ((double) member.getEntity().getWidth());
                    double dist_sq = member.getEntity().loc.distanceSquared(eyeLoc);
                    if (dist_sq > (max * max)) {
                        continue;
                    }

                    EntityNetworkController<?> enc = member.getEntity().getNetworkController();
                    if (enc instanceof MinecartMemberNetwork && ((MinecartMemberNetwork) enc).handleInteraction(entityId)) {
                        // Rewrite the packet
                        UseAction useAction = packet.read(PacketType.IN_USE_ENTITY.useAction);
                        packet.write(PacketType.IN_USE_ENTITY.clickedEntityId, member.getEntity().getEntityId());
                        if (useAction == UseAction.INTERACT_AT) {
                            useAction = UseAction.INTERACT;
                            packet.write(PacketType.IN_USE_ENTITY.useAction, useAction);
                        }

                        // If nearby the player, allow standard interaction. Otherwise, do all of this ourselves.
                        // Minecraft enforces a 3 block radius when not having line of sight, assume this limit.
                        if (event.getPlayer().getGameMode().name().equals("SPECTATOR")) {
                            return; // Don't know how to deal with this one
                        }
                        if (dist_sq < (3.0 * 3.0)) {
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

    public static void fakeAttack(MinecartMember<?> member, Player player) {
        if (HAS_ATTACK_METHOD) {
            try {
                Object playerHandleRaw = HandleConversion.toEntityHandle(player);
                EntityHumanHandle.createHandle(playerHandleRaw).attack(member.getEntity().getEntity());
            } catch (NoSuchMethodError e) {
                HAS_ATTACK_METHOD = false;
            }
        }
        if (!HAS_ATTACK_METHOD) {
            // Some slow crappy workaround for older versions of BKCommonLib
            try {
                Object playerHandleRaw = HandleConversion.toEntityHandle(player);
                Method m = EntityHumanHandle.T.getType().getDeclaredMethod("attack", EntityHandle.T.getType());
                m.invoke(playerHandleRaw, member.getEntity().getHandle());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static void fakeInteraction(MinecartMember<?> member, Player player, HumanHand hand) {
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
