package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionEject extends SignAction {

	@Override
	public boolean click(SignActionEvent info, Player player, Action action) {
		if (!info.isType("eject")) {
			return false;
		}
		MinecartMember member = MinecartMember.get(player.getVehicle());
		if (member == null) {
			return true;
		}
		info.setMember(member);
		eject(info);
		return true;
	}

	public void eject(SignActionEvent info) {
		//read from the sign
		String[] offsettext = info.getLine(2).split("/");
		String[] angletext = info.getLine(3).split("/");
		Vector offset = new Vector();
		if (offsettext.length == 3) {
			offset.setX(ParseUtil.parseDouble(offsettext[0], 0.0));
			offset.setY(ParseUtil.parseDouble(offsettext[1], 0.0));
			offset.setZ(ParseUtil.parseDouble(offsettext[2], 0.0));
		} else if (offsettext.length == 1) {
			offset.setY(ParseUtil.parseDouble(offsettext[0], 0.0));
		}
		if (offset.length() > TrainCarts.maxEjectDistance) {
			offset.normalize().multiply(TrainCarts.maxEjectDistance);
		}

		float dyaw = 0F;
		float dpitch = 0F;
		if (angletext.length == 2) {
			dyaw = (float) ParseUtil.parseDouble(angletext[0], 0.0);
			dpitch = (float) ParseUtil.parseDouble(angletext[1], 0.0);
		} else if (angletext.length == 1) {
			dyaw = (float) ParseUtil.parseDouble(angletext[0], 0.0);
		}
		float signyawoffset = (float) FaceUtil.faceToYaw(info.getFacing().getOppositeFace());
		dyaw += signyawoffset + 90F;
		
		//convert to sign-relative-space
		offset = MathUtil.rotate(signyawoffset, 0F, offset);
		
		//actually eject
		if (info.isTrainSign()) {
			if (info.isRCSign()) {
				for (MinecartGroup group : info.getRCTrainGroups()) {
					for (MinecartMember mm : group) {
						eject(mm, offset, dyaw, dpitch);
					}
				}
			} else {
				MinecartGroup group = info.getGroup();
				if (group != null) {
					for (MinecartMember mm : group) {
						eject(mm, offset, dyaw, dpitch);
					}
				}
			}
		} else {
			eject(info.getMember(), offset, dyaw, dpitch);
		}
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("eject")) return;
		boolean isRemote = false;
		if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
		} else if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
		} else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
			isRemote = true;
		} else {
			return;
		}
		if (isRemote || (info.hasMember() && info.isPowered())) {
			eject(info);
		}
	}

	private void eject(MinecartMember mm, Vector offset, float dyaw, float dpitch) {
		Location loc = mm.getBlock().getLocation();
		loc = loc.add(0.5, 1.5, 0.5).add(offset);
		loc.setYaw(dyaw);
		loc.setPitch(dpitch);
		mm.eject(loc);
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}
	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			if (event.isType("eject")) {
				if (event.isRCSign()) {
					return handleBuild(event, Permission.BUILD_EJECTOR, "cart ejector", "eject the passengers of a remote train");
				} else {
					return handleBuild(event, Permission.BUILD_EJECTOR, "cart ejector", "eject the passengers of a train");
				}
			}
		}
		return false;
	}

}
