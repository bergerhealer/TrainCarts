package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.utils.SoftReference;

public class TrainProperties extends TrainPropertiesStore implements IProperties {
	private static final long serialVersionUID = 1L;
	public static final TrainProperties EMPTY = new TrainProperties("");

	protected String trainname;	
	private String displayName;
	private boolean allowLinking = true;
	private boolean trainCollision = true;
	private boolean slowDown = true;
	private double speedLimit = 0.4;
	public boolean pushMobs = false;
	public boolean pushPlayers = false;
	public boolean pushMisc = true;
	public boolean requirePoweredMinecart = false;
	public boolean keepChunksLoaded = false;
	private SoftReference<MinecartGroup> group = new SoftReference<MinecartGroup>();

	protected TrainProperties(String trainname) {
		this.displayName = this.trainname = trainname;
		this.setDefault();
	}

	/**
	 * Gets the Group associated with these Properties
	 * 
	 * @return the MinecartGroup these properties belong to, or null if not loaded
	 */
	public MinecartGroup getGroup() {
		MinecartGroup group = this.group.get();
		if (group == null || group.isRemoved()) {
			return this.group.set(MinecartGroup.get(this.trainname));
		} else {
			return group;
		}
	}

	/**
	 * Gets the maximum speed this Train can move at
	 * 
	 * @return max speed in blocks/tick
	 */
	public double getSpeedLimit() {
		return this.speedLimit;
	}

	/**
	 * Sets the maximum speed this Train can move at<br>
	 * The maximum speed limit is enforced.
	 * 
	 * @param limit in blocks/tick
	 */
	public void setSpeedLimit(double limit) {
		this.speedLimit = Math.min(limit, TrainCarts.maxVelocity);
	}

	/**
	 * Gets whether the Train slows down over time
	 * 
	 * @return True if it slows down, False if not
	 */
	public boolean getSlowingDown() {
		return this.slowDown;
	}

	/**
	 * Sets whether the Train slows down over time
	 * 
	 * @param slowingDown state to set to
	 */
	public void setSlowingDown(boolean slowingDown) {
		this.slowDown = slowingDown;
	}
	
	/**
	 * Gets whether this Train can collide with other Entities and Trains
	 * 
	 * @return True if it can collide, False if not
	 */
	public boolean getColliding() {
		return this.trainCollision;
	}

	/**
	 * Sets whether this Train can collide with other Entities and Trains
	 * 
	 * @param state to set to
	 */
	public void setColliding(boolean state) {
		this.trainCollision = state;
	}

	/**
	 * Gets whether this Train can link to other trains
	 * 
	 * @return linking state
	 */
	public boolean getLinking() {
		return this.allowLinking;
	}

	/**
	 * Sets whether this Train can link to other trains
	 * 
	 * @param linking state to set to
	 */
	public void setLinking(boolean linking) {
		this.allowLinking = linking;
	}

	/**
	 * Sets the Display Name for these properties
	 * 
	 * @param displayName to set to
	 */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * Gets the Display Name of these properties
	 * 
	 * @return display name
	 */
	public String getDisplayName() {
		return this.displayName;
	}

	/*
	 * Carts
	 */
	public void add(MinecartMember member) {
		this.add(member.getProperties());
	}

	public void remove(MinecartMember member) {
		this.remove(member.getProperties());
	}

	@Override
	public boolean add(CartProperties properties) {
		properties.group = this;
		return super.add(properties);
	}

	public CartProperties get(int index) {
		for (CartProperties prop : this) {
			if (index-- == 0) {
				return prop;
			}
		}
		throw new IndexOutOfBoundsException("No cart properties found at index " + index);
	}

	@Override
	public void setPickup(boolean pickup) {
		for (CartProperties prop : this) {
			prop.setPickup(pickup);
		}
	}

	/*
	 * Owners
	 */
	public boolean hasOwners() {
		for (CartProperties prop : this) {
			if (prop.hasOwners()) return true;
		}
		return false;
	}
	public boolean hasOwnership(Player player) {
        if (!CartProperties.canHaveOwnership(player)) return false;
        if (CartProperties.hasGlobalOwnership(player)) return true;
        if (!this.hasOwners()) return true;
        return this.isOwner(player);
	}
	public boolean isOwner(Player player) {
		boolean hasowner = false;
		for (CartProperties prop : this) {
			if (prop.isOwner(player)) return true;
			if (prop.hasOwners()) hasowner = true;
		}
		return !hasowner;
	}	
	public boolean isDirectOwner(Player player) {
		return this.isDirectOwner(player.getName().toLowerCase());
	}
	public boolean isDirectOwner(String player) {
		for (CartProperties prop : this) {
			if (prop.isOwner(player)) return true;
		}
		return false;
	}
	public Set<String> getOwners() {
		Set<String> rval = new HashSet<String>();
		for (CartProperties cprop : this) {
			rval.addAll(cprop.getOwners());
		}
		return rval;
	}

