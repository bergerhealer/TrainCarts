package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
		this.properties = new ArrayList<Property>();
	}
	
	private ArrayList<Property> properties;
	private File source;
	
	public <T> Property<T> getProperty(String path, T def) {
		Property<T> prop = new Property<T>();
		prop.path = path;
		prop.value = def;
		this.properties.add(prop);
		return prop;
	}
	
	public boolean exists() {
		return this.source.exists();
	}
	public void init() {
		this.load();
		this.save();
	}
	@SuppressWarnings("unchecked")
	public void load() {
		try {
			this.load(this.source);
			for (Property p : this.properties) {
				p.value = this.get(p.path, p.value);
			}
		} catch (Exception ex) {
			System.out.println("[Configuration] Error while loading file '" + this.source + "':");
			ex.printStackTrace();
		}
	}
	public void save() {
		try {
			for (Property p : this.properties) {
				this.set(p.path, p.value);
			}
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
	
	public static class Property<T> {
		private String path;
		private T value;
		private Property() {};
		
		public T get() {
			return this.value;
		}
		public void set(T value) {
			this.value = value;
		}

	}

}
