package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.NativeUtil;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.RailType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.MemberBlockChangeEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.railphysics.RailLogic;
import com.bergerkiller.bukkit.tc.railphysics.RailLogicGround;
import com.bergerkiller.bukkit.tc.railphysics.RailLogicVertical;
import com.bergerkiller.bukkit.tc.railphysics.RailLogicVerticalSlopeDown;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.PoweredCartSoundLoop;
import com.bergerkiller.bukkit.tc.utils.SoundLoop;

import net.minecraft.server.v1_4_5.AxisAlignedBB;
import net.minecraft.server.v1_4_5.Block;
import net.minecraft.server.v1_4_5.DamageSource;
import net.minecraft.server.v1_4_5.Entity;
import net.minecraft.server.v1_4_5.EntityHuman;
import net.minecraft.server.v1_4_5.EntityItem;
import net.minecraft.server.v1_4_5.EntityLiving;
import net.minecraft.server.v1_4_5.EntityPlayer;
import net.minecraft.server.v1_4_5.Item;
import net.minecraft.server.v1_4_5.ItemStack;
import net.minecraft.server.v1_4_5.NBTTagCompound;
import net.minecraft.server.v1_4_5.Packet;
import net.minecraft.server.v1_4_5.Packet23VehicleSpawn;
import net.minecraft.server.v1_4_5.World;
import net.minecraft.server.v1_4_5.EntityMinecart;

public abstract class NativeMinecartMember extends EntityMinecart {
	public static final int FUEL_PER_COAL = 3600;
	public static final double GRAVITY_MULTIPLIER = 0.04;
	public static final double VERTRAIL_MULTIPLIER = 0.02;
	public static final double VERT_TO_SLOPE_MIN_VEL = 8.0 * VERTRAIL_MULTIPLIER;
	public static final double SLOPE_VELOCITY_MULTIPLIER = 0.0078125;
	public static final double POWERED_RAIL_START_BOOST = 0.02;
	public int fuel;
	private int fuelCheckCounter = 0;
	private boolean forcedBlockUpdate = true;
	public boolean vertToSlope = false;
	protected BlockFace pushDirection = BlockFace.SELF;
	private final SoundLoop soundLoop;

	public NativeMinecartMember(World world, double d0, double d1, double d2, int i) {
		super(world);
		setPosition(d0, d1 + (double) height, d2);
		this.type = i;
		this.lastX = this.locX;
		this.lastY = this.locY;
		this.lastZ = this.locZ;
		this.motX = 0.0D;
		this.motY = 0.0D;
		this.motZ = 0.0D;
		if (this.isPoweredCart()) {
			this.soundLoop = new PoweredCartSoundLoop(this.member());
		} else {
			this.soundLoop = new SoundLoop(this.member());
		}
	}

	/**
	 * Checks if this minecart is dead, and throws an exception if it is
	 * 
	 * @throws MemberMissingException
	 */
	public void checkMissing() throws MemberMissingException {
		if (this.dead) {
			this.die();
			throw new MemberMissingException();
		} else if (this.member().isUnloaded()) {
			throw new MemberMissingException();
		}
	}

	public double getXZForceSquared() {
		return MathUtil.lengthSquared(this.motX, this.motZ);
	}
	public double getXZForce() {
		return MathUtil.length(this.motX, this.motZ);
	}
	public double getX() {
		return this.locX;
	}
	public double getY() {
		return this.locY;
	}
	public double getZ() {
		return this.locZ;
	}
	public int getLiveChunkX() {
		return MathUtil.toChunk(this.locX);
	}
	public int getLiveChunkY() {
		return MathUtil.toChunk(this.locY);
	}
	public int getLiveChunkZ() {
		return MathUtil.toChunk(this.locZ);
	}
	public int getLiveBlockX() {
		return MathUtil.floor(this.getX());
	}
	public int getLiveBlockY() {
		return MathUtil.floor(this.getY());
	}
	public int getLiveBlockZ() {
		return MathUtil.floor(this.getZ());
	}
	public int getBlockX() {
		return moveinfo.blockX;
	}
	public int getBlockY() {
		return moveinfo.blockY;
	}
	public int getBlockZ() {
		return moveinfo.blockZ;
	}
	public IntVector3 getBlockPos() {
		return new IntVector3(getBlockX(), getBlockY(), getBlockZ());
	}
	public org.bukkit.World getWorld() {
		return world.getWorld();
	}
	public Location getLocation() {
		return new Location(getWorld(), getX(), getY(), getZ(), this.yaw, this.pitch);
	}

	public boolean isMoving() {
		return Math.abs(this.motX) > 0.001 || Math.abs(this.motZ) > 0.001 || Math.abs(this.motY) > 0.001;
	}
	public boolean hasFuel() {
		return this.fuel > 0;
	}

	private MinecartMember member() {
		return (MinecartMember) this;
	}
	private MinecartGroup group() {
		return member().getGroup();
	}


