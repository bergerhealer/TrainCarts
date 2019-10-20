package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlockChanger;
import com.bergerkiller.bukkit.tc.storage.OfflineGroup;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.SignSkipOptions;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;
import com.bergerkiller.bukkit.tc.utils.SoftReference;

public class TrainProperties extends TrainPropertiesStore implements IProperties {
    public static final TrainProperties EMPTY = new TrainProperties("");
    private static final long serialVersionUID = 1L;

    static {
        EMPTY.add(CartProperties.EMPTY);
    }

    private final SoftReference<MinecartGroup> group = new SoftReference<>();
    private Map<CollisionConfig, CollisionMode> collisionModes = new HashMap<>();
    public CollisionMode playerCollision = CollisionMode.DEFAULT;
    public CollisionMode miscCollision = CollisionMode.PUSH;
    public CollisionMode trainCollision = CollisionMode.LINK;
    public CollisionMode blockCollision = CollisionMode.DEFAULT;
    public boolean requirePoweredMinecart = false;
    protected String trainname;
    private String displayName;
    private boolean collision = true;
    private final EnumSet<SlowdownMode> slowDownOptions = EnumSet.allOf(SlowdownMode.class);
    private double speedLimit = 0.4;
    private double collisionDamage = 1.0D;
    private boolean keepChunksLoaded = false;
    private boolean allowManualMovement = false;
    private boolean allowPlayerTake = false;
    private boolean soundEnabled = true;
    private List<String> tickets = new ArrayList<>();
    private SignSkipOptions skipOptions = new SignSkipOptions();
    private String blockTypes = "";
    private int blockOffset = SignActionBlockChanger.BLOCK_OFFSET_NONE;
    private double waitDistance = 0.0;
    private double bankingStrength = 0.0;
    private double bankingSmoothness = 10.0;
    private boolean suffocation = true;
    private String killMessage = "";

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
     * Gets the maximum speed this Train can move at. Do not use this property inside physics functions! In there
     * getEntity().getMaxSpeed() should be used instead.
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
        this.speedLimit = MathUtil.clamp(limit, 0, TCConfig.maxVelocity);
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
     * Gets the Display Name of these properties
     *
     * @return display name
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Sets the Display Name for these properties<br>
     * If a null or empty String is passed in as argument, the display name is set to the train name. (it is reset)
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
    public Set<CollisionMode> getAllCollisionModes() {
        return (Set<CollisionMode>) this.collisionModes.values();
    }

