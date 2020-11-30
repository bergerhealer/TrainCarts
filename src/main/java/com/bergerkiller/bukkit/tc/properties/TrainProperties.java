package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.collision.CollisionConfig;
import com.bergerkiller.bukkit.tc.properties.collision.CollisionMobCategory;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.FieldBackedStandardTrainPropertiesHolder;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlockChanger;
import com.bergerkiller.bukkit.tc.storage.OfflineGroup;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.SignSkipOptions;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;
import com.bergerkiller.bukkit.tc.utils.SoftReference;

public class TrainProperties extends TrainPropertiesStore implements IProperties {
    private static final long serialVersionUID = 1L;

    private final SoftReference<MinecartGroup> group = new SoftReference<>();
    private final FieldBackedStandardTrainPropertiesHolder standardProperties = new FieldBackedStandardTrainPropertiesHolder();
    private final ConfigurationNode config;
    public boolean requirePoweredMinecart = false;
    protected String trainname;
    private final EnumSet<SlowdownMode> slowDownOptions = EnumSet.allOf(SlowdownMode.class);
    private double collisionDamage = 1.0D;
    private boolean keepChunksLoaded = false;
    private boolean allowPlayerManualMovement = false;
    private boolean allowMobManualMovement = false;
    private boolean allowPlayerTake = false;
    private boolean soundEnabled = true;
    private List<String> tickets = new ArrayList<>();
    private SignSkipOptions skipOptions = new SignSkipOptions();
    private String blockTypes = "";
    private int blockOffset = SignActionBlockChanger.BLOCK_OFFSET_NONE;
    private double waitDistance = 0.0;
    private double waitDelay = 0.0;
    private double waitDeceleration = 0.0;
    private double waitAcceleration = 0.0;
    private double bankingStrength = 0.0;
    private double bankingSmoothness = 10.0;
    private boolean suffocation = true;
    private double gravity = 1.0;
    private String killMessage = "";

    protected TrainProperties(String trainname, ConfigurationNode config) {
        this.trainname = trainname;
        this.config = config;

        // Pre-initialize the cart configuration, if such is available
        if (config.isNode("carts")) {
            for (ConfigurationNode cartConfig : config.getNode("carts").getNodes()) {
                // Decode node key as UUID
                UUID uuid;
                try {
                    uuid = UUID.fromString(cartConfig.getName());
                } catch (IllegalArgumentException ex) {
                    TrainCarts.plugin.getLogger().log(Level.WARNING, "Invalid UUID for cart: " + cartConfig.getName());
                    continue;
                }

                // Initialize cart properties and assign it to these train properties
                // Note: use super.add() to avoid call to .getConfig(), which would wipe everything
                CartProperties cProp = CartPropertiesStore.createNew(this, cartConfig, uuid);
                cProp.group = this;
                super.add(CartPropertiesStore.createNew(this, cartConfig, uuid));
            }
        }
    }

    @Override
    public String getTypeName() {
        return "train";
    }

    @Override
    public final ConfigurationNode getConfig() {
        return this.config;
    }

    @Override
    public final <T> T get(IProperty<T> property) {
        return property.get(this);
    }

    @Override
    public final <T> void set(IProperty<T> property, T value) {
        property.set(this, value);
    }

    /**
     * Internal use only
     */
    public FieldBackedStandardTrainPropertiesHolder getStandardPropertiesHolder() {
        return standardProperties;
    }

    @Override
    public MinecartGroup getHolder() {
        MinecartGroup group = this.group.get();
        if (group == null || group.isRemoved()) {
            return this.group.set(MinecartGroupStore.get(this));
        } else {
            return group;
        }
    }

    @Override
    public boolean hasHolder() {
        return this.getHolder() != null;
    }

