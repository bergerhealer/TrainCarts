package com.bergerkiller.bukkit.tc.listeners;

import java.util.HashSet;

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;

public class TCBlockListener extends BlockListener {
	
	private HashSet<Block> poweredBlocks = new HashSet<Block>();

	@Override
	public void onBlockRedstoneChange(BlockRedstoneEvent event) {
		if (BlockUtil.isSign(event.getBlock())) {
			SignActionEvent info = new SignActionEvent(event.getBlock());
			SignAction.executeAll(info, SignActionType.REDSTONE_CHANGE);
			boolean powered = poweredBlocks.contains(event.getBlock());
			if (event.getNewCurrent() > 0 && !powered) {
				poweredBlocks.add(event.getBlock());
				SignAction.executeAll(info, SignActionType.REDSTONE_ON);
			} else if (powered && event.getNewCurrent() == 0) {
				poweredBlocks.remove(event.getBlock());
				SignAction.executeAll(info, SignActionType.REDSTONE_OFF);
			}
		}
	}
	
	public void onSignChange(SignChangeEvent event) {
		SignAction.handleBuild(event);
	}
}