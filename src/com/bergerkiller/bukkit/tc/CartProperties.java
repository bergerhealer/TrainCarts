package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.EnumUtil;
import com.bergerkiller.bukkit.tc.signactions.SignActionAnnounce;
import com.bergerkiller.bukkit.tc.utils.SoftReference;

public class CartProperties {
	public static final CartProperties EMPTY = new CartProperties(null, TrainProperties.EMPTY);
	
	private static HashMap<UUID, CartProperties> properties = new HashMap<UUID, CartProperties>();
	private static HashMap<String, CartProperties> editing = new HashMap<String, CartProperties>();
	
	public static CartProperties getEditing(String playername) {
		return editing.get(playername.toLowerCase());
	}
	public static void setEditing(String playername, CartProperties properties) {
		if (properties == null) {
			editing.remove(playername.toLowerCase());
		} else {
			editing.put(playername.toLowerCase(), properties);
		}
	}
	
	public static CartProperties get(UUID uuid, TrainProperties train) {
		CartProperties prop = properties.get(uuid);
		if (prop == null) {
			prop = new CartProperties(uuid, train);
			properties.put(uuid, prop);
		}
		return prop;
	}
	public static CartProperties get(MinecartMember member) {
		return get(member.uniqueId, member.getGroup().getProperties());
	}
	
	private CartProperties(UUID uuid, TrainProperties group) {
		this.uuid = uuid;
		this.group = group;
	}
	
	private final UUID uuid;
	private final Set<String> owners = new HashSet<String>();
	private final Set<String> tags = new HashSet<String>();
	public final Set<Material> blockBreakTypes = new HashSet<Material>();
	public boolean allowMobsEnter = true;
	public boolean allowPlayerExit = true;
	public boolean allowPlayerEnter = true;
	public String enterMessage = null;
	public String destination = "";
	public boolean isPublic = true;
	public boolean pickUp = false;
	private SoftReference<MinecartMember> member = new SoftReference<MinecartMember>();
	protected TrainProperties group = null;
	
	public void setEditing(String playername) {
		setEditing(playername, this);
	}
	public void remove() {
		properties.remove(this.uuid);
	}
	public TrainProperties getTrainProperties() {
		return this.group;
	}
	public MinecartMember getMember() {
		MinecartMember member = this.member.get();
		if (member == null || member.dead || !member.uniqueId.equals(this.uuid)) {
			return this.member.set(MinecartMember.get(this.uuid));
		} else {
			return member;
		}
	}
	public MinecartGroup getGroup() {
		MinecartMember member = this.getMember();
		if (member == null) {
			return this.group == null ? null : this.group.getGroup();
		} else {
			return member.getGroup();
		}
	}
	public UUID getUUID() {
		return this.uuid;
	}
	
	public void tryUpdate() {
		MinecartMember m = this.getMember();
		if (m != null) m.update();
	}
	
	/*
	 * Block obtaining
	 */
	public boolean canBreak(Block block) {
		if (this.blockBreakTypes.isEmpty()) return false;
		return this.blockBreakTypes.contains(block.getType());
	}

	/*
	 * Owners
	 */
	public boolean hasOwnership(Player player) {
		if (!canHaveOwnership(player)) return false;
		if (!this.hasOwners()) return true;
		if (hasGlobalOwnership(player)) return true;
		return this.isOwner(player);
	}
	public static boolean hasGlobalOwnership(Player player) {
		return Permission.COMMAND_GLOBALPROPERTIES.has(player);
	}
	public static boolean canHaveOwnership(Player player) {
		return Permission.COMMAND_PROPERTIES.has(player) || hasGlobalOwnership(player);
	}
	public boolean isOwner(Player player) {
		return this.isOwner(player.getName().toLowerCase());
	}
	public boolean isOwner(String player) {
		return this.owners.contains(player);
	}
	public void setOwner(String player) {
		this.setOwner(player, true);
	}
	public void setOwner(String player, boolean owner) {
		if (owner) {
			this.owners.add(player);
		} else {
			this.owners.add(player);
		}
	}
	public void setOwner(Player player) {
		this.setOwner(player, true);
	}
	public void setOwner(Player player, boolean owner) {
		this.setOwner(player.getName().toLowerCase(), owner);
	}
	public Set<String> getOwners() {
		return this.owners;
	}
	public void clearOwners() {
		this.owners.clear();
	}
	public boolean hasOwners() {
		return !this.owners.isEmpty();
	}
	public boolean sharesOwner(CartProperties properties) {
		if (!this.hasOwners()) return true;
		if (!properties.hasOwners()) return true;
		for (String owner : properties.owners) {
			if (properties.isOwner(owner)) return true;
		}
		return false;
	}