    public CollisionMode getCollisionMode(Entity entity) {
        if (!this.getColliding() || entity.isDead()) {
            return CollisionMode.CANCEL;
        }
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(entity);
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
            GameMode playerGameMode = ((Player) entity).getGameMode();
            if (playerGameMode == GameMode.SPECTATOR) {
                return CollisionMode.CANCEL;
            }
            if (TCConfig.collisionIgnoreOwners && this.playerCollision != CollisionMode.DEFAULT) {
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
                if (this.playerCollision == CollisionMode.KILL || this.playerCollision == CollisionMode.KILLNODROPS || this.playerCollision == CollisionMode.DAMAGE
                        || this.playerCollision == CollisionMode.DAMAGENODROPS) {
                    return CollisionMode.PUSH;
                }
            }
            return this.playerCollision;
        } else {
            for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
                CollisionMode collisionMode = this.collisionModes.get(collisionConfigObject);
                if (collisionMode != null && collisionConfigObject.isMobType(entity)) {
                    return collisionMode;
                }
            }
            return this.miscCollision;
        }
    }

    public String getTrainName() {
        return this.trainname;
    }

    /**
     * Renames this train, this should be called to rename the train safely
     *
     * @param newtrainname to set to
     * @return this
     */
    public TrainProperties setName(String newtrainname) {
        rename(this, newtrainname);
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
        this.skipOptions.filter = options.filter;
        this.skipOptions.ignoreCtr = options.ignoreCtr;
        this.skipOptions.skipCtr = options.skipCtr;
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
        this.tryUpdate();
    }

    public void setDefault(Player player) {
        if (player == null) {
            // Set default
            this.setDefault();
        } else {
            // Load it
            this.setDefault(getDefaultsByPlayer(player));
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
            this.playerCollision = mode;
        } else if (key.equalsIgnoreCase("misccollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null) return false;
            this.miscCollision = mode;
        } else if (key.equalsIgnoreCase("traincollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null) return false;
            this.trainCollision = mode;
        } else if (key.equalsIgnoreCase("blockcollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null) return false;
            this.blockCollision = mode;
        } else if (key.equalsIgnoreCase("collisiondamage")) {
            this.setCollisionDamage(Double.parseDouble(arg));
        } else if (key.equalsIgnoreCase("suffocation")) {
            this.suffocation = ParseUtil.parseBool(arg);
        } else if (this.setCollisionMode(key, arg)) {
            return true;
        } else if (LogicUtil.containsIgnoreCase(key, "collision", "collide")) {
            this.setColliding(ParseUtil.parseBool(arg));
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
            this.playerCollision = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
        } else if (key.equalsIgnoreCase("pushmisc")) {
            this.miscCollision = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "push", "pushing")) {
            CollisionMode mode = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
            this.playerCollision = this.miscCollision = mode;
            this.updateAllCollisionProperties(mode);
        } else if (LogicUtil.containsIgnoreCase(key, "speedlimit", "maxspeed")) {
            this.setSpeedLimit(ParseUtil.parseDouble(arg, 0.4));
        } else if (LogicUtil.containsIgnoreCase(key, "allowmanual", "manualmove", "manual")) {
            this.allowManualMovement = ParseUtil.parseBool(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "keepcloaded", "loadchunks", "keeploaded")) {
            this.setKeepChunksLoaded(ParseUtil.parseBool(arg));
        } else if (key.equalsIgnoreCase("addtag")) {
            this.addTags(arg);
        } else if (key.equalsIgnoreCase("settag")) {
            this.setTags(arg);
        } else if (key.equalsIgnoreCase("destination")) {
            this.setDestination(arg);
        } else if (key.equalsIgnoreCase("remtag") || key.equalsIgnoreCase("removetag")) {
            this.removeTags(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "name", "rename", "setname")) {
            this.setName(generateTrainName(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "dname", "displayname", "setdisplayname", "setdname")) {
            this.setDisplayName(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "mobenter", "mobsenter")) {
            this.updateAllCollisionProperties(CollisionMode.fromEntering(ParseUtil.parseBool(arg)));
        } else if (key.equalsIgnoreCase("waitdistance")) {
            this.setWaitDistance(ParseUtil.parseDouble(arg, this.waitDistance));
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
        } else {
            return false;
        }
        this.tryUpdate();
        return true;
    }

    /*
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

    /**
     * Sets train collision mode to link when true. When false is specified and the collision mode is linking, it is set to
     * default. This is legacy behavior.
     * 
     * @param linking
     */
    public void setLinking(boolean linking) {
        if (linking) {
            this.trainCollision = CollisionMode.LINK;
        } else if (this.trainCollision == CollisionMode.LINK) {
            this.trainCollision = CollisionMode.DEFAULT;
        }
    }

    public boolean updateCollisionProperties(String mobType, CollisionMode mode) {
        if (mode == null) {
            return false;
        }
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            if (mobType.equals(collisionConfigObject.getMobType()) || mobType.equals(collisionConfigObject.getPluralMobType())) {
                this.collisionModes.put(collisionConfigObject, mode);
                return true;
            }
        }
        return false;
    }

    public void updateAllCollisionProperties(CollisionMode mode) {
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            if (collisionConfigObject.isAddToConfigFile() == true) {
                this.collisionModes.put(collisionConfigObject, mode);
            }
        }
    }

    @Override
    public void load(ConfigurationNode node) {
        this.setDisplayName(node.get("displayName", this.displayName));
        this.allowPlayerTake = node.get("allowPlayerTake", this.allowPlayerTake);
        this.collision = node.get("trainCollision", this.collision);
        this.setCollisionDamage(node.get("collisionDamage", this.getCollisionDamage()));
        this.soundEnabled = node.get("soundEnabled", this.soundEnabled);

        if (node.isNode("slowDown")) {
            ConfigurationNode slowDownNode = node.getNode("slowDown");
            for (SlowdownMode mode : SlowdownMode.values()) {
                this.setSlowingDown(mode, slowDownNode.get(mode.getKey(), this.isSlowingDown(mode)));
            }
        } else if (node.contains("slowDown")) {
            this.setSlowingDown(node.get("slowDown", true));
        }

        if (node.contains("collision")) {
            for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
                CollisionMode mode = node.get("collision." + collisionConfigObject.getMobType(), CollisionMode.SKIP);
                this.collisionModes.put(collisionConfigObject, mode == CollisionMode.SKIP ? null : mode);
            }
            this.playerCollision = node.get("collision.players", this.playerCollision);
            this.miscCollision = node.get("collision.misc", this.miscCollision);
            this.trainCollision = node.get("collision.train", this.trainCollision);
            this.blockCollision = node.get("collision.block", this.blockCollision);
        }
        this.speedLimit = MathUtil.clamp(node.get("speedLimit", this.speedLimit), 0, TCConfig.maxVelocity);
        this.requirePoweredMinecart = node.get("requirePoweredMinecart", this.requirePoweredMinecart);
        this.setKeepChunksLoaded(node.get("keepChunksLoaded", this.keepChunksLoaded));
        this.allowManualMovement = node.get("allowManualMovement", this.allowManualMovement);
        this.waitDistance = node.get("waitDistance", this.waitDistance);
        this.suffocation = node.get("suffocation", this.suffocation);
        this.killMessage = node.get("killMessage", this.killMessage);
        for (String ticket : node.getList("tickets", String.class)) {
            this.tickets.add(ticket);
        }
        if (node.isNode("skipOptions")) {
            this.skipOptions.load(node.getNode("skipOptions"));
        }

        // Only used when loading defaults from tickets, or when 'destination: ' is set in DefTrProps.yml
        // This allows properties defined at train level to be applied to all carts
        for (CartProperties cart : this) {
            cart.load(node);
        }

        // Load individual carts (by name)
        if (node.isNode("carts")) {
            for (ConfigurationNode cart : node.getNode("carts").getNodes()) {
                try {
                    CartProperties prop = CartPropertiesStore.get(UUID.fromString(cart.getName()), this);
                    this.add(prop);
                    prop.load(cart);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        // These properties are purely saved so they are written correctly when saving defaults
        // There are not meant to be read anywhere, because these exist as part of minecart metadata
        // Only read these when actually set, don't add them using get's default if not so
        // We don't want
        if (node.contains("blockTypes") || node.contains("blockOffset")) {
            this.blockTypes = node.get("blockTypes", "");
            this.blockOffset = node.get("blockOffset", SignActionBlockChanger.BLOCK_OFFSET_NONE);
        } else {
            this.blockTypes = "";
            this.blockOffset = SignActionBlockChanger.BLOCK_OFFSET_NONE;
        }

        // Banking
        if (node.isNode("banking")) {
            ConfigurationNode banking = node.getNode("banking");
            this.bankingStrength = banking.get("strength", this.bankingStrength);
            this.bankingSmoothness = banking.get("smoothness", this.bankingSmoothness);
        }

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
        this.slowDownOptions.clear();
        this.slowDownOptions.addAll(source.slowDownOptions);
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            this.collisionModes.put(collisionConfigObject, source.getCollisionMode(collisionConfigObject));
        }
        this.playerCollision = source.playerCollision;
        this.miscCollision = source.miscCollision;
        this.trainCollision = source.trainCollision;
        this.blockCollision = source.blockCollision;
        this.setCollisionDamage(source.collisionDamage);
        this.speedLimit = MathUtil.clamp(source.speedLimit, 0, 20);
        this.requirePoweredMinecart = source.requirePoweredMinecart;
        this.setKeepChunksLoaded(source.keepChunksLoaded);
        this.allowManualMovement = source.allowManualMovement;
        this.tickets = new ArrayList<>(source.tickets);
        this.setSkipOptions(source.skipOptions);
        this.blockTypes = source.blockTypes;
        this.blockOffset = source.blockOffset;
        this.waitDistance = source.waitDistance;
        this.bankingStrength = source.bankingStrength;
        this.bankingSmoothness = source.bankingSmoothness;
        this.suffocation = source.suffocation;
        this.killMessage = source.killMessage;
    }

    public CollisionMode getCollisionMode(CollisionConfig collisionConfigObject) {
        return this.collisionModes.get(collisionConfigObject);
    }

    @Override
    public void saveAsDefault(ConfigurationNode node) {
        node.set("soundEnabled", this.soundEnabled);
        node.set("displayName", this.displayName);
        node.set("allowPlayerTake", this.allowPlayerTake);
        node.set("requirePoweredMinecart", this.requirePoweredMinecart);
        node.set("trainCollision", this.collision);
        node.set("collisionDamage", this.getCollisionDamage());
        node.set("keepChunksLoaded", this.keepChunksLoaded);
        node.set("speedLimit", this.speedLimit);
        node.set("waitDistance", this.waitDistance);
        node.set("suffocation", this.suffocation);
        node.set("killMessage", this.killMessage);

        ConfigurationNode banking = node.getNode("banking");
        banking.set("strength", this.bankingStrength);
        banking.set("smoothness", this.bankingSmoothness);

        if (this.isSlowingDownAll()) {
            node.set("slowDown", true);
        } else if (this.isSlowingDownNone()) {
            node.set("slowDown", false);
        } else {
            ConfigurationNode slowdownNode = node.getNode("slowDown");
            for (SlowdownMode mode : SlowdownMode.values()) {
                slowdownNode.set(mode.getKey(), this.isSlowingDown(mode));
            }
        }

        node.set("allowManualMovement", this.allowManualMovement);
        node.set("tickets", StringUtil.EMPTY_ARRAY);
        node.set("collision.players", this.playerCollision);
        node.set("collision.misc", this.miscCollision);
        node.set("collision.train", this.trainCollision);
        node.set("collision.block", this.blockCollision);
        node.set("blockTypes", (this.blockTypes == null) ? "" : this.blockTypes);
        node.set("blockOffset", (this.blockOffset == SignActionBlockChanger.BLOCK_OFFSET_NONE) ? "unset" : this.blockOffset);
        for (CartProperties prop : this) {
            prop.saveAsDefault(node);
            break;
        }
    }

    @Override
    public void save(ConfigurationNode node) {
        node.set("displayName", this.displayName.equals(this.trainname) ? null : this.displayName);
        node.set("soundEnabled", this.soundEnabled ? null : false);
        node.set("allowPlayerTake", this.allowPlayerTake ? true : null);
        node.set("requirePoweredMinecart", this.requirePoweredMinecart ? true : null);
        node.set("trainCollision", this.collision ? null : false);
        node.set("collisionDamage", this.getCollisionDamage());
        node.set("keepChunksLoaded", this.keepChunksLoaded ? true : null);
        node.set("speedLimit", this.speedLimit != 0.4 ? this.speedLimit : null);
        node.set("waitDistance", (this.waitDistance > 0) ? this.waitDistance : null);
        node.set("suffocation", this.suffocation ? null : false);
        node.set("killMessage", this.killMessage.isEmpty() ? null : this.killMessage);

        if (this.bankingStrength != 0.0 || this.bankingSmoothness != 10.0) {
            ConfigurationNode banking = node.getNode("banking");
            banking.set("strength", this.bankingStrength != 0.0 ? this.bankingStrength : null);
            banking.set("smoothness", this.bankingSmoothness != 10.0 ? this.bankingSmoothness : null);
        } else {
            node.remove("banking");
        }

        if (this.isSlowingDownAll()) {
            node.remove("slowDown");
        } else if (this.isSlowingDownNone()) {
            node.set("slowDown", false);
        } else {
            ConfigurationNode slowdownNode = node.getNode("slowDown");
            for (SlowdownMode mode : SlowdownMode.values()) {
                slowdownNode.set(mode.getKey(), this.isSlowingDown(mode));
            }
        }

        node.set("allowManualMovement", this.allowManualMovement ? true : null);
        node.set("tickets", LogicUtil.toArray(this.tickets, String.class));
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            CollisionMode value = this.collisionModes.get(collisionConfigObject);
            if (collisionConfigObject.isAddToConfigFile() || value != null) {
                node.set("collision." + collisionConfigObject.getMobType(), value != null ? value : CollisionMode.DEFAULT);
            }
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
        if (this.blockCollision != CollisionMode.DEFAULT) {
            node.set("collision.block", this.blockCollision);
        }
        if (!this.isEmpty()) {
            ConfigurationNode carts = node.getNode("carts");
            for (CartProperties prop : this) {
                ConfigurationNode cart = carts.getNode(prop.getUUID().toString());
                prop.save(cart);
                if (cart.getKeys().isEmpty()) carts.remove(cart.getName());
            }
        }

        if (this.skipOptions.isActive()) {
            this.skipOptions.save(node.getNode("skipOptions"));
        } else if (node.contains("skipOptions")) {
            node.remove("skipOptions");
        }
    }

    public double getCollisionDamage() {
        return this.collisionDamage;
    }

    public void setCollisionDamage(double collisionDamage) {
        this.collisionDamage = collisionDamage;
    }
}
