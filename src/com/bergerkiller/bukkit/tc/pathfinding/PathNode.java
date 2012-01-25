package com.bergerkiller.bukkit.tc.pathfinding;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.utils.BlockLocation;

public class PathNode {
	private static Map<String, PathNode> nodes = new HashMap<String, PathNode>();
	public static PathNode find(PathNode from, final String name) {
		PathNode node = get(name);
		if (node != null) return node;
		return from.findNode(name);
	}
	public static PathNode get(final String name) {
		return nodes.get(name);
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
		}
		return node;
	}
	
	private PathNode(final String name, final BlockLocation location) {
		this.location = location;
		this.name = name;
		this.explored = false;
	}
	
	public boolean explored;
	public final String name;
	public final BlockLocation location;
	public final Map<PathNode, PathConnection> neighbours = new HashMap<PathNode, PathConnection>(2);
	private final Map<PathNode, PathConnection> connections = new HashMap<PathNode, PathConnection>();
	public PathConnection getConnection(PathNode node) {
		return node == null ? null : connections.get(node);
	}
	public PathConnection getConnection(String name) {
		return this.getConnection(get(name));
	}
	
	public PathConnection createNeighbourConnection(final PathNode to, final double distance, final BlockFace direction) {
		PathConnection conn = this.createConnection(to, distance, direction);
		if (conn == null) return null;
		this.neighbours.put(to, conn);
		return conn;
	}
	public PathConnection createConnection(final PathNode to, final double distance, final BlockFace direction) {
		if (to == null) return null;
		PathConnection conn = this.getConnection(to);
		if (conn == null || conn.distance > distance) {
			conn = new PathConnection(distance, direction, to);
			this.connections.put(to, conn);
			//make sure all neighbours get informed of this connection
			for (Map.Entry<PathNode, PathConnection> entry : this.neighbours.entrySet()) {
				double newdistance = entry.getValue().distance + distance + 1;
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
		if (this.explored) {
			return null;
		} else {
			this.explore();
			PathNode found;
			for (PathNode node : this.neighbours.keySet()) {
				found = node.findNode(name);
				if (found != null) return found;
			}
			return null;
		}
	}

	public void explore() {
		this.explore(BlockFace.NORTH);
		this.explore(BlockFace.EAST);
		this.explore(BlockFace.SOUTH);
		this.explore(BlockFace.WEST);
		this.explored = true;
	}
	public void explore(BlockFace dir) {
		if (this.location == null) return;
		Block tmpblock = this.location.getBlock();
		if (tmpblock == null) return;
		tmpblock = TrackMap.getNext(tmpblock, dir);
		TrackMap map = new TrackMap(tmpblock, dir);
		String newdest;
		BlockLocation location;
		while (tmpblock != null){
			for (Block signblock : map.getAttachedSignBlocks()) {
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
					PathNode to = new PathNode(newdest, location);
					this.createNeighbourConnection(to, map.getTotalDistance() + 1, dir);
					return;
				}
			}
			tmpblock = map.next();
		}
	}

}
