package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.collections.EntityMap;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.events.ChunkLoadEntitiesEvent;
import com.bergerkiller.bukkit.common.events.EntityAddEvent;
import com.bergerkiller.bukkit.common.events.EntityRemoveFromServerEvent;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.attachments.FakePlayerSpawner;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.light.LightAPIController;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.debug.DebugTool;
import com.bergerkiller.bukkit.tc.editor.TCMapControl;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.portals.PortalDestination;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.storage.OfflineGroup;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
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
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Rails;

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
    private final TrainCarts plugin;
    private EntityMap<Player, Long> lastHitTimes = new EntityMap<>();
    private EntityMap<Player, BlockFace> lastClickedDirection = new EntityMap<>();

    public TCListener(TrainCarts plugin) {
        this.plugin = plugin;
    }

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
        FakePlayerSpawner.onViewerQuit(event.getPlayer());
        plugin.getGlowColorTeamProvider().reset(event.getPlayer());

        // Disable any active packet queues
        plugin.getPacketQueueMap().remove(event.getPlayer());
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
            plugin.log(Level.SEVERE, "Chunk " + chunk.getX() + "/" + chunk.getZ() +
                    " of group " + group.getProperties().getTrainName() + " unloaded unexpectedly!");
        } else {
            plugin.log(Level.SEVERE, "Chunk " + chunk.getX() + "/" + chunk.getZ() +
                    " of group " + group.getProperties().getTrainName() + " unloaded because chunk area wasn't up to date!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoadEntities(ChunkLoadEntitiesEvent event) {
        OfflineGroupManager.loadChunk(plugin, event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        // Refresh the groups on this world
        OfflineGroupManager.refresh(plugin, event.getWorld());

        // Start loading the chunks kept loaded by trains with property keep chunks loaded
        Map<OfflineGroup, List<ForcedChunk>> chunks = OfflineGroupManager.getForceLoadedChunks(event.getWorld());
        if (!chunks.isEmpty()) {
            plugin.log(Level.INFO, "Restoring trains and loading nearby chunks on world " + event.getWorld().getName() + "...");
            plugin.preloadChunks(chunks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        OfflineGroupManager.unloadWorld(event.getWorld());
        if (Bukkit.getPluginManager().isPluginEnabled("LightAPI")) {
            disableLightAPIWorld(event.getWorld());
        }

        // Memory cleanup
        TCConfig.enabledWorlds.onWorldUnloaded(event.getWorld());
        TCConfig.disabledWorlds.onWorldUnloaded(event.getWorld());
    }

    // Put in its own method to further avoid loading the class when LightAPI is not enabled
    private static void disableLightAPIWorld(World world) {
        LightAPIController.disableWorld(world);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        plugin.getGlowColorTeamProvider().reset(event.getPlayer());
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
            MinecartMemberStore.convert(plugin, (Minecart) event.getEntity());
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

            if (EntityHandle.fromBukkit(event.getEntity()).isDestroyed()) {
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
                    plugin.log(Level.WARNING, "Train '" + group.getProperties().getTrainName() + "' forcibly unloaded!");
                } else {
                    plugin.log(Level.WARNING, "Train '" + group.getProperties().getTrainName() + "' had to be restored after unexpected unload");
                }
                group.unload();
                // For the next tick: update the storage system to restore trains here and there
                CommonUtil.nextTick(new Runnable() {
                    public void run() {
                        OfflineGroupManager.refresh(plugin);
                    }
                });
            }
        }
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
            plugin.handle(t);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (TrainCarts.isWorldDisabled(event.getPlayer().getWorld())) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (DebugTool.onDebugInteract(plugin, event.getPlayer(), event.getClickedBlock(), event.getItem(), false)) {
                event.setUseInteractedBlock(Result.DENY);
                return;
            }
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

        try {
            // Obtain the clicked block
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null) {
                // Use ray tracing to obtain the correct block
                clickedBlock = CommonEntity.get(event.getPlayer()).getTargetBlock();
            }

            // Debug tools
            if (DebugTool.onDebugInteract(plugin, event.getPlayer(), clickedBlock, event.getItem(), true)) {
                event.setUseInteractedBlock(Result.DENY);
                return;
            }

            // No interaction occurred
            if (clickedBlock == null) {
                return;
            }

            //plugin.log(Level.INFO, "Interacted with block [" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ() + "]");

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
                    plugin.log(Level.INFO, "INVISIBLE: " + block);
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
            plugin.handle(t);
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
                WorldUtil.queueBlockSend(placedBlock);

                CommonUtil.callEvent(placeEvent);
                if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
                    WorldUtil.setBlockDataFast(placedBlock, BlockData.AIR);
                } else {
                    // Note: this is completely broken since MC 1.19. Can't do this anymore without breaking the rails :(
                    plugin.applyBlockPhysics(placedBlock, railData);

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
        if (clickedBlock != null && (MaterialUtil.ISMINECART.get(heldItem) || Util.ISTCRAIL.get(heldItem))) {
            BlockData type = WorldUtil.getBlockData(clickedBlock);
            RailType railType = RailType.getType(clickedBlock, type);
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
                        TrainCarts.plugin.setBlockDataWithoutBreaking(clickedBlock, BlockData.fromMaterialData(rails));
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

        MinecartMemberStore.spawnBy(plugin, at, player);
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

        // Handle the vehicle change
        if (event.getRightClicked() instanceof RideableMinecart) {
            event.setCancelled(!plugin.handlePlayerVehicleChange(event.getPlayer(), event.getRightClicked()));

            // Store right now what seats a player is eligible for based on where the player clicked
            // If the Player indeed does enter the Minecart, then we know what seat to pick
            MinecartMember<?> newMinecart = MinecartMemberStore.getFromEntity(event.getRightClicked());
            if (!event.isCancelled() && newMinecart != null) {
                newMinecart.getAttachments().storeSeatHint(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (MaterialUtil.ISSIGN.get(event.getBlock())) {
            SignAction.handleDestroy(new SignActionEvent(event.getBlock()));
            // Also remove from the loaded sign controller
            plugin.getSignController().notifySignRemoved(event.getBlock());
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
                    BlockData blockData = WorldUtil.getBlockData(placed);
                    RailType railType = RailType.getType(placed, blockData);
                    if (railType != RailType.NONE) {
                        railType.onBlockPlaced(placed);
                        plugin.applyBlockPhysics(placed, blockData);
                    }
                }
            });
        }
    }

    /*
     * Handles all the block physics changes - plugin wide
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        MinecartGroupStore.notifyPhysicsChange();
        Block block = event.getBlock();
        BlockData blockData = Util.getBlockDataOfPhysicsEvent(event);

        // Check if a rail block is broken
        {
            for (RailType type : RailType.values()) {
                if (type.isHandlingPhysics() && RailType.checkRailTypeIsAt(type, block, blockData)) {
                    // First check that the rails are supported as they are
                    // If not, it will be destroyed either by onBlockPhysics or Vanilla physics
                    if (!type.isRailsSupported(block)) {
                        onRailsBreak(block);
                    }

                    // Let the rail type handle any custom physics
                    type.onBlockPhysics(event);

                    // Force verification of this Rails Block in case it changes
                    // This is especially important for powered/activator rails, which change
                    // type and behavior due to physics.
                    RailLookup.CachedRailPiece cachedRailPiece = RailLookup.lookupCachedRailPieceIfCached(OfflineBlock.of(block), type);
                    if (!cachedRailPiece.isNone()) {
                        cachedRailPiece.forceCacheVerification();
                    }
                }
            }
        }

        // Handle signs being broken because their supporting block got destroyed
        if (MaterialUtil.ISSIGN.get(blockData) && !Util.isSignSupported(block, blockData)) {
            // Sign is no longer supported - clear all sign actions
            SignAction.handleDestroy(new SignActionEvent(event.getBlock()));
            // Also remove from the loaded sign controller
            plugin.getSignController().notifySignRemoved(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlaceSignCheck(BlockPlaceEvent event) {
        Sign sign;
        if (
            !event.canBuild() ||
            TrainCarts.isWorldDisabled(event) ||
            !MaterialUtil.ISSIGN.get(event.getBlockPlaced()) ||
            (sign = BlockUtil.getSign(event.getBlockPlaced())) == null
        ) {
            return;
        }

        // Mock a sign change event to handle building it
        SignChangeEvent change_event = new SignChangeEvent(
                event.getBlockPlaced(),
                event.getPlayer(),
                sign.getLines());
        handleSignChange(change_event);

        // If cancelled, cancel block placement too
        if (change_event.isCancelled()) {
            event.setBuild(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!TrainCarts.isWorldDisabled(event)) {
            handleSignChange(event);
        }
    }

    private void handleSignChange(SignChangeEvent event) {
        // Reset cache to make sure all signs are recomputed later, after the sign was made
        // Doing it here, in the most generic case, so that custom addon signs are also refreshed
        // TODO: Maybe only recalculate the rails impacted, or nearby the sign? This could be costly.
        RailLookup.forceRecalculation();

        SignAction.handleBuild(event);
        if (event.isCancelled()) {
            // Properly give the sign back to the player that placed it
            // We do not want to place down an empty sign, that is annoying
            // If this is impossible for whatever reason, just drop it
            Material signBlockType = event.getBlock().getType();
            if (!Util.canInstantlyBuild(event.getPlayer()) && MaterialUtil.ISSIGN.get(signBlockType)) {
                // Find the type of item matching the sign type
                Material signItemType;
                if (signBlockType == MaterialUtil.getMaterial("LEGACY_SIGN_POST")
                        || signBlockType == MaterialUtil.getMaterial("LEGACY_WALL_SIGN")
                ) {
                    // Legacy (pre-1.13 support)
                    signItemType = MaterialUtil.getFirst("OAK_SIGN", "LEGACY_SIGN");
                } else if (signBlockType.name().contains("_WALL_")) {
                    // BIRCH_WALL_SIGN -> BIRCH_SIGN
                    signItemType = MaterialUtil.getMaterial(signBlockType.name().replace("_WALL_", "_"));
                    if (signItemType == null) {
                        // Fallback to at least return 'a' sign
                        signItemType = MaterialUtil.getFirst("OAK_SIGN", "LEGACY_SIGN");
                    }
                } else {
                    // Same as the sign block type
                    signItemType = signBlockType;
                }

                ItemStack item = HumanHand.getItemInMainHand(event.getPlayer());
                if (LogicUtil.nullOrEmpty(item)) {
                    HumanHand.setItemInMainHand(event.getPlayer(), new ItemStack(signItemType, 1));
                } else if (MaterialUtil.isType(item, signItemType) && item.getAmount() < ItemUtil.getMaxSize(item)) {
                    ItemUtil.addAmount(item, 1);
                    HumanHand.setItemInMainHand(event.getPlayer(), item);
                } else {
                    // Drop the item
                    Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
                    loc.getWorld().dropItemNaturally(loc, new ItemStack(signItemType, 1));
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCreativeSetSlot(InventoryCreativeEvent event) {
        // While inside a seat that uses an FPV mode which makes the player invisible,
        // deny changing the equipment slots. This prevents player equipment wiping
        // when the player opens the inventory.
        if (event.getSlotType() == SlotType.ARMOR) {
            CartAttachmentSeat seat = plugin.getSeatAttachmentMap().get(event.getWhoClicked().getEntityId());
            if (seat != null && seat.firstPerson.getLiveMode().isRealPlayerInvisible()) {
                event.setResult(Result.DENY);
            }
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
        /*
        if (event.getPortalTravelAgent() != null && loc != null) {
            loc = event.getPortalTravelAgent().findPortal(loc);
        }
        */
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
