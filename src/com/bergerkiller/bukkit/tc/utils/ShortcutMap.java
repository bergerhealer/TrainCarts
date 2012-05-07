package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleEntry;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * Stores several key versus value pairs to replace values
 */
public class ShortcutMap {
	
	private List<Entry<String, String>> shortcuts = new ArrayList<Entry<String, String>>();

	public String replace(String value) {
		for (Entry<String, String> shortcut : shortcuts) {
			value = value.replace(shortcut.getKey(), shortcut.getValue());
		}
		return value;
	}

	public ShortcutMap add(String key, String value) {
		if (key != null && value != null && !value.contains(key)) {
			shortcuts.add(new SimpleEntry<String, String>(key, value));
		}
		return this;
	}

	public ShortcutMap load(ConfigurationNode node) {
		for (String key : node.getKeys()) {
			add(key, node.get(key, key));
		}
		return this;
	}

	public ShortcutMap clear() {
		shortcuts.clear();
		return this;
	}
}
