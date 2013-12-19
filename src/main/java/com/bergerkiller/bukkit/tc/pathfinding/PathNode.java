package com.bergerkiller.bukkit.tc.pathfinding;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.collections.BlockSet;
import com.bergerkiller.bukkit.common.config.CompressedDataReader;
import com.bergerkiller.bukkit.common.config.CompressedDataWriter;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class PathNode {
	private static BlockMap<PathNode> blockNodes = new BlockMap<PathNode>();
	private static Map<String, PathNode> nodes = new HashMap<String, PathNode>();
	public static void clearAll() {
		nodes.clear();
		blockNodes.clear();
	}

	/**
	 * Re-calculates all path nodes from scratch
	 */
	public static void reroute() {
		BlockSet blocks = new BlockSet();
		blocks.addAll(blockNodes.keySet());
		clearAll();
		String name;
		SignActionEvent info;
		for (BlockLocation location : blocks) {
			name = location.toString();
			// Destination sign? If so, fix up the name
			Block block = location.getBlock();
			if (block == null) {
				continue;
			}
			for (Block signBlock : Util.getSignsFromRails(block)) {
				info = new SignActionEvent(signBlock);
				if (info.getSign() != null && info.isType("destination")) {
					name = info.getLine(2);
					break;
				}
			}
			getOrCreate(name, location);
		}
	}

	public static PathNode get(Block block) {
		if (block == null) {
			return null;
		}
		return blockNodes.get(block);
	}
	public static PathNode get(final String name) {
		return nodes.get(name);
	}
	public static PathNode remove(Block railsblock) {
		if (railsblock == null) return null;
		PathNode node = blockNodes.remove(railsblock);
		if (node != null) node.remove();
		return node;
	}
	public static PathNode getOrCreate(SignActionEvent event) {
		if (event.isType("destination")) {
			//get this destination name
			return getOrCreate(event.getLine(2), event.getRails());
		} else {
			//check if the current train or cart has a destination
			if (event.isCartSign()) {
				if (!event.hasMember() || !event.getMember().getProperties().hasDestination()) {
					return null;
				}
			} else if (event.isTrainSign()) {
				if (!event.hasGroup() || !event.getGroup().getProperties().hasDestination()) {
					return null;
				}
			}
			//create from location
			return getOrCreate(event.getRails());
		}
	}
	public static PathNode getOrCreate(Block location) {
		return getOrCreate(new BlockLocation(location));
	}
	public static PathNode getOrCreate(BlockLocation location) {
		return getOrCreate(location.toString(), location);
	}
	public static PathNode getOrCreate(final String name, Block location) {
		return getOrCreate(name, new BlockLocation(location));
	}
	public static PathNode getOrCreate(final String name, final BlockLocation location) {
		if (LogicUtil.nullOrEmpty(name) || location == null) {
			return null;
		}
		PathNode node = get(name);
		if (node != null) {
			return node;
		}
		node = blockNodes.get(location);
		if (node == null) {
			// Create a new node
			node = new PathNode(name, location);
			node.addToMapping();
			PathProvider.schedule(node);
		} else {
			// Add the name to the existing node
			node.addName(name);
		}
		return node;
	}

	private PathNode(final String name, final BlockLocation location) {
		this.location = location;
		if (!LogicUtil.nullOrEmpty(name)) {
			LogicUtil.addArray(this.names, name.split("\n", -1));
		}
	}

	public int index;
	public final BlockLocation location;
	private final Set<String> names = new HashSet<String>();
	private final List<PathConnection> neighbors = new ArrayList<PathConnection>(3);
	private int lastDistance;
	private PathConnection lastTaken;

	/**
	 * Tries to find a connection from this node to the node specified
	 * 
	 * @param destination name of the node to find
	 * @return A connection, or null if none could be found
	 */
	public PathConnection findConnection(String destination) {
		PathNode node = get(destination);
		return node == null ? null : findConnection(node);
	}

	/**
	 * Tries to find a connection from this node to the node specified
	 * 
	 * @param destination node to find
	 * @return A connection, or null if none could be found
	 */
	public PathConnection findConnection(PathNode destination) {
		for (PathNode node : nodes.values()) {
			node.lastDistance = Integer.MAX_VALUE;
			node.lastTaken = null;
		}
		int maxDistance = Integer.MAX_VALUE;
		int distance;
		final PathConnection from = new PathConnection(this, 0, BlockFace.SELF);
		for (PathConnection connection : this.neighbors) {
			distance = getDistanceTo(from, connection, 0, maxDistance, destination);
			if (maxDistance > distance) {
				maxDistance = distance;
				this.lastTaken = connection;
			}
		}
		if (this.lastTaken == null) {
			return null;
		} else {
			return new PathConnection(destination, maxDistance, this.lastTaken.direction);
		}
	}

	/**
	 * Tries to find the exact route (all nodes) to reach a destination from this node
	 * 
	 * @param destination to reach
	 * @return the route taken, or an empty array if none could be found
	 */
	public PathNode[] findRoute(PathNode destination) {
		if (findConnection(destination) == null) {
			return new PathNode[0];
		}
		List<PathNode> route = new ArrayList<PathNode>();
		route.add(this);
		PathConnection conn = this.lastTaken;
		while (conn != null) {
			route.add(conn.destination);
			conn = conn.destination.lastTaken;
		}
		return route.toArray(new PathNode[0]);
	}

	private static int getDistanceTo(PathConnection from, PathConnection conn, int currentDistance, int maxDistance, PathNode destination) {
		final PathNode node = conn.destination;
		currentDistance += conn.distance;
		// Consider taking turns as one distance longer
		// This avoids the excessive use of turns in 2-way 'X' intersections
		if (from.direction != conn.direction) {
			currentDistance++;
		}
		if (destination == node) {
			return currentDistance;
		}
		// Initial distance check before continuing
		if (node.lastDistance < currentDistance || currentDistance > maxDistance) {
			return Integer.MAX_VALUE;
		}
		node.lastDistance = currentDistance;
		// Check all neighbors and obtain the lowest distance recursively
		int distance;
		for (PathConnection connection : node.neighbors) {
			distance = getDistanceTo(conn, connection, currentDistance, maxDistance, destination);
			if (maxDistance > distance) {
				maxDistance = distance;
				node.lastTaken = connection;
			}
		}
		return maxDistance;
	}

	/**
	 * Adds a neighbour connection to this node
	 * 
	 * @param to the node to make a connection with
	 * @param distance of the connection
	 * @param direction of the connection
	 * @return The connection that was made
	 */
	public PathConnection addNeighbour(final PathNode to, final int distance, final BlockFace direction) {
		PathConnection conn;
		Iterator<PathConnection> iter = this.neighbors.iterator();
		while (iter.hasNext()) {
			conn = iter.next();
			if (conn.destination == to) {
				if (conn.distance <= distance) {
					// Lower distance is contained - all done
					return conn;
				} else {
					// Higher distance is contained - remove old element
					iter.remove();
					break;
				}
			}
		}
		// Add a new one
		conn = new PathConnection(to, distance, direction);
		this.neighbors.add(conn);
		return conn;
	}

	public void clear() {
		this.neighbors.clear();
		for (PathNode node : nodes.values()) {
			Iterator<PathConnection> iter = node.neighbors.iterator();
			while (iter.hasNext()) {
				if (iter.next().destination == this) {
					iter.remove();
				}
			}
		}
	}

	/**
	 * Removes a single available name that was usable by this Path Node.
	 * If no names are left, the node is removed entirely.
	 * 
	 * @param name to remove
	 */
	public void removeName(String name) {
		if (!this.names.remove(name)) {
			return;
		}
		nodes.remove(name);
		if (PathProvider.DEBUG_MODE) {
			String dbg = "NODE " + location + " NO LONGER HAS NAME " + name;
			if (this.names.isEmpty()) {
				dbg += " AND IS NOW BEING REMOVED (NO NAMES)";
			}
			System.out.println(dbg);
		}
		if (this.names.isEmpty()) {
			this.remove();
		}
	}

	/**
	 * Removes this node and all names associated with it.
	 */
	public void remove() {
		this.clear();
		//remove globally
		for (String name : this.names) {
			nodes.remove(name);
		}
		blockNodes.remove(this.location);
	}

	/**
	 * Checks whether this node contains a name
	 * 
	 * @param name to check
	 * @return True if the name is contained, False if not
	 */
	public boolean containsName(String name) {
		return this.names.contains(name);
	}

	/**
	 * Checks whether all this node contains is a switcher sign,
	 * and no other signs (destinations) are set.
	 * 
	 * @return True if only a switcher sign is contained, False if not
	 */
	public boolean containsOnlySwitcher() {
		return this.names.size() == 1 && this.containsSwitcher();
	}

	/**
	 * Checks whether this node is covered by a switcher sign
	 * 
	 * @return True if a switcher sign is contained, False if not
	 */
	public boolean containsSwitcher() {
		return this.names.contains(this.location.toString());
	}

	/**
	 * Gets a name of this Node, using get on this name will result in this node being returned.
	 * Returns null if this node contains no name (and is invalid)
	 * 
	 * @return Reverse-lookup-able Node name
	 */
	public String getName() {
		if (this.names.isEmpty()) {
			return null;
		} else {
			return this.names.iterator().next();
		}
	}

	/**
	 * Gets the Display name of this Path Node, which covers the names given or the location
	 * if this is an unnamed node.
	 * 
	 * @return Node display name
	 */
	public String getDisplayName() {
		String locDName = "[" + this.location.x + "/" + this.location.y + "/" + this.location.z + "]";
		// No name at all - use location as name
		if (this.names.isEmpty()) {
			return locDName;
		}

		// Get all names except the location name
		String locName = this.location.toString();
		if (this.names.size() == 1) {
			// Show this one name
			return this.names.iterator().next().replace(locName, locDName);
		} else {
			// Show a list of names
			StringBuilder builder = new StringBuilder(this.names.size() * 15);
			builder.append('{');
			for (String name : this.names) {
				if (builder.length() > 1) {
					builder.append("/");
				}
				builder.append(name.replace(locName, locDName));
			}
			builder.append('}');
			return builder.toString();
		}
	}

	@Override
	public String toString() {
		return this.getDisplayName();
	}

	public void addName(String name) {
		if (this.names.add(name)) {
			nodes.put(name, this);
		}
	}

	private void addToMapping() {
		for (String name : this.names) {
			nodes.put(name, this);
		}
		blockNodes.put(this.location, this);
	}

	public static void deinit() {
		clearAll();
	}

	public static void init(String filename) {
		new CompressedDataReader(filename) {
			public void read(DataInputStream stream) throws IOException {
				//initializing the nodes
				int count = stream.readInt();
				nodes = new HashMap<String, PathNode>(count);
				blockNodes.clear();
				PathNode[] parr = new PathNode[count];
				for (int i = 0; i < count; i++) {
					String name = stream.readUTF();
					BlockLocation loc = new BlockLocation(stream.readUTF(), stream.readInt(), stream.readInt(), stream.readInt());
					if (name.isEmpty()) {
						name = loc.toString();
					}
					parr[i] = new PathNode(name, loc);
					parr[i].addToMapping();
				}
				//generating connections
				for (PathNode node : parr) {
					int ncount = stream.readInt();
					for (int i = 0 ; i < ncount; i++) {
						node.neighbors.add(new PathConnection(parr[stream.readInt()], stream));
					}
				}
			}
		}.read();
	}
	public static void save(String filename) {
		new CompressedDataWriter(filename) {
			public void write(DataOutputStream stream) throws IOException {
				stream.writeInt(nodes.size());
				//generate indices
				int i = 0;
				for (PathNode node : nodes.values()) {
					node.index = i;
					if (node.containsOnlySwitcher()) {
						stream.writeUTF("");
					} else {
						stream.writeUTF(StringUtil.join("\n", node.names));
					}
					stream.writeUTF(node.location.world);
					stream.writeInt(node.location.x);
					stream.writeInt(node.location.y);
					stream.writeInt(node.location.z);
					i++;
				}
				//write out connections
				for (PathNode node : nodes.values()) {
					stream.writeInt(node.neighbors.size());
					for (PathConnection conn : node.neighbors) {
						conn.writeTo(stream);
					}
				}
			}
		}.write();
	}
}
