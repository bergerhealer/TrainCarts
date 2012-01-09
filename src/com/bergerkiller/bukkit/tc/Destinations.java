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

	private static class Node {
		public Node(BlockFace dir, double dist) {
			this.dir = dir;
			this.dist = dist;
		}
		public BlockFace dir;
		public double dist;
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
		Node r = dest.getDir(destname);
		checked.clear();
		return r.dir;
	}
	
	private String destname;
	public HashMap<String, Node> dests = new HashMap<String, Node>(); //all possible connected nodes
	public HashSet<String> neighbours = new HashSet<String>(); 

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
	public Node getDir(String reqname){
		//is this us? return DOWN;
		if (reqname.equals(this.destname)) return new Node(BlockFace.DOWN, 0.0);
		//explore first if not explored yet
		if (this.neighbours.isEmpty()){
			this.explore();
		}
		//ask the neighbours what they know
		if (checked.add(this.destname)) this.askNeighbours(reqname);
		//return what we know
		Node n = this.dests.get(reqname);
		if (n != null) return n;
		return new Node(BlockFace.UP, 100000);
	}

	private void askNeighbours(String reqname) {
		for (String neigh : this.neighbours){
			if (neigh.equals(this.destname)) continue; //skip self
			Node node = this.dests.get(neigh);
			Destinations n = get(neigh); //get this neighbour
			n.getDir(reqname); //make sure this node is explored
			for (Map.Entry<String, Node> dest : n.dests.entrySet()) {
				if (dest.getKey() == this.destname) continue; //skip self
				updateDest(dest.getKey(), new Node(node.dir, dest.getValue().dist + node.dist + 1));
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
					this.updateDest(newdest, new Node(dir, map.getTotalDistance() + 1));
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
	private void updateDest(String newdest, Node newnode){
		if (newnode.dist >= 100000.0) return; //don't store failed calculations
		//if we already know about this destination, and we are not faster, ignore it.
        Node n = this.dests.get(newdest);
		if ((n != null) && (n.dist <= newnode.dist)) return;
		//save.
		this.dests.put(newdest, newnode);
	}

	public String getDestName() {
		return this.destname;
	}

	public void remove() {
		properties.remove(this.destname);
	}
	public void add() {
		properties.put(this.destname, this);
	}
	public void rename(String newdestname) {
		this.remove();
		this.destname = newdestname;
		properties.put(newdestname, this);
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
			this.dests.put(k, new Node(bf, node.get(k + ".dist", 100000.0)));
		}
	}
	public void load(Destinations source) {
		this.dests.putAll(source.dests);
	}
	public void save(ConfigurationNode node) {
		node.set("neighbours", new ArrayList<String>(this.neighbours));
		for (Map.Entry<String, Node> entry : this.dests.entrySet()){
			node.set(entry.getKey() + ".dir", entry.getValue().dir.toString());
			node.set(entry.getKey() + ".dist", entry.getValue().dist);
		}
	}
}