	/*
	 * Replaced standard droppings based on TrainCarts settings. For source, see:
	 * https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/EntityMinecart.java
	 */
	@Override
	public boolean damageEntity(DamageSource damagesource, int i) {
		if (this.dead) {
			return true;
		}
		try {
			// CraftBukkit start
			Vehicle vehicle = (Vehicle) this.getBukkitEntity();
			org.bukkit.entity.Entity passenger = (damagesource.getEntity() == null) ? null : damagesource.getEntity().getBukkitEntity();

			VehicleDamageEvent event = new VehicleDamageEvent(vehicle, passenger, i);

			if (CommonUtil.callEvent(event).isCancelled()) {
				return true;
			}

			i = event.getDamage();
			// CraftBukkit end

			this.i(-this.k());
			this.h(10);
			this.K();
			setDamage(getDamage() + i * 10);
			if (TrainCarts.instantCreativeDestroy) {
				if ((damagesource.getEntity() instanceof EntityHuman) && ((EntityHuman) damagesource.getEntity()).abilities.canInstantlyBuild) {
					setDamage(100);
				}
			}
			if (getDamage() > 40) {
				// CraftBukkit start
				List<org.bukkit.inventory.ItemStack> drops = new ArrayList<org.bukkit.inventory.ItemStack>();
				drops.addAll(this.getDrops());

				VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, passenger);
				if (CommonUtil.callEvent(destroyEvent).isCancelled()) {
					setDamage(40);
					return true;
				}
				// CraftBukkit end

				if (this.passenger != null) {
					this.passenger.mount(this);
				}

				for (org.bukkit.inventory.ItemStack stack : drops) {
					this.a(NativeUtil.getNative(stack), 0.0F);
				}

				this.die();
			}
			return true;
		} catch (Throwable t) {
			TrainCarts.plugin.handle(t);
			return false;
		}
	}

	/**
	 * Gets all the drops to spawn when this minecart is broken
	 * 
	 * @return items to spawn
	 */
	public List<org.bukkit.inventory.ItemStack> getDrops() {
		ArrayList<org.bukkit.inventory.ItemStack> drops = new ArrayList<org.bukkit.inventory.ItemStack>(2);
		if (TrainCarts.breakCombinedCarts) {
			drops.add(new org.bukkit.inventory.ItemStack(Item.MINECART.id, 1));
			if (this.isStorageCart()) {
				drops.add(new org.bukkit.inventory.ItemStack(Material.CHEST.getId(), 1));
			} else if (this.isPoweredCart()) {
				drops.add(new org.bukkit.inventory.ItemStack(Material.FURNACE.getId(), 1));
			}
			return drops;
		} else {
			switch (this.type) {
				case 0:
					drops.add(new org.bukkit.inventory.ItemStack(Item.MINECART.id, 1));
					break;
				case 1:
					drops.add(new org.bukkit.inventory.ItemStack(Item.STORAGE_MINECART.id, 1));
					break;
				case 2:
					drops.add(new org.bukkit.inventory.ItemStack(Item.POWERED_MINECART.id, 1));
					break;
			}
		}
		return drops;
	}

	/*
	 * Stores physics information (since functions are now pretty much scattered around)
	 */
	private final MoveInfo moveinfo = new MoveInfo((MinecartMember) this);
	private class MoveInfo {
		public final MinecartMember owner;
		public int blockX, blockY, blockZ;
		public org.bukkit.block.Block lastBlock, block;
		public RailType railType;
		public RailType prevRailType = RailType.NONE;
		public RailLogic railLogic = RailLogicGround.INSTANCE;
		public RailLogic prevRailLogic = RailLogicGround.INSTANCE;
		public boolean railLogicSnapshotted = false;

		public MoveInfo(MinecartMember owner) {
			this.owner = owner;
			this.blockX = owner.getLiveBlockX();
			this.blockY = owner.getLiveBlockY();
			this.blockZ = owner.getLiveBlockZ();
			this.lastBlock = this.block = owner.world.getWorld().getBlockAt(this.blockX, this.blockY, this.blockZ);
		}

		public boolean blockChanged() {
			return blockX != lastBlock.getX() || blockY != lastBlock.getY() || blockZ != lastBlock.getZ();
		}

		public void updateBlock() {
			updateBlock(owner.world.getTypeId(blockX, blockY, blockZ));
		}

		public void updateBlock(int railtype) {
			if (this.blockChanged()) {
				this.block = owner.world.getWorld().getBlockAt(this.blockX, this.blockY, this.blockZ);
			}
			int raildata = owner.world.getData(blockX, blockY, blockZ);
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
			this.lastBlock = this.block;
			this.blockX = owner.getLiveBlockX();
			this.blockY = owner.getLiveBlockY();
			this.blockZ = owner.getLiveBlockZ();
			owner.vertToSlope = false;

			// Find the rail - first step
			int railtype = world.getTypeId(blockX, blockY - 1, blockZ);
			if (MaterialUtil.ISRAILS.get(railtype) || MaterialUtil.ISPRESSUREPLATE.get(railtype)) {
				this.blockY--;
			} else if (Util.ISVERTRAIL.get(railtype) && this.prevRailType != RailType.VERTICAL) {
				this.blockY--;
			} else {
				railtype = world.getTypeId(blockX, blockY, blockZ);
			}
			this.updateBlock(railtype);

			// Slope UP -> Vertical
			if (this.railType == RailType.VERTICAL && this.prevRailLogic.isSloped()) {
				if (this.prevRailLogic.getDirection() == owner.getDirection().getOppositeFace()) {
					owner.locY = (double) blockY + 0.95;
				}
			}

			// Vertical -> Slope UP
			if (this.railType == RailType.NONE && owner.motY > 0) {
				org.bukkit.block.Block next = owner.world.getWorld().getBlockAt(blockX + this.prevRailLogic.getDirection().getModX(), blockY, blockZ + this.prevRailLogic.getDirection().getModZ());
				Rails rails = BlockUtil.getRails(next);
				if (rails != null && rails.isOnSlope()) {
					if (rails.getDirection() == this.prevRailLogic.getDirection()) {
						// Move the minecart to the slope
						this.blockX = next.getX();
						this.blockZ = next.getZ();
						this.updateBlock();
						owner.locX = (double) this.blockX + 0.5 - 0.49 * this.prevRailLogic.getDirection().getModX();
						owner.locZ = (double) this.blockZ + 0.5 - 0.49 * this.prevRailLogic.getDirection().getModZ();
						// Y offset
						final double transOffset = 0.01; // How high above the slope to teleport to
						owner.locY = this.blockY + transOffset;
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

	public void addFuel(int fuel) {
		this.fuel += fuel;
		if (this.fuel <= 0) {
			this.fuel = 0;
			this.pushDirection = BlockFace.SELF;
		} else if (this.pushDirection == BlockFace.SELF) {
			this.pushDirection = this.member().getDirection();
		}
	}

	@Override
	public void j_() {
		try {
			this.onTick();
		} catch (Throwable t) {
			TrainCarts.plugin.log(Level.SEVERE, "An error occurred while performing minecart physics:");
			t.printStackTrace();
		}
	}

	/**
	 * Executed when the entity tick logic is performed<br>
	 * When calling super.onTick(), it will perform the internal minecart logic
	 */
	public void onTick() {
		try {
			super.j_();
		} catch (Throwable t) {
			System.out.println("An error occurred while performing native minecart physics:");
			t.printStackTrace();
		}
	}

	/**
	 * Executes the block and pre-movement calculations, which handles rail information updates<br>
	 * Physics stage: <b>1</b>
	 */
	public void onPhysicsStart() {
		//Some fixed
		this.motX = MathUtil.fixNaN(this.motX);
		this.motY = MathUtil.fixNaN(this.motY);
		this.motZ = MathUtil.fixNaN(this.motZ);

		// CraftBukkit start
		this.lastX = this.locX;
		this.lastY = this.locY;
		this.lastZ = this.locZ;
		this.lastYaw = this.yaw;
		this.lastPitch = this.pitch;
		// CraftBukkit end

		this.refreshBlockInformation();
	}

	/**
	 * Executes the block change events<br>
	 * Physics stage: <b>2</b>
	 */
	public void onPhysicsBlockChange() {
		// Handle block changes
		this.checkMissing();
		if (moveinfo.blockChanged() || this.forcedBlockUpdate) {
			this.forcedBlockUpdate = false;
			// Perform events and logic - validate along the way
			MemberBlockChangeEvent.call(this.member(), moveinfo.lastBlock, moveinfo.block);
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
		// fire ticks decrease
		if (this.j() > 0) {
			this.h(this.j() - 1);
		}

		// health regenerate
		if (this.getDamage() > 0) {
			this.setDamage(this.getDamage() - 1);
		}

		// Kill entity if falling into the void
		if (this.locY < -64.0D) {
			this.C();
		}

		// Perform gravity
		if (!group().isVelocityAction()) {
			this.motY -= this.moveinfo.railLogic.getGravityMultiplier(this.member());
		}

		// reset fall distance
		if (!this.isDerailed()) {
			this.fallDistance = 0.0f;
		}

		// Perform rails logic
		moveinfo.railLogic.onPreMove(this.member());

		this.setPosition(this.locX, this.locY, this.locZ);
		// Slow down on unpowered booster tracks
		// Note: HAS to be in PreUpdate, otherwise glitches occur!
		if (moveinfo.railType == RailType.BRAKE && !group().isVelocityAction()) {
			if (this.getXZForceSquared() < 0.0009) {
				this.setForceFactor(0.0);
			} else {
				this.setForceFactor(0.5);
			}
		}
	}

	/**
	 * Moves the minecart and performs post-movement logic such as events, onBlockChanged and other (rail) logic
	 * Physics stage: <b>4</b>
	 * 
	 * @param speedFactor to apply when moving
	 * @throws MemberMissingException - thrown when the minecart is dead or dies
	 * @throws GroupUnloadedException - thrown when the group is no longer loaded
	 */
	@SuppressWarnings("unchecked")
	public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
		this.checkMissing();

		// Modify speed factor to stay within bounds
		speedFactor = MathUtil.clamp(MathUtil.fixNaN(speedFactor, 1), 0.1, 10);

		// Apply speed factor to maxed and not-a-number-fixed values
		double motX = speedFactor * MathUtil.clamp(MathUtil.fixNaN(this.motX), this.maxSpeed);
		double motY = speedFactor * MathUtil.clamp(MathUtil.fixNaN(this.motY), this.maxSpeed);
		double motZ = speedFactor * MathUtil.clamp(MathUtil.fixNaN(this.motZ), this.maxSpeed);

		// No vertical motion if stuck to the rails that way
		if (!moveinfo.railLogic.hasVerticalMovement()) {
			motY = 0.0;
		}

		// Move using set motion, and perform post-move rail logic
		this.onMove(motX, motY, motZ);
		this.checkMissing();
		this.moveinfo.railLogic.onPostMove(this.member());

		// Post-move logic
		if (!this.isDerailed()) {
			// Powered minecart physics section
			if (this.isPoweredCart()) {
				// Update pushing direction
				if (this.pushDirection != BlockFace.SELF) {
					BlockFace dir = this.member().getDirection();
					if (this.isOnVertical()) {
						if (dir != this.pushDirection.getOppositeFace()) {
							this.pushDirection = dir;
						}
					} else {
						if (FaceUtil.isVertical(this.pushDirection) || FaceUtil.getFaceYawDifference(dir, this.pushDirection) <= 45) {
							this.pushDirection = dir;
						}
					}
				}
				
				// Velocity boost is applied
				if (!group().isVelocityAction()) {
					if (this.pushDirection != BlockFace.SELF) {
						double boost = 0.04 + TrainCarts.poweredCartBoost;
						this.setForceFactor(0.8);
						this.motX += boost * -FaceUtil.cos(this.pushDirection);
						this.motY += (boost + 0.04) * this.pushDirection.getModY();
						this.motZ += boost * -FaceUtil.sin(this.pushDirection);
					} else {
						if (this.group().getProperties().isSlowingDown()) {
							this.setForceFactor(0.9);
						}
					}
				}
			}

			// Slowing down of minecarts
			if (this.group().getProperties().isSlowingDown()) {
				if (this.passenger != null || !this.slowWhenEmpty || !TrainCarts.slowDownEmptyCarts) {
					this.setForceFactor(TrainCarts.slowDownMultiplierNormal);
				} else {
					this.setForceFactor(TrainCarts.slowDownMultiplierSlow);
				}
			}

			// Launching on powered booster tracks
			if (moveinfo.railType == RailType.BOOST && !group().isVelocityAction()) {
				double motLength = this.getXZForce();
				if (motLength > 0.01) {
					// Simple motion boosting when already moving
					double launchFactor = TrainCarts.poweredRailBoost / motLength;
					this.motX += this.motX * launchFactor;
					this.motZ += this.motZ * launchFactor;
				} else {
					// Launch away from a suffocating block
					BlockFace dir = this.getRailDirection();
					org.bukkit.block.Block block = this.getBlock();
					boolean pushFrom1 = MaterialUtil.SUFFOCATES.get(block.getRelative(dir.getOppositeFace()));
					boolean pushFrom2 = MaterialUtil.SUFFOCATES.get(block.getRelative(dir));
					// If pushing from both directions, block all movement
					if (pushFrom1 && pushFrom2) {
						this.setForceFactor(0.0);
					} else if (pushFrom1 != pushFrom2) {
						// Boosting to the open spot
						final double boost = MathUtil.invert(POWERED_RAIL_START_BOOST, pushFrom2);
						this.motX = boost * dir.getModX();
						this.motZ = boost * dir.getModZ();
					}
				}
			}
		}

		// Update rotation
		this.onRotationUpdate();
		this.b(this.yaw, this.pitch);

		// Invalidate volatile information
		moveinfo.railLogicSnapshotted = false;

		// CraftBukkit start
		Location from = new Location(this.world.getWorld(), this.lastX, this.lastY, this.lastZ, this.lastYaw, this.lastPitch);
		Location to = this.getLocation();
		Vehicle vehicle = (Vehicle) this.getBukkitEntity();

		CommonUtil.callEvent(new VehicleUpdateEvent(vehicle));

		if (!from.equals(to)) {
			// Execute move events
			CommonUtil.callEvent(new VehicleMoveEvent(vehicle, from, to));
			for (org.bukkit.block.Block sign : this.member().getActiveSigns()) {
				SignAction.executeAll(new SignActionEvent(sign, this.member()), SignActionType.MEMBER_MOVE);
			}
		}
		// CraftBukkit end

		// Minecart collisions
		List<Entity> list = this.world.getEntities(this, this.boundingBox.grow(0.2, 0, 0.2));
		if (list != null && !list.isEmpty()) {
			for (Entity entity : list) {
				if (entity != this.passenger && entity.M() && entity instanceof EntityMinecart) {
					entity.collide(this);
				}
			}
		}

		// Ensure that null or dead passengers are cleared
		if (this.passenger != null && this.passenger.dead) {
			this.passenger.vehicle = null; // CraftBukkit
			this.passenger = null;
		}

		// Fuel update routines
		if (this.isPoweredCart()) {
			if (this.fuel > 0) {
				this.fuel--;
				if (this.fuel == 0) {
					//TrainCarts - Actions to be done when empty
					if (this.onCoalUsed()) {
						this.addFuel(FUEL_PER_COAL); //Refill
					}
				}
			}
			// Put coal into cart if needed
			if (this.fuel <= 0) {
				if (fuelCheckCounter++ % 20 == 0 && TrainCarts.useCoalFromStorageCart && this.member().getCoalFromNeighbours()) {
					this.addFuel(FUEL_PER_COAL);
				}
			} else {
				this.fuelCheckCounter = 0;
			}
			if (this.fuel <= 0) {
				this.fuel = 0;
				this.pushDirection = BlockFace.SELF;
			}
			this.e(this.hasFuel());

			// Play additional sound effects
			this.soundLoop.onTick();
		}
	}

	private void setAngleSafe(float newyaw, float pitch, boolean mode) {
		if (MathUtil.getAngleDifference(this.yaw, newyaw) > 170) {
			this.yaw = MathUtil.wrapAngle(newyaw + 180);
			this.pitch = mode ? -pitch : (pitch - 180f);
		} else {
			this.yaw = newyaw;
			this.pitch = pitch;
		}
	}

	/**
	 * Called when the blocks below this minecart change block coordinates
	 * 
	 * @param from block - the old block
	 * @param to block - the new block
	 */
	public abstract void onBlockChange(org.bukkit.block.Block from, org.bukkit.block.Block to);

	/**
	 * Performs rotation updates for yaw and pitch
	 */
	public void onRotationUpdate() {
		//Update yaw and pitch based on motion
		double movedX = this.lastX - this.locX;
		double movedY = this.lastY - this.locY;
		double movedZ = this.lastZ - this.locZ;
		boolean movedXZ = MathUtil.lengthSquared(movedX, movedZ) > 0.001;
		float newyaw = movedXZ ? MathUtil.getLookAtYaw(movedX, movedZ) : this.yaw;
		float newpitch = this.pitch;
		boolean mode = true;
		if (this.onGround) {
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

	/**
	 * Overridden function used to let players interact with this minecart
	 * Changes: use my own fuel and changes direction of all attached carts
	 */
	@Override
	public boolean a(EntityHuman entityhuman) {
		if (this.isPoweredCart()) {
			ItemStack itemstack = entityhuman.inventory.getItemInHand();
			if (itemstack != null && itemstack.id == Item.COAL.id) {
				if (--itemstack.count == 0) {
					entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
				}
				this.addFuel(3600);
			}
			if (this.isOnVertical()) {
				this.pushDirection = Util.getVerticalFace((this.locY - entityhuman.locY) > 0.0);
			} else {
				BlockFace dir = FaceUtil.getRailsCartDirection(this.getRailDirection());
				if (MathUtil.isHeadingTo(dir, new Vector(this.locX - entityhuman.locX, 0.0, this.locZ - entityhuman.locZ))) {
					this.pushDirection = dir;
				} else {
					this.pushDirection = dir.getOppositeFace();
				}
			}
			if (this.group().isMoving()) {
				if (this.pushDirection == this.member().getDirection().getOppositeFace()) {
					this.group().reverse();
					// Prevent push direction being inverted
					this.pushDirection = this.pushDirection.getOppositeFace();
				}
			}
			return true;
		} else {
			return super.a(entityhuman);
		}
	}

	/**
	 * Main saving function - overridden to make it save properly (id was faulty)
	 */
	@Override
	public boolean c(NBTTagCompound nbttagcompound) {
		if (!this.dead) {
			nbttagcompound.setString("id", "Minecart");
			this.d(nbttagcompound);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Saving function - overridden to save powered minecart pushing 
	 * direction and fuel properly (allow vertical)
	 */
	@Override
	public void b(NBTTagCompound nbttagcompound) {
		super.b(nbttagcompound);
		if (this.isPoweredCart()) {
			nbttagcompound.setShort("Fuel", (short) this.fuel);
			nbttagcompound.setDouble("PushX", this.pushDirection.getModX());
			nbttagcompound.setDouble("PushZ", this.pushDirection.getModZ());
		}
	}

	/**
	 * Checks if new coal can be used
	 * 
	 * @return True if new coal can be put into the powered minecart, False if not
	 */
	public abstract boolean onCoalUsed();

	/**
	 * Cloned move function and updated to prevent collisions. For source, see:
	 * https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/Entity.java
	 */
	@SuppressWarnings("unchecked")
	public void onMove(double d0, double d1, double d2) {
		if (this.Y) {
			this.boundingBox.d(d0, d1, d2);
			this.locX = (this.boundingBox.a + this.boundingBox.d) / 2.0D;
			this.locY = (this.boundingBox.b + (double) this.height) - (double) this.W;
			this.locZ = (this.boundingBox.c + this.boundingBox.f) / 2.0D;
		} else {
			this.W *= 0.4F;
			double d3 = this.locX;
			double d4 = this.locY;
			double d5 = this.locZ;
			if (this.J) {
				this.J = false;
				d0 *= 0.25D;
				d1 *= 0.05D;
				d2 *= 0.25D;
				this.motX = 0.0D;
				this.motY = 0.0D;
				this.motZ = 0.0D;
			}
			double d6 = d0;
			double d7 = d1;
			double d8 = d2;
			AxisAlignedBB axisalignedbb = this.boundingBox.clone();
			List<AxisAlignedBB> list = this.world.getCubes(this, boundingBox.a(d0, d1, d2));

			//================================================
			filterCollisionList(list);
			//================================================

			// Collision testing using Y
			for (AxisAlignedBB aabb : list) {
				d1 = aabb.b(this.boundingBox, d1);
			}
			this.boundingBox.d(0.0D, d1, 0.0D);
			if(!this.K && d7 != d1) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			// Collision testing using X
			boolean flag1 = this.onGround || d7 != d1 && d7 < 0.0D;
			for (AxisAlignedBB aabb : list) {
				d0 = aabb.a(this.boundingBox, d0);
			}
			this.boundingBox.d(d0, 0.0D, 0.0D);
			if(!this.K && d6 != d0) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			// Collision testing using Z
			for (AxisAlignedBB aabb : list) {
				d2 = aabb.c(this.boundingBox, d2);
			}
			this.boundingBox.d(0.0D, 0.0D, d2);
			if (!this.K && d8 != d2) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			double d10;
			double d11;
			double d12;

			if(this.X > 0.0F && flag1 && this.W < 0.05F && (d6 != d0 || d8 != d2)) {
				d10 = d0;
				d11 = d1;
				d12 = d2;
				d0 = d6;
				d1 = (double) this.X;
				d2 = d8;

				AxisAlignedBB axisalignedbb1 = this.boundingBox.clone();
				this.boundingBox.c(axisalignedbb);

				list = world.getCubes(this, this.boundingBox.a(d6, d1, d8));

				//================================================
				filterCollisionList(list);
				//================================================

				for (AxisAlignedBB aabb : list) {
					d1 = aabb.b(this.boundingBox, d1);
				}
				this.boundingBox.d(0.0D, d1, 0.0D);
				if(!this.K && d7 != d1) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				for (AxisAlignedBB aabb : list) {
					d0 = aabb.a(this.boundingBox, d0);
				}
				this.boundingBox.d(d0, 0.0D, 0.0D);
				if (!this.K && d6 != d0) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				for (AxisAlignedBB aabb : list) {
					d2 = aabb.c(this.boundingBox, d2);
				}
				this.boundingBox.d(0.0D, 0.0D, d2);
				if (!this.K && d8 != d2) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				if (!this.K && d7 != d1) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				} else {
					d1 = (double) -this.X;
					for (int k = 0; k < list.size(); k++) {
						d1 = list.get(k).b(this.boundingBox, d1);
					}
					this.boundingBox.d(0.0D, d1, 0.0D);
				}
				if (d10 * d10 + d12 * d12 >= d0 * d0 + d2 * d2) {
					d0 = d10;
					d1 = d11;
					d2 = d12;
					this.boundingBox.c(axisalignedbb1);
				} else {
					double d13 = this.boundingBox.b - (double)(int) this.boundingBox.b;
					if (d13 > 0.0D) {
						this.W = (float)((double) this.W + d13 + 0.01D);
					}
				}
			}

			this.locX = (this.boundingBox.a + this.boundingBox.d) / 2D;
			this.locY = (this.boundingBox.b + (double) this.height) - (double) this.W;
			this.locZ = (this.boundingBox.c + this.boundingBox.f) / 2D;
			this.positionChanged = d6 != d0 || d8 != d2;
			this.G = d7 != d1;
			this.onGround = d7 != d1 && d7 < 0.0D;
			this.H = positionChanged || this.G;
			a(d1, this.onGround);

			if (d7 != d1) {
				this.motY = 0.0D;
			}

			//========TrainCarts edit: Math.abs check to prevent collision slowdown=====
			if (d6 != d0) {
				if (Math.abs(motX) > Math.abs(motZ)) {
					this.motX = 0.0D;
				}
			}
			if (d8 != d2) {
				if (Math.abs(motZ) > Math.abs(motX)) {
					this.motZ = 0.0D;
				}
			}
			//===========================================================================

			d10 = this.locX - d3;
			d11 = this.locY - d4;
			d12 = this.locZ - d5;
			if (positionChanged) {
				Vehicle vehicle = (Vehicle)getBukkitEntity();
				org.bukkit.block.Block block = world.getWorld().getBlockAt(MathUtil.floor(locX), MathUtil.floor(locY - (double)height), MathUtil.floor(locZ));
				if (d6 > d0) {
					block = block.getRelative(BlockFace.EAST);
				} else if (d6 < d0) {
					block = block.getRelative(BlockFace.WEST);
				} else if (d8 > d2) {
					block = block.getRelative(BlockFace.SOUTH);
				} else if (d8 < d2) {
					block = block.getRelative(BlockFace.NORTH);
				}
				VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, block);
				world.getServer().getPluginManager().callEvent(event);
			}

			if (this.f_() && this.vehicle == null) {
                int i = MathUtil.floor(this.locX);
                int j = MathUtil.floor(this.locY - 0.2D - (double) this.height);
                int k = MathUtil.floor(this.locZ);
                int l = this.world.getTypeId(i, j, k);

                if (l == 0 && this.world.getTypeId(i, j - 1, k) == Material.FENCE.getId()) {
                    l = this.world.getTypeId(i, j - 1, k);
                }

                if (l != Material.LADDER.getId()) {
                    d11 = 0.0D;
                }

                this.Q = (float) ((double) this.Q + Math.sqrt(d10 * d10 + d12 * d12) * 0.6D);
                this.R = (float) ((double) this.R + Math.sqrt(d10 * d10 + d11 * d11 + d12 * d12) * 0.6D);
                if (this.R > (float) this.c && l > 0) {
                    this.c = (int) this.R + 1;
                    if (this.H()) {
                        float f = (float) Math.sqrt(this.motX * this.motX * 0.20000000298023224D + this.motY * this.motY + this.motZ * this.motZ * 0.20000000298023224D) * 0.35F;

                        if (f > 1.0F) {
                            f = 1.0F;
                        }

                        this.world.makeSound(this, "liquid.swim", f, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                    }

                    this.a(i, j, k, l);
					Block.byId[k].b(this.world, i, j, k, this);
				}
			}

			this.D(); // Handle block collisions

			// Fire tick calculation (check using block collision)
			boolean flag2 = this.G();
			if (this.world.e(boundingBox.shrink(0.001D, 0.001D, 0.001D))) {
				this.burn(1);
				if(!flag2) {
					this.fireTicks++;
					if(this.fireTicks <= 0) {
						EntityCombustEvent event = new EntityCombustEvent(getBukkitEntity(), 8);
						this.world.getServer().getPluginManager().callEvent(event);
						if (!event.isCancelled()) {
							this.setOnFire(event.getDuration());
						}
					} else {
						this.setOnFire(8);
					}
				}
			} else if (this.fireTicks <= 0) {
				this.fireTicks = -this.maxFireTicks;
			}
			if (flag2 && this.fireTicks > 0) {
				this.world.makeSound(this, "random.fizz", 0.7F, 1.6F + (random.nextFloat() - random.nextFloat()) * 0.4F);
				this.fireTicks = -this.maxFireTicks;
			}
		}
	}

	/**
	 * Handles the collision of this minecart with another Entity
	 * 
	 * @param e entity with which is collided
	 * @return True if collision is allowed, False if it is ignored
	 */
	public boolean onEntityCollision(Entity e) {
		MinecartMember mm1 = this.member();
		if (mm1.isCollisionIgnored(e) || mm1.isUnloaded()) return false;
		if (e.dead || this.dead) return false;
		if (this.group().isVelocityAction()) return false;
		if (e instanceof MinecartMember) {
			//colliding with a member in the group, or not?
			MinecartMember mm2 = (MinecartMember) e;
			if (mm2.isUnloaded()) {
				// The minecart is unloaded - ignore it
				return false;
			} else if (mm1.getGroup() == mm2.getGroup()) {
				//Same group, but do prevent penetration
				if (mm1.distance(mm2) > 0.5) {
					return false;
				}
			} else if (!mm1.getGroup().getProperties().getColliding()) {
				//Allows train collisions?
				return false;
			} else if (!mm2.getGroup().getProperties().getColliding()) {
				//Other train allows train collisions?
				return false;
			} else if (mm2.getGroup().isVelocityAction()) {
				//Is this train targeting?
				return false;
			}
			// Check if both minecarts are on the same vertical column
			RailLogic logic1 = mm1.getRailLogic();
			if (logic1 instanceof RailLogicVerticalSlopeDown) {
				RailLogic logic2 = mm2.getRailLogic();
				if (logic2 instanceof RailLogicVerticalSlopeDown) {
					org.bukkit.block.Block b1 = mm1.getBlock(logic1.getDirection());
					org.bukkit.block.Block b2 = mm2.getBlock(logic2.getDirection());
					if (BlockUtil.equals(b1, b2)) {
						return false;
					}
				}
			}
			return true;
		} else if (e instanceof EntityLiving && e.vehicle != null && e.vehicle instanceof EntityMinecart) {
			//Ignore passenger collisions
			return false;
		} else {
			TrainProperties prop = this.group().getProperties();
			// Is it picking up this item?
			if (e instanceof EntityItem && this.member().getProperties().canPickup()) {
				return false;
			}

			//No collision is allowed? (Owners override)
			if (!prop.getColliding()) {
				if (e instanceof EntityPlayer) {
					Player p = (Player) e.getBukkitEntity();
					if (!prop.isOwner(p)) {
						return false;
					}
				} else {
					return false;
				}
			}

			// Collision modes
			if (!prop.getCollisionMode(e.getBukkitEntity()).execute(this.member(), e.getBukkitEntity())) {
				return false;
			}
		}
		// Collision occurred, collided head-on? Stop the entire train
		if (this.member().isHeadingTo(e)) {
			this.group().stop();
		}
		return true;
	}
	
	/**
	 * Handles the collision of this minecart with a Block
	 * 
	 * @param block with which this minecart collided
	 * @param hitFace of the block that the minecart hit
	 * @return True if collision is allowed, False if it is ignored
	 */
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
		if (!this.member().isTurned() && hitFace.getOppositeFace() == this.member().getDirectionTo() && !this.isDerailed()) {
			// Cancel collisions with blocks at the heading of sloped rails
			if (this.isOnSlope() && hitFace == this.getRailDirection().getOppositeFace()) {
				// Vertical rail above?
				if (Util.isVerticalAbove(this.getBlock(), this.getRailDirection())) {
					return false;
				}
			}
			// Stop the train
			this.group().stop();
		}
		return true;
	}

	/*
	 * Prevents passengers and Minecarts from colliding with Minecarts
	 */
	@SuppressWarnings("unchecked")
	private void filterCollisionList(List<AxisAlignedBB> list) {		
		try {
			// Shortcut to prevent unneeded logic
			if (list.isEmpty()) {
				return;
			}
			List<Entity> entityList = this.world.entityList;

			Iterator<AxisAlignedBB> iter = list.iterator();
			AxisAlignedBB a;
			boolean isBlock;
			double dx, dy, dz;
			BlockFace dir;
			while (iter.hasNext()) {
				a = iter.next();
				isBlock = true;
				for (Entity e : entityList) {
					if (e.boundingBox == a) {
						if (!onEntityCollision(e)) iter.remove();
						isBlock = false;
						break;
					}
				}
				if (isBlock) {
					org.bukkit.block.Block block = this.world.getWorld().getBlockAt(MathUtil.floor(a.a), MathUtil.floor(a.b), MathUtil.floor(a.c));
					
					dx = this.locX - block.getX() - 0.5;
					dy = this.locY - block.getY() - 0.5;
					dz = this.locZ - block.getZ() - 0.5;
					if (Math.abs(dx) < 0.1 && Math.abs(dz) < 0.1) {
						dir = Util.getVerticalFace(dy >= 0.0);
					} else {
						dir = FaceUtil.getDirection(dx, dz, false);
					}
					if (!this.onBlockCollision(block, dir)) {
						iter.remove();
					}
				}
			}
		} catch (ConcurrentModificationException ex) {
			TrainCarts.plugin.log(Level.WARNING, "Another plugin is interacting with the world entity list from another thread, please check your plugins!");
		}
	}

	/**
	 * Gets the packet to spawn this Minecart Member
	 * 
	 * @return spawn packet
	 */
	public Packet getSpawnPacket() {
		return new Packet23VehicleSpawn(this, 10 + this.type);
	}

	public BlockFace getRailDirection() {
		return this.getRailLogic().getDirection();
	}

	public void setVelocity(Vector velocity) {
		this.motX = velocity.getX();
		this.motZ = velocity.getZ();
		this.motY = velocity.getY();
	}

	public Vector getVelocity() {
		return new Vector(this.motX, this.motY, this.motZ);
	}

	public void setForceFactor(final double factor) {
		this.motX *= factor;
		this.motY *= factor;
		this.motZ *= factor;
	}

	/**
	 * Gets the block this minecart is currently in, or driving on
	 * 
	 * @return Rail block or block at minecart position
	 */
	public org.bukkit.block.Block getBlock() {
		return moveinfo.block;
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
			return RailLogic.get(this.member());
		}
	}

	public boolean hasBlockChanged() {
		return moveinfo.blockChanged();
	}

	public boolean isDerailed() {
		return this.moveinfo.railType == RailType.NONE;
	}

	public boolean isOnSlope() {
		return this.getRailLogic().isSloped();
	}

	public boolean isFlying() {
		return this.moveinfo.railType == RailType.NONE && !this.onGround;
	}

	public boolean isMovingVerticalOnly() {
		return this.isMovingVertically() && !this.isMovingHorizontally();
	}

	public boolean isMovingVertically() {
		return Math.abs(MathUtil.wrapAngle(this.pitch)) == 90f && (this.motY > 0.001 || (this.motY < -0.001 && !this.onGround));
	}

	public boolean isMovingHorizontally() {
		return Math.abs(this.motX) >= 0.001 || Math.abs(this.motZ) >= 0.001;
	}

	/**
	 * Checks whether this Minecart can be entered and ridden by living entities
	 * 
	 * @return True if it can be ridden, False if not
	 */
	public boolean canBeRidden() {
		return this.type == 0;
	}

	/**
	 * Checks whether this Minecart contains a chest with items and can be opened by players
	 * 
	 * @return True if it is a storage minecart, False if not
	 */
	public boolean isStorageCart() {
		return this.type == 1;
	}

	/**
	 * Checks whether this Minecart contains a furnace which powers the train using coal
	 * 
	 * @return True if it is a powered minecart, False if not
	 */
	public boolean isPoweredCart() {
		return this.type == 2;
	}
}
