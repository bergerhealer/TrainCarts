package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.controller.EntityController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketFields;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockInfo;
import com.bergerkiller.bukkit.common.wrappers.DamageSource;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.RailType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.actions.MemberActionLaunch;
import com.bergerkiller.bukkit.tc.actions.MemberActionLaunchDirection;
import com.bergerkiller.bukkit.tc.actions.MemberActionLaunchLocation;
import com.bergerkiller.bukkit.tc.actions.MemberActionWaitDistance;
import com.bergerkiller.bukkit.tc.actions.MemberActionWaitLocation;
import com.bergerkiller.bukkit.tc.actions.MemberActionWaitOccupied;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.MemberBlockChangeEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.railphysics.RailLogic;
import com.bergerkiller.bukkit.tc.railphysics.RailLogicGround;
import com.bergerkiller.bukkit.tc.railphysics.RailLogicVertical;
import com.bergerkiller.bukkit.tc.railphysics.RailLogicVerticalSlopeDown;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.SoundLoop;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import com.bergerkiller.bukkit.tc.utils.TrackMap;

public abstract class MinecartMember<T extends CommonMinecart<?>> extends EntityController<T> {
	public static final double GRAVITY_MULTIPLIER = 0.04;
	public static final double VERTRAIL_MULTIPLIER = 0.02;
	public static final double VERT_TO_SLOPE_MIN_VEL = 8.0 * VERTRAIL_MULTIPLIER;
	public static final double SLOPE_VELOCITY_MULTIPLIER = 0.0078125;
	public static final double POWERED_RAIL_START_BOOST = 0.02;
	public static final double MIN_VEL_FOR_SLOPE = 0.05;

	private static List<Block> tmpblockbuff = new ArrayList<Block>();
	private BlockFace direction;
	private BlockFace directionTo;
	private BlockFace directionFrom = BlockFace.SELF;
	protected MinecartGroup group;
	protected boolean died = false;
	private final ToggledState needsUpdate = new ToggledState();
	private final ToggledState forcedBlockUpdate = new ToggledState(true);
	private final ToggledState railActivated = new ToggledState(false);
	private int teleportImmunityTick = 0;
	private boolean ignoreAllCollisions = false;
	private int collisionEnterTimer = 0;
	private CartProperties properties;
	private Map<UUID, AtomicInteger> collisionIgnoreTimes = new HashMap<UUID, AtomicInteger>();
	private Set<Block> activeSigns = new LinkedHashSet<Block>();
	private List<DetectorRegion> activeDetectorRegions = new ArrayList<DetectorRegion>(0);
	protected boolean unloaded = false;
	public boolean vertToSlope = false;
	protected SoundLoop<?> soundLoop;

	@Override
	public void onAttached() {
		super.onAttached();
		this.moveinfo = new MoveInfo(this);
		this.soundLoop = new SoundLoop<MinecartMember<?>>(this);
		this.prevcx = entity.loc.x.chunk();
		this.prevcz = entity.loc.z.chunk();
		this.direction = FaceUtil.yawToFace(entity.loc.getYaw());
		this.directionFrom = this.directionTo = FaceUtil.yawToFace(entity.loc.getYaw(), false);
	}

	/*
	 * General getters and setters
	 */
 	public CartProperties getProperties() {
 		if (this.properties == null) {
 			this.properties = CartProperties.get(this);
 		}
 		return this.properties;
 	}

