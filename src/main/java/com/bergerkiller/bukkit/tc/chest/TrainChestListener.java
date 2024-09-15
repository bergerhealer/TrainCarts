package com.bergerkiller.bukkit.tc.chest;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.bergerkiller.bukkit.common.collections.EntityMap;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCListener;
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
    private final TrainCarts plugin;
    private final EntityMap<Player, Integer> ticksSinceLastAction = new EntityMap<Player, Integer>();

    public TrainChestListener(TrainCarts plugin) {
        this.plugin = plugin;
    }

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
        CommonItemStack heldItem = CommonItemStack.of(HumanHand.getItemInMainHand(event.getPlayer()));
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
        if (!Permission.COMMAND_STORAGE_CHEST_USE.has(event.getPlayer())) {
            Localization.CHEST_NOPERM.message(event.getPlayer());
            return;
        }

        // Parse group, check not empty
        TrainChestItemUtil.SpawnResult result;
        SpawnableGroup group = TrainChestItemUtil.getSpawnableGroup(plugin, heldItem);

        // Options for spawning the train
        TrainChestItemUtil.SpawnOptions spawnOptions = new TrainChestItemUtil.SpawnOptions(event.getPlayer());
        spawnOptions.initialSpeed = TrainChestItemUtil.getSpeed(heldItem);
        spawnOptions.tryExtendTrains = !event.getPlayer().isSneaking();

        if (group == null) {
            // Invalid item, or empty item
            result = TrainChestItemUtil.SpawnResult.FAIL_EMPTY;
        } else if (!group.checkSpawnPermissions(event.getPlayer())) {
            // No permission to spawn a train with these types of carts
            result = TrainChestItemUtil.SpawnResult.FAIL_NO_PERM;
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Clicked on a block that could be a rails block itself
            result = TrainChestItemUtil.spawnAtBlock(group, event.getClickedBlock(), spawnOptions);
            if (result == TrainChestItemUtil.SpawnResult.FAIL_NORAIL) {
                // Try to spawn looking at instead as a fall-back
                result = TrainChestItemUtil.spawnLookingAt(group, event.getPlayer(), event.getPlayer().getEyeLocation(), spawnOptions);
                if (result == TrainChestItemUtil.SpawnResult.FAIL_NORAIL_LOOK) {
                    result = TrainChestItemUtil.SpawnResult.FAIL_NORAIL;
                }
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            // Follow where the player is looking and spawn there
            result = TrainChestItemUtil.spawnLookingAt(group, event.getPlayer(), event.getPlayer().getEyeLocation(), spawnOptions);
        } else {
            // Impossible
            result = TrainChestItemUtil.SpawnResult.FAIL_NORAIL_LOOK;
        }

        // Swap out for an empty chest item if finite
        // Remove item if the chest item is also locked
        if (result == TrainChestItemUtil.SpawnResult.SUCCESS && TrainChestItemUtil.isFiniteSpawns(heldItem)) {
            if (TrainChestItemUtil.isLocked(heldItem)) {
                HumanHand.setItemInMainHand(event.getPlayer(), heldItem.clone()
                        .subtractAmount(1)
                        .toBukkit());
            } else {
                heldItem = heldItem.clone();
                TrainChestItemUtil.clear(heldItem);
                HumanHand.setItemInMainHand(event.getPlayer(), heldItem.toBukkit());
            }
        }

        if (result.hasMessage()) {
            String customSpawnMessage = null;
            if (result == TrainChestItemUtil.SpawnResult.SUCCESS) {
                customSpawnMessage = TrainChestItemUtil.getSpawnMessage(heldItem);
            }

            if (customSpawnMessage == null) {
                result.getLocale().message(event.getPlayer());
            } else if (!customSpawnMessage.isEmpty()) {
                event.getPlayer().sendMessage(customSpawnMessage);
            }
        }

        if (result == TrainChestItemUtil.SpawnResult.SUCCESS) {
            TrainChestItemUtil.playSoundSpawn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Handle clicking groups while holding a train storage chest
        CommonItemStack heldItem = CommonItemStack.of(HumanHand.getItemInMainHand(event.getPlayer()));
        if (TrainChestItemUtil.isItem(heldItem)) {
            event.setCancelled(true);

            // Avoid spam
            if (!spamCheck(event.getPlayer()) ) {
                return;
            }

            if (!Permission.COMMAND_STORAGE_CHEST_USE.has(event.getPlayer())) {
                Localization.CHEST_NOPERM.message(event.getPlayer());
                return;
            }
            if (TrainChestItemUtil.isLocked(heldItem)) {
                Localization.CHEST_LOCKED.message(event.getPlayer());
                return;
            }
            if (!TrainChestItemUtil.isEmpty(heldItem) && TrainChestItemUtil.isFiniteSpawns(heldItem)) {
                Localization.CHEST_FULL.message(event.getPlayer());
                return;
            }

            MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getRightClicked());
            if (member == null || member.isUnloaded() || member.getGroup() == null) {
                return;
            }
            if (!member.getProperties().hasOwnership(event.getPlayer())) {
                Localization.EDIT_NOTOWNED.message(event.getPlayer());
                return;
            }

            heldItem = heldItem.clone();
            TrainChestItemUtil.store(heldItem, member.getGroup());
            HumanHand.setItemInMainHand(event.getPlayer(), heldItem.toBukkit());
            Localization.CHEST_PICKUP.message(event.getPlayer());
            TrainChestItemUtil.playSoundStore(event.getPlayer());

            if (!event.getPlayer().isSneaking() || TrainChestItemUtil.isFiniteSpawns(heldItem)) {
                boolean wasCancelled = TCListener.cancelNextDrops;
                try {
                    TCListener.cancelNextDrops = true;
                    member.getGroup().destroy();
                } finally {
                    TCListener.cancelNextDrops = wasCancelled;
                }
            }

            return;
        }
    }
}
