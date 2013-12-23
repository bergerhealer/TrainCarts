package com.bergerkiller.bukkit.tc.signactions;

import java.util.Collection;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionEnter extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.getMode() != SignActionMode.NONE && info.isType("enter");
	}

	@Override
	public void execute(SignActionEvent info) {
		// If triggered by redstone, always activate
		if (!info.isAction(SignActionType.REDSTONE_ON)) {
			if (info.isCartSign()) {
				if (!info.isAction(SignActionType.MEMBER_ENTER)) {
					return;
				}
			} else if (info.isTrainSign()) {
				if (!info.isAction(SignActionType.GROUP_ENTER)) {
					return;
				}
			} else {
				return;
			}
		}
		// Sign is powered?
		if (!info.isPowered()) {
			return;
		}
		// Read the radius to look at
		double radiusXZ = ParseUtil.parseDouble(info.getLine(1), 2.0);
		double radiusY = 1.0;
		// Radius cylindrical or spherical?
		if (info.getLine(1).toLowerCase().endsWith("s")) {
			// Spherical
			radiusY = radiusXZ;
		}
		// Read whether mobs or players should enter
		boolean enterPlayers = false;
		boolean enterMobs = false;
		if (!info.getLine(2).isEmpty()) {
			String mode = info.getLine(2).toLowerCase();
			if (mode.contains("mob")) {
				enterMobs = true;
			}
			if (mode.contains("player")) {
				enterPlayers = true;
			}
		} else {
			// By default only players
			enterPlayers = true;
		}
		// Look around sign or around each minecart?
		boolean aroundSign = ParseUtil.parseBool(info.getLine(3));		
		// Get all the member to work on
		Collection<MinecartMember<?>> members = info.getMembers();
		if (aroundSign) {
			// Obtain all players near the sign and begin teleporting them cart-by-cart
			Location center = info.hasRails() ? info.getRailLocation() : info.getLocation();
			for (Entity entity : WorldUtil.getNearbyEntities(center, radiusXZ, radiusY, radiusXZ)) {
				// Not already in a vehicle?
				if (entity.getVehicle() != null) {
					continue;
				}
				// Can this entity enter the minecart?
				if (canEnter(entity, enterPlayers, enterMobs)) {
					// Look for an Empty minecart to put him in
					for (MinecartMember<?> member : members) {
						if (!member.getEntity().hasPassenger()) {
							// Move Entity to this Minecart and finish
							member.getEntity().setPassenger(entity);
							break;
						}
					}
				}
			}
		} else {
			// Go by each cart and find the nearest player, then make it enter that cart
			double lastDistance;
			double distance;
			Entity selectedEntity;
			for (MinecartMember<?> member : members) {
				List<Entity> nearby = member.getEntity().getNearbyEntities(radiusXZ, radiusY, radiusXZ);
				lastDistance = Double.MAX_VALUE;
				selectedEntity = null;
				for (Entity entity : nearby) {
					if (entity.getVehicle() != null || !canEnter(entity, enterPlayers, enterMobs)) {
						continue;
					}
					distance = member.getEntity().loc.distanceSquared(entity);
					if (distance < lastDistance) {
						lastDistance = distance;
						selectedEntity = entity;
					}
				}
				// Try to enter
				if (selectedEntity != null) {
					member.getEntity().setPassenger(selectedEntity);
				}
			}
		}
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			return handleBuild(event, Permission.BUILD_ENTER, "train enter sign", "cause nearby players/mobs to enter the train");
		}
		return false;
	}

	private static boolean canEnter(Entity entity, boolean enterPlayers, boolean enterMobs) {
		return (enterPlayers && entity instanceof Player) || (enterMobs && EntityUtil.isMob(entity));
	}
}
