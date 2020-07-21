package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.EntityMap;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.events.EntityAddEvent;
import com.bergerkiller.bukkit.common.events.EntityRemoveFromServerEvent;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.attachments.ProfileNameModifier;
import com.bergerkiller.bukkit.tc.attachments.control.light.LightAPIController;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.debug.DebugTool;
import com.bergerkiller.bukkit.tc.editor.TCMapControl;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.portals.PortalDestination;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bergerkiller.bukkit.tc.utils.StoredTrainItemUtil;
import com.bergerkiller.bukkit.tc.utils.TrackMap;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Rails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class TCListener implements Listener {
    private static final boolean DEBUG_DO_TRACKTEST = false;
    private static final boolean DEBUG_DO_INVISIBLE_TRACK = false;
    private static final long SIGN_CLICK_INTERVAL = 500; // Interval in MS where left-click interaction is allowed
    private static final long MAX_INTERACT_INTERVAL = 300; // Interval in MS where spam-interaction is allowed
    public static boolean cancelNextDrops = false;
    public static MinecartMember<?> killedByMember = null;
    public static List<Entity> exemptFromEjectOffset = new ArrayList<Entity>();
    private static Map<Player, Integer> markedForUnmounting = new HashMap<Player, Integer>();
    private EntityMap<Player, Long> lastHitTimes = new EntityMap<>();
    private EntityMap<Player, BlockFace> lastClickedDirection = new EntityMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Eject players from minecarts if player takable is false
        MinecartMember<?> vehicle = MinecartMemberStore.getFromEntity(event.getPlayer().getVehicle());
        if (vehicle != null && !vehicle.isPlayerTakable()) {
            // Eject the player before proceeding to the saving
            // This prevents the player 'taking' the minecart with him
            vehicle.getEntity().removePassenger(event.getPlayer());
        }

        // Clean up the fake teams we've sent
        ProfileNameModifier.onViewerQuit(event.getPlayer());
        TrainCarts.plugin.getGlowColorTeamProvider().reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (cancelNextDrops) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // This chunk is still referenced, ensure that it is really gone
        long chunkCoordLong = MathUtil.longHashToLong(event.getChunk().getX(), event.getChunk().getZ());
        OfflineGroupManager.lastUnloadChunk = Long.valueOf(chunkCoordLong);

        // Check no trains are keeping the chunk loaded
        World chunkWorld = event.getWorld();
        for (MinecartGroup group : MinecartGroup.getGroups().cloneAsIterable()) {
            if (group.isInChunk(chunkWorld, chunkCoordLong)) {
                unloadChunkForGroup(group, event.getChunk());
            }
        }

        // Double-check
        for (Entity entity : WorldUtil.getEntities(event.getChunk())) {
            if (entity instanceof Minecart) {
                MinecartMember<?> member = MinecartMemberStore.getFromEntity(entity);
                if (member == null || !member.isInteractable()) {
                    continue;
                }
                unloadChunkForGroup(member.getGroup(), event.getChunk());
            }
        }

        OfflineGroupManager.unloadChunk(event.getChunk());
        OfflineGroupManager.lastUnloadChunk = null;
    }

    private void unloadChunkForGroup(MinecartGroup group, Chunk chunk) {
        if (group.canUnload()) {
            group.unload();
        } else if (group.getChunkArea().containsChunk(chunk.getX(), chunk.getZ()))  {
            TrainCarts.plugin.log(Level.SEVERE, "Chunk " + chunk.getX() + "/" + chunk.getZ() +
                    " of group " + group.getProperties().getTrainName() + " unloaded unexpectedly!");
        } else {
            TrainCarts.plugin.log(Level.SEVERE, "Chunk " + chunk.getX() + "/" + chunk.getZ() +
                    " of group " + group.getProperties().getTrainName() + " unloaded because chunk area wasn't up to date!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        OfflineGroupManager.loadChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        // Refresh the groups on this world
        OfflineGroupManager.refresh(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (MinecartGroup group : MinecartGroup.getGroups().cloneAsIterable()) {
            if (group.getWorld() == event.getWorld()) {
                group.unload();
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("LightAPI")) {
            disableLightAPIWorld(event.getWorld());
        }
    }

    // Put in its own method to further avoid loading the class when LightAPI is not enabled
    private static void disableLightAPIWorld(World world) {
        LightAPIController.disableWorld(world);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        TrainCarts.plugin.getGlowColorTeamProvider().reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (killedByMember != null) {
            String deathMessage = killedByMember.getGroup().getProperties().getKillMessage();
            if (!deathMessage.isEmpty()) {
                deathMessage = deathMessage.replaceAll("%0%", event.getEntity().getDisplayName());
                deathMessage = deathMessage.replaceAll("%1%", killedByMember.getGroup().getProperties().getDisplayName());
                event.setDeathMessage(deathMessage);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityAdd(EntityAddEvent event) {
        if (MinecartMemberStore.canConvertAutomatically(event.getEntity())) {
            MinecartMemberStore.convert((Minecart) event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromServer(EntityRemoveFromServerEvent event) {
        if (event.getEntity() instanceof Minecart) {
            // Verify this entity UUID does not exist on the world as a new entity instance
            // This can happen when entities are unloaded and reloaded rapidly in the same tick
            // The entity Bukkit instance is removed, but the actual entity itself simply re-spawned
            UUID entityUUID = event.getEntity().getUniqueId();
            for (Entity otherEntity : WorldUtil.getEntities(event.getEntity().getWorld())) {
                if (otherEntity.getUniqueId().equals(entityUUID)) {
                    return;
                }
            }

            if (event.getEntity().isDead()) {
                OfflineGroupManager.removeMember(entityUUID);
            } else {
                MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getEntity());
                if (member == null) {
                    return;
                }
                MinecartGroup group = member.getGroup();
                if (group == null) {
                    return;
                }
                // Minecart was removed but was not dead - unload the group
                // This really should never happen - Chunk/World unload events take care of this
                // If it does happen, it implies that a chunk unloaded without raising an event
                if (group.canUnload()) {
                    TrainCarts.plugin.log(Level.WARNING, "Train '" + group.getProperties().getTrainName() + "' forcibly unloaded!");
                } else {
                    TrainCarts.plugin.log(Level.WARNING, "Train '" + group.getProperties().getTrainName() + "' had to be restored after unexpected unload");
                }
                group.unload();
                // For the next tick: update the storage system to restore trains here and there
                CommonUtil.nextTick(new Runnable() {
                    public void run() {
                        OfflineGroupManager.refresh();
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getVehicle());
        if (member == null) {
            return;
        }
        if (!member.isInteractable()) {
            event.setCancelled(true);
            return;
        }

        CartProperties prop = member.getProperties();

        if (event.getEntered() instanceof Player) {
            Player player = (Player) event.getEntered();
            if (!member.isPassengerEnterForced(event.getEntered())) {
                if (!prop.getPlayersEnter()) {
                    event.setCancelled(true);
                    return;
                }
                if (!prop.isPublic() && !prop.hasOwnership(player)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (!TicketStore.handleTickets(player, member.getGroup().getProperties())) {
                event.setCancelled(true);
                return;
            }
            CartPropertiesStore.setEditing(player, member.getProperties());
            prop.showEnterMessage(player);
        } else if (EntityUtil.isMob(event.getEntered())) {
            // This does not appear to be needed (anymore) to stop mobs from going into the Minecarts
            // Keeping this will cause enter signs or other plugins to no longer be able to add passengers
            //CollisionMode x = member.getGroup().getProperties().getCollisionMode(event.getEntered());
            //if (x != CollisionMode.ENTER) {
            //    event.setCancelled(true);
            //}
        }
        member.onPropertiesChanged();
    }

    /*
     * Bukkit now sends a VehicleExitEvent after a cancelled VehicleEnterEvent event.
     * To prevent the player teleporting into the Minecart, make him exempt here.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVehicleEnterCheck(VehicleEnterEvent event) {
        if (event.isCancelled()) {
            final Entity entered = event.getEntered();
            exemptFromEjectOffset.add(entered);
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    exemptFromEjectOffset.remove(entered);
                }
            });
        }
    }

    /**
     * Tells the listener that a player decided, for itself, to exit the Minecart, but that
     * it is not known yet what vehicle the player is inside of.
     * 
     * @param player
     */
    public static void markForUnmounting(Player player) {
        synchronized (markedForUnmounting) {
            if (markedForUnmounting.isEmpty()) {
                new Task(TrainCarts.plugin) {
                    @Override
                    public void run() {
                        synchronized (markedForUnmounting) {
                            int curr_ticks = CommonUtil.getServerTicks();
                            Iterator<Map.Entry<Player, Integer>> iter = markedForUnmounting.entrySet().iterator();
                            while (iter.hasNext()) {
                                Map.Entry<Player, Integer> e = iter.next();
                                if (e.getKey().isSneaking() && e.getKey().getVehicle() == null) {
                                    e.setValue(Integer.valueOf(curr_ticks));
                                } else if ((curr_ticks - e.getValue().intValue()) >= 2) {
                                    iter.remove();
                                }
                            }
                            if (markedForUnmounting.isEmpty()) {
                                stop();
                            }
                        }
                    }
                }.start(1, 1);
            }
            markedForUnmounting.put(player, CommonUtil.getServerTicks());
        }
    }

    /*
     * We must handle vehicle exit for when an unmount packet is received before
     * the player is actually seated inside a vehicle. Player exit is normally
     * handled inside the packet listener instead of here.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleExitCheck(VehicleExitEvent event) {
        // Only do this check when marked for unmounting by the packet listener
        synchronized (markedForUnmounting) {
            if (!markedForUnmounting.containsKey(event.getExited())) {
                return;
            }
        }

        MinecartMember<?> mm = MinecartMemberStore.getFromEntity(event.getVehicle());
        if (mm != null && (!mm.getProperties().getPlayersExit())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        MinecartMember<?> mm = MinecartMemberStore.getFromEntity(event.getVehicle());
        if (mm == null || exemptFromEjectOffset.contains(event.getExited())) {
            return;
        }

        final Entity e = event.getExited();
        final Location old_location = e.getLocation();
        final Location loc = mm.getPassengerEjectLocation(e);

        // Teleport to the exit position a tick later
        CommonUtil.nextTick(new Runnable() {
            public void run() {
                if (e.isDead() || e.getVehicle() != null) {
                    return;
                }

                // Do not teleport if the player changed position dramatically after exiting
                // This is the case when teleporting (/tp)
                // The default vanilla exit position is going to be at most 1 block away in all axis
                Location new_location = e.getLocation();
                if (old_location.getWorld() != new_location.getWorld()) {
                    return;
                }
                if (Math.abs(old_location.getBlockX() - new_location.getBlockX()) > 1) {
                    return;
                }
                if (Math.abs(old_location.getBlockY() - new_location.getBlockY()) > 1) {
                    return;
                }
                if (Math.abs(old_location.getBlockZ() - new_location.getBlockZ()) > 1) {
                    return;
                }

                Util.correctTeleportPosition(loc);
                e.teleport(loc);
            }
        });
        mm.resetCollisionEnter();
        mm.onPropertiesChanged();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        MinecartMember<?> mm = MinecartMemberStore.getFromEntity(event.getVehicle());
        if (mm == null) {
            return;
        }
        Entity attacker = event.getAttacker();
        if (attacker instanceof Projectile) {
            attacker = (Entity) ((Projectile) attacker).getShooter();
        }

        boolean breakAny = ((attacker instanceof Player) && Permission.BREAK_MINECART_ANY.has((Player) attacker));
        if (mm.getProperties().isInvincible() && !breakAny) {
            event.setCancelled(true);
            return;
        }
        if (attacker instanceof Player) {
            Player p = (Player) attacker;
            if (!breakAny && (!mm.getProperties().hasOwnership(p) || !Permission.BREAK_MINECART_SELF.has(p))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        if (TrainCarts.isWorldDisabled(event.getVehicle().getWorld())) {
            return;
        }
        try {
            MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getVehicle());
            if (member != null) {
                event.setCancelled(!member.onEntityCollision(event.getEntity()));
            }
        } catch (Throwable t) {
            TrainCarts.plugin.handle(t);
        }
    }

    // Note: obsoleted by onItemDrop after BKCommonLib 1.15.2-v3 or later is the minimum required dependency!
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerItemDrop(PlayerDropItemEvent event) {
        AttachmentEditor editor = MapDisplay.getHeldDisplay(event.getPlayer(), AttachmentEditor.class);
        if (editor != null && editor.acceptItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (TrainCarts.isWorldDisabled(event.getPlayer().getWorld())) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        ItemStack heldItem = HumanHand.getItemInMainHand(event.getPlayer());

        // Map control: select the clicked block
        if (TCMapControl.isTCMapItem(event.getItem())) {
            if (event.getClickedBlock() != null) {
                ItemUtil.getMetaTag(event.getItem()).putBlockLocation("selected", new BlockLocation(event.getClickedBlock()));
            }

            TCMapControl.updateMapItem(event.getPlayer(), true);

            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            return;
        }

        // Train spawning chest item
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && StoredTrainItemUtil.isItem(heldItem)) {
            if (!Permission.COMMAND_USE_STORAGE_CHEST.has(event.getPlayer())) {
                Localization.CHEST_NOPERM.message(event.getPlayer());
                return;
            }

            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);

            StoredTrainItemUtil.SpawnResult result;
            result = StoredTrainItemUtil.spawn(heldItem, event.getPlayer(), event.getClickedBlock());
            result.getLocale().message(event.getPlayer());
            if (result == StoredTrainItemUtil.SpawnResult.SUCCESS) {
                StoredTrainItemUtil.playSoundSpawn(event.getPlayer());
            }

            return;
        }

        try {
            // Obtain the clicked block
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null) {
                // Use ray tracing to obtain the correct block
                clickedBlock = CommonEntity.get(event.getPlayer()).getTargetBlock();
            }

            // Debug tools
            if (event.getItem() != null) {
                CommonTagCompound tag = ItemUtil.getMetaTag(event.getItem());
                if (tag != null) {
                    String debugType = tag.getValue("TrainCartsDebug", String.class);
                    if (debugType != null) {
                        if (Permission.DEBUG_COMMAND_DEBUG.has(event.getPlayer())) {
                            DebugTool.onDebugInteract(event.getPlayer(), clickedBlock, event.getItem(), debugType);
                        } else {
                            event.getPlayer().sendMessage(ChatColor.RED + "No permission to use this item!");
                        }
                    }
                }
            }

            // No interaction occurred
            if (clickedBlock == null) {
                return;
            }

            //System.out.println("Interacted with block [" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ() + "]");

            Material m = (event.getItem() == null) ? Material.AIR : event.getItem().getType();

            // Track map debugging logic
            if (DEBUG_DO_TRACKTEST && m == Material.FEATHER) {
                TrackMap map = new TrackMap(clickedBlock, FaceUtil.yawToFace(event.getPlayer().getLocation().getYaw() - 90, false));
                while (map.hasNext()) {
                    map.next();
                }
                Material wool_type = getMaterial("LEGACY_WOOL");
                byte data = 0;
                for (Block block : map) {
                    BlockUtil.setTypeAndRawData(block, wool_type, data, false);
                    data++;
                    if (data == 16) {
                        data = 0;
                    }
                }
                return;
            }

            // Temporarily makes all connected tracks invisible
            if (DEBUG_DO_INVISIBLE_TRACK && m == Material.BREAD) {
                TrackMap map = new TrackMap(clickedBlock, FaceUtil.yawToFace(event.getPlayer().getLocation().getYaw() - 90, false));
                while (map.hasNext()) {
                    map.next();
                }
                for (Block block : map) {
                    System.out.println("INVISIBLE: " + block);
                    CommonPacket packet = PacketType.OUT_BLOCK_CHANGE.newInstance();
                    packet.write(PacketType.OUT_BLOCK_CHANGE.position, new IntVector3(block));
                    packet.write(PacketType.OUT_BLOCK_CHANGE.blockData, BlockData.fromMaterial(Material.AIR));
                    PacketUtil.sendPacket(event.getPlayer(), packet);
                }
                return;
            }

            // Keep track of when a player interacts to detect spamming
            long lastHitTime = lastHitTimes.getOrDefault(event.getPlayer(), Long.MIN_VALUE);
            long time = System.currentTimeMillis();
            long clickInterval = time - lastHitTime;
            lastHitTimes.put(event.getPlayer(), time);

            // Handle upside-down rail placement
            handleRailPlacement(event, heldItem);

            // Execute the click
            if (!event.isCancelled() && !onRightClick(clickedBlock, event.getPlayer(), heldItem, clickInterval)) {
                event.setUseItemInHand(Result.DENY);
                event.setUseInteractedBlock(Result.DENY);
                event.setCancelled(true);
            }
        } catch (Throwable t) {
            TrainCarts.plugin.handle(t);
        }
    }

    /**
     * Handles placement of special types of rails that can not be built normally.
     * TODO: make this less hacked in
     * 
     * @param event
     * @param heldItem
     */
    private void handleRailPlacement(PlayerInteractEvent event, ItemStack heldItem) {
        if (event.getClickedBlock() == null || heldItem == null) {
            return;
        }

        // When clicked block is interactable, do not place
        if (MaterialUtil.ISINTERACTABLE.get(event.getClickedBlock()) && !event.getPlayer().isSneaking()) {
            return;
        }

        Block placedBlock = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!MaterialUtil.ISAIR.get(placedBlock)) {
            return;
        }

        Material railType = heldItem.getType();

        // Upside-down rails
        if (MaterialUtil.ISRAILS.get(railType) && TCConfig.allowUpsideDownRails) {

            // If the block below is air or rail, and above is a solid
            Block below = placedBlock.getRelative(BlockFace.DOWN);
            Block above = placedBlock.getRelative(BlockFace.UP);
            if ((MaterialUtil.ISAIR.get(below) || Util.ISVERTRAIL.get(below)) && Util.isUpsideDownRailSupport(above)) {

                // Custom placement of an upside-down normal rail
                BlockPlaceEvent placeEvent = new BlockPlaceEvent(placedBlock, placedBlock.getState(),
                        event.getClickedBlock(), heldItem.clone(), event.getPlayer(), true);

                // Build a standard south-facing straight track
                // The onBlockPlace event trigger will shape up this track the next tick
                BlockData railData = BlockData.fromMaterial(railType);
                WorldUtil.setBlockDataFast(placedBlock, railData);
                WorldUtil.queueBlockSend(placedBlock.getWorld(), placedBlock.getX(), placedBlock.getY(), placedBlock.getZ());

                CommonUtil.callEvent(placeEvent);
                if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
                    WorldUtil.setBlockDataFast(placedBlock, BlockData.AIR);
                } else {
                    BlockUtil.applyPhysics(placedBlock, railType);

                    // If not survival, subtract one item from player's inventory
                    //TODO: Isn't this the 'instant build' property or something?
                    if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                        ItemStack oldItem = HumanHand.getItemInMainHand(event.getPlayer());
                        if (oldItem == null || oldItem.getAmount() <= 1) {
                            oldItem = null;
                        } else {
                            oldItem = oldItem.clone();
                            oldItem.setAmount(oldItem.getAmount() - 1);
                        }
                        HumanHand.setItemInMainHand(event.getPlayer(), oldItem);
                    }

                    // Play sound of the material 'placed'
                    WorldUtil.playSound(event.getClickedBlock().getLocation(), railData.getPlaceSound(), 1.0f, 1.0f);

                    // Cancel the original interaction event
                    event.setUseItemInHand(Result.DENY);
                    event.setUseInteractedBlock(Result.DENY);
                    event.setCancelled(true);
                }
            }
            
        }
        
    }

    /**
     * Executes right-click Block logic
     *
     * @param clickedBlock right-clicked block
     * @param player player that interacted
     * @param heldItem item the player held
     * @param clickInterval in MS since the last right-click
     * @return True to allow default logic to continue, False to suppress it
     */
    public boolean onRightClick(Block clickedBlock, Player player, ItemStack heldItem, long clickInterval) {
        // Handle interaction with minecart or rails onto another Block
        if (MaterialUtil.ISMINECART.get(heldItem) || Util.ISTCRAIL.get(heldItem)) {
            BlockData type = clickedBlock == null ? BlockData.AIR : WorldUtil.getBlockData(clickedBlock);
            RailType railType = RailType.getType(clickedBlock);
            if (railType != RailType.NONE) {
                if (MaterialUtil.ISMINECART.get(heldItem)) {
                    // Handle the interaction with rails while holding a minecart
                    // Place a TrainCart/Minecart on top of the rails, and handles permissions
                    return handleMinecartPlacement(player, clickedBlock);
                } else if (type.isType(heldItem.getType()) && MaterialUtil.ISRAILS.get(type) && TCConfig.allowRailEditing && clickInterval >= MAX_INTERACT_INTERVAL) {
                    if (BlockUtil.canBuildBlock(clickedBlock, type)) {
                        // Edit the rails to make a connection/face the direction the player clicked
                        BlockFace direction = FaceUtil.getDirection(player.getLocation().getDirection(), false);
                        BlockFace lastDirection = lastClickedDirection.getOrDefault(player, direction);
                        Rails rails = BlockUtil.getRails(clickedBlock);
                        // First check whether we are clicking towards an up-slope block
                        if (BlockUtil.isSolid(clickedBlock.getRelative(direction))) {
                            // Sloped logic
                            if (rails.isOnSlope()) {
                                if (rails.getDirection() == direction) {
                                    // Switch between sloped and flat
                                    rails.setDirection(direction, false);
                                } else {
                                    // Other direction slope
                                    rails.setDirection(direction, true);
                                }
                            } else {
                                // Set to slope
                                rails.setDirection(direction, true);
                            }
                        } else if (RailType.REGULAR.isRail(type)) {
                            // This needs advanced logic for curves and everything!
                            BlockFace[] faces = FaceUtil.getFaces(rails.getDirection());
                            if (!LogicUtil.contains(direction.getOppositeFace(), faces)) {
                                // Try to make a connection towards this point
                                // Which of the two faces do we sacrifice?
                                BlockFace otherFace = faces[0] == lastDirection.getOppositeFace() ? faces[0] : faces[1];
                                rails.setDirection(FaceUtil.combine(otherFace, direction.getOppositeFace()), false);
                            }
                        } else {
                            // Simple switching (straight tracks)
                            rails.setDirection(direction, false);
                        }
                        // Update
                        BlockUtil.setData(clickedBlock, rails);
                        lastClickedDirection.put(player, direction);
                    }
                }
            }
        }

        // Handle right-click interaction with signs
        return !(MaterialUtil.ISSIGN.get(clickedBlock) && clickInterval >= SIGN_CLICK_INTERVAL && SignAction.handleClick(clickedBlock, player));
    }

    /**
     * @param player       that placed the Minecart
     * @param clickedBlock to spawn a Minecart on
     * @param railType     that was clicked
     * @return True to allow default logic to continue, False to suppress it
     */
    private boolean handleMinecartPlacement(Player player, Block clickedBlock) {
        // handle permission
        if (!Permission.GENERAL_PLACE_MINECART.has(player)) {
            return false;
        }

        // Check if clicking a rail
        RailType clickedRailType = RailType.getType(clickedBlock);
        if (clickedRailType == RailType.NONE){
            return true; // nothing happening; not a rail
        }

        // IS the placement of a TrainCart allowed?
        if (!TCConfig.allMinecartsAreTrainCarts && !Permission.GENERAL_PLACE_TRAINCART.has(player)) {
            return true;
        }

        BlockFace orientation = FaceUtil.yawToFace(player.getEyeLocation().getYaw() - 90.0f, false);
        Location at = clickedRailType.getSpawnLocation(clickedBlock, orientation);

        // No minecart blocking it?
        if (MinecartMemberStore.getAt(at, null, 0.5) != null) {
            return false;
        }

        //TODO: Check if this rail type is a 'normal' type to see if we need to handle spawning minecarts ourselves
        //if (clickedRailType ==

        //if (clickedRailType instanceof RailTypeRegular) {
        ////    return true;
        //}

        MinecartMemberStore.spawnBy(at, player);
        return false;

        /*
        
        // Place logic for special rail types
        if (MaterialUtil.ISPRESSUREPLATE.get(railType)) {
        } else if (Util.ISVERTRAIL.get(railType)) {
        } else {
            // Set ownership and convert during the upcoming minecart spawning (entity add) event
            lastPlayer = player;
        }
        return true;
        */
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Minecart)) {
            return;
        }
        
        // Check that we are not spam-clicking (for block placement, that is!)
        Long lastHitTime = lastHitTimes.get(event.getPlayer());
        if (lastHitTime != null) {
            long time = System.currentTimeMillis();
            long clickInterval = time - lastHitTime.longValue();
            if (clickInterval < MAX_INTERACT_INTERVAL) {
                event.setCancelled(true);
                return;
            }
        }

        // Handle clicking groups while holding a train storage chest
        ItemStack heldItem = HumanHand.getItemInMainHand(event.getPlayer());
        if (StoredTrainItemUtil.isItem(heldItem)) {
            event.setCancelled(true);
            if (!Permission.COMMAND_USE_STORAGE_CHEST.has(event.getPlayer())) {
                Localization.CHEST_NOPERM.message(event.getPlayer());
                return;
            }
            if (StoredTrainItemUtil.isLocked(heldItem)) {
                Localization.CHEST_LOCKED.message(event.getPlayer());
                return;
            }

            MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getRightClicked());
            if (member == null || member.isUnloaded() || member.getGroup() == null) {
                return;
            }

            heldItem = heldItem.clone();
            StoredTrainItemUtil.store(heldItem, member.getGroup());
            HumanHand.setItemInMainHand(event.getPlayer(), heldItem);
            Localization.CHEST_PICKUP.message(event.getPlayer());
            StoredTrainItemUtil.playSoundStore(event.getPlayer());

            if (!event.getPlayer().isSneaking()) {
                member.getGroup().destroy();
            }

            return;
        }

        // Handle the vehicle change
        if (event.getRightClicked() instanceof RideableMinecart) {
            event.setCancelled(!TrainCarts.handlePlayerVehicleChange(event.getPlayer(), event.getRightClicked()));

            // Store right now what seats a player is eligible for based on where the player clicked
            // If the Player indeed does enter the Minecart, then we know what seat to pick
            MinecartMember<?> newMinecart = MinecartMemberStore.getFromEntity(event.getRightClicked());
            if (!event.isCancelled() && newMinecart != null) {
                MinecartMemberNetwork network = CommonUtil.tryCast(newMinecart.getEntity().getNetworkController(), MinecartMemberNetwork.class);
                if (network != null) {
                    network.storeSeatHint(event.getPlayer());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (MaterialUtil.ISSIGN.get(event.getBlock())) {
            SignAction.handleDestroy(new SignActionEvent(event.getBlock()));
        } else if (MaterialUtil.ISRAILS.get(event.getBlock())) {
            onRailsBreak(event.getBlock());
        }
    }

    /*
     * Fires the onBlockPlaced handler for Rail Types
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        RailType railType = RailType.getType(event.getBlockPlaced());
        if (railType != RailType.NONE) {
            final Block placed = event.getBlockPlaced();
            CommonUtil.nextTick(new Runnable() {
                public void run() {
                    RailType railType = RailType.getType(placed);
                    if (railType != RailType.NONE) {
                        railType.onBlockPlaced(placed);
                        BlockUtil.applyPhysics(placed, placed.getType());
                    }
                }
            });
        }
    }

    /*
     * Fires the onBlockPhysics handler for Rail Types
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        MinecartGroupStore.notifyPhysicsChange();
        RailType railType = RailType.getType(event.getBlock());
        if (railType != RailType.NONE) {
            // First check that the rails are supported as they are
            // If not, it will be destroyed either by onBlockPhysics or Vanilla physics
            if (!railType.isRailsSupported(event.getBlock())) {
                onRailsBreak(event.getBlock());
            }

            // Let the rail type handle any custom physics
            railType.onBlockPhysics(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysicsMonitor(BlockPhysicsEvent event) {
        // Handle signs being broken because their supporting block got destroyed
        Block block = event.getBlock();
        if (MaterialUtil.ISSIGN.get(block)) {
            if (!Util.isSignSupported(event.getBlock())) {
                // Sign is no longer supported - clear all sign actions
                SignAction.handleDestroy(new SignActionEvent(event.getBlock()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        if (event.isCancelled() || TrainCarts.isWorldDisabled(event)) {
            return;
        }

        // Reset cache to make sure all signs are recomputed later, after the sign was made
        // Doing it here, in the most generic case, so that custom addon signs are also refreshed
        RailSignCache.reset();

        SignAction.handleBuild(event);
        if (event.isCancelled()) {
            // Properly give the sign back to the player that placed it
            // If this is impossible for whatever reason, just drop it
            if (!Util.canInstantlyBuild(event.getPlayer())) {
                ItemStack item = HumanHand.getItemInMainHand(event.getPlayer());
                if (LogicUtil.nullOrEmpty(item)) {
                    HumanHand.setItemInMainHand(event.getPlayer(), new ItemStack(Material.SIGN, 1));
                } else if (MaterialUtil.isType(item, Material.SIGN) && item.getAmount() < ItemUtil.getMaxSize(item)) {
                    ItemUtil.addAmount(item, 1);
                    HumanHand.setItemInMainHand(event.getPlayer(), item);
                } else {
                    // Drop the item
                    Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
                    loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.SIGN, 1));
                }
            }

            // Break the block
            event.getBlock().setType(Material.AIR);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getEntity().getVehicle());
        if (member != null && !member.canTakeDamage(event.getEntity(), event.getCause())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getEntity());
        if (member == null) {
            return;
        }

        // TrainCarts minecart teleportation does not work. Cancel it at all times.
        event.setCancelled(true);
        if (!TCConfig.allowNetherTeleport) {
            return;
        }

        // Find out the actual location we are teleporting to
        Location loc = event.getTo();
        if (event.getPortalTravelAgent() != null && loc != null) {
            loc = event.getPortalTravelAgent().findPortal(loc);
        }
        if (loc == null) {
            return;
        }

        // Deduce a region of blocks to look for rails to spawn into
        Direction direction = Direction.fromFace(member.getDirectionFrom());
        final PortalDestination dest = PortalDestination.findDestinationAtNetherPortal(loc.getBlock(), direction);
        if (dest != null && dest.getRailsBlock() != null && dest.hasDirections()) {
            final MinecartGroup group = member.getGroup();
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    group.teleport(dest.getRailsBlock(), dest.getDirections()[0]);
                }
            });
        }
    }

    /**
     * Called when a rails block is being broken
     *
     * @param railsBlock that is broken
     */
    public void onRailsBreak(Block railsBlock) {
        MinecartMember<?> mm = MinecartMemberStore.getAt(railsBlock);
        if (mm != null) {
            mm.getGroup().getSignTracker().updatePosition();
        }
        // Remove path node from path finding
        PathNode.remove(railsBlock);
    }
}
