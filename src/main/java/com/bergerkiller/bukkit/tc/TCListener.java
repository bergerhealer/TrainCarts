package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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

import com.bergerkiller.bukkit.common.collections.EntityMap;
import com.bergerkiller.bukkit.common.events.EntityAddEvent;
import com.bergerkiller.bukkit.common.events.EntityRemoveFromServerEvent;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MemberConverter;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import com.bergerkiller.bukkit.common.utils.BlockUtil;

public class TCListener implements Listener {
	public static Player lastPlayer = null;
	public static boolean cancelNextDrops = false;
	private ArrayList<MinecartGroup> expectUnload = new ArrayList<MinecartGroup>();
	private EntityMap<Player, Long> lastHitTimes = new EntityMap<Player, Long>();
	private static final boolean DEBUG_DO_TRACKTEST = false;
	private static final long SIGN_CLICK_INTERVAL = 500; // Interval in MS where left-click interaction is allowed

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		MinecartMember<?> vehicle = MemberConverter.toMember.convert(event.getPlayer().getVehicle());
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

	@EventHandler(priority = EventPriority.LOWEST)
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
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent event) {
		if (!event.isCancelled()) {
			// This chunk is still referenced, ensure that it is really gone
			OfflineGroupManager.lastUnloadChunk = MathUtil.longHashToLong(event.getChunk().getX(), event.getChunk().getZ());
			// Unload groups
			synchronized (this.expectUnload) {
				for (MinecartGroup mg : this.expectUnload) {
					if (mg.isInChunk(event.getChunk())) {
						OfflineGroupManager.hideGroup(mg);
					}
				}
			}
			OfflineGroupManager.unloadChunk(event.getChunk());
			OfflineGroupManager.lastUnloadChunk = null;
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldLoad(WorldLoadEvent event) {
		OfflineGroupManager.initChunks(event.getWorld());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldUnload(WorldUnloadEvent event) {
		if (!event.isCancelled()) {
			for (MinecartGroup group : MinecartGroup.getGroups()) {
				if (group.getWorld() == event.getWorld()) {
					OfflineGroupManager.hideGroup(group);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		OfflineGroupManager.loadChunk(event.getChunk());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onVehicleExit(VehicleExitEvent event) {
		if (event.isCancelled() || TrainCarts.isWorldDisabled(event.getVehicle().getWorld())) {
			return;
		}
		MinecartMember<?> mm = MinecartMemberStore.get(event.getVehicle());
		if (mm == null) {
			return;
		}
		Location mloc = mm.getEntity().getLocation();
		mloc.setYaw(mloc.getYaw() + 180);
		mloc.setPitch(0.0f);
		final Location loc = MathUtil.move(mloc, TrainCarts.exitOffset);
		final Entity e = event.getExited();
		//teleport
		CommonUtil.nextTick(new Runnable() {
			public void run() {
				if (e.isDead()) {
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

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityAdd(EntityAddEvent event) {
		if (!MinecartMemberStore.canConvert(event.getEntity())) {
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
			MinecartMember<?> member = MinecartMemberStore.get(event.getEntity());
			if (member == null) {
				return;
			}
			MinecartGroup group = member.getGroup();
			if (group == null) {
				return;
			}
			if (group.size() == 1) {
				group.unload();
			} else {
				group.remove(member);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (!event.isCancelled()) {
			MinecartMember<?> member = MinecartMemberStore.get(event.getVehicle());
			if (member != null && !member.getEntity().isDead() && !member.isUnloaded()) {
				CartProperties prop = member.getProperties();
				if (event.getEntered() instanceof Player) {
					Player player = (Player) event.getEntered();
					if (prop.getPlayersEnter() && (prop.isPublic() || prop.isOwner(player))) {
						prop.showEnterMessage(player);
					} else {
						event.setCancelled(true);
					}
				} else if (member.getGroup().getProperties().mobCollision != CollisionMode.ENTER) {
					event.setCancelled(true);
				}
				member.onPropertiesChanged();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onVehicleDamage(VehicleDamageEvent event) {
		if (event.getAttacker() != null && event.getAttacker() instanceof Player) {
			if (!event.isCancelled()) {
				MinecartMember<?> mm = MinecartMemberStore.get(event.getVehicle());
				if (mm != null) {
					Player p = (Player) event.getAttacker();
					if (mm.getProperties().hasOwnership(p)) {
						CartPropertiesStore.setEditing(p, mm.getProperties());
						// Manual movement
						MinecartGroup group = mm.getGroup();
						if (group.getProperties().isManualMovementAllowed()) {
							// Get velocity modifier
							float yaw = p.getLocation().getYaw();
							mm.getEntity().setVelocity(MathUtil.getDirection(yaw, 0.0f).multiply(TrainCarts.manualMovementSpeed));
							group.updateDirection();
						}
					} else {
						event.setCancelled(true);
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
		if (TrainCarts.isWorldDisabled(event.getVehicle().getWorld())) {
			return;
		}
		try {
			MinecartMember<?> member = MinecartMemberStore.get(event.getVehicle());
			if (member != null) {
				event.setCancelled(!member.onEntityCollision(event.getEntity()));
			}
		} catch (Throwable t) {
			TrainCarts.plugin.handle(t);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (TrainCarts.isWorldDisabled(event.getPlayer().getWorld())) return;
		try {
			if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
				int id = event.getClickedBlock().getTypeId();
				if (!Util.ISTCRAIL.get(id)) {
					return;
				}
				ItemStack item = event.getPlayer().getItemInHand();
				if (item == null || !MaterialUtil.ISMINECART.get(item)) {
					return;
				}

				// handle permission
				if (!Permission.GENERAL_PLACE_MINECART.has(event.getPlayer())) {
					event.setCancelled(true);
					return;
				}

				// Track map debugging logic
				if (DEBUG_DO_TRACKTEST) {
					// Track map test
					TrackMap map = new TrackMap(event.getClickedBlock(), FaceUtil.yawToFace(event.getPlayer().getLocation().getYaw() - 90, false));
					while (map.hasNext()) {
						map.next();
					}
					byte data = 0;
					for (Block block : map) {
						block.setTypeIdAndData(Material.WOOL.getId(), data, false);
						data++;
						if (data == 16) {
							data = 0;
						}
					}
					event.setCancelled(true);
					return;
				}

				Location at = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);

				// No minecart blocking it?
				if (MinecartMemberStore.getAt(at, null, 0.5) != null) {
					event.setCancelled(true);
					return;
				}

				// Place logic for special rail types
				lastPlayer = event.getPlayer();
				if (MaterialUtil.ISPRESSUREPLATE.get(id)) {
					BlockFace dir = Util.getPlateDirection(event.getClickedBlock());
					if (dir == BlockFace.SELF) {
						dir = FaceUtil.yawToFace(event.getPlayer().getLocation().getYaw() - 90, false);
					}
					at.setYaw(FaceUtil.faceToYaw(dir));
					MinecartMemberStore.spawnBy(at, event.getPlayer());
				} else if (Util.ISVERTRAIL.get(id)) {
					BlockFace dir = Util.getVerticalRailDirection(event.getClickedBlock().getData());
					at.setYaw(FaceUtil.faceToYaw(dir));
					at.setPitch(-90.0f);
					MinecartMemberStore.spawnBy(at, event.getPlayer());
				}
			}
			final boolean isLeftClick = event.getAction() == Action.LEFT_CLICK_BLOCK;			
			if ((isLeftClick || (event.getAction() == Action.RIGHT_CLICK_BLOCK)) && MaterialUtil.ISSIGN.get(event.getClickedBlock())) {
				boolean clickAllowed = true;
				// Prevent creative players instantly destroying signs after clicking
				if (isLeftClick && event.getPlayer().getGameMode() == GameMode.CREATIVE) {
					// Deny left-clicking at a too high interval
					Long lastHitTime = lastHitTimes.get(event.getPlayer());
					long time = System.currentTimeMillis();
					if (lastHitTime != null && (lastHitTime.longValue() + SIGN_CLICK_INTERVAL) >= time) {
						clickAllowed = false;
					}
					lastHitTimes.put(event.getPlayer(), time);
				}
				if (clickAllowed) {
					SignAction.handleClick(event);
				}
			}
		} catch (Throwable t) {
			TrainCarts.plugin.handle(t);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		try {
			MinecartMember<?> mm = MinecartMemberStore.get(event.getRightClicked());
			if (mm != null) {
				CartPropertiesStore.setEditing(event.getPlayer(), mm.getProperties());
				MinecartMember<?> entered = MinecartMemberStore.get(event.getPlayer().getVehicle());
				if (entered != null && !entered.getProperties().getPlayersExit()) {
					event.setCancelled(true);
				}
			}
		} catch (Throwable t) {
			TrainCarts.plugin.handle(t);
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onBlockBreak(BlockBreakEvent event) {
		if (!event.isCancelled()) {
			if (MaterialUtil.ISSIGN.get(event.getBlock())) {
				SignAction.handleDestroy(new SignActionEvent(event.getBlock()));
			} else if (MaterialUtil.ISRAILS.get(event.getBlock())) {
				onRailsBreak(event.getBlock());
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onBlockPlace(final BlockPlaceEvent event) {
		if (!event.isCancelled() && MaterialUtil.ISRAILS.get(event.getBlockPlaced())) {
			CommonUtil.nextTick(new Runnable() {
				public void run() {
					updateRails(event.getBlockPlaced());
				}
			});
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		if (event.isCancelled()) {
			return;
		}
		final Block block = event.getBlock();
		final int type = block.getTypeId();
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
			BlockFace dir = Util.getVerticalRailDirection(vertRail.getData());
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
				below.setData(rails.getData());
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
				ItemStack item = event.getPlayer().getItemInHand();
				if (LogicUtil.nullOrEmpty(item)) {
					event.getPlayer().setItemInHand(new ItemStack(Material.SIGN, 1));
				} else if (item.getTypeId() == Material.SIGN.getId() && item.getAmount() < ItemUtil.getMaxSize(item)) {
					ItemUtil.addAmount(item, 1);
					event.getPlayer().setItemInHand(item);
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
		MinecartMember<?> member = MinecartMemberStore.get(event.getEntity().getVehicle());
		if (member != null && member.getGroup().isTeleportImmune()) {
			event.setCancelled(true);
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
