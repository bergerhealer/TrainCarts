package com.bergerkiller.bukkit.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;

@SuppressWarnings({"unchecked","rawtypes"})
public class ConfigurationNode {
	private final MemorySection source;
	private final Set<String> readkeys;
	public ConfigurationNode() {
		this(new HashSet<String>(), new YamlConfiguration());
	}
	private ConfigurationNode(ConfigurationNode source, String root) {
		this.readkeys = source.readkeys;
		MemorySection sect = (MemorySection) source.source.getConfigurationSection(root);
		if (sect == null) {
			this.source = (MemorySection) source.source.createSection(root);
		} else {
			this.source = sect;
		}
		this.setRead();
	}
	private ConfigurationNode(final Set<String> readkeys, final MemorySection source) {
		this.readkeys = readkeys;
		this.source = source;
	}
	
	public boolean hasParent() {
		return this.source.getParent() != null;
	}
	public ConfigurationNode getParent() {
		MemorySection sec = (MemorySection) this.source.getParent();
		if (sec == null) return null;
		return new ConfigurationNode(this.readkeys, sec);
	}
	public String getPath(String append) {
		String p = this.getPath();
		if (append == null || append.length() == 0) return p;
		if (p == null || p.length() == 0) return append;
		return p + "." + append;
	}
	public String getPath() {
		return this.source.getCurrentPath();
	}
	public String getName() {
		return this.source.getName();
	}
	
	public MemorySection getSection() {
		return this.source;
	}
	public YamlConfiguration getSource() {
		return (YamlConfiguration) this.source.getRoot();
	}
	
	public boolean isNode(String path) {
		return this.source.isConfigurationSection(path);
	}
	public ConfigurationNode getNode(String path) {
		return new ConfigurationNode(this, path);
	}
	public Set<ConfigurationNode> getNodes() {
		Set<ConfigurationNode> rval = new HashSet<ConfigurationNode>();
		for (String path : this.getKeys()) {
			if (this.isNode(path)) {
				rval.add(this.getNode(path));
			}
		}
		return rval;
	}
	public Set<String> getKeys() {
		return this.source.getKeys(false);
	}	
		
	public boolean isRead() {
		return this.isRead(null);
	}
	public boolean isRead(String path) {
		return this.readkeys.contains(this.getPath(path));
	}
	public void setRead() {
		this.setRead(null);
	}
	public void setRead(String path) {
		this.setReadFullPath(this.getPath(path));
	}
	private void setReadFullPath(String path) {
		if (this.readkeys.add(path)) {
			int dotindex = path.lastIndexOf('.');
			if (dotindex > 0) {
				this.setReadFullPath(path.substring(0, dotindex));
			}
		}
	}
	
	public void trim() {
		for (String key : this.getKeys()) {
			if (this.isRead(key)) {
				if (this.isNode(key)) {
					this.getNode(key).trim();
				}
			} else {
				this.remove(key);
			}
		}
	}
	
	public boolean contains(String path) {
		return this.source.contains(path);
	}
	
	public void clear() {
		for (String key : this.getKeys()) this.remove(key);
	}
	
	public void remove(String path) {
		this.set(path, null);
	}
	public void set(String path, Object value) {
		if (value != null) this.setRead(path);
		this.source.set(path, value);
	}	
	
	public List getList(String path) {
		this.setRead(path);
		return this.source.getList(path);
	}
	public <T> List<T> getList(String path, Class<T> type) {
		return this.getList(path, type, new ArrayList<T>());
	}
	public <T> List<T> getList(String path, Class<T> type, List<T> def) {
		List list = this.getList(path);
		if (list != null) {
			def = new ArrayList<T>();
			T val;
			for (Object o : list) {
				val = convert(o, type);
				if (val != null) def.add(val);
			}
		}
		this.set(path, def);
		return def;
	}
		
	public Object get(String path) {
		this.setRead(path);
		return this.source.get(path);
	}
	public <T> T get(String path, Class<T> type) {
		return this.get(path, type, null);
	}
	public <T> T get(String path, T def) {
		return this.get(path, (Class<T>) def.getClass(), def);
	}
	public <T> T get(String path, Class<T> type, T def) {
		T rval = convert(this.get(path), type, def);
		this.set(path, rval);
		return rval;
	}
	
	public static <T> T convert(Object object, Class<T> type) {
		return convert(object, type, null);
	}
	public static <T> T convert(Object object, T def) {
		return convert(object, (Class<T>) def.getClass(), def);
	}
	public static <T> T convert(Object object, Class<T> type, T def) {
		if (object == null) return def;
		Object rval = def;
		try {
			if (type == object.getClass()) {
				rval = (T) object;
			} else if (type == Integer.class) {
				rval = Integer.parseInt(object.toString());
			} else if (type == Double.class) {
				rval = Double.parseDouble(object.toString());;
			}
		} catch (Exception ex) {
			rval = def;
		}
		return (T) rval;
	}
		
}
