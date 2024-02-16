package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.bases.ExtendedEntity;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.controller.EntityController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.inventory.MergedInventory;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DamageSource;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.common.wrappers.MoveType;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TCListener;
import com.bergerkiller.bukkit.tc.TCSeatChangeListener;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerMember;
import com.bergerkiller.bukkit.tc.controller.components.AnimationController;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRailWalker;
import com.bergerkiller.bukkit.tc.controller.components.RailTrackerMember;
import com.bergerkiller.bukkit.tc.controller.components.SignTracker;
import com.bergerkiller.bukkit.tc.controller.components.SignTrackerMember;
import com.bergerkiller.bukkit.tc.controller.components.SoundLoop;
import com.bergerkiller.bukkit.tc.controller.components.WheelTrackerMember;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.SlowdownMode;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.WorldRailLookup;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVertical;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeActivator;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.ChunkArea;
import com.bergerkiller.bukkit.tc.utils.Effect;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.phys.AxisAlignedBBHandle;

public abstract class MinecartMember<T extends CommonMinecart<?>> extends EntityController<T>
        implements IPropertiesHolder, AnimationController, TrainCarts.Provider {
    public static final double GRAVITY_MULTIPLIER_RAILED = 0.015625;
    public static final double GRAVITY_MULTIPLIER = 0.04;
    public static final int MAXIMUM_DAMAGE_SUSTAINED = 40;
    private final TrainCarts traincarts;
    protected final ToggledState forcedBlockUpdate = new ToggledState(true);
    private final SignTrackerMember signTracker;
    private final ActionTrackerMember actionTracker;
    private final RailTrackerMember railTrackerMember;
    private final WheelTrackerMember wheelTracker;
    private final AttachmentControllerMember attachmentController;
    private final ToggledState railActivated = new ToggledState(false);
    public boolean vertToSlope = false;
    protected MinecartGroup group;
    protected boolean died = false;
    private boolean unloaded = true;
    protected boolean unloadedLastPlayerTakable = false;
    protected SoundLoop<?> soundLoop;
    protected BlockFace direction;
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
    private Vector lastRailRefreshPosition = null;
    private Vector lastRailRefreshDirection = null;
    private Location firstKnownDerailedPosition = null;
    private List<Entity> enterForced = new ArrayList<Entity>(1);
    private boolean wasMoving = false; // for starting driveSound property. TODO: Attachment?
    /**
     * Tracks the location since last vehicle move event. Works around an issue where the server is
     * modifying the entity last location outside of the main tick() loop, causing random desynchs.
     * Should be ignored if null or on a different World.
     */
    private Location lastLocation;
    /**
     * Is set to lastLocation before it is updated. This way, outside of vehicle tick()
     * this is set to last tick's location, the same behavior of Entity getLastLocation().
     */
    private Location lastLocationSync;
    private WorldRailLookup railLookup = WorldRailLookup.NONE; // current-world rail lookup

    public MinecartMember(TrainCarts traincarts) {
        if (traincarts == null) {
            throw new IllegalArgumentException("TrainCarts plugin cannot be null");
        }
        this.traincarts = traincarts;

        // Must be initialized after the traincarts plugin instance is set to avoid potential trouble
        this.signTracker = new SignTrackerMember(this);
        this.actionTracker = new ActionTrackerMember(this);
        this.railTrackerMember = new RailTrackerMember(this);
        this.wheelTracker = new WheelTrackerMember(this);
        this.attachmentController = new AttachmentControllerMember(this);
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

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
    public CartProperties getProperties() {
        if (this.properties == null) {
            this.properties = CartPropertiesStore.createForMember(this);
        }
        return this.properties;
    }

    /**
     * Saves the properties of this cart, including metadata such as the type of entity,
     * inventory contents and orientation to a YAML configuration node.
     *
     * @return saved configuration
     */
    public ConfigurationNode saveConfig() {
        ConfigurationNode savedCartConfig = getProperties().saveToConfig().clone();

        savedCartConfig.set("entityType", getEntity().getType());
        savedCartConfig.set("flipped", getOrientationForward().dot(FaceUtil.faceToVector(getDirection())) < 0.0);
        savedCartConfig.remove("owners");

        ConfigurationNode data = new ConfigurationNode();
        onTrainSaved(data);
        if (!data.isEmpty()) {
            savedCartConfig.set("data", data);
        }

        return savedCartConfig;
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
            if (this.group == null) {
                if (this.isUnloaded()) {
                    throw new RuntimeException("Unloaded members do not have groups!");
                }
                throw new IllegalStateException("Failed to initialize new group for member at " +
                        "world=" + entity.loc.getWorld().getName() +
                        " x=" + entity.loc.getX() +
                        " y=" + entity.loc.getY() +
                        " z=" + entity.loc.getZ());
            }
        }
        return this.group;
    }

    /**
     * Gets whether this member has been assigned to a group. If not,
     * and the member is not unloaded, {@link #getGroup()} will assign
     * it a new group.
     *
     * @return True if it has a group
     */
    public boolean hasInitializedGroup() {
        return this.group != null && !this.isUnloaded();
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
            return entity.isRemoved() ? -1 : 0;
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
    @SuppressWarnings("deprecation")
    public void onTrainSaved(ConfigurationNode data) {
        if (entity != null) {
            int offset = entity.getBlockOffset();
            BlockData block = entity.getBlock();
            boolean hasOffset = (offset != Util.getDefaultDisplayedBlockOffset());
            boolean hasBlock = (block != null && block != BlockData.AIR);
            if (hasOffset || hasBlock) {
                // Save displayed block information
                ConfigurationNode displayedBlock = data.getNode("displayedBlock");
                displayedBlock.set("offset", hasOffset ? offset : null);
                displayedBlock.set("type", hasBlock ? block.getCombinedId() : null);
            } else {
                data.remove("displayedBlock");
            }
        }
    }

    /**
     * Called when a train is being spawned, allowing this Minecart Member to load
     * additional data specific to the entity itself.
     * 
     * @param data
     */
    @SuppressWarnings("deprecation")
    public void onTrainSpawned(ConfigurationNode data) {
        if (entity != null && data.isNode("displayedBlock")) {
            ConfigurationNode displayedBlock = data.getNode("displayedBlock");
            if (displayedBlock.contains("offset")) {
                entity.setBlockOffset(displayedBlock.get("offset", Util.getDefaultDisplayedBlockOffset()));
            }
            if (displayedBlock.contains("type")) {
                BlockData type = BlockData.fromCombinedId(displayedBlock.get("type", 0));
                if (type != null && type != BlockData.AIR) {
                    entity.setBlock(type);
                }
            }
        }
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

        // Force a respawn right now
        this.getWheels().startTeleport();
        this.getWheels().update();
        this.getAttachments().syncRespawn();
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
     * Gets the controller for the attachments configured for this cart
     *
     * @return attachment controller
     */
    public AttachmentControllerMember getAttachments() {
        return attachmentController;
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
            if (unloaded && this.group != null) {
                this.unloadedLastPlayerTakable = this.group.getProperties().isPlayerTakeable();
            }
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
        return entity != null && !entity.isRemoved() && !this.isUnloaded();
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

        // Transform passenger position with it
        Location position = this.getPassengerLocation(passenger);
        position.setY(position.getY() + 1.0);
        Block block = position.getBlock();

        // Check if suffocating
        return BlockUtil.isSuffocating(block);
    }

    /**
     * Gets the absolute world coordinates and orientation a passenger exiting this Minecart
     * will have. If the passenger Entity is not a passenger, then a default exit
     * offset is assumed.
     * 
     * @param passenger
     * @return passenger eject position
     */
    public Location getPassengerEjectLocation(Entity passenger) {
        CartAttachmentSeat seat = this.getAttachments().findSeat(passenger);

        if (seat == null || !seat.isAttached()) {
            // Fallback
            Location mloc = entity.getLocation();
            mloc.setYaw(FaceUtil.faceToYaw(getDirection()));
            mloc.setPitch(0.0f);
            return MathUtil.move(mloc, getProperties().getExitOffset().getPosition());
        } else {
            // Use seat
            return seat.getEjectPosition(passenger);
        }
    }

    /**
     * Gets the absolute world coordinates and orientation of a passenger of this Minecart.
     * 
     * @param passenger
     * @return passenger position
     */
    public Location getPassengerLocation(Entity passenger) {
        CartAttachmentSeat seat = this.getAttachments().findSeat(passenger);

        if (seat == null) {
            // Fallback
            Location mloc = entity.getLocation();
            mloc.setYaw(FaceUtil.faceToYaw(getDirection()));
            mloc.setPitch(0.0f);
            return mloc;
        } else {
            // Use seat
            return seat.getPosition(passenger);
        }
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
     * direction.
     *
     * @return True if inverted, False if not
     * @deprecated Use {@link #isOrientationInverted()} instead
     */
    @Deprecated
    public boolean isYawInverted() {
        return this.isOrientationInverted();
    }

    /*
     * Block functions
     */
    public Block getBlock(int dx, int dy, int dz) {
        IntVector3 pos = getBlockPos();
        return entity.getWorld().getBlockAt(pos.x + dx, pos.y + dy, pos.z + dz);
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
            double baseSpeed = Math.min(entity.vel.length(), entity.getMaxSpeed()) / this.group.getUpdateSpeedFactor();
            return Math.min(baseSpeed, group.getObstacleTracker().getSpeedLimit());
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
        force = Math.min(1.0, force); // Limit to a pushing force of 1.0 to avoid trouble

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
        force = Math.min(1.0, force); // Limit to a pushing force of 1.0 to avoid trouble

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
            loc.getWorld().playEffect(loc, org.bukkit.Effect.SMOKE, 0);
        }
        WorldUtil.playSound(loc, SoundEffect.EXTINGUISH, 1.0f, 2.0f);
    }

    /**
     * Checks if this minecart is dead, and throws an exception if it is
     *
     * @throws MemberMissingException
     */
    public void checkMissing() throws MemberMissingException {
        if (entity == null) {
            throw new MemberMissingException();
        } else if (entity.isRemoved()) {
            this.onDie(true);
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
        if (ignoreVelocity || motionLengthSq <= 1e-20) {
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
        state.setRailPiece(RailPiece.createWorldPlaceholder(railLookup()));
        state.setMember(this);

        // Initially try going into the direction that leads away from the
        // cart behind, or towards the cart in front. This is usually the
        // right direction to go.
        state.position().setMotion(this.calcMotionVector(false));
        state.position().setLocation(entity.getLocation());
        return RailType.loadRailInformation(state);
    }

    /**
     * Gets the World Rail Lookup used by this Member, for the current World it is
     * on. Updates automatically.
     *
     * @return World Rail Lookup
     */
    public WorldRailLookup railLookup() {
        WorldRailLookup result = this.railLookup;
        if (!result.isValidForWorld(entity.getWorld())) {
            result = this.railLookup = RailLookup.forWorld(entity.getWorld());
        }
        return result;
    }

    /**
     * Looks at the current position information and attempts to discover any rails
     * at these positions. The movement of the minecart is taken into account. If
     * derailed, the rail type of the state is set to NONE.
     * 
     * @return rail state
     */
    public RailState discoverRail() {
        /* Timings: discoverRail (Train Physics, RailLogic) */
        {
            // Store motion vector in state
            RailState state = new RailState();
            state.setMember(this);
            boolean result = this.fillRailInformation(state);
            if (!result) {
                // Note: Cannot use world placeholder, because then the rail block is null
                //       Also cannot set to NONE, because then there is no world.
                state.setRailPiece(RailPiece.create(RailType.NONE, state.railBlock(), state.railLookup()));
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
     * Snaps the minecart onto a rail path, preserving the movement direction.
     * Can be used in rail logic pre/post-move to adjust and correct
     * position on the path.
     * 
     * @param path The path to snap this member onto
     */
    public void snapToPath(RailPath path) {
        if (!path.isEmpty()) {
            RailPath.Position pos = RailPath.Position.fromPosDir(entity.loc.vector(), entity.getVelocity());
            path.move(pos, this.getBlock(), 0.0);
            this.snapToPosition(pos);
        }
    }

    /**
     * Snaps the minecart onto a rail path position, using the new movement direction
     * stored in the position.
     *
     * @param position The positiion to snap this member to
     */
    public void snapToPosition(RailPath.Position position) {
        snapToPosition(position, false);
    }

    private void snapToPosition(RailPath.Position position, boolean invertedMotion) {
        position.assertAbsolute();
        double velocity = entity.vel.length();
        if (invertedMotion) {
            velocity = -velocity;
        }
        entity.setPosition(position.posX, position.posY, position.posZ);
        entity.vel.set(position.motX * velocity,
                       position.motY * velocity,
                       position.motZ * velocity);
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
     * Gets the World and coordinates that this MinecartMember was first at after
     * derailing from the rails. Returns a <i>null</i> Location if this member
     * is currently railed.
     *
     * @return First known position after derailing. May not be the actual position
     *         if the train unloaded or reloaded after a server restart.
     *         Is <i>null</i> if this member is currently railed.
     */
    public Location getFirstKnownDerailedPosition() {
        return this.firstKnownDerailedPosition;
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

    /**
     * Sets the direction property of this minecart to the forward orientation
     *
     * @deprecated Does not take flipped state into account and shouldn't be used
     */
    @Deprecated
    public void setDirectionForward() {
        setDirectionForward(false);
    }

    /**
     * Sets the direction property of this minecart to the forward orientation
     *
     * @param flipped Whether the forward-orientation is flipped 180 degrees compared to the
     *                movement direction
     */
    public void setDirectionForward(boolean flipped) {
        this.directionFrom = this.directionTo = null;
        this.direction = Util.vecToFace(this.getOrientationForward(), true);
        if (flipped) {
            this.direction = this.direction.getOppositeFace();
        }
    }

    /**
     * Reverses the movement direction of the cart.
     * Called from MinecartGroup.
     */
    void reverseDirection() {
        entity.vel.multiply(-1.0);
        if (direction != null) {
            direction = direction.getOppositeFace();
        }
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
        // Remember the original direction (flip it) when the train is not moving
        RailState state = tracker.getState();
        if (this.direction == null ||
            this.entity.vel.lengthSquared() > 1e-10 ||
            state.position().motDot(this.direction) >= 0.0)
        {
            this.direction = state.position().getMotionFaceWithSubCardinal();
        } else {
            this.direction = state.position().getMotionFaceWithSubCardinal().getOppositeFace();
        }

        // TO direction is simply the enter face in the opposite direction
        RailState state_inv = state.clone();
        state_inv.position().invertMotion();
        state_inv.initEnterDirection();
        this.directionTo = state_inv.enterFace().getOppositeFace();
    }

    @Override
    public boolean onDamage(DamageSource damagesource, double damage) {
        if (entity.isRemoved()) {
            return false;
        }
        if (damagesource.toString().equals("fireworks")) {
            return false; // Ignore firework damage (used for cosmetics)
        }

        final Entity damager = damagesource.getEntity();

        // Note: When a player is sprinting and damaging the Minecart, or uses
        //       a weapon with knockback, the minecart is given a 'push'.
        //       When collision with this entity/player is disabled, this is undesirable.
        //       To disable this behavior, we return false from this method, causing
        //       all the extra 'knockback' code to be skipped internally. There is no
        //       Bukkit event we can handle to disable this otherwise.
        boolean executePostLogic = true;
        if (damager instanceof HumanEntity) {
            ItemStack itemInMainHand = HumanHand.getItemInMainHand((HumanEntity) damager);
            boolean willDoKnockback = false;
            if (itemInMainHand != null &&
                    itemInMainHand.hasItemMeta() &&
                    itemInMainHand.getItemMeta().hasEnchant(Enchantment.KNOCKBACK)
            ) {
                willDoKnockback = true;
            } else if (damager instanceof Player && ((Player) damager).isSprinting()) {
                willDoKnockback = true;
            }
            if (willDoKnockback) {
                if (this.isUnloaded()) {
                    executePostLogic = false; // better safe than sorry
                } else if (!this.getGroup().getProperties().getCollisionMode(damager).permitsKnockback()) {
                    executePostLogic = false;
                }
            }
        }

        try {
            // Call CraftBukkit event
            VehicleDamageEvent event = new VehicleDamageEvent(entity.getEntity(), damager, damage);
            if (CommonUtil.callEvent(event).isCancelled()) {
                return executePostLogic;
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
                    return executePostLogic;
                }

                // Spawn drops and die
                for (ItemStack stack : drops) {
                    entity.spawnItemDrop(stack, 0.0F);
                }
                this.onDie(true);
            } else {
                // Select the Minecart for editing otherwise
                if (damager instanceof Player) {
                    traincarts.getPlayer((Player) damager).editMember(this);
                }
            }
        } catch (Throwable t) {
            traincarts.handle(t);
        }
        return executePostLogic;
    }

    @Override
    public void onDie(boolean killed) {
        try {
            if (!entity.isRemoved() || !this.died) {
                // Before we actually die, eject passengers and release the signs
                // This must be done while the entity is still "alive"
                boolean cancelDrops = false;
                if (!this.isUnloaded()) {
                    // Note: No getGroup() calls are allowed here!
                    // They may create new groups!
                    cancelDrops = !this.getProperties().getSpawnItemDrops();
                    if (entity.hasPassenger()) {
                        this.eject();
                    }
                    if (this.group != null) {
                        this.getSignTracker().clear();
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

                {
                    boolean cancelDropsOriginal = TCListener.cancelNextDrops;
                    TCListener.cancelNextDrops |= cancelDrops;
                    try {
                        super.onDie(killed);
                    } finally {
                        TCListener.cancelNextDrops = cancelDropsOriginal;
                    }
                }
                this.died = true;
            }
        } catch (Throwable t) {
            traincarts.handle(t);
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

        CollisionMode mode = this.getGroup().getProperties().getCollisionMode(e);
        if (mode == CollisionMode.CANCEL) {
            return false;
        }

        // Verify that the entity is actually inside the bounding box of this entity
        // This involves a complicated rotated box intersection test
        if (!this.isModelIntersectingWith(e)) {
            return false;
        }

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

    // This prevents the player bumping the cart sideways, causing the
    // rail physics to think the minecart is taking a curve
    @Override
    public void onEntityBump(org.bukkit.entity.Entity e) {
        // Note: required, is in original implementation also
        VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent(entity.getEntity(), e);
        if (CommonUtil.callEvent(collisionEvent).isCancelled()) {
            return;
        }

        // When bumping with other minecarts we should technically do the whole vanilla train mechanic
        // Instead for simplicity's sake we're executing fairly vanilla behavior
        if (e instanceof Minecart) {
            Vector pos_diff = e.getLocation().subtract(entity.getLocation()).toVector();
            double len_sq = pos_diff.lengthSquared();
            if (len_sq >= 1.0e-4) {
                double n = MathUtil.getNormalizationFactorLS(len_sq);
                pos_diff.multiply(n);
                if (n > 1.0) {
                    n = 1.0;
                }
                pos_diff.multiply(0.05 * n);
                applyBump(e, pos_diff);
                applyBump(entity.getEntity(), pos_diff.multiply(-1.0));
            }

            return;
        }

        Vector pos_diff = e.getLocation().subtract(entity.getLocation()).toVector();
        if (this.isDerailed()) {
            pos_diff.setY(0.0);
        }
        double len_sq = pos_diff.lengthSquared();
        if (len_sq >= 1.0e-4) {
            double n = MathUtil.getNormalizationFactorLS(len_sq);
            pos_diff.multiply(n);
            if (n > 1.0) {
                n = 1.0;
            }
            pos_diff.multiply(0.05 * n);

            // Apply minor pushback to the entity pushing the minecart
            applyBump(e, pos_diff.clone().multiply(0.25));

            pos_diff.multiply(-1.0);
            if (!this.isDerailed()) {
                // When railed, the bump vector must be aligned with the current movement vector
                // Invert movement vector based on what direction the player is pushing
                Vector railMot = this.getRailTracker().getRail().state.motionVector().normalize();
                if (railMot.dot(pos_diff) < 0.0) {
                    railMot.multiply(-1.0);
                }
                pos_diff = railMot.multiply(pos_diff.multiply(railMot).length());
            }
            applyBump(entity.getEntity(), pos_diff);
        }
    }

    private static void applyBump(Entity entity, Vector v) {
        // Impulse()
        EntityHandle eh = EntityHandle.fromBukkit(entity);
        eh.setMot(eh.getMotX() + v.getX(), eh.getMotY() + v.getY(), eh.getMotZ() + v.getZ());
        eh.setPositionChanged(true);
    }

    @Override
    public boolean onBlockCollision(org.bukkit.block.Block hitBlock, BlockFace hitFace) {
        /* Timings: onBlockCollision  (Train Physics, Post-Move) */
        {
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
            return this.isModelIntersectingWith_impl(other.entity.getWrappedHandle())
                    && other.isModelIntersectingWith_impl(this.entity.getWrappedHandle());
        } else {
            return this.isModelIntersectingWith_impl(entity);
        }
    }

    private final boolean isModelIntersectingWith_impl(Entity entity) {
        return isModelIntersectingWith_impl(EntityHandle.fromBukkit(entity));
    }

    private final boolean isModelIntersectingWith_impl(EntityHandle entityHandle) {
        // We lack a proper bounding box collision test
        // Instead we do a poor man's method of probing various points on the entity
        AxisAlignedBBHandle aabb = entityHandle.getBoundingBox();
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
     * Calculates the distance on the rails between this member and another member of the
     * same train as this train. If the member ahead isn't part of the same train, then
     * the rails are walked to calculate the distance that way.
     *
     * @return Distance between this member and the member ahead.
     *         Returns NaN if no valid path can be detected or either member is derailed.
     */
    public double calculateRailDistanceToMemberAhead(MinecartMember<?> memberAhead) {
        // If either is derailed, just use the as-the-crow-flies distance
        if (this.isDerailed() || memberAhead.isDerailed()) {
            return Double.NaN;
        }

        // Walks along the tracked rail information
        TrackedRailWalker walker = this.getRailTracker().getTrackedRailWalker();

        // Target position and distance to reach it
        // At most allow for walking 5x the amount of distance before failing
        final RailPath.Position targetPos = memberAhead.getRailTracker().getRail().state.position();
        double distanceRemaining = walker.position().distance(targetPos);
        double distanceLimit = 5.0 * distanceRemaining;

        // Walk from the from cart to the to cart, making use of the tracked rails if possible
        // If for whatever reason we run out, fall back to walking the track itself
        // Can't do this if the members are part of different groups
        double distanceMoved = 0.0;
        if (this.getGroup() == memberAhead.group) {
            double moved;
            while ((moved = walker.move(distanceRemaining)) > 0.0) {
                distanceMoved += moved;

                // Refresh remaining distance after the move, check reached goal
                distanceRemaining = walker.position().distance(targetPos);
                if (distanceRemaining < 1e-10) {
                    return distanceMoved;
                }

                // Break out if we've been moving for too long a distance
                if (distanceMoved > distanceLimit) {
                    // Can't find the other cart. Abort.
                    return Double.NaN;
                }
            }
        }

        // Couldn't find the other minecart for whatever reason. Maybe the rail information is outdated?
        // Try to use a track walking point instead, with a sensible limit
        walker.state().initEnterDirection();
        TrackWalkingPoint p = new TrackWalkingPoint(walker.state());
        p.skipFirst();
        p.movedTotal = distanceMoved;
        int zeroMoveLimit = 100;
        while (p.move(distanceRemaining)) {
            distanceRemaining = p.state.position().distance(targetPos);
            if (distanceRemaining < 1e-10) {
                return p.movedTotal;
            }

            // Break out if we've been moving for too long a distance
            if (p.movedTotal > distanceLimit) {
                break;
            }

            // Break out if we've been 'moving' 0 distance too many times
            // Here just to prevent infinite loops, dunno if it's needed at all.
            if (p.moved < 1e-10 && --zeroMoveLimit == 0) {
                break;
            }
        }

        // No path between the members - fallback distance
        return Double.NaN;
    }

    /**
     * Gets the inventory view of all player passengers of this Minecart.
     *
     * @return the passengers Player inventory
     */
    public Inventory getPlayerInventory() {
        Inventory[] source = this.getEntity().getPlayerPassengers().stream()
                .map(Player::getInventory)
                .toArray(Inventory[]::new);
        return new MergedInventory(source);
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
     * rotation specified. The original yaw and pitch of the entity is maintained.
     *
     * @param offset to teleport to
     */
    public void eject(Vector offset) {
        eject(new Location(entity.getWorld(), entity.loc.getX() + offset.getX(), entity.loc.getY() + offset.getY(),
                entity.loc.getZ() + offset.getZ(), 0.0f, 0.0f), true);
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
        eject(to, false);
    }

    /**
     * Ejects the passenger of this Minecart and teleports him to the location
     * specified
     *
     * @param to location to eject/teleport to
     * @param retainEntityRotation Whether to retain the original yaw and pitch of the
     *                             rotation (camera view) of the entity. When false, does
     *                             not change where players look.
     */
    public void eject(final Location to, boolean retainEntityRotation) {
        if (entity.hasPassenger()) {
            final List<Entity> oldPassengers = new ArrayList<>(entity.getPassengers());
            TCSeatChangeListener.exemptFromEjectOffset.addAll(oldPassengers);
            this.eject();
            if (!oldPassengers.isEmpty()) {
                CommonUtil.nextTick(() -> {
                    for (Entity oldPassenger : oldPassengers) {
                        if (retainEntityRotation) {
                            Util.teleportPosition(oldPassenger, to);
                        } else {
                            EntityUtil.teleport(oldPassenger, to);
                        }
                    }
                });
            }
            for (Entity oldPassenger : oldPassengers) {
                EntityUtil.teleportNextTick(oldPassenger, to);
            }
            TCSeatChangeListener.exemptFromEjectOffset.removeAll(oldPassengers);
        }
    }

    /**
     * Puts a passenger inside a seat of this Minecart Member, ignoring enter rules or permissions.
     * 
     * @param passenger
     * @return True if the passenger was added
     */
    public boolean addPassengerForced(Entity passenger) {
        try {
            this.enterForced.add(passenger);
            return this.entity.addPassenger(passenger);
        } finally {
            this.enterForced.remove(passenger);
        }
    }

    /**
     * Gets whether a passenger being added to this Minecart was forced.
     * Internal use only.
     * 
     * @param entity
     * @return True if forced
     */
    public boolean isPassengerEnterForced(Entity entity) {
        return this.enterForced.contains(entity);
    }

    public boolean connect(MinecartMember<?> with) {
        return this.getGroup().connect(this, with);
    }

    @Override
    public void onPropertiesChanged() {
        this.getSignTracker().update();

        // Enable/disable collision handling to improve performance
        if (this.group != null) {
            CollisionOptions collision = this.group.getProperties().getCollision();
            setEntityCollisionEnabled(collision.collidesWithEntities());
            setBlockCollisionEnabled(collision.blockMode() == CollisionMode.DEFAULT);
        }
    }

    public boolean onModelChanged(AttachmentModel model) {
        if (entity == null) {
            return false;
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
        return true;
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
        setUnloaded((entity == null) || entity.isRemoved() ||
                traincarts.getOfflineGroups().containsMinecart(entity.getUniqueId()));
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
        if (lastLocation == null || lastLocation.getWorld() != entity.getWorld()) {
            lastLocation = entity.getLocation();
        }
        if (lastLocationSync == null) {
            lastLocationSync = lastLocation;
        }
    }

    /**
     * Reads input from passengers of this Minecart to perform manual movement of
     * the minecart, if enabled
     */
    public void updateManualMovement() {
        // Vehicle steering input from living entity passengers
        // This is only allowed when the property is enabled and our velocity < 0.1
        // blocks/tick (0.01 squared)
        boolean player_manual = getGroup().getProperties().isManualMovementAllowed();
        boolean mob_manual = getGroup().getProperties().isMobManualMovementAllowed();
        if ((player_manual || mob_manual) && entity.vel.lengthSquared() < 0.01 && !this.isDerailed()) {
            for (Entity passenger : entity.getPassengers()) {
                if (!(passenger instanceof LivingEntity)) {
                    continue;
                }
                if (!((passenger instanceof Player) ? player_manual : mob_manual)) {
                    continue;
                }
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
        dieIfOutsideWorldBorder();

        // reset fall distance
        if (!this.isDerailed()) {
            entity.setFallDistance(0.0f);
        }

        // Perform rails logic
        this.railTrackerMember.getRailLogic().onPreMove(this);

        // Refresh last-update direction and block information
        this.getRailTracker().updateLast();

        // Update the entity shape
        entity.setPosition(entity.loc.getX(), entity.loc.getY(), entity.loc.getZ());
    }

    private void dieIfOutsideWorldBorder() {
        double limitSq = TCConfig.worldBorderKillDistance * TCConfig.worldBorderKillDistance;
        if (WorldUtil.getBlockBorder(getWorld()).distanceSquared(entity.loc.vector()) > limitSq) {
            this.onDie(true);
            throw new MemberMissingException();
        }
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

        // Before executing movement logic, check if we should check for block activation at all
        // Only used when enabled in the configuration
        if (TCConfig.optimizeBlockActivation) {
            boolean enabled = false;
            for (TrackedRail rail : this.getGroup().getRailTracker().getRailInformation()) {
                if (rail.member == this && rail.state.railPiece().hasBlockActivation()) {
                    enabled = true;
                    break;
                }
            }
            this.setBlockActivationEnabled(enabled);
        }

        // Detect when the train starts and stops moving, to play a configured drive sound
        if (wasMoving != (vel.lengthSquared() >= 1e-10)) {
            wasMoving = !wasMoving;
            if (wasMoving) {
                String effectName = this.getProperties().getDriveSound();
                if (effectName != null && !effectName.isEmpty()) {
                    Effect effect = new Effect();
                    effect.parseEffect(effectName);
                    effect.volume = 100;

                    // Play the sound effect in first person, close to the player
                    for(Player p : this.entity.getPlayerPassengers()) {
                        effect.play(p);
                    }

                    // Play the sound effect for all players nearby
                    for (Entity nearby : entity.getNearbyEntities(64.0)) {
                        if (nearby instanceof Player && !entity.isPassenger(nearby)) {
                            effect.play(entity.getLocation(), (Player) nearby);
                        }
                    }
                }
            }

            // Notify signs about the change to support velocity==0 statement triggering
            this.onPropertiesChanged();
        }

        // Save the state prior to moving, then move the entity the required distance
        // During this movement, entity/block collisions are handled
        RailState preMoveState;
        boolean preMoveInverted = false;
        /* Timings: onPhysicsPostMove:onMove  (Train Physics, Post-Move) */
        {
            preMoveState = this.railTrackerMember.getRail().state.clone();
            if (preMoveState.position().motDot(vel) < 0.0) {
                preMoveState = preMoveState.cloneAndInvertMotion();
                preMoveInverted = true;
            }

            /*
            double distanceFromPath = preMoveState.loadRailLogic().getPath().distanceSquared(preMoveState.railPosition());
            if (distanceFromPath > 0.1) {
                plugin.log(Level.INFO, "TOO BIG DISTANCE: " + distanceFromPath);
            }
            */
            onMove(MoveType.SELF, vel.getX(), vel.getY(), vel.getZ());
        }

        this.checkMissing();

        // The train has been moved. Check the actual distance moved relative
        // to the origin, and move the same distance by walking on the track path
        // We might be iterating over multiple rail paths while doing this
        /* Timings: onPhysicsPostMove:onPostMove  (Train Physics, Post-Move, RailLogic) */
        {
            boolean moveSuccessful = false;

            // If we were on rails before, attempt to move the full moved distance
            if (preMoveState != null && preMoveState.railType() != RailType.NONE) {
                final double distanceToMove = preMoveState.position().distance(entity.loc);
                final TrackWalkingPoint p = new TrackWalkingPoint(preMoveState);
                do {
                    // Set MinecartMember position to the current walked point,
                    // and then call onPostMove() to let the logic/type do whatever with it.
                    this.snapToPosition(p.state.position(), preMoveInverted);
                    p.currentRailLogic.onPostMove(this);
                    p.state.railType().onPostMove(this);
                } while (p.moveStep(distanceToMove - p.movedTotal));

                if (moveSuccessful = (p.failReason == TrackWalkingPoint.FailReason.LIMIT_REACHED)) {
                    // Moving on rails to a new location
                    // Now execute onPostMove() for the current rail
                    this.snapToPosition(p.state.position(), preMoveInverted);
                    p.currentRailLogic.onPostMove(this);
                    p.state.railType().onPostMove(this);
                } else {
                    // plugin.log(Level.INFO, "[Debug] Failed to move on track: " + p.failReason);

                    // Moved some portion of the track. Assume we're moving the
                    // rest of it forwards in a straight line.
                    // Assume all the remaining distance traveled is 'air'
                    double remaining = distanceToMove - p.movedTotal;
                    if (remaining > 1e-10) {
                        p.state.position().move(remaining);
                    }

                    // Place the cart here
                    this.snapToPosition(p.state.position(), preMoveInverted);
                }
            }

            // If for some reason move was impossible (rail end found), start completely
            // from scratch. This identifies rails at the current position, and ignores
            // the movement start position. This makes sure 'hopping' over air works
            // properly.
            if (!moveSuccessful) {
                RailState newRailState = this.discoverRail();
                if (newRailState.railType() != RailType.NONE) {
                    this.snapToPosition(newRailState.position(), preMoveInverted);
                }
            }
        }

        // Update manual movement from player input
        updateManualMovement();

        // Post-move logic
        this.doPostMoveLogic();
        if (!this.isDerailed()) {
            // Slowing down of minecarts
            TrainProperties trainProp = this.getGroup().getProperties();
            if (trainProp.isSlowingDown(SlowdownMode.FRICTION) && entity.getMaxSpeed() > 0.0) {
                double factor;
                if (entity.hasPassenger() || !entity.isSlowWhenEmpty() || !TCConfig.slowDownEmptyCarts) {
                    factor = TCConfig.slowDownMultiplierNormal;
                } else {
                    factor = TCConfig.slowDownMultiplierSlow;
                }
                factor = Math.max(0.0, 1.0 + trainProp.getFriction() * (factor - 1.0));
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

        // Track last location and update the sync one
        Location from = lastLocation;
        Location to = entity.getLocation();
        if (from == null || from.getWorld() != to.getWorld()) {
            lastLocation = from = to; // Force correct it
        }
        lastLocationSync = from;
        lastLocation = to;
        entity.last.set(from);

        // Perform some (CraftBukkit) events
        Vehicle vehicle = entity.getEntity();

        /* Timings: onPhysicsPostMove:VehicleUpdateEvent  (Train Physics, Post-Move, Bukkit) */
        {
            CommonUtil.callEvent(new VehicleUpdateEvent(vehicle));
        }

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            // Execute move events
            /* Timings: onPhysicsPostMove:VehicleMoveEvent  (Train Physics, Post-Move, Bukkit) */
            {
                CommonUtil.callEvent(new VehicleMoveEvent(vehicle, from, to));
            }

            // Execute signs MEMBER_MOVE
            /* Timings: onPhysicsPostMove:SignMemberMove  (Train Physics, Post-Move, Sign Tracker) */
            {
                for (SignTracker.ActiveSign sign : this.getSignTracker().getActiveTrackedSigns().cloneAsIterable()) {
                    sign.executeEventForMember(SignActionType.MEMBER_MOVE, this);
                }
            }
        }

        // If this member is derailed, track the first known position it derailed
        // If not derailed, reset it again
        if (!this.isDerailed()) {
            this.firstKnownDerailedPosition = null;
        } else if (this.firstKnownDerailedPosition == null ||
                   this.firstKnownDerailedPosition.getWorld() != entity.getWorld()
        ) {
            this.firstKnownDerailedPosition = entity.getLocation();
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
        // Make sure at all times this member has a group, if not unloaded
        // We don't do any actual updates here because paper RUINS it.
        if (!this.isUnloaded()) {
            this.getGroup();
        }

        // The server breaks entity last location by resetting it. Restore that info here.
        if (lastLocationSync != null && lastLocationSync.getWorld() == entity.getWorld()) {
            entity.last.set(lastLocationSync);
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
     * @param newyaw      New yaw to set
     * @param newpitch    New pitch to set
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
        if (!isSingle()) {
            name += " (Train)";
        }
        return name;
    }

    /**
     * Gets whether this Minecart Member can be taken by players when they log off, or when the server is shut down.
     * Ejects players when they log off, if this returns false.
     */
    public boolean isPlayerTakable() {
        if (this.isUnloaded()) {
            return this.unloadedLastPlayerTakable;
        } else {
            return this.isSingle() && this.getGroup().getProperties().isPlayerTakeable();
        }
    }

    /**
     * Gets the number of seats still available for new entities to enter the
     * minecart
     * 
     * @return number of available seats
     */
    public int getAvailableSeatCount(Entity passenger) {
        // Sync passengers first, which is for now done by the network controller
        @SuppressWarnings("deprecation")
        MinecartMemberNetwork network = CommonUtil.tryCast(entity.getNetworkController(), MinecartMemberNetwork.class);
        if (network != null) {
            network.syncPassengers();
        }

        // Ask attachments controller for the number of available seats
        return this.getAttachments().getAvailableSeatCount(passenger);
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
    public OrientedBoundingBox getHitBox() {
        final Quaternion orientation = this.getOrientation();
        final Vector position = this.getWheels().getPosition().clone();
        final double height = 1.0;

        // Bounding box position is relative to the center of the box
        // We want it to be relative to the bottom. To make that happen,
        // offset position upwards by half the height.
        position.add(orientation.upVector().multiply(0.5 * height));

        OrientedBoundingBox box = new OrientedBoundingBox();
        box.setPosition(position);
        box.setSize(1.0, height, entity.getWidth());
        box.setOrientation(orientation);
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
            this.lastRailRefreshDirection = entity.vel.vector();
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

    @Override
    public List<String> getAnimationNames() {
        return this.getAttachments().isAttached() ?
                this.getAttachments().getRootAttachment().getAnimationNamesRecursive()
                : Collections.emptyList();
    }

    @Override
    public Set<String> getAnimationScenes(String animationName) {
        return this.getAttachments().isAttached() ?
                this.getAttachments().getRootAttachment().getAnimationScenesRecursive(animationName)
                : Collections.emptySet();
    }

    /**
     * Plays an animation for a single attachment node for this minecart. Only the
     * attachment at the targetPath will play the animation.
     * 
     * @param targetPath
     * @param options    defining the animation to play
     * @return True if the attachment node and animation could be found
     */
    @Override
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
    @Override
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
    @Override
    public boolean playNamedAnimation(String name) {
        return AnimationController.super.playNamedAnimation(name);
    }

    /**
     * Plays an animation using the animation options specified for this minecart.
     * All attachments storing an animation with the options' name will play.
     * 
     * @param options for the animation
     * @return True if an animation was found and started
     */
    @Override
    public boolean playNamedAnimation(AnimationOptions options) {
        return this.getAttachments().isAttached() &&
                this.getAttachments().getRootAttachment().playNamedAnimationRecursive(options);
    }

    /**
     * Looks up an attachment of the Minecart by target path. The path is a series of indices to
     * get to that particular attachment in the tree hierarchy.
     * 
     * @param targetPath
     * @return Attachment at this path, or null if not found
     */
    public Attachment findAttachment(int[] targetPath) {
        if (this.getAttachments().isAttached()) {
            return this.getAttachments().getRootAttachment().findChild(targetPath);
        } else {
            return null;
        }
    }
}
