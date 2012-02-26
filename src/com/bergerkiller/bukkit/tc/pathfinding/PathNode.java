package com.bergerkiller.bukkit.tc.pathfinding;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.BlockMap;
import com.bergerkiller.bukkit.common.config.CompressedDataReader;
import com.bergerkiller.bukkit.common.config.CompressedDataWriter;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlock;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

public class PathNode {
	private static Set<PathNode> findTraversed = new HashSet<PathNode>();
	private static BlockMap<PathNode> blockNodes = new BlockMap<PathNode>();
	private static Map<String, PathNode> nodes = new HashMap<String, PathNode>();
	public static void clearAll() {
		nodes.clear();
		blockNodes.clear();
	}
	public static PathNode find(PathNode from, final String name) {
		PathNode node = get(name);
		if (node != null && from.connections.containsKey(node)) {
			return node;
		} else {
			node = from.findNode(name);
			findTraversed.clear();
			return node;
		}
	}
	public static PathNode get(Block block) {
		if (block == null) return null;
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
		if (name == null || name.isEmpty()) return null;
		PathNode node = get(name);
		if (node == null) {
			node = new PathNode(name, location);
			nodes.put(node.name, node);
			blockNodes.put(location, node);
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
	public final Map<PathNode, PathConnection> neighboursFrom = new HashMap<PathNode, PathConnection>(2);
	public final Map<PathNode, PathConnection> neighboursTo = new HashMap<PathNode, PathConnection>(2);
	public final Map<PathNode, PathConnection> connections = new HashMap<PathNode, PathConnection>();
		
	public boolean containsConnection(PathNode node) {
		return this.connections.containsKey(node);
	}
	
	public PathConnection getConnection(PathNode node) {
		return node == null ? null : connections.get(node);
	}
	public PathConnection getConnection(String name) {
		return this.getConnection(get(name));
	}
	
	public PathConnection removeNeightbourConnection(final PathNode to) {
		PathConnection conn = this.neighboursTo.remove(to);
		to.neighboursFrom.remove(this);
		return conn;
	}
	
	public PathConnection createNeighbourConnection(final PathNode to, final int distance, final BlockFace direction) {
		PathConnection conn = this.createConnection(to, distance, direction);
		if (conn == null) return null;
		this.neighboursTo.put(to, conn);
		to.neighboursFrom.put(this, conn);
		return conn;
	}
	public PathConnection createConnection(final PathNode to, final int distance, final BlockFace direction) {
		if (to == null || to == this) return null;
		PathConnection conn = this.getConnection(to);
		if (conn == null || conn.distance > distance) {
			conn = new PathConnection(distance, direction, to);
			this.connections.put(to, conn);

			//make sure all neighbours get informed of this connection
			for (Map.Entry<PathNode, PathConnection> entry : this.neighboursFrom.entrySet()) {
				int newdistance = entry.getValue().distance + distance + 1;
				entry.getKey().createConnection(to, newdistance, entry.getValue().direction);
			}
			
			//any connections to make to other nodes contained in to?
			for (Map.Entry<PathNode, PathConnection> entry : to.connections.entrySet()) {
				int newdistance = entry.getValue().distance + distance + 1;
				this.createConnection(entry.getKey(), newdistance, direction);
			}
		}
		return conn;
	}

	public PathConnection findConnection(final String name) {
		PathConnection conn = this.getConnection(name);
		if (conn == null) {
			conn = this.getConnection(find(this, name));
		}
		return conn;
	}	
	
	private PathNode findNode(final String name) {
		if (this.name.equals(name)) return this;
		if (!findTraversed.add(this)) return null;
		if (this.neighboursTo.isEmpty()) this.explore();
		PathNode found;
		for (PathNode node : this.neighboursTo.keySet()) {
			found = node.findNode(name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}
	
	public void clear() {
		this.clearMapping();
		for (PathNode node : nodes.values()) {
			if (node.containsConnection(this)) {
				node.clearMapping();
			}
		}
	}
	private void clearMapping() {
		this.neighboursFrom.clear();
		this.neighboursTo.clear();
		this.connections.clear();
	}
	
	public void remove() {
		this.clear();
		//remove globally
		nodes.remove(this.name);
		blockNodes.remove(this.location);
	}

	public String toString() {
		return "[" + this.name + "]";
	}
	
	public void explore() {
		this.explore(BlockFace.NORTH);
		this.explore(BlockFace.EAST);
		this.explore(BlockFace.SOUTH);
		this.explore(BlockFace.WEST);
	}
	public void explore(final BlockFace dir) {
		if (this.location == null) return;
		Block tmpblock = this.location.getBlock();
		if (tmpblock == null) return;
		TrackIterator iter = new TrackIterator(tmpblock, dir);
		iter.next(); //ignore first (start) block

		String newdest;
		BlockLocation location;
		while (iter.hasNext()) {
			tmpblock = iter.next();
			for (Block signblock : Util.getSignsFromRails(tmpblock)) {
				SignActionEvent event = new SignActionEvent(signblock);
				if (event.getMode() != SignActionMode.NONE) {
					if (event.isType("tag", "switcher")){
						location = new BlockLocation(tmpblock);
						newdest = location.toString();
					} else if (event.isType("destination")) {
						location = new BlockLocation(tmpblock);
						newdest = event.getLine(2);
					} else if (event.isType("blocker")) {
						if (SignActionBlock.isHeadingTo(event, iter.currentDirection())) {
							return;
						} else {
							continue;
						}
					} else {
						continue;
					}
					if (newdest.isEmpty()) continue;
					if (newdest.equals(this.name)) continue;
					//finished, we found our first target - create connection
					PathNode to = getOrCreate(newdest, location);
					this.createNeighbourConnection(to, iter.getDistance() + 1, dir);
					return;
				}
			}
		}
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
					if (name.length() == 0) {
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
						node.neighboursTo.put(conn.destination, conn);
						conn.destination.neighboursFrom.put(node, conn);
						node.connections.put(conn.destination, conn);
					}
					
					ncount = stream.readInt();
					for (int i = 0 ; i < ncount; i++) {
						conn = new PathConnection(stream, parr[stream.readInt()]);
						node.connections.put(conn.destination, conn);
					}					
				}
			}
		}.read();
	}
	public static void deinit(String filename) {
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
					stream.writeInt(node.neighboursTo.size());
					for (PathConnection conn : node.neighboursTo.values()) {
					    conn.writeTo(stream);
					}
					stream.writeInt(node.connections.size() - node.neighboursTo.size());
					for (PathConnection conn : node.connections.values()) {
						if (node.neighboursTo.containsKey(conn.destination)) continue;
						conn.writeTo(stream);
					}
				}
			}
		}.write();
		clearAll();
	}
	
	
}