    @Override
    public boolean restore() {
        if (this.isLoaded()) {
            return true;
        }
        // Load all the chunks of this group to trigger a restore
        OfflineGroup group = OfflineGroupManager.findGroup(this.trainname);
        if (group == null) {
            TrainPropertiesStore.remove(this.getTrainName());
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
     * Gets the wait distance. The train will automatically wait to maintain this distance between itself and the train up
     * ahead.
     * 
     * @return waitDistance
     */
    public double getWaitDistance() {
        return this.waitDistance;
    }

    /**
     * Sets the wait distance. The train will automatically wait to maintain this distance between itself and the train up
     * ahead.
     * 
     * @param waitDistance
     */
    public void setWaitDistance(double waitDistance) {
        this.waitDistance = waitDistance;
    }

    /**
     * Gets the number of seconds that must elapse after waiting for another train before the train starts moving again.
     * This is used when a wait distance is set.
     * 
     * @return wait delay in seconds, used after waiting for a train
     */
    public double getWaitDelay() {
        return this.waitDelay;
    }

    /**
     * Sets the number of seconds that must elapse after waiting for another train before the train starts moving again.
     * This is used when a wait distance is set.
     * 
     * @param delay Delay to set to in seconds
     */
    public void setWaitDelay(double delay) {
        this.waitDelay = delay;
    }

    /**
     * Gets the acceleration in blocks/tick^2 of the train when speeding up again after waiting for a train.
     * The speed of the train goes up by this amount every tick. If 0, the acceleration is instant.
     * 
     * @return acceleration of the train when speeding up after waiting
     */
    public double getWaitAcceleration() {
        return this.waitAcceleration;
    }

    /**
     * Sets the acceleration in blocks/tick^2 of the train when speeding up and slowing down
     * while trying to maintain distance. The acceleration of speeding up and slowing down
     * is set to the same value
     * 
     * @param acceleration Acceleration at which to speed up/slow down
     */
    public void setWaitAcceleration(double acceleration) {
        setWaitAcceleration(acceleration, acceleration);
    }

    /**
     * Sets the acceleration in blocks/tick^2 of the train when speeding up and slowing down
     * while trying to maintain distance. The acceleration (speed up) and deceleration (slowing down)
     * can be separately specified.
     * 
     * @param acceleration Acceleration at which to speed up
     * @param deceleration Acceleration at which to slow down
     * @see {@link #getWaitAcceleration()}
     */
    public void setWaitAcceleration(double acceleration, double deceleration) {
        this.waitAcceleration = acceleration;
        this.waitDeceleration = deceleration;
    }

    /**
     * Gets the deceleration inblocks/tick^2 of the train when slowing down, when the train has to wait
     * for another train. Speed of the train goes down by this amount every tick. If 0, the deceleration
     * is instant.
     * 
     * @return deceleration of the train when slowing down to wait for another train
     */
    public double getWaitDeceleration() {
        return this.waitDeceleration;
    }

    /**
     * Gets the maximum speed this Train can move at. Do not use this property inside physics functions! In there
     * getEntity().getMaxSpeed() should be used instead.
     *
     * @return max speed in blocks/tick
     */
    public double getSpeedLimit() {
        return StandardProperties.speedLimit.getHolderDoubleValue(this.standardProperties);
    }

    /**
     * Sets the maximum speed this Train can move at<br>
     * The maximum speed limit is enforced.
     *
     * @param limit in blocks/tick
     */
    public void setSpeedLimit(double limit) {
        StandardProperties.speedLimit.set(this, limit);
    }

    /**
     * Gets the gravity factor applied to the train, where 1.0 is the default
     * 
     * @return gravity factor
     */
    public double getGravity() {
        return this.gravity;
    }

    /**
     * Sets the gravity factor applied to the train, where 1.0 is the default
     * 
     * @param gravity
     */
    public void setGravity(double gravity) {
        this.gravity = gravity;
    }

    /**
     * Gets whether the Train slows down over time.<br>
     * <b>Deprecated: This returns True if any slowdown mode is true.</b>
     *
     * @return True if it slows down, False if not
     */
    @Deprecated
    public boolean isSlowingDown() {
        return !this.slowDownOptions.isEmpty();
    }

    /**
     * Gets whether the slow down options are set to a default, where all slowdown modes are active.
     * 
     * @return True if all modes are active (legacy slowdown = true set)
     */
    public boolean isSlowingDownAll() {
        return this.slowDownOptions.size() == SlowdownMode.values().length;
    }

    /**
     * Gets whether all slow down options are disabled.
     * 
     * @return True if all slowdown is disabled (legacy slowdown = false set)
     */
    public boolean isSlowingDownNone() {
        return this.slowDownOptions.isEmpty();
    }

    /**
     * Sets whether the Train slows down over time.<br>
     * <b>Note: sets or clears all possible slowdown options at once</b>
     *
     * @param slowingDown state to set to
     */
    public void setSlowingDown(boolean slowingDown) {
        if (slowingDown) {
            for (SlowdownMode mode : SlowdownMode.values()) {
                this.slowDownOptions.add(mode);
            }
        } else {
            this.slowDownOptions.clear();
        }
    }

    /**
     * Gets whether a particular slow down mode is activated
     * 
     * @param mode to check
     * @return True if the slowdown mode is activated
     */
    public boolean isSlowingDown(SlowdownMode mode) {
        return this.slowDownOptions.contains(mode);
    }

    /**
     * Sets whether a particular slow down mode is activated
     * 
     * @param mode        to set
     * @param slowingDown option to set that mode to
     */
    public void setSlowingDown(SlowdownMode mode, boolean slowingDown) {
        LogicUtil.addOrRemove(this.slowDownOptions, mode, slowingDown);
    }

    /**
     * Gets the Display Name of these properties. If none is configured,
     * returns the train name instead. To check whether one is configured,
     * use {@link #getDisplayNameOrEmpty()}.
     *
     * @return display name
     */
    public String getDisplayName() {
        String name = get(StandardProperties.displayName);
        return name.isEmpty() ? this.getTrainName() : name;
    }

    /**
     * Gets the currently configured display name. If none is configured,
     * returns an Empty String.
     * 
     * @return display name, or empty if none is set
     */
    public String getDisplayNameOrEmpty() {
        return get(StandardProperties.displayName);
    }

    /**
     * Sets the Display Name for these properties<br>
     * If a null or empty String is passed in as argument, the display name is set to the train name. (it is reset)
     *
     * @param displayName to set to
     */
    public void setDisplayName(String displayName) {
        set(StandardProperties.displayName, displayName);
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
            this.restore();
        }
        if (state != this.keepChunksLoaded) {
            this.keepChunksLoaded = state;
            MinecartGroup group = this.getHolder();
            if (group != null) {
                group.keepChunksLoaded(state);
            }
        }
    }

    /**
     * Gets whether ambient Minecart sounds are enabled
     *
     * @return True if enabled, False if not
     */
    public boolean isSoundEnabled() {
        return this.soundEnabled;
    }

    /**
     * Sets whether ambient Minecart sounds are enabled
     *
     * @param enabled state to set to
     */
    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    @Override
    public boolean add(CartProperties properties) {
        // Before assigning, un-assign from the previous train properties
        // This will remove it from itself, and the configuration
        if (properties.group != null && properties.group != this) {
            properties.group.remove(properties);
        }

        // Assign new group and try to add
        properties.group = this;
        if (!super.add(properties)) {
            return false;
        }

        // Bind cart properties configuration to this train's configuration
        this.config.getNode("carts").set(properties.getUUID().toString(), properties.getConfig());
        return true;
    }

    @Override
    public boolean remove(Object o) {
        // MinecartMember -> CartProperties (TODO: Get rid of this? Gross!)
        if (o instanceof MinecartMember<?>) {
            o = ((MinecartMember<?>) o).getProperties();
        }

        // Not part of these train properties
        if (!super.remove(o)) {
            return false;
        }

        // Remove from 'carts' configuration
        if (o instanceof CartProperties && this.config.isNode("carts")) {
            this.config.getNode("carts").remove(((CartProperties) o).getUUID().toString());
        }
        return true;
    }

    public CartProperties get(int index) {
        int i = 0;
        for (CartProperties prop : this) {
            if (i++ == index) {
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
        Set<String> rval = new HashSet<>();
        for (CartProperties cprop : this) {
            rval.addAll(cprop.getOwnerPermissions());
        }
        return rval;
    }

    @Override
    public Set<String> getOwners() {
        Set<String> rval = new HashSet<>();
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
     * Gets whether this Train supports players taking minecarts with them when they leave. When the Minecart is part of a
     * Train, it is always disallowed.
     *
     * @return True if players can take Minecarts with them, False if not.
     */
    public boolean isPlayerTakeable() {
        return this.allowPlayerTake;
    }

    /**
     * Sets whether this Train supports players taking minecarts with them when they leave. When the Minecart is part of a
     * Train, it is always disallowed.
     *
     * @param takeable state to set to
     */
    public void setPlayerTakeable(boolean takeable) {
        this.allowPlayerTake = takeable;
    }

    public double getBankingStrength() {
        return this.bankingStrength;
    }

    public double getBankingSmoothness() {
        return this.bankingSmoothness;
    }

    public void setBankingStrength(double strength) {
        this.bankingStrength = strength;
    }

    public void setBankingSmoothness(double smoothness) {
        this.bankingSmoothness = smoothness;
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
    public void setPublic(boolean state) {
        for (CartProperties prop : this) {
            prop.setPublic(state);
        }
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
        Set<String> tags = new HashSet<>();
        for (CartProperties prop : this) {
            tags.addAll(prop.getTags());
        }
        return tags;
    }

    @Override
    public void setTags(String... tags) {
        for (CartProperties prop : this) {
            prop.setTags(tags);
        }
    }

    @Override
    public void clearTags() {
        for (CartProperties prop : this) {
            prop.clearTags();
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
    public boolean getPlayersEnter() {
        for (CartProperties prop : this) {
            if (prop.getPlayersEnter()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setPlayersEnter(boolean state) {
        for (CartProperties prop : this) {
            prop.setPlayersEnter(state);
        }
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
    public void setPlayersExit(boolean state) {
        for (CartProperties prop : this) {
            prop.setPlayersExit(state);
        }
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
    public void setInvincible(boolean enabled) {
        for (CartProperties prop : this) {
            prop.setInvincible(enabled);
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
    public void setSpawnItemDrops(boolean spawnDrops) {
        for (CartProperties prop : this) {
            prop.setSpawnItemDrops(spawnDrops);
        }
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
    public List<String> getDestinationRoute() {
        for (CartProperties prop : this) {
            List<String> route = prop.getDestinationRoute();
            if (!route.isEmpty()) {
                return route;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void setDestinationRoute(List<String> route) {
        for (CartProperties prop : this) {
            prop.setDestinationRoute(route);
        }
    }

    @Override
    public void clearDestinationRoute() {
        for (CartProperties prop : this) {
            prop.clearDestinationRoute();
        }
    }

    @Override
    public void addDestinationToRoute(String destination) {
        for (CartProperties prop : this) {
            prop.addDestinationToRoute(destination);
        }
    }

    @Override
    public void removeDestinationFromRoute(String destination) {
        for (CartProperties prop : this) {
            prop.removeDestinationFromRoute(destination);
        }
    }

    @Override
    public int getCurrentRouteDestinationIndex() {
        for (CartProperties prop : this) {
            if (!prop.getDestinationRoute().isEmpty()) {
                return prop.getCurrentRouteDestinationIndex();
            }
        }
        return -1;
    }

    @Override
    public String getNextDestinationOnRoute() {
        for (CartProperties prop : this) {
            if (!prop.getDestinationRoute().isEmpty()) {
                return prop.getNextDestinationOnRoute();
            }
        }
        return "";
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

    public CollisionMode getCollisionMode(Entity entity) {
        if (entity.isDead()) {
            return CollisionMode.CANCEL;
        }
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(entity);
        CollisionConfig collision = this.getCollision();
        if (member != null) {
            if (collision.trainMode() == CollisionMode.LINK) {
                if (member.getGroup().getProperties().getCollision().trainMode() == CollisionMode.LINK) {
                    return CollisionMode.LINK;
                } else {
                    return CollisionMode.CANCEL;
                }
            } else {
                return collision.trainMode();
            }
        } else if (entity instanceof Player) {
            GameMode playerGameMode = ((Player) entity).getGameMode();
            if (playerGameMode == GameMode.SPECTATOR) {
                return CollisionMode.CANCEL;
            }
            if (TCConfig.collisionIgnoreOwners && collision.playerMode() != CollisionMode.DEFAULT) {
                if (TCConfig.collisionIgnoreGlobalOwners) {
                    if (CartProperties.hasGlobalOwnership((Player) entity)) {
                        return CollisionMode.DEFAULT;
                    }
                }
                if (this.hasOwnership((Player) entity)) {
                    return CollisionMode.DEFAULT;
                }
            }
            // Don't kill or damage players in creative
            if (playerGameMode == GameMode.CREATIVE) {
                if (collision.playerMode() == CollisionMode.KILL ||
                    collision.playerMode() == CollisionMode.KILLNODROPS ||
                    collision.playerMode() == CollisionMode.DAMAGE ||
                    collision.playerMode() == CollisionMode.DAMAGENODROPS)
                {
                    return CollisionMode.PUSH;
                }
            }
            return collision.playerMode();
        } else {
            return collision.forEntity(entity);
        }
    }

    public String getTrainName() {
        return this.trainname;
    }

    /**
     * Renames this train, this should be called to rename the train safely.
     *
     * @param newtrainname to set to
     */
    public void setTrainName(String newTrainName) {
        rename(this, newTrainName);
    }

    /**
     * Renames this train, this should be called to rename the train safely<br>
     * <br>
     * <b>Deprecated: use {@link #setTrainName(String)} instead</b>
     *
     * @param newtrainname to set to
     * @return this
     */
    @Deprecated
    public TrainProperties setName(String newtrainname) {
        setTrainName(newtrainname);
        return this;
    }

    /**
     * Gets whether passengers inside this train sustain suffocation damage when their head is submerged inside a block.
     * 
     * @return True if suffocation damage is enabled
     */
    public boolean hasSuffocation() {
        return this.suffocation;
    }

    /**
     * Sets whether passengers inside this train sustain suffocation damage when their head is submerged inside a block.
     * 
     * @param suffocation option
     */
    public void setSuffocation(boolean suffocation) {
        this.suffocation = suffocation;
    }

    /**
     * Gets whether train player passengers can manually move the train they are in
     *
     * @return True if manual movement is allowed, False if not
     */
    public boolean isManualMovementAllowed() {
        return this.allowPlayerManualMovement;
    }

    /**
     * Sets whether train player passengers can manually move the train they are in
     *
     * @param allow state to set to
     */
    public void setManualMovementAllowed(boolean allow) {
        this.allowPlayerManualMovement = allow;
    }

    /**
     * Gets whether train non-player passengers can manually move the train they are in
     *
     * @return True if manual movement is allowed, False if not
     */
    public boolean isMobManualMovementAllowed() {
        return this.allowMobManualMovement;
    }

    /**
     * Sets whether train non-player passengers can manually move the train they are in
     *
     * @param allow state to set to
     */
    public void setMobManualMovementAllowed(boolean allow) {
        this.allowMobManualMovement = allow;
    }

    /**
     * Gets a list of tickets that can be used for entering this train
     * 
     * @return tickets
     */
    public List<String> getTickets() {
        return Collections.unmodifiableList(this.tickets);
    }

    /**
     * Adds a new ticket that can be used for entering this train.
     * 
     * @param ticketName to add
     */
    public void addTicket(String ticketName) {
        this.tickets.add(ticketName);
    }

    /**
     * Revokes a ticket from being used for entering this train.
     * 
     * @param ticketName to remove
     */
    public void removeTicket(String ticketName) {
        this.tickets.remove(ticketName);
    }

    public void clearTickets() {
        this.tickets.clear();
    }

    public SignSkipOptions getSkipOptions() {
        return this.skipOptions;
    }

    public void setSkipOptions(SignSkipOptions options) {
        this.skipOptions.load(options, false);
    }

    public String getKillMessage() {
        return this.killMessage;
    }

    public void setKillMessage(String killMessage) {
        this.killMessage = killMessage;
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

    @Override
    public BlockLocation getLocation() {
        for (CartProperties prop : this) {
            return prop.getLocation();
        }
        return null;
    }

    public void setDefault() {
        this.setDefault("default");
    }

    public void setDefault(String key) {
        this.apply(getDefaultsByName(key));
    }

    /**
     * <b>Deprecated: use {@link #apply(ConfigurationNode)} instead</b>
     */
    @Deprecated
    public void setDefault(ConfigurationNode node) {
        this.apply(node);
    }

    public void setDefault(Player player) {
        if (player == null) {
            // Set default
            this.setDefault();
        } else {
            // Load it
            this.apply(getDefaultsByPlayer(player));
        }
    }

    public void tryUpdate() {
        MinecartGroup g = this.getHolder();
        if (g != null) g.onPropertiesChanged();
    }

    @Override
    public boolean parseSet(String key, String arg) {
        TrainPropertiesStore.markForAutosave();
        if (key.equalsIgnoreCase("exitoffset")) {
            Vector vec = Util.parseVector(arg, null);
            if (vec != null) {
                if (vec.length() > TCConfig.maxEjectDistance) {
                    vec.normalize().multiply(TCConfig.maxEjectDistance);
                }
                for (CartProperties prop : this) {
                    prop.exitOffset = vec;
                }
            }
        } else if (key.equalsIgnoreCase("exityaw")) {
            float yaw = ParseUtil.parseFloat(arg, 0.0f);
            for (CartProperties prop : this) {
                prop.exitYaw = yaw;
            }
        } else if (key.equalsIgnoreCase("exitpitch")) {
            float pitch = ParseUtil.parseFloat(arg, 0.0f);
            for (CartProperties prop : this) {
                prop.exitPitch = pitch;
            }
        } else if (LogicUtil.containsIgnoreCase(key, "exitrot", "exitrotation")) {
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
        } else if (key.equalsIgnoreCase("killmessage")) {
            this.killMessage = arg;
        } else if (key.equalsIgnoreCase("sound") || key.equalsIgnoreCase("minecartsound")) {
            this.soundEnabled = ParseUtil.parseBool(arg);
        } else if (key.equalsIgnoreCase("playercollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null) return false;
            setCollision(getCollision().setPlayerMode(mode));
        } else if (key.equalsIgnoreCase("misccollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null) return false;
            setCollision(getCollision().setMiscMode(mode));
        } else if (key.equalsIgnoreCase("traincollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null) return false;
            setCollision(getCollision().setTrainMode(mode));
        } else if (key.equalsIgnoreCase("blockcollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null) return false;
            setCollision(getCollision().setBlockMode(mode));
        } else if (key.equalsIgnoreCase("collisiondamage")) {
            this.setCollisionDamage(Double.parseDouble(arg));
        } else if (key.equalsIgnoreCase("suffocation")) {
            this.suffocation = ParseUtil.parseBool(arg);
        } else if (this.setCollisionMode(key, arg)) {
            return true;
        } else if (LogicUtil.containsIgnoreCase(key, "collision", "collide")) {
            if (ParseUtil.parseBool(arg)) {
                // Legacy support: just reset to defaults
                // Preserve mob collision rules
                setCollision(CollisionConfig.DEFAULT);
            } else {
                // Disable all collision
                setCollision(CollisionConfig.CANCEL);
            }
        } else if (LogicUtil.containsIgnoreCase(key, "linking", "link")) {
            this.setLinking(ParseUtil.parseBool(arg));
        } else if (key.toLowerCase(Locale.ENGLISH).startsWith("slow")) {
            SlowdownMode slowMode = null;
            for (SlowdownMode mode : SlowdownMode.values()) {
                if (key.contains(mode.getKey())) {
                    slowMode = mode;
                    break;
                }
            }
            if (slowMode != null) {
                this.setSlowingDown(slowMode, ParseUtil.parseBool(arg));
            } else {
                this.setSlowingDown(ParseUtil.parseBool(arg));
            }
        } else if (LogicUtil.containsIgnoreCase(key, "setdefault", "default")) {
            this.setDefault(arg);
        } else if (key.equalsIgnoreCase("pushplayers")) {
            CollisionMode mode = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
            setCollision(getCollision().setPlayerMode(mode));
        } else if (key.equalsIgnoreCase("pushmisc")) {
            CollisionMode mode = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
            setCollision(getCollision().setMiscMode(mode));
        } else if (LogicUtil.containsIgnoreCase(key, "push", "pushing")) {
            CollisionMode mode = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
            setCollision(getCollision().setPlayerMode(mode).setMiscMode(mode));
            this.setCollisionModeForMobs(mode);
        } else if (LogicUtil.containsIgnoreCase(key, "speedlimit", "maxspeed")) {
            this.setSpeedLimit(Util.parseVelocity(arg, this.getSpeedLimit()));
        } else if (LogicUtil.containsIgnoreCase(key, "gravity")) {
            this.setGravity(ParseUtil.parseDouble(arg, 1.0));
        } else if (LogicUtil.containsIgnoreCase(key, "allowmanual", "manualmove", "manual")) {
            this.setManualMovementAllowed(ParseUtil.parseBool(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "allowmobmanual", "mobmanualmove", "mobmanual")) {
            this.setMobManualMovementAllowed(ParseUtil.parseBool(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "keepcloaded", "loadchunks", "keeploaded")) {
            this.setKeepChunksLoaded(ParseUtil.parseBool(arg));
        } else if (key.equalsIgnoreCase("addtag")) {
            this.addTags(arg);
        } else if (key.equalsIgnoreCase("settag")) {
            this.setTags(arg);
        } else if (key.equalsIgnoreCase("remtag") || key.equalsIgnoreCase("removetag")) {
            this.removeTags(arg);
        } else if (key.equalsIgnoreCase("destination")) {
            this.setDestination(arg);
        } else if (key.equalsIgnoreCase("addroute")) {
            this.addDestinationToRoute(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "remroute", "removeroute")) {
            this.removeDestinationFromRoute(arg);
        } else if (key.equalsIgnoreCase("clearroute")) {
            this.clearDestinationRoute();
        } else if (key.equalsIgnoreCase("setroute")) {
            this.clearDestinationRoute();
            this.addDestinationToRoute(arg);
        } else if (key.equalsIgnoreCase("loadroute")) {
            this.setDestinationRoute(TrainCarts.plugin.getRouteManager().findRoute(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "name", "rename", "setname")) {
            this.setTrainName(generateTrainName(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "dname", "displayname", "setdisplayname", "setdname")) {
            this.setDisplayName(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "mobenter", "mobsenter")) {
            if (ParseUtil.parseBool(arg)) {
                this.setCollisionModeForMobs(CollisionMode.ENTER);
            } else {
                this.setCollisionModeIfModeForMobs(CollisionMode.ENTER, CollisionMode.DEFAULT);
            }
        } else if (key.equalsIgnoreCase("waitdistance")) {
            this.setWaitDistance(ParseUtil.parseDouble(arg, this.waitDistance));
        } else if (key.equalsIgnoreCase("waitdelay")) {
            this.setWaitDelay(ParseUtil.parseDouble(arg, this.waitDelay));
        } else if (LogicUtil.containsIgnoreCase(key, "waitacceleration", "waitaccel", "waitacc")) {
            String[] args = arg.trim().split(" ");
            if (args.length >= 2) {
                this.setWaitAcceleration(Util.parseAcceleration(args[0], this.waitAcceleration),
                                         Util.parseAcceleration(args[1], this.waitDeceleration));
            } else {
                double accel = Util.parseAcceleration(arg, Double.NaN);
                if (!Double.isNaN(accel)) {
                    this.setWaitAcceleration(accel);
                }
            }
        } else if (key.equalsIgnoreCase("playerenter")) {
            this.setPlayersEnter(ParseUtil.parseBool(arg));
        } else if (key.equalsIgnoreCase("playerexit")) {
            this.setPlayersExit(ParseUtil.parseBool(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "invincible", "godmode")) {
            this.setInvincible(ParseUtil.parseBool(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "banking")) {
            String[] args = arg.split(" ");
            this.setBankingStrength(ParseUtil.parseDouble(args[0], this.bankingStrength));
            if (args.length >= 2) {
                this.setBankingSmoothness(ParseUtil.parseDouble(args[1], this.bankingSmoothness));
            }
        } else if (key.equalsIgnoreCase("setownerperm")) {
            for (CartProperties prop : this) {
                prop.clearOwnerPermissions();
                prop.getOwnerPermissions().add(arg);
            }
        } else if (key.equalsIgnoreCase("addownerperm")) {
            for (CartProperties prop : this) {
                prop.getOwnerPermissions().add(arg);
            }
        } else if (key.equalsIgnoreCase("remownerperm")) {
            for (CartProperties prop : this) {
                prop.getOwnerPermissions().remove(arg);
            }
        } else if (key.equalsIgnoreCase("setowner")) {
            arg = arg.toLowerCase();
            for (CartProperties cprop : this) {
                cprop.clearOwners();
                cprop.getOwners().add(arg);
            }
        } else if (key.equalsIgnoreCase("addowner")) {
            arg = arg.toLowerCase();
            for (CartProperties cprop : this) {
                cprop.getOwners().add(arg);
            }
        } else if (key.equalsIgnoreCase("remowner")) {
            arg = arg.toLowerCase();
            for (CartProperties cprop : this) {
                cprop.getOwners().remove(arg);
            }
        } else if (LogicUtil.containsIgnoreCase(key, "spawnitemdrops", "spawndrops", "killdrops")) {
            this.setSpawnItemDrops(ParseUtil.parseBool(arg));
        } else if (key.equalsIgnoreCase("addticket")) {
            this.addTicket(arg);
        } else if (key.equalsIgnoreCase("remticket")) {
            this.removeTicket(arg);
        } else if (key.equalsIgnoreCase("setticket")) {
            this.clearTickets();
            if (arg.length() > 0) {
                this.addTicket(arg);
            }
        } else if (key.equalsIgnoreCase("clrticket")) {
            this.clearTickets();
        } else if (LogicUtil.containsIgnoreCase(key, "drivesound", "driveeffect")) {
            for (CartProperties cprop : this) {
                cprop.setDriveSound(arg);
            }
        } else if (LogicUtil.containsIgnoreCase(key, "entermessage", "entermsg")) {
            this.setEnterMessage(arg);
        } else {
            return false;
        }
        this.tryUpdate();
        return true;
    }

    /**
     * Gets the collision configuration of the train. This stores all the collision rules
     * to follow when colliding with entities and blocks.
     * 
     * @return collision configuration
     */
    public CollisionConfig getCollision() {
        return get(StandardProperties.collision);
    }

    /**
     * Sets the collision configuration of the train. This stores all the collision rules
     * to follow when colliding with entities and blocks. Use the static methods
     * of {@link CollisionConfig} to change individual modes.
     * 
     * @param collisionConfig New collision configuration to set to
     */
    public void setCollision(CollisionConfig collisionConfig) {
        set(StandardProperties.collision, collisionConfig);
    }

    /**
     * Sets the collision mode configured for a collision mob category
     * 
     * @param mobCategory Category of mob
     * @param mode to set to, null to reset to defaults
     */
    public void setCollisionMode(CollisionMobCategory mobCategory, CollisionMode mode) {
        setCollision(getCollision().setMobMode(mobCategory, mode));
    }

    /**
     * Sets collision mode, used by ParseSet. Supports the formats: - mobcollision / playercollision /creepercollision /
     * etc. - pushplayers / pushmobs / etc.
     */
    public boolean setCollisionMode(String key, String value) {
        key = key.toLowerCase(Locale.ENGLISH);
        value = value.toLowerCase(Locale.ENGLISH);
        if (key.startsWith("push") && key.length() > 4) {
            String mobType = key.substring(4);
            CollisionMode mode;
            if (ParseUtil.isBool(value)) {
                mode = CollisionMode.fromPushing(ParseUtil.parseBool(value));
            } else {
                mode = CollisionMode.parse(value);
            }
            return this.updateCollisionProperties(mobType, mode);
        }
        if (key.endsWith("collision") && key.length() > 9) {
            String mobType = key.substring(0, key.length() - 9);
            CollisionMode mode = CollisionMode.parse(value);
            return this.updateCollisionProperties(mobType, mode);
        }
        return false;
    }

    public boolean updateCollisionProperties(String mobType, CollisionMode mode) {
        if (mode == null) {
            return false;
        }
        if (mobType.equals("mob") || mobType.equals("mobs")) {
            this.setCollisionModeForMobs(mode);
            return true;
        } else {
            for (CollisionMobCategory mobCategory : CollisionMobCategory.values()) {
                if (mobType.equals(mobCategory.getMobType()) || mobType.equals(mobCategory.getPluralMobType())) {
                    this.setCollisionMode(mobCategory, mode);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Updates the collision mode for all mob collision categories
     * 
     * @param mode
     */
    public void setCollisionModeForMobs(CollisionMode mode) {
        for (CollisionMobCategory collision : CollisionMobCategory.values()) {
            if (collision.isMobCategory()) {
                setCollisionMode(collision, mode);
            }
        }
    }

    /**
     * Updates the collision mode for all mob collision categories if the current configured value
     * matches the expected mode
     * 
     * @param expected
     * @param mode
     */
    public void setCollisionModeIfModeForMobs(CollisionMode expected, CollisionMode mode) {
        for (CollisionMobCategory collision : CollisionMobCategory.values()) {
            if (collision.isMobCategory() && getCollision().mobMode(collision) == expected) {
                setCollisionMode(collision, mode);
            }
        }
    }

    /**
     * Sets train collision mode to link when true. When false is specified and the collision mode is linking, it is set to
     * default. This is legacy behavior.
     * 
     * @param linking
     */
    public void setLinking(boolean linking) {
        if (linking) {
            setCollision(getCollision().setTrainMode(CollisionMode.LINK));
        } else if (this.getCollision().trainMode() == CollisionMode.LINK) {
            setCollision(getCollision().setTrainMode(CollisionMode.DEFAULT));
        }
    }

    /**
     * Loads the properties from the TrainProperties source specified<br>
     * Cart properties are not transferred or updated!
     * This is used when splitting the properties of one train into two.
     *
     * @param source to load from
     * @see #load(ConfigurationNode)
     */
    public void load(TrainProperties source) {
        this.load(source.saveToConfig());
    }

    /**
     * Loads the train properties from YAML configuration.
     * All train-level properties are loaded. Cart properties of carts that
     * belong to this train are not updated.<br>
     * <br>
     * The input configuration should <b>only</b> contain train configurations.
     * If properties for carts should be loaded also, use {@link #apply(ConfigurationNode)}
     * instead.<br>
     * <br>
     * All original values and child nodes of the input node is deep-cloned, so further
     * changes to the node do not affect these properties and vice-versa.
     * 
     * @param node The YAML configuration node to load from
     */
    @Override
    public void load(ConfigurationNode node) {
        // Wipe all original properties except 'carts', effectively resetting to the defaults
        for (String key : new ArrayList<String>(this.config.getKeys())) {
            if (!"carts".equals(key)) {
                this.config.remove(key);
            }
        }

        // Deep-copy input train configuration to train configuration, skip 'carts'
        Util.cloneInto(node, this.config, Collections.singleton("carts"));

        // Reload properties
        onConfigurationChanged();
    }

    @Override
    public void save(ConfigurationNode node) {
        Util.cloneInto(saveToConfig(), node, Collections.emptySet());
    }

    // Temporary while loading is done here
    private <T> T getConfigValue(String key, T defaultValue) {
        return config.contains(key) ? config.get(key, defaultValue) : defaultValue;
    }

    protected void onConfigurationChanged() {
        // Refresh registered IProperties
        // All below should eventually become IProperties, which is when this function
        // can be removed!
        for (IProperty<?> property : IPropertyRegistry.instance().all()) {
            property.onConfigurationChanged(this);
        }

        // TODO: Replace all below with IProperty objects
        // Note: completely disregards all previous configuration!
        this.allowPlayerTake = getConfigValue("allowPlayerTake", false);
        this.collisionDamage = getConfigValue("collisionDamage", 1.0);
        this.soundEnabled = getConfigValue("soundEnabled", true);
        this.gravity = getConfigValue("gravity", 1.0);
        this.requirePoweredMinecart = getConfigValue("requirePoweredMinecart", false);
        this.setKeepChunksLoaded(getConfigValue("keepChunksLoaded", false));
        this.allowPlayerManualMovement = getConfigValue("allowManualMovement", false);
        this.allowMobManualMovement = getConfigValue("allowMobManualMovement", false);
        this.suffocation = getConfigValue("suffocation", true);
        this.killMessage = getConfigValue("killMessage", "");

        // Wait distance legacy, and the new wait properties
        if (config.contains("wait.distance")) {
            this.waitDistance = config.get("wait.distance", 0.0);
        } else if (config.contains("waitDistance")) {
            this.waitDistance = config.get("waitDistance", 0.0);
        } else {
            this.waitDistance = 0.0;
        }
        this.waitDelay = getConfigValue("wait.delay", 0.0);
        this.waitAcceleration = getConfigValue("wait.acceleration", 0.0);
        this.waitDeceleration = getConfigValue("wait.deceleration", 0.0);

        // Slowdown options for friction and gravity (and others?)
        if (config.isNode("slowDown")) {
            ConfigurationNode slowDownNode = config.getNode("slowDown");

            for (SlowdownMode mode : SlowdownMode.values()) {
                if (slowDownNode.contains(mode.getKey())) {
                    this.setSlowingDown(mode, slowDownNode.get(mode.getKey(), true));
                } else {
                    this.setSlowingDown(mode, true);
                }
            }
        } else if (config.contains("slowDown")) {
            this.setSlowingDown(getConfigValue("slowDown", true));
        } else {
            this.setSlowingDown(true);
        }

        // Banking
        this.bankingStrength = getConfigValue("banking.strength", 0.0);
        this.bankingSmoothness = getConfigValue("banking.smoothness", 10.0);

        // Tickets that can be used for this train
        this.tickets.clear();
        if (config.contains("tickets")) {
            this.tickets.addAll(config.getList("tickets", String.class));
        }

        // Load train skip options, if it exists
        this.skipOptions = new SignSkipOptions();
        if (config.isNode("skipOptions")) {
            this.skipOptions.load(config.getNode("skipOptions"));
        }

        // These properties are purely saved so they are written correctly when saving defaults
        // There are not meant to be read anywhere, because these exist as part of minecart metadata
        // Only read these when actually set, don't add them using get's default if not so
        // We don't want
        this.blockTypes = "";
        this.blockOffset = SignActionBlockChanger.BLOCK_OFFSET_NONE;
        if (config.contains("blockTypes") || config.contains("blockOffset")) {
            this.blockTypes = getConfigValue("blockTypes", "");
            this.blockOffset = getConfigValue("blockOffset", SignActionBlockChanger.BLOCK_OFFSET_NONE);

            // Apply block types / block height to the actual minecart, if set
            if (!this.blockTypes.isEmpty() || this.blockOffset != SignActionBlockChanger.BLOCK_OFFSET_NONE) {
                MinecartGroup group = this.getHolder();
                if (group != null) {
                    if (this.blockTypes.isEmpty()) {
                        SignActionBlockChanger.setBlocks(group, new ItemParser[0], this.blockOffset);
                    } else {
                        SignActionBlockChanger.setBlocks(group, this.blockTypes, this.blockOffset);
                    }
                }
            }
        }
    }

    /**
     * Forces all properties to be saved to the {@link #getConfig()}.
     * Note: this will be removed once all properties are
     * part of IProperties! Then they are all live-updated and this
     * method is no longer needed.
     * 
     * @return saved {@link #getConfig()}
     */
    public ConfigurationNode saveToConfig() {
        config.set("soundEnabled", this.soundEnabled ? null : false);
        config.set("allowPlayerTake", this.allowPlayerTake ? true : null);
        config.set("requirePoweredMinecart", this.requirePoweredMinecart ? true : null);
        config.set("collisionDamage", (this.collisionDamage == 1.0) ? null : this.collisionDamage == 1.0);
        config.set("keepChunksLoaded", this.keepChunksLoaded ? true : null);
        config.set("gravity", this.gravity != 1.0 ? this.gravity : null);
        config.set("suffocation", this.suffocation ? null : false);
        config.set("killMessage", this.killMessage.isEmpty() ? null : this.killMessage);

        config.remove("waitDistance"); // cleanup legacy
        if (this.waitDistance > 0 || this.waitDelay > 0.0 || this.waitAcceleration != 0.0 || this.waitDeceleration != 0.0) {
            ConfigurationNode wait = config.getNode("wait");
            wait.set("distance", (this.waitDistance > 0) ? this.waitDistance : null);
            wait.set("delay", (this.waitDelay > 0.0) ? this.waitDelay : null);
            wait.set("acceleration", (this.waitAcceleration > 0.0) ? this.waitAcceleration : null);
            wait.set("deceleration", (this.waitDeceleration > 0.0) ? this.waitDeceleration : null);
        } else {
            config.remove("wait");
        }

        if (this.bankingStrength != 0.0 || this.bankingSmoothness != 10.0) {
            ConfigurationNode banking = config.getNode("banking");
            banking.set("strength", this.bankingStrength != 0.0 ? this.bankingStrength : null);
            banking.set("smoothness", this.bankingSmoothness != 10.0 ? this.bankingSmoothness : null);
        } else {
            config.remove("banking");
        }

        if (this.isSlowingDownAll()) {
            config.remove("slowDown");
        } else if (this.isSlowingDownNone()) {
            config.set("slowDown", false);
        } else {
            ConfigurationNode slowdownNode = config.getNode("slowDown");
            for (SlowdownMode mode : SlowdownMode.values()) {
                slowdownNode.set(mode.getKey(), this.isSlowingDown(mode));
            }
        }

        config.set("allowManualMovement", this.isManualMovementAllowed() ? true : null);
        config.set("allowMobManualMovement", this.isMobManualMovementAllowed() ? true : null);
        config.set("tickets", this.tickets.isEmpty() ? null : LogicUtil.toArray(this.tickets, String.class));

        if (this.skipOptions.isActive()) {
            this.skipOptions.save(config.getNode("skipOptions"));
        } else if (config.contains("skipOptions")) {
            config.remove("skipOptions");
        }

        // Save carts too!
        for (CartProperties cProp : this) {
            cProp.saveToConfig();
        }

        return config;
    }

    /**
     * Applies all of the configuration options defined in a configuration
     * node to this train and this train's carts. For every cart property
     * the property is updated for all the carts of this train. If a property
     * isn't stored in the configuration, the original value of this train
     * is preserved.
     * 
     * @param node Configuration node to apply to this train and carts
     */
    public void apply(ConfigurationNode node) {
        if (node == null) {
            return;
        }

        // Read all properties TrainCarts knows about from the configuration
        // This will read and apply both train and cart properties
        for (IProperty<Object> property : IPropertyRegistry.instance().all()) {
            Optional<Object> value = property.readFromConfig(node);
            if (value.isPresent()) {
                this.set(property, value.get());
            }
        }

        //TODO: These properties need to be transferred to IProperties!
        this.applyConfig(node);
        for (CartProperties prop : this) {
            prop.applyConfig(node);
        }

        // Fire onPropertiesChanged, if possible
        this.tryUpdate();
        for (CartProperties prop : this) {
            prop.tryUpdate();
        }
    }

    /**
     * Applies configuration. Will be replaced by IProperties eventually.
     * 
     * @param node
     */
    protected void applyConfig(ConfigurationNode node) {
        // TODO: Replace all below with IProperty objects
        this.allowPlayerTake = node.get("allowPlayerTake", this.allowPlayerTake);
        this.setCollisionDamage(node.get("collisionDamage", this.getCollisionDamage()));
        this.soundEnabled = node.get("soundEnabled", this.soundEnabled);
        this.gravity = node.get("gravity", this.gravity);
        this.requirePoweredMinecart = node.get("requirePoweredMinecart", this.requirePoweredMinecart);
        this.setKeepChunksLoaded(node.get("keepChunksLoaded", this.keepChunksLoaded));
        this.setManualMovementAllowed(node.get("allowManualMovement", this.isManualMovementAllowed()));
        this.setMobManualMovementAllowed(node.get("allowMobManualMovement", this.isMobManualMovementAllowed()));
        this.suffocation = node.get("suffocation", this.suffocation);
        this.killMessage = node.get("killMessage", this.killMessage);

        // Wait distance legacy, and the new wait properties
        if (node.contains("waitDistance")) {
            node.set("wait.distance", node.get("waitDistance"));
        }
        if (node.isNode("wait")) {
            ConfigurationNode wait = node.getNode("wait");
            this.waitDistance = wait.get("distance", this.waitDistance);
            this.waitDelay = wait.get("delay", this.waitDelay);
            this.waitAcceleration = wait.get("acceleration", this.waitAcceleration);
            this.waitDeceleration = wait.get("deceleration", this.waitDeceleration);
        }

        // Slowdown options for friction and gravity (and others?)
        if (node.isNode("slowDown")) {
            ConfigurationNode slowDownNode = node.getNode("slowDown");
            for (SlowdownMode mode : SlowdownMode.values()) {
                this.setSlowingDown(mode, slowDownNode.get(mode.getKey(), this.isSlowingDown(mode)));
            }
        } else if (node.contains("slowDown")) {
            this.setSlowingDown(node.get("slowDown", true));
        }

        // Banking
        if (node.isNode("banking")) {
            ConfigurationNode banking = node.getNode("banking");
            this.bankingStrength = banking.get("strength", this.bankingStrength);
            this.bankingSmoothness = banking.get("smoothness", this.bankingSmoothness);
        }

        // Tickets that can be used for this train
        if (node.contains("tickets")) {
            this.tickets.clear();
            this.tickets.addAll(node.getList("tickets", String.class));
        }

        // Load train skip options, if it exists
        if (node.isNode("skipOptions")) {
            this.skipOptions.load(node.getNode("skipOptions"));
        }

        // These properties are purely saved so they are written correctly when saving defaults
        // There are not meant to be read anywhere, because these exist as part of minecart metadata
        // Only read these when actually set, don't add them using get's default if not so
        // We don't want
        if (node.contains("blockTypes") || node.contains("blockOffset")) {
            this.blockTypes = node.get("blockTypes", this.blockTypes);
            this.blockOffset = node.get("blockOffset", this.blockOffset);

            // Apply block types / block height to the actual minecart, if set
            if (!this.blockTypes.isEmpty() || this.blockOffset != SignActionBlockChanger.BLOCK_OFFSET_NONE) {
                MinecartGroup group = this.getHolder();
                if (group != null) {
                    if (this.blockTypes.isEmpty()) {
                        SignActionBlockChanger.setBlocks(group, new ItemParser[0], this.blockOffset);
                    } else {
                        SignActionBlockChanger.setBlocks(group, this.blockTypes, this.blockOffset);
                    }
                }
            }
        }
    }

    // Stores all the default property values not already covered by IProperty
    protected static void generateDefaults(ConfigurationNode node) {
        node.set("soundEnabled", true);
        node.set("allowPlayerTake", false);
        node.set("requirePoweredMinecart", false);
        node.set("trainCollision", true);
        node.set("collisionDamage", 1.0);
        node.set("keepChunksLoaded", false);
        node.set("gravity", 1.0);
        node.set("suffocation", true);
        node.set("killMessage", "");

        ConfigurationNode wait = node.getNode("wait");
        wait.set("distance", 0.0);
        wait.set("delay", 0.0);
        wait.set("acceleration", 0.0);
        wait.set("deceleration", 0.0);

        ConfigurationNode banking = node.getNode("banking");
        banking.set("strength", 0.0);
        banking.set("smoothness", 10.0);

        ConfigurationNode slowdownNode = node.getNode("slowDown");
        for (SlowdownMode mode : SlowdownMode.values()) {
            slowdownNode.set(mode.getKey(), true);
        }

        node.set("allowManualMovement", false);
        node.set("allowMobManualMovement", false);
        node.set("tickets", StringUtil.EMPTY_ARRAY);

        node.set("collision.players", CollisionMode.DEFAULT);
        node.set("collision.misc", CollisionMode.PUSH);
        node.set("collision.train", CollisionMode.LINK);
        node.set("collision.block", CollisionMode.DEFAULT);

        node.set("blockTypes", "");
        node.set("blockOffset", "unset");
    }

    public double getCollisionDamage() {
        return this.collisionDamage;
    }

    public void setCollisionDamage(double collisionDamage) {
        this.collisionDamage = collisionDamage;
    }
}
