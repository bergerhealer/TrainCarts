package com.bergerkiller.bukkit.tc;

import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

public class TrainCarts extends JavaPlugin {
	/*
	 * Settings
	 */	
	public static double cartDistance = 1.42;
	public static double turnedCartDistance = 1.7;
	public static boolean removeDerailedCarts = true;
	public static double cartDistanceForcer = 0.6;
	public static double turnedCartDistanceForcer = 0.9;
	public static double nearCartDistanceFactor = 1.3;
	public static double maxCartSpeed = 0.350;
	public static double maxCartDistance = 4;
	public static double linkRadius = 2.5;
	public static int linkInterval = 2;
	public static boolean contactLinking = false;
	public static boolean breakCombinedCarts = false;
	
	
	public static TrainCarts plugin;
	private final TCPlayerListener playerListener = new TCPlayerListener();
	private final TCVehicleListener vehicleListener = new TCVehicleListener(this);	
	private int ugtask;
	
	public void onEnable() {	
		plugin = this;
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_INTERACT_ENTITY, playerListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.VEHICLE_UPDATE, vehicleListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.VEHICLE_DESTROY, vehicleListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.VEHICLE_COLLISION_ENTITY, vehicleListener, Priority.Lowest, this);
		pm.registerEvent(Event.Type.VEHICLE_COLLISION_BLOCK, vehicleListener, Priority.Lowest, this);
		
		//Load from config
		Configuration config = this.getConfiguration();
		boolean use = config.getBoolean("use", true);
		if (use) {
			cartDistance = config.getDouble("normal.cartDistance", cartDistance);
			cartDistanceForcer = config.getDouble("normal.cartDistanceForcer", cartDistanceForcer);	
			turnedCartDistance = config.getDouble("turned.cartDistance", turnedCartDistance);
			turnedCartDistanceForcer = config.getDouble("turned.cartDistanceForcer", turnedCartDistanceForcer);	
			nearCartDistanceFactor = config.getDouble("nearCartDistanceFactor", nearCartDistanceFactor);	
			removeDerailedCarts = config.getBoolean("removeDerailedCarts", removeDerailedCarts);
			maxCartSpeed = config.getDouble("maxCartSpeed", maxCartSpeed);
			maxCartDistance = config.getDouble("maxCartDistance", maxCartDistance);
			contactLinking = config.getBoolean("linking.useContactLinking", contactLinking);
			linkRadius = config.getDouble("linking.linkRadius", linkRadius);
			linkInterval = config.getInt("linking.linkInterval", linkInterval);
			breakCombinedCarts = config.getBoolean("breakCombinedCarts", breakCombinedCarts);
			
			//save it again (no file was there or invalid)
			config.setHeader("# In here you can change the settings for the TrainCarts plugin", 
					"# Please note that the default settings are usually the best", 
					"# Changing these settings can cause the plugin to fail", 
					"# To reset the settings, remove this file", 
					"# cartDistance is the distance kept between normal/turned carts in a group", 
					"# cartDistanceForcer is the factor applied to adjust this distance, too high and it will bump violently", 
					"# nearCartDistanceFactor is the factor applied to the regular forcers if the carts are too close to eachother", 
					"# removeDerailedCarts sets if carts without tracks underneath are cleared from the group", 
					"# maxCartSpeed is the maximum speed to use for trains. Use a value between 0 and 0.4, at 0.4 carts will not be able to outrun other carts!", 
					"# maxCartDistance sets the distance at which carts are thrown out of their group", 
					"# useContactLinking sets if carts should be linked on collision, instead of using a radius", 
					"# linkRadius sets the radius in which minecarts can be linked (useContactLinking = false)", 
					"# linkInterval sets the interval in which to check for carts that can be linked, in ticks (1/20 of a second)", 
					"# breakCombinedCarts sets if combined carts (e.g. PoweredMinecart) breaks up into multiple parts upon breaking (e.g. furnace and minecart)");
			       
			config.setProperty("use", use);
			config.setProperty("normal.cartDistance", cartDistance);
			config.setProperty("normal.cartDistanceForcer", cartDistanceForcer);
			config.setProperty("turned.cartDistance", turnedCartDistance);
			config.setProperty("turned.cartDistanceForcer", turnedCartDistanceForcer);
			config.setProperty("nearCartDistanceFactor", nearCartDistanceFactor);
			config.setProperty("removeDerailedCarts", removeDerailedCarts);
			config.setProperty("maxCartSpeed", maxCartSpeed);
			config.setProperty("maxCartDistance", maxCartDistance);
			config.setProperty("linking.useContactLinking", contactLinking);
			config.setProperty("linking.linkRadius", linkRadius);
			config.setProperty("linking.linkInterval", linkInterval);	
			config.setProperty("breakCombinedCarts", breakCombinedCarts);
			config.save();
		}
		
		//clean groups from dead and derailed carts and form new groups
    	ugtask = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
    	    public void run() {
    	    	MinecartGroup.cleanGroups();
    	    	if (!contactLinking) MinecartGroup.updateGroups();
    	    }
    	}, 0, linkInterval);
    		
        //final msg
        PluginDescriptionFile pdfFile = this.getDescription();
        System.out.println(pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
	}
	public void onDisable() {
		//Stop tasks
		getServer().getScheduler().cancelTask(ugtask);
		//undo replacements for correct saving
		MinecartFixer.undoReplacement();
		
		System.out.println("TrainCarts disabled!");
	}
		
}
