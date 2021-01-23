package com.bergerkiller.bukkit.tc.chest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.collections.EntityMap;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;

/**
 * Listens for player interaction events, and if holding the
 * spawn chest item, initiates the spawn logic.
 */
public class TrainChestListener implements Listener {
    private static final int INTERACT_TIMEOUT_TICKS = 5;
    private final EntityMap<Player, Integer> ticksSinceLastAction = new EntityMap<Player, Integer>();

    /**
     * Interaction events can (in part due to TCInteractionPacketListener) result
     * in 5 interactions at once. This can cause strange glitches like rapidly spawning
     * and picking up a train. We prevent this by requiring some ticks between each
     * interaction.
     *
     * @param player The player to check
     * @return True if clicking is allowed, False it ignored
     */
    private boolean spamCheck(Player player) {
        final int currentTick = CommonUtil.getServerTicks();
        try {
            Integer t = ticksSinceLastAction.get(player);
            return (t == null) || ((currentTick - t.intValue()) >= INTERACT_TIMEOUT_TICKS);
        } finally {
            ticksSinceLastAction.put(player, currentTick);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (TrainCarts.isWorldDisabled(event.getPlayer().getWorld())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK &&
            event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        // Check train spawning chest item
        ItemStack heldItem = HumanHand.getItemInMainHand(event.getPlayer());
        if (!TrainChestItemUtil.isItem(heldItem)) {
            return;
        }

        // Block use of the item as an actual block
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);

        // Avoid spam
        if (!spamCheck(event.getPlayer()) ) {
            return;
        }

        // Perm check
        if (!Permission.COMMAND_USE_STORAGE_CHEST.has(event.getPlayer())) {
            Localization.CHEST_NOPERM.message(event.getPlayer());
            return;
        }

        // Parse group, check not empty
        TrainChestItemUtil.SpawnResult result;
        SpawnableGroup group = TrainChestItemUtil.getSpawnableGroup(heldItem);
        if (group == null) {
            // Invalid item, or empty item
            result = TrainChestItemUtil.SpawnResult.FAIL_EMPTY;
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Clicked on a block that could be a rails block itself
            result = TrainChestItemUtil.spawnAtBlock(group, event.getPlayer(), event.getClickedBlock());
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            // Follow where the player is looking and spawn there
            result = TrainChestItemUtil.spawnLookingAt(group, event.getPlayer(), event.getPlayer().getEyeLocation());
        } else {
            // Impossible
            result = TrainChestItemUtil.SpawnResult.FAIL_NORAIL;
        }

        result.getLocale().message(event.getPlayer());
        if (result == TrainChestItemUtil.SpawnResult.SUCCESS) {
            TrainChestItemUtil.playSoundSpawn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Handle clicking groups while holding a train storage chest
        ItemStack heldItem = HumanHand.getItemInMainHand(event.getPlayer());
        if (TrainChestItemUtil.isItem(heldItem)) {
            event.setCancelled(true);

            // Avoid spam
            if (!spamCheck(event.getPlayer()) ) {
                return;
            }

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
