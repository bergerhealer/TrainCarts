package com.bergerkiller.bukkit.tc.pathfinding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.config.ConfigurationNode;
import com.bergerkiller.bukkit.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.utils.BlockLocation;
import com.bergerkiller.bukkit.tc.utils.BlockMap;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;
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
		PathNode node = blockNodes.remove(railsblock);
		if (node != null) node.remove();
		return node;
	}
	public static PathNode clear(Block railsblock) {
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
		PathNode node = get(name);
		if (node == null) {
			node = new PathNode(name, location);
			nodes.put(node.name, node);
			blockNodes.put(location, node);
		}
		return node;
	}
	
	private PathNode(ConfigurationNode node) {
		this.location = BlockLocation.parseLocation(node.getName());
		if (this.location == null) {
			this.name = null;
			this.hasName = false;
		} else {
			this.name = node.get("name", this.location.toString());
			this.hasName = !this.name.equals(this.location.toString());
		}
	}
	private PathNode(final String name, final BlockLocation location) {
		this.location = location;
		this.name = name;
		this.hasName = !this.name.equals(this.location.toString());
	}
	
	public final String name;
	public final BlockLocation location;
	private final boolean hasName;
	public final Map<PathNode, PathConnection> neighboursFrom = new HashMap<PathNode, PathConnection>(2);
	public final Map<PathNode, PathConnection> neighboursTo = new HashMap<PathNode, PathConnection>(2);
	private final Map<PathNode, PathConnection> connections = new HashMap<PathNode, PathConnection>();
		
	public boolean containsConnection(PathNode node) {
		return this.connections.containsKey(node);
	}
	
	public PathConnection getConnection(PathNode node) {
		return node == null ? null : connections.get(node);
	}
	public PathConnection getConnection(String name) {
		return this.getConnection(get(name));
	}
	
	public PathConnection createNeighbourConnection(final PathNode to, final int distance, final BlockFace direction) {
		PathConnection conn = this.createConnection(to, distance, direction);
		if (conn == null) return null;
		this.neighboursTo.put(to, conn);
		to.neighboursFrom.put(this, conn);
		return conn;
	}
	public PathConnection createConnection(final PathNode to, final int distance, final BlockFace direction) {
		if (to == null) return null;
		PathConnection conn = this.getConnection(to);
		if (conn == null || conn.distance > distance) {
			conn = new PathConnection(distance, direction, to);
			this.connections.put(to, conn);
			//make sure all neighbours get informed of this connection
			for (Map.Entry<PathNode, PathConnection> entry : this.neighboursFrom.entrySet()) {
				int newdistance = entry.getValue().distance + distance + 1;
				entry.getKey().createConnection(to, newdistance, entry.getValue().direction);
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
			if (found != null) return found;
		}
		return null;
	}
	
	public void clear() {
		for (PathNode node : nodes.values()) {
			if (node.containsConnection(this)) {
				node.clear();
			}
		}
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
		return "[" + (this.hasName ? "" : this.name + "/") + this.location.toString() + "]";
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
		tmpblock = TrackIterator.getNextTrack(tmpblock, dir);
		
		TrackIterator iter = new TrackIterator(tmpblock, dir);

		String newdest;
		BlockLocation location;
		while (iter.hasNext()) {
			tmpblock = iter.next();
			for (Block signblock : BlockUtil.getSignsAttached(tmpblock)) {
				SignActionEvent event = new SignActionEvent(signblock);
				if (event.getMode() != SignActionMode.NONE) {
					if (event.isType("tag", "switcher")){
						location = new BlockLocation(tmpblock);
						newdest = location.toString();
					} else if (event.isType("destination")) {
						location = new BlockLocation(tmpblock);
						newdest = event.getLine(2);
					} else if (event.isType("oneway")) {
						//allow us to continue or not?
						
						
						
						continue;
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

	private static final String destinationsFile = "destinations.yml";
	public static void init() {
		FileConfiguration config = new FileConfiguration(TrainCarts.plugin, destinationsFile);
		config.load();
		load(config);
	}
	public static void deinit() {
		FileConfiguration config = new FileConfiguration(TrainCarts.plugin, destinationsFile);
		save(config);
		config.save();
		clearAll();
	}
	public static void load(FileConfiguration config) {
		for (ConfigurationNode node : config.getNodes()) {
			PathNode pn = new PathNode(node);
			if (pn.location != null && pn.name != null) {
				nodes.put(pn.name, pn);
				blockNodes.put(pn.location, pn);
			}
		}
		for (PathNode pnode : nodes.values()) {
			pnode.load(config.getNode(pnode.location.toString()));
		}
	}
	public static void save(FileConfiguration config) {
		for (PathNode pnode : nodes.values()) {
			pnode.save(config.getNode(pnode.location.toString()));
		}
	}
	public void load(ConfigurationNode node) {
		node = node.getNode("neighbours");
		String directionname;
		for (ConfigurationNode neigh : node.getNodes()) {
			PathNode pnode = get(neigh.getName());
			if (pnode == null) continue; //not found
			Integer distance = neigh.get("dist", Integer.class);
			if (distance == null) continue; //invalid
			directionname = neigh.get("dir", String.class, null);
			if (directionname == null) continue; //invalid
			BlockFace direction = null;
			//parse direction
			if (directionname.equals("NORTH")) direction = BlockFace.NORTH;
			if (directionname.equals("EAST")) direction = BlockFace.EAST;
			if (directionname.equals("SOUTH")) direction = BlockFace.SOUTH;
			if (directionname.equals("WEST")) direction = BlockFace.WEST;
			if (direction == null) continue; //invalid
			//create
			this.createNeighbourConnection(pnode, distance, direction);
		}
	}
	public void save(ConfigurationNode node) {
		if (this.hasName) node.set("name", this.name);
		if (!this.neighboursTo.isEmpty()) {
			node = node.getNode("neighbours");
			for (Map.Entry<PathNode, PathConnection> entry : this.neighboursTo.entrySet()) {
				ConfigurationNode neigh = node.getNode(entry.getKey().name);
				neigh.set("dir", entry.getValue().direction.toString());
				neigh.set("dist", entry.getValue().distance);
			}
		}
	}
}
