package com.bergerkiller.bukkit.tc.listeners;

import java.util.HashSet;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import org.bukkit.event.player.PlayerPickupItemEvent;
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

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.GroupManager;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.permissions.Permission;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.utils.EntityUtil;
import com.bergerkiller.bukkit.tc.utils.ItemUtil;

public class TCListener implements Listener {
	
	private HashSet<Block> poweredBlocks = new HashSet<Block>();
	public static Player lastPlayer = null;

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent event) {
		if (!event.isCancelled()) {
			boolean hastounload = false;
			for (MinecartGroup mg : MinecartGroup.getGroups()) {
				if (mg.isInChunk(event.getChunk())) {
					if (mg.canUnload()) {
						hastounload = true;
					} else {
						event.setCancelled(true);
						return;
					}
				}
			}
			if (hastounload) {
				for (MinecartGroup mg : MinecartGroup.getGroups()) {
					if (mg.isInChunk(event.getChunk())) {
						GroupManager.hideGroup(mg);
					}
				}
			}
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		GroupManager.refresh(event.getWorld());
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
		if (!event.isCancelled() && event.getVehicle() instanceof Minecart) {			
			Minecart m = (Minecart) event.getVehicle();
			Location loc = m.getLocation();
			loc.setYaw(EntityUtil.getMinecartYaw(m) + 180);
			loc = Util.move(loc, TrainCarts.exitOffset);
			//teleport
			Task t = new Task(TrainCarts.plugin, event.getExited(), loc) {
				public void run() {
					Entity e = (Entity) getArg(0);
					Location loc = (Location) getArg(1);
					loc.setYaw(e.getLocation().getYaw());
					loc.setPitch(e.getLocation().getPitch());
					e.teleport(loc);
				}
			};
			t.startDelayed(0);
		}
	}
		
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onVehicleCreate(VehicleCreateEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			if (MinecartMember.canConvert(event.getVehicle())) {
				new Task(TrainCarts.plugin, event.getVehicle(), lastPlayer) {
					public void run() {
						MinecartMember mm = MinecartMember.convert(getArg(0));
						Player lp = (Player) getArg(1);
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
				}.startDelayed(0);
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
		if (event.getVehicle() instanceof Minecart && !event.getVehicle().isDead()) {
			MinecartMember mm1 = MinecartMember.convert(event.getVehicle());
			if (mm1 != null) {
				if (mm1.getGroup().isVelocityAction()) {
					event.setCancelled(true);
				} else if (mm1.isCollisionIgnored(event.getEntity())) {
					event.setCancelled(true);
				} else {
					TrainProperties prop = mm1.getGroup().getProperties();
					if (event.getEntity() instanceof Minecart) {
						MinecartMember mm2 = MinecartMember.convert(event.getEntity());
						if (mm2 == null || mm1.getGroup() == mm2.getGroup() || MinecartGroup.link(mm1, mm2)) {
							event.setCancelled(true);
						} else if (mm2.getGroup().isVelocityAction()) {
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
	}
	
	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		if (event.isCancelled()) return;
		if (TrainCarts.stackMinecarts && ItemUtil.isMinecartItem(event.getItem())) {
			ItemStack stack = event.getItem().getItemStack();
			ItemUtil.transfer(stack, event.getPlayer().getInventory(), Integer.MAX_VALUE);
			EntityUtil.getNative(event.getPlayer()).receive(EntityUtil.getNative(event.getItem()), 1);
			event.getItem().remove();
			event.setCancelled(true);
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if (BlockUtil.isRails(event.getClickedBlock())) {
				ItemStack item = event.getPlayer().getItemInHand();
				if (item != null) {
					if (item.getType() == Material.MINECART || 
							item.getType() == Material.POWERED_MINECART || 
							item.getType() == Material.STORAGE_MINECART) {
						//Placing a minecart on the tracks
						if (Permission.GENERAL_PLACE_MINECART.has(event.getPlayer())) {
							//Not already a minecart at this spot?
							Location at = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
							if (MinecartMember.getAt(at, null, 0.5) == null) {
								lastPlayer = event.getPlayer();
								return;
							}
						}
						event.setCancelled(true);
					}
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
	}
		
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		MinecartMember mm = MinecartMember.get(event.getRightClicked());
		if (mm != null) {
			mm.setEditing(event.getPlayer());
			MinecartMember entered = MinecartMember.get(event.getPlayer().getVehicle());
			if (entered != null && !entered.getProperties().allowPlayerExit) {
				event.setCancelled(true);
			}
			if (!mm.getProperties().isPublic && !mm.getProperties().isOwner(event.getPlayer())) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockRedstoneChange(BlockRedstoneEvent event) {
		if (BlockUtil.isSign(event.getBlock())) {
			SignActionEvent info = new SignActionEvent(event.getBlock());
			SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
			boolean powered = poweredBlocks.contains(event.getBlock());
			if (!powered && event.getNewCurrent() > 0) {
				poweredBlocks.add(event.getBlock());
				SignAction.executeAll(info, SignActionType.REDSTONE_ON);
			} else if (powered && event.getNewCurrent() == 0) {
				poweredBlocks.remove(event.getBlock());
				SignAction.executeAll(info, SignActionType.REDSTONE_OFF);
			}
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onBlockBreak(BlockBreakEvent event) {
		if (!event.isCancelled()) {
			if (BlockUtil.isSign(event.getBlock())) {
				SignActionDetector.removeDetector(event.getBlock());
				//invalidate this piece of track
				PathNode.clear(BlockUtil.getRailsBlockFromSign(event.getBlock()));
			} else if (BlockUtil.isRails(event.getBlock())) {
				PathNode.remove(event.getBlock());
			}
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onSignChange(SignChangeEvent event) {
		SignAction.handleBuild(event);
	}

}
