package com.bergerkiller.bukkit.tc.properties;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.storage.OfflineGroup;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.SoftReference;

public class TrainProperties extends TrainPropertiesStore implements IProperties {
	private static final long serialVersionUID = 1L;
	public static final TrainProperties EMPTY = new TrainProperties("");
	static {
		EMPTY.add(CartProperties.EMPTY);
	}

	protected String trainname;	
	private String displayName;
	private boolean collision = true;
	private boolean slowDown = true;
	private double speedLimit = 0.4;
	private boolean keepChunksLoaded = false;
	private boolean allowManualMovement = false;
	private boolean allowPlayerTake = true;
	private boolean soundEnabled = true;
	public CollisionMode mobCollision = CollisionMode.DEFAULT;
	public CollisionMode playerCollision = CollisionMode.DEFAULT;
	public CollisionMode miscCollision = CollisionMode.PUSH;
	public CollisionMode trainCollision = CollisionMode.LINK;
	public boolean requirePoweredMinecart = false;
	private final SoftReference<MinecartGroup> group = new SoftReference<MinecartGroup>();

	protected TrainProperties(String trainname) {
		this.displayName = this.trainname = trainname;
	}

	@Override
	public String getTypeName() {
		return "train";
	}

	@Override
	public MinecartGroup getHolder() {
		MinecartGroup group = this.group.get();
		if (group == null || group.isRemoved()) {
			return this.group.set(MinecartGroup.get(this));
		} else {
			return group;
		}
	}

	@Override
	public boolean hasHolder() {
		return getHolder() != null;
	}

	@Override
	public boolean restore() {
		if (this.isLoaded()) {
			return true;
		}
		// Load all the chunks of this group to trigger a restore
		OfflineGroup group = OfflineGroupManager.findGroup(this.trainname);
		if (group == null) {
			TrainProperties.remove(getTrainName());
			return false;
		}
		World world = Bukkit.getWorld(group.worldUUID);
		if (world != null) {
			for (long chunk : group.chunks) {
				world.getChunkAt(MathUtil.longHashMsw(chunk), MathUtil.longHashLsw(chunk));
			}
		}
		return this.hasHolder();
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
		this.speedLimit = MathUtil.clamp(limit, 0, TrainCarts.maxVelocity);
	}

	/**
	 * Gets whether the Train slows down over time
	 * 
	 * @return True if it slows down, False if not
	 */
	public boolean isSlowingDown() {
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
		return this.collision;
	}

	/**
	 * Sets whether this Train can collide with other Entities and Trains
	 * 
	 * @param state to set to
	 */
	public void setColliding(boolean state) {
		this.collision = state;
	}

	/**
	 * Sets the Display Name for these properties<br>
	 * If a null or empty String is passed in as argument, the display name is set to 
	 * the train name. (it is reset)
	 * 
	 * @param displayName to set to
	 */
	public void setDisplayName(String displayName) {
		if (displayName == null || displayName.isEmpty()) {
			this.displayName = this.trainname;
		} else {
			this.displayName = displayName;
		}
	}

	/**
	 * Gets the Display Name of these properties
	 * 
	 * @return display name
	 */
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Gets whether this Train keeps nearby chunks loaded
	 * 
	 * @return True or False
	 */
	public boolean isKeepingChunksLoaded() {
		return this.keepChunksLoaded;
	}

	/**
	 * Sets whether this Train keeps nearby chunks loaded
	 * 
	 * @param state to set to
	 */
	public void setKeepChunksLoaded(boolean state) {
		if (state && !this.keepChunksLoaded) {
			restore();
		}
		this.keepChunksLoaded = state;
	}
	
	/**
	 * Sets whether ambient Minecart sounds are enabled
	 * 
	 * @param enabled state to set to
	 */
	public void setSoundEnabled(boolean enabled) {
		this.soundEnabled = enabled;
	}

