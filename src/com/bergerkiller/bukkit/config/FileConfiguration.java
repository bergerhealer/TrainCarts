package com.bergerkiller.bukkit.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

public class FileConfiguration extends ConfigurationNode {
	private static Logger logger = Logger.getLogger("minecraft");
	
	private final File file;
	public FileConfiguration(JavaPlugin plugin) {
		this(plugin, "config.yml");
	}
	public FileConfiguration(JavaPlugin plugin, String filepath) {
		this(plugin.getDataFolder() + File.separator + filepath);
	}
	public FileConfiguration(String filepath) {
		this(new File(filepath));
	}
	public FileConfiguration(final File file) {
		this.file = file;
	}
	
	public boolean exists() {
		return this.file.exists();
	}
	public void load() {
		try {
			this.getSource().load(this.file);
		} catch (FileNotFoundException ex) {
		} catch (Exception ex) {
			logger.log(Level.SEVERE, "[Configuration] An error occured while loading file '" + this.file + "':");
			ex.printStackTrace();
		}
	}
	public void save() {
		try {
			boolean regen = !this.exists();
			this.getSource().save(this.file);
			if (regen) logger.log(Level.INFO, "[Configuration] File '" + this.file + "' has been generated");
		} catch (Exception ex) {
			logger.log(Level.SEVERE, "[Configuration] An error occured while saving to file '" + this.file + "':");
			ex.printStackTrace();
		}
	}
	
}
