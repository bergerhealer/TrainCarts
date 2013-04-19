package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.storage.OfflineMember;
import com.bergerkiller.bukkit.tc.utils.SoftReference;

public class CartProperties extends CartPropertiesStore implements IProperties {
	public static final CartProperties EMPTY = new CartProperties(UUID.randomUUID(), null);

	private final UUID uuid;
	private final Set<String> owners = new HashSet<String>();
	private final Set<String> tags = new HashSet<String>();
	private final Set<Material> blockBreakTypes = new HashSet<Material>();
	private boolean allowPlayerExit = true;
	private boolean allowPlayerEnter = true;
	private String enterMessage = null;
	private String destination = "";
	private String lastPathNode = "";
	private boolean isPublic = true;
	private boolean pickUp = false;
	private SoftReference<MinecartMember<?>> member = new SoftReference<MinecartMember<?>>();
	protected TrainProperties group = null;

	protected CartProperties(UUID uuid, TrainProperties group) {
		this.uuid = uuid;
		this.group = group;
	}

	public TrainProperties getTrainProperties() {
		return this.group;
	}

	@Override
	public String getTypeName() {
		return "cart";
	}

	@Override
	public MinecartMember<?> getHolder() {
		MinecartMember<?> member = this.member.get();
		if (member == null || member.getEntity().isDead() || member.isUnloaded() || !member.getEntity().getUniqueId().equals(this.uuid)) {
			return this.member.set(MinecartMemberStore.get(this.uuid));
		} else {
			return member;
		}
	}

	@Override
	public boolean hasHolder() {
		return getHolder() != null;
	}

	@Override
	public boolean restore() {
		return getTrainProperties().restore() && hasHolder();
	}

	public MinecartGroup getGroup() {
		MinecartMember<?> member = this.getHolder();
		if (member == null) {
			return this.group == null ? null : this.group.getHolder();
		} else {
			return member.getGroup();
		}
	}
	public UUID getUUID() {
		return this.uuid;
	}
	
	public void tryUpdate() {
		MinecartMember<?> m = this.getHolder();
		if (m != null) m.onPropertiesChanged();
	}

	/**
	 * Gets a collection of lower-case player names that are editing these properties
	 * 
	 * @return Collection of editing player names
	 */
	public Collection<String> getEditing() {
		ArrayList<String> players = new ArrayList<String>();
		for (Map.Entry<String, CartProperties> entry : editing.entrySet()) {
			if (entry.getValue() == this) {
				players.add(entry.getKey());
			}
		}
		return players;
	}

	/**
	 * Gets a collection of online players that are editing these properties
	 * 
	 * @return Collection of editing players
	 */
	public Collection<Player> getEditingPlayers() {
		Collection<String> names = getEditing();
		ArrayList<Player> players = new ArrayList<Player>(names.size());
		for (String name : names) {
			Player p = Bukkit.getServer().getPlayerExact(name);
			if (p != null) {
				players.add(p);
			}
		}
		return players;
	}

	/*
	 * Block obtaining
	 */
	public boolean canBreak(Block block) {
		return !this.blockBreakTypes.isEmpty() && this.blockBreakTypes.contains(block.getType());
	}

	/*
	 * Owners
	 */
	public boolean hasOwnership(Player player) {
		if (!canHaveOwnership(player)) {
			return false;
		}
		if (!this.hasOwners()) {
			return true;
		}
		if (hasGlobalOwnership(player)) {
			return true;
		}
		return this.isOwner(player);
	}
	public static boolean hasGlobalOwnership(Player player) {
		return Permission.COMMAND_GLOBALPROPERTIES.has(player);
	}
	public static boolean canHaveOwnership(Player player) {
		return Permission.COMMAND_PROPERTIES.has(player) || hasGlobalOwnership(player);
	}