	@Override
	public void setPublic(boolean state) {
		for (CartProperties prop : this) {
			prop.setPublic(state);
		}
	}

	@Override
	public boolean isPublic() {
		for (CartProperties prop : this) {
			if (prop.isPublic()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void setEnterMessage(String message) {
		for (CartProperties prop : this) {
			prop.setEnterMessage(message);
		}
	}

	@Override
	public boolean matchTag(String tag) {
		for (CartProperties prop : this) {
			if (prop.matchTag(tag)) return true;
		}
		return false;
	}

	@Override
	public boolean hasTags() {
		for (CartProperties prop : this) {
			if (prop.hasTags()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Collection<String> getTags() {
		List<String> tags = new ArrayList<String>();
		for (CartProperties prop : this) {
			tags.addAll(prop.getTags());
		}
		return tags;
	}

	@Override
	public void clearTags() {
		for (CartProperties prop : this) {
			prop.clearTags();
		}
	}

	@Override
	public void setTags(String... tags) {
		for (CartProperties prop : this) {
			prop.setTags(tags);
		}
	}

	@Override
	public void addTags(String... tags) {
		for (CartProperties prop : this) {
			prop.addTags(tags);
		}
	}

	@Override
	public void removeTags(String... tags) {
		for (CartProperties prop : this) {
			prop.removeTags(tags);
		}
	}

	@Override
	public void setPlayersEnter(boolean state) {
		for (CartProperties prop : this) {
		    prop.setPlayersEnter(state);
		}
	}

	@Override
	public void setPlayersExit(boolean state) {
		for (CartProperties prop : this) {
		    prop.setPlayersExit(state);
		}
	}

	@Override
	public void setMobsEnter(boolean state) {
		for (CartProperties prop : this) {
		    prop.setMobsEnter(state);
		}
	}

	@Override
	public boolean getPlayersEnter() {
		for (CartProperties prop : this) {
			if (prop.getPlayersEnter()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean getPlayersExit() {
		for (CartProperties prop : this) {
			if (prop.getPlayersExit()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean getMobsEnter() {
		for (CartProperties prop : this) {
			if (prop.getMobsEnter()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean hasDestination() {
		for (CartProperties prop : this) {
			if (prop.hasDestination()) return true;
		}
		return false;
	}

	@Override
	public String getDestination() {
		for (CartProperties prop : this) {
			if (prop.hasDestination()) return prop.getDestination();
		}
		return "";
	}

	@Override
	public void setDestination(String destination) {
		for (CartProperties prop : this) {
			prop.setDestination(destination);
		}
	}

	@Override
	public void clearDestination() {
		for (CartProperties prop : this) {
			prop.clearDestination();
		}
	}

	/*
	 * Push away settings
	 */
	public boolean canPushAway(Entity entity) {
		if (entity instanceof Player) {
			if (this.pushPlayers) {
				if (!TrainCarts.pushAwayIgnoreOwners) return true;
				if (TrainCarts.pushAwayIgnoreGlobalOwners) {
					if (CartProperties.hasGlobalOwnership((Player) entity)) return false;
				}
				return !this.isOwner((Player) entity);
			}
		} else if (entity instanceof Creature || entity instanceof Slime || entity instanceof Ghast) {
			if (this.pushMobs) return true;
		} else {
			if (this.pushMisc) return true;
		}
		return false;
	}

	/*
	 * Collision settings
	 */
	public boolean canCollide(MinecartMember with) {
		return this.canCollide(with.getGroup());
	}
	public boolean canCollide(MinecartGroup with) {
		return this.trainCollision && with.getProperties().trainCollision;
	}
	public boolean canCollide(Entity with) {
		MinecartMember mm = MinecartMember.get(with);
		if (mm == null) {
			if (this.trainCollision) return true;
			if (with instanceof Player) {
				return this.isOwner((Player) with);
			} else {
				return false;
			}
		} else {
			return this.canCollide(mm);
		}
	}

	public String getTrainName() {
		return this.trainname;
	}

	/**
	 * Renames this train, this should be called to rename the train safely
	 * @param newtrainname to set to
	 * @return this
	 */
	public TrainProperties setName(String newtrainname) {
		rename(this, newtrainname);
		return this;
	}

	public boolean isTrainRenamed() {
		if (this.trainname.startsWith("train")) {
			try {
				Integer.parseInt(this.trainname.substring(5));
			} catch (NumberFormatException ex) {return true;}
		}
		return false;
	}

	public boolean isLoaded() {
		return this.getGroup() != null;
	}

	public boolean matchName(String expression) {
		return Util.matchText(this.getTrainName(), expression);
	}

	public boolean matchName(String[] expressionElements, boolean firstAny, boolean lastAny) {
		return Util.matchText(this.getTrainName(), expressionElements, firstAny, lastAny);
	}

	public BlockLocation getLocation() {
		for (CartProperties prop : this) {
			return prop.getLocation();
		}
		return null;
	}

	public void setDefault() {
		setDefault("default");
	}

	public void setDefault(String key) {
		this.setDefault(getDefaultsByName(key));
	}

	public void setDefault(ConfigurationNode node) {
		if (node == null) {
			return;
		}
		this.load(node);
		for (CartProperties prop : this) {
			prop.load(node);
		}
	}

	public void setDefault(Player player) {
		if (player == null) {
			//Set default
			this.setDefault();
		} else {
			//Load it
			this.setDefault(getDefaultsByPlayer(player));
		}
	}

	public void tryUpdate() {
		MinecartGroup g = this.getGroup();
		if (g != null) g.update();
	}

	@Override
	public void load(ConfigurationNode node) {
		this.displayName = node.get("displayName", this.displayName);
		this.allowLinking = node.get("allowLinking", this.allowLinking);
		this.trainCollision = node.get("trainCollision", this.trainCollision);
		this.slowDown = node.get("slowDown", this.slowDown);
		this.pushMobs = node.get("pushAway.mobs", this.pushMobs);
		this.pushPlayers = node.get("pushAway.players", this.pushPlayers);
		this.pushMisc = node.get("pushAway.misc", this.pushMisc);
		this.speedLimit = MathUtil.limit(node.get("speedLimit", this.speedLimit), 0, 20);
		this.requirePoweredMinecart = node.get("requirePoweredMinecart", this.requirePoweredMinecart);
		this.keepChunksLoaded = node.get("keepChunksLoaded", this.keepChunksLoaded);
		for (ConfigurationNode cart : node.getNode("carts").getNodes()) {
			try {
				CartProperties prop = CartProperties.get(UUID.fromString(cart.getName()), this);
				this.add(prop);
				prop.load(cart);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Loads the properties from the TrainProperties source specified
	 * 
	 * @param source to load from
	 */
	public void load(TrainProperties source) {
		this.displayName = source.displayName;
		this.allowLinking = source.allowLinking;
		this.trainCollision = source.trainCollision;
		this.slowDown = source.slowDown;
		this.pushMobs = source.pushMobs;
		this.pushPlayers = source.pushPlayers;
		this.pushMisc = source.pushMisc;
		this.speedLimit = MathUtil.limit(source.speedLimit, 0, 20);
		this.requirePoweredMinecart = source.requirePoweredMinecart;
		this.keepChunksLoaded = source.keepChunksLoaded;
		this.clear();
		this.addAll(source);
	}

	@Override
	public void save(ConfigurationNode node, boolean minimal) {	
		if (minimal) {
			node.set("displayName", this.displayName.equals(this.trainname) ? null : this.displayName);
			node.set("allowLinking", this.allowLinking ? null : false);
			node.set("requirePoweredMinecart", this.requirePoweredMinecart ? true : null);
			node.set("trainCollision", this.trainCollision ? null : false);
			node.set("keepChunksLoaded", this.keepChunksLoaded ? true : null);
			node.set("speedLimit", this.speedLimit != 0.4 ? this.speedLimit : null);
			node.set("slowDown", this.slowDown ? null : false);
			if (this.pushMobs || this.pushPlayers || !this.pushMisc) {
				node.set("pushAway.mobs", this.pushMobs ? true : null);
				node.set("pushAway.players", this.pushPlayers ? true : null);
				node.set("pushAway.misc", this.pushMisc ? null : false);
			} else {
				node.remove("pushAway");
			}
		} else {
			node.set("displayName", this.displayName);
			node.set("allowLinking", this.allowLinking);
			node.set("requirePoweredMinecart", this.requirePoweredMinecart);
			node.set("trainCollision", this.trainCollision);
			node.set("keepChunksLoaded", this.keepChunksLoaded);
			node.set("speedLimit", this.speedLimit);
			node.set("slowDown", this.slowDown);
			node.set("pushAway.mobs", this.pushMobs);
			node.set("pushAway.players", this.pushPlayers);
			node.set("pushAway.misc", this.pushMisc);
		}
		if (!this.isEmpty()) {
			ConfigurationNode carts = node.getNode("carts");
			for (CartProperties prop : this) {
				ConfigurationNode cart = carts.getNode(prop.getUUID().toString());
				prop.save(cart, minimal);
				if (cart.getKeys().isEmpty()) carts.remove(cart.getName());
			}
		}
	}
}
