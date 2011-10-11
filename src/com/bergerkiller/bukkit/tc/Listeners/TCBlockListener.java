package com.bergerkiller.bukkit.tc.Listeners;

import java.util.HashSet;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent.ActionType;
import com.bergerkiller.bukkit.tc.Utils.BlockUtil;

public class TCBlockListener extends BlockListener {
	
	private HashSet<Block> poweredBlocks = new HashSet<Block>();

	@Override
	public void onBlockRedstoneChange(BlockRedstoneEvent event) {
		if (BlockUtil.isSign(event.getBlock())) {
			SignActionEvent info = new SignActionEvent(event.getBlock());
			CustomEvents.onSign(info, ActionType.REDSTONE_CHANGE);
			boolean powered = poweredBlocks.contains(event.getBlock());
			if (event.getNewCurrent() > 0 && !powered) {
				poweredBlocks.add(event.getBlock());
				CustomEvents.onSign(info, ActionType.REDSTONE_ON);
			} else if (powered && event.getNewCurrent() == 0) {
				poweredBlocks.remove(event.getBlock());
				CustomEvents.onSign(info, ActionType.REDSTONE_OFF);
			}
		}
	}
	
	public void onSignChange(SignChangeEvent event) {
		if (event.getLine(0).equalsIgnoreCase("[train]")) {
			String line = event.getLine(1).toLowerCase();
			if (line.toLowerCase().startsWith("station")) {
				if (!event.getPlayer().hasPermission("train.build.station")) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use this sign");
				} else {
					event.getPlayer().sendMessage(ChatColor.GREEN + "You built a station!");
				}
			} else if (line.toLowerCase().startsWith("spawn")) {
				if (!event.getPlayer().hasPermission("train.build.spawner")) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use this sign");
				} else {
					event.getPlayer().sendMessage(ChatColor.GREEN + "You built a train spawner!");
				}
			} else if (line.toLowerCase().startsWith("trigger")) {
				if (!event.getPlayer().hasPermission("train.build.trigger")) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use this sign");
				} else {
					event.getPlayer().sendMessage(ChatColor.GREEN + "Trigger built!");
				}
			} else if (line.toLowerCase().equals("tag")) {
				if (!event.getPlayer().hasPermission("train.build.tagswitcher")) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use this sign");
				} else {
					event.getPlayer().sendMessage(ChatColor.GREEN + "Tag switcher built!");
				}
			} else if (line.toLowerCase().startsWith("destroy")) {
				if (!event.getPlayer().hasPermission("train.build.destructor")) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use this sign");
				} else {
					event.getPlayer().sendMessage(ChatColor.GREEN + "You built a train destructor!");
				}
			} else if (line.toLowerCase().startsWith("eject")) {
				if (!event.getPlayer().hasPermission("train.build.ejector")) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use this sign");
				} else {
					event.getPlayer().sendMessage(ChatColor.GREEN + "You built a train passenger ejector!");
				}
			} else if (line.toLowerCase().startsWith("push")) {
				if (!event.getPlayer().hasPermission("train.build.pushHandler")) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use this sign");
				} else if (line.equalsIgnoreCase("push deny")) {
					event.getPlayer().sendMessage(ChatColor.GREEN + "You built a train push denier!");
				} else if (line.equalsIgnoreCase("push allow")) {
					event.getPlayer().sendMessage(ChatColor.GREEN + "You built a train push allower!");
				}
			} else if (line.toLowerCase().startsWith("property")) {
				if (!event.getPlayer().hasPermission("train.build.propertychanger")) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to use this sign");
				} else {
					String mode = event.getLine(2).toLowerCase().trim();
					if (mode.equals("settag")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets a tag!");
					} else if (mode.equals("addtag")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which adds a tag!");
					} else if (mode.equals("remtag")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which removes a tag!");
					} else if (mode.equals("collision") || mode.equals("collide")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if the train can collide!");
					} else if (mode.equals("linking") || mode.equals("link")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if the train can link!");
					} else if (mode.equals("mobenter") || mode.equals("mobsenter")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if mobs can enter the train!");
					} else if (mode.equals("slow") || mode.equals("slowdown")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if the train slow down!");
					} else if (mode.equals("setdefault") || mode.equals("default")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets train properties to a default!");
					} else if (mode.equals("push") || mode.equals("pushing")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which toggles pushing on and off!");
					} else if (mode.equals("pushmobs")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if mobs are pushed!");
					} else if (mode.equals("pushplayers")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if players are pushed!");
					} else if (mode.equals("pushmisc")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if misc entities are pushed!");
					} else if (mode.equals("playerenter")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if players can enter the train!");
					} else if (mode.equals("playerexit")) {
						event.getPlayer().sendMessage(ChatColor.GREEN + "You built a property changer which sets if players can exit the train!");
					}
				}
			}
		}
	}
}