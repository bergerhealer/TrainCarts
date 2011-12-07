package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("rawtypes")
public class Configuration extends YamlConfiguration {
	
	private HashSet<String> readKeys = new HashSet<String>();
	
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
	
	public <T> T parse(final String path) {
		return this.parse(path, null);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T parse(final String path, T def) {
		Object res = this.get(path, def);
		if (res.getClass().equals(def.getClass())) {
			def = (T) res;
		} else {
			System.out.println("[Configuration] An error occured while reading '" + path + "' from '" + this.source + "': ");
			System.out.println("[Configuration] A " + res.getClass().getSimpleName() + " was read while a" + def.getClass().getSimpleName() + " was expected");
		}
		this.set(path, def);
		return def;
	}
	
	public boolean exists() {
		return this.source.exists();
	}
	public void init() {
		this.load();
		this.save();
	}
	
	public void setRead(final String path) {
		this.sr(path.toLowerCase());
	}
	private void sr(final String path) {
		this.readKeys.add(path);
		int index = path.lastIndexOf('.');
		if (index != -1) {
			this.sr(path.substring(0, index));
		}	
	}
	
	public Object get(String path, Object def) {
		this.setRead(path);
		return super.get(path, def);
	}
	
	private void trimkey(String keyname) {
		if (!this.readKeys.contains(keyname.toLowerCase())) {
			this.set(keyname, null);
		} else {
			for (String key : this.getKeys(keyname)) {
				trimkey(keyname + "." + key);
			}
		}
	}
	
	public void trim() {
		for (String key : this.getKeys(false)) {
			trimkey(key);
		}
		this.readKeys.clear();
	}

	public void load() {
		try {
			this.load(this.source);
		} catch (FileNotFoundException ex) {
			System.out.println("[Configuration] File '" + this.source + "' was not found");
		} catch (Exception ex) {
			System.out.println("[Configuration] Error while loading file '" + this.source + "':");
			ex.printStackTrace();
		}
	}
	public void save() {
		try {
			boolean regen = !this.exists();
			this.save(this.source);
			if (regen) System.out.println("[Configuration] File '" + this.source + "' has been regenerated");
		} catch (Exception ex) {
			System.out.println("[Configuration] Error while saving to file '" + this.source + "':");
			ex.printStackTrace();
		}
	}
	
	public List getList(String path, List def) {
		this.setRead(path.toLowerCase());
		return super.getList(path, def);
	}
	
	public <T> List<T> getListOf(String path) {
		return this.getListOf(path, new ArrayList<T>());
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
	
	public ConfigurationSection getConfigurationSection(String path) {
		this.setRead(path);
		return super.getConfigurationSection(path);
	}
	
	public Set<String> getKeys(String path) {
		this.setRead(path);
		try {
			return this.getConfigurationSection(path).getKeys(false);
		} catch (Exception ex) {
			return new HashSet<String>();
		}
	}
	
}
