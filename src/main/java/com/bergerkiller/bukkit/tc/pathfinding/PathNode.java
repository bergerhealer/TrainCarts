package com.bergerkiller.bukkit.tc.pathfinding;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
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
	private final Map<PathNode, PathConnection> neighbours = new HashMap<PathNode, PathConnection>(2);
	private int lastDistance;

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
		return this.findConnectionRecursive(0, destination);
	}

	private PathConnection findConnectionRecursive(int currentDistance, PathNode destination) {
		if (this.lastDistance < currentDistance) {
			return null;
		}
		this.lastDistance = currentDistance;
		PathConnection conn = this.neighbours.get(destination);
		if (conn == null) {
			int distance = Integer.MAX_VALUE;
			for (Map.Entry<PathNode, PathConnection> connection : this.neighbours.entrySet()) {
				final int neighDistance = currentDistance + connection.getValue().distance;
				PathConnection neighConn = connection.getKey().findConnectionRecursive(neighDistance, destination);
				if (neighConn != null) {
					final int toDist = neighDistance + neighConn.distance;
					if (toDist < distance) {
						conn = new PathConnection(distance = toDist, connection.getValue().direction, destination);
					}
				}
			}
		}
		return conn;
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
		PathConnection conn = neighbours.get(to);
		if (conn == null || conn.distance > distance) {
			conn = new PathConnection(distance, direction, to);
			neighbours.put(to, conn);
		}
		return conn;
	}

	public void clear() {
		this.neighbours.clear();
		for (PathNode node : nodes.values()) {
			node.neighbours.remove(this);
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
					PathConnection conn;
					int ncount = stream.readInt();
					for (int i = 0 ; i < ncount; i++) {
						conn = new PathConnection(stream, parr[stream.readInt()]);
						node.neighbours.put(conn.destination, conn);
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
					stream.writeInt(node.neighbours.size());
					for (PathConnection conn : node.neighbours.values()) {
						conn.writeTo(stream);
					}
				}
			}
		}.write();
	}
}
