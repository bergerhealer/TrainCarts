package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.BlockSet;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.storage.WorldGroupManager;
import com.bergerkiller.bukkit.common.utils.BlockUtil;

public class TCListener implements Listener {

	private BlockSet poweredBlocks = new BlockSet();
	public static Player lastPlayer = null;
	private ArrayList<MinecartGroup> expectUnload = new ArrayList<MinecartGroup>();

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
			WorldGroupManager.unloadChunk(event.getChunk());
			synchronized (this.expectUnload) {
				for (MinecartGroup mg : this.expectUnload) {
					if (mg.isInChunk(event.getChunk())) {
						WorldGroupManager.hideGroup(mg);
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		WorldGroupManager.loadChunk(event.getChunk());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onVehicleBlockCollision(VehicleBlockCollisionEvent event) {
		MinecartMember mm = MinecartMember.get(event.getVehicle());
		if (mm != null) {
			//direct hit or not?
			if (!mm.isTurned()) {
				mm.getGroup().stop();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onVehicleExit(VehicleExitEvent event) {
		if (TrainCarts.isWorldDisabled(event.getVehicle().getWorld())) return;
		if (!event.isCancelled() && event.getVehicle() instanceof Minecart) {
			Minecart m = (Minecart) event.getVehicle();
			Location loc = m.getLocation();
			loc.setYaw(m.getLocation().getYaw() + 180);
			loc = MathUtil.move(loc, TrainCarts.exitOffset);
			//teleport
			new Task(TrainCarts.plugin, event.getExited(), loc) {
				public void run() {
					Entity e = arg(0, Entity.class);
					if (e.isDead()) return;
					Location loc = arg(1, Location.class);
					loc.setYaw(e.getLocation().getYaw());
					loc.setPitch(e.getLocation().getPitch());
					e.teleport(loc);
				}
			}.start(0);
			MinecartMember mm = MinecartMember.get(m);
			if (mm != null) mm.update();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onVehicleCreate(VehicleCreateEvent event) {
		if (TrainCarts.isWorldDisabled(event.getVehicle().getWorld())) return;
		if (event.getVehicle() instanceof Minecart && !event.getVehicle().isDead()) {
			if (MinecartMember.canConvert(event.getVehicle())) {
				new Task(TrainCarts.plugin, event.getVehicle(), lastPlayer) {
					public void run() {
						MinecartMember mm = MinecartMember.convert(arg(0));
						Player lp = arg(1, Player.class);
						if (mm != null) {
							if (lp != null) {
								mm.getGroup().getProperties().setDefault(lp);
								if (TrainCarts.setOwnerOnPlacement) {
									mm.getProperties().setOwner(lp);
								}
								mm.setEditing(lp);
							}
						}
					}
				}.start(0);
				lastPlayer = null;
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
					if (prop.allowPlayerEnter && (prop.isPublic || prop.isOwner(player))) {
						prop.showEnterMessage(player);
					} else {
						event.setCancelled(true);
					}
				} else if (!prop.allowMobsEnter) {
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
						mm.setEditing(p);
					} else {
						event.setCancelled(true);
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		if (!event.isCancelled()) {
			MinecartMember mm = MinecartMember.get(event.getVehicle());
			if (mm != null) {
				mm.die();
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
		if (TrainCarts.isWorldDisabled(event.getVehicle().getWorld())) return;
		try {
			if (event.getVehicle() instanceof Minecart && !event.getVehicle().isDead()) {
				if (WorldGroupManager.wasInGroup(event.getVehicle())) {
					event.setCancelled(true);
					return;
				}
				MinecartMember mm1 = MinecartMember.convert(event.getVehicle());
				if (mm1 != null) {
					MinecartGroup g1 = mm1.getGroup();
					if (g1 == null) {
						event.setCancelled(true);
					} else if (g1.isVelocityAction()) {
						event.setCancelled(true);
					} else if (mm1.isCollisionIgnored(event.getEntity())) {
						event.setCancelled(true);
					} else {
						TrainProperties prop = g1.getProperties();
						if (event.getEntity() instanceof Minecart) {
							if (WorldGroupManager.wasInGroup(event.getEntity())) {
								event.setCancelled(true);
								return;
							}
							MinecartMember mm2 = MinecartMember.convert(event.getEntity());
							MinecartGroup g2 = mm2.getGroup();
							if (g2 == null || mm2 == null || mm1.getGroup() == g2 || MinecartGroup.link(mm1, mm2)) {
								event.setCancelled(true);
							} else if (g2.isVelocityAction()) {
								event.setCancelled(true);
							} else if (!g2.getProperties().canCollide(mm1)) {
								event.setCancelled(true);
							} else if (!g1.getProperties().canCollide(mm2)) {
								event.setCancelled(true);
							}
						} else if (prop.canPushAway(event.getEntity())) {
							mm1.pushSideways(event.getEntity());
							event.setCancelled(true);
						} else if (!prop.canCollide(event.getEntity())) {
							event.setCancelled(true);
						}
					}
				}
			}
		} catch (Throwable t) {
			TrainCarts.handleError(t);
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
				Sign sign = BlockUtil.getSign(event.getClickedBlock());
				if (sign != null) {
					if (sign.getLine(0).equalsIgnoreCase("[train]")) {
						if (sign.getLine(1).equalsIgnoreCase("destination")) {
							//get the train this player is editing
							Player p = event.getPlayer();
							MinecartMember mm = MinecartMember.getEditing(p);
							if (mm != null) {
								TrainProperties prop = mm.getGroup().getProperties();
								if (prop == null) {
									if (CartProperties.canHaveOwnership(p)) {
										p.sendMessage(ChatColor.YELLOW + "You haven't selected a train to edit yet!");
									} else {
										p.sendMessage(ChatColor.RED + "You are not allowed to own trains!");
									}
								} else if (!prop.isOwner(p)) {
									p.sendMessage(ChatColor.RED + "You don't own this train!");
								} else {
									String dest = sign.getLine(2);
									prop.setDestination(dest);
									p.sendMessage(ChatColor.YELLOW + "You have selected " + ChatColor.WHITE + dest + ChatColor.YELLOW + " as your destination!");
								}
							}
						}
					}
				}
			}
		} catch (Throwable t) {
			TrainCarts.handleError(t);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		try {
			MinecartMember mm = MinecartMember.get(event.getRightClicked());
			if (mm != null) {
				mm.setEditing(event.getPlayer());
				MinecartMember entered = MinecartMember.get(event.getPlayer().getVehicle());
				if (entered != null && !entered.getProperties().allowPlayerExit) {
					event.setCancelled(true);
				}
			}
		} catch (Throwable t) {
			TrainCarts.handleError(t);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockRedstoneChange(BlockRedstoneEvent event) {
		if (TrainCarts.isWorldDisabled(event)) return;
		int type = event.getBlock().getTypeId();
		if (BlockUtil.isType(type, Material.LEVER)) {
			Block up = event.getBlock().getRelative(BlockFace.UP);
			Block down = event.getBlock().getRelative(BlockFace.DOWN);
			if (BlockUtil.isSign(up)) {
				triggerRedstoneChange(up, event);
			}
			if (BlockUtil.isSign(down)) {
				triggerRedstoneChange(down, event);
			}
		} else if (BlockUtil.isType(type, Material.SIGN_POST, Material.WALL_SIGN)) {
			triggerRedstoneChange(event.getBlock(), event);
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

	@EventHandler(priority = EventPriority.MONITOR)
	public void onBlockBreak(BlockBreakEvent event) {
		if (!event.isCancelled()) {
			if (BlockUtil.isSign(event.getBlock())) {
				SignActionDetector.removeDetector(event.getBlock());
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

}
