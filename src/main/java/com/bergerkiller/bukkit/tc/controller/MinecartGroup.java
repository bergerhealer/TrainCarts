package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.inventory.MergedInventory;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.cache.RailMemberCache;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerGroup;
import com.bergerkiller.bukkit.tc.controller.components.SignTrackerGroup;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailTrackerGroup;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberChest;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberFurnace;
import com.bergerkiller.bukkit.tc.events.*;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.ChunkArea;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class MinecartGroup extends MinecartGroupStore implements IPropertiesHolder {
    private static final long serialVersionUID = 3;
    private static final LongHashSet chunksBuffer = new LongHashSet(50);
    protected final ToggledState networkInvalid = new ToggledState();
    protected final ToggledState ticked = new ToggledState();
    protected final ChunkArea chunkArea = new ChunkArea();
    private final SignTrackerGroup signTracker = new SignTrackerGroup(this);
    private final RailTrackerGroup railTracker = new RailTrackerGroup(this);
    private final ActionTrackerGroup actionTracker = new ActionTrackerGroup(this);
    protected long lastSync = Long.MIN_VALUE;
    private TrainProperties prop = null;
    private boolean breakPhysics = false;
    private int teleportImmunityTick = 0;
    private double updateSpeedFactor = 1.0;
    private boolean lastUpdateStep = true;
    private boolean unloaded = false;

    protected MinecartGroup() {
        this.ticked.set();
    }

    public boolean isPropertiesEqual(TrainProperties prop) {
        return this.prop == prop;
    }

    @Override
    public TrainProperties getProperties() {
        if (this.prop == null) {
            if (this.isUnloaded()) {
                throw new IllegalStateException("Group is unloaded");
            }
            this.prop = TrainPropertiesStore.create();
            for (MinecartMember<?> member : this) {
                this.prop.add(member);
            }
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
        if (this.prop != null) {
            TrainPropertiesStore.remove(this.prop.getTrainName());
        }
        this.prop = properties;
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

    private void addMember(MinecartMember<?> member) {
        member.setGroup(this);
        this.getSignTracker().updatePosition();
        this.getProperties().add(member);
    }

    public void add(int index, MinecartMember<?> member) {
        if (member.isUnloaded()) {
            throw new IllegalArgumentException("Can not add unloaded members to groups");
        }
        super.add(index, member);
        MemberAddEvent.call(member, this);
        this.addMember(member);
    }

    public boolean add(MinecartMember<?> member) {
        if (member.isUnloaded()) {
            throw new IllegalArgumentException("Can not add unloaded members to groups");
        }
        super.add(member);
        MemberAddEvent.call(member, this);
        this.addMember(member);
        return true;
    }

    public boolean addAll(int index, Collection<? extends MinecartMember<?>> members) {
        super.addAll(index, members);
        MinecartMember<?>[] memberArr = members.toArray(new MinecartMember<?>[0]);
        for (MinecartMember<?> m : memberArr) {
            if (m.isUnloaded()) {
                throw new IllegalArgumentException("Can not add unloaded members to groups");
            }
            MemberAddEvent.call(m, this);
        }
        for (MinecartMember<?> member : memberArr) {
            this.addMember(member);
        }
        return true;
    }

    public boolean addAll(Collection<? extends MinecartMember<?>> members) {
        super.addAll(members);
        MinecartMember<?>[] memberArr = members.toArray(new MinecartMember<?>[0]);
        for (MinecartMember<?> m : memberArr) {
            if (m.isUnloaded()) {
                throw new IllegalArgumentException("Can not add unloaded members to groups");
            }
            MemberAddEvent.call(m, this);
        }
        for (MinecartMember<?> member : memberArr) {
            this.addMember(member);
        }
        return true;
    }

    public boolean containsIndex(int index) {
        return !this.isEmpty() && (index >= 0 && index < this.size());
    }

    public World getWorld() {
        return isEmpty() ? null : get(0).getEntity().getWorld();
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
        return !this.isEmpty() && (this.size() == 1 || !this.getProperties().requirePoweredMinecart || this.size(EntityType.MINECART_FURNACE) > 0);
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

    public boolean remove(Object o) {
        int index = this.indexOf(o);
        return index != -1 && this.remove(index) != null;
    }

    private MinecartMember<?> removeMember(int index) {
        MinecartMember<?> member = super.get(index);
        MemberRemoveEvent.call(member);
        super.remove(index);
        this.getActions().removeActions(member);
        this.getSignTracker().updatePosition();
        onMemberRemoved(member);
        member.group = null;
        return member;
    }

    private void onMemberRemoved(MinecartMember<?> member) {
        this.getProperties().remove(member);
        this.getRailTracker().removeMemberRails(member);
        try (Timings t = TCTimings.RAILMEMBERCACHE.start()) {
            RailMemberCache.remove(member);
        }
    }

    public MinecartMember<?> remove(int index) {
        MinecartMember<?> removed = this.removeMember(index);
        if (this.isEmpty()) {
            //Remove empty group as a result
            this.remove();
        } else {
            //Split the train at the index
            removed.playLinkEffect();
            this.split(index);
        }
        return removed;
    }

    /**
     * Splits this train, the index is the first cart for the new group<br><br>
     * <p/>
     * For example, this Group has a total cart count of 5<br>
     * If you then split at index 2, it will result in:<br>
     * - This group becomes a group of 2 carts<br>
     * - A new group of 3 carts is created
     */
    public MinecartGroup split(int at) {
        if (at <= 0) return this;
        if (at >= this.size()) return null;
        //transfer the new removed carts
        MinecartGroup gnew = new MinecartGroup();
        int count = this.size();
        for (int i = at; i < count; i++) {
            gnew.add(this.removeMember(this.size() - 1));
        }
        //Remove this train if now empty
        if (!this.isValid()) {
            this.remove();
        }
        //Remove if empty or not allowed, else add
        if (gnew.isValid()) {
            //Add the group
            groups.add(gnew);

            //Set the new group properties
            gnew.getProperties().load(this.getProperties());

            GroupCreateEvent.call(gnew);
            return gnew;
        } else {
            gnew.clear();
            return null;
        }
    }

    @Override
    public void clear() {
        this.getSignTracker().clear();
        this.getActions().clear();
        for (MinecartMember<?> mm : this.toArray()) {
            this.getProperties().remove(mm);
            if (mm.getEntity().isDead()) {
                mm.onDie();
            } else {
                mm.group = null;
                mm.getGroup().getProperties().load(this.getProperties());
            }
        }
        super.clear();
    }

    public void remove() {
        if (!groups.remove(this)) {
            return; // Already removed
        }
        GroupRemoveEvent.call(this);
        this.clear();
        this.updateChunkInformation();
        if (this.prop != null) {
            TrainPropertiesStore.remove(this.prop.getTrainName());
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
        this.unloaded = true;

        // Undo partial-unloading before calling the event
        for (MinecartMember<?> member : this) {
            member.group = this;
            member.setUnloaded(false);
        }

        // Event
        GroupUnloadEvent.call(this);

        // Unload in detector regions
        getSignTracker().unload();

        // Store the group offline
        OfflineGroupManager.storeGroup(this);

        // Free memory cached in train properties
        getProperties().getSkipOptions().unloadSigns();
        for (MinecartMember<?> member : this) {
            member.getProperties().getSkipOptions().unloadSigns();
        }

        // Unload
        this.stop(true);
        groups.remove(this);
        for (MinecartMember<?> member : this) {
            member.group = null;
            member.setUnloaded(true);

            // We must correct position here, because it will no longer be ticked!
            member.getEntity().doPostTick();
        }

        // Clear group members and disable this group further
        super.clear();
        this.prop = null;
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
        this.getSignTracker().updatePosition();
    }

    private void teleportMember(MinecartMember<?> member, Location location) {
        member.ignoreDie.set();
        if (member.isYawInverted()) {
            location = location.clone();
            location.setYaw(location.getYaw() + 180.0f);
        }
        member.getWheels().startTeleport();
        member.getEntity().teleport(location);
        member.ignoreDie.clear();
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

    public void reverse() {
        for (MinecartMember<?> mm : this) {
            mm.reverse();
        }
        Collections.reverse(this);
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

    public void updateDirection() {
        try (Timings t = TCTimings.GROUP_UPDATE_DIRECTION.start()) {
            if (this.size() == 1) {
                this.getRailTracker().refresh();
                this.head().updateDirection();
            } else if (this.size() > 1) {
                int reverseCtr = 0;
                while (true) {
                    // Update direction of individual carts
                    this.getRailTracker().refresh();
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
                        fforce += m.getRailTracker().getState().position().motDot(m.getEntity().getVelocity());
                    }
                    if (fforce >= 0) {
                        break;
                    } else {
                        Collections.reverse(this);
                    }
                }
            }
        }

        /*
        if (this.size() == 1) {
            this.get(0).updateDirectionSelf();
        } else if (this.size() > 1) {
            int reverseCtr = 0;
            while (true) {
                // Update direction of individual carts
                head().updateDirectionFromBehind(head(1));
                for (int i = 1; i < size(); i++) {
                    head(i).updateDirectionFollow(head(i-1));
                }

                // Handle train reversing (with maximum 2 attempts)
                if (reverseCtr++ == 2) {
                    break;
                }
                double fforce = 0;
                for (MinecartMember<?> m : this) {
                    fforce += m.getForwardForce();
                }
                if (fforce >= 0) {
                    break;
                } else {
                    Collections.reverse(this);
                }
            }
        }
        */
    }

    /**
     * Gets the average speed/force of this train. Airbound Minecarts are exempt
     * from the average. See also:
     * {@link com.bergerkiller.bukkit.tc.rails.logic.RailLogic#hasForwardVelocity(member) RailLogic.hasForwardVelocity(member)}
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
            force += MathUtil.invert(m.getForce(), m.getForwardForce() < 0.0);
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
     * Checks if this Minecart Group can unload, or if chunks are kept loaded instead<br>
     * The keepChunksLoaded property is read, as well the moving state if configured<br>
     * If a player is inside the train, it will keep the chunks loaded as well
     *
     * @return True if it can unload, False if it keeps chunks loaded
     */
    public boolean canUnload() {
        if (this.getProperties().isKeepingChunksLoaded()) {
            if (!TCConfig.keepChunksLoadedOnlyWhenMoving || this.isMoving()) {
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

    public Inventory getInventory() {
        //count amount of storage minecarts
        Inventory[] source = new Inventory[this.size(EntityType.MINECART_CHEST)];
        int i = 0;
        for (MinecartMember<?> mm : this) {
            if (mm instanceof MinecartMemberChest) {
                source[i] = ((MinecartMemberChest) mm).getEntity().getInventory();
                i++;
            }
        }
        if (source.length == 1) {
            return source[0];
        }
        return new MergedInventory(source);
    }

    public Inventory getPlayerInventory() {
        //count amount of player passengers
        int count = 0;
        for (MinecartMember<?> mm : this) {
            if (mm.getEntity().hasPlayerPassenger()) {
                count++;
            }
        }
        Inventory[] source = new Inventory[count];
        if (source.length == 1) {
            return source[0];
        }
        int i = 0;
        for (MinecartMember<?> mm : this) {
            if (mm.getEntity().hasPlayerPassenger()) {
                source[i] = mm.getPlayerInventory();
                i++;
            }
        }
        return new MergedInventory(source);
    }

    public void loadChunks() {
        for (MinecartMember<?> mm : this) mm.loadChunks();
    }

    public boolean isInChunk(Chunk chunk) {
        return this.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public boolean isInChunk(World world, int cx, int cz) {
        for (MinecartMember<?> mm : this) {
            if (mm.isInChunk(world, cx, cz)) return true;
        }
        return false;
    }

    @Override
    public boolean parseSet(String key, String args) {
        return false;
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
     * Gets whether the currently executing updates are the final update step.
     * See {@link #getUpdateSpeedFactor()} for an explanation of what this means.
     * 
     * @return True if this is the last update step
     */
    public boolean isLastUpdateStep() {
        return this.lastUpdateStep;
    }

    /**
     * Aborts any physics routines going on in this tick
     */
    public void breakPhysics() {
        this.breakPhysics = true;
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
                    int time = (int) MathUtil.clamp(2 / gnew.head().getForce(), 20, 40);
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

    private void updateChunkInformation() {
        try (Timings t = TCTimings.GROUP_UPDATE_CHUNKS.start()) {
            // Create a set of all chunks directly occupied by the minecarts in this group
            chunksBuffer.clear();
            for (MinecartMember<?> mm : this) {
                chunksBuffer.add(mm.getEntity().loc.x.chunk(), mm.getEntity().loc.z.chunk());
            }

            // Refresh the chunk area tracker using this information
            this.chunkArea.refresh(this.getWorld(), chunksBuffer);

            // Keep-chunks-loaded or automatic unloading when moving into unloaded chunks
            if (this.canUnload()) {
                // Check all newly added chunks whether the chunk is unloaded
                // When such a chunk is found, unload this train
                for (ChunkArea.OwnedChunk chunk : this.chunkArea.getAdded()) {
                    if (!chunk.isLoaded()) {
                        this.unload();
                        throw new GroupUnloadedException();
                    }
                }
            } else {
                // Enqueue chunks we moved away from for unloading
                for (ChunkArea.OwnedChunk chunk : this.chunkArea.getRemoved()) {
                    chunk.unloadChunkRequest();
                }

                // Load chunks closeby right away and guarantee they are loaded at all times
                for (ChunkArea.OwnedChunk chunk : this.chunkArea.getAll()) {
                    if (chunk.getDistance() <= 1 && chunk.getPreviousDistance() > 1) {
                        chunk.loadChunk();
                    }
                }

                // Load chunks we entered, and are far enough away, for asynchronous loading
                for (ChunkArea.OwnedChunk chunk : this.chunkArea.getAdded()) {
                    if (chunk.getDistance() > 1) {
                        chunk.loadChunkRequest();
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
        System.out.println(msg);
    }

    private double getSpeedAhead() {
        // Not sure if fixed, but if this train is empty, return MAX_VALUE
        if (this.isEmpty()) {
            return Double.MAX_VALUE;
        }

        // If no wait distance is set and no mutex zones are anywhere close, skip these expensive calculations
        if (this.getProperties().getWaitDistance() <= 0.0) {
            UUID world = this.head().getEntity().getWorld().getUID();
            IntVector3 block = this.head().getBlockPos();
            if (!MutexZoneCache.isMutexZoneNearby(world, block)) {
                return Double.MAX_VALUE;
            }
        }

        boolean checkTrains = false;
        double waitDistance = this.getProperties().getWaitDistance();
        if (waitDistance > 0.0) {
            checkTrains = true;
        }

        // Two blocks are used to slow down the train, to make it match up to speed with the train up ahead
        // Check for any mutex zones ~2 blocks ahead, and stop before we enter them
        // If a wait distance is set, also check for trains there
        final double CHECK_MARGIN = 1.5;
        double checkDistance = waitDistance + (0.5 * this.head().getEntity().getWidth()) + CHECK_MARGIN - this.head().calcSubBlockDistance();

        UUID worldUUID = this.getWorld().getUID();
        double cartDistance;
        TrackIterator iter = new TrackIterator(this.head().getBlock(), this.head().getDirectionTo());
        while ((cartDistance = iter.getCartDistance()) <= checkDistance && iter.hasNext()) {
            Block rail = iter.next();

            // Check for mutex zones the next block. If one is found that is occupied, stop right away
            if (iter.getDistance() == 2) {
                MutexZone zone = MutexZoneCache.find(worldUUID, new IntVector3(rail));
                if (zone != null && !zone.tryEnter(this)) {
                    return 0.0;
                }

                if (!checkTrains) {
                    break;
                }
            }

            // Only check for trains on the rails when a wait distance is set
            if (!checkTrains) {
                continue;
            }
            MinecartMember<?> other = MinecartMemberStore.getAt(rail);
            if (other != null && other.getGroup() != this) {
                // Train is heading for me! Stop!
                if (MathUtil.isHeadingTo(iter.currentDirection().getOppositeFace(), other.getEntity().getVelocity())) {
                    return 0.0;
                }

                // The distance we have presently is to the middle of the current block of the minecart
                // However, what we want is the distance to the minecart itself, not the block
                // To avoid jumpy behavior, factor in the position of the minecart in the distance calculation
                cartDistance += other.calcSubBlockDistance();

                // Find the distance we can still move from our current position
                double remaining = (cartDistance - waitDistance);

                // If remaining is negative, stop! We can't possibly move any further without violating our rule
                if (remaining <= 0.0) {
                    return 0.0;
                }

                // Maintain distance. Use remaining to switch between force and absolute 0 for a smooth slowdown
                double otherSpeed = MathUtil.clamp(other.getForce(), other.getEntity().getMaxSpeed());
                return Math.min(otherSpeed, remaining);
            }
        }

        return Double.MAX_VALUE;
    }

    private void tickActions() {
        try (Timings t = TCTimings.GROUP_TICK_ACTIONS.start()) {
            this.getActions().doTick();
        }
    }

    public void doPhysics() {
        // NOP if unloaded
        if (this.isUnloaded()) {
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
        try {
            double totalforce = this.getAverageForce();
            double speedlimit = this.getProperties().getSpeedLimit();
            int update_steps = 1;
            if (totalforce > 0.4 && speedlimit > 0.4) {
                update_steps = (int) Math.ceil(speedlimit / 0.4);
            }
            this.updateSpeedFactor = 1.0 / (double) update_steps;

            try (Timings t = TCTimings.GROUP_DOPHYSICS.start()) {
                if (update_steps > 1) {
                    for (MinecartMember<?> mm : this) {
                        mm.getEntity().vel.multiply(this.updateSpeedFactor);
                    }
                    for (int i = 0; i < update_steps; i++) {
                        this.lastUpdateStep = (i == (update_steps - 1));
                        while (!this.doPhysics_step()) ;
                    }
                } else {
                    this.lastUpdateStep = true;
                    this.doPhysics_step();
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
        } catch (GroupUnloadedException ex) {
            //this group is gone
        } catch (Throwable t) {
            final TrainProperties p = getProperties();
            TrainCarts.plugin.log(Level.SEVERE, "Failed to perform physics on train '" + p.getTrainName() + "' at " + p.getLocation() + ":");
            TrainCarts.plugin.handle(t);
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
            double speedLimitClamped = MathUtil.clamp(this.getProperties().getSpeedLimit() * this.updateSpeedFactor, 0.4);
            for (MinecartMember<?> mm : this) {
                mm.checkMissing();
                mm.getEntity().setMaxSpeed(speedLimitClamped);
            }

            // Set up a valid network controller if needed
            if (networkInvalid.clear()) {
                for (MinecartMember<?> m : this) {
                    EntityNetworkController<?> controller = m.getEntity().getNetworkController();
                    if (!(controller instanceof MinecartMemberNetwork)) {
                        m.getEntity().setNetworkController(new MinecartMemberNetwork());
                    }
                }
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
            try (Timings t = TCTimings.MEMBER_PHYSICS_PRE.start()) {
                for (MinecartMember<?> member : this) {
                    member.onPhysicsPreMove();
                }
            }

            // Direction can change as a result of gravity
            this.updateDirection();

            // Stop if all dead
            if (this.isEmpty()) {
                return false;
            }

            // If a wait distance is set, check for trains ahead of the track and wait for those
            // We do the waiting by setting the max speed of the train (NOT speed limit!) to match that train's speed
            try (Timings t = TCTimings.GROUP_ENFORCE_SPEEDAHEAD.start()) {
                double speedAhead = this.getSpeedAhead();
                double newSpeedLimit = Math.min(this.getProperties().getSpeedLimit(), speedAhead);
                if (newSpeedLimit < this.getProperties().getSpeedLimit()) {
                    speedLimitClamped = MathUtil.clamp(newSpeedLimit * this.updateSpeedFactor, 0.4);
                    for (MinecartMember<?> mm : this) {
                        mm.checkMissing();
                        mm.getEntity().setMaxSpeed(speedLimitClamped);
                    }
                }
            }

            // Share forward force between all the Minecarts when size > 1
            if (this.size() > 1) {
                //Get the average forwarding force of all carts
                double force = this.getAverageForce();

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
                        m.setForwardForce(force);
                    }
                }
            }

            // Calculate the speed factor that will be used to adjust the distance between the minecarts
            for (MinecartMember<?> member : this) {
                member.calculateSpeedFactor();
            }

            // Add the gravity effects right before moving the Minecart
            // This changes velocity slightly so that minecarts go downslope or fall down
            // It is important to do it here, so that gravity is taken into account
            // when sliding over the ground. Doing this in the wrong spot will make the minecart 'hover'.
            if (this.getProperties().isSlowingDown(SlowdownMode.GRAVITY)) {
                for (MinecartMember<?> member : this) {
                    if (member.isUnloaded()) continue; // not loaded - no physics occur
                    if (member.isMovementControlled()) continue; // launched by station, launcher, etc.

                    // Find segment of the rails path the Minecart is on
                    RailLogic logic = member.getRailLogic();
                    CommonMinecart<?> entity = member.getEntity();
                    Block block = member.getRailTracker().getBlock();
                    RailPath.Segment segment = logic.getPath().findSegment(entity.loc.vector(), block);
                    if (segment == null) {
                        // Not on any segment? Simply subtract GRAVITY_MULTIPLIER
                        entity.vel.y.subtract(logic.getGravityMultiplier(member));
                    } else if (segment.dt_norm.y < -1e-6 || segment.dt_norm.y > 1e-6) {
                        // On a non-level segment, gravity must be applied based on the slope the segment is at
                        double f = logic.getGravityMultiplier(member) * segment.dt_norm.y;
                        entity.vel.subtract(segment.dt_norm.x * f, segment.dt_norm.y * f, segment.dt_norm.z * f);
                    }
                }
            }

            // Perform the move and post-movement logic
            for (MinecartMember<?> member : this) {
                try (Timings t = TCTimings.MEMBER_PHYSICS_POST.start()) {
                    member.onPhysicsPostMove();
                    if (this.breakPhysics) return true;
                }
            }

            // Update directions and perform connection checks after the position changes
            this.updateDirection();
            if (!this.doConnectionCheck()) {
                return true; //false;
            }

            // Refresh chunks
            this.updateChunkInformation();

            // Refresh wheel position information, important to do it AFTER updateDirection()
            for (MinecartMember<?> member : this) {
                try (Timings t = TCTimings.MEMBER_PHYSICS_UPDATE_WHEELS.start()) {
                    member.getWheels().update();
                }
            }

            return true;
        } catch (MemberMissingException ex) {
            return false;
        }
    }
}
