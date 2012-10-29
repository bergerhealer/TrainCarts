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

import net.minecraft.server.ChunkCoordinates;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.IInventory;
import net.minecraft.server.ItemStack;
import net.minecraft.server.LocaleI18n;
import net.minecraft.server.World;
import net.minecraft.server.EntityItem;

import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftInventoryPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.items.ItemParser;
import com.bergerkiller.bukkit.common.items.MergedInventory;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberDeadException;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.*;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.MemberBlockChangeEvent;
import com.bergerkiller.bukkit.tc.events.MemberCoalUsedEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.common.reflection.classes.EntityMinecartRef;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;
import com.bergerkiller.bukkit.tc.utils.TrackMap;

public class MinecartMember extends MinecartMemberStore {
	private BlockFace direction;
	private BlockFace directionTo;
	private BlockFace directionFrom;
	protected MinecartGroup group;
	private boolean forcedBlockUpdate = true;
	protected boolean died = false;
	private int teleportImmunityTick = 0;
	private boolean needsUpdate = false;
	private boolean ignoreAllCollisions = false;
	private CartProperties properties;
	private Map<UUID, AtomicInteger> collisionIgnoreTimes = new HashMap<UUID, AtomicInteger>();
	private Set<Block> activeSigns = new LinkedHashSet<Block>();
	protected MinecartMemberTrackerEntry tracker;
	private List<DetectorRegion> activeDetectorRegions = new ArrayList<DetectorRegion>(0);
	protected boolean unloaded = false;

	protected MinecartMember(EntityMinecart source) {
		this(source.world, source.lastX, source.lastY, source.lastZ, source.type);
		EntityMinecartRef.TEMPLATE.transfer(source, this);
	}

	protected MinecartMember(World world, double x, double y, double z, int type) {
		super(world, x, y, z, type);
		this.prevcx = MathUtil.locToChunk(this.locX);
		this.prevcz = MathUtil.locToChunk(this.locZ);
		this.direction = FaceUtil.yawToFace(this.yaw);
		this.directionFrom = this.directionTo = FaceUtil.yawToFace(this.yaw, false);
	}

	/*
	 * Overridden Minecart functions
	 */
	@Override
	public void h_() {
		if (this.isUnloaded()) {
			return;
		}
		MinecartGroup g = this.getGroup();
		if (g == null) return;
		if (this.dead) {
			//remove self
			g.remove(this);
		} else if (g.isEmpty()) {
			g.remove();
			super.h_();
		} else if (g.tail() == this) {
			g.doPhysics();
		}
	}

	public boolean preUpdate(int stepcount) {
		//subtract times
		Iterator<AtomicInteger> times = collisionIgnoreTimes.values().iterator();
		while (times.hasNext()) {			
			if (times.next().decrementAndGet() <= 0) times.remove();
		}
		if (this.teleportImmunityTick > 0) {
			this.teleportImmunityTick--;
		}
		return super.preUpdate(stepcount);
	}

	public void postUpdate(double speedFactor) throws MemberDeadException, GroupUnloadedException {
		super.postUpdate(speedFactor);
		if (this.isOnMinecartTrack) {
			this.fallDistance = 0.0f; // reset fall distance
		}
		this.validate();
		if (this.getProperties().canPickup() && this.isStorageCart()) {
			Inventory inv = this.getInventory();
			org.bukkit.inventory.ItemStack stack;
			Item item;
			for (net.minecraft.server.Entity e : this.getNearbyEntities(2)) {
				if (e instanceof EntityItem) {
					item = (Item) e.getBukkitEntity();
					if (EntityUtil.isIgnored(item)) continue;
					stack = item.getItemStack();
					double distance = this.distance(e);
					if (ItemUtil.testTransfer(stack, inv) == stack.getAmount()) {
						if (distance < 0.7) {
							ItemUtil.transfer(stack, inv, Integer.MAX_VALUE);
							//this.world.playNote
							this.world.getWorld().playEffect(this.getLocation(), Effect.CLICK1, 0);
							if (stack.getAmount() == 0) {
								e.dead = true;
								continue;
							}
						} else {
							final double factor;
							if (distance > 1) {
								factor = 0.8;
							} else if (distance > 0.75) {
								factor = 0.5;
							} else {
								factor = 0.25;
							}
							this.push(e.getBukkitEntity(), -factor / distance);
							continue;
						}
					}
					this.push(e.getBukkitEntity(), 1 / distance);
				}
			}
		}
		this.updateBlock();
		if (this.needsUpdate) {
			this.needsUpdate = false;
			for (Block b : this.activeSigns) {
				SignAction.executeAll(new SignActionEvent(b, this), SignActionType.MEMBER_UPDATE);
			}
			for (DetectorRegion reg : this.activeDetectorRegions) {
				reg.update(this);
			}
		}
	}