	/**
	 * Sets the group of this Minecart, removing this member from the previous group<br>
	 * Only called by internal methods (as it relies on group adding)
	 * 
	 * @param group to set to
	 */
	protected void setGroup(MinecartGroup group) {
		if (this.group != null && this.group != group) {
			this.group.removeSilent(this);
		}
		this.unloaded = false;
		this.group = group;
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
 			MinecartGroup.create(this);
 		}
 		return this.group;
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
 			return this.entity.isDead() ? -1 : 0;
 		} else {
 			return this.group.indexOf(this);
 		}
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
		if (this.getGroup() == null) return new MinecartMember<?>[0];
		int index = this.getIndex();
		if (index == -1) return new MinecartMember<?>[0];
		if (index > 0) {
			if (index < this.getGroup().size() - 1) {
				return new MinecartMember<?>[] {this.getGroup().get(index - 1), this.getGroup().get(index + 1)};
			} else {
				return new MinecartMember<?>[] {this.getGroup().get(index - 1)};
			}
		} else if (index < this.getGroup().size() - 1) {
			return new MinecartMember<?>[] {this.getGroup().get(index + 1)};
		} else {
			return new MinecartMember<?>[0];
		}
	}
 

 	/*
 	 * Active signs
 	 */
	public boolean isActiveSign(Block signblock) {
		if (signblock == null) return false;
		return this.activeSigns.contains(signblock);
	}
	public boolean addActiveSign(Block signblock) {
		if (this.activeSigns.add(signblock)) {
			if (entity.isDead()) return true;
			SignActionEvent info = new SignActionEvent(signblock, this);
			SignAction.executeAll(info, SignActionType.MEMBER_ENTER);
			if (entity.isDead()) return true;
			this.getGroup().setActiveSign(info, true);
			return true;
		} else {
			return false;
		}
	}
	private void handleActiveSignRemove(Block signblock) {
		SignAction.executeAll(new SignActionEvent(signblock, this), SignActionType.MEMBER_LEAVE);
		// This sign is not present in other members of the group?
		for (MinecartMember<?> mm : this.getGroup()) {
			if (mm != this && mm.isActiveSign(signblock)) {
				// Active for another minecart - no group removal
				return;
			}
		}
		this.getGroup().setActiveSign(signblock, false);
	}
	public void removeActiveSign(Block signblock) {
		if (this.activeSigns.remove(signblock)) {
			handleActiveSignRemove(signblock);
		}
	}
	public void clearActiveSigns() {
		if (this.isUnloaded()) {
			return;
		}
		for (Block signblock : this.activeSigns) {
			handleActiveSignRemove(signblock);
		}
		this.activeSigns.clear();
	}
	public void clearActiveDetectors() {
		for (DetectorRegion region : this.activeDetectorRegions) {
			region.remove(this);
		}
		this.activeDetectorRegions.clear();
	}
	public Set<Block> getActiveSigns() {
		return this.activeSigns;
	}
	public boolean hasSign() {
		return !this.activeSigns.isEmpty();
	}
	public List<DetectorRegion> getActiveDetectorRegions() {
		return this.activeDetectorRegions;
	}

	/**
	 * Gets whether this Minecart is unloaded
	 * 
	 * @return True if it is unloaded, False if not
	 */
	public boolean isUnloaded() {
		return this.unloaded;
	}

	public boolean isInChunk(Chunk chunk) {
		return this.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
	}

	public boolean isInChunk(org.bukkit.World world, int cx, int cz) {
		if (world != entity.getWorld()) return false;
		if (Math.abs(cx - entity.loc.x.chunk()) > 2) return false;
		if (Math.abs(cz - entity.loc.z.chunk()) > 2) return false;
		return true;
	}

	public boolean isSingle() {
		return this.group == null || this.group.size() == 1;
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
	public Block getBlockRelative(BlockFace direction) {
		return this.getBlock(FaceUtil.add(direction, this.getDirection()));
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
	public double getForceSquared() {
		if (entity.isOnGround()) {
			return entity.vel.xz.lengthSquared();
		}
		return entity.vel.lengthSquared();
	}
	public double getForce() {
		return Math.sqrt(this.getForceSquared());
	}
	public double getForwardForce() {
		return this.getRailLogic().getForwardVelocity(this);
	}
	public void setForwardForce(double force) {
		this.getRailLogic().setForwardVelocity(this, force);
	}
	public void limitSpeed() {
		//Limits the velocity to the maximum
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

	/*
	 * Teleportation
	 */
	public void loadChunks() {
		WorldUtil.loadChunks(entity.getWorld(), entity.loc.x.chunk(), entity.loc.z.chunk(), 2);
	}
	public void teleport(Block railsblock) {
		this.teleport(railsblock.getLocation().add(0.5, 0.5, 0.5));
	}
	public void teleport(Location to) {
		boolean changedWorld = to.getWorld() != this.entity.getWorld();
		entity.setDead(true);
		// === Teleport - set unloaded to true and false again to prevent group unloading ===
		this.unloaded = true;
		EntityUtil.teleport(this.entity.getEntity(), to);
		this.unloaded = false;
		// =======================
		if (changedWorld) {
			//this.tracker = new MinecartMemberTrackerEntry(this);
			//WorldUtil.setTrackerEntry(this.getEntity(), this.tracker);
		}
		this.teleportImmunityTick = 10;
		entity.setDead(false);
		this.refreshBlockInformation();
	}

	/**
	 * Gets whether this Minecart and the passenger has immunity as a result of teleportation
	 * 
	 * @return True if it is immune, False if not
	 */
	public boolean isTeleportImmune() {
		return this.teleportImmunityTick > 0;
	}

	public boolean isCollisionIgnored(org.bukkit.entity.Entity entity) {
		MinecartMember<?> member = MinecartMemberStore.get(entity);
		if (member != null) {
			return this.isCollisionIgnored(member);
		}
		if (this.ignoreAllCollisions) {
			return true;
		}
		return collisionIgnoreTimes.containsKey(entity.getUniqueId());
	}
	public boolean isCollisionIgnored(MinecartMember<?> member) {
		if (this.ignoreAllCollisions || member.ignoreAllCollisions) {
			return true; 
		}
		return this.collisionIgnoreTimes.containsKey(member.entity.getUniqueId()) || 
				member.collisionIgnoreTimes.containsKey(this.entity.getUniqueId());
	}
	public void ignoreCollision(org.bukkit.entity.Entity entity, int ticktime) {
		collisionIgnoreTimes.put(entity.getUniqueId(), new AtomicInteger(ticktime));
	}

	/**
	 * Checks whether mobs/players are allowed to automatically (by collision) enter this Minecart
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
		this.collisionEnterTimer = TrainCarts.collisionReEnterDelay;
	}

	/*
	 * Scheduled Actions
	 */
	public <A extends Action> A addAction(A action) {
		return this.getGroup().addAction(action);
	}
	public MemberActionWaitDistance addActionWaitDistance(double distance) {
		return this.addAction(new MemberActionWaitDistance(this, distance));
	}
	public MemberActionWaitLocation addActionWaitLocation(Location location) {
		return this.addAction(new MemberActionWaitLocation(this, location));
	}
	public MemberActionWaitLocation addActionWaitLocation(Location location, double radius) {
		return this.addAction(new MemberActionWaitLocation(this, location, radius));
	}
	public MemberActionLaunch addActionLaunch(double distance, double targetvelocity) {
		return this.addAction(new MemberActionLaunch(this, distance, targetvelocity));
	}
	public MemberActionLaunchLocation addActionLaunch(Location destination, double targetvelocity) {
		return this.addAction(new MemberActionLaunchLocation(this, targetvelocity, destination));
	}
	public MemberActionLaunchLocation addActionLaunch(Vector offset, double targetvelocity) {
		return this.addActionLaunch(entity.getLocation().add(offset), targetvelocity);
	}
	public MemberActionLaunchDirection addActionLaunch(final BlockFace direction, double targetdistance, double targetvelocity) {
		return this.addAction(new MemberActionLaunchDirection(this, targetdistance, targetvelocity, direction));
	}
	public MemberActionWaitOccupied addActionWaitOccupied(int maxsize, long launchDelay, double launchDistance) {
		return this.addAction(new MemberActionWaitOccupied(this, maxsize, launchDelay, launchDistance));
	}

	/*
	 * Actions
	 */
	public void pushSideways(org.bukkit.entity.Entity entity) {
		this.pushSideways(entity, TrainCarts.pushAwayForce);
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
		loc.getWorld().playEffect(loc, Effect.EXTINGUISH, 0);
	}

	/**
	 * Checks if this minecart is dead, and throws an exception if it is
	 * 
	 * @throws MemberMissingException
	 */
	public void checkMissing() throws MemberMissingException {
		if (entity.isDead()) {
			this.onDie();
			throw new MemberMissingException();
		} else if (this.isUnloaded()) {
			throw new MemberMissingException();
		}
	}

	public IntVector3 getBlockPos() {
		return moveinfo.blockPos;
	}

	/**
	 * Gets the block this minecart is currently in, or driving on
	 * 
	 * @return Rail block or block at minecart position
	 */
	public Block getBlock() {
		return moveinfo.block;
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
		return this.moveinfo.railType == RailType.NONE;
	}
	/**
	 * Checks whether this minecart is currently traveling on a vertical rail
	 * 
	 * @return True if traveling vertically, False if not
	 */
	public boolean isOnVertical() {
		return this.getRailLogic() instanceof RailLogicVertical;
	}
	public RailLogic getPrevRailLogic() {
		return moveinfo.prevRailLogic;
	}
	public RailLogic getRailLogic() {
		if (moveinfo.railLogicSnapshotted) {
			return moveinfo.railLogic;
		} else {
			return RailLogic.get(this);
		}
	}
	public boolean hasBlockChanged() {
		return moveinfo.blockChanged();
	}
	public boolean isOnSlope() {
		return this.getRailLogic().isSloped();
	}
	public boolean isFlying() {
		return this.moveinfo.railType == RailType.NONE && !entity.isOnGround();
	}
	public boolean isMovingHorizontally() {
		return entity.isMovingHorizontally();
	}
	public boolean isMovingVerticalOnly() {
		return this.isMovingVertically() && !this.isMovingHorizontally();
	}
	public boolean isMovingVertically() {
		return Math.abs(MathUtil.wrapAngle(entity.loc.getPitch())) == 90f && (entity.vel.getY() > 0.001 || (entity.vel.getY() < -0.001 && !entity.isOnGround()));
	}
	public boolean isNearOf(MinecartMember<?> member) {
		double max = TrainCarts.maxCartDistance * TrainCarts.maxCartDistance;
		if (entity.loc.xz.distanceSquared(member.entity) > max) {
			return false;
		}
		if (this.isDerailed() || this.isOnVertical() || member.isDerailed() || member.isOnVertical()) {
			return Math.abs(entity.loc.getY() - member.entity.loc.getY()) <= max;
		}
		return true;
	}
	public boolean isHeadingTo(org.bukkit.entity.Entity entity) {
		return this.isHeadingTo(entity.getLocation());
	}
	public boolean isHeadingTo(IntVector3 location) {
		return MathUtil.isHeadingTo(this.entity.loc.offsetTo(location.x, location.y, location.z), entity.getVelocity());
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

		// If moving, use current direction, otherwise be flexible and allow both directions
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
	public static boolean isTrackConnected(MinecartMember<?> m1, MinecartMember<?> m2) {
		//Can the minecart reach the other?
		boolean m1moving = m1.isMoving();
		boolean m2moving = m2.isMoving();
		if (m1moving && m2moving) {
			if (!m1.isFollowingOnTrack(m2) && !m2.isFollowingOnTrack(m1)) return false;
		} else if (m1moving) {
			if (!m1.isFollowingOnTrack(m2)) return false;
		} else if (m2moving) {
			if (!m2.isFollowingOnTrack(m1)) return false;
		} else {
			if (!m1.isNearOf(m2)) return false;
			if (!TrackIterator.isConnected(m1.getBlock(), m2.getBlock(), false)) return false;
		}
		return true;
	}

	/*
	 * Directional functions
	 */
	public BlockFace getDirection() {
		return this.direction;
	}
	public BlockFace getDirectionFrom() {
		return this.directionFrom;
	}
	public BlockFace getDirectionTo() {
		return this.directionTo;
	}
	public BlockFace getRailDirection() {
		return this.getRailLogic().getDirection();
	}
	public int getDirectionDifference(BlockFace dircomparer) {
		return FaceUtil.getFaceYawDifference(this.getDirection(), dircomparer);
	}
	public int getDirectionDifference(MinecartMember<?> comparer) {
		return this.getDirectionDifference(comparer.getDirection());
	}
	public void updateDirection(Vector movement) {
		if (this.isOnVertical()) {
			this.directionTo = this.direction = Util.getVerticalFace(movement.getY() > 0.0);
		} else if (this.isFlying() && this.isMovingVerticalOnly()) {
			this.directionTo = this.direction = Util.getVerticalFace(movement.getY() > 0.0);
		} else if (this.isDerailed()) {
			this.direction = FaceUtil.getDirection(movement);
			this.directionTo = FaceUtil.getDirection(movement, false);
		} else {
			final BlockFace raildirection = this.getRailDirection();
			if (this.isOnSlope() && Math.abs(movement.getX()) < 0.001 && Math.abs(movement.getZ()) < 0.001 && Math.abs(movement.getY()) > 0.001) {
				// Going from vertical down to a slope
				if (movement.getY() > 0.0) {
					this.direction = raildirection;
				} else {
					this.direction = raildirection.getOppositeFace();
				}
				this.directionTo = this.direction;
			} else {
				this.direction = FaceUtil.getRailsCartDirection(raildirection);
				if (movement.getX() == 0 || movement.getZ() == 0) {
					// Moving along one axis - simplified calculation
					if (FaceUtil.getFaceYawDifference(this.direction, FaceUtil.getDirection(movement)) > 90) {
						this.direction = this.direction.getOppositeFace();
					}
				} else {
					final float moveYaw = MathUtil.getLookAtYaw(movement);
					// Compare with the movement direction to find out whether the opposite is needed
					float diff1 = MathUtil.getAngleDifference(moveYaw, FaceUtil.faceToYaw(this.direction));
					float diff2 = MathUtil.getAngleDifference(moveYaw, FaceUtil.faceToYaw(this.direction.getOppositeFace()));
					// Compare with the previous direction to sort out equality problems
					if (diff1 == diff2) {
						diff1 = FaceUtil.getFaceYawDifference(this.directionFrom, this.direction);
						diff2 = FaceUtil.getFaceYawDifference(this.directionFrom, this.direction.getOppositeFace());
					}
					// Use the opposite direction if needed
					if (diff1 > diff2) {
						this.direction = this.direction.getOppositeFace();
					}
				}
				// The to direction using the rail direction and movement direction
				if (this.direction == BlockFace.NORTH_EAST) {
					this.directionTo = raildirection == BlockFace.NORTH_WEST ? BlockFace.EAST : BlockFace.NORTH;
				} else if (this.direction == BlockFace.SOUTH_EAST) {
					this.directionTo = raildirection == BlockFace.NORTH_EAST ? BlockFace.SOUTH : BlockFace.EAST;
				} else if (this.direction == BlockFace.SOUTH_WEST) {
					this.directionTo = raildirection == BlockFace.NORTH_WEST ? BlockFace.SOUTH : BlockFace.WEST;
				} else if (this.direction == BlockFace.NORTH_WEST) {
					this.directionTo = raildirection == BlockFace.NORTH_EAST ? BlockFace.WEST : BlockFace.NORTH;
				} else {
					this.directionTo = this.direction;
				}
			}
		}
		// Force-update the from direction if it is invalidated
		if (this.directionFrom == BlockFace.SELF) {
			this.directionFrom = this.directionTo;
		}
	}
	public void updateDirection() {
		this.updateDirection(this.entity.getVelocity());
	}
	public void updateDirectionTo(MinecartMember<?> member) {
		this.updateDirection(this.entity.loc.offsetTo(member.entity));
	}
	public void updateDirectionFrom(MinecartMember<?> member) {
		this.updateDirection(member.entity.loc.offsetTo(this.entity));
	}

	@Override
	public void onDamage(DamageSource damagesource, int damage) {
		if (this.entity.isDead()) {
			return;
		}
		final Entity damager = damagesource.getEntity();
		try {
			// Call CraftBukkit event
			VehicleDamageEvent event = new VehicleDamageEvent(this.entity.getEntity(), damager, damage);
			if (CommonUtil.callEvent(event).isCancelled()) {
				return;
			}
			damage = event.getDamage();

			// Play shaking animation and logic
			this.entity.setShakingDirection(-this.entity.getShakingDirection());
			this.entity.setShakingFactor(10);
			this.entity.setVelocityChanged(true);
			this.entity.setDamage(this.entity.getDamage() + damage * 10);

			if (TrainCarts.instantCreativeDestroy) {
				// Check whether the entity is a creative (insta-build) entity
				if (damager instanceof HumanEntity && EntityUtil.getAbilities((HumanEntity) damager).canInstantlyBuild()) {
					this.entity.setDamage(100);
				}
			}
			if (this.entity.getDamage() > 40) {
				// CraftBukkit start
				List<ItemStack> drops = new ArrayList<ItemStack>(2);
				if (TrainCarts.breakCombinedCarts) {
					drops.addAll(this.entity.getBrokenDrops());
				} else {
					drops.add(new ItemStack(this.entity.getCombinedItem()));
				}
				VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(this.entity.getEntity(), damager);
				if (CommonUtil.callEvent(destroyEvent).isCancelled()) {
					this.entity.setDamage(40);
					return;
				}
				// CraftBukkit end

				// Some sort of validation check (what is the use...?)
				if (this.entity.hasPassenger()) {
					this.getEntity().setPassenger(this.entity.getPassenger());
				}

				// Spawn drops and die
				for (ItemStack stack : drops) {
					this.entity.spawnItemDrop(stack, 0.0F);
				}
				this.onDie();
			}
		} catch (Throwable t) {
			TrainCarts.plugin.handle(t);
		}
	}

	/*
	 * Stores physics information (since functions are now pretty much scattered around)
	 */
	private MoveInfo moveinfo;
	private class MoveInfo {
		public final MinecartMember<?> owner;
		public IntVector3 blockPos = new IntVector3(0, 0, 0);
		public Block lastBlock, block;
		public RailType railType;
		public RailType prevRailType = RailType.NONE;
		public RailLogic railLogic = RailLogicGround.INSTANCE;
		public RailLogic prevRailLogic = RailLogicGround.INSTANCE;
		public boolean railLogicSnapshotted = false;

		public MoveInfo(MinecartMember<?> owner) {
			this.owner = owner;
			this.blockPos = owner.entity.loc.block();
			this.lastBlock = this.block = this.blockPos.toBlock(owner.entity.getWorld());
		}

		public boolean blockChanged() {
			return blockPos.x != lastBlock.getX() || blockPos.y != lastBlock.getY() || blockPos.z != lastBlock.getZ();
		}

		public void updateBlock() {
			updateBlock(WorldUtil.getBlockTypeId(owner.entity.getWorld(), blockPos));
		}

		public void updateBlock(int railtype) {
			if (this.blockChanged()) {
				this.block = this.blockPos.toBlock(owner.entity.getWorld());
			}
			int raildata = WorldUtil.getBlockData(owner.entity.getWorld(), blockPos);
			// Update rail type and sloped state
			this.railType = RailType.get(railtype, raildata);
		}

		public void updateRailLogic() {
			this.prevRailType = this.railType;
			this.prevRailLogic = this.railLogic;
			this.railLogic = RailLogic.get(this.owner);
			if (this.railLogic instanceof RailLogicVertical) {
				this.railType = RailType.VERTICAL;
			}
			this.railLogicSnapshotted = true;
		}

		public void fillRailsData() {
			final World world = owner.entity.getWorld();
			this.lastBlock = this.block;
			this.blockPos = owner.entity.loc.block();
			IntVector3 below = this.blockPos.subtract(0, 1, 0);
			owner.vertToSlope = false;

			// Find the rail - first step
			int railtype = WorldUtil.getBlockTypeId(world, below);
			if (MaterialUtil.ISRAILS.get(railtype) || MaterialUtil.ISPRESSUREPLATE.get(railtype)) {
				this.blockPos = below;
			} else if (Util.ISVERTRAIL.get(railtype) && this.prevRailType != RailType.VERTICAL) {
				this.blockPos = below;
			} else {
				railtype = WorldUtil.getBlockTypeId(world, this.blockPos);
			}
			this.updateBlock(railtype);

			// Slope UP -> Vertical
			if (this.railType == RailType.VERTICAL && this.prevRailLogic.isSloped()) {
				if (this.prevRailLogic.getDirection() == owner.getDirection().getOppositeFace()) {
					owner.entity.loc.setY((double) blockPos.y + 0.95);
				}
			}

			// Vertical -> Slope UP
			if (this.railType == RailType.NONE && owner.entity.vel.getY() > 0) {
				final IntVector3 nextPos = blockPos.add(this.prevRailLogic.getDirection());
				Block next = nextPos.toBlock(world);
				Rails rails = BlockUtil.getRails(next);
				if (rails != null && rails.isOnSlope()) {
					if (rails.getDirection() == this.prevRailLogic.getDirection()) {
						// Move the minecart to the slope
						this.blockPos = nextPos;
						this.updateBlock();
						owner.entity.loc.xz.set(this.blockPos.x + 0.5, this.blockPos.z + 0.5);
						owner.entity.loc.xz.subtract(this.prevRailLogic.getDirection(), 0.49);
						// Y offset
						final double transOffset = 0.01; // How high above the slope to teleport to
						owner.entity.loc.setY(this.blockPos.y + transOffset);
					}
				}
			}
		}
	}

	/**
	 * Refreshes the rail information of this minecart
	 */
	protected void refreshBlockInformation() {
		moveinfo.fillRailsData();
	}

	@Override
	public void onDie() {
		if (!this.died) {
			super.onDie();
			this.died = true;
			if (!this.isUnloaded()) {
				// Note: No getGroup() calls are allowed here!
				// They may create new groups!
				if (this.group != null) {
					entity.setDead(false);
					this.clearActiveSigns();
					this.clearActiveDetectors();
					entity.setDead(true);
				}
				if (entity.hasPassenger()) {
					entity.setPassenger(null);
				}
				if (this.group != null) {
					this.group.remove(this);
				}
				CartPropertiesStore.remove(entity.getUniqueId());
			}
		}
	}

	private int prevcx, prevcz;
	protected void checkChunks(boolean canunload) throws GroupUnloadedException {
		int newcx = entity.loc.x.chunk();
		int newcz = entity.loc.z.chunk();
		if (newcx != prevcx || newcz != prevcz) {
			if (canunload) {
				if (!WorldUtil.areChunksLoaded(entity.getWorld(), newcx, newcz, 2)) {
					OfflineGroupManager.hideGroup(this.getGroup());
					throw new GroupUnloadedException();
				}
			} else {
				// Queue the chunks this minecart left for unloading
				LongHashSet unloadedChunks = new LongHashSet(25);
				int cx, cz;
				// Add in the previous chunks
				for (cx = -2; cx <= 2; cx++) {
					for (cz = -2; cz <= 2; cz++) {
						unloadedChunks.add(prevcx + cx, prevcz + cz);
					}
				}
				// Remove the current chunks
				for (cx = -2; cx <= 2; cx++) {
					for (cz = -2; cz <= 2; cz++) {
						unloadedChunks.remove(newcx + cx, newcz + cz);
					}
				}
				// Queue-unload the chunks that are not kept loaded
				for (long key : unloadedChunks.toArray()) {
					cx = MathUtil.longHashMsw(key);
					cz = MathUtil.longHashLsw(key);
					entity.getWorld().unloadChunkRequest(cx, cz, true);
				}
				this.loadChunks();
			}
			prevcx = newcx;
			prevcz = newcz;
		}
	}

	@Override
	public boolean onEntityCollision(Entity e) {
		MinecartMember<?> mm1 = this;
		if (mm1.isCollisionIgnored(e) || mm1.isUnloaded() || e.isDead() || this.entity.isDead() || this.getGroup().isMovementControlled()) {
			return false;
		}
		MinecartMember<?> mm2 = MemberConverter.toMember.convert(e);
		//colliding with a member in the group, or not?
		if (mm2 != null) {
			if (mm2.isUnloaded()) {
				// The minecart is unloaded - ignore it
				return false;
			} else if (mm1.getGroup() == mm2.getGroup()) {
				//Same group, but do prevent penetration
				if (mm1.entity.loc.distance(mm2.entity) > 0.5) {
					return false;
				}
			} else if (!mm1.getGroup().getProperties().getColliding()) {
				//Allows train collisions?
				return false;
			} else if (!mm2.getGroup().getProperties().getColliding()) {
				//Other train allows train collisions?
				return false;
			} else if (mm2.getGroup().isMovementControlled()) {
				//Is this train targeting?
				return false;
			}
			// Check if both minecarts are on the same vertical column
			RailLogic logic1 = mm1.getRailLogic();
			if (logic1 instanceof RailLogicVerticalSlopeDown) {
				RailLogic logic2 = mm2.getRailLogic();
				if (logic2 instanceof RailLogicVerticalSlopeDown) {
					Block b1 = mm1.getBlock(logic1.getDirection());
					Block b2 = mm2.getBlock(logic2.getDirection());
					if (BlockUtil.equals(b1, b2)) {
						return false;
					}
				}
			}
			return true;
		} else if (e.isInsideVehicle() && e.getVehicle() instanceof Minecart) {
			//Ignore passenger collisions
			return false;
		} else {
			TrainProperties prop = this.getGroup().getProperties();
			// Is it picking up this item?
			if (e instanceof Item && this.getProperties().canPickup()) {
				return false;
			}

			//No collision is allowed? (Owners override)
			if (!prop.getColliding() && (!(e instanceof Player) || !prop.isOwner((Player) e))) {
				return false;
			}

			// Collision modes
			if (!prop.getCollisionMode(e).execute(this, e)) {
				return false;
			}
		}
		// Collision occurred, collided head-on? Stop the entire train
		if (this.isHeadingTo(e)) {
			this.getGroup().stop();
		}
		return true;
	}

	@Override
	public boolean onBlockCollision(org.bukkit.block.Block block, BlockFace hitFace) {
		if (Util.ISVERTRAIL.get(block)) {
			return false;
		}
		if (moveinfo.railType == RailType.VERTICAL && hitFace != BlockFace.UP && hitFace != BlockFace.DOWN) {
			// Check if the collided block has vertical rails
			if (Util.ISVERTRAIL.get(block.getRelative(hitFace))) {
				return false;
			}
		}
		// Handle collision
		if (!this.isTurned() && hitFace.getOppositeFace() == this.getDirectionTo() && !this.isDerailed()) {
			// Cancel collisions with blocks at the heading of sloped rails
			if (this.isOnSlope() && hitFace == this.getRailDirection().getOppositeFace()) {
				// Vertical rail above?
				if (Util.isVerticalAbove(this.getBlock(), this.getRailDirection())) {
					return false;
				}
			}
			// Stop the train
			this.getGroup().stop();
		}
		return true;
	}

	/**
	 * Gets the packet to spawn this Minecart Member
	 * 
	 * @return spawn packet
	 */
	public CommonPacket getSpawnPacket() {
		return PacketFields.VEHICLE_SPAWN.newInstance(entity.getEntity(), 10 + entity.getMinecartType());
		/*
		final MinecartMemberTrackerEntry tracker = this.getTracker();
		final int type = Conversion.toMinecartTypeId.convert(getType());
		final CommonPacket p = new CommonPacket(PacketFields.VEHICLE_SPAWN.newInstance(this.getEntity(), 10 + type));
		if (tracker != null) {
			// Entity tracker is available - use it for the right position
			p.write(PacketFields.VEHICLE_SPAWN.x, tracker.xLoc);
			p.write(PacketFields.VEHICLE_SPAWN.y, tracker.yLoc);
			p.write(PacketFields.VEHICLE_SPAWN.z, tracker.zLoc);
			p.write(PacketFields.VEHICLE_SPAWN.yaw, (byte) tracker.xRot);
			p.write(PacketFields.VEHICLE_SPAWN.pitch, (byte) tracker.yRot);
		}
		return p;
		*/
	}

	/**
	 * Gets the inventory of a potential Player passenger
	 * 
	 * @return the passenger Player inventory, or null if there is no player
	 */
	public PlayerInventory getPlayerInventory() {
		Entity passenger = entity.getPassenger();
		if (passenger instanceof Player) {
			return ((Player) passenger).getInventory();
		} else {
			return null;
		}
	}

	/**
	 * Ejects the passenger of this Minecart
	 */
	public void eject() {
		this.getEntity().eject();
		this.resetCollisionEnter();
	}

	/**
	 * Ejects the passenger of this Minecart and teleports him to the offset and rotation specified
	 * 
	 * @param offset to teleport to
	 * @param yaw rotation
	 * @param pitch rotation
	 */
	public void eject(Vector offset, float yaw, float pitch) {
		eject(new Location(entity.getWorld(), entity.loc.getX() + offset.getX(), entity.loc.getY() + offset.getY(), entity.loc.getZ() + offset.getZ(), yaw, pitch));
	}

	/**
	 * Ejects the passenger of this Minecart and teleports him to the location specified
	 * 
	 * @param to location to eject/teleport to
	 */
	public void eject(final Location to) {
		if (entity.hasPassenger()) {
			final Entity passenger = this.entity.getPassenger();
			this.eject();
			EntityUtil.teleportNextTick(passenger, to);
		}
	}

	public boolean connect(MinecartMember<?> with) {
		return this.getGroup().connect(this, with);
	}

	public void update() {
		if (entity.isDead()) {
			return; 
		}
		this.needsUpdate.set();
		this.getGroup().update();
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
	public void reverse() {
		entity.vel.multiply(-1.0);
		this.direction = this.direction.getOppositeFace();
	}

	/**
	 * Respawns the entity to the client (used to avoid teleport smoothing)
	 */
	public void respawn() {
		entity.getNetworkController().syncRespawn();
	}

	/**
	 * Called when the blocks below this minecart change block coordinates
	 * 
	 * @param from block - the old block
	 * @param to block - the new block
	 */
	public void onBlockChange(Block from, Block to) {
		//update from direction
		if (BlockUtil.getManhattanDistance(from, to, true) > 3) {
			this.directionFrom = BlockFace.SELF;
		} else {
			this.directionFrom = this.directionTo;
		}

		//update active signs
		this.clearActiveSigns();
		this.checkMissing();
		if (!this.isDerailed()) {
			for (Block sign : Util.getSignsFromRails(tmpblockbuff, this.getBlock())) {
				this.addActiveSign(sign);
				this.checkMissing();
			}

			//destroy blocks
			Block left = this.getBlockRelative(BlockFace.WEST);
			Block right = this.getBlockRelative(BlockFace.EAST);
			if (this.getProperties().canBreak(left)) {
				BlockInfo.get(left).destroy(left, 20.0f);
			}
			if (this.getProperties().canBreak(right)) {
				BlockInfo.get(right).destroy(right, 20.0f);
			}
		}

		//Detector regions
		List<DetectorRegion> newregions = DetectorRegion.handleMove(this, from, to);
		this.activeDetectorRegions.clear();
		if (newregions != null) {
			this.activeDetectorRegions.addAll(newregions);
		}
	}

	/**
	 * Executes the block and pre-movement calculations, which handles rail information updates<br>
	 * Physics stage: <b>1</b>
	 */
	public void onPhysicsStart() {
		//subtract times
		Iterator<AtomicInteger> times = collisionIgnoreTimes.values().iterator();
		while (times.hasNext()) {			
			if (times.next().decrementAndGet() <= 0) times.remove();
		}
		if (this.teleportImmunityTick > 0) {
			this.teleportImmunityTick--;
		}
		if (this.collisionEnterTimer > 0) {
			this.collisionEnterTimer--;
		}

		// Prepare
		entity.vel.fixNaN();
		entity.last.set(entity.loc);
		this.refreshBlockInformation();
	}

	/**
	 * Executes the block change events<br>
	 * Physics stage: <b>2</b>
	 */
	public void onPhysicsBlockChange() {
		// Handle block changes
		this.checkMissing();
		if (moveinfo.blockChanged() | forcedBlockUpdate.clear()) {
			// Perform events and logic - validate along the way
			MemberBlockChangeEvent.call(this, moveinfo.lastBlock, moveinfo.block);
			this.checkMissing();
			this.onBlockChange(moveinfo.lastBlock, moveinfo.block);
			this.checkMissing();
		}
		moveinfo.updateRailLogic();
	}

	/**
	 * Executes the velocity and pre-movement calculations, which handles logic prior to actual movement occurs<br>
	 * Physics stage: <b>3</b>
	 */
	public void onPhysicsPreMove() {
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
		}

		// Perform gravity
		if (!getGroup().isMovementControlled()) {
			entity.vel.y.subtract(this.moveinfo.railLogic.getGravityMultiplier(this));
		}

		// reset fall distance
		if (!this.isDerailed()) {
			entity.setFallDistance(0.0f);
		}

		// Perform rails logic
		moveinfo.railLogic.onPreMove(this);

		// Update the entity shape
		entity.setPosition(entity.loc.getX(), entity.loc.getY(), entity.loc.getZ());

		// Slow down on unpowered booster tracks
		// Note: HAS to be in PreUpdate, otherwise glitches occur!
		if (moveinfo.railType == RailType.BRAKE && !getGroup().isMovementControlled()) {
			if (entity.vel.xz.lengthSquared() < 0.0009) {
				entity.vel.multiply(0.0);
			} else {
				entity.vel.multiply(0.5);
			}
		}
	}

	/**
	 * Performs all logic right after movement has occurred
	 */
	public void doPostMoveLogic() {
	}

	/**
	 * Performs the move logic for when the Minecart travels on top of an Activator rail.
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

	/**
	 * Moves the minecart and performs post-movement logic such as events, onBlockChanged and other (rail) logic
	 * Physics stage: <b>4</b>
	 * 
	 * @param speedFactor to apply when moving
	 * @throws MemberMissingException - thrown when the minecart is dead or dies
	 * @throws GroupUnloadedException - thrown when the group is no longer loaded
	 */
	public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
		this.checkMissing();

		// Modify speed factor to stay within bounds
		speedFactor = MathUtil.clamp(MathUtil.fixNaN(speedFactor, 1), 0.1, 10);

		// Apply speed factor to maxed and not-a-number-fixed values
		double motX = speedFactor * entity.vel.x.fixNaN().getClamped(entity.getMaxSpeed());
		double motY = speedFactor * entity.vel.y.fixNaN().getClamped(entity.getMaxSpeed());
		double motZ = speedFactor * entity.vel.z.fixNaN().getClamped(entity.getMaxSpeed());

		// No vertical motion if stuck to the rails that way
		if (!moveinfo.railLogic.hasVerticalMovement()) {
			motY = 0.0;
		}

		// Move using set motion, and perform post-move rail logic
		this.onMove(motX, motY, motZ);
		this.checkMissing();
		this.moveinfo.railLogic.onPostMove(this);

		// Post-move logic
		this.doPostMoveLogic();
		if (!this.isDerailed()) {
			// Slowing down of minecarts
			if (this.getGroup().getProperties().isSlowingDown()) {
				if (entity.hasPassenger() || !entity.isSlowWhenEmpty() || !TrainCarts.slowDownEmptyCarts) {
					entity.vel.multiply(TrainCarts.slowDownMultiplierNormal);
				} else {
					entity.vel.multiply(TrainCarts.slowDownMultiplierSlow);
				}
			}

			// Perform Rails-specific logic
			if (moveinfo.railType == RailType.ACTIVATOR_ON) {
				// Activating the Minecart
				this.onActivatorUpdate(true);
				if (railActivated.set()) {
					this.onActivate();
				}
			} else if (moveinfo.railType == RailType.ACTIVATOR_OFF) {
				// De-activating the Minecart
				this.onActivatorUpdate(false);
				railActivated.clear();
			} else {
				railActivated.clear();
			}

			// Launching on powered booster tracks
			if (moveinfo.railType == RailType.BOOST && !getGroup().isMovementControlled()) {
				double motLength = entity.vel.xz.length();
				if (motLength > 0.01) {
					// Simple motion boosting when already moving
					entity.vel.xz.add(entity.vel.xz, TrainCarts.poweredRailBoost / motLength);
				} else {
					// Launch away from a suffocating block
					BlockFace dir = this.getRailDirection();
					org.bukkit.block.Block block = this.getBlock();
					boolean pushFrom1 = MaterialUtil.SUFFOCATES.get(block.getRelative(dir.getOppositeFace()));
					boolean pushFrom2 = MaterialUtil.SUFFOCATES.get(block.getRelative(dir));
					// If pushing from both directions, block all movement
					if (pushFrom1 && pushFrom2) {
						entity.vel.xz.setZero();
					} else if (pushFrom1 != pushFrom2) {
						// Boosting to the open spot
						final double boost = MathUtil.invert(POWERED_RAIL_START_BOOST, pushFrom2);
						entity.vel.xz.set(boost * dir.getModX(), boost * dir.getModZ());
					}
				}
			}
		}

		// Update rotation
		this.onRotationUpdate();

		// Ensure that the yaw and pitch stay within limits
		entity.loc.setYaw(entity.loc.getYaw() % 360.0f);
		entity.loc.setPitch(entity.loc.getPitch() % 360.0f);

		// Invalidate volatile information
		moveinfo.railLogicSnapshotted = false;

		// Perform some (CraftBukkit) events
		Location from = entity.getLastLocation();
		Location to = entity.getLocation();
		Vehicle vehicle = entity.getEntity();
		CommonUtil.callEvent(new VehicleUpdateEvent(vehicle));
		if (!from.equals(to)) {
			// Execute move events
			CommonUtil.callEvent(new VehicleMoveEvent(vehicle, from, to));
			for (org.bukkit.block.Block sign : this.getActiveSigns()) {
				SignAction.executeAll(new SignActionEvent(sign, this), SignActionType.MEMBER_MOVE);
			}
		}

		// Minecart collisions
		for (Entity near : entity.getNearbyEntities(0.2, 0, 0.2)) {
			if (near instanceof Minecart && near != this.entity.getPassenger()) {
				EntityUtil.doCollision(near, this.entity.getEntity());
			}
		}

		// Ensure that dead passengers are cleared
		if (entity.hasPassenger() && entity.getPassenger().isDead()) {
			entity.setPassenger(null);
		}

		// Final logic
		this.checkMissing();

		// Play additional sound effects
		this.soundLoop.onTick();

		// Updating
		if (this.needsUpdate.clear()) {
			for (Block b : this.activeSigns) {
				SignAction.executeAll(new SignActionEvent(b, this), SignActionType.MEMBER_UPDATE);
			}
			for (DetectorRegion reg : this.activeDetectorRegions) {
				reg.update(this);
			}
		}
	}

	@Override
	public void onTick() {
		if (this.isUnloaded()) {
			return;
		}
		MinecartGroup g = this.getGroup();
		if (g == null) {
			return;
		}
		if (entity.isDead()) {
			// remove self
			g.remove(this);
		} else if (g.isEmpty()) {
			g.remove();
			super.onTick();
		} else if (g.tail() == this) {
			g.doPhysics();
		}
	}

	private void setAngleSafe(float newyaw, float pitch, boolean mode) {
		if (entity.loc.getYawDifference(newyaw) > 170) {
			entity.loc.setYaw(MathUtil.wrapAngle(newyaw + 180));
			entity.loc.setPitch(mode ? -pitch : (pitch - 180f));
		} else {
			entity.loc.setYaw(newyaw);
			entity.loc.setPitch(pitch);
		}
	}

	/**
	 * Performs rotation updates for yaw and pitch
	 */
	public void onRotationUpdate() {
		//Update yaw and pitch based on motion
		double movedX = -entity.getMovedX();
		double movedY = -entity.getMovedY();
		double movedZ = -entity.getMovedZ();
		boolean movedXZ = entity.hasMovedHorizontally();
		float newyaw = movedXZ ? MathUtil.getLookAtYaw(movedX, movedZ) : entity.loc.getYaw();
		float newpitch = entity.loc.getPitch();
		boolean mode = true;
		if (entity.isOnGround()) {
			if (Math.abs(newpitch) > 0.1) {
				newpitch *= 0.1;
			} else {
				newpitch = 0;
			}
		} else if (this.isOnVertical()) {
			newyaw = FaceUtil.faceToYaw(this.getRailDirection());
			newpitch = -90f;
			mode = false;
		} else if (moveinfo.railType == RailType.PRESSUREPLATE) {
			newpitch = 0.0F; //prevent weird pitch angles on pressure plates
		} else if (movedXZ) {
			if (this.moveinfo.railType.isHorizontal()) {
				newpitch = -0.8F * MathUtil.getLookAtPitch(movedX, movedY, movedZ);
			} else {
				newpitch = 0.7F * MathUtil.getLookAtPitch(movedX, movedY, movedZ);
			}
			newpitch = MathUtil.clamp(newpitch, 60F);
		}
		setAngleSafe(newyaw, newpitch, mode);
	}

	@Override
	public String getLocalizedName() {
		return isSingle() ? "Minecart" : "Train";
	}
}
