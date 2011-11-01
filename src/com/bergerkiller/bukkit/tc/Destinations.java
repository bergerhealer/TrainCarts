package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class Destinations {
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
	public static void load(String filename) {
		Configuration config = new Configuration(filename);
		config.load();
		for (String destname : config.getKeys(false)) {
			get(destname).load(config.getConfigurationSection("destname"));
		}
	}
	public static void save(String filename) {
		Configuration config = new Configuration(filename);
		config.load();
		Set<String> keys = config.getKeys(false);
		for (String key : keys){config.set(key, null);}
		for (Destinations prop : properties.values()) {
			prop.save(config, prop.getDestName());
		}
		config.save();
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
	public static BlockFace getDir(String destname, Location currloc){
		checked.clear();
		String thistag = Util.loc2string(currloc);
		Node r = get(thistag).getDir(destname);
		return r.dir;
	}
	
	private String destname;
	public HashMap<String, Node> dests = new HashMap<String, Node>();
	public List<String> neighbours = new ArrayList<String>();

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
		//if we don't know anything, explore first.
		if (this.neighbours.isEmpty()){
			this.explore(BlockFace.NORTH);
			this.explore(BlockFace.EAST);
			this.explore(BlockFace.SOUTH);
			this.explore(BlockFace.WEST);
		}
		//if unknown, ask neighbours
		if (checked.add(this.destname)){
			if (!this.dests.containsKey(reqname)) this.askNeighbours(reqname);
		}
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
		Location tmploc = Util.string2loc(destname);
		if (tmploc == null) return;
		Block tmp = tmploc.getBlock().getRelative(dir);
		TrackMap map = new TrackMap(tmp, dir);
		while (tmp != null){
			Sign sign = map.getSign();
			if (sign != null){
				if (sign.getLine(0).equalsIgnoreCase("[train]")){
					String newdest = "";
					if (sign.getLine(1).toLowerCase().startsWith("tag")){
						newdest = Util.loc2string(tmp.getLocation());
					}
					if (sign.getLine(1).toLowerCase().startsWith("destination")){
						newdest = sign.getLine(2);
					}
					if (newdest.equals(destname)) newdest = "";
					if (!newdest.isEmpty()){
						this.neighbours.add(newdest);
						this.updateDest(newdest, new Node(dir, map.getTotalDistance() + 1));
						return;
					}
				}
			}
			tmp = map.next();
		}
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
		if (n != null){
			if (n.dist <= newnode.dist) return;
			if (this.neighbours.contains(newdest)) return; //skip alternatives for direct neighbours
		}else{
			
		}
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

	@SuppressWarnings("unchecked") //prevent warning for getlist -> stringlist
	public void load(ConfigurationSection config) {
		if (config == null) return;
		this.neighbours = config.getList("neighbours");
		for (String k : config.getKeys(false)){
			if (k.equals("neighbours")) continue; //skip neighbours
			BlockFace bf = BlockFace.UP;
			String dir = config.getString(k + ".dir");
			if (dir.equals("NORTH")) bf = BlockFace.NORTH;
			if (dir.equals("EAST")) bf = BlockFace.EAST;
			if (dir.equals("SOUTH")) bf = BlockFace.SOUTH;
			if (dir.equals("WEST")) bf = BlockFace.WEST;
			this.dests.put(k, new Node(bf, config.getDouble(k + ".dist")));
		}
	}
	public void load(Destinations source) {
		this.dests.putAll(source.dests);
	}
	public void save(FileConfiguration config, String key) {
		config.set(key + ".neighbours", this.neighbours);
		for (String d : dests.keySet()){
			config.set(key + "." + d + ".dir", this.dests.get(d).dir.toString());
			config.set(key + "." + d + ".dist", this.dests.get(d).dist);
		}
	}
}