	/*
	 * Tags
	 */
	private boolean hasTag(String[] requirements, boolean firstIsAllPossible, boolean lastIsAllPossible) {
		if (requirements.length == 0) return this.tags.size() > 0;
		for (String tag : this.tags) {
			int index = 0;
			boolean has = true;
			boolean first = true;
			for (int i = 0; i < requirements.length; i++) {
				if (requirements[i].length() == 0) continue;
				index = tag.indexOf(requirements[i], index);
				if (index == -1 || (first && !firstIsAllPossible && index != 0)) {
					has = false;
					break;
				} else {
					index += requirements[i].length();
				}
				first = false;
			}
			if (has) {
				if (lastIsAllPossible || index == tag.length()) {
					return true;
				}
			}
		}
		return false;
	}
	public boolean hasTag(String tag) {
		if (this.tags.isEmpty()) return false;
		if (tag.startsWith("!")) {
			return !hasTag(tag.substring(1));
		} else {
			if (tag.length() == 0) return false;
			return hasTag(tag.split("\\*"), tag.startsWith("*"), tag.endsWith("*"));
		}
	}
	public boolean hasTags() {
		return !this.tags.isEmpty();
	}
	public void addTags(String... tags) {
		for (String tag : tags) {
			if (!this.hasTag(tag)) this.tags.add(tag);
		}
	}
	public void setTags(String... tags) {
		this.tags.clear();
		this.addTags(tags);
	}
	public Set<String> getTags() {
		return this.tags;
	}
	public void clearTags() {
		this.tags.clear();
	}
	public void removeTags(String... tags) {
		for (String tag : tags) {
			this.tags.remove(tag);
		}
	}

	/*
	 * Enter message
	 */
	public boolean hasEnterMessage() {
		return this.enterMessage != null && !this.enterMessage.equals("");
	}
	public void showEnterMessage(Player player) {
		if (this.hasEnterMessage()) {
			SignActionAnnounce.sendMessage(ChatColor.YELLOW + SignActionAnnounce.getMessage(enterMessage), player);
		}
	}
	
	/*
	 * Destination
	 */
	public boolean hasDestination() {
		return this.destination != null && this.destination.length() != 0;
	}

	public void load(CartProperties from) {
		this.owners.clear();
		this.owners.addAll(from.owners);
		this.tags.clear();
		this.tags.addAll(from.tags);
		this.allowMobsEnter = from.allowMobsEnter;
		this.allowPlayerEnter = from.allowPlayerEnter;
		this.allowPlayerExit = from.allowPlayerExit;
	}
	public void load(ConfigurationNode node) {
		for (String owner : node.getList("owners", String.class)) {
			this.owners.add(owner.toLowerCase());
		}
		for (String tag : node.getList("tags", String.class)) {
			this.tags.add(tag);
		}
		this.allowMobsEnter = node.get("allowMobsEnter", this.allowMobsEnter);
		this.allowPlayerEnter = node.get("allowPlayerEnter", this.allowPlayerEnter);
		this.allowPlayerExit = node.get("allowPlayerExit", this.allowPlayerExit);
		this.isPublic = node.get("isPublic", this.isPublic);
		this.pickUp = node.get("pickUp", this.pickUp);
		for (String blocktype : node.getList("blockBreakTypes", String.class)) {
			Material mat = EnumUtil.parseMaterial(blocktype, null);
			if (mat != null) this.blockBreakTypes.add(mat);
		}
	}
	public void save(ConfigurationNode node) {
		this.save(node, true);
	}
	public void save(ConfigurationNode node, boolean minimal) {
		if (minimal) {
			node.set("owners", this.owners.isEmpty() ? null : new ArrayList<String>(this.owners));
			node.set("tags", this.tags.isEmpty() ? null : new ArrayList<String>(this.tags));
			node.set("allowPlayerEnter", this.allowPlayerEnter ? null : false);
			node.set("allowPlayerExit", this.allowPlayerExit ? null : false);	
			node.set("allowMobsEnter", this.allowMobsEnter ? null : false);
			node.set("isPublic", this.isPublic ? null : false);
			node.set("pickUp", this.pickUp ? true : null);
		} else {
            node.set("owners", new ArrayList<String>(this.owners));
            node.set("tags", new ArrayList<String>(this.tags));
            node.set("allowPlayerEnter", this.allowPlayerEnter);
            node.set("allowPlayerExit", this.allowPlayerExit);
            node.set("allowMobsEnter", this.allowMobsEnter);
            node.set("isPublic", this.isPublic);
            node.set("pickUp", this.pickUp);
		}
		if (!minimal || !this.blockBreakTypes.isEmpty()) {
			List<String> items = node.getList("blockBreakTypes", String.class);
			for (Material mat : this.blockBreakTypes) {
				items.add(mat.toString());
			}
		} else {
			node.remove("blockBreakTypes");
		}
		node.set("destination", this.hasDestination() ? this.destination : null);
		node.set("enterMessage", this.hasEnterMessage() ? this.enterMessage : null);
	}

}
