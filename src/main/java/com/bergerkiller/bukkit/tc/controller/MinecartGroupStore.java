package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.GroupCreateEvent;
import com.bergerkiller.bukkit.tc.events.GroupLinkEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.utils.TrackWalkIterator;

public class MinecartGroupStore extends ArrayList<MinecartMember<?>> {
	private static final long serialVersionUID = 1;
	protected static HashSet<MinecartGroup> groups = new HashSet<MinecartGroup>();
	private static List<MinecartGroup> groupTickBuffer = new ArrayList<MinecartGroup>(5);

	/**
	 * Called onPhysics for all Minecart Groups who didn't get ticked in the previous run
	 * This is a sort of hack against the bugged issues on some server implementations
	 */
	public static void doFixedTick() {
		groupTickBuffer.clear();
		groupTickBuffer.addAll(groups);
		try {
			for (MinecartGroup group : groupTickBuffer) {
				if (!group.ticked.clear()) {
					// Ticked was False, tick it now
					group.doPhysics();
					// Update the positions of the entities in the world(s)
					for (MinecartMember<?> member : group) {
						member.getEntity().doPostTick();
					}
				}
			}
		} catch (Throwable t) {
			TrainCarts.plugin.handle(t);
		}
	}

	public static MinecartGroup create() {
		MinecartGroup g = new MinecartGroup();
		groups.add(g);
		return g;
	}
	public static MinecartGroup create(MinecartMember<?>... members) {
		return create(null, members);
	}
	public static MinecartGroup create(String name, MinecartMember<?>... members) {
		// There is not a group with this name already?
		MinecartGroup g = new MinecartGroup();
		if (name != null) {
			g.setProperties(TrainProperties.get(name));
		}
		for (MinecartMember<?> member : members) {
			if (member != null && !member.getEntity().isDead()) {
				member.unloaded = false;
				g.add(member);
			}
		}
		g.updateDirection();
		g.getAverageForce();
		groups.add(g);
		GroupCreateEvent.call(g);
		return g;
	}

	public static MinecartGroup spawn(Location[] at, EntityType... types) {
		if (at.length != types.length || at.length == 0) return null;
		MinecartGroup g = new MinecartGroup();
		for (int i = 0; i < types.length; i++) {
			g.add(MinecartMemberStore.spawn(at[i], types[i]));
		}
		groups.add(g);
		GroupCreateEvent.call(g);
		return g;
	}
	public static MinecartGroup spawn(Block startblock, BlockFace direction, EntityType... types) {
		return spawn(startblock, direction, Arrays.asList(types));
	}
	public static MinecartGroup spawn(Block startblock, BlockFace direction, List<EntityType> types) {
		Location[] destinations = TrackWalkIterator.walk(startblock, direction, types.size(), TrainCarts.cartDistance);
		if (types.size() != destinations.length || destinations.length == 0) return null;
		MinecartGroup g = new MinecartGroup();
		for (int i = 0; i < destinations.length; i++) {
			g.add(MinecartMemberStore.spawn(destinations[destinations.length - i - 1], types.get(i)));
		}
		groups.add(g);
		GroupCreateEvent.call(g);
		return g;
	}

	/**
	 * Finds all the Minecart Groups that match the name with the expression given
	 * 
	 * @param expression to match to
	 * @return a Collection of MinecartGroup that match
	 */
	public static Collection<MinecartGroup> matchAll(String expression) {
		List<MinecartGroup> rval = new ArrayList<MinecartGroup>();
		if (expression != null && !expression.isEmpty()) {
			String[] elements = expression.split("\\*");
			boolean first = expression.startsWith("*");
			boolean last = expression.endsWith("*");
			for (MinecartGroup group : groups) {
				if (group.getProperties().matchName(elements, first, last)) {
					rval.add(group);
				}
			}
		}
		return rval;
	}
	public static Set<MinecartGroup> getGroupsUnsafe() {
		return groups;
	}
	public static MinecartGroup[] getGroups() {
		return groups.toArray(new MinecartGroup[0]);
	}
	public static MinecartGroup get(Entity e) {
		final MinecartMember<?> mm = MinecartMemberStore.get(e);
		return mm == null ? null : mm.getGroup();
	}
	public static MinecartGroup get(TrainProperties prop) {
		for (MinecartGroup group : groups) {
			if (group.getProperties() == prop) return group;
		}
		return null;
	}

	public static boolean link(MinecartMember<?> m1, MinecartMember<?> m2) {
		if (m1 == null || m2 == null || m1 == m2 || !m1.isInteractable() || !m2.isInteractable()) {
			return false;
		}
		MinecartGroup g1 = m1.getGroup();
		MinecartGroup g2 = m2.getGroup();
		//max links per update
		if (g1 != g2) {
			if (m1.isDerailed() || m2.isDerailed()) {
				return false;
			}
			//Can the two groups bind?
			TrainProperties prop1 = g1.getProperties();
			TrainProperties prop2 = g2.getProperties();

			//Is a powered minecart required?
			if (prop1.requirePoweredMinecart || prop2.requirePoweredMinecart) {
				if (g1.size(EntityType.MINECART_FURNACE) == 0 && g2.size(EntityType.MINECART_FURNACE) == 0) {
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

			//Transfer properties if needed
			if (g1.size() > g2.size() || (g1.size() == g2.size() && g1.getTicksLived() > g2.getTicksLived())) {
				// Transfer properties
				g2.getProperties().load(g1.getProperties());
				// Transfer name, assigning a random name to the removed properties
				String name = g1.getProperties().getTrainName();
				g1.getProperties().setName(TrainProperties.generateTrainName());
				g2.getProperties().setName(name);
			}

			//Clear targets and active signs
			g1.getBlockTracker().clear();
			g2.getBlockTracker().clear();

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

			//Correct the yaw and order
			g2.getAverageForce();
			g2.updateDirection();
			g2.getBlockTracker().updatePosition();

			g1.remove();
			m2.playLinkEffect();
			return true;
		}
		return false;
	}
}
