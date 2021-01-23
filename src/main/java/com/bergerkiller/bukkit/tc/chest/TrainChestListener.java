package com.bergerkiller.bukkit.tc.chest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;

/**
 * Listens for player interaction events, and if holding the
 * spawn chest item, initiates the spawn logic.
 */
public class TrainChestListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (TrainCarts.isWorldDisabled(event.getPlayer().getWorld())) {
            return;
        }

        // Train spawning chest item
        ItemStack heldItem = HumanHand.getItemInMainHand(event.getPlayer());
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && TrainChestItemUtil.isItem(heldItem)) {
            if (!Permission.COMMAND_USE_STORAGE_CHEST.has(event.getPlayer())) {
                Localization.CHEST_NOPERM.message(event.getPlayer());
                return;
            }

            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);

            TrainChestItemUtil.SpawnResult result;
            result = TrainChestItemUtil.spawn(heldItem, event.getPlayer(), event.getClickedBlock());
            result.getLocale().message(event.getPlayer());
            if (result == TrainChestItemUtil.SpawnResult.SUCCESS) {
                TrainChestItemUtil.playSoundSpawn(event.getPlayer());
            }

            return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Handle clicking groups while holding a train storage chest
        ItemStack heldItem = HumanHand.getItemInMainHand(event.getPlayer());
        if (TrainChestItemUtil.isItem(heldItem)) {
            event.setCancelled(true);
            if (!Permission.COMMAND_USE_STORAGE_CHEST.has(event.getPlayer())) {
                Localization.CHEST_NOPERM.message(event.getPlayer());
                return;
            }
            if (TrainChestItemUtil.isLocked(heldItem)) {
                Localization.CHEST_LOCKED.message(event.getPlayer());
                return;
            }

            MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getRightClicked());
            if (member == null || member.isUnloaded() || member.getGroup() == null) {
                return;
            }

            heldItem = heldItem.clone();
            TrainChestItemUtil.store(heldItem, member.getGroup());
            HumanHand.setItemInMainHand(event.getPlayer(), heldItem);
            Localization.CHEST_PICKUP.message(event.getPlayer());
            TrainChestItemUtil.playSoundStore(event.getPlayer());

            if (!event.getPlayer().isSneaking()) {
                member.getGroup().destroy();
            }

            return;
        }
    }
}
