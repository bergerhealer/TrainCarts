package com.bergerkiller.bukkit.tc.pathfinding;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.collections.BlockSet;
import com.bergerkiller.bukkit.common.config.CompressedDataReader;
import com.bergerkiller.bukkit.common.config.CompressedDataWriter;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
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
	public static PathNode remove(final String name) {
		PathNode node = nodes.remove(name);
		if (node != null) node.remove();
		return node;
	}
	public static PathNode remove(Block railsblock) {
		if (railsblock == null) return null;
		PathNode node = blockNodes.remove(railsblock);
		if (node != null) node.remove();
		return node;
	}
	public static PathNode clear(Block railsblock) {
		if (railsblock == null) return null;
		PathNode node = blockNodes.get(railsblock);
		if (node != null) node.clear();
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
		if (node == null) {
			// Create a new node
			node = new PathNode(name, location);
			nodes.put(node.name, node);
			blockNodes.put(location, node);
			// Start exploration
			Block nodeBlock = location.getBlock();
			if (nodeBlock != null) {
				Block startBlock;
				for (BlockFace dir : FaceUtil.AXIS) {
					startBlock = Util.getRailsBlock(nodeBlock.getRelative(dir));
					if (startBlock == null) {
						continue;
					}
					PathProvider.schedule(node, startBlock, dir);
				}
			}
		}
		return node;
	}
	
	private PathNode(final String name, final BlockLocation location) {
		this.location = location;
		this.name = name == null ? location.toString() : name;
	}

	public int index;
	public final String name;
	public final BlockLocation location;
	private final List<PathConnection> neighbors = new ArrayList<PathConnection>(3);
	private int lastDistance;
	private PathConnection lastTaken;

	/**
	 * Gets whether this Path Node has a special name assigned which differs from the default (location)
	 * 
	 * @return True if named, False if not
	 */
	public boolean isNamed() {
		return !this.name.equals(this.location.toString());
	}

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

	public void remove() {
		this.clear();
		//remove globally
		nodes.remove(this.name);
		blockNodes.remove(this.location);
	}

	@Override
	public String toString() {
		return "[" + this.name + "]";
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
					nodes.put(parr[i].name, parr[i]);
					blockNodes.put(loc, parr[i]);
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
					if (node.name.equals(node.location.toString())) {
						stream.writeShort(0);
					} else {
						stream.writeUTF(node.name);
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
