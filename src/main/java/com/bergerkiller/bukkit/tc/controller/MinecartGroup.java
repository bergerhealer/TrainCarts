package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.bases.mutable.VectorAbstract;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.inventory.MergedInventory;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet.LongIterator;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerGroup;
import com.bergerkiller.bukkit.tc.controller.components.AnimationController;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerGroup;
import com.bergerkiller.bukkit.tc.controller.components.SignTrackerGroup;
import com.bergerkiller.bukkit.tc.controller.components.ObstacleTracker;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRailWalker;
import com.bergerkiller.bukkit.tc.controller.components.RailTrackerGroup;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberChest;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberFurnace;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatusProvider;
import com.bergerkiller.bukkit.tc.events.*;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.SaveLockOrientationMode;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.CartLockOrientation;
import com.bergerkiller.bukkit.tc.properties.standard.type.SlowdownMode;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.offline.train.OfflineGroup;
import com.bergerkiller.bukkit.tc.offline.train.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.ChunkArea;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bergerkiller.generated.net.minecraft.world.level.chunk.ChunkHandle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MinecartGroup extends MinecartGroupStore implements IPropertiesHolder, AnimationController, TrainStatusProvider, TrainCarts.Provider {
    private static final long serialVersionUID = 3;
    private static final LongHashSet chunksBuffer = new LongHashSet(50);
    private final TrainCarts traincarts;
    protected final ChunkArea chunkArea = new ChunkArea();
    private boolean chunkAreaValid = false;
    private final SignTrackerGroup signTracker = new SignTrackerGroup(this);
    private final RailTrackerGroup railTracker = new RailTrackerGroup(this);
    private final ActionTrackerGroup actionTracker = new ActionTrackerGroup(this);
    private final ObstacleTracker obstacleTracker = new ObstacleTracker(this);
    private final AttachmentControllerGroup attachmentController = new AttachmentControllerGroup(this);
    protected long lastSync = Long.MIN_VALUE;
    private TrainProperties prop = null;
    private boolean breakPhysics = false;
    private int teleportImmunityTick = 0;
    private double updateSpeedFactor = 1.0;
    private int updateStepCount = 1;
    private int updateStepNr = 1;
    private boolean unloaded = false;

    protected MinecartGroup(TrainCarts traincarts) {
        this.traincarts = traincarts;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    @Override
    public TrainProperties getProperties() {
        if (this.prop == null) {
            if (this.isUnloaded()) {
                throw new IllegalStateException("Group is unloaded");
            }
            this.prop = TrainPropertiesStore.create();
            for (MinecartMember<?> member : this) {
                this.prop.add(member.getProperties());
            }
            TrainPropertiesStore.bindGroupToProperties(this.prop, this);
        }
        return this.prop;
    }

    public void setProperties(TrainProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("Can not set properties to null");
        }
        if (this.isUnloaded()) {
            throw new IllegalStateException("Group is unloaded");
        }
        if (this.prop == properties) {
            return;
        }
        if (this.prop != null) {
            TrainPropertiesStore.remove(this.prop.getTrainName());
            TrainPropertiesStore.unbindGroupFromProperties(this.prop, this);
        }
        this.prop = properties;
        TrainPropertiesStore.bindGroupToProperties(this.prop, this);
    }

    /**
     * Same as {@link #saveConfig()} but excludes ownership (claim) information and includes
     * information about used models. This method is used when executing the
     * /train export command.
     *
     * @return configuration useful for exporting off-server
     */
    public ConfigurationNode exportConfig() {
        ConfigurationNode exportedConfig = saveConfig();
        exportedConfig.remove("claims");
        exportedConfig.set("usedModels", getAttachments().getUsedModelsAsExport());
        return exportedConfig;
    }

    /**
     * Saves the properties of this train, preserving information such the order of the carts
     * and the orientation of each cart. Owner information is stripped.<br>
     * <br>
     * If for one or more carts the orientation was locked at some point, and the train is
     * flipped according to a majority of those carts, the produced properties will have
     * the carts flipped. As such, there is no guarantee the configuration will have the same
     * order lists as the minecarts of this group.
     *
     * @return configuration useful for saving as a train
     */
    public ConfigurationNode saveConfig() {
        return saveConfig(SaveLockOrientationMode.AUTOMATIC);
    }

    /**
     * Saves the properties of this train, preserving information such the order of the carts
     * and the orientation of each cart.<br>
     * <br>
     * A save lock mode can be set. This will make the train remember the flipped state of the
     * carts when saving, so that future saves will remember the orientation the train had.
     * With this method this locked mode can also be turned off again, by specifying
     * {@link SaveLockOrientationMode#DISABLED}.<br>
     * <br>
     * If for one or more carts the orientation was locked at some point, and the train is
     * flipped according to a majority of those carts, the produced properties will have
     * the carts flipped. As such, there is no guarantee the configuration will have the same
     * order lists as the minecarts of this group.
     *
     * @param setSaveLockMode Overrides whether the orientation of the train should be locked or not.
     *                        Does nothing if set to AUTOMATIC.
     * @return configuration useful for saving as a train
     */
    public ConfigurationNode saveConfig(SaveLockOrientationMode setSaveLockMode) {
        // Save train properties getConfig() to a new configuration node copy
        // Omit cart details, overwrite with the member configurations
        ConfigurationNode savedConfig = this.getProperties().saveToConfig().clone();
        savedConfig.remove("carts");

        // Save carts
        List<ConfigurationNode> carts = this.stream()
                .map(MinecartMember::saveConfig)
                .collect(Collectors.toCollection(ArrayList::new));

        if (setSaveLockMode == SaveLockOrientationMode.DISABLED) {
            // If lock orientation mode is DISABLED, strip all carts from locked orientation information
            for (ConfigurationNode cart : carts) {
                StandardProperties.LOCK_ORIENTATION_FLIPPED.writeToConfig(cart, Optional.empty());
            }

        } else if (setSaveLockMode == SaveLockOrientationMode.ENABLED_OVERRIDE) {
            // Enables the lock orientation mode. Saves current flipped state as the locked orientation.
            for (ConfigurationNode cart : carts) {
                StandardProperties.LOCK_ORIENTATION_FLIPPED.writeToConfig(cart,
                        Optional.of(CartLockOrientation.locked(cart.get("flipped", false))));
            }

        } else if (setSaveLockMode == SaveLockOrientationMode.ENABLED ||
                ( setSaveLockMode == SaveLockOrientationMode.AUTOMATIC &&
                  this.isSavedTrainOrientationLocked() )
        ) {
            // If mode AUTOMATIC, detect whether or not any of the carts use locking or not
            // if mode ENABLED, always use locking
            // In here we handle the locking enabled logic

            // Some carts will have both a 'flipped' and 'flippedAtSaved'
            // Use these to decide whether the train orientation must be flipped around
            int trainFlippedCounter = 0;
            for (ConfigurationNode cart : carts) {
                CartLockOrientation ori = StandardProperties.LOCK_ORIENTATION_FLIPPED.readFromConfig(cart)
                        .orElse(CartLockOrientation.NONE);
                if (ori != CartLockOrientation.NONE) {
                    if (ori.isFlipped() == cart.get("flipped", false)) {
                        trainFlippedCounter--;
                    } else {
                        trainFlippedCounter++;
                    }
                }
            }

            // If counter is positive, then almost surely the carts must all be reversed
            if (trainFlippedCounter > 0) {
                // Invert 'flipped' state of all carts, then reverse the list
                // We also modify the 'flippedAtSave' inadvertently, but that's fine as we overwrite
                // this later with the flipped state. It's a waste of cpu time, but oh well.
                carts.forEach(StandardProperties::reverseSavedCart);
                Collections.reverse(carts);
            }

            // Ensure that for all carts the lock orientation is set to the flipped state
            for (ConfigurationNode cart : carts) {
                StandardProperties.LOCK_ORIENTATION_FLIPPED.writeToConfig(cart,
                        Optional.of(CartLockOrientation.locked(cart.get("flipped", false))));
            }
        }

        savedConfig.setNodeList("carts", carts);
        return savedConfig;
    }

    /**
     * Gets whether the orientation of the train is locked. This means that when the train
     * is saved as a saved train, it will always face the same way. This can be changed
     * using {@link #saveConfig(SaveLockOrientationMode)} and specifying a mode to use.
     *
     * @return True if the orientation of the train is locked and will not change when saving
     */
    public boolean isSavedTrainOrientationLocked() {
        for (MinecartMember<?> member : this) {
            if (member.getProperties().get(StandardProperties.LOCK_ORIENTATION_FLIPPED) != CartLockOrientation.NONE) {
                return true;
            }
        }
        return false;
    }

    public SignTrackerGroup getSignTracker() {
        return this.signTracker;
    }

    /**
     * Gets the Action Tracker that keeps track of the actions of this Group
     *
     * @return action tracker
     */
    public ActionTrackerGroup getActions() {
        return this.actionTracker;
    }

    /**
     * Gets the Rail Tracker that keeps track of the rails this train occupies.
     * 
     * @return rail tracker
     */
    public RailTrackerGroup getRailTracker() {
        return this.railTracker;
    }

    /**
     * Gets the attachment controller for this group. This controller manages
     * the (synchronized) updates of all carts of the train.
     *
     * @return group attachment controller
     */
    public AttachmentControllerGroup getAttachments() {
        return this.attachmentController;
    }

    public MinecartMember<?> head(int index) {
        return this.get(index);
    }

    public MinecartMember<?> head() {
        return this.head(0);
    }

    public MinecartMember<?> tail(int index) {
        return this.get(this.size() - 1 - index);
    }

    public MinecartMember<?> tail() {
        return this.tail(0);
    }

    public MinecartMember<?> middle() {
        return this.get((int) Math.floor((double) size() / 2));
    }

    public Iterator<MinecartMember<?>> iterator() {
        final Iterator<MinecartMember<?>> listIter = super.iterator();
        return new Iterator<MinecartMember<?>>() {
            @Override
            public boolean hasNext() {
                return listIter.hasNext();
            }

            @Override
            public MinecartMember<?> next() {
                try {
                    return listIter.next();
                } catch (ConcurrentModificationException ex) {
                    throw new MemberMissingException();
                }
            }

            @Override
            public void remove() {
                listIter.remove();
            }
        };
    }

    public MinecartMember<?>[] toArray() {
        return super.toArray(new MinecartMember<?>[0]);
    }

    public boolean connect(MinecartMember<?> contained, MinecartMember<?> with) {
        if (this.size() <= 1) {
            this.add(with);
        } else if (this.head() == contained && this.canConnect(with, 0)) {
            this.add(0, with);
        } else if (this.tail() == contained && this.canConnect(with, this.size() - 1)) {
            this.add(with);
        } else {
            return false;
        }
        return true;
    }

    public boolean containsIndex(int index) {
        return !this.isEmpty() && (index >= 0 && index < this.size());
    }

    @Override
    public World getWorld() {
        return isEmpty() ? null : get(0).getWorld();
    }

    public int size(EntityType carttype) {
        int rval = 0;
        for (MinecartMember<?> mm : this) {
            if (mm.getEntity().getType() == carttype) {
                rval++;
            }
        }
        return rval;
    }

    public boolean isValid() {
        return !this.isEmpty() && (this.size() == 1 || !this.getProperties().isPoweredMinecartRequired() || this.size(EntityType.MINECART_FURNACE) > 0);
    }

    @Override
    public void add(int index, MinecartMember<?> member) {
        if (member.isUnloaded()) {
            throw new IllegalArgumentException("Can not add unloaded members to groups");
        }
        super.add(index, member);
        this.fireMemberAddEvent(member);
        this.onMemberAdded(member);
    }

    @Override
    public boolean add(MinecartMember<?> member) {
        if (member.isUnloaded()) {
            throw new IllegalArgumentException("Can not add unloaded members to groups");
        }
        super.add(member);
        this.fireMemberAddEvent(member);
        this.onMemberAdded(member);
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends MinecartMember<?>> members) {
        super.addAll(index, members);
        MinecartMember<?>[] memberArr = members.toArray(new MinecartMember<?>[0]);
        for (MinecartMember<?> m : memberArr) {
            if (m.isUnloaded()) {
                throw new IllegalArgumentException("Can not add unloaded members to groups");
            }
            this.fireMemberAddEvent(m);
        }
        for (MinecartMember<?> member : memberArr) {
            this.onMemberAdded(member);
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends MinecartMember<?>> members) {
        super.addAll(members);
        MinecartMember<?>[] memberArr = members.toArray(new MinecartMember<?>[0]);
        for (MinecartMember<?> m : memberArr) {
            if (m.isUnloaded()) {
                throw new IllegalArgumentException("Can not add unloaded members to groups");
            }
            this.fireMemberAddEvent(m);
        }
        for (MinecartMember<?> member : memberArr) {
            this.onMemberAdded(member);
        }
        return true;
    }

    /**
     * Removes a member without splitting the train or causing link effects
     *
     * @param member to remove
     * @return True if removed, False if not
     */
    public boolean removeSilent(MinecartMember<?> member) {
        int index = this.indexOf(member);
        if (index == -1) {
            return false;
        }
        this.removeMember(index);
        if (this.isEmpty()) {
            this.remove();
        }
        return true;
    }

    @Override
    public boolean remove(Object o) {
        int index = this.indexOf(o);
        return index != -1 && this.remove(index) != null;
    }

    @Override
    public MinecartMember<?> remove(int index) {
        MinecartMember<?> removed = this.removeMember(index);
        if (this.isEmpty()) {
            //Remove empty group as a result
            this.remove();
        } else {
            //Split the train at the index
            if (TCConfig.playHissWhenCartRemoved) {
                removed.playLinkEffect();
            }
            this.split(index);
        }
        return removed;
    }

    private MinecartMember<?> removeMember(int index) {
        this.chunkAreaValid = false; // Probably unneeded but keeping it just in case
        notifyPhysicsChange();
        MinecartMember<?> member = super.get(index);
        MemberRemoveEvent.call(member);
        super.remove(index);
        getActions().removeActions(member);
        onMemberRemoved(member);
        member.group = null;
        return member;
    }

    /**
     * Called whenever the composition of this group changes. That is, members are added
     * or removed, or this group is removed in its entirety.
     */
    private void onCompositionChanged() {
        this.chunkAreaValid = false;
        this.attachmentController.notifyGroupCompositionChanged();
    }

    // Called before addMember to fire the MemberAddEvent
    // Sets the group to this group before adding to avoid problems
    // when .getGroup() is called on the member to query the previous group.
    private void fireMemberAddEvent(MinecartMember<?> member) {
        boolean wasGroupNull = false;
        if (member.group == null) {
            member.group = this;
            wasGroupNull = true;
        }
        CommonUtil.callEvent(new MemberAddEvent(member, this));
        if (wasGroupNull && member.group == this) {
            member.group = null;
        }
    }

    private void onMemberAdded(MinecartMember<?> member) {
        onCompositionChanged();
        notifyPhysicsChange();
        member.setGroup(this);
        getSignTracker().updatePosition();
        getProperties().add(member.getProperties());
    }

    private void onMemberRemoved(MinecartMember<?> member) {
        onCompositionChanged();
        getSignTracker().onMemberRemoved(member);
        getProperties().remove(member.getProperties());
        getRailTracker().removeMemberRails(member);

        /* Timings: cacheRailMembers  (Train Physics, Rail Tracker, Cache) */
        {
            RailLookup.removeMemberFromAll(member);
        }
    }

    /**
     * Splits this train, the index is the first cart for the new group<br><br>
     * <p>
     * For example, this Group has a total cart count of 5<br>
     * If you then split at index 2, it will result in:<br>
     * - This group becomes a group of 2 carts<br>
     * - A new group of 3 carts is created
     */
    public MinecartGroup split(int at) {
        Util.checkMainThread("MinecartGroup::split()");
        if (at <= 0) return this;
        if (at >= this.size()) return null;

        // Remove carts split off and create a new group using them
        MinecartGroup gnew;
        {
            List<MinecartMember<?>> splitMembers = new ArrayList<>();
            int count = this.size();
            for (int i = at; i < count; i++) {
                splitMembers.add(this.removeMember(this.size() - 1));
            }
            gnew = MinecartGroupStore.createSplitFrom(this.getProperties(),
                    splitMembers.toArray(new MinecartMember[0]));
        }

        //Remove this train if now empty
        if (!this.isValid()) {
            this.remove();
        } else {
            // Refresh chunk area after the split, and fire property changed (switcher)
            // The new train was already created, with the chunks kept loaded
            // by it marked, so it's safe to remove chunks not part of the
            // remaining train without chunks unloading.
            this.onGroupCreated();
        }
        //Remove if empty or not allowed, else add
        return gnew;
    }

    @Override
    public void clear() {
        // Stop tracking
        unregisterFromServer();

        final TrainProperties properties = this.getProperties();
        for (MinecartMember<?> mm : this.toArray()) {
            properties.remove(mm.getProperties());
            if (mm.getEntity().isRemoved()) {
                mm.onDie(true);
            } else {
                // Unassign member from previous group
                mm.group = null;

                // Create and assign a new group to this member with the properties already created earlier
                mm.group = MinecartGroupStore.createSplitFrom(properties, mm);
            }
        }
        super.clear();
    }

    public void remove() {
        Util.checkMainThread("MinecartGroup::remove()");
        if (!groups.remove(this)) {
            return; // Already removed
        }

        GroupRemoveEvent.call(this);
        this.clear();
        if (this.prop != null) {
            TrainPropertiesStore.remove(this.prop.getTrainName());
            TrainPropertiesStore.unbindGroupFromProperties(this.prop, this);
            this.prop = null;
        }
    }

    public void destroy() {
        List<MinecartMember<?>> copy = new ArrayList<MinecartMember<?>>(this);
        for (MinecartMember<?> mm : copy) {
            mm.getEntity().remove();
        }
        this.remove();
    }

    /**
     * Whether this group has been unloaded. This means members of this group can no longer be addressed
     * and methods and properties of this group are unreliable.
     * 
     * @return True if unloaded
     */
    public boolean isUnloaded() {
        return this.unloaded;
    }

    /**
     * Unloads this group, saving it in offline storage for later reloading. Does nothing if already unloaded.
     */
    public void unload() {
        // If already unloaded, do nothing
        if (this.unloaded) {
            return;
        }

        // Protect.
        Util.checkMainThread("MinecartGroup::unload()");
        this.unloaded = true;

        try {
            // Undo partial-unloading before calling the event
            for (MinecartMember<?> member : this) {
                member.group = this;
                member.setUnloaded(false);
            }

            // Event
            GroupUnloadEvent.call(this);

            // Save current state to an offline representation
            OfflineGroup offlineGroup = OfflineGroupManager.saveGroup(this);

            // Stop tracking
            unregisterFromServer();

            // Store the group offline
            if (offlineGroup != null) {
                traincarts.getOfflineGroups().storeGroup(offlineGroup);
            }

            // Unload. CancelLocationChange must be false otherwise saving position desync occurs!
            this.stop(false);
        } finally {
            groups.remove(this);
        }

        for (MinecartMember<?> member : this) {
            member.group = null;
            member.unloadedLastPlayerTakable = this.getProperties().isPlayerTakeable();
            member.setUnloaded(true);

            // We must correct position here, because it will no longer be ticked!
            member.getEntity().doPostTick();
        }

        // Clear group members and disable this group further
        super.clear();

        if (this.prop != null) {
            TrainPropertiesStore.unbindGroupFromProperties(this.prop, this);
        }
        this.prop = null;
    }

    /**
     * Un-registers this train from the server. This disables presence in active
     * detector regions and rail lookup cache.
     */
    private void unregisterFromServer() {
        // Unload in detector regions
        getSignTracker().unload();

        // Remove from member-by-rail cache
        getRailTracker().unload();

        // Just for good measure
        getActions().clear();

        // Release chunks previously kept loaded by this train
        this.chunkArea.reset();
        this.chunkAreaValid = false;
        this.onCompositionChanged();
    }

    /**
     * Visually respawns this minecart to avoid teleportation smoothing
     */
    public void respawn() {
        for (MinecartMember<?> mm : this) {
            mm.respawn();
        }
    }

    public void playLinkEffect() {
        for (MinecartMember<?> mm : this) {
            mm.playLinkEffect();
        }
    }

    public void stop() {
        this.stop(false);
    }

    public void stop(boolean cancelLocationChange) {
        for (MinecartMember<?> m : this) {
            m.stop(cancelLocationChange);
        }
    }

    public void limitSpeed() {
        for (MinecartMember<?> mm : this) {
            mm.limitSpeed();
        }
    }

    public void eject() {
        for (MinecartMember<?> mm : this) mm.eject();
    }

    /**
     * A simple version of teleport where the inertia of the train is maintained
     */
    public void teleportAndGo(Block start, BlockFace direction) {
        double force = this.getAverageForce();
        this.teleport(start, direction);
        this.stop();
        this.getActions().clear();
        if (Math.abs(force) > 0.01) {
            this.tail().getActions().addActionLaunch(direction, 1.0, force);
        }
    }

    public void teleport(Block start, BlockFace direction) {
        Location[] locations = new Location[this.size()];
        TrackWalkingPoint walker = new TrackWalkingPoint(start, direction);
        walker.skipFirst();
        for (int i = 0; i < locations.length; i++) {
            boolean canMove;
            if (i == 0) {
                canMove = walker.move(0.0);
            } else {
                canMove = walker.move(get(i - 1).getPreferredDistance(get(i)));
            }
            if (canMove) {
                locations[i] = walker.state.positionLocation();
            } else if (i > 0) {
                locations[i] = locations[i - 1].clone();
            } else {
                return; // Failed!
            }
        }
        this.teleport(locations, true);
    }

    public void teleport(Location[] locations) {
        this.teleport(locations, false);
    }

    public void teleport(Location[] locations, boolean reversed) {
        if (LogicUtil.nullOrEmpty(locations) || locations.length != this.size()) {
            return;
        }
        this.teleportImmunityTick = 10;
        this.getSignTracker().clear();
        this.getSignTracker().updatePosition();
        this.breakPhysics();

        // De-spawn the carts at the original position for all current viewers, and signal
        // the train is teleporting. This prevents any new viewers being added until later.
        for (MinecartMember<?> member : this) {
            member.getAttachments().startTeleport();
        }

        // Invert location orientations where carts had orientation flipped
        // Do all this BEFORE teleporting, or these orientation flipped checks will fail
        // as they use the cart's relative positions to figure this out.
        locations = locations.clone();
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).isOrientationInverted()) {
                int locIndx = reversed ? (locations.length - i - 1) : i;
                locations[locIndx] = Util.invertRotation(locations[locIndx].clone());
            }
        }

        if (reversed) {
            for (int i = 0; i < locations.length; i++) {
                teleportMember(this.get(i), locations[locations.length - i - 1]);
            }
        } else {
            for (int i = 0; i < locations.length; i++) {
                teleportMember(this.get(i), locations[i]);
            }
        }
        this.updateDirection();
        this.updateChunkInformation(!this.canUnload(), false);
        this.updateWheels();
        this.getSignTracker().updatePosition();

        // Respawn the cart. If teleported to a different world, re-creates the entire
        // attachment tree.
        for (MinecartMember<?> member : this) {
            member.getAttachments().finishTeleport();
        }
    }

    private void teleportMember(MinecartMember<?> member, Location location) {
        member.getWheels().startTeleport();
        member.getEntity().teleport(location);
        member.getOrientation();
    }

    /**
     * Flips the orientation of this entire Train, making the front cart the back cart
     * and also flipping the orientation of all individual carts. This will actively
     * teleport carts around to make this happen.
     */
    public void flipOrientation() {
        // Shortcuts
        if (this.isEmpty()) {
            return;
        } else if (this.size() == 1) {
            this.head().flipOrientation();
            return;
        }

        // The amount of extra distance to move forwards/backwards
        double shiftDistance = 0.5 * ((double) this.tail().getEntity().getWidth() - (double) this.head().getEntity().getWidth());

        // Compute distances between members on the rails before
        // This is a linked list of members from front to back
        // As we spawn from back to front, this flips the members around
        FlippedMember currentMember = null;
        boolean areAllCartsReachable = true;
        for (int i = this.size() - 1; i >= 1; i--) {
            double distance = this.head(i).calculateRailDistanceToMemberAhead(this.head(i - 1));
            if (Double.isNaN(distance)) {
                // As fallback use the ideal distance between the carts
                distance = this.head(i).getPreferredDistance(this.head(i - 1));
                areAllCartsReachable = false;
            }
            FlippedMember next = new FlippedMember(this.head(i), distance);
            next.next = currentMember;
            currentMember = next;
        }
        {
            FlippedMember next = new FlippedMember(this.head(), Math.max(0.0, -shiftDistance));
            next.next = currentMember;
            currentMember = next;
        }
        final FlippedMember rootMember = currentMember;

        // If not all carts are reachable to one-another, some carts might be on different track
        // or derailed entirely. This complicates things. In that case, we can't really use
        // the train rail information to compute distances, and must do the entire thing
        // using a rail walking point.
        RailState current;
        if (areAllCartsReachable) {
            TrackedRailWalker walker = this.tail().getRailTracker().getTrackedRailWalker();

            // If shift distance is negative, then the first spawn position is behind the start position
            // For this we must move a small distance backwards. For as most as we can, we use the train
            // rail information.
            if (shiftDistance > 0.0) {
                walker.invertMotion();
                shiftDistance -= walker.move(shiftDistance);
                walker.invertMotion();
            }

            if (shiftDistance > 0.0) {
                // Can't walk the full distance backwards using the walker alone
                // Will need to use a track walking point to walk the rest of the distance
                walker.invertMotion();
                walker.state().initEnterDirection();
                TrackWalkingPoint p = new TrackWalkingPoint(walker.state());
                p.skipFirst();
                p.move(shiftDistance);
                current = p.state;
                current.position().invertMotion();
                current.initEnterDirection();
            } else {
                // Walk to the current member until we can no longer find them
                while (true) {
                    currentMember.distanceRemaining -= walker.move(currentMember.distanceRemaining);
                    if (currentMember.distanceRemaining <= 0.0) {
                        currentMember.flippedState = walker.state().clone();
                        currentMember.flippedState.initEnterDirection();
                        currentMember = currentMember.next;
                        if (currentMember == null) {
                            break;
                        }
                    } else {
                        // End of track. Rest must be done with a rail walking point
                        break;
                    }
                }

                current = walker.state();
                current.initEnterDirection();
            }
        } else {
            TrackedRail currentRail = null;
            for (int i = size() - 1; i >= 0; i--) {
                MinecartMember<?> member = this.get(i);
                if (!member.isDerailed()) {
                    currentRail = member.getRailTracker().getRail();
                    break;
                }
            }
            if (currentRail == null) {
                // None of the minecarts are on rails. Got to abort.
                flipOrientationFallback();
                return;
            }

            current = currentRail.state.clone();
            current.initEnterDirection();
        }

        // Move the remaining steps using a track walking point
        if (currentMember != null) {
            TrackWalkingPoint p = new TrackWalkingPoint(current);
            p.skipFirst();
            do {
                if (!p.move(currentMember.distanceRemaining)) {
                    // End of the rails encountered. Can't do a proper re-spawning.
                    flipOrientationFallback();
                    return;
                }
                currentMember.flippedState = p.state.clone();
                currentMember = currentMember.next;
            } while (currentMember != null);
        }

        applyFlippedStates(rootMember);
    }

    /**
     * Flips orientation of the train by teleporting the front cart to the
     * back cart and flipping each cart's orientation. This doesn't take into
     * account the relative distances of the carts.
     */
    private void flipOrientationFallback() {
        FlippedMember current = null;
        for (int i = 0; i < size(); i++) {
            MinecartMember<?> member = head(i);
            MinecartMember<?> swapped = tail(i);
            if (member == swapped) {
                continue;
            }

            FlippedMember flipped = new FlippedMember(member, 0.0);
            flipped.flippedState = swapped.getRailTracker().getState().clone();
            flipped.next = current;
            current = flipped;
        }

        applyFlippedStates(current);
    }

    private void applyFlippedStates(FlippedMember rootMember) {
        // Teleport all the members
        for (FlippedMember currentMember = rootMember; currentMember != null; currentMember = currentMember.next) {
            currentMember.apply();
        }

        // Refresh direction and wheel information to make everything correct
        this.updateDirection();
        this.updateWheels();
        this.getAttachments().syncRespawn();
    }
    
    private static class FlippedMember {
        public final MinecartMember<?> member;
        public final boolean orientationInverted;
        public final double velocity;
        public double distanceRemaining;
        public RailState flippedState;
        public FlippedMember next;

        public FlippedMember(MinecartMember<?> member, double distanceFromBehind) {
            this.member = member;
            this.orientationInverted = member.isOrientationInverted();
            this.velocity = member.getForce();
            this.distanceRemaining = distanceFromBehind;
            this.flippedState = null;
            this.next = null;
        }

        public void apply() {
            Location position = flippedState.position().toLocation(flippedState.railBlock());
            Vector velocityVec = flippedState.motionVector().clone().multiply(velocity);
            Vector upVector = flippedState.position().getWheelOrientation().upVector();
            Vector forwardVector = flippedState.motionVector();
            if (!orientationInverted) { // Note: inverse, as we WANT to flip orientation
                forwardVector.multiply(-1.0);
            }
            Quaternion orientation = Quaternion.fromLookDirection(forwardVector, upVector);

            this.member.getEntity().setPosition(position.getX(), position.getY(), position.getZ());
            this.member.getEntity().setVelocity(velocityVec);
            this.member.setOrientation(orientation);
            this.member.getWheels().startTeleport();
        }
    }

    /**
     * Gets whether this Minecart and the passenger has immunity as a result of teleportation
     *
     * @return True if it is immune, False if not
     */
    public boolean isTeleportImmune() {
        return this.teleportImmunityTick > 0;
    }

    public void shareForce() {
        double f = this.getAverageForce();
        for (MinecartMember<?> m : this) {
            m.setForwardForce(f);
        }
    }

    public void setForwardForce(double force) {
        for (MinecartMember<?> mm : this) {
            final double currvel = mm.getForce();
            if (currvel <= 0.01 || Math.abs(force) < 0.01) {
                mm.setForwardForce(force);
            } else {
                mm.getEntity().vel.multiply(force / currvel);
            }
        }

        /*
        final double currvel = this.head().getForce();
        if (currvel <= 0.01 || Math.abs(force) < 0.01) {
            for (MinecartMember<?> mm : this) {
                mm.setForwardForce(force);
            }
        } else {
            final double f = force / currvel;
            for (MinecartMember<?> mm : this) {
                mm.getEntity().vel.multiply(f);
            }
        }
        */

    }

    @Override
    public List<String> getAnimationNames() {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else if (this.size() == 1) {
            return this.get(0).getAnimationNames();
        } else {
            return Collections.unmodifiableList(this.stream()
                    .flatMap(m -> m.getAnimationNames().stream())
                    .distinct()
                    .collect(Collectors.toList()));
        }
    }

    @Override
    public Set<String> getAnimationScenes(String animationName) {
        if (this.isEmpty()) {
            return Collections.emptySet();
        } else if (this.size() == 1) {
            return this.get(0).getAnimationScenes(animationName);
        } else {
            return Collections.unmodifiableSet(this.stream()
                    .flatMap(m -> m.getAnimationScenes(animationName).stream())
                    .collect(Collectors.toSet()));
        }
    }

    /**
     * Plays an animation by name for this train
     * 
     * @param name of the animation
     * @return True if an animation was started for one or more minecarts in this train
     */
    @Override
    public boolean playNamedAnimation(String name) {
        return AnimationController.super.playNamedAnimation(name);
    }

    /**
     * Plays an animation using the animation options specified for this train
     * 
     * @param options for the animation
     * @return True if an animation was started for one or more minecarts in this train
     */
    @Override
    public boolean playNamedAnimation(AnimationOptions options) {
        boolean success = false;
        for (MinecartMember<?> member : this) {
            success |= member.playNamedAnimation(options);
        }
        return success;
    }

    @Override
    public boolean playNamedAnimationFor(int[] targetPath, AnimationOptions options) {
        boolean success = false;
        for (MinecartMember<?> member : this) {
            success |= member.playNamedAnimationFor(targetPath, options);
        }
        return success;
    }

    @Override
    public boolean playAnimationFor(int[] targetPath, Animation animation) {
        boolean success = false;
        for (MinecartMember<?> member : this) {
            success |= member.playAnimationFor(targetPath, animation);
        }
        return success;
    }

    public boolean canConnect(MinecartMember<?> mm, int at) {
        if (this.size() == 1) return true;
        if (this.size() == 0) return false;
        CommonMinecart<?> connectedEnd;
        CommonMinecart<?> otherEnd;
        if (at == 0) {
            // Compare the head
            if (!this.head().isNearOf(mm)) {
                return false;
            }
            connectedEnd = this.head().getEntity();
            otherEnd = this.tail().getEntity();
        } else if (at == this.size() - 1) {
            //compare the tail
            if (!this.tail().isNearOf(mm)) {
                return false;
            }
            connectedEnd = this.tail().getEntity();
            otherEnd = this.head().getEntity();
        } else {
            return false;
        }
        // Verify connected end is closer than the opposite end of this Train
        // This ensures that no wrongful connections are made in curves
        return connectedEnd.loc.distanceSquared(mm.getEntity()) < otherEnd.loc.distanceSquared(mm.getEntity());
    }

    /**
     * Refreshes rail information when physics occurred since the last time {@link #refreshRailTrackerIfChanged()}
     * was called. Physics can be notified using {@link #notifyPhysicsChange()}. In addition,
     * this method checks whether the physics position of the train was changed since the last time
     * this method was called.
     */
    private void refreshRailTrackerIfChanged() {
        // Go by all the Minecarts and check whether the position since last time has changed
        for (MinecartMember<?> member : this) {
            hasPhysicsChanges |= member.railDetectPositionChange();
        }

        // If changed, reset and refresh rails
        if (hasPhysicsChanges) {
            hasPhysicsChanges = false;
            this.getRailTracker().refresh();
        }
    }

    public void updateDirection() {
        /* Timings: updateDirection  (Train Physics) */
        {
            if (this.size() == 1) {
                this.refreshRailTrackerIfChanged();
                this.head().updateDirection();
            } else if (this.size() > 1) {
                int reverseCtr = 0;
                while (true) {
                    this.refreshRailTrackerIfChanged();

                    // Update direction of individual carts
                    for (MinecartMember<?> member : this) {
                        member.updateDirection();
                    }

                    // Handle train reversing (with maximum 2 attempts)
                    if (reverseCtr++ == 2) {
                        break;
                    }
                    double fforce = 0;
                    for (MinecartMember<?> m : this) {
                        // Use rail tracker instead of recalculating for improved performance
                        // fforce += m.getForwardForce();

                        VectorAbstract vel = m.getEntity().vel;
                        fforce += m.getRailTracker().getState().position().motDot(vel.getX(), vel.getY(), vel.getZ());
                    }
                    if (fforce >= 0) {
                        break;
                    } else {
                        reverseDataStructures();
                        notifyPhysicsChange();
                    }
                }
            }
        }
    }

    public void reverse() {
        // Reverse current movement direction for each individual cart
        for (MinecartMember<?> mm : this) {
            mm.reverseDirection();
        }

        // Reverses train data structures so head becomes tail
        reverseDataStructures();

        // With velocity at 0, updateDirection() would (falsely) assume there are no changes
        // Just to make sure we always recalculate the rails, force an update
        notifyPhysicsChange();

        // Must be re-calculated since this alters the path the train takes
        this.updateDirection();
    }

    // Reverses all structures that store information sorted from head to tail
    // This is done when the direction of the train changes
    private void reverseDataStructures() {
        Collections.reverse(this);
        this.getRailTracker().reverseRailData();
    }

    // Refresh wheel position information, important to do it AFTER updateDirection()
    private void updateWheels() {

        for (MinecartMember<?> member : this) {
            /* Timings: updateWheels  (Train Physics, Wheel Tracker) */
            {
                member.getWheels().update();
            }
        }
    }

    /**
     * Gets the average forward motion of all Members of this train. This takes into
     * account the forward orientation of the members, so this method could return
     * a negative number when the train reverses.
     *
     * @return average (forward) force
     */
    public double getAverageForce() {
        if (this.isEmpty()) {
            return 0;
        }
        if (this.size() == 1) {
            return this.get(0).getForce();
        }
        //Get the average forward force of all carts
        double force = 0;
        for (MinecartMember<?> m : this) {
            force += m.getForwardForce();
        }
        return force / (double) size();
    }

    public List<Material> getTypes() {
        ArrayList<Material> types = new ArrayList<>(this.size());
        for (MinecartMember<?> mm : this) {
            types.add(mm.getEntity().getCombinedItem());
        }
        return types;
    }

    public boolean hasPassenger() {
        for (MinecartMember<?> mm : this) {
            if (mm.getEntity().hasPassenger()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasFuel() {
        for (MinecartMember<?> mm : this) {
            if (mm instanceof MinecartMemberFurnace && ((MinecartMemberFurnace) mm).getEntity().hasFuel()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasItems() {
        for (MinecartMember<?> mm : this) {
            if (mm instanceof MinecartMemberChest && ((MinecartMemberChest) mm).hasItems()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasItem(ItemParser item) {
        for (MinecartMember<?> mm : this) {
            if (mm instanceof MinecartMemberChest && ((MinecartMemberChest) mm).hasItem(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean isMoving() {
        return !this.isEmpty() && this.head().isMoving();
    }

    /**
     * Gets whether this train is moving, or waiting on a station or other sign mechanic.
     *
     * @return Moving or waiting
     */
    public boolean isMovingOrWaiting() {
        return isMoving() || this.getActions().isWaitAction();
    }

    /**
     * Checks if this Minecart Group can unload, or if chunks are kept loaded instead<br>
     * The keepChunksLoaded property is read, as well the moving state if configured<br>
     * If a player is inside the train, it will keep the chunks loaded as well
     *
     * @return True if it can unload, False if it keeps chunks loaded
     */
    public boolean canUnload() {
        if (this.getProperties().isKeepingChunksLoaded()) {
            if (!TCConfig.keepChunksLoadedOnlyWhenMoving || this.isMovingOrWaiting()) {
                return false;
            }
        }
        for (MinecartMember<?> member : this) {
            if (member.getEntity() != null && member.getEntity().hasPlayerPassenger()) {
                return false;
            }
        }
        return !this.isTeleportImmune();
    }

    public boolean isRemoved() {
        return !groups.contains(this);
    }

    /**
     * Gets an inventory view of all the items in the carts of this train
     *
     * @return cart inventory view
     */
    public Inventory getInventory() {
        Inventory[] source = this.stream()
                .map(MinecartMember::getEntity)
                .map(CommonEntity::getEntity)
                .filter(e -> e instanceof InventoryHolder)
                .map(e -> ((InventoryHolder) e).getInventory())
                .toArray(Inventory[]::new);
        return new MergedInventory(source);
    }

    /**
     * Gets an inventory view of all players inside all carts of this train
     *
     * @return player inventory view
     */
    public Inventory getPlayerInventory() {
        Inventory[] source = this.stream().flatMap(m -> m.getEntity().getPlayerPassengers().stream())
                     .map(Player::getInventory)
                     .toArray(Inventory[]::new);
        return new MergedInventory(source);
    }

    public void keepChunksLoaded(boolean keepLoaded) {
        for (ChunkArea.OwnedChunk chunk : this.chunkArea.getAll()) {
            chunk.keepLoaded(keepLoaded);
        }
    }

    /**
     * Gets the chunk area around this train. This is the area kept loaded if chunks are kept loaded,
     * or the chunks that when unloaded, will cause the train to unload if not.
     * 
     * @return chunk area
     */
    public ChunkArea getChunkArea() {
        return this.chunkArea;
    }

    public boolean isInChunk(World world, long chunkLongCoord) {
        if (this.getWorld() != world) {
            return false;
        }

        if (this.chunkAreaValid) {
            return this.chunkArea.containsChunk(chunkLongCoord);
        } else {
            // Slow calculation as a fallback when the chunkArea is outdated
            int center_chunkX = MathUtil.longHashMsw(chunkLongCoord);
            int center_chunkZ = MathUtil.longHashLsw(chunkLongCoord);
            LongIterator chunkIter = this.loadChunksBuffer().longIterator();
            while (chunkIter.hasNext()) {
                long chunk = chunkIter.next();
                if (Math.abs(MathUtil.longHashMsw(chunk) - center_chunkX) <= 2 &&
                    Math.abs(MathUtil.longHashLsw(chunk) - center_chunkZ) <= 2)
                {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void onPropertiesChanged() {
        this.getSignTracker().update();
        for (MinecartMember<?> member : this.toArray()) {
            member.onPropertiesChanged();
        }
    }

    /**
     * Gets the maximum amount of ticks a member of this group has lived
     *
     * @return maximum amount of lived ticks
     */
    public int getTicksLived() {
        int ticksLived = 0;
        for (MinecartMember<?> member : this) {
            ticksLived = Math.max(ticksLived, member.getEntity().getTicksLived());
        }
        return ticksLived;
    }

    /**
     * Gets the speed factor that is applied to all velocity and movement updates in the current update.<br>
     * <br>
     * <b>Explanation:</b><br>
     * When a train moves faster than 0.4 blocks/tick, the update is split into several update steps per tick.
     * This prevents nasty derailing and makes sure that block-by-block motion can still occur. In a single tick
     * the train moves 5 blocks, which is done by doing 8 or so actual update steps. The update speed factor
     * specifies the multiplier to apply to speeds for the current update.<br>
     * <br>
     * When moving 0.4 b/t and under, this value will always be 1.0 (one update). Above it, it will be
     * set to an increasingly small number 1/stepcount. Outside of the physics function, the factor will always be 1.0.<br>
     * <br>
     * <b>When to use</b><br>
     * This factor should only be used when applying an absolute velocity. For example, when
     * a launcher sign uses a certain desired velocity, this speed factor must be used to make sure it is correctly applied.
     * Say we want a speed of "2.4", and the update is split in 6 (f=0.1666), we should apply <i>2.4*0.1666=0.4</i>. When all
     * updates finish, the velocities are corrected and will be set to the 2.4 that was requested.<br>
     * <br>
     * However, when a velocity is taken over from inside the physics loop, this factor should <b>not</b> be used.
     * For example, if you want to do <i>velocity = velocity * 0.95</i> the original velocity is already factored,
     * and no update speed factor should be applied again.
     * 
     * @return Update speed factor
     */
    public double getUpdateSpeedFactor() {
        return this.updateSpeedFactor;
    }

    /**
     * Gets the total number of physics updates performed per tick. See also the information
     * of {@link #getUpdateSpeedFactor()}.
     * 
     * @return update step count (normally 1)
     */
    public int getUpdateStepCount() {
        return this.updateStepCount;
    }

    /**
     * Gets whether the currently executing updates are the first update step.
     * See {@link #getUpdateSpeedFactor()} for an explanation of what this means.
     * 
     * @return True if this is the first update step
     */
    public boolean isFirstUpdateStep() {
        return this.updateStepNr == 1;
    }

    /**
     * Gets whether the currently executing updates are the final update step.
     * See {@link #getUpdateSpeedFactor()} for an explanation of what this means.
     * 
     * @return True if this is the last update step
     */
    public boolean isLastUpdateStep() {
        return this.updateStepNr == this.updateStepCount;
    }

    /**
     * Aborts any physics routines going on in this tick
     */
    public void breakPhysics() {
        this.breakPhysics = true;
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        List<TrainStatus> info = new ArrayList<>(3);
        info.addAll(this.getActions().getStatusInfo());
        for (MinecartMember<?> member : this) {
            info.addAll(member.getActions().getStatusInfo());
        }
        info.addAll(this.obstacleTracker.getStatusInfo());

        for (MinecartMember<?> member : this) {
            if (member.isDerailed()) {
                info.add(new TrainStatus.Derailed());
                break;
            }
        }

        if (this.getProperties().getSpeedLimit() <= 1e-5) {
            info.add(new TrainStatus.WaitingZeroSpeedLimit());
        } else if (this.head().getEntity().getMaxSpeed() <= 1e-5) {
            info.add(new TrainStatus.NotMovingSpeedLimited());
        } else {
            double speed = this.head().getRealSpeedLimited();
            if (speed <= 1e-5) {
                info.add(new TrainStatus.NotMoving());
            } else {
                info.add(new TrainStatus.Moving(speed));
            }
        }

        if (this.getProperties().isKeepingChunksLoaded()) {
            info.add(new TrainStatus.KeepingChunksLoaded());
        }

        return info;
    }

    /*
     * These two overrides ensure that sets use this MinecartGroup properly
     * Without it, the AbstractList versions were used, which don't apply here
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object other) {
        return other == this;
    }

    /**
     * Gets the Member that is on the rails block position specified
     *
     * @param position to get the member at
     * @return member at the position, or null if not found
     */
    public MinecartMember<?> getAt(IntVector3 position) {
        return getRailTracker().getMemberFromRails(position);
    }

    private boolean doConnectionCheck() {
        // Check all railed minecarts follow the same tracks
        // This is important for switcher/junction split logic
        for (int i = 0; i < this.size() - 1; i++) {
            // (!get(i + 1).isFollowingOnTrack(get(i))) {
            if (get(i).getRailTracker().isTrainSplit()) {
                // Undo stepcount based velocity modifications
                for (int j = i + 1; j < this.size(); j++) {
                    this.get(j).getEntity().vel.divide(this.updateSpeedFactor);
                }
                // Split
                MinecartGroup gnew = this.split(i + 1);
                if (gnew != null) {
                    //what time do we want to prevent them from colliding too soon?
                    //needs to travel 2 blocks in the meantime
                    int time = (int) MathUtil.clamp(2 / gnew.head().getRealSpeed(), 20, 40);
                    for (MinecartMember<?> mm1 : gnew) {
                        for (MinecartMember<?> mm2 : this) {
                            mm1.ignoreCollision(mm2.getEntity().getEntity(), time);
                        }
                    }
                }
                return false;
            }
        }

        // Check that no minecart is too far apart from another
        for (int i = 0; i < this.size() - 1; i++) {
            MinecartMember<?> m1 = get(i);
            MinecartMember<?> m2 = get(i + 1);
            if (!m1.isDerailed() && !m2.isDerailed()) {
                continue; // ignore railed minecarts that can still reach each other
            }
            if (m1.getEntity().loc.distance(m2.getEntity().loc) >= m1.getMaximumDistance(m2)) {
                this.split(i + 1);
                return false;
            }
        }
        return true;
    }

    // loads the static chunksBuffer with the chunk coordinates of the minecarts of this group
    private LongHashSet loadChunksBuffer() {
        chunksBuffer.clear();
        for (MinecartMember<?> mm : this) {
            chunksBuffer.add(mm.getEntity().loc.x.chunk(), mm.getEntity().loc.z.chunk());
        }
        return chunksBuffer;
    }

    /**
     * Called after the group has been spawned or created for the first time
     */
    public void onGroupCreated() {
        this.onPropertiesChanged();

        // When keep chunks loaded is active, make sure to enforce that right away
        // If we do it next tick a chunk could unload before we can do so
        // Do not do this for normal unloading logic, as that may unload the train in there (this should be later)
        if (this.getProperties().isKeepingChunksLoaded()) {
            this.updateChunkInformation(true, false);
        }
    }

    /**
     * Refreshes the chunks this train is occupying. When the train keeps chunks loaded,
     * makes sure to load the new chunks and allow old chunks to unload again.
     * 
     * @param keepChunksLoaded Whether to keep chunks loaded, or track train unloading
     * @param isRemoving When true, the train is in process of being removed, and no logic
     *                   besides refreshing the chunk area should be performed.
     */
    private void updateChunkInformation(boolean keepChunksLoaded, boolean isRemoving) {
        /* Timings: updateChunkInformation  (Train Physics) */
        {
            // Refresh the chunk area tracker using this information
            this.chunkArea.refresh(this.getWorld(), this.loadChunksBuffer());
            this.chunkAreaValid = true;

            // Keep-chunks-loaded or automatic unloading when moving into unloaded chunks
            if (keepChunksLoaded) {
                // Load chunks we entered for asynchronous loading
                for (ChunkArea.OwnedChunk chunk : this.chunkArea.getAdded()) {
                    chunk.keepLoaded(true);
                }

                // Load chunks closeby right away and guarantee they are loaded at all times
                for (ChunkArea.OwnedChunk chunk : this.chunkArea.getAll()) {
                    if (chunk.getDistance() <= 1 && chunk.getPreviousDistance() > 1) {
                        chunk.loadChunk();
                    }
                }
            } else if (!isRemoving) {
                // Check all newly added chunks whether the chunk is unloaded
                // When such a chunk is found, unload this train
                for (ChunkArea.OwnedChunk chunk : this.chunkArea.getAdded()) {
                    if (!chunk.isLoaded()) {
                        this.unload();
                        throw new GroupUnloadedException();
                    }
                }
            }
        }
    }

    public void logCartInfo(String header) {
        StringBuilder msg = new StringBuilder(size() * 7 + 10);
        msg.append(header);
        for (MinecartMember<?> member : this) {
            msg.append(" [");
            msg.append(member.getDirection());
            msg.append(" - ").append(member.getEntity().vel);
            msg.append("]");
        }
        traincarts.log(Level.INFO, msg.toString());
    }

    /**
     * Gets the obstacle avoidance tracker. This tracker searches the rails up ahead of this
     * train to find other trains, mutex zones, or other types of obstacles. It then maintains
     * a speed limit to avoid colliding with it.
     *
     * @return Obstacle tracker
     */
    public ObstacleTracker getObstacleTracker() {
        return this.obstacleTracker;
    }

    /**
     * Gets the distance and speed of all obstacles up ahead on the tracks.
     * This can be another train, or a mutex zone that blocks further movement.
     * 
     * @param distance The distance in blocks to check for obstacles
     * @param trains Whether to include other trains up ahead in the results
     * @param railObstacles Whether to include rail obstacles, like mutex zones, in the results
     * @return obstacle found within this distance, null if there is none
     */
    public List<ObstacleTracker.Obstacle> findObstaclesAhead(double distance, boolean trains, boolean railObstacles) {
        return this.obstacleTracker.findObstaclesAhead(distance, trains, railObstacles, 0.0);
    }

    /**
     * Checks whether there are any obstacles up ahead on the tracks.
     * This can be another train, or a mutex zone that blocks further movement.
     * 
     * @param distance to look for trains ahead
     * @param trains Whether to include other trains up ahead in the results
     * @param railObstacles Whether to include rail obstacles, like mutex zones, in the results
     * @return True if a matched obstacle is up ahead, False if not
     * @see #findObstaclesAhead(double, boolean, boolean)
     */
    public boolean isObstacleAhead(double distance, boolean trains, boolean railObstacles) {
        return !this.findObstaclesAhead(distance, trains, railObstacles).isEmpty();
    }

    /**
     * Checks whether there are any obstacles up ahead on the tracks.
     * If there are, returns the maximum speed the train can have to avoid the
     * closest obstacle at the current wait deceleration rate.
     * If a wait distance is configured, then it will also check for other trains.
     *
     * @param distance Distance to look for trains up ahead
     * @return Found obstacle speed limit. Can be {@link ObstacleTracker.ObstacleSpeedLimit#NONE}
     */
    public ObstacleTracker.ObstacleSpeedLimit findObstacleSpeedLimit(double distance) {
        return findObstacleSpeedLimit(distance, getProperties().getWaitDeceleration());
    }

    /**
     * Checks whether there are any obstacles up ahead on the tracks.
     * If there are, returns the maximum speed the train can have to avoid the
     * closest obstacle at the specified deceleration rate.
     * If a wait distance is configured, then it will also check for other trains.
     *
     * @param distance Distance to look for trains up ahead
     * @param deceleration Maximum rate of deceleration in blocks/tick^2
     * @return Found obstacle speed limit. Can be {@link ObstacleTracker.ObstacleSpeedLimit#NONE}
     */
    public ObstacleTracker.ObstacleSpeedLimit findObstacleSpeedLimit(double distance, double deceleration) {
        double waitDistance = getProperties().getWaitDistance();
        List<ObstacleTracker.Obstacle> obstacles = this.obstacleTracker.findObstaclesAhead(distance, waitDistance > 0.0, true, waitDistance);
        return ObstacleTracker.minimumSpeedLimit(obstacles, deceleration);
    }

    private void tickActions() {
        /* Timings: tickActions  (Train Physics) */
        {
            this.getActions().doTick();
        }
    }

    protected void doPhysics(TrainCarts plugin) {
        // NOP if unloaded
        // This should never happen, so remove the group as a precaution
        // Somehow it got re-added again.
        if (this.isUnloaded()) {
            groups.remove(this);
            return;
        }

        // Remove minecarts from this group that don't actually belong to this group
        // This is a fallback/workaround for a reported resource bug where fake trains are created
        {
            for (int i = 0; i < this.size(); i++) {
                MinecartMember<?> member = super.get(i);
                if (member.getEntity() == null) {
                    // Controller is detached. It's completely invalid!
                    // We handle unloading ourselves, so the minecart should be considered gone :(
                    CartPropertiesStore.remove(member.getProperties().getUUID());
                    onMemberRemoved(member);
                    super.remove(i--);
                    continue;
                }
                if (member.group != this) {
                    // Assigned to a different group. Quietly remove it. You saw nothing!
                    onMemberRemoved(member);
                    super.remove(i--);
                    continue;
                }
            }
        }

        // Remove minecarts from this group that are dead
        // This operation can completely alter the structure of the group iterated over
        // For this reason, this logic is inside a loop
        boolean finishedRemoving;
        do {
            finishedRemoving = true;
            for (int i = 0; i < this.size(); i++) {
                MinecartMember<?> member = super.get(i);
                if (member.getEntity().isRemoved()) {
                    this.remove(i);
                    finishedRemoving = false;
                    break;
                }
            }
        } while (!finishedRemoving);

        // Remove empty trains entirely before doing any physics at all
        if (super.isEmpty()) {
            this.remove();
            return;
        }

        if (this.canUnload()) {
            for (MinecartMember<?> m : this) {
                if (m.isUnloaded()) {
                    this.unload();
                    return;
                }
            }
        } else {
            for (MinecartMember<?> m : this) {
                m.setUnloaded(false);
            }
        }

        // If physics disabled this tick, cut off here.
        if (!plugin.getTrainUpdateController().isTicking()) {
            return;
        }

        try {
            double totalforce = this.getAverageForce();
            double speedlimit = this.getProperties().getSpeedLimit();
            double realtimeFactor = this.getProperties().hasRealtimePhysics()
                    ? plugin.getTrainUpdateController().getRealtimeFactor() : 1.0;

            if ((realtimeFactor*totalforce) > 0.4 && (realtimeFactor*speedlimit) > 0.4) {
                this.updateStepCount = (int) Math.ceil((realtimeFactor*speedlimit) / 0.4);
                this.updateSpeedFactor = realtimeFactor / (double) this.updateStepCount;
            } else {
                this.updateStepCount = 1;
                this.updateSpeedFactor = realtimeFactor;
            }

            /* Timings: Train Physics */
            {
                // Perform the physics changes
                if (this.updateStepCount > 1) {
                    for (MinecartMember<?> mm : this) {
                        mm.getEntity().vel.multiply(this.updateSpeedFactor);
                    }
                }
                for (int i = 1; i <= this.updateStepCount; i++) {
                    this.updateStepNr = i;
                    while (!this.doPhysics_step());
                }
            }

            // Restore velocity / max speed to what is exposed outside the physics function
            // Use the speed factor for this, since the max speed may have been changed during the physics update
            // This can happen with, for example, the use of waitDistance
            for (MinecartMember<?> mm : this) {
                mm.getEntity().vel.divide(this.updateSpeedFactor);

                double newMaxSpeed = mm.getEntity().getMaxSpeed() / this.updateSpeedFactor;
                newMaxSpeed = Math.min(newMaxSpeed, this.getProperties().getSpeedLimit());
                mm.getEntity().setMaxSpeed(newMaxSpeed);
            }

            this.updateSpeedFactor = 1.0;

            // Server bugfix: prevents an old Minecart duplicate staying behind inside a chunk when saved
            // This issue has been resolved on Paper, see https://github.com/PaperMC/Paper/issues/1223
            for (MinecartMember<?> mm : this) {
                CommonEntity<?> entity = mm.getEntity();
                if (entity.isInLoadedChunk()) {
                    int cx = entity.getChunkX();
                    int cz = entity.getChunkZ();
                    if (cx != entity.loc.x.chunk() || cz != entity.loc.z.chunk()) {
                        ChunkHandle.fromBukkit(entity.getWorld().getChunkAt(cx, cz)).markDirty();
                    }
                }
            }

        } catch (GroupUnloadedException ex) {
            //this group is gone
        } catch (Throwable t) {
            final TrainProperties p = getProperties();
            plugin.log(Level.SEVERE, "Failed to perform physics on train '" + p.getTrainName() + "' at " + p.getLocation() + ":");
            plugin.handle(t);
        }
    }

    private boolean doPhysics_step() throws GroupUnloadedException {
        this.breakPhysics = false;
        try {
            // Prevent index exceptions: remove if not a train
            if (this.isEmpty()) {
                this.remove();
                throw new GroupUnloadedException();
            }

            // Validate members and set max speed
            // We must limit it to 0.4, otherwise derailment can occur when the
            // minecart speeds up inside the physics update function
            {
                double speedLimitClamped = Math.min(this.getProperties().getSpeedLimit() * this.updateSpeedFactor, 0.4);
                for (MinecartMember<?> mm : this) {
                    mm.checkMissing();
                    mm.getEntity().setMaxSpeed(speedLimitClamped);
                }
            }

            // Set up a valid network controller if needed
            for (MinecartMember<?> member : this) {
                member.getAttachments().fixNetworkController();
            }

            // Update some per-tick stuff
            if (this.teleportImmunityTick > 0) {
                this.teleportImmunityTick--;
            }

            // Update direction and executed actions prior to updates
            this.updateDirection();
            this.getSignTracker().refresh();

            // Perform block change Minecart logic, also take care of potential new block changes
            for (MinecartMember<?> member : this) {
                member.checkMissing();
                if (member.hasBlockChanged() | member.forcedBlockUpdate.clear()) {
                    // Perform events and logic - validate along the way
                    MemberBlockChangeEvent.call(member, member.getLastBlock(), member.getBlock());
                    member.checkMissing();
                    member.onBlockChange(member.getLastBlock(), member.getBlock());
                    this.getSignTracker().updatePosition();
                    member.checkMissing();
                }
            }
            this.getSignTracker().refresh();

            this.updateDirection();
            if (!this.doConnectionCheck()) {
                return true; //false;
            }

            this.tickActions();

            this.updateDirection();

            // Perform velocity updates
            for (MinecartMember<?> member : this) {
                member.onPhysicsStart();
            }

            // Perform velocity updates
            /* Timings: onPhysicsPreMove  (Train Physics) */
            {
                for (MinecartMember<?> member : this) {
                    member.onPhysicsPreMove();
                }
            }

            // Stop if all dead
            if (this.isEmpty()) {
                return false;
            }

            // Add the gravity effects right before moving the Minecart
            // This changes velocity slightly so that minecarts go downslope or fall down
            // It is important to do it here, so that gravity is taken into account
            // when sliding over the ground. Doing this in the wrong spot will make the minecart 'hover'.
            if (this.getProperties().isSlowingDown(SlowdownMode.GRAVITY)) {
                double usf_sq = this.getProperties().getGravity() * this.getUpdateSpeedFactor() * this.getUpdateSpeedFactor();
                for (MinecartMember<?> member : this) {
                    if (member.isUnloaded()) continue; // not loaded - no physics occur
                    if (member.isMovementControlled()) continue; // launched by station, launcher, etc.

                    // Find segment of the rails path the Minecart is on
                    member.getRailLogic().onGravity(member, usf_sq);
                }
            }

            // Direction can change as a result of gravity
            this.updateDirection();

            // Pre-movement rail updates. Must be done after gravity, otherwise
            // trains slide down unpowered powered rails. We can't move gravity, because
            // the rail logic pre-move logic must occur first (snap to rails) for proper calculations.
            for (MinecartMember<?> member : this) {
                member.getRailTracker().getRailType().onPreMove(member);
            }

            // Rail Type preMove can change it (i.e. powered rail)
            this.updateDirection();

            // Share forward force between all the Minecarts when size > 1
            double forwardMovingSpeed;
            if (this.size() > 1) {
                //Get the average forwarding force of all carts
                forwardMovingSpeed = this.getAverageForce();

                //Perform forward force or not? First check if we are not messing up...
                boolean performUpdate = true;
                for (int i = 0; i < this.size() - 1; i++) {
                    if (get(i).getRailTracker().isTrainSplit()) {
                        performUpdate = false;
                        break;
                    }
                }

                if (performUpdate) {
                    //update force
                    for (MinecartMember<?> m : this) {
                        m.setForwardForce(forwardMovingSpeed);
                    }
                }
            } else {
                forwardMovingSpeed = this.head().getForce();
            }

            // If a wait distance is set, check for trains ahead of the track and wait for those
            // We do the waiting by setting the max speed of the train (NOT speed limit!) to match that train's speed
            // It is important speed of this train is updated before doing these checks.
            /* Timings: getSpeedAhead  (Train Physics) */
            {
                if (isFirstUpdateStep()) {
                    this.obstacleTracker.update(forwardMovingSpeed / getUpdateSpeedFactor());
                }
                double limitedSpeed = this.obstacleTracker.getSpeedLimit();

                // If not blocked, re-apply the speed limit as this may have changed during this tick!
                if (limitedSpeed == Double.MAX_VALUE) {
                    limitedSpeed = this.getProperties().getSpeedLimit();
                }

                // Apply to the carts. Take speed factor into account, limit to at most 0.4 block movement
                limitedSpeed = Math.min(0.4, this.updateSpeedFactor * limitedSpeed);
                for (MinecartMember<?> mm : this) {
                    mm.getEntity().setMaxSpeed(limitedSpeed);
                }
            }

            // Calculate the speed factor that will be used to adjust the distance between the minecarts
            for (MinecartMember<?> member : this) {
                member.calculateSpeedFactor();
            }

            // Perform the rail post-movement logic
            /* Timings: onPhysicsPostMove  (Train Physics) */
            {
                for (MinecartMember<?> member : this) {
                    member.onPhysicsPostMove();
                    if (this.breakPhysics) return true;
                }
            }

            // Always refresh at least once per tick
            // This moment is strategically chosen, because after movement is the most likely
            // that a physics change will be required
            if (this.isLastUpdateStep()) {
                notifyPhysicsChange();
            }

            // Update directions and perform connection checks after the position changes
            this.updateDirection();
            if (!this.doConnectionCheck()) {
                return true; //false;
            }

            // Refresh chunks - may cause group to unload here
            this.updateChunkInformation(!this.canUnload(), false);

            // Refresh wheel position information, important to do it AFTER updateDirection()
            this.updateWheels();

            // If keeping chunks loaded, verify none of the members of this train as derailed
            // and flying off into nowhere. When this happens, forcibly unload the train and
            // log a warning.
            if (!this.isEmpty() && this.getProperties().isKeepingChunksLoaded()) {
                double thres = TCConfig.unloadRunawayTrainDistance * TCConfig.unloadRunawayTrainDistance;
                for (MinecartMember<?> member : this) {
                    Location derailedStartPos = member.getFirstKnownDerailedPosition();
                    if (derailedStartPos != null) {
                        double distanceSqSinceDerailed = member.getEntity().loc.distanceSquared(derailedStartPos);
                        if (distanceSqSinceDerailed > thres) {
                            Location loc = member.getEntity().getLocation();
                            traincarts.getLogger().log(Level.WARNING, "A cart of train " +
                                    this.getProperties().getTrainName() + " at world=" +
                                    loc.getWorld().getName() + " x=" + loc.getBlockX() +
                                    " y=" + loc.getBlockY() + " z=" + loc.getBlockZ() +
                                    " derailed and went moving/flying off into nowhere!");
                            traincarts.getLogger().log(Level.WARNING, "The train's keepChunksLoaded property has been " +
                                    " reset to false to prevent endless chunks being generated");
                            traincarts.getLogger().log(Level.WARNING, "The derailment likely occurred at " +
                                    "x=" + derailedStartPos.getBlockX() + " y=" + derailedStartPos.getBlockY() +
                                    " z=" + derailedStartPos.getBlockZ());
                            getProperties().setKeepChunksLoaded(false);
                            break;
                        }
                    }
                }
            }

            return true;
        } catch (MemberMissingException ex) {
            return false;
        }
    }
}
