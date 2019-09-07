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
        displayName = this.trainname = trainname;
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
        return getHolder() != null;
    }

    @Override
    public boolean restore() {
        if (isLoaded()) {
            return true;
        }
        // Load all the chunks of this group to trigger a restore
        OfflineGroup group = OfflineGroupManager.findGroup(trainname);
        if (group == null) {
            TrainPropertiesStore.remove(getTrainName());
            return false;
        }
        World world = Bukkit.getWorld(group.worldUUID);
        if (world != null) {
            for (long chunk : group.chunks) {
                world.getChunkAt(MathUtil.longHashMsw(chunk), MathUtil.longHashLsw(chunk));
            }
        }
        return hasHolder();
    }

    /**
     * Gets the wait distance. The train will automatically wait to maintain this distance between itself and the train up
     * ahead.
     * 
     * @return waitDistance
     */
    public double getWaitDistance() {
        return waitDistance;

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
        return speedLimit;
    }

    /**
     * Sets the maximum speed this Train can move at<br>
     * The maximum speed limit is enforced.
     *
     * @param limit in blocks/tick
     */
    public void setSpeedLimit(double limit) {
        speedLimit = MathUtil.clamp(limit, 0, TCConfig.maxVelocity);
    }

    /**
     * Gets whether the Train slows down over time.<br>
     * <b>Deprecated: This returns True if any slowdown mode is true.</b>
     *
     * @return True if it slows down, False if not
     */
    @Deprecated
    public boolean isSlowingDown() {
        return !slowDownOptions.isEmpty();
    }

    /**
     * Gets whether the slow down options are set to a default, where all slowdown modes are active.
     * 
     * @return True if all modes are active (legacy slowdown = true set)
     */
    public boolean isSlowingDownAll() {
        return slowDownOptions.size() == SlowdownMode.values().length;
    }

    /**
     * Gets whether all slow down options are disabled.
     * 
     * @return True if all slowdown is disabled (legacy slowdown = false set)
     */
    public boolean isSlowingDownNone() {
        return slowDownOptions.isEmpty();
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
                slowDownOptions.add(mode);
            }
        } else {
            slowDownOptions.clear();
        }
    }

    /**
     * Gets whether a particular slow down mode is activated
     * 
     * @param mode to check
     * @return True if the slowdown mode is activated
     */
    public boolean isSlowingDown(SlowdownMode mode) {
        return slowDownOptions.contains(mode);
    }

    /**
     * Sets whether a particular slow down mode is activated
     * 
     * @param mode        to set
     * @param slowingDown option to set that mode to
     */
    public void setSlowingDown(SlowdownMode mode, boolean slowingDown) {
        LogicUtil.addOrRemove(slowDownOptions, mode, slowingDown);
    }

    /**
     * Gets whether this Train can collide with other Entities and Trains
     *
     * @return True if it can collide, False if not
     */
    public boolean getColliding() {
        return collision;
    }

    /**
     * Sets whether this Train can collide with other Entities and Trains
     *
     * @param state to set to
     */
    public void setColliding(boolean state) {
        collision = state;
    }

    /**
     * Gets the Display Name of these properties
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the Display Name for these properties<br>
     * If a null or empty String is passed in as argument, the display name is set to the train name. (it is reset)
     *
     * @param displayName to set to
     */
    public void setDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            this.displayName = trainname;
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
        return keepChunksLoaded;
    }

    /**
     * Sets whether this Train keeps nearby chunks loaded
     *
     * @param state to set to
     */
    public void setKeepChunksLoaded(boolean state) {
        if (state && !keepChunksLoaded) {
            restore();
        }
        if (state != keepChunksLoaded) {
            keepChunksLoaded = state;
            MinecartGroup group = getHolder();
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
        return soundEnabled;
    }

    /**
     * Sets whether ambient Minecart sounds are enabled
     *
     * @param enabled state to set to
     */
    public void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
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
        return !hasOwners() && !hasOwnerPermissions();
    }

    @Override
    public boolean hasOwners() {
        for (CartProperties prop : this) {
            if (prop.hasOwners())
                return true;
        }
        return false;
    }

    @Override
    public boolean hasOwnership(Player player) {
        return CartProperties.hasGlobalOwnership(player) || isOwnedByEveryone() || isOwner(player);
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
        return allowPlayerTake;
    }

    /**
     * Sets whether this Train supports players taking minecarts with them when they leave. When the Minecart is part of a
     * Train, it is always disallowed.
     *
     * @param takeable state to set to
     */
    public void setPlayerTakeable(boolean takeable) {
        allowPlayerTake = takeable;
    }

    public double getBankingStrength() {
        return bankingStrength;
    }

    public double getBankingSmoothness() {
        return bankingSmoothness;
    }

    public void setBankingStrength(double strength) {
        bankingStrength = strength;
    }

    public void setBankingSmoothness(double smoothness) {
        bankingSmoothness = smoothness;
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
            if (prop.matchTag(tag))
                return true;
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
            if (prop.hasDestination())
                return true;
        }
        return false;
    }

    @Override
    public String getDestination() {
        for (CartProperties prop : this) {
            if (prop.hasDestination())
                return prop.getDestination();
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
        return isEmpty() ? "" : this.get(0).getLastPathNode();
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
        return (Set<CollisionMode>) collisionModes.values();
    }

    public CollisionMode getCollisionMode(Entity entity) {
        if (!getColliding() || entity.isDead()) {
            return CollisionMode.CANCEL;
        }
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(entity);
        if (member != null) {
            if (trainCollision == CollisionMode.LINK) {
                if (member.getGroup().getProperties().trainCollision == CollisionMode.LINK) {
                    return CollisionMode.LINK;
                } else {
                    return CollisionMode.CANCEL;
                }
            } else {
                return trainCollision;
            }
        } else if (entity instanceof Player) {
            GameMode playerGameMode = ((Player) entity).getGameMode();
            if (playerGameMode == GameMode.SPECTATOR) {
                return CollisionMode.CANCEL;
            }
            if (TCConfig.collisionIgnoreOwners && playerCollision != CollisionMode.DEFAULT) {
                if (TCConfig.collisionIgnoreGlobalOwners) {
                    if (CartProperties.hasGlobalOwnership((Player) entity)) {
                        return CollisionMode.DEFAULT;
                    }
                }
                if (hasOwnership((Player) entity)) {
                    return CollisionMode.DEFAULT;
                }
            }
            // Don't kill or damage players in creative
            if (playerGameMode == GameMode.CREATIVE) {
                if (playerCollision == CollisionMode.KILL || playerCollision == CollisionMode.KILLNODROPS || playerCollision == CollisionMode.DAMAGE
                        || playerCollision == CollisionMode.DAMAGENODROPS) {
                    return CollisionMode.PUSH;
                }
            }
            return playerCollision;
        } else {
            for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
                CollisionMode collisionMode = collisionModes.get(collisionConfigObject);
                if (collisionMode != null && collisionConfigObject.isMobType(entity)) {
                    return collisionMode;
                }
            }
            return miscCollision;
        }
    }

    public String getTrainName() {
        return trainname;
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
        return suffocation;
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
        return allowManualMovement;
    }

    /**
     * Sets whether minecart passengers can manually move the train they are in
     *
     * @param allow state to set to
     */
    public void setManualMovementAllowed(boolean allow) {
        allowManualMovement = allow;
    }

    /**
     * Gets a list of tickets that can be used for entering this train
     * 
     * @return tickets
     */
    public List<String> getTickets() {
        return Collections.unmodifiableList(tickets);
    }

    /**
     * Adds a new ticket that can be used for entering this train.
     * 
     * @param ticketName to add
     */
    public void addTicket(String ticketName) {
        tickets.add(ticketName);
    }

    /**
     * Revokes a ticket from being used for entering this train.
     * 
     * @param ticketName to remove
     */
    public void removeTicket(String ticketName) {
        tickets.remove(ticketName);
    }

    public void clearTickets() {
        tickets.clear();
    }

    public SignSkipOptions getSkipOptions() {
        return skipOptions;
    }

    public void setSkipOptions(SignSkipOptions options) {
        skipOptions.filter = options.filter;
        skipOptions.ignoreCtr = options.ignoreCtr;
        skipOptions.skipCtr = options.skipCtr;
    }

    public String getKillMessage() {
        return killMessage;
    }

    public void setKillMessage(String killMessage) {
        this.killMessage = killMessage;
    }

    public boolean isTrainRenamed() {
        return !trainname.startsWith("train") || !ParseUtil.isNumeric(trainname.substring(5));
    }

    public boolean isLoaded() {
        return hasHolder();
    }

    public boolean matchName(String expression) {
        return Util.matchText(getTrainName(), expression);
    }

    public boolean matchName(String[] expressionElements, boolean firstAny, boolean lastAny) {
        return Util.matchText(getTrainName(), expressionElements, firstAny, lastAny);
    }

    @Override
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
        tryUpdate();
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
        MinecartGroup g = getHolder();
        if (g != null)
            g.onPropertiesChanged();
    }

    @Override
    public boolean parseSet(String key, String arg) {
        TrainPropertiesStore.markForAutosave();
        if (key.equals("exitoffset")) {
            Vector vec = Util.parseVector(arg, null);
            if (vec != null) {
                if (vec.length() > TCConfig.maxEjectDistance) {
                    vec.normalize().multiply(TCConfig.maxEjectDistance);
                }
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
        } else if (key.equals("killmessage")) {
            killMessage = arg;
        } else if (key.equals("sound") || key.equals("minecartsound")) {
            soundEnabled = ParseUtil.parseBool(arg);
        } else if (key.equals("playercollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null)
                return false;
            playerCollision = mode;
        } else if (key.equals("misccollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null)
                return false;
            miscCollision = mode;
        } else if (key.equals("traincollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null)
                return false;
            trainCollision = mode;
        } else if (key.equals("blockcollision")) {
            CollisionMode mode = CollisionMode.parse(arg);
            if (mode == null)
                return false;
            blockCollision = mode;
        } else if (key.equals("collisiondamage")) {
            setCollisionDamage(Double.parseDouble(arg));
        } else if (key.equals("suffocation")) {
            suffocation = ParseUtil.parseBool(arg);
        } else if (setCollisionMode(key, arg)) {
            return true;
        } else if (LogicUtil.contains(key, "collision", "collide")) {
            setColliding(ParseUtil.parseBool(arg));
        } else if (LogicUtil.contains(key, "linking", "link")) {
            setLinking(ParseUtil.parseBool(arg));
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
        } else if (LogicUtil.contains(key, "setdefault", "default")) {
            this.setDefault(arg);
        } else if (key.equals("pushplayers")) {
            playerCollision = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
        } else if (key.equals("pushmisc")) {
            miscCollision = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
        } else if (LogicUtil.contains(key, "push", "pushing")) {
            CollisionMode mode = CollisionMode.fromPushing(ParseUtil.parseBool(arg));
            playerCollision = miscCollision = mode;
            updateAllCollisionProperties(mode);
        } else if (LogicUtil.contains(key, "speedlimit", "maxspeed")) {
            setSpeedLimit(ParseUtil.parseDouble(arg, 0.4));
        } else if (LogicUtil.contains(key, "allowmanual", "manualmove", "manual")) {
            allowManualMovement = ParseUtil.parseBool(arg);
        } else if (LogicUtil.contains(key, "keepcloaded", "loadchunks", "keeploaded")) {
            setKeepChunksLoaded(ParseUtil.parseBool(arg));
        } else if (key.equals("addtag")) {
            addTags(arg);
        } else if (key.equals("settag")) {
            setTags(arg);
        } else if (key.equals("destination")) {
            setDestination(arg);
        } else if (key.equals("remtag") || key.equals("removetag")) {
            removeTags(arg);
        } else if (LogicUtil.contains(key, "name", "rename", "setname")) {
            setName(generateTrainName(arg));
        } else if (LogicUtil.contains(key, "dname", "displayname", "setdisplayname", "setdname")) {
            setDisplayName(arg);
        } else if (LogicUtil.contains(key, "mobenter", "mobsenter")) {
            updateAllCollisionProperties(CollisionMode.fromEntering(ParseUtil.parseBool(arg)));
        } else if (key.equals("waitdistance")) {
            setWaitDistance(ParseUtil.parseDouble(arg, waitDistance));
        } else if (key.equals("playerenter")) {
            setPlayersEnter(ParseUtil.parseBool(arg));
        } else if (key.equals("playerexit")) {
            setPlayersExit(ParseUtil.parseBool(arg));
        } else if (LogicUtil.contains(key, "invincible", "godmode")) {
            setInvincible(ParseUtil.parseBool(arg));
        } else if (LogicUtil.contains(key, "banking")) {
            String[] args = arg.split(" ");
            setBankingStrength(ParseUtil.parseDouble(args[0], bankingStrength));
            if (args.length >= 2) {
                setBankingSmoothness(ParseUtil.parseDouble(args[1], bankingSmoothness));
            }
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
            setSpawnItemDrops(ParseUtil.parseBool(arg));
        } else if (key.equals("addticket")) {
            addTicket(arg);
        } else if (key.equals("remticket")) {
            removeTicket(arg);
        } else if (key.equals("setticket")) {
            clearTickets();
            if (arg.length() > 0) {
                addTicket(arg);
            }
        } else if (key.equals("clrticket")) {
            clearTickets();
        } else if (key.equals("drivesound")) {
            for (CartProperties cprop : this) {
                cprop.setDriveSound(arg);
            }
        } else {
            return false;
        }
        tryUpdate();
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
            return updateCollisionProperties(mobType, mode);
        }
        if (key.endsWith("collision") && key.length() > 9) {
            String mobType = key.substring(0, key.length() - 9);
            CollisionMode mode = CollisionMode.parse(value);
            return updateCollisionProperties(mobType, mode);
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
            trainCollision = CollisionMode.LINK;
        } else if (trainCollision == CollisionMode.LINK) {
            trainCollision = CollisionMode.DEFAULT;
        }
    }

    public boolean updateCollisionProperties(String mobType, CollisionMode mode) {
        if (mode == null) {
            return false;
        }
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            if (mobType.equals(collisionConfigObject.getMobType()) || mobType.equals(collisionConfigObject.getPluralMobType())) {
                collisionModes.put(collisionConfigObject, mode);
                return true;
            }
        }
        return false;
    }

    public void updateAllCollisionProperties(CollisionMode mode) {
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            if (collisionConfigObject.isAddToConfigFile() == true) {
                collisionModes.put(collisionConfigObject, mode);
            }
        }
    }

    @Override
    public void load(ConfigurationNode node) {
        setDisplayName(node.get("displayName", displayName));
        allowPlayerTake = node.get("allowPlayerTake", allowPlayerTake);
        collision = node.get("trainCollision", collision);
        setCollisionDamage(node.get("collisionDamage", getCollisionDamage()));
        soundEnabled = node.get("soundEnabled", soundEnabled);

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
                collisionModes.put(collisionConfigObject, mode == CollisionMode.SKIP ? null : mode);
            }
            playerCollision = node.get("collision.players", playerCollision);
            miscCollision = node.get("collision.misc", miscCollision);
            trainCollision = node.get("collision.train", trainCollision);
            blockCollision = node.get("collision.block", blockCollision);
        }
        speedLimit = MathUtil.clamp(node.get("speedLimit", speedLimit), 0, TCConfig.maxVelocity);
        requirePoweredMinecart = node.get("requirePoweredMinecart", requirePoweredMinecart);
        setKeepChunksLoaded(node.get("keepChunksLoaded", keepChunksLoaded));
        allowManualMovement = node.get("allowManualMovement", allowManualMovement);
        waitDistance = node.get("waitDistance", waitDistance);
        suffocation = node.get("suffocation", suffocation);
        killMessage = node.get("killMessage", killMessage);
        for (String ticket : node.getList("tickets", String.class)) {
            tickets.add(ticket);
        }
        if (node.isNode("skipOptions")) {
            skipOptions.load(node.getNode("skipOptions"));
        }

        // Only used when loading defaults from tickets, or when 'destination: ' is set
        // in DefTrProps.yml
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

        // These properties are purely saved so they are written correctly when saving
        // defaults
        // There are not meant to be read anywhere, because these exist as part of
        // minecart metadata
        blockTypes = node.get("blockTypes", "");
        blockOffset = node.get("blockOffset", SignActionBlockChanger.BLOCK_OFFSET_NONE);

        // Banking
        if (node.isNode("banking")) {
            ConfigurationNode banking = node.getNode("banking");
            bankingStrength = banking.get("strength", bankingStrength);
            bankingSmoothness = banking.get("smoothness", bankingSmoothness);
        }

        // Apply block types / block height to the actual minecart, if set
        if (!blockTypes.isEmpty() || blockOffset != SignActionBlockChanger.BLOCK_OFFSET_NONE) {
            MinecartGroup group = getHolder();
            if (group != null) {
                if (blockTypes.isEmpty()) {
                    SignActionBlockChanger.setBlocks(group, new ItemParser[0], blockOffset);
                } else {
                    SignActionBlockChanger.setBlocks(group, blockTypes, blockOffset);
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
        soundEnabled = source.soundEnabled;
        displayName = source.displayName;
        collision = source.collision;
        slowDownOptions.clear();
        slowDownOptions.addAll(source.slowDownOptions);
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            collisionModes.put(collisionConfigObject, source.getCollisionMode(collisionConfigObject));
        }
        playerCollision = source.playerCollision;
        miscCollision = source.miscCollision;
        trainCollision = source.trainCollision;
        blockCollision = source.blockCollision;
        setCollisionDamage(source.collisionDamage);
        speedLimit = MathUtil.clamp(source.speedLimit, 0, 20);
        requirePoweredMinecart = source.requirePoweredMinecart;
        setKeepChunksLoaded(source.keepChunksLoaded);
        allowManualMovement = source.allowManualMovement;
        tickets = new ArrayList<>(source.tickets);
        setSkipOptions(source.skipOptions);
        blockTypes = source.blockTypes;
        blockOffset = source.blockOffset;
        waitDistance = source.waitDistance;
        bankingStrength = source.bankingStrength;
        bankingSmoothness = source.bankingSmoothness;
        suffocation = source.suffocation;
        killMessage = source.killMessage;
    }

    public CollisionMode getCollisionMode(CollisionConfig collisionConfigObject) {
        return collisionModes.get(collisionConfigObject);
    }

    @Override
    public void saveAsDefault(ConfigurationNode node) {
        node.set("soundEnabled", soundEnabled);
        node.set("displayName", displayName);
        node.set("allowPlayerTake", allowPlayerTake);
        node.set("requirePoweredMinecart", requirePoweredMinecart);
        node.set("trainCollision", collision);
        node.set("collisionDamage", getCollisionDamage());
        node.set("keepChunksLoaded", keepChunksLoaded);
        node.set("speedLimit", speedLimit);
        node.set("waitDistance", waitDistance);
        node.set("suffocation", suffocation);
        node.set("killMessage", killMessage);

        ConfigurationNode banking = node.getNode("banking");
        banking.set("strength", bankingStrength);
        banking.set("smoothness", bankingSmoothness);

        if (isSlowingDownAll()) {
            node.set("slowDown", true);
        } else if (isSlowingDownNone()) {
            node.set("slowDown", false);
        } else {
            ConfigurationNode slowdownNode = node.getNode("slowDown");
            for (SlowdownMode mode : SlowdownMode.values()) {
                slowdownNode.set(mode.getKey(), this.isSlowingDown(mode));
            }
        }

        node.set("allowManualMovement", allowManualMovement);
        node.set("tickets", StringUtil.EMPTY_ARRAY);
        node.set("collision.players", playerCollision);
        node.set("collision.misc", miscCollision);
        node.set("collision.train", trainCollision);
        node.set("collision.block", blockCollision);
        node.set("blockTypes", (blockTypes == null) ? "" : blockTypes);
        node.set("blockOffset", (blockOffset == SignActionBlockChanger.BLOCK_OFFSET_NONE) ? "unset" : blockOffset);
        for (CartProperties prop : this) {
            prop.saveAsDefault(node);
            break;
        }
    }

    @Override
    public void save(ConfigurationNode node) {
        node.set("displayName", displayName.equals(trainname) ? null : displayName);
        node.set("soundEnabled", soundEnabled ? null : false);
        node.set("allowPlayerTake", allowPlayerTake ? true : null);
        node.set("requirePoweredMinecart", requirePoweredMinecart ? true : null);
        node.set("trainCollision", collision ? null : false);
        node.set("collisionDamage", getCollisionDamage());
        node.set("keepChunksLoaded", keepChunksLoaded ? true : null);
        node.set("speedLimit", speedLimit != 0.4 ? speedLimit : null);
        node.set("waitDistance", (waitDistance > 0) ? waitDistance : null);
        node.set("suffocation", suffocation ? null : false);
        node.set("killMessage", killMessage.isEmpty() ? null : killMessage);

        if (bankingStrength != 0.0 || bankingSmoothness != 10.0) {
            ConfigurationNode banking = node.getNode("banking");
            banking.set("strength", bankingStrength != 0.0 ? bankingStrength : null);
            banking.set("smoothness", bankingSmoothness != 10.0 ? bankingSmoothness : null);
        } else {
            node.remove("banking");
        }

        if (isSlowingDownAll()) {
            node.remove("slowDown");
        } else if (isSlowingDownNone()) {
            node.set("slowDown", false);
        } else {
            ConfigurationNode slowdownNode = node.getNode("slowDown");
            for (SlowdownMode mode : SlowdownMode.values()) {
                slowdownNode.set(mode.getKey(), this.isSlowingDown(mode));
            }
        }

        node.set("allowManualMovement", allowManualMovement ? true : null);
        node.set("tickets", LogicUtil.toArray(tickets, String.class));
        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
            CollisionMode value = collisionModes.get(collisionConfigObject);
            if (collisionConfigObject.isAddToConfigFile() || value != null) {
                node.set("collision." + collisionConfigObject.getMobType(), value != null ? value : CollisionMode.DEFAULT);
            }
        }
        if (playerCollision != CollisionMode.DEFAULT) {
            node.set("collision.players", playerCollision);
        }
        if (miscCollision != CollisionMode.DEFAULT) {
            node.set("collision.misc", miscCollision);
        }
        if (trainCollision != CollisionMode.LINK) {
            node.set("collision.train", trainCollision);
        }
        if (blockCollision != CollisionMode.DEFAULT) {
            node.set("collision.block", blockCollision);
        }
        if (!isEmpty()) {
            ConfigurationNode carts = node.getNode("carts");
            for (CartProperties prop : this) {
                ConfigurationNode cart = carts.getNode(prop.getUUID().toString());
                prop.save(cart);
                if (cart.getKeys().isEmpty())
                    carts.remove(cart.getName());
            }
        }

        if (skipOptions.isActive()) {
            skipOptions.save(node.getNode("skipOptions"));
        } else if (node.contains("skipOptions")) {
            node.remove("skipOptions");
        }
    }

    public double getCollisionDamage() {
        return collisionDamage;
    }

    public void setCollisionDamage(double collisionDamage) {
        this.collisionDamage = collisionDamage;
    }
}