	@Override
	public boolean onCoalUsed() {
		MemberCoalUsedEvent event = MemberCoalUsedEvent.call(this);
		if (event.useCoal()) {
			return this.getCoalFromNeighbours();
		}
		return event.refill();
	}

	@Override
	public String getLocalizedName() {
		if (this.group == null || this.group.size() == 1) {
			return LocaleI18n.get("entity.Minecart.name");
		} else {
			return "Train";
		}
	}

	public boolean getCoalFromNeighbours() {
		for (MinecartMember mm : this.getNeightbours()) {
			//Is it a storage minecart?
			if (mm.type == 1) {
				//has coal?
				for (int i = 0; i < mm.getSize(); i++) {
					if (mm.getItem(i) != null && mm.getItem(i).id == Material.COAL.getId()) {
						 mm.getItem(i).count--;
						 return true;
					}
				}
			}
		}
		return false;
	}

	private static List<Block> tmpblockbuff = new ArrayList<Block>();
	private void updateBlock() throws MemberDeadException, GroupUnloadedException {
		if (this.locY < 0) {
			this.dead = true;
		}
		this.validate();
		if (!this.activeSigns.isEmpty()) {
			SignActionEvent info;
			for (Block sign : this.activeSigns) {
				info = new SignActionEvent(sign, this);
				SignAction.executeAll(info, SignActionType.MEMBER_MOVE);
			}
		}
		int x = this.getBlockX();
		int y = this.getBlockY();
		int z = this.getBlockZ();
		int prevX = this.getLastBlockX();
		int prevY = this.getLastBlockY();
		int prevZ = this.getLastBlockZ();
		boolean forced = forcedBlockUpdate || Math.abs(prevX - x) > 128 || Math.abs(prevY - y) > 128 || Math.abs(prevZ - z) > 128;
		Block from = forced ? null : this.getBlock();
		if (forced || x != prevX || z != prevZ || y != prevY) {
			getGroup().needsBlockUpdate = true;
			//find the correct Y-value
			this.forcedBlockUpdate = false;

			//Update from value if it was not set
			Block to = this.getBlock();
			if (from == null) from = to;

			//update active signs
			this.clearActiveSigns();
			this.validate();
			if (!this.isDerailed()) {
				for (Block sign : Util.getSignsFromRails(tmpblockbuff, this.getBlock())) {
					this.addActiveSign(sign);
					this.validate();
				}
				
				//destroy blocks
				Block left = this.getBlockRelative(BlockFace.WEST);
				Block right = this.getBlockRelative(BlockFace.EAST);
				if (this.getProperties().canBreak(left)) BlockUtil.breakBlock(left);
				if (this.getProperties().canBreak(right)) BlockUtil.breakBlock(right);
			}
			
			//Detector regions
			List<DetectorRegion> newregions = DetectorRegion.handleMove(this, from, to);
			this.activeDetectorRegions.clear();
			if (newregions != null) {
				this.activeDetectorRegions.addAll(newregions);
			}
			this.validate();
								
			//event
			MemberBlockChangeEvent.call(this, from, to);
			this.validate();
		}
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
	public Minecart getMinecart() {
		return (Minecart) this.getBukkitEntity();
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
 			return this.dead ? -1 : 0;
 		} else {
 			return this.group.indexOf(this);
 		}
 	}
 	public MinecartMember getNeighbour(int offset) {
 		int index = this.getIndex();
 		if (index == -1) return null;
 		index += offset;
 		if (this.getGroup().containsIndex(index)) return this.getGroup().get(index);
 		return null;
 	}
 	public MinecartMember[] getNeightbours() {
		if (this.getGroup() == null) return new MinecartMember[0];
		int index = this.getIndex();
		if (index == -1) return new MinecartMember[0];
		if (index > 0) {
			if (index < this.getGroup().size() - 1) {
				return new MinecartMember[] {
						this.getGroup().get(index - 1), 
						this.getGroup().get(index + 1)};
			} else {
				return new MinecartMember[] {this.getGroup().get(index - 1)};
			}
		} else if (index < this.getGroup().size() - 1) {
			return new MinecartMember[] {this.getGroup().get(index + 1)};
		} else {
			return new MinecartMember[0];
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
			if (this.dead) return true;
			SignActionEvent info = new SignActionEvent(signblock, this);
			SignAction.executeAll(info, SignActionType.MEMBER_ENTER);
			if (this.dead) return true;
			this.getGroup().setActiveSign(info, true);
			return true;
		} else {
			return false;
		}
	}
	public void clearActiveSigns() {
		boolean found;
		for (Block signblock : this.activeSigns) {
			SignAction.executeAll(new SignActionEvent(signblock, this), SignActionType.MEMBER_LEAVE);
			if (this.getGroup() == null) return; 
			//this sign is not present in other members of the group?
			found = false;
			for (MinecartMember mm : this.getGroup()) {
				if (mm != this && mm.isActiveSign(signblock)) {
					found = true;
					break;
				}
			}
			if (found) continue;
			if (this.getGroup().tail() == this) {
				this.getGroup().setActiveSign(signblock, false);
			}
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

 	/*
 	 * Block functions
 	 */
 	public Block getBlock(int dx, int dy, int dz) {
 		return this.world.getWorld().getBlockAt(this.getBlockX() + dx, this.getBlockY() + dy, this.getBlockZ() + dz);
 	}
	public Block getBlock(BlockFace face) {
		return this.getBlock(face.getModX(), face.getModY(), face.getModZ());
	}
	public Block getBlock() {
		return this.getBlock(0, 0, 0);
	}
	public Block getBlockRelative(BlockFace direction) {
		return this.getBlock(FaceUtil.add(direction, this.getDirection()));
	}
 	public Block getRailsBlock() {
		if (this.isDerailed()) return null;
		Block b = this.getBlock();
		if (Util.isRails(b)) {
			return b;
		} else {
			return null;
		}
	}
 	public Rails getRails() {
 		return BlockUtil.getRails(this.getRailsBlock());
 	}
	public BlockFace getRailDirection() {
		Rails r = getRails();
		if (r == null) return this.getDirection();
		return r.getDirection();
	}
	public Block getGroundBlock() {
		return this.getBlock(0, -1, 0);
	}

	/*
	 * Actions
	 */
	public <T extends Action> T addAction(T action) {
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
		return this.addActionLaunch(this.getLocation().add(offset), targetvelocity);
	}
	public MemberActionLaunchDirection addActionLaunch(final BlockFace direction, double targetdistance, double targetvelocity) {
		return this.addAction(new MemberActionLaunchDirection(this, targetdistance, targetvelocity, direction));
	}
	public MemberActionWaitOccupied addActionWaitOccupied(int maxsize, long launchDelay, double launchDistance) {
		return this.addAction(new MemberActionWaitOccupied(this, maxsize, launchDelay, launchDistance));
	}

	/*
	 * Velocity functions
	 */
	public double getForceSquared() {
		if (this.isMovingVertical()) {
			return this.motY * this.motY;
		}
		return MathUtil.lengthSquared(this.motX, this.motZ);
	}
	public double getForce() {
		return Math.sqrt(getForceSquared());
	}
	public double getForwardForce() {
		if (this.isMovingVertical()) {
			return this.motY * this.direction.getModY();
		}
		return -FaceUtil.sin(this.direction) * this.motZ - FaceUtil.cos(this.direction) * this.motX; 
	}
	public void setForceFactor(final double factor) {
		this.motX *= factor;
		this.motY *= factor;
		this.motZ *= factor;
	}

	private void setYForce(double force) {
		if (this.isOnSlope()) {
			//calculate upwards or downwards force
			BlockFace raildir = this.getRailDirection();
			if (direction == raildir) {
				this.motY = MathUtil.halfRootOfTwo * force;
			} else if (direction == raildir.getOppositeFace()) {
				this.motY = -MathUtil.halfRootOfTwo * force;
			}
		}
	}
	public void setForce(double force) {
		setForceFactor(force / this.getForce());
	}
	public void setForce(double force, BlockFace direction) {
		if (this.isMovingVertical()) {
			this.motY = (double) direction.getModY() * force;
			this.motX = this.motZ = 0.0;
		} else {
			this.setYForce(force);
			this.motX = -FaceUtil.cos(direction) * force;
			this.motZ = -FaceUtil.sin(direction) * force;
		}
	}
	public void setForwardForce(double force) {
		if (this.isMoving() && force > 0.01 && !this.isOnVertical()) {
			if (FaceUtil.getDirection(this.motX, this.motZ, false) == this.direction) {
				this.setYForce(force);
				force /= this.getForce();
				this.motX *= force;
				this.motZ *= force;
				return;
			}
		}
		this.setForce(force, this.direction);
	}
	public void limitSpeed() {
		//Limits the velocity to the maximum
		double currvel = getForce();
		if (currvel > this.maxSpeed && currvel > 0.01) {
			double factor = this.maxSpeed / currvel;
			this.motX *= factor;
			this.motZ *= factor;
		}
	}
	public void setVelocity(Vector velocity) {
		this.motX = velocity.getX();
		this.motZ = velocity.getZ();
		this.motY = velocity.getY();
	}
	public Vector getVelocity() {
		return new Vector(this.motX, this.motY, this.motZ);
	}
	public Vector getLimitedVelocity() {
		double max;
		if (this.isUnloaded()) {
			max = this.maxSpeed;
		} else {
			max = this.getGroup().getProperties().getSpeedLimit();
		}
		return new Vector(MathUtil.clamp(this.motX, max), MathUtil.clamp(this.motY, max), MathUtil.clamp(this.motZ, max));
	}
	public TrackMap makeTrackMap(int size) {
		return new TrackMap(this.getRailsBlock(), this.direction, size);
	}

	/*
	 * Location functions
	 */
	public double getSubX() {
		double x = getX() + 0.5;
		return x - (int) x;
	}	
	public double getSubZ() {
		double z = getZ() + 0.5;
		return z - (int) z;
	}

	public int getChunkX() {
		return this.getBlockX() >> 4;
	}
	public int getChunkZ() {
		return this.getBlockZ() >> 4;
	}
	public double getMovedX() {
		return this.locX - this.lastX;
	}
	public double getMovedY() {
		return this.locY - this.lastY;
	}
	public double getMovedZ() {
		return this.locZ - this.lastZ;
	}
	public double getMovedDistanceXZ() {
		return MathUtil.length(this.getMovedX(), this.getMovedZ());
	}
	public double getMovedDistance() {
		return MathUtil.length(this.getMovedX(), this.getMovedY(), this.getMovedZ());
	}
	public double distance(net.minecraft.server.Entity e) {
		return MathUtil.distance(this.getX(), this.getY(), this.getZ(), e.locX, e.locY, e.locZ);
	}
	public double distance(Location l) {
		return MathUtil.distance(this.getX(), this.getY(), this.getZ(), l.getX(), l.getY(), l.getZ());
	}
	public double distanceXZ(net.minecraft.server.Entity e) {
		return MathUtil.distance(this.getX(), this.getZ(), e.locX, e.locZ);
	}
	public double distanceXZ(Location l) {
		return MathUtil.distance(this.getX(), this.getZ(), l.getX(), l.getZ());
	}
	public double distanceSquared(net.minecraft.server.Entity e) {
		return MathUtil.distanceSquared(this.getX(), this.getY(), this.getZ(), e.locX, e.locY, e.locZ);
	}
	public double distanceSquared(Location l) {
		return MathUtil.distanceSquared(this.getX(), this.getY(), this.getZ(), l.getX(), l.getY(), l.getZ());
	}
	public double distanceXZSquared(net.minecraft.server.Entity e) {
		return MathUtil.distanceSquared(this.getX(), this.getZ(), e.locX, e.locZ);
	}
	public double distanceXZSquared(Location l) {
		return MathUtil.distanceSquared(this.getX(), this.getZ(), l.getX(), l.getZ());
	}
	public boolean isNearOf(MinecartMember member) {
		double max = TrainCarts.maxCartDistance * TrainCarts.maxCartDistance;
		if (this.distanceXZSquared(member) > max) return false;
		if (this.isDerailed() || member.isDerailed()) {
			return Math.abs(this.getY() - member.getY()) <= max;
		}
		return true;
	}
	public List<net.minecraft.server.Entity> getNearbyEntities(double radius) {
		return this.getNearbyEntities(radius, radius, radius);
	}
	@SuppressWarnings("unchecked")
	public List<net.minecraft.server.Entity> getNearbyEntities(double x, double y, double z) {
		return this.world.getEntities(this, this.boundingBox.grow(x, y, z));
	}
	public Vector getOffset(ChunkCoordinates to) {
		return new Vector(to.x - this.getX(), to.y - this.getY(), to.z - this.getZ());
	}
	public Vector getOffset(Entity to) {
		return getOffset(to.getLocation());
	}
	public Vector getOffset(MinecartMember to) {
		return new Vector(to.getX() - this.getX(), to.getY() - this.getY(), to.getZ() - this.getZ());
	}
	public Vector getOffset(Location to) {
		return new Vector(to.getX() - this.getX(), to.getY() - this.getY(), to.getZ() - this.getZ());
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
	public int getDirectionDifference(BlockFace dircomparer) {
		return FaceUtil.getFaceYawDifference(this.direction, dircomparer);
	}
	public int getDirectionDifference(MinecartMember comparer) {
		return this.getDirectionDifference(comparer.direction);
	}
	public void updateDirection() {
		this.updateDirection(this.getVelocity());
	}
	public void updateDirection(Vector movement) {
		if (this.isOnVertical() || (this.isFlying() && Math.abs(this.motX) < 0.001 && Math.abs(this.motZ) < 0.001)) {
			if (movement.getY() > 0) {
				this.direction = BlockFace.UP;
			} else {
				this.direction = BlockFace.DOWN;
			}
			this.directionFrom = this.directionTo = this.direction;
			return;
		}
		if (this.isDerailed()) {
			this.direction = FaceUtil.getDirection(movement);
			this.directionFrom = this.directionTo;
			this.directionTo = FaceUtil.getDirection(movement, false);
			return;
		}
		Rails rails = this.getRails();
		if (rails == null) {
			return;
		}
		BlockFace raildirection = rails.getDirection();
		if (this.directionTo == BlockFace.DOWN && rails.isOnSlope()) {
			// Going from vertical down to a slope
			this.direction = this.directionTo = this.directionFrom = raildirection.getOppositeFace();
			return;
		} else if (this.directionTo == BlockFace.UP && rails.isOnSlope()) {
			// Going from vertical up to a sloped rail
			this.direction = this.directionTo = this.directionFrom = raildirection;
			return;
		}
		this.direction = FaceUtil.getRailsCartDirection(raildirection);
		if (movement.getX() == 0 || movement.getZ() == 0) {
			if (FaceUtil.getFaceYawDifference(this.direction, FaceUtil.getDirection(movement)) > 90) {
				this.direction = this.direction.getOppositeFace();
			}
		} else {
			if (MathUtil.getAngleDifference(MathUtil.getLookAtYaw(movement), FaceUtil.faceToYaw(this.direction)) > 90) {
				this.direction = this.direction.getOppositeFace();
			}
		}
		//calculate from and to
		switch (this.direction) {
			case NORTH_WEST :
				if (raildirection == BlockFace.NORTH_EAST) {
					this.directionFrom = BlockFace.NORTH;
					this.directionTo = BlockFace.WEST;
				} else {
					this.directionFrom = BlockFace.WEST;
					this.directionTo = BlockFace.NORTH;
				}
				break;
			case SOUTH_EAST :
				if (raildirection == BlockFace.NORTH_EAST) {
					this.directionFrom = BlockFace.EAST;
					this.directionTo = BlockFace.SOUTH;
				} else {
					this.directionFrom = BlockFace.SOUTH;
					this.directionTo = BlockFace.EAST;
				}
				break;
			case NORTH_EAST :
				if (raildirection == BlockFace.NORTH_WEST) {
					this.directionFrom = BlockFace.NORTH;
					this.directionTo = BlockFace.EAST;
				} else {
					this.directionFrom = BlockFace.EAST;
					this.directionTo = BlockFace.NORTH;
				}
				break;
			case SOUTH_WEST :
				if (raildirection == BlockFace.NORTH_WEST) {
					this.directionFrom = BlockFace.WEST;
					this.directionTo = BlockFace.SOUTH;
				} else {
					this.directionFrom = BlockFace.SOUTH;
					this.directionTo = BlockFace.WEST;
				}
				break;
			default :
				this.directionFrom = this.directionTo = direction;
				break;
		}
	}
	public void updateDirectionTo(MinecartMember member) {
		this.updateDirection(this.getOffset(member));
	}
	public void updateDirectionFrom(MinecartMember member) {
		this.updateDirection(member.getOffset(this));
	}

	/*
	 * Pitch functions
	 */
	public float getPitch() {
		return this.pitch;
	}
	public float getPitchDifference(MinecartMember comparer) {
		return getPitchDifference(comparer.getPitch());
	}
	public float getPitchDifference(float pitchcomparer) {
		return MathUtil.getAngleDifference(this.getPitch(), pitchcomparer);
	}

	/*
	 * Yaw functions
	 */
	public float getYaw() {
		return this.yaw;
	}
	public float getYawDifference(float yawcomparer) {
		return MathUtil.getAngleDifference(this.getYaw(), yawcomparer);
	}

	/*
	 * States
	 */
 	public boolean hasMoved() {
 		return Math.abs(this.getMovedX()) > 0.001 || Math.abs(this.getMovedZ()) > 0.001;
 	}
	public boolean isTurned() {
		return FaceUtil.isSubCardinal(this.direction);
	}
	public boolean isHeadingTo(net.minecraft.server.Entity entity) {
		return this.isHeadingTo(entity.getBukkitEntity());
	}
	public boolean isHeadingTo(Entity entity) {
		return this.isHeadingTo(entity.getLocation());
	}
	public boolean isHeadingTo(ChunkCoordinates location) {
		return MathUtil.isHeadingTo(this.getOffset(location), this.getVelocity());
		
	}
	public boolean isHeadingTo(Location target) {
		return MathUtil.isHeadingTo(this.getLocation(), target, this.getVelocity());
	}
	public boolean isHeadingTo(BlockFace direction) {
		return MathUtil.isHeadingTo(direction, this.getVelocity());
	}
	public boolean isHeadingToTrack(Block track) {
		return this.isHeadingToTrack(track, 0);
	}
	public boolean isHeadingToTrack(Block track, int maxstepcount) {
		if (this.isDerailed()) return false;
		Block from = this.getRailsBlock();
		if (from == null || track == null) return false;
		if (BlockUtil.equals(from, track)) return true;
		if (maxstepcount == 0) maxstepcount = 1 + 2 * BlockUtil.getManhattanDistance(from, track, false);
		TrackIterator iter = new TrackIterator(from, this.directionTo);
		for (;maxstepcount > 0 && iter.hasNext(); --maxstepcount) {
			if (BlockUtil.equals(iter.next(), track)) return true;
		}
		return false;
	}
	public boolean isFollowingOnTrack(MinecartMember member) {
		//checks if this member is able to follow the specified member on the tracks
		if (!this.isNearOf(member)) return false;
		if (this.isDerailed() || member.isDerailed()) return true; //if derailed keep train alive
		if (this.isOnVertical() || member.isOnVertical()) {
			return true; //TEMP HAX
		}
		if (this.isMoving()) {
			Block memberrail = member.getRailsBlock();
			if (memberrail == null) return true; //derailed
			if (this.isHeadingToTrack(memberrail)) {
				return true;
			} else {
				return TrackIterator.isConnected(this.getRailsBlock(), memberrail, true);
			}
		} else {
			return TrackIterator.isConnected(this.getRailsBlock(), member.getRailsBlock(), false);
		}
	}	
	public static boolean isTrackConnected(MinecartMember m1, MinecartMember m2) {
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
			if (!TrackIterator.isConnected(m1.getRailsBlock(), m2.getRailsBlock(), false)) return false;
		}
		return true;
	}

	public boolean isMovingVertical() {
		return super.isOnVertical() || this.direction == BlockFace.UP || this.direction == BlockFace.DOWN;
	}
	public boolean isUnloaded() {
		return this.unloaded;
	}
	public boolean isInChunk(Chunk chunk) {
		return this.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
	}
	public boolean isInChunk(org.bukkit.World world, int cx, int cz) {
		if (world != this.getWorld()) return false;
		if (Math.abs(cx - (super.getLiveBlockX() >> 4)) > 2) return false;
		if (Math.abs(cz - (super.getLiveBlockZ() >> 4)) > 2) return false;
		return true;
	}
	public boolean isRegularMinecart() {
		return this.type == 0;
	}
	public boolean isSingle() {
		return this.group == null || this.group.size() == 1;
	}
	public boolean hasPassenger() {
		return this.passenger != null;
	}
	public Entity getPassenger() {
		return this.passenger == null ? null : this.passenger.getBukkitEntity();
	}
	public Inventory getInventory() {
		return new CraftInventory(this);
	}
	public Inventory getPlayerInventory() {
		if (this.hasPlayerPassenger()) {
			return new CraftInventoryPlayer(((EntityPlayer) this.passenger).inventory);
		} else {
			return new CraftInventory(new MergedInventory(new IInventory[0]));
		}
	}
	public boolean hasPlayerPassenger() {
		return this.passenger != null && this.passenger instanceof EntityPlayer;
	}
	public boolean hasItem(ItemParser item) {
		if (item == null) return false;
		if (item.hasData()) {
			return this.hasItem(item.getTypeId(), item.getData());
		} else {
			return this.hasItem(item.getTypeId());
		}
	}
	public boolean hasItem(Material type, int data) {
		return this.hasItem(type.getId(), data);
	}
	public boolean hasItem(Material type) {
		return this.hasItem(type.getId());
	}
	public boolean hasItem(int typeid) {
		if (!this.isStorageCart()) return false;
		for (ItemStack stack : this.getContents()) {
			if (stack != null) {
				if (stack.id == typeid) {
					return true;
				}
			}
		}
		return false;
	}
	public boolean hasItem(int typeid, int data) {
		if (!this.isStorageCart()) return false;
		for (ItemStack stack : this.getContents()) {
			if (stack != null) {
				if (stack.id == typeid && stack.getData() == data) {
					return true;
				}
			}
		}
		return false;
	}
	public boolean hasItems() {
		if (!this.isStorageCart()) return false;
		for (ItemStack stack : this.getContents()) {
			if (stack != null) return true;
		}
		return false;
	}
	
	/*
	 * Actions
	 */
	public void pushSideways(Entity entity) {
		this.pushSideways(entity, TrainCarts.pushAwayForce);
	}
	public void pushSideways(Entity entity, double force) {
		float yaw = FaceUtil.faceToYaw(this.direction);
		float lookat = MathUtil.getLookAtYaw(this.getBukkitEntity(), entity) - yaw;
		lookat = MathUtil.wrapAngle(lookat);
		if (lookat > 0) {
			yaw -= 180;
		}
		Vector vel = MathUtil.getDirection(yaw, 0).multiply(force);
		entity.setVelocity(vel);
	}
	public void push(Entity entity, double force) {
		Vector offset = this.getOffset(entity);
		MathUtil.setVectorLength(offset, force);
		entity.setVelocity(entity.getVelocity().add(offset));
	}
	public void playLinkEffect() {
		this.playLinkEffect(true);
	}
	public void playLinkEffect(boolean showSmoke) {
		Location loc = this.getLocation();
		if (showSmoke) loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
		loc.getWorld().playEffect(loc, Effect.EXTINGUISH, 0);
	}

	/*
	 * Teleportation
	 */
	public void loadChunks() {
		WorldUtil.loadChunks(this.getWorld(), super.getLiveBlockX() >> 4, super.getLiveBlockZ() >> 4, 2);
	}
	public void teleport(Block railsblock) {
		this.teleport(railsblock.getLocation().add(0.5, 0.5, 0.5));
	}
	public void teleport(Location to) {
		boolean changedWorld = to.getWorld() != this.getWorld();
		this.died = true;
		EntityUtil.teleport(this, to);
		if (changedWorld) {
			this.tracker = new MinecartMemberTrackerEntry(this);
			WorldUtil.setTrackerEntry(this, this.tracker);
		}
		this.teleportImmunityTick = 10;
		this.died = false;
	}

	/**
	 * Gets whether this Minecart and the passenger has immunity as a result of teleportation
	 * 
	 * @return True if it is immune, False if not
	 */
	public boolean isTeleportImmune() {
		return this.teleportImmunityTick > 0;
	}

	public boolean isCollisionIgnored(Entity entity) {
		return isCollisionIgnored(EntityUtil.getNative(entity));
	}
	public boolean isCollisionIgnored(net.minecraft.server.Entity entity) {
		if (entity instanceof MinecartMember) {
			return this.isCollisionIgnored((MinecartMember) entity);
		}
		if (this.ignoreAllCollisions) return true;
		return collisionIgnoreTimes.containsKey(entity.uniqueId);
	}
	public boolean isCollisionIgnored(MinecartMember member) {
		if (this.ignoreAllCollisions || member.ignoreAllCollisions) return true; 
		return this.collisionIgnoreTimes.containsKey(member.uniqueId) || 
				member.collisionIgnoreTimes.containsKey(this.uniqueId);
	}
	public void ignoreCollision(Entity entity, int ticktime) {
		this.ignoreCollision(EntityUtil.getNative(entity), ticktime);
	}
	public void ignoreCollision(net.minecraft.server.Entity entity, int ticktime) {
		collisionIgnoreTimes.put(entity.uniqueId, new AtomicInteger(ticktime));
	}
	public void eject() {
		this.getMinecart().eject();
	}
	public void eject(final Location to) {
		if (this.passenger != null) {
			final Entity passenger = this.passenger.getBukkitEntity();
			this.passenger.setPassengerOf(null);
			CommonUtil.nextTick(new Runnable() {
				public void run() {
					passenger.teleport(to);
				}
			});
		}
	}
	public boolean connect(MinecartMember with) {
		return this.getGroup().connect(this, with);
	}

	public void setItem(int index, net.minecraft.server.ItemStack item) {
		super.setItem(index, item);
		this.update();
	}

	public void update() {
		if (this.dead) return; 
		this.needsUpdate = true;
		this.getGroup().update();
	}

	public boolean isIgnoringCollisions() {
		return this.ignoreAllCollisions;
	}
	public void setIgnoreCollisions(boolean ignoreAll) {
		this.ignoreAllCollisions = ignoreAll;
	}

	/**
	 * Respawns the entity to the client (used to avoid teleport smoothing)
	 */
	public void respawn() {
		if (this.tracker != null) {
			this.tracker.doRespawn();
		}
	}
	public void stop() {
		this.stop(false);
	}
	public void stop(boolean cancelLocationChange) {
		this.motX = 0;
		this.motY = 0;
		this.motZ = 0;
		if (cancelLocationChange) {
			this.locX = this.lastX;
			this.locY = this.lastY;
			this.locZ = this.lastZ;
		}
	}
	public void reverse() {
		this.motX *= -1;
		this.motY *= -1;
		this.motZ *= -1;
		this.b *= -1;
		this.c *= -1;
		this.direction = this.direction.getOppositeFace();
	}

	public MinecartMemberTrackerEntry getTracker() {
		return this.tracker;
	}

	@Override
	public void die() {
		if (!this.died) {
			super.die();
			this.dead = false;
			this.died = true;
			this.clearActiveSigns();
			this.clearActiveDetectors();
			if (this.passenger != null) this.passenger.setPassengerOf(null);
			if (this.group != null) this.group.remove(this);
			CartPropertiesStore.remove(this.uniqueId);
			this.dead = true;
		}
	}
	private int prevcx, prevcz;
	protected void checkChunks(boolean canunload) throws GroupUnloadedException {
		int newcx = MathUtil.locToChunk(this.lastX);
		int newcz = MathUtil.locToChunk(this.lastZ);
		if (newcx != prevcx || newcz != prevcz) {
			prevcx = newcx;
			prevcz = newcz;
			if (canunload) {
				if (!this.world.areChunksLoaded(newcx << 4, this.getLiveBlockY(), newcz << 4, 32)) {
					OfflineGroupManager.hideGroup(this.getGroup());
					throw new GroupUnloadedException();
				}
			} else {
				this.loadChunks();
			}
		}
	}
}