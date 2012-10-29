package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberDeadException;
import com.bergerkiller.bukkit.tc.MoveDirection;
import com.bergerkiller.bukkit.tc.RailType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

import net.minecraft.server.AxisAlignedBB;
import net.minecraft.server.ChunkPosition;
import net.minecraft.server.DamageSource;
import net.minecraft.server.Entity;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.MathHelper;
import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.Packet;
import net.minecraft.server.Packet23VehicleSpawn;
import net.minecraft.server.Vec3D;
import net.minecraft.server.World;
import net.minecraft.server.EntityMinecart;

public class NativeMinecartMember extends EntityMinecart {
	/*
	 * Values taken over from source to use in the m_ function, see attached source links
	 */
	public int fuel;
	private int fuelCheckCounter = 0;
	public boolean isOnMinecartTrack = true;
	public boolean wasOnMinecartTrack = true;

	public static final int FUEL_PER_COAL = 3600;
	private static final double HOR_VERT_TRADEOFF = 2.0;

	public void validate() throws MemberDeadException {
		if (this.dead) {
			this.die();
			throw new MemberDeadException();
		}
	}

	private double getForceSquared() {
		return MathUtil.lengthSquared(this.motX, this.motZ);
	}
	private double getForce() {
		return Math.sqrt(getForceSquared());
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
	public int getLiveBlockX() {
		return MathHelper.floor(this.getX());
	}
	public int getLiveBlockY() {
		return MathHelper.floor(this.getY());
	}
	public int getLiveBlockZ() {
		return MathHelper.floor(this.getZ());
	}
	public int getLastBlockX() {
		return moveinfo.lastBlockX;
	}
	public int getLastBlockY() {
		return moveinfo.lastBlockY;
	}
	public int getLastBlockZ() {
		return moveinfo.lastBlockZ;
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
	public ChunkPosition getBlockPos() {
		return new ChunkPosition(getBlockX(), getBlockY(), getBlockZ());
	}
	public org.bukkit.World getWorld() {
		return world.getWorld();
	}
	public Location getLocation() {
		return new Location(getWorld(), getX(), getY(), getZ(), this.yaw, this.pitch);
	}

	public boolean isMoving() {
		return Math.abs(this.motX) > 0.001 || Math.abs(this.motZ) > 0.001;
	}
	public boolean hasFuel() {
		return this.fuel > 0;
	}

	private boolean ignoreForces() {
		return group().isVelocityAction();
	}

	private MinecartMember member() {
		return (MinecartMember) this;
	}
	private MinecartGroup group() {
		return member().getGroup();
	}

	public NativeMinecartMember(World world, double d0, double d1, double d2, int i) {
		super(world);
		setPosition(d0, d1 + (double) height, d2);
		motX = 0.0D;
		motY = 0.0D;
		motZ = 0.0D;
		lastX = d0;
		lastY = d1;
		lastZ = d2;
		type = i;
		this.locX = this.lastX;
		this.locY = this.lastY;
		this.locZ = this.lastZ;
	}

	/*
	 * Replaced standard droppings based on TrainCarts settings. For source, see:
	 * https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/EntityMinecart.java
	 */
	@Override
	public boolean damageEntity(DamageSource damagesource, int i)
	{
		try {
			if(!this.dead)
			{
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
					if ((damagesource.getEntity() instanceof EntityHuman) && ((EntityHuman)damagesource.getEntity()).abilities.canInstantlyBuild) {
						setDamage(100);
					}
				}
				if(getDamage() > 40) {
					// CraftBukkit start
					List<org.bukkit.inventory.ItemStack> drops = new ArrayList<org.bukkit.inventory.ItemStack>();
					drops.addAll(this.getDrops());

					VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, passenger);
					if(CommonUtil.callEvent(destroyEvent).isCancelled()) {
						setDamage(40);
						return true;
					}
					// CraftBukkit end

					if(this.passenger != null) {
						this.passenger.mount(this);
					}

					for (org.bukkit.inventory.ItemStack stack : drops) {
						this.a(CraftItemStack.createNMSItemStack(stack), 0.0F);
					}
					
					this.die();
				}
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
			drops.add(new CraftItemStack(Item.MINECART.id, 1));
			if (this.isStorageCart()) {
				drops.add(new CraftItemStack(Material.CHEST.getId(), 1));
			} else if (this.isPoweredCart()) {
				drops.add(new CraftItemStack(Material.FURNACE.getId(), 1));
			}
			return drops;
		} else {
			switch (this.type) {
				case 0:
					drops.add(new CraftItemStack(Item.MINECART.id, 1));
					break;
				case 1:
					drops.add(new CraftItemStack(Item.STORAGE_MINECART.id, 1));
					break;
				case 2:
					drops.add(new CraftItemStack(Item.POWERED_MINECART.id, 1));
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
		public MoveDirection moveDirection;
		public int blockX, blockY, blockZ;
		public int lastBlockX, lastBlockY, lastBlockZ;
		public RailType railType;
		public RailType prevRailType = RailType.NONE;
		public BlockFace prevRailDirecton = BlockFace.NORTH;
		public BlockFace railDirection;
		public boolean isSloped;
		public boolean slopeToVert, vertToSlope;

		public MoveInfo(MinecartMember owner) {
			this.owner = owner;
			this.lastBlockX = this.blockX = owner.getLiveBlockX();
			this.lastBlockY = this.blockY = owner.getLiveBlockY();
			this.lastBlockZ = this.blockZ = owner.getLiveBlockZ();
		}

		public boolean blockChanged() {
			return blockX != lastBlockX || blockY != lastBlockY || blockZ != lastBlockZ;
		}

		public void updateBlock() {
			updateBlock(owner.world.getTypeId(blockX, blockY, blockZ));
		}

		public void updateBlock(int railtype) {
			updateBlock(railtype, owner.world.getData(blockX, blockY, blockZ));
		}

		public void updateBlock(int railtype, int raildata) {
			// Update rail type and sloped state
			this.railType = RailType.get(railtype, raildata);
			this.isSloped = this.railType.isTrack() && Util.isSloped(raildata);
			// Update direction
			if (this.railType == RailType.BRAKE || this.railType == RailType.BOOST || this.railType == RailType.DETECTOR) {
				raildata &= 0x7;
			}
			this.railDirection = BlockFace.DOWN;
			if (this.railType.isTrack()) {
				MaterialData data = Material.getMaterial(railtype).getNewData((byte) raildata);
				if (data instanceof Rails) {
					this.railDirection = ((Rails) data).getDirection();
				}
			} else if (this.railType == RailType.PRESSUREPLATE) {
				// driving on top of a pressure plate
				Block curr = world.getWorld().getBlockAt(blockX, blockY, blockZ);
				BlockFace dir = Util.getPlateDirection(curr);
				if (dir == BlockFace.SELF) {
					//set track direction based on direction of this cart (0 or 1)
					if (Math.abs(owner.motX) > Math.abs(owner.motZ)) {
						this.railDirection = BlockFace.SOUTH;
					} else {
						this.railDirection = BlockFace.WEST;
					}
				} else {
					this.railDirection = dir;
				}
			} else if (this.railType == RailType.VERTICAL) {
				this.railDirection = Util.getVerticalRailDirection(raildata);
			}
		}

		public void fillRailsData() {
			this.lastBlockX = this.blockX;
			this.lastBlockY = this.blockY;
			this.lastBlockZ = this.blockZ;
			this.blockX = MathHelper.floor(owner.locX);
			this.blockY = MathHelper.floor(owner.locY);
			this.blockZ = MathHelper.floor(owner.locZ);
			this.slopeToVert = this.vertToSlope = false;

			// Find the rail - first step
			int railtype = world.getTypeId(blockX, blockY - 1, blockZ);
			if (BlockUtil.isRails(railtype) || Util.isPressurePlate(railtype)) {
				this.blockY--;
			} else if (Util.isVerticalRail(railtype) && this.prevRailType != RailType.VERTICAL) {
				this.blockY--;
			} else {
				railtype = world.getTypeId(blockX, blockY, blockZ);
			}
			this.updateBlock(railtype);

			// Snap to slope
			if (this.railType == RailType.NONE && this.prevRailType == RailType.VERTICAL && owner.motY > 0) {
				Block next = owner.world.getWorld().getBlockAt(blockX + this.prevRailDirecton.getModX(), blockY, blockZ + this.prevRailDirecton.getModZ());
				Rails rails = BlockUtil.getRails(next);
				if (rails != null && rails.isOnSlope()) {
					if (rails.getDirection() == this.prevRailDirecton) {
						// Move the minecart to the slope
						blockX = next.getX();
						blockZ = next.getZ();
						this.updateBlock();
						this.vertToSlope = true;
						owner.locX = blockX + 0.5 - 0.49 * this.prevRailDirecton.getModX();
						owner.locZ = blockZ + 0.5 - 0.49 * this.prevRailDirecton.getModZ();
						// Y offset
						owner.locY = blockY + 0.8;
					}
				}
			}

			// Snap from sloped rail to vertical rail
			if (this.isSloped) {
				int aboveType = world.getTypeId(blockX, blockY + 1, blockZ);
				if (Util.isVerticalRail(aboveType)) {
					// Is this minecart moving towards the up-side of the slope?
					if (owner.motY > 0 || owner.isHeadingTo(this.railDirection)) {
						blockY++;
						this.updateBlock(aboveType);
						this.railType = RailType.VERTICAL;
						this.isSloped = false;
						this.slopeToVert = true;
					} else if (this.blockChanged()) {
						this.vertToSlope = true;
					}
				} else if (this.prevRailType == RailType.VERTICAL) {
					this.vertToSlope = true;
				}
			}

			// Calculate the move matrix
			this.moveDirection = MoveDirection.getDirection(this.railDirection, this.isSloped);
			this.prevRailType = this.railType;
			this.prevRailDirecton = this.railDirection;
		}
	}

	public void addFuel(int fuel) {
		this.fuel += fuel;
		if (this.fuel <= 0) {
			this.fuel = 0;
			this.b = this.c = 0.0;
		} else if (this.b == 0.0 && this.c == 0.0) {
			this.b = this.motX;
			this.c = this.motZ;
		}
	}

	/*
	 * Executes the pre-velocity and location updates
	 * Returns whether or not any velocity updates were done. (if the cart is NOT static)
	 */
	public boolean preUpdate(int stepcount) {
		//Some fixed
		if (this.dead) return false;
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

		//fire ticks decrease
		if (this.j() > 0) {
			this.h(this.j() - 1);
		}

		//health regenerate
		if (this.getDamage() > 0) {
			this.setDamage(this.getDamage() - 1);
		}

		// Kill entity if falling into the void
		if (this.locY < -64.0D) {
			this.C();
		}

		//put coal into cart if needed
		if (this.isPoweredCart()) {
			if (this.fuel <= 0) {
				if (fuelCheckCounter++ % 20 == 0 && TrainCarts.useCoalFromStorageCart && this.member().getCoalFromNeighbours()) {
					this.addFuel(FUEL_PER_COAL);
				}
			} else {
				this.fuelCheckCounter = 0;
				if (MathUtil.lengthSquared(this.b, this.c) < 0.001) {
					this.b = this.motX;
					this.c = this.motZ;
				}
			}
		}

		if (!this.ignoreForces()) {
			this.motY -= 0.04;
		}

		this.wasOnMinecartTrack = this.isOnMinecartTrack;
		moveinfo.fillRailsData();
		this.isOnMinecartTrack = moveinfo.railType.isTrack();

		// Ignore forced: Make powered or brake rail act as a regular rail
		if (this.ignoreForces()) {
			if (moveinfo.railType == RailType.BOOST || moveinfo.railType == RailType.BRAKE) {
				moveinfo.railType = RailType.REGULAR;
			}
		}

		//TrainCarts - prevent sloped movement if forces are ignored
		double slopedMotion = this.ignoreForces() ? 0 : 0.0078125; //forward movement on slopes

		if (moveinfo.railType != RailType.NONE) {
			if (moveinfo.isSloped) {
				// Velocity modifier for sloped tracks
				if (this.group().getProperties().isSlowingDown()) {
					this.motX -= moveinfo.railDirection.getModX() * slopedMotion;
					this.motZ -= moveinfo.railDirection.getModZ() * slopedMotion;
				}
			}

			// Turn vertical velocity into horizontal velocity
			if (moveinfo.vertToSlope) {
				double force = this.motY / HOR_VERT_TRADEOFF;
				this.motX += force * moveinfo.railDirection.getModX();
				this.motZ += force * moveinfo.railDirection.getModZ();
				this.motY = 0.0;
			}

			if (moveinfo.railType == RailType.VERTICAL) {
				// Horizontal rail force to motY
				if (moveinfo.slopeToVert) {
					this.motY += MathUtil.length(this.motX, this.motZ) * HOR_VERT_TRADEOFF;
					this.locY = MathUtil.clamp(this.locY, moveinfo.blockY + 0.5, Double.MAX_VALUE);
				} else {
					this.motY -= MathUtil.length(this.motX, this.motZ) * HOR_VERT_TRADEOFF;
				}
				this.motX = 0.0;
				this.motZ = 0.0;
				// Position update
				this.locX = moveinfo.blockX + 0.5;
				this.locZ = moveinfo.blockZ + 0.5;
			} else {
				//snap locY to tracks
				this.locY = (double) moveinfo.blockY + (double) this.height;
				if (moveinfo.isSloped) {
					this.locY += 1.0;
				}

				//slows down minecarts on unpowered powered rails
				if (moveinfo.railType == RailType.BRAKE) {
					if (this.getForceSquared() < 0.0009D) {
						this.motX = 0;
						this.motY = 0;
						this.motZ = 0;
					} else {
						this.motX /= 2;
						this.motY = 0;
						this.motZ /= 2;
					}
				}

				//rail motion is calculated from the rails
				double railMotionX = moveinfo.moveDirection.dx;
				double railMotionZ = moveinfo.moveDirection.dz;
				//reverse motion if needed
				if ((this.motX * railMotionX + this.motZ * railMotionZ) < 0.0) {
					railMotionX = -railMotionX;
					railMotionZ = -railMotionZ;
				}

				//rail motion is applied (railFactor is used to normalize the rail motion to current motion)
				double railFactor = MathUtil.normalize(railMotionX, railMotionZ, this.motX, this.motZ);
				this.motX = railFactor * railMotionX;
				this.motZ = railFactor * railMotionZ;

				//location is updated to follow the tracks
				double oldRailX = (double) moveinfo.blockX + 0.5 + moveinfo.moveDirection.x1 * 0.5;
				double oldRailZ = (double) moveinfo.blockZ + 0.5 + moveinfo.moveDirection.z1 * 0.5;
				double newRailX = (double) moveinfo.blockX + 0.5 + moveinfo.moveDirection.x2 * 0.5;
				double newRailZ = (double) moveinfo.blockZ + 0.5 + moveinfo.moveDirection.z2 * 0.5;

				railMotionX = newRailX - oldRailX;
				railMotionZ = newRailZ - oldRailZ;
				if (railMotionX == 0) {
					railMotionZ *= this.locZ - moveinfo.blockZ;
				} else if (railMotionZ == 0) {
					railMotionX *= this.locX - moveinfo.blockX;
				} else {
					double factor = railMotionX * (this.locX - oldRailX) + railMotionZ * (this.locZ - oldRailZ);
					factor *= 2;
					railMotionX *= factor;
					railMotionZ *= factor;
				}
				this.locX = oldRailX + railMotionX;
				this.locZ = oldRailZ + railMotionZ;
			}

			//finally update the position
			this.setPosition(this.locX, this.locY, this.locZ);
		} else {
			Vector der;
			if (this.onGround) {
				der = this.getDerailedVelocityMod();
			} else {
				der = this.getFlyingVelocityMod();
			}
			this.motX *= der.getX();
			this.motY *= der.getY();
			this.motZ *= der.getZ();
		}
		return true;
	}

	/*
	 * Executes the post-velocity and positioning updates
	 */
	@SuppressWarnings("unchecked")
	public void postUpdate(double speedFactor) throws MemberDeadException, GroupUnloadedException {
		this.validate();
		double motX = MathUtil.fixNaN(this.motX);
		double motY = MathUtil.fixNaN(this.motY);
		double motZ = MathUtil.fixNaN(this.motZ);

		// No vertical motion if stuck to the rails
		if (moveinfo.railType != RailType.VERTICAL && moveinfo.railType != RailType.NONE) {
			motY = 0.0;
		}

		// Modify speed factor to stay within bounds
		speedFactor = MathUtil.clamp(MathUtil.fixNaN(speedFactor, 1), 0.1, 10);

		// Apply speed factor to maxed values
		motX = speedFactor * MathUtil.clamp(motX, this.maxSpeed);
		motY = speedFactor * MathUtil.clamp(motY, this.maxSpeed);
		motZ = speedFactor * MathUtil.clamp(motZ, this.maxSpeed);

		// Move using set motion
		this.move(motX, motY, motZ);

		// Post-move logic
		if (moveinfo.railType != RailType.NONE) {
			if (moveinfo.railType != RailType.VERTICAL) {
				// Snap to rails vertically
				for (Vector mov : moveinfo.moveDirection.raw) {
					if (mov.getY() != 0 && MathHelper.floor(this.locX) - moveinfo.blockX == mov.getX() && MathHelper.floor(this.locZ) - moveinfo.blockZ == mov.getZ()) {
						this.setPosition(this.locX, this.locY + mov.getY(), this.locZ);
						break;
					}
				}
			}

			this.validate();

			// CraftBukkit
			//==================TrainCarts edited==============
			if (this.isPoweredCart() && !ignoreForces()) {
				double fuelPower;
				if (this.hasFuel() && (fuelPower = MathUtil.length(this.b, this.c)) > 0) {
					this.b /= fuelPower;
					this.c /= fuelPower;
					double boost = 0.04 + TrainCarts.poweredCartBoost;

					this.motX *= 0.8;
					this.motZ *= 0.8;
					this.motX += this.b * boost;
					this.motZ += this.c * boost;
				} else {
					if (this.group().getProperties().isSlowingDown()) {
						this.motX *= 0.9;
						this.motZ *= 0.9;
					}
				}
			}
			if (this.group().getProperties().isSlowingDown()) {
				if (this.passenger != null || !this.slowWhenEmpty || !TrainCarts.slowDownEmptyCarts) {
					this.motX *= TrainCarts.slowDownMultiplierNormal;
					this.motZ *= TrainCarts.slowDownMultiplierNormal;
				} else {
					this.motX *= TrainCarts.slowDownMultiplierSlow;
					this.motZ *= TrainCarts.slowDownMultiplierSlow;
				}
			}
			// Prevent vertical motion buildup
			if (moveinfo.railType != RailType.VERTICAL) {
				this.motY = 0.0;
			}
			//==================================================

			// Slope physics and snap to rails logic
			double motLength;
			Vec3D startVector = this.a(this.lastX, this.lastY, this.lastZ);
			Vec3D endVector = this.a(this.locX, this.locY, this.locZ);
			if (moveinfo.isSloped && endVector != null && startVector != null) {
				if (this.group().getProperties().isSlowingDown()) {
					motLength = this.getForce();
					if (motLength > 0) {
						double slopeSlowDown = (startVector.b - endVector.b) * 0.05 / motLength + 1;
						this.motX *= slopeSlowDown;
						this.motZ *= slopeSlowDown;
					}
				}
				this.setPosition(this.locX, endVector.b, this.locZ);
			}

			// Update motion direction based on changed location
			int newBlockX = MathHelper.floor(this.locX);
			int newBlockZ = MathHelper.floor(this.locZ);
			if (newBlockX != moveinfo.blockX || newBlockZ != moveinfo.blockZ) {
				motLength = this.getForce();
				this.motX = motLength * (double) (newBlockX - moveinfo.blockX);
				this.motZ = motLength * (double) (newBlockZ - moveinfo.blockZ);
			}

			//PushX and PushZ updated for Powered Minecarts
			if (this.isPoweredCart()) {
				motLength = MathUtil.length(this.b, this.c);
				if (motLength > 0.01 && this.getForceSquared() > 0.001) {
					this.b /= motLength;
					this.c /= motLength;
					if (this.b * this.motX + this.c * this.motZ < 0) {
						this.b = 0;
						this.c = 0;
					} else {
						this.b = this.motX;
						this.c = this.motZ;
					}
				}
			}

			//Launch on powered rails
			if (moveinfo.railType == RailType.BOOST) {
				motLength = this.getForce();
				if (motLength > 0.01) {
					//simple motion boosting when already moving
					double launchFactor = 0.06D / motLength;
					this.motX += this.motX * launchFactor;
					this.motZ += this.motZ * launchFactor;
				} else if (moveinfo.railDirection == BlockFace.SOUTH) {
					//launch at x-axis
					if (this.world.s(moveinfo.blockX - 1, moveinfo.blockY, moveinfo.blockZ)) {
						this.motX = 0.02;
					} else if (this.world.s(moveinfo.blockX + 1, moveinfo.blockY, moveinfo.blockZ)) {
						this.motX = -0.02;
					}
				} else if (moveinfo.railDirection == BlockFace.WEST) {
					//launch at z-axis
					if (this.world.s(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ - 1)) {
						this.motZ = 0.02;
					} else if (this.world.s(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ + 1)) {
						this.motZ = -0.02;
					}
				}
			}
		}

		// Update rotation
		this.updateRotation();
		this.b(this.yaw, this.pitch);

		// CraftBukkit start
		Location from = new Location(this.world.getWorld(), this.lastX, this.lastY, this.lastZ, this.lastYaw, this.lastPitch);
		Location to = this.getLocation();
		Vehicle vehicle = (Vehicle) this.getBukkitEntity();

		CommonUtil.callEvent(new VehicleUpdateEvent(vehicle));

		if (!from.equals(to)) {
			CommonUtil.callEvent(new VehicleMoveEvent(vehicle, from, to));
		}
		// CraftBukkit end

		// Minecart collisions
		List<Entity> list = this.world.getEntities(this, this.boundingBox.grow(0.2, 0, 0.2));
		if (list != null && !list.isEmpty()) {
			for (Entity entity : list) {
				if (entity != this.passenger && entity.M() && entity instanceof EntityMinecart && MinecartMemberStore.validateMinecart(entity)) {
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
			if (this.fuel <= 0) {
				this.fuel = 0;
				this.b = this.c = 0.0;
			}
			this.d(this.hasFuel());
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
	 * Performs rotation updates for yaw and pitch
	 */
	public void updateRotation() {
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
		} else if (moveinfo.railType == RailType.VERTICAL) {
			newyaw = FaceUtil.faceToYaw(moveinfo.railDirection);
			newpitch = -90;
			mode = false;
		} else if (moveinfo.railType == RailType.PRESSUREPLATE) {
			newpitch = 0.0F; //prevent weird pitch angles on pressure plates
		} else if (movedXZ) {
			if (this.moveinfo.railType.isTrack()) {
				newpitch = -0.8F * MathUtil.getLookAtPitch(movedX, movedY, movedZ);
			} else {
				newpitch = 0.7F * MathUtil.getLookAtPitch(movedX, movedY, movedZ);
			}
			newpitch = MathUtil.clamp(newpitch, 60F);
		}
		setAngleSafe(newyaw, newpitch, mode);
	}

	/*
	 * Overridden function used to let players interact with this minecart
	 * Changes: use my own fuel and changes direction of all attached carts
	 */
	@Override
	public boolean c(EntityHuman entityhuman) {
		if (this.isPoweredCart()) {
			ItemStack itemstack = entityhuman.inventory.getItemInHand();
			if (itemstack != null && itemstack.id == Item.COAL.id) {
				if (--itemstack.count == 0) {
					entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
				}
				this.addFuel(3600);
			}
			this.b = this.locX - entityhuman.locX;
			this.c = this.locZ - entityhuman.locZ;	
			BlockFace dir = FaceUtil.getDirection(this.b, this.c, false);
			if (this.group().isMoving()) {
				if (dir == this.member().getDirectionTo().getOppositeFace()) {
					this.group().reverse();
				}
			}
			return true;
		} else {
			return super.c(entityhuman);
		}
	}

	/*
	 * Overridden to make it save properly (id was faulty)
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

	/*
	 * To be overridden by MinecartMember
	 * Returns if the fuel should be refilled
	 */
	public boolean onCoalUsed() {
		return false;
	}

	/*
	 * Cloned and updated to prevent collisions. For source, see:
	 * https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/Entity.java
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void move(double d0, double d1, double d2) {
		if (this.X) {
			boundingBox.d(d0, d1, d2);
			locX = (boundingBox.a + boundingBox.d) / 2.0D;
			locY = (boundingBox.b + (double) this.height) - (double) this.V;
			locZ = (boundingBox.c + boundingBox.f) / 2.0D;
		} else {
			this.V *= 0.4F;
			double d3 = locX;
			double d4 = locZ;
			if(this.J) {
				this.J = false;
				d0 *= 0.25D;
				d1 *= 0.05D;
				d2 *= 0.25D;
				motX = 0.0D;
				motY = 0.0D;
				motZ = 0.0D;
			}
			double d5 = d0;
			double d6 = d1;
			double d7 = d2;
			AxisAlignedBB axisalignedbb = boundingBox.clone();
			List<AxisAlignedBB> list = world.getCubes(this, boundingBox.a(d0, d1, d2));

			//================================================
			filterCollisionList(list);
			//================================================

			// Collision testing using Y
			for (AxisAlignedBB aabb : list) {
				d1 = aabb.b(boundingBox, d1);
			}
			boundingBox.d(0.0D, d1, 0.0D);
			if(!this.K && d6 != d1) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			// Collision testing using X
			boolean flag1 = onGround || d6 != d1 && d6 < 0.0D;
			for (AxisAlignedBB aabb : list) {
				d0 = aabb.a(boundingBox, d0);
			}
			boundingBox.d(d0, 0.0D, 0.0D);
			if(!this.K && d5 != d0) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			// Collision testing using Z
			for (AxisAlignedBB aabb : list) {
				d2 = aabb.c(boundingBox, d2);
			}
			boundingBox.d(0.0D, 0.0D, d2);
			if(!this.K && d7 != d2) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			double d9;
			double d10;
			int k;
			if(this.W > 0.0F && flag1 && this.V < 0.05F && (d5 != d0 || d7 != d2)) {
				d9 = d0;
				d10 = d1;
				double d11 = d2;
				d0 = d5;
				d1 = (double) this.W;
				d2 = d7;

				AxisAlignedBB axisalignedbb1 = boundingBox.clone();
				boundingBox.c(axisalignedbb);

				list = world.getCubes(this, boundingBox.a(d5, d1, d7));

				//================================================
				filterCollisionList(list);
				//================================================

				for (AxisAlignedBB aabb : list) {
					d1 = aabb.b(boundingBox, d1);
				}
				boundingBox.d(0.0D, d1, 0.0D);
				if(!this.K && d6 != d1)
				{
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				for (AxisAlignedBB aabb : list) {
					d0 = aabb.a(boundingBox, d0);
				}
				boundingBox.d(d0, 0.0D, 0.0D);
				if(!this.K && d5 != d0)
				{
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				for (AxisAlignedBB aabb : list) {
					d2 = aabb.c(boundingBox, d2);
				}
				boundingBox.d(0.0D, 0.0D, d2);
				if(!this.K && d7 != d2) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}
				
				if(!this.K && d6 != d1) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				} else {
					d1 = (double) -this.W;
					for(k = 0; k < list.size(); k++) {
						d1 = ((AxisAlignedBB)list.get(k)).b(boundingBox, d1);
					}
					boundingBox.d(0.0D, d1, 0.0D);
				}
				if (d9 * d9 + d11 * d11 >= d0 * d0 + d2 * d2) {
					d0 = d9;
					d1 = d10;
					d2 = d11;
					boundingBox.c(axisalignedbb1);
				} else {
					double d12 = boundingBox.b - (double)(int) boundingBox.b;
					if (d12 > 0.0D) {
						this.V = (float)((double) this.V + d12 + 0.01D);
					}
				}
			}

			locX = (boundingBox.a + boundingBox.d) / 2D;
			locY = (boundingBox.b + (double) this.height) - (double) this.V;
			locZ = (boundingBox.c + boundingBox.f) / 2D;
			positionChanged = d5 != d0 || d7 != d2;
			this.G = d6 != d1;
			onGround = d6 != d1 && d6 < 0.0D;
			this.H = positionChanged || this.G;
			a(d1, onGround);

			if (d6 != d1) {
				motY = 0.0D;
			}

			//========TrainCarts edit: Math.abs check to prevent collision slowdown=====
			if (d5 != d0) {
				if (Math.abs(motX) > Math.abs(motZ)) {
					motX = 0.0D;
				}
			}
			if (d7 != d2) {
				if (Math.abs(motZ) > Math.abs(motX)) {
					motZ = 0.0D;
				}
			}
			//===========================================================================

			d9 = locX - d3;
			d10 = locZ - d4;
			if (positionChanged) {
				Vehicle vehicle = (Vehicle)getBukkitEntity();
				Block block = world.getWorld().getBlockAt(MathHelper.floor(locX), MathHelper.floor(locY - (double)height), MathHelper.floor(locZ));
				if (d5 > d0) {
					block = block.getRelative(BlockFace.SOUTH);
				} else if (d5 < d0) {
					block = block.getRelative(BlockFace.NORTH);
				} else if (d7 > d2) {
					block = block.getRelative(BlockFace.WEST);
				} else if (d7 < d2) {
					block = block.getRelative(BlockFace.EAST);
				}
				VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, block);
				world.getServer().getPluginManager().callEvent(event);
				//========TrainCarts edit: Stop entire train ============
				if (!this.isOnMinecartTrack || !this.member().isTurned()) {
					this.group().stop();
				}
				//=======================================================
			}
			int l;
			int i1;
			int j1;
			if (this.e_() && this.vehicle == null) {
				this.Q = (float) ((double) this.Q + MathUtil.length(d9, d10) * 0.6D);
				l = MathHelper.floor(locX);
				i1 = MathHelper.floor(locY - 0.2D - (double) height);
				j1 = MathHelper.floor(locZ);
				k = world.getTypeId(l, i1, j1);
				if (k == 0 && world.getTypeId(l, i1 - 1, j1) == Material.FENCE.getId()) {
					k = world.getTypeId(l, i1 - 1, j1);
				}
				if (this.Q > (float) b && k > 0) {
					b = (int) this.Q + 1;
					a(l, i1, j1, k);
					net.minecraft.server.Block.byId[k].b(world, l, i1, j1, this);
				}
			}

			this.D(); // Handle block collisions

			// Fire tick calculation (check using block collision)
			boolean flag2 = this.G();
			if (world.e(boundingBox.shrink(0.001D, 0.001D, 0.001D))) {
				this.burn(1);
				if(!flag2) {
					this.fireTicks++;
					if(this.fireTicks <= 0) {
						EntityCombustEvent event = new EntityCombustEvent(getBukkitEntity(), 8);
						this.world.getServer().getPluginManager().callEvent(event);
						if(!event.isCancelled()) {
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

	/*
	 * Returns if this entity is allowed to collide with another entity
	 */
	private boolean canCollide(Entity e) {
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
			return false;
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
		return MinecartMemberStore.validateMinecart(e);
	}

	/*
	 * Checks if collision with a given block is allowed
	 */
	private boolean canCollide(Block block, BlockFace hitFace) {
		if (Util.isVerticalRail(block.getTypeId())) {
			return false;
		}
		if (moveinfo.railType == RailType.VERTICAL && hitFace != BlockFace.UP && hitFace != BlockFace.DOWN) {
			// Check if the collided block has vertical rails
			if (Util.isVerticalRail(block.getRelative(hitFace).getTypeId())) {
				return false;
			}
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
						if (!canCollide(e)) iter.remove();
						isBlock = false;
						break;
					}
				}
				if (isBlock) {
					Block block = this.world.getWorld().getBlockAt(MathHelper.floor(a.a), MathHelper.floor(a.b), MathHelper.floor(a.c));
					dx = this.locX - block.getX() - 0.5;
					dy = this.locY - block.getY() - 0.5;
					dz = this.locZ - block.getZ() - 0.5;
					if (Math.abs(dx) < 0.1 && Math.abs(dz) < 0.1) {
						dir = dy >= 0.0 ? BlockFace.UP : BlockFace.DOWN;
					} else {
						dir = FaceUtil.getDirection(dx, dz, false);
					}
					if (!this.canCollide(block, dir)) {
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

	public boolean isOnVertical() {
		return this.moveinfo.railType == RailType.VERTICAL;
	}

	public boolean isDerailed() {
		return this.moveinfo.railType == RailType.NONE;
	}

	public boolean isOnSlope() {
		return this.moveinfo.isSloped;
	}

	public boolean isFlying() {
		return this.moveinfo.railType == RailType.NONE && !this.onGround;
	}

	public boolean canBeRidden() { return this.type == 0; }
	public boolean isStorageCart() { return this.type == 1; }
	public boolean isPoweredCart() { return this.type == 2; }
}
