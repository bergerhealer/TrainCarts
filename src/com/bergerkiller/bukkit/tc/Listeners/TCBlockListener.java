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
			}
		}
	}
}