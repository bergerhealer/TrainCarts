package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitOccupied;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

public class SignActionWait extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.hasGroup()) return;
		if (info.isType("wait") && info.hasRails()) {
			if (info.isAction(SignActionType.GROUP_ENTER) && info.isPowered()) {
				//allowed?
				BlockFace dir = info.getMember().getDirection();
				if (SignActionBlock.isHeadingTo(info, dir.getOppositeFace())) {
					//distance
					int dist = 0;
					try {
						dist = Integer.parseInt(info.getLine(3));
					} catch (NumberFormatException ex) {
						TrackIterator iter = new TrackIterator(info.getRails(), dir);
						dist = 50;
						while (iter.hasNext() && --dist >= 0) {
							for (Block sign : Util.getSignsFromRails(iter.next())) {
								if (BlockUtil.getSign(sign).getLine(1).toLowerCase().startsWith("wait")) {
									dist = 0;
									break;
								}
							}
						}
						dist = iter.getDistance();
					}
					if (GroupActionWaitOccupied.isOccupied(info.getRails(), dir, info.getGroup(), dist)) {
						info.getGroup().clearActions();
						info.getGroup().addActionWaitOccupied(dist);
					}
				}
			} else if (info.isAction(SignActionType.REDSTONE_OFF)) {
				info.getGroup().clearActions();
			}
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("wait")) {
				handleBuild(event, Permission.BUILD_WAIT, "train wait sign", "waits the train until the tracks ahead are clear");
			}
		}
	}

}
