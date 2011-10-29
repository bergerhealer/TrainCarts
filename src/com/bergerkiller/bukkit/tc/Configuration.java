package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("rawtypes")
public class Configuration extends YamlConfiguration {
	
	public Configuration(JavaPlugin plugin) {
		this(plugin.getDataFolder() + File.separator + "config.yml");
	}
	public Configuration(String sourcepath) {
		this(new File(sourcepath));
	}
	public Configuration(File source) {
		this.source = source;
	}
	
	private File source;
	
	@SuppressWarnings("unchecked")
	public <T> T parse(String path, T def) {
		T rval = (T) this.get(path, def);
		this.set(path, rval);
		return rval;
	}
	
	public boolean exists() {
		return this.source.exists();
	}
	public void init() {
		this.load();
		this.save();
	}

	public void load() {
		try {
			this.load(this.source);
		} catch (Exception ex) {
			System.out.println("[Configuration] Error while loading file '" + this.source + "':");
			ex.printStackTrace();
		}
	}
	public void save() {
		try {
			this.save(this.source);
		} catch (Exception ex) {
			System.out.println("[Configuration] Error while saving to file '" + this.source + "':");
			ex.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T> List<T> getListOf(String path, List<T> def) {
		List list = this.getList(path, null);
		if (list == null) {
			return def;
		} else {
			List<T> rval = new ArrayList<T>();
			for (Object object : this.getList(path)) {
				try {
					rval.add((T) object);
				} catch (Throwable t) {}
			}
			return rval;
		}
	}
	
	public Set<String> getKeys(String path) {
		try {
			return this.getConfigurationSection(path).getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}
	
}