	/**
	 * Gets whether ambient Minecart sounds are enabled
	 * 
	 * @return True if enabled, False if not
	 */
	public boolean isSoundEnabled() {
		return soundEnabled;
	}

	/*
	 * Carts
	 */
	public void add(MinecartMember<?> member) {
		this.add(member.getProperties());
	}

	@Override
	public boolean remove(Object o) {
		if (o instanceof MinecartMember<?>) {
			return super.remove(((MinecartMember<?>) o).getProperties());
		} else {
			return super.remove(o);
		}
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
	@Override
	public boolean isOwnedByEveryone() {
		return !this.hasOwners() && !this.hasOwnerPermissions();
	}

	@Override
	public boolean hasOwners() {
		for (CartProperties prop : this) {
			if (prop.hasOwners()) return true;
		}
		return false;
	}

	@Override
	public boolean hasOwnership(Player player) {
		return CartProperties.hasGlobalOwnership(player) || this.isOwnedByEveryone() || this.isOwner(player);
	}

	@Override
	public boolean isOwner(Player player) {
		for (CartProperties prop : this) {
			if (prop.isOwner(player)) {
				return true;
			}
		}
		return false;
	}	

	@Override
	public boolean hasOwnerPermissions() {
		for (CartProperties prop : this) {
			if (prop.hasOwnerPermissions()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Set<String> getOwnerPermissions() {
		Set<String> rval = new HashSet<String>();
		for (CartProperties cprop : this) {
			rval.addAll(cprop.getOwnerPermissions());
		}
		return rval;
	}

	@Override
	public Set<String> getOwners() {
		Set<String> rval = new HashSet<String>();
		for (CartProperties cprop : this) {
			rval.addAll(cprop.getOwners());
		}
		return rval;
	}

	@Override
	public void clearOwners() {
		for (CartProperties prop : this) {
			prop.clearOwners();
		}
	}

	@Override
	public void clearOwnerPermissions() {
		for (CartProperties prop : this) {
			prop.clearOwnerPermissions();
		}
	}

	/**
	 * Sets whether this Train supports players taking minecarts with them when they leave.
	 * When the Minecart is part of a Train, it is always disallowed.
	 * 
	 * @param takeable state to set to
	 */
	public void setPlayerTakeable(boolean takeable) {
		this.allowPlayerTake = takeable;
	}

	/**
	 * Gets whether this Train supports players taking minecarts with them when they leave.
	 * When the Minecart is part of a Train, it is always disallowed.
	 * 
	 * @return True if players can take Minecarts with them, False if not.
	 */
	public boolean isPlayerTakeable() {
		return allowPlayerTake;
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
		Set<String> tags = new HashSet<String>();
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
	public void setInvincible(boolean enabled) {
		for (CartProperties prop : this) {
			prop.setInvincible(enabled);
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
	public boolean isInvincible() {
		for (CartProperties prop : this) {
			if (prop.isInvincible()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void setSpawnItemDrops(boolean spawnDrops) {
		for (CartProperties prop : this) {
			prop.setSpawnItemDrops(spawnDrops);
		}
	}

	@Override
	public boolean getSpawnItemDrops() {
		for (CartProperties prop : this) {
			if (prop.getSpawnItemDrops()) {
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

	@Override
	public String getLastPathNode() {
		return this.isEmpty() ? "" : this.get(0).getLastPathNode();
	}

	@Override
	public void setLastPathNode(String nodeName) {
		for (CartProperties cprop : this) {
			cprop.setLastPathNode(nodeName);
		}
	}

	/**
	 * Gets the Collision Mode for colliding with the Entity specified
	 * 
	 * @param entity to collide with
	 * @return Collision Mode
	 */
	public CollisionMode getCollisionMode(Entity entity) {
		if (!this.getColliding() || entity.isDead()) {
			return CollisionMode.CANCEL;
		}
		MinecartMember<?> member = MinecartMemberStore.get(entity);
		if (member != null) {
			if (this.trainCollision == CollisionMode.LINK) {
				if (member.getGroup().getProperties().trainCollision == CollisionMode.LINK) {
					return CollisionMode.LINK;
				} else {
					return CollisionMode.CANCEL;
				}
			} else {
				return this.trainCollision;
			}
		} else if (entity instanceof Player) {
			if (TrainCarts.collisionIgnoreOwners && this.playerCollision != CollisionMode.DEFAULT) {
				if (TrainCarts.collisionIgnoreGlobalOwners) {
					if (CartProperties.hasGlobalOwnership((Player) entity)) {
						return CollisionMode.DEFAULT;
					}
				}
				if (this.hasOwnership((Player) entity)) {
					return CollisionMode.DEFAULT;
				}
			}
			return this.playerCollision;
		} else if (EntityUtil.isMob(entity)) {
			return this.mobCollision;
		} else {
			return this.miscCollision;
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

	/**
	 * Gets whether minecart passengers can manually move the train they are in
	 * 
	 * @return True if manual movement is allowed, False if not
	 */
	public boolean isManualMovementAllowed() {
		return this.allowManualMovement;
	}

	/**
	 * Sets whether minecart passengers can manually move the train they are in
	 * 
	 * @param allow state to set to
	 */
	public void setManualMovementAllowed(boolean allow) {
		this.allowManualMovement = allow;
	}

	public boolean isTrainRenamed() {
		return !this.trainname.startsWith("train") || !ParseUtil.isNumeric(this.trainname.substring(5));
	}

	public boolean isLoaded() {
		return this.hasHolder();
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
		MinecartGroup g = this.getHolder();
		if (g != null) g.onPropertiesChanged();
	}

	@Override
	public boolean parseSet(String key, String arg) {
		if (key.equals("exitoffset")) {
			Vector vec = Util.parseVector(arg, null);
			if (vec != null) {
				for (CartProperties prop : this) {
					prop.exitOffset = vec;
				}
			}
		} else if (key.equals("exityaw")) {
			float yaw = ParseUtil.parseFloat(arg, 0.0f);
			for (CartProperties prop : this) {
				prop.exitYaw = yaw;
			}
		} else if (key.equals("exitpitch")) {
			float pitch = ParseUtil.parseFloat(arg, 0.0f);
			for (CartProperties prop : this) {
				prop.exitPitch = pitch;
			}
		} else if (LogicUtil.contains(key, "exitrot", "exitrotation")) {
			String[] angletext = Util.splitBySeparator(arg);
			float yaw = 0.0f;
			float pitch = 0.0f;
			if (angletext.length == 2) {
				yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
				pitch = ParseUtil.parseFloat(angletext[1], 0.0f);
			} else if (angletext.length == 1) {
				yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
			}
			for (CartProperties prop : this) {
				prop.exitYaw = yaw;
				prop.exitPitch = pitch;
			}
		} else if (key.equals("sound") || key.equals("minecartsound")) {
			this.soundEnabled = ParseUtil.parseBool(arg);
		} else if (key.equals("mobcollision")) {
			this.mobCollision = CollisionMode.parse(arg);
		} else if (key.equals("playercollision")) {
			this.playerCollision = CollisionMode.parse(arg);
		} else if (key.equals("misccollision")) {
			this.miscCollision = CollisionMode.parse(arg);
		} else if (key.equals("traincollision")) {
			this.trainCollision = CollisionMode.parse(arg);
		} else if (LogicUtil.contains(key, "collision", "collide")) {
			this.setColliding(ParseUtil.parseBool(arg));
		} else if (LogicUtil.contains(key, "linking", "link")) {
			this.trainCollision = CollisionMode.fromLinking(ParseUtil.parseBool(arg));
		} else if (LogicUtil.contains(key, "slow", "slowdown")) {
			this.setSlowingDown(ParseUtil.parseBool(arg));
		} else if (LogicUtil.contains(key, "setdefault", "default")) {
			this.setDefault(arg);
		} else if (key.equals("pushmobs")) {
			this.mobCollision = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
		} else if (key.equals("pushplayers")) {
			this.playerCollision = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
		} else if (key.equals("pushmisc")) {
			this.miscCollision = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
		} else if (LogicUtil.contains(key, "push", "pushing")) {
			CollisionMode mode = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
			this.playerCollision = this.mobCollision = this.miscCollision = mode;
		} else if (LogicUtil.contains(key, "speedlimit", "maxspeed")) {
			this.setSpeedLimit(ParseUtil.parseDouble(arg, 0.4));
		} else if (LogicUtil.contains(key, "allowmanual", "manualmove", "manual")) {
			this.allowManualMovement = ParseUtil.parseBool(arg);
		} else if (LogicUtil.contains(key, "keepcloaded", "loadchunks", "keeploaded")) {
			this.keepChunksLoaded = ParseUtil.parseBool(arg);
		} else if (key.equals("addtag")) {
			this.addTags(arg);
		} else if (key.equals("settag")) {
			this.setTags(arg);
		} else if (key.equals("destination")) {
			this.setDestination(arg);
		} else if (key.equals("remtag")) {
			this.removeTags(arg);
		} else if (LogicUtil.contains(key, "name", "rename", "setname")) {
			this.setName(generateTrainName(arg));
		} else if (LogicUtil.contains(key, "dname", "displayname", "setdisplayname", "setdname")) {
			this.setDisplayName(arg);
		} else if (LogicUtil.contains(key, "mobenter", "mobsenter")) {
			this.mobCollision = CollisionMode.fromEntering(ParseUtil.parseBool(arg));
		} else if (key.equals("playerenter")) {
			this.setPlayersEnter(ParseUtil.parseBool(arg));
		} else if (key.equals("playerexit")) {
			this.setPlayersExit(ParseUtil.parseBool(arg));
		} else if(LogicUtil.contains(key, "invincible", "godmode")) {
			this.setInvincible(ParseUtil.parseBool(arg));
		} else if (key.equals("setownerperm")) {
			for (CartProperties prop : this) {
				prop.clearOwnerPermissions();
				prop.getOwnerPermissions().add(arg);
			}
		} else if (key.equals("addownerperm")) {
			for (CartProperties prop : this) {
				prop.getOwnerPermissions().add(arg);
			}
		} else if (key.equals("remownerperm")) {
			for (CartProperties prop : this) {
				prop.getOwnerPermissions().remove(arg);
			}
		} else if (key.equals("setowner")) {
			arg = arg.toLowerCase();
			for (CartProperties cprop : this) {
				cprop.clearOwners();
				cprop.getOwners().add(arg);
			}
		} else if (key.equals("addowner")) {
			arg = arg.toLowerCase();
			for (CartProperties cprop : this) {
				cprop.getOwners().add(arg);
			}
		} else if (key.equals("remowner")) {
			arg = arg.toLowerCase();
			for (CartProperties cprop : this) {
				cprop.getOwners().remove(arg);
			}
		} else if (LogicUtil.contains(key, "spawnitemdrops", "spawndrops", "killdrops")) {
			this.setSpawnItemDrops(ParseUtil.parseBool(arg));
		} else {
			return false;
		}
		return true;
	}

	@Override
	public void load(ConfigurationNode node) {
		this.setDisplayName(node.get("displayName", this.displayName));
		this.allowPlayerTake = node.get("allowPlayerTake", this.allowPlayerTake);
		this.collision = node.get("trainCollision", this.collision);
		this.soundEnabled = node.get("soundEnabled", this.soundEnabled);
		this.slowDown = node.get("slowDown", this.slowDown);
		if (node.contains("collision")) {
			this.mobCollision = node.get("collision.mobs", this.mobCollision);
			this.playerCollision = node.get("collision.players", this.playerCollision);
			this.miscCollision = node.get("collision.misc", this.miscCollision);
			this.trainCollision = node.get("collision.train", this.trainCollision);
		}
		this.speedLimit = MathUtil.clamp(node.get("speedLimit", this.speedLimit), 0, 20);
		this.requirePoweredMinecart = node.get("requirePoweredMinecart", this.requirePoweredMinecart);
		this.keepChunksLoaded = node.get("keepChunksLoaded", this.keepChunksLoaded);
		this.allowManualMovement = node.get("allowManualMovement", this.allowManualMovement);
		if (node.isNode("carts")) {
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
	}

	/**
	 * Loads the properties from the TrainProperties source specified<br>
	 * Cart properties are not transferred or cleared!
	 * 
	 * @param source to load from
	 */
	public void load(TrainProperties source) {
		this.soundEnabled = source.soundEnabled;
		this.displayName = source.displayName;
		this.collision = source.collision;
		this.slowDown = source.slowDown;
		this.mobCollision = source.mobCollision;
		this.playerCollision = source.playerCollision;
		this.miscCollision = source.miscCollision;
		this.trainCollision = source.trainCollision;
		this.speedLimit = MathUtil.clamp(source.speedLimit, 0, 20);
		this.requirePoweredMinecart = source.requirePoweredMinecart;
		this.keepChunksLoaded = source.keepChunksLoaded;
		this.allowManualMovement = source.allowManualMovement;
	}

	@Override
	public void saveAsDefault(ConfigurationNode node) {
		node.set("soundEnabled", this.soundEnabled);
		node.set("displayName", this.displayName);
		node.set("allowPlayerTake", this.allowPlayerTake);
		node.set("requirePoweredMinecart", this.requirePoweredMinecart);
		node.set("trainCollision", this.collision);
		node.set("keepChunksLoaded", this.keepChunksLoaded);
		node.set("speedLimit", this.speedLimit);
		node.set("slowDown", this.slowDown);
		node.set("allowManualMovement", this.allowManualMovement);
		node.set("collision.mobs", this.mobCollision);
		node.set("collision.players", this.playerCollision);
		node.set("collision.misc", this.miscCollision);
		node.set("collision.train", this.trainCollision);
		for (CartProperties prop : this) {
			prop.saveAsDefault(node);
			break;
		}
	}

	@Override
	public void save(ConfigurationNode node) {	
		node.set("displayName", this.displayName.equals(this.trainname) ? null : this.displayName);
		node.set("soundEnabled", this.soundEnabled ? null : false);
		node.set("allowPlayerTake", this.allowPlayerTake ? null : false);
		node.set("requirePoweredMinecart", this.requirePoweredMinecart ? true : null);
		node.set("trainCollision", this.collision ? null : false);
		node.set("keepChunksLoaded", this.keepChunksLoaded ? true : null);
		node.set("speedLimit", this.speedLimit != 0.4 ? this.speedLimit : null);
		node.set("slowDown", this.slowDown ? null : false);
		node.set("allowManualMovement",allowManualMovement ? true : null);
		if (this.mobCollision != CollisionMode.DEFAULT) {
			node.set("collision.mobs", this.mobCollision);
		}
		if (this.playerCollision != CollisionMode.DEFAULT) {
			node.set("collision.players", this.playerCollision);
		}
		if (this.miscCollision != CollisionMode.DEFAULT) {
			node.set("collision.misc", this.miscCollision);
		}
		if (this.trainCollision != CollisionMode.LINK) {
			node.set("collision.train", this.trainCollision);
		}
		if (!this.isEmpty()) {
			ConfigurationNode carts = node.getNode("carts");
			for (CartProperties prop : this) {
				ConfigurationNode cart = carts.getNode(prop.getUUID().toString());
				prop.save(cart);
				if (cart.getKeys().isEmpty()) carts.remove(cart.getName());
			}
		}
	}
}
