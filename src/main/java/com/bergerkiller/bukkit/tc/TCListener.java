package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.EntityMap;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.events.EntityAddEvent;
import com.bergerkiller.bukkit.common.events.EntityRemoveFromServerEvent;
import com.bergerkiller.bukkit.common.events.map.MapShowEvent;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.editor.TCMapControl;
import com.bergerkiller.bukkit.tc.editor.TCMapEditor;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.portals.PortalDestination;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeRegular;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import com.bergerkiller.mountiplex.reflection.SafeMethod;
import com.bergerkiller.reflection.net.minecraft.server.NMSEntity;
import com.bergerkiller.reflection.net.minecraft.server.NMSEntityMinecart;
import com.bergerkiller.reflection.net.minecraft.server.NMSVector;

import org.bukkit.Location;
import org.bukkit.Material;
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
import java.util.List;
import java.util.logging.Level;

public class TCListener implements Listener {
    private static final boolean DEBUG_DO_TRACKTEST = false;
    private static final boolean DEBUG_DO_INVISIBLE_TRACK = false;
    private static final long SIGN_CLICK_INTERVAL = 500; // Interval in MS where left-click interaction is allowed
    private static final long MAX_INTERACT_INTERVAL = 300; // Interval in MS where spam-interaction is allowed
    public static Player lastPlayer = null;
    public static boolean cancelNextDrops = false;
    public static List<Entity> exemptFromEjectOffset = new ArrayList<Entity>();
    private final ArrayList<MinecartGroup> expectUnload = new ArrayList<>();
    private EntityMap<Player, Long> lastHitTimes = new EntityMap<>();
    private EntityMap<Player, BlockFace> lastClickedDirection = new EntityMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMapViewed(MapShowEvent event) {
        if ( event.isHeldEvent() && 
             TCMapControl.isTCMapItem(event.getMapItem()) &&
             !(event.getDisplay() instanceof TCMapEditor) ) {

            event.setDisplay(TrainCarts.plugin, new TCMapEditor());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        MinecartMember<?> vehicle = MinecartMemberStore.getFromEntity(event.getPlayer().getVehicle());
        if (vehicle != null && !vehicle.isPlayerTakable()) {
            vehicle.ignoreNextDie();
            // Eject the player before proceeding to the saving
            vehicle.eject();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (cancelNextDrops) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChunkUnloadLow(ChunkUnloadEvent event) {
        synchronized (this.expectUnload) {
            this.expectUnload.clear();
            for (MinecartGroup mg : MinecartGroup.getGroupsUnsafe()) {
                if (mg.isInChunk(event.getChunk())) {
                    if (mg.canUnload()) {
                        this.expectUnload.add(mg);
                    } else {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            // Double-check
            for (Entity entity : WorldUtil.getEntities(event.getChunk())) {
                if (entity instanceof Minecart) {
                    MinecartMember<?> member = MinecartMemberStore.getFromEntity(entity);
                    if (member == null || !member.isInteractable()) {
                        continue;
                    }
                    if (member.getGroup().canUnload()) {
                        if (!this.expectUnload.contains(member.getGroup())) {
                            this.expectUnload.add(member.getGroup());
                        }
                    } else {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // This chunk is still referenced, ensure that it is really gone
        OfflineGroupManager.lastUnloadChunk = MathUtil.longHashToLong(event.getChunk().getX(), event.getChunk().getZ());
        // Unload groups
        synchronized (this.expectUnload) {
            for (MinecartGroup mg : this.expectUnload) {
                if (mg.isInChunk(event.getChunk())) {
                    mg.unload();
                }
            }
        }
        OfflineGroupManager.unloadChunk(event.getChunk());
        OfflineGroupManager.lastUnloadChunk = null;
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
        for (MinecartGroup group : MinecartGroup.getGroups()) {
            if (group.getWorld() == event.getWorld()) {
                group.unload();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityAdd(EntityAddEvent event) {
        if (!MinecartMemberStore.canConvert(event.getEntity())) {
            return;
        }

        // If placed by a player, only allow conversion for players that have the permissions
        if (!OfflineGroupManager.containsMinecart(event.getEntity().getUniqueId())
                && !TrainCarts.allMinecartsAreTrainCarts && lastPlayer == null) {
            // No conversion allowed
            return;
        }

        MinecartMember<?> member = MinecartMemberStore.convert((Minecart) event.getEntity());
        if (member != null && !member.isUnloaded() && lastPlayer != null) {
            // A player just placed a minecart - set defaults and ownership
            member.getGroup().getProperties().setDefault(lastPlayer);
            if (TrainCarts.setOwnerOnPlacement) {
                member.getProperties().setOwner(lastPlayer);
            }
            CartPropertiesStore.setEditing(lastPlayer, member.getProperties());
            lastPlayer = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromServer(EntityRemoveFromServerEvent event) {
        if (event.getEntity() instanceof Minecart) {
            if (event.getEntity().isDead()) {
                OfflineGroupManager.removeMember(event.getEntity().getUniqueId());
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
            if (prop.getPlayersEnter() && (prop.isPublic() || prop.hasOwnership(player))) {
                prop.showEnterMessage(player);
            } else {
                event.setCancelled(true);
            }
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

    /* Note: is extra, we already do these checks elsewhere. Should not be needed but just in case. */
    /*
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleExitCheck(VehicleExitEvent event) {
        MinecartMember<?> mm = MinecartMemberStore.get(event.getVehicle());
        if (mm != null && (!mm.getProperties().getPlayersExit())) {
            event.setCancelled(true);
        }
    }
    */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        MinecartMember<?> mm = MinecartMemberStore.getFromEntity(event.getVehicle());
        if (mm == null || exemptFromEjectOffset.contains(event.getExited())) {
            return;
        }

        Location mloc = mm.getEntity().getLocation();
        mloc.setYaw(FaceUtil.faceToYaw(mm.getDirection()));
        mloc.setPitch(0.0f);
        final Location loc = MathUtil.move(mloc, mm.getProperties().exitOffset);
        final Entity e = event.getExited();
        //teleport
        CommonUtil.nextTick(new Runnable() {
            public void run() {
                if (e.isDead() || e.getVehicle() != null) {
                    return;
                }
                loc.setYaw(e.getLocation().getYaw());
                loc.setPitch(e.getLocation().getPitch());
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
            if (breakAny || (mm.getProperties().hasOwnership(p) && Permission.BREAK_MINECART_SELF.has(p))) {
                CartPropertiesStore.setEditing(p, mm.getProperties());
            } else {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (TrainCarts.isWorldDisabled(event.getPlayer().getWorld())) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

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
                if (clickedBlock == null) {
                    // No interaction occurred
                    return;
                }
            }

            //System.out.println("Interacted with block [" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ() + "]");

            Material m = (event.getItem() == null) ? Material.AIR : event.getItem().getType();

            // Tests the original MC slope calculations
            if (false && m == Material.STICK) {
                Object mc = NMSEntityMinecart.Rideable.T.newInstanceNull();
                NMSEntity.world.set(mc, clickedBlock.getWorld());

                SafeMethod<Object> v = NMSEntityMinecart.Rideable.T.getMethod("j", double.class, double.class, double.class);

                double x = clickedBlock.getX() + 0.5;
                double y = clickedBlock.getY() + 0.5;
                double z = clickedBlock.getZ() + 0.5;

                System.out.println("POS=" + x + ", " + y + ", "+  z);

                for (double dx = -0.6; dx <= 0.6; dx += 0.05) {
                    Object vec = v.invoke(mc, x + dx, y, z);
                    System.out.println(dx + "," + (NMSVector.getVecY(vec)-y));
                }
            }

            // Track map debugging logic
            if (DEBUG_DO_TRACKTEST && m == Material.FEATHER) {
                TrackMap map = new TrackMap(clickedBlock, FaceUtil.yawToFace(event.getPlayer().getLocation().getYaw() - 90, false));
                while (map.hasNext()) {
                    map.next();
                }
                byte data = 0;
                for (Block block : map) {
                    BlockUtil.setTypeAndRawData(block, Material.WOOL, data, false);
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
            long lastHitTime = LogicUtil.fixNull(lastHitTimes.get(event.getPlayer()), Long.MIN_VALUE);
            long time = System.currentTimeMillis();
            long clickInterval = time - lastHitTime;
            lastHitTimes.put(event.getPlayer(), time);

            // Execute the click
            ItemStack item = event.getPlayer().getItemInHand();
            if (!onRightClick(clickedBlock, event.getPlayer(), item, clickInterval)) {
                event.setUseItemInHand(Result.DENY);
                event.setUseInteractedBlock(Result.DENY);
                event.setCancelled(true);
            }
        } catch (Throwable t) {
            TrainCarts.plugin.handle(t);
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
            Material type = clickedBlock == null ? Material.AIR : clickedBlock.getType();
            RailType railType = RailType.getType(clickedBlock);
            if (railType != RailType.NONE) {
                if (MaterialUtil.ISMINECART.get(heldItem)) {
                    // Handle the interaction with rails while holding a minecart
                    // Place a TrainCart/Minecart on top of the rails, and handles permissions
                    return handleMinecartPlacement(player, clickedBlock, type);
                } else if (type == heldItem.getType() && MaterialUtil.ISRAILS.get(type) && TrainCarts.allowRailEditing && clickInterval >= MAX_INTERACT_INTERVAL) {
                    if (BlockUtil.canBuildBlock(clickedBlock, type)) {
                        // Edit the rails to make a connection/face the direction the player clicked
                        BlockFace direction = FaceUtil.getDirection(player.getLocation().getDirection(), false);
                        BlockFace lastDirection = LogicUtil.fixNull(lastClickedDirection.get(player), direction);
                        Rails rails = BlockUtil.getRails(clickedBlock);
                        // First check whether we are clicking towards an up-slope block
                        if (MaterialUtil.ISSOLID.get(clickedBlock.getRelative(direction))) {
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
                        } else if (type == Material.RAILS) {
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
    private boolean handleMinecartPlacement(Player player, Block clickedBlock, Material railType) {
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
        if (!TrainCarts.allMinecartsAreTrainCarts && !Permission.GENERAL_PLACE_TRAINCART.has(player)) {
            return true;
        }

        BlockFace orientation = FaceUtil.yawToFace(player.getLocation().getYaw() - 90, false);
        Location at = clickedRailType.getSpawnLocation(clickedBlock, orientation);

        // No minecart blocking it?
        if (MinecartMemberStore.getAt(at, null, 0.5) != null) {
            return false;
        }

        // Set ownership and convert during the upcoming minecart spawning (entity add) event
        lastPlayer = player;

        //TODO: Check if this rail type is a 'normal' type to see if we need to handle spawning minecarts ourselves
        //if (clickedRailType ==

        if (clickedRailType instanceof RailTypeRegular) {
            return true;
        }

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

        // Handle the vehicle change
        if (event.getRightClicked() instanceof RideableMinecart) {
            event.setCancelled(!TrainCarts.handlePlayerVehicleChange(event.getPlayer(), event.getRightClicked()));
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (MaterialUtil.ISRAILS.get(event.getBlockPlaced())) {
            CommonUtil.nextTick(new Runnable() {
                public void run() {
                    updateRails(event.getBlockPlaced());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        final Block block = event.getBlock();
        final Material type = block.getType();
        if (Util.ISTCRAIL.get(type)) {
            if (!Util.isSupported(block)) {
                // No valid supporting block - clear the active signs of this rails
                onRailsBreak(block);
            } else if (updateRails(block)) {
                // Handle regular physics
                event.setCancelled(true);
            }
        } else if (MaterialUtil.ISSIGN.get(type)) {
            if (!Util.isSupported(block)) {
                // Sign is no longer supported - clear all sign actions
                SignAction.handleDestroy(new SignActionEvent(block));
            }
        }
    }

    public boolean updateRails(final Block below) {
        if (!MaterialUtil.ISRAILS.get(below)) {
            return false;
        }
        // Obtain the vertical rail and the rail below it, if possible
        final Block vertRail = below.getRelative(BlockFace.UP);
        if (Util.ISVERTRAIL.get(vertRail)) {
            // Find and validate rails - only regular types are allowed
            Rails rails = BlockUtil.getRails(below);
            if (rails == null || rails.isCurve() || rails.isOnSlope()) {
                return false;
            }
            BlockFace railDir = rails.getDirection();
            BlockFace dir = Util.getVerticalRailDirection(vertRail);
            // No other directions going on for this rail?
            if (railDir != dir && railDir != dir.getOppositeFace()) {
                if (Util.getRailsBlock(below.getRelative(railDir)) != null) {
                    return false;
                }
                if (Util.getRailsBlock(below.getRelative(railDir.getOppositeFace())) != null) {
                    return false;
                }
            }

            // Direction we are about to connect is supported?
            if (MaterialUtil.SUFFOCATES.get(below.getRelative(dir))) {
                rails.setDirection(dir, true);
                BlockUtil.setData(below, rails);
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        if (event.isCancelled() || TrainCarts.isWorldDisabled(event)) {
            return;
        }
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getEntity().getVehicle());
        if (member != null && member.getGroup().isTeleportImmune()) {
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
        if (!TrainCarts.allowNetherTeleport) {
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
            mm.getGroup().getBlockTracker().updatePosition();
        }
        // Remove path node from path finding
        PathNode.remove(railsBlock);
    }
}
