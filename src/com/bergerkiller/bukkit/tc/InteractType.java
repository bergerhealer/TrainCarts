package com.bergerkiller.bukkit.tc;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * An active type of Object to interact with
 */
public enum InteractType {
	CHEST, FURNACE, DISPENSER, GROUNDITEM;

	/**
	 * Parses all the active interactables represented by the root and name
	 * 
	 * @param root of the active object operation
	 * @param name of the object
	 * @return A collection of objects represented
	 */
	public static Collection<InteractType> parse(String root, String name) {
		name = name.toLowerCase();
		LinkedHashSet<InteractType> typesToCheck = new LinkedHashSet<InteractType>();
		if (root.equals("collect")) {
			if (name.startsWith("chest out")) {
				typesToCheck.add(CHEST);
			} else if (name.startsWith("dispenser out")) {
				typesToCheck.add(DISPENSER);
			} else if (name.startsWith("furnace out")) {
				typesToCheck.add(FURNACE);
			} else if (name.startsWith("pickup") || name.startsWith("pick up")) {
				typesToCheck.add(GROUNDITEM);
			}
		} else if (root.equals("deposit")) {
			if (name.startsWith("chest in")) {
				typesToCheck.add(CHEST);
			} else if (name.startsWith("dispenser in")) {
				typesToCheck.add(DISPENSER);
			} else if (name.startsWith("furnace in")) {
				typesToCheck.add(FURNACE);
			} else if (name.startsWith("smelt")) {
				typesToCheck.add(FURNACE);
			} else if (name.startsWith("drop items") || name.startsWith("dropitems")) {
				typesToCheck.add(GROUNDITEM);
			}
		}
		if (name.startsWith(root + ' ')) {
			String types = name.substring(root.length() + 1).toLowerCase();
			if (types.startsWith("chest")) {
				typesToCheck.add(CHEST);
			} else if (types.startsWith("furn")) {
				typesToCheck.add(FURNACE);
			} else if (types.startsWith("disp")) {
				typesToCheck.add(DISPENSER);
			} else if (types.startsWith("ground")) {
				typesToCheck.add(GROUNDITEM);
			} else {
				for (char c : types.toCharArray()) {
					if (c == 'c') {
						typesToCheck.add(CHEST);
					} else if (c == 'f') {
						typesToCheck.add(FURNACE);
					} else if (c == 'd') {
						typesToCheck.add(DISPENSER);
					} else if (c == 'g') {
						typesToCheck.add(GROUNDITEM);
					}
				}
			}
		} else if (name.startsWith(root)) {
			typesToCheck.add(CHEST);
			typesToCheck.add(FURNACE);
			typesToCheck.add(DISPENSER);
		}
		return typesToCheck;
	}
}
