package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.bases.ExtendedEntity;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.controller.EntityController;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.resources.CommonSounds;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.DamageSource;
import com.bergerkiller.bukkit.common.wrappers.MoveType;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TCListener;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModelOwner;
import com.bergerkiller.bukkit.tc.cache.RailSignCache.TrackedSign;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.components.RailTrackerMember;
import com.bergerkiller.bukkit.tc.controller.components.SignTrackerMember;
import com.bergerkiller.bukkit.tc.controller.components.SoundLoop;
import com.bergerkiller.bukkit.tc.controller.components.WheelTrackerMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVertical;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeActivator;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.ChunkArea;
import com.bergerkiller.bukkit.tc.utils.CollisionBox;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import com.bergerkiller.generated.net.minecraft.server.AxisAlignedBBHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;

public abstract class MinecartMember<T extends CommonMinecart<?>> extends EntityController<T>
        implements IPropertiesHolder, AttachmentModelOwner {
    public static final double GRAVITY_MULTIPLIER_RAILED = 0.015625;
    public static final double GRAVITY_MULTIPLIER = 0.04;
    public static final int MAXIMUM_DAMAGE_SUSTAINED = 40;
    private static final double MAX_MOVEMENT_STEP = 0.7; // ~ sqrt(3 * 0.4^2) with legacy speed limiting
    protected final ToggledState forcedBlockUpdate = new ToggledState(true);
    protected final ToggledState ignoreDie = new ToggledState(false);
    private final SignTrackerMember signTracker = new SignTrackerMember(this);
    private final ActionTrackerMember actionTracker = new ActionTrackerMember(this);
    private final RailTrackerMember railTrackerMember = new RailTrackerMember(this);
    private final WheelTrackerMember wheelTracker = new WheelTrackerMember(this);
    private final ToggledState railActivated = new ToggledState(false);
    protected final ToggledState ticked = new ToggledState();
    public boolean vertToSlope = false;
    protected MinecartGroup group;
    protected boolean died = false;
    private boolean unloaded = true;
    protected SoundLoop<?> soundLoop;
    private BlockFace direction;
    private BlockFace directionTo;
    private BlockFace directionFrom = null;
    private boolean ignoreAllCollisions = false;
    private int collisionEnterTimer = 0;
    private CartProperties properties;
    private Map<UUID, AtomicInteger> collisionIgnoreTimes = new HashMap<>();
    private Vector speedFactor = new Vector(0.0, 0.0, 0.0);
    private double roll = 0.0; // Roll is a custom property added, which is not persistently stored.
    private Quaternion cachedOrientation_quat = null;
    private float cachedOrientation_yaw = 0.0f;
    private float cachedOrientation_pitch = 0.0f;
    private boolean hasLinkedFarMinecarts = false;
    private Location preMovePosition = null;
    private Location postMovePosition = null;
    private Vector lastRailRefreshPosition = null;
    private Vector lastRailRefreshDirection = null;

    public static boolean isTrackConnected(MinecartMember<?> m1, MinecartMember<?> m2) {
        // Can the minecart reach the other?
        boolean m1moving = m1.isMoving();
        boolean m2moving = m2.isMoving();
        if (m1moving && m2moving) {
            if (!m1.isFollowingOnTrack(m2) && !m2.isFollowingOnTrack(m1))
                return false;
        } else if (m1moving) {
            if (!m1.isFollowingOnTrack(m2))
                return false;
        } else if (m2moving) {
            if (!m2.isFollowingOnTrack(m1))
                return false;
        } else {
            if (!m1.isNearOf(m2))
                return false;
            if (!TrackIterator.isConnected(m1.getBlock(), m2.getBlock(), false))
                return false;
        }
        return true;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.setUnloaded(true);
        this.railTrackerMember.onAttached();
        this.soundLoop = new SoundLoop<MinecartMember<?>>(this);
        this.updateDirection();
        this.wheelTracker.update();
        this.hasLinkedFarMinecarts = false;

        // Allows players to place blocks nearby a minecart despite having a custom
        // model
        entity.setPreventBlockPlace(false);

        // Forces a standard bounding box for block collisions
        setBlockCollisionBounds(new Vector(0.98, 0.7, 0.98));
    }

    @Override
    public void onDetached() {
        super.onDetached();

        // Sometimes dead is set to true, which does not get handled until much too late
        // But BKCommonLib does fire onDetached() when that happens. After this call the
        // entity will be gone.
        // It is very important to clean ourselves up here, otherwise a NPE spam occurs
        // with /train destroyall!
        if (entity.isDead()) {
            this.onDie();
        }
    }

    @Override
    public CartProperties getProperties() {
        if (this.properties == null) {
            this.properties = CartPropertiesStore.get(this);
            this.properties.getModel().addOwner(this);
        }
        return this.properties;
    }

    /**
     * Gets the Minecart Group of this Minecart<br>
     * If this Minecart is unloaded, a runtime exception is thrown<br>
     * If no group was previously set, a group is created
     *
     * @return group of this Minecart
     */
    public MinecartGroup getGroup() {
        if (this.isUnloaded()) {
            throw new RuntimeException("Unloaded members do not have groups!");
        }
        if (this.group == null) {
            MinecartGroupStore.create(this);
        }
        return this.group;
    }

    /**
     * Sets the group of this Minecart, removing this member from the previous
     * group<br>
     * Only called by internal methods (as it relies on group adding)
     *
     * @param group to set to
     */
    protected void setGroup(MinecartGroup group) {
        if (this.group != null && this.group != group) {
            this.group.removeSilent(this);
        }
        this.setUnloaded(false);
        this.group = group;
    }

    /**
     * Removes this Minecart from it's current group<br>
     * Upon the next call of getGroup() a new group is created
     */
    public void clearGroup() {
        this.setGroup(null);
    }

    public int getIndex() {
        if (this.group == null) {
            return entity.isDead() ? -1 : 0;
        } else {
            return this.group.indexOf(this);
        }
    }

    @Override
    public World getWorld() {
        return (entity == null) ? null : entity.getWorld();
    }

    /**
     * Called when a train is being saved, allowing this Minecart Member to include
     * additional data specific to the entity itself.
     * 
     * @param data
     */
    public void onTrainSaved(ConfigurationNode data) {
    }

    /**
     * Called when a train is being spawned, allowing this Minecart Member to load
     * additional data specific to the entity itself.
     * 
     * @param data
     */
    public void onTrainSpawned(ConfigurationNode data) {
    }

    /**
     * Gets whether the orientation of the Minecart is inverted compared to the
     * movement direction.
     * 
     * @return True if orientation is inverted
     */
    public boolean isOrientationInverted() {
        return Util.isOrientationInverted(this.calculateOrientation(), this.getWheels().getLastOrientation());
    }

    /**
     * Gets a normalized vector of the desired orientation of the Minecart. This is
     * the orientation the Minecart would have, if not flipped around, always
     * pointing into the movement direction.
     * 
     * @return orientation
     */
    public Vector calculateOrientation() {
        double dx = 0.0, dy = 0.0, dz = 0.0;
        if (this.group == null || this.group.size() <= 1) {
            dx = entity.getMovedX();
            dy = entity.getMovedY();
            dz = entity.getMovedZ();
        } else {
            // Find our displayed angle based on the relative position of this Minecart to
            // the neighbours
            int n = 0;
            if (this != this.group.head()) {
                // Add difference between this cart and the cart before
                MinecartMember<?> m = this.getNeighbour(-1);
                Vector m_pos = m.calcSpeedFactorPos();
                Vector s_pos = this.calcSpeedFactorPos();
                dx += m_pos.getX() - s_pos.getX();
                dy += m_pos.getY() - s_pos.getY();
                dz += m_pos.getZ() - s_pos.getZ();
                n++;
            }
            if (this != this.group.tail()) {
                // Add difference between this cart and the cart after
                MinecartMember<?> m = this.getNeighbour(1);
                Vector m_pos = m.calcSpeedFactorPos();
                Vector s_pos = this.calcSpeedFactorPos();
                dx += s_pos.getX() - m_pos.getX();
                dy += s_pos.getY() - m_pos.getY();
                dz += s_pos.getZ() - m_pos.getZ();
                n++;
            }
            dx /= n;
            dy /= n;
            dz /= n;
        }

        double n = MathUtil.getNormalizationFactor(dx, dy, dz);

        // First, fall back to entity velocity
        if (Double.isInfinite(n) || n >= 1e10) {
            dx = entity.vel.getX();
            dy = entity.vel.getY();
            dz = entity.vel.getZ();
            n = MathUtil.getNormalizationFactor(dx, dy, dz);
        }

        // If still invalid, use forward vector instead. Flip if opposite of direction.
        // This logic only applies when vehicles aren't moving
        if (Double.isInfinite(n)) {
            Vector forward = this.getOrientationForward();
            if (this.direction != null) {
                double dot = forward.getX() * this.direction.getModX() + forward.getY() * this.direction.getModY()
                        + forward.getZ() * this.direction.getModZ();
                if (dot < 0.0) {
                    forward.multiply(-1.0);
                }
            }
            return forward;
        } else {
            return new Vector(dx * n, dy * n, dz * n);
        }
    }

    // used by calculateOrientation()
    private Vector calcSpeedFactorPos() {
        return getWheels().getPosition();
    }

    /**
     * Gets the orientation of the Minecart. This is the direction of the 'front' of
     * the Minecart model. The orientation is automatically synchronized from/to the
     * yaw/pitch rotation angles of the Entity. To avoid gymbal lock, the Quaternion
     * is cached and returned for so long the yaw/pitch of the Entity is not
     * altered.
     * 
     * @return orientation
     */
    public Quaternion getOrientation() {
        if (entity.loc.getYaw() != this.cachedOrientation_yaw) {
            this.cachedOrientation_yaw = entity.loc.getYaw();
            this.cachedOrientation_quat = null;
        }
        if (entity.loc.getPitch() != this.cachedOrientation_pitch) {
            this.cachedOrientation_pitch = entity.loc.getPitch();
            this.cachedOrientation_quat = null;
        }
        if (this.cachedOrientation_quat == null) {
            this.cachedOrientation_quat = Quaternion.fromYawPitchRoll(this.cachedOrientation_pitch,
                    this.cachedOrientation_yaw + 90.0f, 0.0f);
        }
        return this.cachedOrientation_quat.clone();
    }

    /**
     * Gets the forward direction vector of the orientation of the Minecart. See
     * also: {@link #getOrientation()}.
     * 
     * @return forward orientation vector
     */
    public Vector getOrientationForward() {
        // TODO: Beneficial to cache this maybe?
        return this.getOrientation().forwardVector();
    }

    /**
     * Sets the orientation of the Minecart. This is the direction vector of the
     * 'front' of the Minecart model. The orientation is automatically synchronized
     * from/to the yaw/pitch rotation angles of the Entity.
     * 
     * @param orientation
     */
    public void setOrientation(Quaternion orientation) {
        // Check if not already equal. Don't do anything if it is.
        // Saves unneeded trig function calls
        if (this.cachedOrientation_quat != null) {
            double dx = this.cachedOrientation_quat.getX() - orientation.getX();
            double dy = this.cachedOrientation_quat.getY() - orientation.getY();
            double dz = this.cachedOrientation_quat.getZ() - orientation.getZ();
            double dw = this.cachedOrientation_quat.getW() - orientation.getW();
            if ((dx * dx + dy * dy + dz * dz + dw * dw) < 1E-20) {
                this.cachedOrientation_quat = orientation.clone();
                return;
            }
        }

        // Refresh
        this.cachedOrientation_quat = orientation.clone();
        Vector ypr = this.cachedOrientation_quat.getYawPitchRoll();
        this.cachedOrientation_yaw = (float) ypr.getY() - 90.0f;
        this.cachedOrientation_pitch = (float) ypr.getX();
        entity.setRotation(this.cachedOrientation_yaw, this.cachedOrientation_pitch);
    }

    /**
     * Flips the orientation of this Minecart, making front back and back front.
     */
    public void flipOrientation() {
        Quaternion orientation = this.getOrientation();
        orientation.rotateYFlip();
        this.setOrientation(orientation);
    }

    public MinecartMember<?> getNeighbour(int offset) {
        int index = this.getIndex();
        if (index == -1) {
            return null;
        }
        index += offset;
        if (this.getGroup().containsIndex(index)) {
            return this.getGroup().get(index);
        }
        return null;
    }

    public MinecartMember<?>[] getNeightbours() {
        if (this.getGroup() == null)
            return new MinecartMember<?>[0];
        int index = this.getIndex();
        if (index == -1)
            return new MinecartMember<?>[0];
        if (index > 0) {
            if (index < this.getGroup().size() - 1) {
                return new MinecartMember<?>[] { this.getGroup().get(index - 1), this.getGroup().get(index + 1) };
            } else {
                return new MinecartMember<?>[] { this.getGroup().get(index - 1) };
            }
        } else if (index < this.getGroup().size() - 1) {
            return new MinecartMember<?>[] { this.getGroup().get(index + 1) };
        } else {
            return new MinecartMember<?>[0];
        }
    }

    public SignTrackerMember getSignTracker() {
        return signTracker;
    }

    public WheelTrackerMember getWheels() {
        return wheelTracker;
    }

    /**
     * Sets whether this Minecart is unloaded. An unloaded minecart can not move and
     * can not be part of a group. Minecarts that are set unloaded will have all
     * standard behavior frozen until they are loaded again.
     * 
     * @param unloaded to set to
     */
    public void setUnloaded(boolean unloaded) {
        if (this.unloaded != unloaded) {
            this.unloaded = unloaded;
        }
    }

    /**
     * Gets whether this Minecart is unloaded
     *
     * @return True if it is unloaded, False if not
     */
    public boolean isUnloaded() {
        return this.unloaded || entity == null;
    }

    /**
     * Gets whether this Minecart allows player and world interaction. Unloaded or
     * dead minecarts do not allow world interaction.
     *
     * @return True if interactable, False if not
     */
    public boolean isInteractable() {
        return entity != null && !entity.isDead() && !this.isUnloaded();
    }

    /**
     * Calculates the distance traveled by this Minecart on a block, relative to a
     * movement direction. This is used for the adjustment from block distances to
     * cart distances
     * 
     * @return block moved sub-distance
     */
    public double calcSubBlockDistance() {
        double distance = 0.0;
        IntVector3 blockPos = entity.loc.block();
        distance += (this.direction.getModX() * (entity.loc.getX() - blockPos.midX()));
        distance += (this.direction.getModY() * (entity.loc.getY() - blockPos.midY()));
        distance += (this.direction.getModZ() * (entity.loc.getZ() - blockPos.midZ()));

        // Normalize if sub-cardinal
        if (FaceUtil.isSubCardinal(this.direction)) {
            distance /= 2.0;
        }

        return distance;
    }

    /**
     * Checks whether passengers of this Minecart take damage
     * 
     * @param cause of the damage
     * @return True if damage is allowed
     */
    public boolean canTakeDamage(Entity passenger, DamageCause cause) {
        if (getGroup().isTeleportImmune()) {
            return false;
        }

        // Suffocation damage presently only occurs from blocks above because of Vanilla
        // behavior
        // If this Minecart does not suffocate at all, cancel this event
        if (cause == DamageCause.SUFFOCATION && !this.isPassengerSuffocating(passenger)) {
            return false;
        }

        return true;
    }

    /**
     * Checks whether a passenger of this Minecart is stuck inside a block, and
     * therefore will be suffocating.
     * 
     * @param passenger to check
     * @return True if suffocating
     */
    public boolean isPassengerSuffocating(Entity passenger) {
        // If unloaded or suffocation is disabled, return false
        if (this.isUnloaded() || !this.getGroup().getProperties().hasSuffocation()) {
            return false;
        }

        // Turn Minecart position into a 4x4 transform matrix
        Matrix4x4 transform = new Matrix4x4();
        transform.translateRotate(entity.getLocation());

        // Transform passenger position with it
        Vector position = this.getPassengerPosition(passenger);
        transform.transformPoint(position);
        Block block = entity.getWorld().getBlockAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());

        // Check if suffocating
        return BlockUtil.isSuffocating(block);
    }

    /**
     * Gets the relative position of a passenger of this Minecart
     * 
     * @param passenger
     * @return passenger position
     */
    public Vector getPassengerPosition(Entity passenger) {
        return new Vector(0.0, 1.0, 0.0);
    }

    public boolean isInChunk(Chunk chunk) {
        return this.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public boolean isInChunk(org.bukkit.World world, int cx, int cz) {
        return entity != null && world == entity.getWorld()
                && Math.abs(cx - entity.getChunkX()) <= ChunkArea.CHUNK_RANGE
                && Math.abs(cz - entity.getChunkZ()) <= ChunkArea.CHUNK_RANGE;
    }

    public boolean isSingle() {
        return this.group == null || this.group.size() == 1;
    }

    /**
     * Gets whether the entity yaw is inverted 180 degrees with the actual
     * direction.<br>
     * <b>Deprecated: use {@link #isOrientationInverted()} instead</b>
     * 
     * @return True if inverted, False if not
     */
    @Deprecated
    public boolean isYawInverted() {
        return this.isOrientationInverted();
    }

    /*
     * Block functions
     */
    public Block getBlock(int dx, int dy, int dz) {
        return entity.getWorld().getBlockAt(getBlockPos().x + dx, getBlockPos().y + dy, getBlockPos().z + dz);
    }

    public Block getBlock(BlockFace face) {
        return this.getBlock(face.getModX(), face.getModY(), face.getModZ());
    }

    /**
     * Gets a Block relative to the current rail, offset by the notchOffset
     * 
     * @param notchOffset to offset by
     * @return relative block at this notch offset
     */
    public Block getBlockRelative(int notchOffset) {
        return this.getBlock(FaceUtil.notchFaceOffset(direction, notchOffset));
    }

    public Rails getRails() {
        return BlockUtil.getRails(this.getBlock());
    }

    public Block getGroundBlock() {
        return this.getBlock(0, -1, 0);
    }

    /*
     * Velocity functions
     */
    public double getForce() {
        return entity.vel.length();
    }

    /**
     * Gets the real speed of the minecart, keeping the
     * {@link MinecartGroup#getUpdateSpeedFactor()} into account. The speed is the
     * length of the velocity vector.
     * 
     * @return real speed
     */
    public double getRealSpeed() {
        if (this.group != null) {
            return entity.vel.length() / this.group.getUpdateSpeedFactor();
        } else {
            return entity.vel.length();
        }
    }

    /**
     * Gets the real speed of the minecart, like {@link #getRealSpeed()}, but limits
     * it to the maximum speed set for the train.
     * 
     * @return real speed, limited by max speed
     */
    public double getRealSpeedLimited() {
        if (this.group != null) {
            return Math.min(entity.vel.length(), entity.getMaxSpeed()) / this.group.getUpdateSpeedFactor();
        } else {
            return Math.min(entity.vel.length(), entity.getMaxSpeed());
        }
    }

    public double getForwardForce() {
        return this.getRailLogic().getForwardVelocity(this);
    }

    public void setForwardForce(double force) {
        this.getRailLogic().setForwardVelocity(this, force);
    }

    public void limitSpeed() {
        // Limits the velocity to the maximum
        final double currvel = getForce();
        if (currvel > entity.getMaxSpeed() && currvel > 0.01) {
            entity.vel.xz.multiply(entity.getMaxSpeed() / currvel);
        }
    }

    public Vector getLimitedVelocity() {
        double max;
        if (this.isUnloaded()) {
            max = entity.getMaxSpeed();
        } else {
            max = this.getGroup().getProperties().getSpeedLimit();
        }
        return new Vector(entity.vel.x.getClamped(max), entity.vel.y.getClamped(max), entity.vel.z.getClamped(max));
    }

    public TrackMap makeTrackMap(int size) {
        return new TrackMap(this.getBlock(), this.direction, size);
    }

    public boolean isCollisionIgnored(org.bukkit.entity.Entity entity) {
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(entity);
        if (member != null) {
            return this.isCollisionIgnored(member);
        }
        return this.ignoreAllCollisions || collisionIgnoreTimes.containsKey(entity.getUniqueId());
    }

    public boolean isCollisionIgnored(MinecartMember<?> member) {
        return this.ignoreAllCollisions || member.ignoreAllCollisions
                || this.collisionIgnoreTimes.containsKey(member.entity.getUniqueId())
                || member.collisionIgnoreTimes.containsKey(entity.getUniqueId());
    }

    public void ignoreCollision(org.bukkit.entity.Entity entity, int ticktime) {
        collisionIgnoreTimes.put(entity.getUniqueId(), new AtomicInteger(ticktime));
    }

    /**
     * Checks whether mobs/players are allowed to automatically (by collision) enter
     * this Minecart
     *
     * @return True if entities can enter, False if not
     */
    public boolean canCollisionEnter() {
        return collisionEnterTimer == 0;
    }

    /**
     * Resets the enter collision timer, waiting the tick time as configured before
     * taking in new entities when colliding with them.
     */
    public void resetCollisionEnter() {
        this.collisionEnterTimer = TCConfig.collisionReEnterDelay;
    }

    /*
     * Actions
     */
    public void pushSideways(org.bukkit.entity.Entity entity) {
        this.pushSideways(entity, TCConfig.pushAwayForce);
    }

    public void pushSideways(org.bukkit.entity.Entity entity, double force) {
        float yaw = FaceUtil.faceToYaw(this.direction);
        float lookat = MathUtil.getLookAtYaw(this.entity.getEntity(), entity) - yaw;
        lookat = MathUtil.wrapAngle(lookat);
        if (lookat > 0) {
            yaw -= 180;
        }
        Vector vel = MathUtil.getDirection(yaw, 0).multiply(force);
        entity.setVelocity(vel);
    }

    public void push(org.bukkit.entity.Entity entity, double force) {
        Vector offset = this.entity.loc.offsetTo(entity);
        MathUtil.setVectorLength(offset, force);
        entity.setVelocity(entity.getVelocity().add(offset));
    }

    public void playLinkEffect() {
        this.playLinkEffect(true);
    }

    public void playLinkEffect(boolean showSmoke) {
        Location loc = entity.getLocation();
        if (showSmoke) {
            loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
        }
        WorldUtil.playSound(loc, CommonSounds.EXTINGUISH, 1.0f, 2.0f);
    }

    /**
     * Checks if this minecart is dead, and throws an exception if it is
     *
     * @throws MemberMissingException
     */
    public void checkMissing() throws MemberMissingException {
        if (entity == null) {
            throw new MemberMissingException();
        } else if (entity.isDead()) {
            this.onDie();
            throw new MemberMissingException();
        } else if (this.isUnloaded()) {
            throw new MemberMissingException();
        }
    }

    /**
     * Obtains the Action Tracker that keeps track of actions for this Minecart
     *
     * @return action tracker
     */
    public ActionTrackerMember getActions() {
        return actionTracker;
    }

    /**
     * Gets the rail tracker that keeps track of the current Rail of this Minecart
     *
     * @return the Rail Tracker
     */
    public RailTrackerMember getRailTracker() {
        return this.railTrackerMember;
    }

    public IntVector3 getBlockPos() {
        return getRailTracker().getBlockPos();
    }

    /**
     * Gets the block this minecart was previously in, or driving on
     *
     * @return Last rail block or block at last minecart position
     */
    public Block getLastBlock() {
        return getRailTracker().getLastBlock();
    }

    /**
     * Gets the block this minecart is currently in, or driving on
     *
     * @return Rail block or block at minecart position
     */
    public Block getBlock() {
        return getRailTracker().getBlock();
    }

    private final Vector calcMotionVector(boolean ignoreVelocity) {
        // When derailed, we must rely on relative positioning to figure out the
        // direction
        // This only works when the minecart has a direct neighbor
        // If no direct neighbor is available, it will default to using its own velocity
        Vector motionVector = entity.getVelocity();
        double motionLengthSq = motionVector.lengthSquared();
        if (Double.isNaN(motionLengthSq)) {
            motionVector = new Vector();
            motionLengthSq = 0.0;
        }
        if (ignoreVelocity || motionLengthSq <= 1e-5) {
            if (!this.isDerailed() && this.direction != null) {
                motionVector = FaceUtil.faceToVector(this.direction);
            } else if (!this.isSingle()) {
                Vector alterMotionVector = motionVector;
                MinecartMember<?> next = this.getNeighbour(-1);
                if (next != null) {
                    alterMotionVector = getEntity().last.offsetTo(next.getEntity().last);
                } else {
                    MinecartMember<?> prev = this.getNeighbour(1);
                    if (prev != null) {
                        alterMotionVector = prev.getEntity().last.offsetTo(getEntity().last);
                    }
                }
                if (!Double.isNaN(alterMotionVector.lengthSquared())) {
                    motionVector = alterMotionVector;
                }
            }
        }
        if (Double.isNaN(motionVector.getX())) {
            throw new IllegalStateException("Motion vector is NaN");
        }
        return motionVector;
    }

    private final boolean fillRailInformation(RailState state) {
        // Need an initial Rail Block set
        state.setRailPiece(RailPiece.createWorldPlaceholder(entity.getWorld()));
        state.setMember(this);
        state.position().setMotion(this.calcMotionVector(false));

        // No pre-move position? Simply return block at current position.
        if (this.preMovePosition == null) {
            state.position().setLocation(entity.getLocation());
            return RailType.loadRailInformation(state);
        }

        // Detect the movement vector
        Vector direction = new Vector(entity.loc.getX() - this.preMovePosition.getX(),
                entity.loc.getY() - this.preMovePosition.getY(), entity.loc.getZ() - this.preMovePosition.getZ());
        double moved = direction.length();

        // When distance is too small or too large (teleport), simply use the current
        // position only
        final double smallStep = 1e-7;
        if (moved <= smallStep || moved > MAX_MOVEMENT_STEP) {
            state.position().setLocation(entity.getLocation());
            return RailType.loadRailInformation(state);
        }

        // Normalize direction vector
        direction.multiply(1.0 / moved);

        // Debug: uses walking point to do this instead of the small offset
        /*
        TrackWalkingPoint p = new TrackWalkingPoint(this.preMovePosition, direction);
        p.move(0.0);
        if (p.move(moved)) {
            state.setTo(p.state);
            state.setMember(this);
            return true;
        }
        */

        // TODO: Do we use this direction vector for motion or not?
        // Using this causes reverse() to not work anymore

        // Iterate the blocks from the preMovePosition to the current position and
        // discover rails here
        // Because we move such a short distance (<=MAX_MOVEMENT_STEP) it is very rare
        // for more than two blocks to ever be iterated
        // So we take a shortcut and only check the pre-move and current positions for
        // blocks in that order
        // The pre-move position might contain an outdated block though, so add a very
        // small amount to it in the direction
        // There is a TODO here to use a proper block iterator.
        Location prePos = new Location(entity.getWorld(), this.preMovePosition.getX() + smallStep * direction.getX(),
                this.preMovePosition.getY() + smallStep * direction.getY(),
                this.preMovePosition.getZ() + smallStep * direction.getZ());
        state.position().setLocation(prePos);
        if (RailType.loadRailInformation(state)) {
            state.position().setLocation(entity.getLocation());
            return true;
        }

        // Current position
        state.position().setLocation(entity.getLocation());
        return RailType.loadRailInformation(state);
    }

    /**
     * Looks at the current position information and attempts to discover any rails
     * at these positions. The movement of the minecart is taken into account. If
     * derailed, the rail type of the state is set to NONE.
     * 
     * @return rail state
     */
    public RailState discoverRail() {
        try (Timings t = TCTimings.MEMBER_PHYSICS_DISCOVER_RAIL.start()) {
            // Store motion vector in state
            RailState state = new RailState();
            state.setMember(this);
            boolean result = this.fillRailInformation(state);
            if (!result) {
                state.setRailType(RailType.NONE);
                state.position().setLocation(entity.getLocation());
                state.setMotionVector(this.calcMotionVector(true));
                state.initEnterDirection();
            }

            // Normalize motion vector
            state.position().normalizeMotion();

            // When railed, compute the direction by snapping the motion vector onto the
            // rail
            // This creates a motion vector perfectly aligned with the rail path.
            // This is important for later when looking for more rails, because we can
            // invert the motion vector to go 'the other way'.
            if (state.railType() != RailType.NONE) {
                RailLogic logic = state.loadRailLogic();
                RailPath path = logic.getPath();
                if (!path.isEmpty()) {
                    path.snap(state.position(), state.railBlock());
                }
            }

            return state;
        }
    }

    /**
     * Snaps a minecart onto a rail path, preserving moved distance from the last
     * position moved. Can be used in rail logic pre/post-move to adjust and correct
     * position on the path.
     * 
     * @param member to snap to this path
     */
    public void snapToPath(RailPath path) {
        if (path.isEmpty()) {
            return;
        }

        RailPath.Position pos;
        double toMove;
        Location currPos = entity.getLocation();
        if (this.preMovePosition == null) {
            pos = RailPath.Position.fromTo(currPos, currPos);
            toMove = 0.0;
        } else {
            pos = RailPath.Position.fromTo(this.preMovePosition, currPos);
            toMove = MathUtil.length(pos.motX, pos.motY, pos.motZ);
        }

        // When movement is large, teleport is almost certain
        // Because the only movement allowed in onMove is limited to 0.4
        if (toMove > MAX_MOVEMENT_STEP) {
            if (this.preMovePosition != null) {
                this.preMovePosition = currPos;
            }
            pos = RailPath.Position.fromTo(currPos, currPos);
            toMove = 0.0;
        }

        toMove -= path.move(pos, this.getBlock(), toMove);

        if (this.preMovePosition != null) {
            this.preMovePosition.setX(pos.posX);
            this.preMovePosition.setY(pos.posY);
            this.preMovePosition.setZ(pos.posZ);
        }

        // Correct motion based on anticipated end location
        // Sometimes the input motion is incorrect
        // When dot = 0 then there is no extra movement (or 90 degree angle, weird)
        /*
         * double dx = currPos.getX() - this.preMovePosition.getX(); double dy =
         * currPos.getY() - this.preMovePosition.getY(); double dz = currPos.getZ() -
         * this.preMovePosition.getZ(); double dot = dx*pos.motX + dy*pos.motY +
         * dz*pos.motZ; if (dot != 0.0) { toMove = Math.sqrt(dx*dx+dy*dy+dz*dz); if (dot
         * < 0.0) { pos.invertMotion(); } }
         */

        if (toMove > 0.0) {
            pos.posX += toMove * pos.motX;
            pos.posY += toMove * pos.motY;
            pos.posZ += toMove * pos.motZ;
        }
        entity.setPosition(pos.posX, pos.posY, pos.posZ);
    }

    /*
     * States
     */
    public boolean isMoving() {
        return entity.isMoving();
    }

    public boolean isTurned() {
        return FaceUtil.isSubCardinal(this.direction);
    }

    public boolean isDerailed() {
        return getRailType() == RailType.NONE;
    }

    /**
     * Checks whether this minecart is currently traveling on a vertical rail
     *
     * @return True if traveling vertically, False if not
     */
    public boolean isOnVertical() {
        return this.getRailLogic() instanceof RailLogicVertical;
    }

    public RailLogic getLastRailLogic() {
        return getRailTracker().getLastLogic();
    }

    public RailLogic getRailLogic() {
        return getRailTracker().getRailLogic();
    }

    public RailType getRailType() {
        return getRailTracker().getRailType();
    }

    public boolean hasBlockChanged() {
        return getRailTracker().hasBlockChanged();
    }

    public boolean isOnSlope() {
        return this.getRailLogic().isSloped();
    }

    public boolean isFlying() {
        return isDerailed() && !entity.isOnGround();
    }

    public boolean isMovingHorizontally() {
        return entity.isMovingHorizontally();
    }

    public boolean isMovingVerticalOnly() {
        return this.isMovingVertically() && !this.isMovingHorizontally();
    }

    public boolean isMovingVertically() {
        if (entity.isOnGround()) {
            // On the ground, are we possibly moving upwards (away from ground)?
            return entity.vel.getY() > ExtendedEntity.MIN_MOVE_SPEED;
        } else {
            // Not on the ground, if derailed we are flying, otherwise check for vertical
            // movement
            return isDerailed() || entity.isMovingVertically();
        }
    }

    public boolean isNearOf(MinecartMember<?> member) {
        double max = this.getMaximumDistance(member);
        return entity.loc.distanceSquared(member.entity) <= (max * max);
    }

    public boolean isHeadingTo(org.bukkit.entity.Entity entity) {
        return this.isHeadingTo(entity.getLocation());
    }

    public boolean isHeadingTo(Vector movement) {
        return MathUtil.isHeadingTo(movement, entity.getVelocity());
    }

    public boolean isHeadingTo(IntVector3 location) {
        return MathUtil.isHeadingTo(entity.loc.offsetTo(location.x, location.y, location.z), entity.getVelocity());
    }

    public boolean isHeadingTo(Location target) {
        return MathUtil.isHeadingTo(entity.getLocation(), target, entity.getVelocity());
    }

    public boolean isHeadingTo(BlockFace direction) {
        return MathUtil.isHeadingTo(direction, entity.getVelocity());
    }

    public boolean isFollowingOnTrack(MinecartMember<?> member) {
        // Checks if this member is able to follow the specified member on the tracks
        if (!this.isNearOf(member)) {
            return false;
        }
        // If derailed keep train alive
        if (this.isDerailed() || member.isDerailed()) {
            return true;
        }

        // Same block?
        Block memberrail = member.getBlock();
        if (BlockUtil.equals(this.getBlock(), memberrail)) {
            return true;
        }

        // If moving, use current direction, otherwise be flexible and allow both
        // directions
        if (this.isMoving()) {
            // Check if the current direction allows this minecart to reach the other rail
            if (TrackIterator.canReach(this.getBlock(), this.getDirectionTo(), memberrail)) {
                return true;
            }
            // Check both ways (just in case this direction is invalid)
            if (TrackIterator.isConnected(this.getBlock(), memberrail, true)) {
                return true;
            }
        } else {
            if (TrackIterator.isConnected(this.getBlock(), memberrail, false)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets whether this Minecart Member is heading into the same direction as
     * specified
     *
     * @param direction to test against
     * @return True if heading in the same direction, False if not
     */
    public boolean isDirectionTo(BlockFace direction) {
        return this.directionTo == direction || this.direction == direction;
    }

    /*
     * Directional functions
     */
    public BlockFace getDirection() {
        return this.direction;
    }

    public BlockFace getDirectionFrom() {
        if (this.directionFrom == null) {
            this.directionFrom = this.directionTo;
        }
        return this.directionFrom;
    }

    public BlockFace getDirectionTo() {
        return this.directionTo;
    }

    public void invalidateDirection() {
        this.directionFrom = this.direction = this.directionTo = null;
    }

    public int getDirectionDifference(BlockFace dircomparer) {
        return FaceUtil.getFaceYawDifference(this.getDirection(), dircomparer);
    }

    public int getDirectionDifference(MinecartMember<?> comparer) {
        return this.getDirectionDifference(comparer.getDirection());
    }

    public void updateDirection() {
        RailTrackerMember tracker = this.getRailTracker();

        // Direction is simply the motion vector on the rail, turned into a BlockFace
        RailState state = tracker.getState();
        this.direction = state.position().getMotionFaceWithSubCardinal();

        // TO direction is simply the enter face in the opposite direction
        RailState state_inv = state.clone();
        state_inv.position().invertMotion();
        state_inv.initEnterDirection();
        this.directionTo = state_inv.enterFace().getOppositeFace();
    }

    @Override
    public boolean onDamage(DamageSource damagesource, double damage) {
        if (entity.isDead()) {
            return false;
        }
        if (damagesource.toString().equals("fireworks")) {
            return false; // Ignore firework damage (used for cosmetics)
        }
        final Entity damager = damagesource.getEntity();
        try {
            // Call CraftBukkit event
            VehicleDamageEvent event = new VehicleDamageEvent(entity.getEntity(), damager, damage);
            if (CommonUtil.callEvent(event).isCancelled()) {
                return true;
            }
            damage = event.getDamage();
            // Play shaking animation and logic
            entity.setShakingDirection(-entity.getShakingDirection());
            entity.setShakingFactor(10);
            entity.setVelocityChanged(true);
            entity.setDamage(entity.getDamage() + damage * 10);
            // Check whether the entity is a creative (insta-build) entity
            boolean isInstantlyDestroyed = Util.canInstantlyBreakMinecart(damager);
            if (isInstantlyDestroyed) {
                entity.setDamage(100);
            }
            if (entity.getDamage() > MAXIMUM_DAMAGE_SUSTAINED) {
                // Send an event, pass in the drops to drop
                List<ItemStack> drops = new ArrayList<>(2);
                if (!isInstantlyDestroyed && getProperties().getSpawnItemDrops()) {
                    if (TCConfig.breakCombinedCarts) {
                        drops.addAll(entity.getBrokenDrops());
                    } else {
                        drops.add(new ItemStack(entity.getCombinedItem()));
                    }
                }
                VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(entity.getEntity(), damager);
                if (CommonUtil.callEvent(destroyEvent).isCancelled()) {
                    entity.setDamage(MAXIMUM_DAMAGE_SUSTAINED);
                    return true;
                }

                // Spawn drops and die
                for (ItemStack stack : drops) {
                    entity.spawnItemDrop(stack, 0.0F);
                }
                this.onDie();
            } else {
                // Select the Minecart for editing otherwise
                if (damager instanceof Player) {
                    CartPropertiesStore.setEditing((Player) damager, this.getProperties());
                }
            }
        } catch (Throwable t) {
            TrainCarts.plugin.handle(t);
        }
        return true;
    }

    /**
     * Tells the Minecart to ignore the very next call to {@link this.onDie()} This
     * is needed to avoid passengers removing their Minecarts.
     */
    public void ignoreNextDie() {
        ignoreDie.set();
    }

    @Override
    public void onDie() {
        try {
            // Die ignored?
            if (this.ignoreDie.clear()) {
                return;
            }
            if (!entity.isDead() || !this.died) {
                super.onDie();
                this.died = true;
                if (!this.isUnloaded()) {
                    // Note: No getGroup() calls are allowed here!
                    // They may create new groups!
                    if (entity.hasPassenger()) {
                        this.eject();
                    }
                    if (this.group != null) {
                        entity.setDead(false);
                        this.getSignTracker().clear();
                        entity.setDead(true);
                    }
                    if (entity.hasPassenger()) {
                        for (Entity passenger : entity.getPassengers()) {
                            entity.removePassenger(passenger);
                        }
                    }
                    if (this.group != null) {
                        this.group.remove(this);
                    }
                    CartPropertiesStore.remove(entity.getUniqueId());
                }
            }
        } catch (Throwable t) {
            TrainCarts.plugin.handle(t);
        }
    }

    @Override
    public boolean onEntityCollision(Entity e) {
        // Check if Entity not a passenger of this Train
        MinecartMember<?> vehicleTrain = MinecartMemberStore.getFromEntity(entity.getVehicle());
        if (vehicleTrain != null && vehicleTrain.group == this.group) {
            return false;
        }

        if (!this.isInteractable()) {
            return false;
        }

        // Verify that the entity is actually inside the bounding box of this entity
        // This involves a complicated rotated box intersection test
        if (!this.isModelIntersectingWith(e)) {
            return false;
        }

        CollisionMode mode = this.getGroup().getProperties().getCollisionMode(e);
        if (!mode.execute(this, e)) {
            return false;
        }
        // Collision occurred, collided head-on? Stop the entire train
        if (this.isHeadingTo(e)) {
            if (entity instanceof Minecart) {
                return false;
            }
            this.getGroup().stop();
        }
        return true;
    }

    @Override
    public boolean onBlockCollision(org.bukkit.block.Block hitBlock, BlockFace hitFace) {
        try (Timings t = TCTimings.MEMBER_PHYSICS_BLOCK_COLLISION.start()) {
            // When the minecart is vertical, minecraft likes to make it collide with blocks
            // on the opposite facing
            // Detect this and cancel this collision. This allows smooth air<>vertical
            // logic.
            Vector upVector = this.getOrientation().upVector();
            if (upVector.getY() >= -0.1 && upVector.getY() <= 0.1) {
                // If HitBlock x/z space contains the x/z position of the Minecart, allow the
                // collision
                double closest_dx = entity.loc.getX() - hitBlock.getX();
                double closest_dz = entity.loc.getZ() - hitBlock.getZ();
                final double MIN_COORD = 1e-10;
                final double MAX_COORD = 1.0 - MIN_COORD;
                if (closest_dx >= MIN_COORD && closest_dx <= MAX_COORD && closest_dz >= MIN_COORD
                        && closest_dz <= MAX_COORD) {
                    // Block is directly above or below; allow the collision
                } else if (upVector.getX() >= -0.1 && upVector.getX() <= 0.1) {
                    if ((-closest_dz) < -0.5)
                        closest_dz -= 1.0;
                    if ((upVector.getZ() > 0.0 && (-closest_dz) < -0.01))
                        return false;
                    if ((upVector.getZ() < 0.0 && (-closest_dz) > 0.01))
                        return false;
                } else if (upVector.getZ() >= -0.1 && upVector.getZ() <= 0.1) {
                    if ((-closest_dx) < -0.5)
                        closest_dx -= 1.0;
                    if ((upVector.getX() > 0.0 && (-closest_dx) < -0.01))
                        return false;
                    if ((upVector.getX() < 0.0 && (-closest_dx) > 0.01))
                        return false;
                }
            }

            if (!RailType.getType(hitBlock).onCollide(this, hitBlock, hitFace)) {
                return false;
            }
            if (!getRailType().onBlockCollision(this, getBlock(), hitBlock, hitFace)) {
                return false;
            }

            // Stop the entire Group if hitting head-on
            if (getRailType().isHeadOnCollision(this, getBlock(), hitBlock)) {
                this.getGroup().stop();
            }
        }
        return true;
    }

    /**
     * Checks whether the bounding box of another Entity is intersecting with this
     * minecart's 3d model bounding box
     * 
     * @param entity
     * @return True if intersecting
     */
    public boolean isModelIntersectingWith(Entity entity) {
        MinecartMember<?> other = MinecartMemberStore.getFromEntity(entity);
        if (other != null) {
            // Have to do both ways around!
            return this.isModelIntersectingWith_impl(entity)
                    && other.isModelIntersectingWith_impl(this.entity.getEntity());
        } else {
            return this.isModelIntersectingWith_impl(entity);
        }
    }

    private final boolean isModelIntersectingWith_impl(Entity entity) {
        // We lack a proper bounding box collision test
        // Instead we do a poor man's method of probing various points on the entity
        AxisAlignedBBHandle aabb = EntityHandle.fromBukkit(entity).getBoundingBox();
        double[] xval = { aabb.getMinX(), 0.5 * (aabb.getMinX() + aabb.getMaxX()), aabb.getMaxX() };
        double[] yval = { aabb.getMinY(), 0.5 * (aabb.getMinY() + aabb.getMaxY()), aabb.getMaxY() };
        double[] zval = { aabb.getMinZ(), 0.5 * (aabb.getMinZ() + aabb.getMaxZ()), aabb.getMaxZ() };
        for (double x : xval) {
            for (double y : yval) {
                for (double z : zval) {
                    if (isModelIntersectingWith_pointTest(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private final boolean isModelIntersectingWith_pointTest(double x, double y, double z) {
        return calculateModelDistance(new Vector(x, y, z)) <= 0.1;
    }

    /**
     * Calculates the distance between a point and this minecart's 3d model shape.
     * The position as controlled by the wheels is used for this.
     * 
     * @param point
     * @return distance
     */
    public double calculateModelDistance(Vector point) {
        // Factor in the offset of the minecart in world coordinates versus the point
        point = point.clone().subtract(this.getWheels().getPosition());

        // Undo the effects of the orientation of the Minecart
        Quaternion invOri = this.getOrientation().clone();
        invOri.invert();
        invOri.transformPoint(point);

        // Compute the 3d box coordinates of this Minecart
        double x_min = -0.5;
        double x_max = 0.5;
        double y_min = 0.0;
        double y_max = 1.0;
        double z_min = -0.5 * entity.getWidth();
        double z_max = 0.5 * entity.getWidth();

        // Perform box to point distance test using max and length
        double dx = Math.max(0.0, Math.max(x_min - point.getX(), point.getX() - x_max));
        double dy = Math.max(0.0, Math.max(y_min - point.getY(), point.getY() - y_max));
        double dz = Math.max(0.0, Math.max(z_min - point.getZ(), point.getZ() - z_max));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Gets the inventory of a potential Player passenger
     *
     * @return the passenger Player inventory, or null if there is no player
     */
    public PlayerInventory getPlayerInventory() {
        List<Player> players = entity.getPlayerPassengers();
        if (players.isEmpty()) {
            return null;
        } else {
            // TODO: Perhaps allow more than one player? Its weird.
            return players.get(0).getInventory();
        }
    }

    /**
     * Ejects the passenger of this Minecart
     */
    public void eject() {
        getEntity().eject();
        this.resetCollisionEnter();
    }

    /**
     * Ejects the passenger of this Minecart and teleports him to the offset and
     * rotation specified
     *
     * @param offset to teleport to
     * @param yaw    rotation
     * @param pitch  rotation
     */
    public void eject(Vector offset, float yaw, float pitch) {
        eject(new Location(entity.getWorld(), entity.loc.getX() + offset.getX(), entity.loc.getY() + offset.getY(),
                entity.loc.getZ() + offset.getZ(), yaw, pitch));
    }

    /**
     * Ejects the passenger of this Minecart and teleports him to the location
     * specified
     *
     * @param to location to eject/teleport to
     */
    public void eject(final Location to) {
        if (entity.hasPassenger()) {
            List<Entity> oldPassengers = new ArrayList<>(entity.getPassengers());
            TCListener.exemptFromEjectOffset.addAll(oldPassengers);
            this.eject();
            for (Entity oldPassenger : oldPassengers) {
                EntityUtil.teleportNextTick(oldPassenger, to);
            }
            TCListener.exemptFromEjectOffset.removeAll(oldPassengers);
        }
    }

    public boolean connect(MinecartMember<?> with) {
        return this.getGroup().connect(this, with);
    }

    @Override
    public void onPropertiesChanged() {
        this.getSignTracker().update();

        // Enable/disable collision handling to improve performance
        if (this.group != null) {
            setEntityCollisionEnabled(this.group.getProperties().getColliding());
            setBlockCollisionEnabled(this.group.getProperties().blockCollision == CollisionMode.DEFAULT);
        }
    }

    @Override
    public void onModelChanged(AttachmentModel model) {
        if (entity == null) {
            model.removeOwner(this);
            return;
        }
        entity.setSize(model.getCartLength(), 0.7f);
        this.wheelTracker.back().setDistance(0.5 * model.getWheelDistance() - model.getWheelCenter());
        this.wheelTracker.front().setDistance(0.5 * model.getWheelDistance() + model.getWheelCenter());

        // Limit the wheel distances to the bounds of half the cart length and 0.0
        double halfLength = 0.5 * model.getCartLength();
        if (this.wheelTracker.back().getDistance() < 0.0) {
            this.wheelTracker.back().setDistance(0.0);
        } else if (this.wheelTracker.back().getDistance() > halfLength) {
            this.wheelTracker.back().setDistance(halfLength);
        }
        if (this.wheelTracker.front().getDistance() < 0.0) {
            this.wheelTracker.front().setDistance(0.0);
        } else if (this.wheelTracker.front().getDistance() > halfLength) {
            this.wheelTracker.front().setDistance(halfLength);
        }
    }

    @Override
    public void onModelNodeChanged(AttachmentModel model, int[] targetPath, ConfigurationNode config) {
        this.onModelChanged(model);
    }

    /**
     * Checks whether this Minecart Member is being controlled externally by an
     * action. If this is True, the default physics such as gravity and slowing-down
     * factors are not applied.
     *
     * @return True if movement is controlled, False if not
     */
    public boolean isMovementControlled() {
        return getActions().isMovementControlled() || getGroup().getActions().isMovementControlled();
    }

    public boolean isIgnoringCollisions() {
        return this.ignoreAllCollisions;
    }

    public void setIgnoreCollisions(boolean ignoreAll) {
        this.ignoreAllCollisions = ignoreAll;
    }

    public void stop() {
        this.stop(false);
    }

    public void stop(boolean cancelLocationChange) {
        entity.vel.setZero();
        if (cancelLocationChange) {
            entity.loc.set(entity.last);
        }
    }

    protected void updateUnloaded() {
        setUnloaded((entity == null) || OfflineGroupManager.containsMinecart(entity.getUniqueId()));
        if (!unloaded && (this.group == null || this.group.canUnload())) {
            // Check a 5x5 chunk area around this Minecart to see if it is loaded
            World world = entity.getWorld();
            int midX = entity.getChunkX();
            int midZ = entity.getChunkZ();
            int cx, cz;
            for (cx = -ChunkArea.CHUNK_RANGE; cx <= ChunkArea.CHUNK_RANGE; cx++) {
                for (cz = -ChunkArea.CHUNK_RANGE; cz <= ChunkArea.CHUNK_RANGE; cz++) {
                    if (!WorldUtil.isLoaded(world, cx + midX, cz + midZ)) {
                        setUnloaded(true);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Respawns the entity to the client (used to avoid teleport smoothing)
     */
    public void respawn() {
        entity.getNetworkController().syncRespawn();
    }

    /**
     * Called when the minecart moves from one (or more) block(s) to another (or
     * more)
     *
     * @param from block - the old block
     * @param to   block - the new block
     */
    public void onBlockChange(Block from, Block to) {
        // Update from direction
        if (BlockUtil.getManhattanDistance(from, to, true) > 3) {
            this.directionFrom = null; // invalidate from direction - too long ago
        }

        // Destroy blocks
        if (!this.isDerailed() && this.getProperties().hasBlockBreakTypes()) {
            // Obtain the block directly to the left (represented by -2) and right
            // (represented by 2) of the minecart
            Block left = this.getBlockRelative(-2);
            Block right = this.getBlockRelative(2);
            if (this.getProperties().canBreak(left)) {
                WorldUtil.getBlockData(left).destroy(left, 20.0f);
            }
            if (this.getProperties().canBreak(right)) {
                WorldUtil.getBlockData(right).destroy(right, 20.0f);
            }
        }
    }

    /**
     * Executes the block and pre-movement calculations, which handles rail
     * information updates<br>
     * Physics stage: <b>1</b>
     */
    public void onPhysicsStart() {
        // subtract times
        Iterator<AtomicInteger> times = collisionIgnoreTimes.values().iterator();
        while (times.hasNext()) {
            if (times.next().decrementAndGet() <= 0)
                times.remove();
        }
        if (this.collisionEnterTimer > 0) {
            this.collisionEnterTimer--;
        }

        // Prepare
        entity.vel.fixNaN();
        entity.last.set(entity.loc);
    }

    /**
     * Reads input from passengers of this Minecart to perform manual movement of
     * the minecart, if enabled
     */
    public void updateManualMovement() {
        // Vehicle steering input from living entity passengers
        // This is only allowed when the property is enabled and our velocity < 0.1
        // blocks/tick (0.01 squared)
        if (getGroup().getProperties().isManualMovementAllowed() && entity.vel.lengthSquared() < 0.01
                && !this.isDerailed()) {
            for (Entity passenger : entity.getPassengers()) {
                if (passenger instanceof LivingEntity) {
                    float forwardMovement = EntityLivingHandle.fromBukkit((LivingEntity) passenger)
                            .getForwardMovement();
                    if (forwardMovement > 0.0f) {
                        // Use Entity yaw and pitch to find the direction to boost the minecart into
                        // For now, this only supports horizontal 'pushing'
                        Vector direction = ((LivingEntity) passenger).getEyeLocation().getDirection();
                        entity.vel.add(direction.getX() * TCConfig.manualMovementFactor, 0.0,
                                direction.getZ() * TCConfig.manualMovementFactor);
                    }
                }
            }
        }
    }

    /**
     * Executes the velocity and pre-movement calculations, which handles logic
     * prior to actual movement occurs<br>
     * Physics stage: <b>3</b>
     */
    public void onPhysicsPreMove() {
        // At this point it's safe to say that the Rail Logic will not change
        getRailTracker().snapshotRailLogic();

        // Reduce shaking over time
        if (entity.getShakingFactor() > 0) {
            entity.setShakingFactor(entity.getShakingFactor() - 1);
        }

        // Health regenerate
        if (entity.getDamage() > 0) {
            entity.setDamage(entity.getDamage() - 1);
        }

        // Kill entity if falling into the void
        if (entity.loc.getY() < -64.0D) {
            this.onDie();
            throw new MemberMissingException();
        }

        // reset fall distance
        if (!this.isDerailed()) {
            entity.setFallDistance(0.0f);
        }

        // Perform rails logic
        getRailLogic().onPreMove(this);

        // Refresh last-update direction and block information
        this.getRailTracker().updateLast();

        // Update the entity shape
        entity.setPosition(entity.loc.getX(), entity.loc.getY(), entity.loc.getZ());

        if (getGroup().getProperties().isManualMovementAllowed() && entity.hasPassenger()) {
            for (Entity passenger : entity.getPassengers()) {
                Vector vel = passenger.getVelocity();
                vel.setY(0.0);
                if (vel.lengthSquared() > 1.0E-4 && entity.vel.xz.lengthSquared() < 0.01) {
                    entity.vel.xz.add(vel.multiply(TCConfig.manualMovementFactor));
                }
            }
        }

        // Perform any pre-movement rail updates
        getRailType().onPreMove(this);
    }

    /**
     * Performs all logic right after movement has occurred
     */
    public void doPostMoveLogic() {
    }

    /**
     * Performs the move logic for when the Minecart travels on top of an Activator
     * rail.
     *
     * @param activated state of the Activator rail
     */
    public void onActivatorUpdate(boolean activated) {
    }

    /**
     * Called when activated goes from FALSE to TRUE
     */
    public void onActivate() {
    }

    public void calculateSpeedFactor() {
        this.speedFactor.setX(0.0).setY(0.0).setZ(0.0);
        MinecartGroup group = this.getGroup();
        if (group.size() != 1 && !group.getActions().isMovementControlled()
                && !this.getActions().isMovementControlled()) {
            MinecartMember<?> n1 = this.getNeighbour(-1);
            MinecartMember<?> n2 = this.getNeighbour(1);
            if (n1 != null) {
                this.speedFactor.add(calculateSpeedFactor(this, n1));
            }
            if (n2 != null) {
                this.speedFactor.add(calculateSpeedFactor(n2, this));
            }
            if (n1 != null && n2 != null) {
                this.speedFactor.multiply(0.5);
            }
        }
    }

    /**
     * Calculates the gap between two minecarts, and the movement direction to
     * change to move from the back cart to the front cart.
     * 
     * @param back      cart
     * @param front     cart
     * @param direction output Vector, is modified by function
     * @return gap between the back and front carts
     */
    public static double calculateGapAndDirection(MinecartMember<?> back, MinecartMember<?> front, Vector direction) {
        // Retrieve the positions of the backwards moving part of the cart,
        // and the forwards moving part of the cart behind. The gap
        // between these two positions must be kept.
        WheelTrackerMember.Wheel frontwheel = front.getWheels().movingBackwards();
        WheelTrackerMember.Wheel backwheel = back.getWheels().movingForwards();
        Vector frontpos = frontwheel.getAbsolutePosition();
        Vector backpos = backwheel.getAbsolutePosition();
        direction.setX(frontpos.getX() - backpos.getX());
        direction.setY(frontpos.getY() - backpos.getY());
        direction.setZ(frontpos.getZ() - backpos.getZ());

        // If distance can not be reliably calculated, use BlockFace direction
        // Otherwise normalize the direction vector
        double distance = direction.length();
        if (distance < 0.01) {
            direction.setX(front.getDirection().getModX());
            direction.setY(front.getDirection().getModY());
            direction.setZ(front.getDirection().getModZ());
            direction.normalize();
        } else {
            direction.multiply(1.0 / distance);
        }

        return distance - frontwheel.getEdgeDistance() - backwheel.getEdgeDistance();
    }

    private final Vector calculateSpeedFactor(MinecartMember<?> back, MinecartMember<?> front) {
        Vector direction = new Vector();
        double gap = calculateGapAndDirection(back, front, direction);
        if (back == this) {
            direction.multiply(-1.0);
        }

        // Set the factor to the offset we must make to correct the distance
        double distanceDiff = (TCConfig.cartDistanceGap - gap);
        direction.multiply(distanceDiff);
        return direction;
    }

    protected void verifyPreMovePosition() {
        if (this.postMovePosition != null) {
            if (this.postMovePosition.getX() != this.entity.loc.getX()
                    || this.postMovePosition.getY() != this.entity.loc.getY()
                    || this.postMovePosition.getZ() != this.entity.loc.getZ()) {
                this.preMovePosition = null;
            }
        }
    }

    protected void calcPostMovePosition() {
        if (this.postMovePosition == null) {
            this.postMovePosition = this.entity.getLocation();
        } else {
            this.entity.getLocation(this.postMovePosition);
        }
    }

    /**
     * Moves the minecart and performs post-movement logic such as events,
     * onBlockChanged and other (rail) logic Physics stage: <b>4</b>
     *
     * @throws MemberMissingException - thrown when the minecart is dead or dies
     * @throws GroupUnloadedException - thrown when the group is no longer loaded
     */
    public void onPhysicsPostMove() throws MemberMissingException, GroupUnloadedException {
        this.checkMissing();

        // Limit velocity to Max Speed
        entity.vel.fixNaN();
        Vector vel = entity.getVelocity();
        if (TCConfig.legacySpeedLimiting) {
            // Legacy limiting limited each axis individually
            // In curves and when going up, this resulted in speeds higher than permitted
            vel.setX(MathUtil.clamp(vel.getX(), entity.getMaxSpeed()));
            vel.setY(MathUtil.clamp(vel.getY(), entity.getMaxSpeed()));
            vel.setZ(MathUtil.clamp(vel.getZ(), entity.getMaxSpeed()));
        } else {
            // New limiting system preserves the velocity direction, but normalizes it to
            // the max speed
            double vel_length = entity.vel.length();
            if (vel_length > entity.getMaxSpeed()) {
                double vel_factor = (entity.getMaxSpeed() / vel_length);
                vel.multiply(vel_factor);
            }
        }

        // Apply speed factor to adjust the minecart positions relative to each other
        // The rate at which this happens depends on the speed of the minecart
        this.getRailLogic().onSpacingUpdate(this, vel, this.speedFactor);

        // No vertical motion if stuck to the rails that way
        /*
         * if (!getRailLogic().hasVerticalMovement()) { vel.setY(0.0); }
         */

        this.directionFrom = this.directionTo;

        // Move using set motion, and perform post-move rail logic
        try (Timings t = TCTimings.MEMBER_PHYSICS_POST_MOVE.start()) {
            if (this.preMovePosition == null) {
                this.preMovePosition = entity.getLocation();
            } else {
                entity.getLocation(this.preMovePosition);
            }
            onMove(MoveType.SELF, vel.getX(), vel.getY(), vel.getZ());
        }

        this.checkMissing();
        try (Timings t = TCTimings.MEMBER_PHYSICS_POST_RAIL_LOGIC.start()) {
            this.getRailLogic().onPostMove(this);
        }

        // Update manual movement from player input
        updateManualMovement();

        // Post-move logic
        this.doPostMoveLogic();
        if (!this.isDerailed()) {
            // Slowing down of minecarts
            if (this.getGroup().getProperties().isSlowingDown(SlowdownMode.FRICTION) && entity.getMaxSpeed() > 0.0) {
                double factor;
                if (entity.hasPassenger() || !entity.isSlowWhenEmpty() || !TCConfig.slowDownEmptyCarts) {
                    factor = TCConfig.slowDownMultiplierNormal;
                } else {
                    factor = TCConfig.slowDownMultiplierSlow;
                }
                if (this.getGroup().getUpdateStepCount() > 1) {
                    factor = Math.pow(factor, this.getGroup().getUpdateSpeedFactor());
                }
                entity.vel.multiply(factor);
            }
        }

        // Activator rail logic here - we can't do it in the rail properly
        if (this.getRailType() instanceof RailTypeActivator) {
            final boolean powered = ((RailTypeActivator) this.getRailType()).isPowered();
            this.onActivatorUpdate(powered);
            if (powered && this.railActivated.set()) {
                this.onActivate();
            } else {
                this.railActivated.clear();
            }
        } else {
            this.railActivated.clear();
        }

        // Perform post-movement rail logic
        getRailType().onPostMove(this);

        // Invalidate volatile information
        getRailTracker().setLiveRailLogic();

        // Perform some (CraftBukkit) events
        Location from = entity.getLastLocation();
        Location to = entity.getLocation();
        Vehicle vehicle = entity.getEntity();

        try (Timings t = TCTimings.MEMBER_PHYSICS_POST_BUKKIT_UPDATE.start()) {
            CommonUtil.callEvent(new VehicleUpdateEvent(vehicle));
        }

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            // Execute move events
            try (Timings t = TCTimings.MEMBER_PHYSICS_POST_BUKKIT_MOVE.start()) {
                CommonUtil.callEvent(new VehicleMoveEvent(vehicle, from, to));
            }

            // Execute signs MEMBER_MOVE
            try (Timings t = TCTimings.MEMBER_PHYSICS_POST_SIGN_MEMBER_MOVE.start()) {
                Collection<TrackedSign> trackedSigns = this.getSignTracker().getActiveTrackedSigns();
                if (!trackedSigns.isEmpty()) {
                    for (TrackedSign sign : trackedSigns) {
                        SignActionEvent event = new SignActionEvent(sign);
                        event.setMember(this);
                        SignAction.executeAll(event, SignActionType.MEMBER_MOVE);
                    }
                }
            }
        }

        // Performs linkage with nearby minecarts
        // This allows trains to link before actually colliding
        // Version 1.12.2-v3: only do this ONCE after placing/spawning to reduce cpu
        // usage
        if (!this.hasLinkedFarMinecarts) {
            this.hasLinkedFarMinecarts = true;
            for (Entity near : entity.getNearbyEntities(0.2, 0, 0.2)) {
                if (near instanceof Minecart && !entity.isPassenger(near)) {
                    EntityUtil.doCollision(near, entity.getEntity());
                }
            }
        }

        // Handle collisions for train lengths > 1.0 in a special way

        /*
         * if (this.getCartLength() > 1.0) { double rad = 0.5 * this.getCartLength();
         * for (Entity near : entity.getNearbyEntities(rad, rad, rad)) { // Skip near
         * entities that are other minecarts in this same train // Saves on performance
         * handling collision events for all of them every tick MinecartMember<?>
         * nearMember = MinecartMemberStore.getFromEntity(near); if (nearMember != null
         * && nearMember.group == this.group) { continue; }
         * 
         * // Verify by using the transform of this Minecart whether or not the entity
         * is actually colliding // We do so by performing a
         * EntityUtil.doCollision(near, this.entity.getEntity()); } }
         */

        // Ensure that dead passengers are cleared
        for (Entity passenger : entity.getPassengers()) {
            if (passenger.isDead()) {
                entity.removePassenger(passenger);
            }
        }

        // Final logic
        this.checkMissing();

        // Play additional sound effects
        this.soundLoop.onTick();
    }

    @Override
    public void onTick() {
        this.ticked.set();
        if (this.isUnloaded()) {
            return;
        }
        MinecartGroup g = this.getGroup();
        if (g != null && g.ticked.set()) {
            g.doPhysics();
        }
    }

    /**
     * Sets the current roll of the Minecart. This does not set the roll induced by
     * shaking effects.
     * 
     * @param newroll
     */
    public void setRoll(double newroll) {
        if (newroll != this.roll) {
            this.roll = newroll;
        }
    }

    /**
     * Gets the current roll of the Minecart. This includes roll induced by shaking
     * effects.
     * 
     * @return roll angle
     */
    public double getRoll() {
        double result = this.roll;
        // TODO: Integrate shaking direction / shaking power into this roll value
        return result + getWheels().getBankingRoll();
    }

    /**
     * Sets the rotation of the Minecart, taking care of wrap-around of the angles
     * 
     * @param newyaw      to set to
     * @param newpitch    to set to
     * @param orientPitch
     */
    public void setRotationWrap(float newyaw, float newpitch) {
        final float oldyaw = entity.loc.getYaw();

        // Fix yaw based on the previous yaw angle
        while ((newyaw - oldyaw) >= 90.0f) {
            newyaw -= 180.0f;
            newpitch = -newpitch;
        }
        while ((newyaw - oldyaw) < -90.0f) {
            newyaw += 180.0f;
            newpitch = -newpitch;
        }

        // Fix up wrap-around angles
        while ((newyaw - oldyaw) <= -180.0f) {
            newyaw += 360.0f;
        }
        while ((newyaw - oldyaw) > 180.0f) {
            newyaw -= 360.0f;
        }

        entity.setRotation(newyaw, newpitch);
    }

    @Override
    public String getLocalizedName() {
        String name = super.getLocalizedName();
        if (name == null || name.equals("unknown")) {
            name = "Minecart"; // Bug with older BKCommonLib!
        }
        if (!isSingle()) {
            name += " (Train)";
        }
        return name;
    }

    @Override
    public boolean isPlayerTakable() {
        return this.isSingle() && (this.isUnloaded() || this.getGroup().getProperties().isPlayerTakeable());
    }

    @Override
    public boolean parseSet(String key, String args) {
        return false;
    }

    /**
     * Gets the number of seats still available for new entities to enter the
     * minecart
     * 
     * @return number of available seats
     */
    public int getAvailableSeatCount() {
        int total = this.getSeatCount();
        int passengers = entity.getPassengers().size();
        if (passengers >= total) {
            return 0;
        } else {
            return total - passengers;
        }
    }

    /**
     * Gets the number of seats available in this Minecart. This is based on the
     * model attachments applied if a model is used. Otherwise, it simply returns 1
     * when this is a rideable minecart.
     * 
     * @return seat count
     */
    public int getSeatCount() {
        AttachmentModel model = this.getProperties().getModel();
        if (model == null) {
            return (entity.getType() == EntityType.MINECART) ? 1 : 0;
        } else {
            return model.getSeatCount();
        }
    }

    /**
     * Calculates the preferred distance between the center of this Minecart and the
     * member specified. By playing with the speed of the two carts, this distance
     * is maintained steady.
     * 
     * @param member
     * @return preferred distance
     */
    public double getPreferredDistance(MinecartMember<?> member) {
        return 0.5 * ((double) entity.getWidth() + (double) member.getEntity().getWidth()) + TCConfig.cartDistanceGap;
    }

    /**
     * Calculates the maximum distance between the center of this Minecart and the
     * member specified. If the distance between them exceeds this value, the two
     * Minecarts lose linkage.
     * 
     * @param member
     * @return maximum distance
     */
    public double getMaximumDistance(MinecartMember<?> member) {
        return 0.5 * ((double) entity.getWidth() + (double) member.getEntity().getWidth())
                + TCConfig.cartDistanceGapMax;
    }

    /**
     * Calculates the maximum amount of block iterations between the center of this
     * Minecart and the member specified that should be performed when discovering
     * rails. Generally aiming too high is not a big deal, this is only here to
     * prevent infinite cycles from crashing the server.
     * 
     * @param member
     * @return maximum block iteration around
     */
    public int getMaximumBlockDistance(MinecartMember<?> member) {
        return MathUtil.ceil(2.0 * getMaximumDistance(member));
    }

    /**
     * Gets a rotated 3D hitbox, which can be used to test whether a player's click
     * is on this entity
     * 
     * @return click hitbox
     */
    public CollisionBox getHitBox() {
        CollisionBox box = new CollisionBox();
        box.setPosition(this.getWheels().getPosition());
        box.setRadius(1.0, 1.0, entity.getWidth());
        box.setOrientation(this.getOrientation());
        return box;
    }

    /**
     * Detect changes in Minecart position since the last time rail information was
     * refreshed.
     * 
     * @return True if position changed
     */
    boolean railDetectPositionChange() {
        Vector nvel = entity.vel.vector();
        double fact = MathUtil.getNormalizationFactor(nvel);
        if (fact != Double.POSITIVE_INFINITY && !Double.isNaN(fact)) {
            nvel.multiply(fact);
        }
        if (this.lastRailRefreshPosition == null || this.lastRailRefreshDirection == null) {
            this.lastRailRefreshPosition = entity.loc.vector();
            this.lastRailRefreshDirection = entity.vel.vector().normalize();
            return true;
        } else if (this.lastRailRefreshPosition.getX() != entity.loc.getX()
                || this.lastRailRefreshPosition.getY() != entity.loc.getY()
                || this.lastRailRefreshPosition.getZ() != entity.loc.getZ()
                || this.lastRailRefreshDirection.getX() != nvel.getX()
                || this.lastRailRefreshDirection.getY() != nvel.getY()
                || this.lastRailRefreshDirection.getZ() != nvel.getZ()) {
            this.lastRailRefreshPosition.setX(entity.loc.getX());
            this.lastRailRefreshPosition.setY(entity.loc.getY());
            this.lastRailRefreshPosition.setZ(entity.loc.getZ());
            this.lastRailRefreshDirection.setX(nvel.getX());
            this.lastRailRefreshDirection.setY(nvel.getY());
            this.lastRailRefreshDirection.setZ(nvel.getZ());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Plays an animation for a single attachment node for this minecart. Only the
     * attachment at the targetPath will play the animation.
     * 
     * @param targetPath
     * @param options    defining the animation to play
     * @return True if the attachment node and animation could be found
     */
    public boolean playNamedAnimationFor(int[] targetPath, AnimationOptions options) {
        Attachment attachment = findAttachment(targetPath);
        return attachment != null && attachment.playNamedAnimation(options);
    }

    /**
     * Plays an animation for a single attachment node for this minecart.
     * 
     * @param targetPath indices for the attachment node
     * @param animation  to play
     * @return True if the attachment node could be found
     */
    public boolean playAnimationFor(int[] targetPath, Animation animation) {
        Attachment attachment = findAttachment(targetPath);
        if (attachment == null) {
            return false;
        } else {
            attachment.startAnimation(animation);
            return true;
        }
    }

    /**
     * Plays an animation by name for this minecart. All attachments storing an
     * animation with this name will play.
     * 
     * @param name of the animation
     * @return True if an animation was found and started
     */
    public boolean playNamedAnimation(String name) {
        return this.playNamedAnimation(new AnimationOptions(name));
    }

    /**
     * Plays an animation using the animation options specified for this minecart.
     * All attachments storing an animation with the options' name will play.
     * 
     * @param options for the animation
     * @return True if an animation was found and started
     */
    public boolean playNamedAnimation(AnimationOptions options) {
        MinecartMemberNetwork network = CommonUtil.tryCast(entity.getNetworkController(), MinecartMemberNetwork.class);
        if (network != null) {
            return network.getRootAttachment().playNamedAnimationRecursive(options);
        } else {
            return false;
        }
    }

    /**
     * Looks up an attachment of the Minecart by target path. The path is a series of indices to
     * get to that particular attachment in the tree hierarchy.
     * 
     * @param targetPath
     * @return Attachment at this path, or null if not found
     */
    public Attachment findAttachment(int[] targetPath) {
        MinecartMemberNetwork network = CommonUtil.tryCast(entity.getNetworkController(), MinecartMemberNetwork.class);
        return (network == null) ? null : network.getRootAttachment().findChild(targetPath);
    }
}
