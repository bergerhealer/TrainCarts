package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.config.ConfigurationNode;
import com.bergerkiller.bukkit.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;

public class Destinations {
	private static final int maxPathLength = 100000;
	private static final String destinationsFile = "destinations.yml";
	private static HashSet<String> checked = new HashSet<String>(); //used to prevent infinite loops
	private static HashMap<String, Destinations> properties = new HashMap<String, Destinations>();
	public static Destinations get(String destname) {
		if (destname == null) return null;
		Destinations prop = properties.get(destname);
		if (prop == null) {
			prop = new Destinations(destname);
			properties.put(destname, prop);
		}
		return prop;
	}
	public static boolean exists(String destname) {
		return properties.containsKey(destname);
	}
	public static void clear(){
		properties.clear();
	}
	public static void load() {
		FileConfiguration config = new FileConfiguration(TrainCarts.plugin, destinationsFile);
		config.load();
		for (ConfigurationNode node : config.getNodes()) {
			get(node.getName()).load(node);
		}
	}
	public static void save() {
		FileConfiguration config = new FileConfiguration(TrainCarts.plugin, destinationsFile);
		for (Destinations prop : properties.values()) {
			prop.save(config.getNode(prop.getDestName()));
		}
		config.save();
	}
	public static void init() {
		load();
	}
	public static void deinit() {
		save();
		checked = null;
		properties = null;
	}

	private static class Connection {
		public Connection(BlockFace dir, int dist) {
			this.dir = dir;
			this.dist = dist;
		}
		public BlockFace dir;
		public int dist;
	}
	
	/**
	 * Finds the direction to go to from currloc to reach destname.
	 * This works by looking up the wanted destination in the destinations file,
	 * then generating this information if not available yet by recursively asking all
	 * known possible destinations if they can reach this destination.
	 * @param destname The wanted destination.
	 * @param currloc The current (rail block) location.
	 * @return The direction to go in from currloc to reach destname, or NORTH if unknown.
	 */
	public static BlockFace getDir(String destname, Block curr){
		String thistag = Util.blockToString(curr);
		Destinations dest = get(thistag);
		if (dest == null) return BlockFace.NORTH;
		dest.explore();
		Connection r = dest.getDir(destname);
		checked.clear();
		return r.dir;
	}
	
	private String destname;
	public HashMap<String, Connection> dests = new HashMap<String, Connection>(); //all possible connections
	public HashSet<String> neighbours = new HashSet<String>(); //directly connected

	private Destinations(String destname) {
		this.destname = destname;
	}
	
	/**
	 * Finds the direction to go to from this destination to reach reqname.
	 * This works by looking up the wanted destination, then generating this information
	 * if not available yet by recursively asking all known destinations
	 * if they can reach this destination.
	 * @param reqname The wanted destination.
	 * @return The direction to go in from currloc to reach destname, or BlockFace.UP if unknown.
	 */
	public Connection getDir(String reqname){
		//is this us? return DOWN;
		if (reqname.equals(this.destname)) return new Connection(BlockFace.DOWN, 0);
		//explore first if not explored yet
		if (this.neighbours.isEmpty()){
			this.explore();
		}
		//ask the neighbours what they know
		if (checked.add(this.destname)) this.askNeighbours(reqname);
		//return what we know
		Connection n = this.dests.get(reqname);
		if (n != null) return n;
		return new Connection(BlockFace.UP, maxPathLength);
	}

	private void askNeighbours(String reqname) {
		for (String neigh : this.neighbours){
			if (neigh.equals(this.destname)) continue; //skip self
			Connection connection = this.dests.get(neigh);
			Destinations destination = get(neigh); //get this neighbour
			destination.getDir(reqname); //make sure this node is explored
			for (Map.Entry<String, Connection> dest : destination.dests.entrySet()) {
				if (dest.getKey().equals(this.destname)) continue; //skip self
				this.updateDestinationDistance(dest.getKey(), connection.dir, dest.getValue().dist + connection.dist + 1);
			}
		}
	}

	/**
	 * Explores in the given direction, until out of rails or either
	 * a tag switcher or destination is found. Adds the first found
	 * to the list for the given direction.
	 * @param dir Direction to explore in. Only works for NORTH/EAST/SOUTH/WEST.
	 */
	private void explore(BlockFace dir){
		Block tmpblock = Util.stringToBlock(destname);
		if (tmpblock == null) return;
		tmpblock = TrackMap.getNext(tmpblock, dir);
		TrackMap map = new TrackMap(tmpblock, dir);
		String newdest;
		while (tmpblock != null){
			for (Block signblock : map.getAttachedSignBlocks()) {
				SignActionEvent event = new SignActionEvent(signblock);
				if (event.getMode() != SignActionMode.NONE) {
					newdest = "";
					if (event.isType("tag", "switcher")){
						newdest = Util.blockToString(tmpblock);
					} else if (event.isType("destination")){
						newdest = event.getLine(2);
					}
					if (newdest.equals(this.destname)) continue;
					if (newdest.isEmpty()) continue;
					//finished, we found our first target
					this.neighbours.add(newdest);
					this.updateDestinationDistance(newdest, dir, map.getTotalDistance() + 1);
					return;
				}
			}
			tmpblock = map.next();
		}
	}
	private void explore() {
		this.explore(BlockFace.NORTH);
		this.explore(BlockFace.EAST);
		this.explore(BlockFace.SOUTH);
		this.explore(BlockFace.WEST);
	}

	/**
	 * Checks if this destination calculation is faster than all
	 * currently known ones, and if yes saves it, propagating the
	 * change to all connected nodes.
	 * @param newdest Name of the destination to check.
	 * @param newdir Direction the destination is in, with this distance.
	 * @param newdist Distance the destination is in, with this direction.
	 */
	private boolean updateDestinationDistance(final String name, final BlockFace direction, final int distance) {
		Connection n = this.dests.get(name);
		if (n == null) {
			this.dests.put(name, new Connection(direction, distance));
			return true;
		} else if (n.dist < distance) {
			return false;
		} else {
			n.dist = distance;
			n.dir = direction;
			return true;
		}
	}

	public String getDestName() {
		return this.destname;
	}

	public void load(ConfigurationNode node) {
		this.neighbours = new HashSet<String>(node.getList("neighbours", String.class));
		for (String k : node.getKeys()) {
			if (k.equals("neighbours")) continue; //skip neighbours
			BlockFace bf = BlockFace.UP;
			String dir = node.get(k + ".dir", "NORTH");
			if (dir.equals("NORTH")) bf = BlockFace.NORTH;
			if (dir.equals("EAST")) bf = BlockFace.EAST;
			if (dir.equals("SOUTH")) bf = BlockFace.SOUTH;
			if (dir.equals("WEST")) bf = BlockFace.WEST;
			this.dests.put(k, new Connection(bf, node.get(k + ".dist", maxPathLength)));
		}
	}
	public void save(ConfigurationNode node) {
		node.set("neighbours", new ArrayList<String>(this.neighbours));
		for (Map.Entry<String, Connection> entry : this.dests.entrySet()){
			node.set(entry.getKey() + ".dir", entry.getValue().dir.toString());
			node.set(entry.getKey() + ".dist", entry.getValue().dist);
		}
	}
}