	@Override
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
			this.owners.remove(player);
		}
	}
	public void setOwner(Player player) {
		this.setOwner(player, true);
	}
	public void setOwner(Player player, boolean owner) {
		if (player == null) {
			return;
		}
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

	/**
	 * Gets whether this Minecart can pick up nearby items
	 * 
	 * @return True if it can pick up items, False if not
	 */
	public boolean canPickup() {
		return this.pickUp;
	}

	public void setPickup(boolean pickup) {
		this.pickUp = pickup;
	}

	@Override
	public boolean isPublic() {
		return this.isPublic;
	}

	@Override
	public void setPublic(boolean state) {
		this.isPublic = state;
	}

	@Override
	public boolean matchTag(String tag) {
		return Util.matchText(this.tags, tag);
	}

	@Override
	public boolean hasTags() {
		return !this.tags.isEmpty();
	}

	@Override
	public void setTags(String... tags) {
		this.tags.clear();
		this.addTags(tags);
	}

	@Override
	public void clearTags() {
		this.tags.clear();
	}

	@Override
	public void addTags(String... tags) {
		for (String tag : tags) {
			this.tags.add(tag);
		}
	}

	@Override
	public void removeTags(String... tags) {
		for (String tag : tags) {
			this.tags.remove(tag);
		}
	}

	@Override
	public Set<String> getTags() {
		return this.tags;
	}

	@Override
	public BlockLocation getLocation() {
		MinecartMember<?> member = this.getHolder();
		if (member != null) {
			return new BlockLocation(member.getEntity().getLocation().getBlock());
		} else {
			// Offline member?
			OfflineMember omember = OfflineGroupManager.findMember(this.getTrainProperties().getTrainName(), this.getUUID());
			if (omember == null) {
				return null;
			} else {
				// Find world
				World world = Bukkit.getWorld(omember.group.worldUUID);
				if (world == null) {
					return new BlockLocation("Unknown", omember.cx << 4, 0, omember.cz << 4);
				} else {
					return new BlockLocation(world, omember.cx << 4, 0, omember.cz << 4);
				}
			}
		}
	}

	/**
	 * Tests whether the Minecart has block types it can break
	 * 
	 * @return True if materials are contained, False if not
	 */
	public boolean hasBlockBreakTypes() {
		return !this.blockBreakTypes.isEmpty();
	}

	/**
	 * Clears all the materials this Minecart can break
	 */
	public void clearBlockBreakTypes() {
		this.blockBreakTypes.clear();
	}

	/**
	 * Gets a Collection of materials this Minecart can break
	 * 
	 * @return a Collection of blocks that are broken
	 */
	public Collection<Material> getBlockBreakTypes() {
		return this.blockBreakTypes;
	}

	/**
	 * Gets the Enter message that is currently displayed when a player enters
	 * 
	 * @return Enter message
	 */
	public String getEnterMessage() {
		return this.enterMessage;
	}

	/**
	 * Gets whether an Enter message is set
	 * 
	 * @return True if a message is set, False if not
	 */
	public boolean hasEnterMessage() {
		return this.enterMessage != null && !this.enterMessage.equals("");
	}

	/**
	 * Shows the enter message to the player specified
	 * 
	 * @param player to display the message to
	 */
	public void showEnterMessage(Player player) {
		if (this.hasEnterMessage()) {
			TrainCarts.sendMessage(player, ChatColor.YELLOW + TrainCarts.getMessage(enterMessage));
		}
	}

	@Override
	public void setEnterMessage(String message) {
		this.enterMessage = message;
	}

	public void clearDestination() {
		this.destination = "";
	}

	@Override
	public boolean hasDestination() {
		return this.destination.length() != 0;
	}

	@Override
	public void setDestination(String destination) {
		this.destination = destination == null ? "" : destination;
	}

	@Override
	public String getDestination() {
		return this.destination;
	}

	@Override
	public String getLastPathNode() {
		return this.lastPathNode;
	}

	@Override
	public void setLastPathNode(String nodeName) {
		this.lastPathNode = nodeName;
	}

	@Override
	public boolean parseSet(String key, String arg) {
		if (key.equals("addtag")) {
			this.addTags(arg);
		} else if (key.equals("settag")) {
			this.setTags(arg);
		} else if (key.equals("destination")) {
			this.setDestination(arg);
		} else if (key.equals("remtag")) {
			this.removeTags(arg);
		} else if (key.equals("playerenter")) {
			this.setPlayersEnter(ParseUtil.parseBool(arg));
		} else if (key.equals("playerexit")) {
			this.setPlayersExit(ParseUtil.parseBool(arg));
		} else if (key.equals("setowner")) {
			arg = arg.toLowerCase();
			this.getOwners().clear();
			this.getOwners().add(arg);
		} else if (key.equals("addowner")) {
			arg = arg.toLowerCase();
			this.getOwners().add(arg);
		} else if (key.equals("remowner")) {
			arg = arg.toLowerCase();
			this.getOwners().remove(arg);
		} else {
			return false;
		}
		return true;
	}

	/**
	 * Loads the information from the properties specified
	 * 
	 * @param properties to load from
	 */
	public void load(CartProperties from) {
		this.destination = from.destination;
		this.owners.clear();
		this.owners.addAll(from.owners);
		this.tags.clear();
		this.tags.addAll(from.tags);
		this.allowPlayerEnter = from.allowPlayerEnter;
		this.allowPlayerExit = from.allowPlayerExit;
	}

	@Override
	public void load(ConfigurationNode node) {
		for (String owner : node.getList("owners", String.class)) {
			this.owners.add(owner.toLowerCase());
		}
		for (String tag : node.getList("tags", String.class)) {
			this.tags.add(tag);
		}
		this.destination = node.get("destination", this.destination);
		this.lastPathNode = node.get("lastPathNode", this.lastPathNode);
		this.allowPlayerEnter = node.get("allowPlayerEnter", this.allowPlayerEnter);
		this.allowPlayerExit = node.get("allowPlayerExit", this.allowPlayerExit);
		this.isPublic = node.get("isPublic", this.isPublic);
		this.pickUp = node.get("pickUp", this.pickUp);
		for (String blocktype : node.getList("blockBreakTypes", String.class)) {
			Material mat = ParseUtil.parseMaterial(blocktype, null);
			if (mat != null) {
				this.blockBreakTypes.add(mat);
			}
		}
	}

	@Override
	public void saveAsDefault(ConfigurationNode node) {
		node.set("owners", new ArrayList<String>(this.owners));
		node.set("tags", new ArrayList<String>(this.tags));
		node.set("allowPlayerEnter", this.allowPlayerEnter);
		node.set("allowPlayerExit", this.allowPlayerExit);
		node.set("isPublic", this.isPublic);
		node.set("pickUp", this.pickUp);
		List<String> items = node.getList("blockBreakTypes", String.class);
		for (Material mat : this.blockBreakTypes) {
			items.add(mat.toString());
		}
		node.set("destination", this.hasDestination() ? this.destination : "");
		node.set("enterMessage", this.hasEnterMessage() ? this.enterMessage : "");
	}

	@Override
	public void save(ConfigurationNode node) {
		node.set("owners", this.owners.isEmpty() ? null : new ArrayList<String>(this.owners));
		node.set("tags", this.tags.isEmpty() ? null : new ArrayList<String>(this.tags));
		node.set("allowPlayerEnter", this.allowPlayerEnter ? null : false);
		node.set("allowPlayerExit", this.allowPlayerExit ? null : false);	
		node.set("isPublic", this.isPublic ? null : false);
		node.set("pickUp", this.pickUp ? true : null);
		if (this.blockBreakTypes.isEmpty()) {
			node.remove("blockBreakTypes");
		} else {
			List<String> items = node.getList("blockBreakTypes", String.class);
			for (Material mat : this.blockBreakTypes) {
				items.add(mat.toString());
			}
		}
		node.set("destination", this.hasDestination() ? this.destination : null);
		node.set("lastPathNode", LogicUtil.nullOrEmpty(this.lastPathNode) ? null : this.lastPathNode);
		node.set("enterMessage", this.hasEnterMessage() ? this.enterMessage : null);
	}

	@Override
	public boolean getPlayersEnter() {
		return this.allowPlayerEnter;
	}

	@Override
	public void setPlayersEnter(boolean state) {
		this.allowPlayerEnter = state;
	}

	@Override
	public boolean getPlayersExit() {
		return this.allowPlayerExit;
	}

	@Override
	public void setPlayersExit(boolean state) {
		this.allowPlayerExit = state;
	}
}
