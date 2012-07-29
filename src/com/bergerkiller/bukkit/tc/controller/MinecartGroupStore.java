package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.GroupCreateEvent;
import com.bergerkiller.bukkit.tc.events.GroupLinkEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.TrackWalkIterator;

public class MinecartGroupStore extends ArrayList<MinecartMember> {
	private static final long serialVersionUID = 1;
	protected static HashSet<MinecartGroup> groups = new HashSet<MinecartGroup>();

	public static MinecartGroup create() {
		MinecartGroup g = new MinecartGroup();
		groups.add(g);
		return g;
	}
	public static MinecartGroup create(Entity... members) {
		return create(null, MinecartMember.convertAll(members));
	}
	public static MinecartGroup create(MinecartMember... members) {
		return create(null, members);
	}
	public static MinecartGroup create(String name, Entity... members) {
		return create(name, MinecartMember.convertAll(members));
	}
	public static MinecartGroup create(String name, MinecartMember... members) {
		MinecartGroup g = new MinecartGroup();
		if (name != null) {
			g.setProperties(TrainProperties.get(name));
		}
		for (MinecartMember member : members) {
			if (member != null && !member.dead) {
				g.add(member);
			}
		}
		if (!g.isValid()) return null;
		groups.add(g);
		GroupCreateEvent.call(g);
		return g;
	}

	public static MinecartGroup spawn(Location[] at, int... types) {
		if (at.length != types.length || at.length == 0) return null;
		MinecartGroup g = new MinecartGroup();
		for (int i = 0; i < types.length; i++) {
			g.add(MinecartMember.spawn(at[i], types[i]));
		}
		groups.add(g);
		GroupCreateEvent.call(g);
		return g;
	}
	public static MinecartGroup spawn(Block startblock, BlockFace direction, int... types) {
		ArrayList<Integer> typelist = new ArrayList<Integer>(types.length);
		for (int i : types) typelist.add(i);
				return spawn(startblock, direction, typelist);
	}
	public static MinecartGroup spawn(Block startblock, BlockFace direction, List<Integer> types) {
		Location[] destinations = TrackWalkIterator.walk(startblock, direction, types.size(), TrainCarts.cartDistance);
		if (types.size() != destinations.length || destinations.length == 0) return null;
		MinecartGroup g = new MinecartGroup();
		for (int i = 0; i < destinations.length; i++) {
			g.add(MinecartMember.spawn(destinations[destinations.length - i - 1], types.get(i)));
		}
		groups.add(g);
		GroupCreateEvent.call(g);
		return g;
	}

	public static Set<MinecartGroup> getGroupsUnsafe() {
		return groups;
	}
	public static MinecartGroup[] getGroups() {
		return groups.toArray(new MinecartGroup[0]);
	}
	public static MinecartGroup get(Entity e) {
		MinecartMember mm = MinecartMember.get(e);
		if (mm == null) return null;
		return mm.getGroup();
	}
	public static MinecartGroup get(String name) {
		for (MinecartGroup group : groups) {
			if (group.getProperties().getTrainName().equalsIgnoreCase(name)) return group;
		}
		return null;
	}

	public static boolean link(Minecart m1, Minecart m2) {
		if (m1 == m2) return false;
		if (m1.isDead()) return false;
		if (m2.isDead()) return false;
		return link(MinecartMember.convert(m1), MinecartMember.convert(m2));
	}
	public static boolean link(MinecartMember m1, MinecartMember m2) {
		if (m1 == null || m2 == null || m1 == m2) return false;
		if (m1.dead || m2.dead) return false;
		MinecartGroup g1 = m1.getGroup();
		MinecartGroup g2 = m2.getGroup();
		//max links per update
		if (g1 != g2) {
			if (m1.isDerailed() || m2.isDerailed()) return false;
			if (OfflineGroupManager.wasInGroup(m1.uniqueId)) return false;
			if (OfflineGroupManager.wasInGroup(m2.uniqueId)) return false;
			//Can the two groups bind?
			TrainProperties prop1 = g1.getProperties();
			TrainProperties prop2 = g2.getProperties();
			if (!prop1.getLinking() || !prop2.getLinking()) {
				return false;
			}

			//Is a powered minecart required?
			if (prop1.requirePoweredMinecart || prop2.requirePoweredMinecart) {
				if (g1.size(Material.POWERED_MINECART) == 0 && g2.size(Material.POWERED_MINECART) == 0) {
					return false;
				}
			}

			//Can the minecarts reach the other?
			if (!MinecartMember.isTrackConnected(m1, m2)) return true;

			//append group1 before or after group2?
			int m1index = g1.indexOf(m1);
			int m2index = g2.indexOf(m2);

			//Validate
			if (!g2.canConnect(m1, m2index) || !g1.canConnect(m2, m1index)) {
				return false;
			}

			//Event
			if (GroupLinkEvent.call(g1, g2).isCancelled()) return false;

			//Transfer properties
			if (g1.size() > g2.size()) {
				g2.getProperties().load(g1.getProperties());
			}

			//Finally link
			if (m1index == 0 && m2index == 0) {					
				Collections.reverse(g1);
				g2.addAll(0, g1);
			} else if (m1index == 0 && m2index == g2.size() - 1) {
				g2.addAll(g1);
			} else if (m1index == g1.size() - 1 && m2index == 0) {
				g2.addAll(0, g1);
			} else {
				return false;
			}

			//Clear targets and active signs
			g1.clearActiveSigns();
			g2.clearActiveSigns();

			//Re-activate the signs underneath the train
			for (MinecartMember mm : g2) {
				for (Block sign : mm.getActiveSigns()) {
					g2.setActiveSign(sign, true);
				}
			}

			//Correct the yaw and order
			g2.getAverageForce();
			g2.updateDirection();

			g1.remove();
			m2.playLinkEffect();
			return true;
		}
		return false;
	}
}
