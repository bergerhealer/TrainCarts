package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class Destinations {
	private static HashMap<String, Destinations> properties = new HashMap<String, Destinations>();
	public static Destinations get(String destname) {
		if (destname == null) return null;
		Destinations prop = properties.get(destname);
		if (prop == null) {
			return new Destinations(destname);
		}
		return prop;
	}
	public static boolean exists(String destname) {
		return properties.containsKey(destname);
	}
	public static void clear(){
		properties.clear();
	}

	private String destname;
	private static List<String> checked = new ArrayList<String>();
	public HashMap<String, Destination> dests = new HashMap<String, Destination>();

	private Destinations() {};
	private Destinations(String destname) {
		properties.put(destname, this);
		this.destname = destname;
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
		String thistag = loc2string(currloc);
		Destination r = get(thistag).getDir(destname);
		if (r.getDir() == BlockFace.UP){r.setDir(BlockFace.NORTH);}
		return r.getDir();
	}

	/**
	 * Finds the direction to go to from this destination to reach reqname.
	 * This works by looking up the wanted destination, then generating this information
	 * if not available yet by recursively asking all known destinations
	 * if they can reach this destination.
	 * @param reqname The wanted destination.
	 * @return The direction to go in from currloc to reach destname, or BlockFace.UP if unknown.
	 */
	public Destination getDir(String reqname){
		//is this us? return DOWN;
		if (reqname == destname){return new Destination(BlockFace.DOWN, 0.0);}
		//were we already checked? cancel by returning unknown.
		if (checked.contains(destname)){return new Destination(BlockFace.UP, 100000.0);}
		//put ourselves in the checked list, preventing loops.
		checked.add(destname);
		//first check what we already know
		if (dests.containsKey(reqname)){return dests.get(reqname);}
		explore(BlockFace.NORTH);
		explore(BlockFace.EAST);
		explore(BlockFace.SOUTH);
		explore(BlockFace.WEST);
		if (dests.containsKey(reqname)){return dests.get(reqname);}
		//destination not known - try asking all known destinations if they can reach this
		Destination r = new Destination(BlockFace.UP, 100000.0);
		List<String> keys = new ArrayList<String>();
		keys.addAll(dests.keySet());
		for (String other : keys){
			Destination node = dests.get(other);
			Destination end = get(other).getDir(reqname);
			if (end.getDir() != BlockFace.UP){
				if (end.getDist() + node.getDist() < r.getDist()){
					r.setDist(end.getDist() + node.getDist());
					r.setDir(node.getDir());
				}
			}
		}
		//save and return what we could find
		updateDest(reqname, r.getDir(), r.getDist());
		return r;
	}

	/**
	 * Explores in the given direction, until out of rails or either
	 * a tag switcher or destination is found. Adds the first found
	 * to the list for the given direction.
	 * @param dir Direction to explore in. Only works for NORTH/EAST/SOUTH/WEST.
	 */
	private void explore(BlockFace dir){
		Location tmploc = string2loc(destname);
		if (tmploc == null){return;}
		Block tmp = tmploc.getBlock();
		if (tmp == null){return;}
		tmp = tmp.getRelative(dir);
		if (tmp == null){return;}
		TrackMap map = new TrackMap(tmp, dir);
		int maxDistance = 500; //Maximum allowed distance between destination signs in blocks
		while (tmp != null){
			Sign sign = map.getSign();
			if (sign != null){
				if (sign.getLine(0).equalsIgnoreCase("[train]")){
					String newdest = "";
					if (sign.getLine(1).toLowerCase().startsWith("tag")){
						newdest = loc2string(tmp.getLocation());
					}
					if (sign.getLine(1).toLowerCase().startsWith("destination")){
						newdest = sign.getLine(2);
					}
					if (newdest == destname) newdest = "";
					if (!newdest.isEmpty()){
						updateDest(newdest, dir, map.getTotalDistance()+1);
						return;
					}
				}
			}
			tmp = map.next();
			if (--maxDistance == 0) break;
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
	private void updateDest(String newdest, BlockFace newdir, double newdist){
		boolean isNew = false;
		if (newdist >= 100000.0){return;} //don't store failed calculations
		//if we already know about this destination, and we are not faster, ignore it.
		if (dests.containsKey(newdest)){
			if (dests.get(newdest).getDist() <= newdist){return;}
		}else{
			isNew = true;
		}
		//otherwise, save.
		dests.put(newdest, new Destination(newdir, newdist));
		//also, check all other points and update this destination there, if better.
		List<String> keys = new ArrayList<String>();
		keys.addAll(properties.keySet());
		for (String propkey : keys){
			Destinations D = properties.get(propkey);
			if (D.destname == this.destname){continue;}//skip self
			Destination node = D.getDir(this.destname);
			D.updateDest(newdest, node.getDir(), newdist+node.getDist());
		}
		//lastly, if this was a new point, check if it has any faster routes for us
		if (isNew){
			Destinations D = get(newdest);
			for (String posNode : D.dests.keySet()){
				updateDest(posNode, newdir, newdist + D.dests.get(posNode).getDist());
			}
		}
	}

	/**
	 * Converts a Location to a destination name.
	 * @param loc The Location to convert
	 * @return A string representing the destination name.
	 */
	public static String loc2string(Location loc){
		return loc.getWorld().getName()+"_"+(int)loc.getX()+"_"+(int)loc.getY()+"_"+(int)loc.getZ();
	}
	/**
	 * Converts a destination name to a String.
	 * @param str The String to convert
	 * @return A Location representing the String.
	 */
	public static Location string2loc(String str){
		try{
			String s[] = str.split("_");
			String w = "";
			Double X = 0.0, Y = 0.0, Z = 0.0;
			for (int i = 0; i < s.length; i++){
				switch (s.length - i){
				case 1: Z = Double.parseDouble(s[i]); break;
				case 2: Y = Double.parseDouble(s[i]); break;
				case 3: X = Double.parseDouble(s[i]); break;
				default: if (!w.isEmpty()){w += "_";} w += s[i]; break;
				}
			}
			Location r = new Location(TrainCarts.plugin.getServer().getWorld(w), X, Y, Z);
			return r;
		} catch (Exception e){
			return null;
		}
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

	public void load(ConfigurationSection config) {
		for (String k : config.getKeys(false)){
			BlockFace bf = BlockFace.UP;
			String dir = config.getString(k+".dir");
			if (dir.equals("NORTH")){bf = BlockFace.NORTH;}
			if (dir.equals("EAST")){bf = BlockFace.EAST;}
			if (dir.equals("SOUTH")){bf = BlockFace.SOUTH;}
			if (dir.equals("WEST")){bf = BlockFace.WEST;}
			this.dests.put(k, new Destination(bf, config.getDouble(k+".dist")));
		}
	}
	public void load(Destinations source) {    
		this.dests.putAll(source.dests);
	}
	public void save(FileConfiguration config, String key) {
		for ( String d : dests.keySet()){
			config.set(key + "." + d + ".dir", dests.get(d).getDir().toString());
			config.set(key + "." + d + ".dist", dests.get(d).getDist());
		}
	}
	public static void load(String filename) {
		try {
			FileConfiguration config = YamlConfiguration.loadConfiguration(new File(filename));
			for (String destname : config.getKeys(false)) {
				get(destname).load(config.getConfigurationSection("destname"));
			}
		} catch (Throwable t) {
			TrainCarts.plugin.getServer().getConsoleSender().sendMessage("[TrainCarts] Error loading destinations file.");
			t.printStackTrace();
		}
	}
	public static void save(String filename) {
		try {
			FileConfiguration config = YamlConfiguration.loadConfiguration(new File(filename));
			Set<String> keys = config.getKeys(false);
			for (String key : keys){config.set(key, null);}
			for (Destinations prop : properties.values()) {
				prop.save(config, prop.getDestName());
			}
			config.save(new File(filename));
		} catch (Throwable t) {
			TrainCarts.plugin.getServer().getConsoleSender().sendMessage("[TrainCarts] Error saving destinations file.");
			t.printStackTrace();
		}
	}

}
