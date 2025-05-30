package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import com.bergerkiller.bukkit.tc.properties.defaults.DefaultProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.ChunkLoadOptions;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.chunk.ChunkFutureProvider;
import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.BankingOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionMobCategory;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.SlowdownMode;
import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;
import com.bergerkiller.bukkit.tc.properties.standard.type.WaitOptions;
import com.bergerkiller.bukkit.tc.offline.train.OfflineGroup;
import com.bergerkiller.bukkit.tc.utils.SoftReference;

public class TrainProperties extends TrainPropertiesStore implements IProperties {
    private static final long serialVersionUID = 1L;

    private final TrainCarts traincarts;
    private final SoftReference<MinecartGroup> group = new SoftReference<>();
    private final FieldBackedStandardTrainProperty.TrainInternalDataHolder standardProperties = new FieldBackedStandardTrainProperty.TrainInternalDataHolder();
    private final ConfigurationNode config;
    protected String trainname;
    protected boolean removed;

    /**
     * Creates new TrainProperties<br>
     * <br>
     * <b>Warning: must call onConfigurationChanged(true) on it or things break!</b>
     * 
     * @param trainname
     * @param config
     */
    protected TrainProperties(TrainCarts traincarts, String trainname, ConfigurationNode config) {
        this.traincarts = traincarts;
        this.trainname = trainname;
        this.config = config;
        this.removed = true; // Not added to a map yet

        // Pre-initialize the cart configuration, if such is available
        if (config.isNode("carts")) {
            for (ConfigurationNode cartConfig : config.getNode("carts").getNodes()) {
                // Decode node key as UUID
                UUID uuid;
                try {
                    uuid = UUID.fromString(cartConfig.getName());
                } catch (IllegalArgumentException ex) {
                    traincarts.getLogger().log(Level.WARNING, "Invalid UUID for cart: " + cartConfig.getName());
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
    public TrainCarts getTrainCarts() {
        return traincarts;
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
    public boolean isRemoved() {
        return removed;
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
    public FieldBackedStandardTrainProperty.TrainInternalDataHolder getStandardPropertiesHolder() {
        return standardProperties;
    }

    @Override
    public MinecartGroup getHolder() {
        MinecartGroup group = this.group.get();
        if (group == null || group.isRemoved()) {
            return null;
        } else {
            return group;
        }
    }

    protected void updateHolder(MinecartGroup holder, boolean set) {
        if (set) {
            if (this.group.get() != holder) {
                this.group.set(holder);
                this.onConfigurationChanged(true);
            }
        } else {
            if (this.group.get() == holder) {
                this.group.set(null);
            }
        }
    }

    @Override
    public boolean hasHolder() {
        return this.getHolder() != null;
    }

    @Override
    public CompletableFuture<Boolean> restore() {
        if (this.isLoaded()) {
            return CompletableFuture.completedFuture(true);
        }
        // Load all the chunks of this group to trigger a restore
        OfflineGroup group = getTrainCarts().getOfflineGroups().findGroup(this.trainname);
        if (group == null) {
            return CompletableFuture.completedFuture(false);
        }

        final List<ForcedChunk> chunksOfTrain = new ArrayList<>();
        final World world = group.world.getLoadedWorld();
        if (world != null) {
            group.forAllChunks((cx, cz) -> chunksOfTrain.add(WorldUtil.forceChunkLoaded(world, cx, cz)));
        }
        CompletableFuture<Void> whenAllChunkEntitiesLoaded;
        whenAllChunkEntitiesLoaded = loadChunkFutureWithFutureProvider(traincarts, chunksOfTrain);

        final CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();
        whenAllChunkEntitiesLoaded.thenAccept(unused -> {
            result.complete(hasHolder());
            chunksOfTrain.forEach(ForcedChunk::close);
        }).exceptionally(err -> {
            traincarts.getLogger().log(Level.SEVERE, "Failed to load chunks of train", err);
            result.complete(false);
            chunksOfTrain.forEach(ForcedChunk::close);
            return null;
        });

        return result;
    }

    private static CompletableFuture<Void> loadChunkFutureWithFutureProvider(TrainCarts traincarts, List<ForcedChunk> chunks) {
        ChunkFutureProvider provider = ChunkFutureProvider.of(traincarts);
        return CompletableFuture.allOf(chunks.stream()
            .map(c -> provider.whenEntitiesLoaded(c.getWorld(), c.getX(), c.getZ()))
            .toArray(CompletableFuture[]::new));
    }

    /**
     * Gets the wait distance. The train will automatically wait to maintain this distance between itself and the train up
     * ahead.
     * 
     * @return waitDistance
     */
    public double getWaitDistance() {
        return get(StandardProperties.WAIT).distance();
    }

    /**
     * Sets the wait distance. The train will automatically wait to maintain this distance between itself and the train up
     * ahead.
     * 
     * @param waitDistance
     */
    public void setWaitDistance(final double waitDistance) {
        this.update(StandardProperties.WAIT, opt -> WaitOptions.create(
                waitDistance, opt.delay(), opt.acceleration(), opt.deceleration(), opt.predict()
        ));
    }

    /**
     * Gets the number of seconds that must elapse after waiting for another train before the train starts moving again.
     * This is used when a wait distance is set.
     * 
     * @return wait delay in seconds, used after waiting for a train
     */
    public double getWaitDelay() {
        return get(StandardProperties.WAIT).delay();
    }

    /**
     * Sets the number of seconds that must elapse after waiting for another train before the train starts moving again.
     * This is used when a wait distance is set.
     * 
     * @param delay Delay to set to in seconds
     */
    public void setWaitDelay(final double delay) {
        this.update(StandardProperties.WAIT, opt -> WaitOptions.create(
                opt.distance(), delay, opt.acceleration(), opt.deceleration(), opt.predict()
        ));
    }

    /**
     * Gets the acceleration in blocks/tick^2 of the train when speeding up again after waiting for a train.
     * The speed of the train goes up by this amount every tick. If 0, the acceleration is instant.
     * 
     * @return acceleration of the train when speeding up after waiting
     */
    public double getWaitAcceleration() {
        return get(StandardProperties.WAIT).acceleration();
    }

    /**
     * Gets the deceleration inblocks/tick^2 of the train when slowing down, when the train has to wait
     * for another train. Speed of the train goes down by this amount every tick. If 0, the deceleration
     * is instant.
     * 
     * @return deceleration of the train when slowing down to wait for another train
     */
    public double getWaitDeceleration() {
        return get(StandardProperties.WAIT).deceleration();
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
     * @see #getWaitAcceleration()
     */
    public void setWaitAcceleration(final double acceleration, final double deceleration) {
        this.update(StandardProperties.WAIT, opt -> WaitOptions.create(
                opt.distance(), opt.delay(), acceleration, deceleration, opt.predict()
        ));
    }

    /**
     * Gets whether the train will use path prediction when deciding what obstacles
     * to wait for. When true, it will detect blockers and switchers up-ahead, and
     * route accordingly.
     *
     * @return True if predicting
     */
    public boolean isWaitPredicted() {
        return get(StandardProperties.WAIT).predict();
    }

    /**
     * Sets whether the train will use path prediction when deciding what obstacles
     * to wait for. When true, it will detect blockers and switchers up-ahead, and
     * route accordingly.
     *
     * @param use Whether to predict
     */
    public void setWaitPredicted(final boolean use) {
        this.update(StandardProperties.WAIT, opt -> WaitOptions.create(
                opt.distance(), opt.delay(), opt.acceleration(), opt.deceleration(), use
        ));
    }

    /**
     * Gets the maximum speed this Train can move at. Do not use this property inside physics functions! In there
     * getEntity().getMaxSpeed() should be used instead.
     *
     * @return max speed in blocks/tick
     */
    public double getSpeedLimit() {
        return StandardProperties.SPEEDLIMIT.getDouble(this);
    }

    /**
     * Sets the maximum speed this Train can move at<br>
     * The maximum speed limit is enforced.
     *
     * @param limit in blocks/tick
     */
    public void setSpeedLimit(double limit) {
        set(StandardProperties.SPEEDLIMIT, limit);
    }

    /**
     * Gets the gravity factor applied to the train, where 1.0 is the default
     * 
     * @return gravity factor
     */
    public double getGravity() {
        return StandardProperties.GRAVITY.getDouble(this);
    }

    /**
     * Sets the gravity factor applied to the train, where 1.0 is the default
     * 
     * @param gravity
     */
    public void setGravity(double gravity) {
        set(StandardProperties.GRAVITY, gravity);
    }

    /**
     * Gets the friction factor applied to the train, where 1.0 is the default
     * and 0 disables friction entirely.
     *
     * @return friction factor
     */
    public double getFriction() {
        return StandardProperties.FRICTION.getDouble(this);
    }

    /**
     * Sets the gravity factor applied to the train, where 1.0 is the default
     * and 0 disables friction entirely.
     *
     * @param friction
     */
    public void setFriction(double friction) {
        set(StandardProperties.FRICTION, friction);
    }

    /**
     * Gets whether the Train slows down over time.
     *
     * @return True if it slows down, False if not
     * @deprecated This returns True if any slowdown mode is true.
     */
    @Deprecated
    public boolean isSlowingDown() {
        return !get(StandardProperties.SLOWDOWN).isEmpty();
    }

    /**
     * Gets whether the slow down options are set to a default, where all slowdown modes are active.
     * 
     * @return True if all modes are active (legacy slowdown = true set)
     */
    public boolean isSlowingDownAll() {
        return get(StandardProperties.SLOWDOWN).equals(EnumSet.allOf(SlowdownMode.class));
    }

    /**
     * Gets whether all slow down options are disabled.
     * 
     * @return True if all slowdown is disabled (legacy slowdown = false set)
     */
    public boolean isSlowingDownNone() {
        return get(StandardProperties.SLOWDOWN).isEmpty();
    }

    /**
     * Sets whether the Train slows down over time.<br>
     * <b>Note: sets or clears all possible slowdown options at once</b>
     *
     * @param slowingDown state to set to
     */
    public void setSlowingDown(boolean slowingDown) {
        if (slowingDown) {
            set(StandardProperties.SLOWDOWN, EnumSet.allOf(SlowdownMode.class));
        } else {
            set(StandardProperties.SLOWDOWN, Collections.emptySet());
        }
    }

    /**
     * Gets whether a particular slow down mode is activated
     * 
     * @param mode to check
     * @return True if the slowdown mode is activated
     */
    public boolean isSlowingDown(SlowdownMode mode) {
        return get(StandardProperties.SLOWDOWN).contains(mode);
    }

    /**
     * Sets whether a particular slow down mode is activated
     * 
     * @param mode        to set
     * @param slowingDown option to set that mode to
     */
    public void setSlowingDown(final SlowdownMode mode, final boolean slowingDown) {
        update(StandardProperties.SLOWDOWN, curr_modes -> {
            if (slowingDown == curr_modes.contains(mode)) {
                return curr_modes;
            } else {
                EnumSet<SlowdownMode> new_modes = EnumSet.noneOf(SlowdownMode.class);
                new_modes.addAll(curr_modes);
                LogicUtil.addOrRemove(new_modes, mode, slowingDown);
                return new_modes;
            }
        });
    }

    /**
     * Gets the Display Name of these properties. If none is configured,
     * returns the train name instead. To check whether one is configured,
     * use {@link #getDisplayNameOrEmpty()}.
     *
     * @return display name
     */
    public String getDisplayName() {
        String name = get(StandardProperties.DISPLAY_NAME);
        return name.isEmpty() ? this.getTrainName() : name;
    }

    /**
     * Gets the currently configured display name. If none is configured,
     * returns an Empty String.
     * 
     * @return display name, or empty if none is set
     */
    public String getDisplayNameOrEmpty() {
        return get(StandardProperties.DISPLAY_NAME);
    }

    /**
     * Sets the Display Name for these properties<br>
     * If a null or empty String is passed in as argument, the display name is set to the train name. (it is reset)
     *
     * @param displayName to set to
     */
    public void setDisplayName(String displayName) {
        set(StandardProperties.DISPLAY_NAME, displayName);
    }

    /**
     * Gets whether this Train keeps nearby chunks loaded
     *
     * @return True or False
     * @see #getChunkLoadOptions()
     */
    public boolean isKeepingChunksLoaded() {
        return get(StandardProperties.CHUNK_LOAD_OPTIONS).keepLoaded();
    }

    /**
     * Sets whether this Train keeps nearby chunks loaded. Use the
     * {@link #setChunkLoadOptions(ChunkLoadOptions)} method for a more fine-grained
     * control over this. This method merely toggles between simulating between modes
     * FULL (true) and DISABLED (false).
     *
     * @param state to set to
     * @see #setChunkLoadOptions(ChunkLoadOptions)
     */
    public void setKeepChunksLoaded(boolean state) {
        setChunkLoadOptions(getChunkLoadOptions().withMode(
                state ? ChunkLoadOptions.Mode.FULL : ChunkLoadOptions.Mode.DISABLED));
    }

    /**
     * Gets the chunk loader configuration of this train
     *
     * @return Chunk loading options
     */
    public ChunkLoadOptions getChunkLoadOptions() {
        return get(StandardProperties.CHUNK_LOAD_OPTIONS);
    }

    /**
     * Sets the chunk loader configuration of this train. Unlike the
     * {@link #setKeepChunksLoaded(boolean)} option this option allows
     * control over the chunk loading radius and whether the loaded chunks
     * simulate entities/redstone.
     *
     * @param options New options
     */
    public void setChunkLoadOptions(ChunkLoadOptions options) {
        set(StandardProperties.CHUNK_LOAD_OPTIONS, options);
    }

    /**
     * Gets whether ambient Minecart sounds are enabled
     *
     * @return True if enabled, False if not
     */
    public boolean isSoundEnabled() {
        return get(StandardProperties.SOUND_ENABLED);
    }

    /**
     * Sets whether ambient Minecart sounds are enabled
     *
     * @param enabled state to set to
     */
    public void setSoundEnabled(boolean enabled) {
        set(StandardProperties.SOUND_ENABLED, enabled);
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
    public void setOwnerPermissions(Set<String> newOwnerPermissions) {
        for (CartProperties cprop : this) {
            cprop.setOwnerPermissions(newOwnerPermissions);
        }
    }

    @Override
    public Set<String> getOwners() {
        return get(StandardProperties.OWNERS);
    }

    @Override
    public void setOwners(Set<String> newOwners) {
        set(StandardProperties.OWNERS, newOwners);
    }

    @Override
    public void clearOwners() {
        set(StandardProperties.OWNERS, Collections.emptySet());
    }

    @Override
    public void addOwners(Collection<String> ownersToAdd) {
        for (CartProperties cprop : this) {
            cprop.addOwners(ownersToAdd);
        }
    }

    @Override
    public void removeOwners(Collection<String> ownersToRemove) {
        for (CartProperties cprop : this) {
            cprop.removeOwners(ownersToRemove);
        }
    }

    @Override
    public void clearOwnerPermissions() {
        for (CartProperties prop : this) {
            prop.clearOwnerPermissions();
        }
    }

    public void addOwnerPermission(final String permission) {
        for (CartProperties prop : this) {
            prop.addOwnerPermission(permission);
        }
    }

    public void removeOwnerPermission(final String permission) {
        for (CartProperties prop : this) {
            prop.removeOwnerPermission(permission);
        }
    }

    /**
     * Sets whether a player name is an owner of carts of this train.
     * 
     * @param player Name of the player to add
     * @param owner True to add as owner, False to remove as owner
     */
    public void setOwner(final String player, final boolean owner) {
        for (CartProperties cProp : this) {
            cProp.setOwner(player, owner);
        }
    }

    /**
     * Gets whether this Train supports players taking minecarts with them when they leave. When the Minecart is part of a
     * Train, it is always disallowed.
     *
     * @return True if players can take Minecarts with them, False if not.
     */
    public boolean isPlayerTakeable() {
        return get(StandardProperties.ALLOW_PLAYER_TAKE);
    }

    /**
     * Sets whether this Train supports players taking minecarts with them when they leave. When the Minecart is part of a
     * Train, it is always disallowed.
     *
     * @param takeable state to set to
     */
    public void setPlayerTakeable(boolean takeable) {
        set(StandardProperties.ALLOW_PLAYER_TAKE, takeable);
    }

    public double getBankingStrength() {
        return get(StandardProperties.BANKING).strength();
    }

    public double getBankingSmoothness() {
        return get(StandardProperties.BANKING).smoothness();
    }

    public void setBanking(double strength, double smoothness) {
        set(StandardProperties.BANKING, BankingOptions.create(strength, smoothness));
    }

    public void setBankingStrength(final double strength) {
        update(StandardProperties.BANKING, opt -> BankingOptions.create(
                strength, opt.smoothness()
        ));
    }

    public void setBankingSmoothness(final double smoothness) {
        update(StandardProperties.BANKING, opt -> BankingOptions.create(
                opt.strength(), smoothness
        ));
    }

    @Override
    public boolean getCanOnlyOwnersEnter() {
        return get(StandardProperties.ONLY_OWNERS_CAN_ENTER);
    }

    @Override
    public void setCanOnlyOwnersEnter(boolean state) {
        set(StandardProperties.ONLY_OWNERS_CAN_ENTER, state);
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
        return get(StandardProperties.TAGS);
    }

    @Override
    public void setTags(String... tags) {
        set(StandardProperties.TAGS, new HashSet<String>(Arrays.asList(tags)));
    }

    @Override
    public void clearTags() {
        set(StandardProperties.TAGS, Collections.emptySet());
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
        return !get(StandardProperties.DESTINATION).isEmpty();
    }

    @Override
    public String getDestination() {
        return get(StandardProperties.DESTINATION);
    }

    @Override
    public void setDestination(String destination) {
        set(StandardProperties.DESTINATION, destination);
    }

    @Override
    public List<String> getDestinationRoute() {
        return get(StandardProperties.DESTINATION_ROUTE);
    }

    @Override
    public void setDestinationRoute(List<String> route) {
        set(StandardProperties.DESTINATION_ROUTE, route);
    }

    @Override
    public void clearDestinationRoute() {
        set(StandardProperties.DESTINATION_ROUTE, Collections.emptyList());
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
    public String getNextDestinationOnRoute(String currentDestination) {
        for (CartProperties prop : this) {
            if (!prop.getDestinationRoute().isEmpty()) {
                return prop.getNextDestinationOnRoute(currentDestination);
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

    public boolean isPoweredMinecartRequired() {
        return get(StandardProperties.REQUIRE_POWERED_MINECART);
    }

    public void setPoweredMinecartRequired(boolean required) {
        set(StandardProperties.REQUIRE_POWERED_MINECART, required);
    }

    public double getCollisionDamage() {
        return get(StandardProperties.COLLISION_DAMAGE);
    }

    public void setCollisionDamage(double collisionDamage) {
        set(StandardProperties.COLLISION_DAMAGE, collisionDamage);
    }

    public CollisionMode getCollisionMode(Entity entity) {
        if (entity.isDead()) {
            return CollisionMode.CANCEL;
        }
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(entity);
        CollisionOptions collision = this.getCollision();
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
     * @param newTrainName New train name to set to
     * @throws IllegalArgumentException if another train by this name already {@link #exists(String)}
     */
    public void setTrainName(String newTrainName) {
        rename(this, newTrainName);
    }

    /**
     * Renames this train, this should be called to rename the train safely
     *
     * @param newtrainname to set to
     * @return this
     * @deprecated Use {@link #setTrainName(String)} instead
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
        return get(StandardProperties.SUFFOCATION);
    }

    /**
     * Sets whether passengers inside this train sustain suffocation damage when their head is submerged inside a block.
     * 
     * @param suffocation option
     */
    public void setSuffocation(boolean suffocation) {
        set(StandardProperties.SUFFOCATION, suffocation);
    }

    /**
     * Gets whether train player passengers can manually move the train they are in
     *
     * @return True if manual movement is allowed, False if not
     */
    public boolean isManualMovementAllowed() {
        return get(StandardProperties.ALLOW_PLAYER_MANUAL_MOVEMENT);
    }

    /**
     * Sets whether train player passengers can manually move the train they are in
     *
     * @param allow state to set to
     */
    public void setManualMovementAllowed(boolean allow) {
        set(StandardProperties.ALLOW_PLAYER_MANUAL_MOVEMENT, allow);
    }

    /**
     * Gets whether train non-player passengers can manually move the train they are in
     *
     * @return True if manual movement is allowed, False if not
     */
    public boolean isMobManualMovementAllowed() {
        return get(StandardProperties.ALLOW_MOB_MANUAL_MOVEMENT);
    }

    /**
     * Sets whether train non-player passengers can manually move the train they are in
     *
     * @param allow state to set to
     */
    public void setMobManualMovementAllowed(boolean allow) {
        set(StandardProperties.ALLOW_MOB_MANUAL_MOVEMENT, allow);
    }

    /**
     * Gets whether the train uses realtime physics. When enabled, takes into account
     * the server tick rate when moving the train. This speeds up the train when the
     * server lags behind, and slows it down when the server catches up. This attempts
     * to make the movement speed of the train constant despite server tick rate jitter.
     *
     * @return True if realtime physics is enabled
     */
    public boolean hasRealtimePhysics() {
        return get(StandardProperties.REALTIME_PHYSICS);
    }

    /**
     * Sets whether the train uses realtime physics. When enabled, takes into account
     * the server tick rate when moving the train. This speeds up the train when the
     * server lags behind, and slows it down when the server catches up. This attempts
     * to make the movement speed of the train constant despite server tick rate jitter.
     *
     * @param realtime Whether realtime physics is enabled
     */
    public void setRealtimePhysics(boolean realtime) {
        set(StandardProperties.REALTIME_PHYSICS, realtime);
    }

    /**
     * Gets a list of tickets that can be used for entering this train
     * 
     * @return tickets
     */
    public Set<String> getTickets() {
        return get(StandardProperties.TICKETS);
    }

    /**
     * Adds a new ticket that can be used for entering this train.
     * 
     * @param ticketName to add
     */
    public void addTicket(String ticketName) {
        update(StandardProperties.TICKETS, tickets -> {
            if (tickets.contains(ticketName)) {
                return tickets;
            } else {
                HashSet<String> new_tickets = new HashSet<>(tickets);
                new_tickets.add(ticketName);
                return new_tickets;
            }
        });
    }

    /**
     * Revokes a ticket from being used for entering this train.
     * 
     * @param ticketName to remove
     */
    public void removeTicket(String ticketName) {
        update(StandardProperties.TICKETS, tickets -> {
            if (!tickets.contains(ticketName)) {
                return tickets;
            } else {
                HashSet<String> new_tickets = new HashSet<>(tickets);
                new_tickets.remove(ticketName);
                return new_tickets;
            }
        });
    }

    public void clearTickets() {
        set(StandardProperties.TICKETS, Collections.emptySet());
    }

    public SignSkipOptions getSkipOptions() {
        return get(StandardProperties.SIGN_SKIP);
    }

    public void setSkipOptions(SignSkipOptions options) {
        set(StandardProperties.SIGN_SKIP, options);
    }

    public String getKillMessage() {
        return get(StandardProperties.KILL_MESSAGE);
    }

    public void setKillMessage(String killMessage) {
        set(StandardProperties.KILL_MESSAGE, killMessage);
    }

    public boolean isTrainRenamed() {
        return !TrainNameFormat.DEFAULT.matches(getTrainName());
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
        DefaultProperties defaults = getDefaultsByName(key);
        if (defaults != null) {
            this.apply(defaults);
        }
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

    /**
     * @deprecated Use {@link #apply(ConfigurationNode)} instead
     */
    @Deprecated
    public void setDefault(ConfigurationNode node) {
        this.apply(node);
    }

    public void tryUpdate() {
        MinecartGroup g = this.getHolder();
        if (g != null) g.onPropertiesChanged();
    }

    @Override
    public boolean parseSet(String key, String arg) {
        // Legacy
        return parseAndSet(key, arg).getReason() != PropertyParseResult.Reason.PROPERTY_NOT_FOUND;
    }

    /**
     * Gets the collision configuration of the train. This stores all the collision rules
     * to follow when colliding with entities and blocks.
     * 
     * @return collision configuration
     */
    public CollisionOptions getCollision() {
        return get(StandardProperties.COLLISION);
    }

    /**
     * Sets the collision configuration of the train. This stores all the collision rules
     * to follow when colliding with entities and blocks. Use the static methods
     * of {@link CollisionOptions} to change individual modes.
     * 
     * @param collisionConfig New collision configuration to set to
     */
    public void setCollision(CollisionOptions collisionConfig) {
        set(StandardProperties.COLLISION, collisionConfig);
    }

    /**
     * Sets the collision mode configured for a collision mob category
     * 
     * @param mobCategory Category of mob
     * @param mode to set to, null to reset to defaults
     */
    public void setCollisionMode(final CollisionMobCategory mobCategory, final CollisionMode mode) {
        update(StandardProperties.COLLISION, opt -> opt.cloneAndSetMobMode(mobCategory, mode));
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
        setCollision(getCollision().cloneAndSetForAllMobs(mode));
    }

    /**
     * Updates the collision mode for all mob collision categories if the current configured value
     * matches the expected mode
     * 
     * @param expected
     * @param mode
     */
    public void setCollisionModeIfModeForMobs(final CollisionMode expected, final CollisionMode mode) {
        setCollision(getCollision().cloneCompareAndSetForAllMobs(expected, mode));
    }

    /**
     * Sets train collision mode to link when true. When false is specified and the collision mode is linking, it is set to
     * default. This is legacy behavior.
     * 
     * @param linking
     */
    public void setLinking(final boolean linking) {
        update(StandardProperties.COLLISION, opt -> {
            if (linking) {
                return opt.cloneAndSetTrainMode(CollisionMode.LINK);
            } else if (opt.trainMode() == CollisionMode.LINK) {
                return opt.cloneAndSetTrainMode(CollisionMode.DEFAULT);
            } else {
                return opt;
            }
        });
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
        this.load(source.getConfig());
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
        node.cloneIntoExcept(this.config, Collections.singleton("carts"));

        // Reload properties
        onConfigurationChanged(false);
    }

    @Override
    public void save(ConfigurationNode node) {
        getConfig().cloneInto(node);
    }

    protected void onConfigurationChanged(boolean cartsChanged) {
        // Refresh registered IProperties
        // All below should eventually become IProperties, which is when this function
        // can be removed!
        for (IProperty<?> property : IPropertyRegistry.instance().all()) {
            property.onConfigurationChanged(this);
        }

        // TODO: Replace all below with IProperty objects
        // Note: completely disregards all previous configuration!

        // Also refresh carts
        if (cartsChanged) {
            for (CartProperties cart : this) {
                cart.onConfigurationChanged();
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
     * @deprecated No longer does anything! Just use {@link #getConfig()}
     */
    @Deprecated
    public ConfigurationNode saveToConfig() {
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
     * is preserved.<br>
     * <br>
     * <b>Note: it is more performant to create a {@link DefaultProperties}
     * configuration and reuse that instead, if possible.</b>
     *
     * @param node Configuration node to apply to this train and carts
     * @see #apply(DefaultProperties)
     */
    public void apply(ConfigurationNode node) {
        if (node != null) {
            DefaultProperties.of(node).applyTo(this);
        }
    }

    /**
     * Applies all of the default property values in a default properties
     * configuration to this train and this train's carts. For every cart property
     * the property is updated for all the carts of this train. If a property
     * isn't stored in the defaults configuration, the original value of this train
     * is preserved.
     *
     * @param defaultProperties Default Properties configuration
     */
    public void apply(DefaultProperties defaultProperties) {
        defaultProperties.applyTo(this);
    }
}
