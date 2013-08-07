package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionEject extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("eject");
	}

	@Override
	public boolean click(SignActionEvent info, Player player) {
		MinecartMember<?> member = MinecartMemberStore.get(player.getVehicle());
		if (member == null) {
			return false;
		}
		info.setMember(member);
		eject(info);
		return true;
	}

	public void eject(SignActionEvent info) {
		final boolean hasSettings = !info.getLine(2).isEmpty() || !info.getLine(3).isEmpty();
		Vector offset = new Vector();
		float yaw = 0F;
		float pitch = 0F;
		if (hasSettings) {
			// Read the offset
			offset = Util.parseVector(info.getLine(2), offset);

			// Read the rotation
			String[] angletext = Util.splitBySeparator(info.getLine(3));
			if (angletext.length == 2) {
				yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
				pitch = ParseUtil.parseFloat(angletext[1], 0.0f);
			} else if (angletext.length == 1) {
				yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
			}

			// Convert to sign-relative-space
			float signyawoffset = (float) FaceUtil.faceToYaw(info.getFacing().getOppositeFace());
			offset = MathUtil.rotate(signyawoffset, 0F, offset);
			yaw += signyawoffset + 90F;
		}

		// Actually eject
		for (MinecartMember<?> mm : info.getMembers()) {
			if (hasSettings) {
				mm.eject(offset, yaw, pitch);
			} else {
				mm.ejectWithOffset();
			}
		}
	}

	@Override
	public void execute(SignActionEvent info) {
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

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			if (event.isRCSign()) {
				return handleBuild(event, Permission.BUILD_EJECTOR, "cart ejector", "eject the passengers of a remote train");
			} else {
				return handleBuild(event, Permission.BUILD_EJECTOR, "cart ejector", "eject the passengers of a train");
			}
		}
		return false;
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}
}
