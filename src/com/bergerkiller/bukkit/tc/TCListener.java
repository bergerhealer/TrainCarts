package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;

import net.minecraft.server.EntityMinecart;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.util.LongHash;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.BlockSet;
import com.bergerkiller.bukkit.common.events.EntityAddEvent;
import com.bergerkiller.bukkit.common.events.EntityRemoveEvent;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.common.utils.BlockUtil;

public class TCListener implements Listener {
	private static final BlockSet ignoredSigns = new BlockSet();
	private BlockSet poweredBlocks = new BlockSet();
	public static Player lastPlayer = null;
	public static boolean cancelNextDrops = false;
	private ArrayList<MinecartGroup> expectUnload = new ArrayList<MinecartGroup>();

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
			OfflineGroupManager.lastUnloadChunk = LongHash.toLong(event.getChunk().getX(), event.getChunk().getZ());
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
		if (TrainCarts.isWorldDisabled(event.getVehicle().getWorld())) return;
		if (!event.isCancelled() && event.getVehicle() instanceof Minecart) {
			Minecart m = (Minecart) event.getVehicle();
			Location mloc = m.getLocation();
			mloc.setYaw(m.getLocation().getYaw() + 180);
			final Location loc = MathUtil.move(mloc, TrainCarts.exitOffset);
			final Entity e = event.getExited();
			//teleport
			CommonUtil.nextTick(new Runnable() {
				public void run() {
					if (e.isDead()) return;
					loc.setYaw(e.getLocation().getYaw());
					loc.setPitch(e.getLocation().getPitch());
					e.teleport(loc);
				}
			});
			MinecartMember mm = MinecartMember.get(m);
			if (mm != null) mm.update();
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityAdd(EntityAddEvent event) {
		net.minecraft.server.Entity entity = EntityUtil.getNative(event.getEntity());
		if (!(entity instanceof EntityMinecart)) {
			return;
		}
		MinecartMember member = MinecartMemberStore.convert((EntityMinecart) entity);
		if (member != null && lastPlayer != null) {
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
	public void onEntityRemove(EntityRemoveEvent event) {
		if (event.getEntity() instanceof Minecart) {
			MinecartMember member = MinecartMember.get(event.getEntity());
			if (member == null) {
				return;
			}
			MinecartGroup group = member.getGroup();
			if (group != null && group.size() == 1) {
				group.unload();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (!event.isCancelled()) {
			MinecartMember member = MinecartMember.get(event.getVehicle());
			if (member != null) {
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
				member.update();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onVehicleDamage(VehicleDamageEvent event) {
		if (event.getAttacker() != null && event.getAttacker() instanceof Player) {
			if (!event.isCancelled()) {
				MinecartMember mm = MinecartMember.get(event.getVehicle());
				if (mm != null) {
					Player p = (Player) event.getAttacker();
					if (mm.getProperties().hasOwnership(p)) {
						CartPropertiesStore.setEditing(p, mm.getProperties());
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
			if (event.getVehicle() instanceof Minecart && !event.getVehicle().isDead()) {
				MinecartMember mm1 = EntityUtil.getNative(event.getVehicle(), MinecartMember.class);
				if (mm1 == null) {
					return;
				}
				if (mm1.isUnloaded() || !MinecartMemberStore.validateMinecart(mm1)) {
					event.setCancelled(true);
					return;
				}
				MinecartGroup g1 = mm1.getGroup();
				if (g1 == null || g1.isVelocityAction() || mm1.isCollisionIgnored(event.getEntity())) {
					event.setCancelled(true);
					return;
				}
				TrainProperties prop = g1.getProperties();
				if (!prop.canCollide(event.getEntity())) {
					event.setCancelled(true);
					return;
				}
				if (event.getEntity() instanceof Minecart) {
					MinecartMember mm2 = MinecartMember.get(event.getEntity());
					if (mm2 == null || mm1 == mm2) {
						event.setCancelled(true);
						return;
					}
					if (mm2.isUnloaded() || !MinecartMemberStore.validateMinecart(mm2)) {
						event.setCancelled(true);
						return;
					}
					MinecartGroup g2 = mm2.getGroup();
					if (!g2.getProperties().canCollide(g1)) {
						event.setCancelled(true);
						return;
					}
					if (g1 == g2 || MinecartGroup.link(mm1, mm2)) {
						event.setCancelled(true);
					} else if (g2.isVelocityAction()) {
						event.setCancelled(true);
					}
				} else if (event.getEntity().getVehicle() instanceof Minecart) {
					event.setCancelled(true);
				} else if (!prop.getCollisionMode(event.getEntity()).execute(mm1, event.getEntity())) {
					event.setCancelled(true);
				}
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
				boolean isplate = Util.isPressurePlate(id);
				if (isplate || BlockUtil.isRails(id)) {
					ItemStack item = event.getPlayer().getItemInHand();
					Material itemmat = item == null ? null : item.getType();

					if (CommonUtil.contains(itemmat, Material.MINECART, Material.POWERED_MINECART, Material.STORAGE_MINECART)) {
						//Placing a minecart on the tracks
						if (Permission.GENERAL_PLACE_MINECART.has(event.getPlayer())) {
							//Not already a minecart at this spot?
							Location at = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
							if (MinecartMember.getAt(at, null, 0.5) == null) {
								lastPlayer = event.getPlayer();
								if (isplate) {
									//perform a manual Minecart spawn
									BlockFace dir = Util.getPlateDirection(event.getClickedBlock());
									if (dir == BlockFace.SELF) {
										dir = FaceUtil.yawToFace(event.getPlayer().getLocation().getYaw() - 90, false);
									}
									//get spawn location
									Location loc = event.getClickedBlock().getLocation().add(0.5, 0.0, 0.5);
									if (dir == BlockFace.SOUTH || dir == BlockFace.NORTH) {
										loc.setYaw(0.0F);
									} else {
										loc.setYaw(90.0F);
									}

									//get type of minecart
									int type;
									switch (itemmat) {
									case STORAGE_MINECART : type = 1; break;
									case POWERED_MINECART : type = 2; break;
									default : type = 0; break;
									}

									//subtract item
									if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
										item.setAmount(item.getAmount() - 1);
										if (item.getAmount() == 0) {
											event.getPlayer().setItemInHand(null);
										}
									}

									//event
									MinecartMember member = MinecartMember.spawn(loc, type);
									CommonUtil.callEvent(new VehicleCreateEvent(member.getMinecart()));
								}
								return;
							}
						}
						event.setCancelled(true);
					}
				}
			}
			if ((event.getAction() == Action.RIGHT_CLICK_BLOCK) || (event.getAction() == Action.LEFT_CLICK_BLOCK)) {
				if (BlockUtil.isSign(event.getClickedBlock())) {
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
			MinecartMember mm = MinecartMember.get(event.getRightClicked());
			if (mm != null) {
				CartPropertiesStore.setEditing(event.getPlayer(), mm.getProperties());
				MinecartMember entered = MinecartMember.get(event.getPlayer().getVehicle());
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
			if (BlockUtil.isSign(event.getBlock())) {
				SignActionDetector.removeDetector(event.getBlock());
				SignActionSpawn.remove(event.getBlock());
				//invalidate this piece of track
				PathNode.clear(Util.getRailsFromSign(event.getBlock()));
			} else if (BlockUtil.isRails(event.getBlock())) {
				PathNode.remove(event.getBlock());
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onSignChange(SignChangeEvent event) {
		if (TrainCarts.isWorldDisabled(event)) return;
		SignAction.handleBuild(event);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockRedstoneChange(BlockRedstoneEvent event) {
		if (TrainCarts.isWorldDisabled(event)) return;
		Material type = event.getBlock().getType();
		if (BlockUtil.isType(type, Material.LEVER)) {
			Block up = event.getBlock().getRelative(BlockFace.UP);
			Block down = event.getBlock().getRelative(BlockFace.DOWN);
			if (BlockUtil.isSign(up)) {
				triggerRedstoneChange(up, event);
			}
			if (BlockUtil.isSign(down)) {
				triggerRedstoneChange(down, event);
			}
			ignoreOutputLever(event.getBlock());
		} else if (BlockUtil.isSign(type)) {
			if (!ignoredSigns.isEmpty() && ignoredSigns.remove(event.getBlock())) {
				return;
			}
			triggerRedstoneChange(event.getBlock(), event);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.isCancelled()) {
			return;
		}
		net.minecraft.server.Entity e = EntityUtil.getNative(event.getEntity());
		if (e.vehicle != null && e.vehicle instanceof MinecartMember && ((MinecartMember) e.vehicle).isTeleportImmune()) {
			event.setCancelled(true);
		}
	}

	/**
	 * Ignores signs of current-tick redstone changes caused by the lever
	 * 
	 * @param lever to ignore
	 */
	public void ignoreOutputLever(Block lever) {
		// Ignore signs that are attached to the block the lever is attached to
		Block att = BlockUtil.getAttachedBlock(lever);
		for (BlockFace face : FaceUtil.attachedFaces) {
			Block signblock = att.getRelative(face);
			if (BlockUtil.isSign(signblock) && BlockUtil.getAttachedFace(signblock) == face.getOppositeFace()) {
				if (ignoredSigns.isEmpty()) {
					// clear this the next tick
					CommonUtil.nextTick(new Runnable() {
						public void run() {
							ignoredSigns.clear();
						}
					});
				}
				ignoredSigns.add(signblock);
			}
		}
	}

	public void triggerRedstoneChange(Block signblock, BlockRedstoneEvent event) {
		boolean powered = poweredBlocks.contains(event.getBlock());
		SignActionEvent info = new SignActionEvent(signblock);
		SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
		if (powered) {
			//no longer powered?
			if (info.isPowerInverted() != (event.getNewCurrent() == 0) && !info.isPowered()) {
				poweredBlocks.remove(signblock);
				SignAction.executeAll(info, SignActionType.REDSTONE_OFF);
			}
		} else {
			//now powered?
			if (info.isPowerInverted() != (event.getNewCurrent() > 0) && info.isPowered()) {
				poweredBlocks.add(event.getBlock());
				SignAction.executeAll(info, SignActionType.REDSTONE_ON);
			}
		}
	}
}
