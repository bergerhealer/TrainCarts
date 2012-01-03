package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.DetectorSign;
import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;

public class SignActionDetector extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.hasRails()) return;
		if (info.getMode() != SignActionMode.NONE) {
			if (info.isType("detector")) {
				if (info.isAction(SignActionType.REDSTONE_ON)) {
//					DetectorSign sign = DetectorSign.getSign(info.getRails());
//					if (sign != null) sign.update(info.getWorld(), null);
				}
			}
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (!DetectorSign.validate(event.getLines())) {
			return;
		}
		if (handleBuild(event, Permission.BUILD_PROPERTY, "train detector", "detects the presence of trains between this detector sign and another")) {
			//try to create the other sign
			Block startsign = event.getBlock();
			Block startrails = startsign.getRelative(0, 2, 0);
			if (!BlockUtil.isRails(startrails)) {
				startrails = BlockUtil.getAttachedBlock(startsign).getRelative(BlockFace.UP);
				if (!BlockUtil.isRails(startrails)) {
					event.getPlayer().sendMessage(ChatColor.RED + "Note that no rails are nearby, so it is not yet activated.");
					return;
				}
			}
			TrackMap map = new TrackMap(startrails, BlockUtil.getFacing(startsign));
			//now try to find the end rails : find the other sign
			boolean found = false;
			int i = TrainCarts.maxDetectorLength;
			Block next;
			Block endsign = null;
			Sign sign;
			while (!found) {
				next = map.next();
				if (--i < 0 || next == null) {
					//no second sign found
					event.getPlayer().sendMessage(ChatColor.RED + "Could not find a second connected detector sign, place one to activate this detector.");
					return;
				}
				for (Block signblock : BlockUtil.getSignsAttached(next)) {
					sign = BlockUtil.getSign(signblock);
					if (SignActionMode.fromSign(sign) != SignActionMode.NONE) {
						if (sign.getLine(1).toLowerCase().startsWith("detector")) {
							endsign = signblock;
							found = true;
							break;
						}
					}
				}
			}
			//start and end found : add it
			event.getPlayer().sendMessage(ChatColor.GREEN + "A second connected detector sign was found, track set.");
			DetectorSign.add(startsign, endsign, map);
		}
	}

}
