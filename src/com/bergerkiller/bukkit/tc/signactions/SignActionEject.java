package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.Location;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionEject extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("eject")) return;
		final boolean isTrain;
		if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
			isTrain = false;
		} else if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
			isTrain = true;
		} else {
			return;
		}
		if (info.hasMember() && info.isPoweredFacing()) {
			//read from the sign
			String[] offsettext = info.getLine(2).split("/");
			String[] angletext = info.getLine(3).split("/");
			Vector offset = new Vector();
			if (offsettext.length == 3) {
				offset.setX(StringUtil.tryParse(offsettext[0], 0.0));
				offset.setY(StringUtil.tryParse(offsettext[1], 0.0));
				offset.setZ(StringUtil.tryParse(offsettext[2], 0.0));
			} else if (offsettext.length == 1) {
				offset.setY(StringUtil.tryParse(offsettext[0], 0.0));
			}
			if (offset.length() > TrainCarts.maxEjectDistance) {
				offset.normalize().multiply(TrainCarts.maxEjectDistance);
			}

			float dyaw = 0F;
			float dpitch = 0F;
			if (angletext.length == 2) {
				dyaw = (float) StringUtil.tryParse(angletext[0], 0.0);
				dpitch = (float) StringUtil.tryParse(angletext[1], 0.0);
			} else if (angletext.length == 1) {
				dyaw = (float) StringUtil.tryParse(angletext[0], 0.0);
			}
			float signyawoffset = (float) FaceUtil.faceToYaw(info.getFacing().getOppositeFace());
			dyaw += signyawoffset + 90F;
			
			//convert to sign-relative-space
			offset = MathUtil.rotate(signyawoffset, 0F, offset);
			
			//actually eject
			if (isTrain) {
				for (MinecartMember mm : info.getGroup()) {
					Location loc = mm.getBlock().getLocation();
					loc = loc.add(0.5, 1.5, 0.5).add(offset);
					loc.setYaw(dyaw);
					loc.setPitch(dpitch);
					mm.eject(loc);
				}
			} else {
				Location loc = info.getRailLocation().add(offset);
				loc.setYaw(dyaw);
				loc.setPitch(dpitch);
				info.getMember().eject(loc);
			}
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("eject")) {
				handleBuild(event, Permission.BUILD_EJECTOR, "cart ejector", "eject the passengers of a train");
			}
		}
	}

}
