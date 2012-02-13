package com.bergerkiller.bukkit.tc;

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
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;

import net.minecraft.server.AxisAlignedBB;
import net.minecraft.server.Block;
import net.minecraft.server.BlockMinecartTrack;
import net.minecraft.server.DamageSource;
import net.minecraft.server.Entity;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.MathHelper;
import net.minecraft.server.Vec3D;
import net.minecraft.server.World;
import net.minecraft.server.EntityMinecart;

@SuppressWarnings("rawtypes")
public class NativeMinecartMember extends EntityMinecart {
	/*
	 * Values taken over from source to use in the m_ function, see attached source links
	 */
	public int fuel;
	public boolean isOnMinecartTrack = true;
	public boolean wasOnMinecartTrack = true;
	private static final int[][][] matrix = new int[][][] { 
		{ { 0, 0, -1 }, { 0, 0, 1 } }, { { -1, 0, 0 }, { 1, 0, 0 } },
		{ { -1, -1, 0 }, { 1, 0, 0 } }, { { -1, 0, 0 }, { 1, -1, 0 } },
		{ { 0, 0, -1 }, { 0, -1, 1 } }, { { 0, -1, -1 }, { 0, 0, 1 } },
		{ { 0, 0, 1 }, { 1, 0, 0 } }, { { 0, 0, 1 }, { -1, 0, 0 } },
		{ { 0, 0, -1 }, { -1, 0, 0 } }, { { 0, 0, -1 }, { 1, 0, 0 } } };

	public void validate() throws MemberDeadException {
		if (this.dead) {
			this.die();
			throw new MemberDeadException();
		}
	}

	public double getForceSquared() {
		return MathUtil.lengthSquared(this.motX, this.motZ);
	}
	public double getForce() {
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
	public int getBlockX() {
		return MathHelper.floor(this.getX());
	}
	public int getBlockY() {
		return MathHelper.floor(this.getY());
	}
	public int getBlockZ() {
		return MathHelper.floor(this.getZ());
	}
	public org.bukkit.World getWorld() {
		return world.getWorld();
	}
	public Location getLocation() {
		if (this instanceof MinecartMember) {
			MinecartMember mm = (MinecartMember) this;
			return new Location(getWorld(), getX(), getY(), getZ(), mm.getYaw(), mm.getPitch());
		} else {
			return new Location(getWorld(), getX(), getY(), getZ(), this.yaw, this.pitch);
		}
	}

	public boolean isMoving() {
 		return Math.abs(this.motX) > 0.001 || Math.abs(this.motZ) > 0.001;
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
		super(world, d0, d1, d2, i);
		this.locX = this.lastX;
		this.locY = this.lastY;
		this.locZ = this.lastZ;
	}

	/*
	 * Replaced standard droppings based on TrainCarts settings. For source, see:
	 * https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/EntityMinecart.java
	 */
	@Override
	public boolean damageEntity(DamageSource damagesource, int i) {
		if (!this.world.isStatic && !this.dead) {
			// CraftBukkit start
			Vehicle vehicle = (Vehicle) this.getBukkitEntity();
			org.bukkit.entity.Entity passenger = (damagesource.getEntity() == null) ? null : damagesource.getEntity().getBukkitEntity();

			VehicleDamageEvent event = new VehicleDamageEvent(vehicle, passenger, i);
			this.world.getServer().getPluginManager().callEvent(event);

			if (event.isCancelled()) {
				return true;
			}

			i = event.getDamage();
			// CraftBukkit end

			this.d(-this.m());
			this.c(10);
			this.aM();
			this.setDamage(this.getDamage() + i * 10);
			if (this.getDamage() > 40) {
				if (this.passenger != null) {
					this.passenger.mount(this);
				}

				// CraftBukkit start
				VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, passenger);
				this.world.getServer().getPluginManager().callEvent(destroyEvent);

				if (destroyEvent.isCancelled()) {
					this.setDamage(40); // Maximize damage so this doesn't get triggered again right away
					return true;
				}
				// CraftBukkit end

				this.die();
				if (TrainCarts.breakCombinedCarts || this.type == 0) {
					if (TrainCarts.spawnItemDrops) this.a(Item.MINECART.id, 1, 0.0F);
				}
				if (this.type == 1) {
					EntityMinecart entityminecart = this;

					for (int j = 0; j < entityminecart.getSize(); ++j) {
						ItemStack itemstack = entityminecart.getItem(j);

						if (itemstack != null) {
							float f = this.random.nextFloat() * 0.8F + 0.1F;
							float f1 = this.random.nextFloat() * 0.8F + 0.1F;
							float f2 = this.random.nextFloat() * 0.8F + 0.1F;

							while (itemstack.count > 0) {
								int k = this.random.nextInt(21) + 10;

								if (k > itemstack.count) {
									k = itemstack.count;
								}

								itemstack.count -= k;
								EntityItem entityitem = new EntityItem(this.world, this.locX + (double) f, this.locY + (double) f1, this.locZ + (double) f2, new ItemStack(itemstack.id, k, itemstack.getData()));
								float f3 = 0.05F;

								entityitem.motX = (double) ((float) this.random.nextGaussian() * f3);
								entityitem.motY = (double) ((float) this.random.nextGaussian() * f3 + 0.2F);
								entityitem.motZ = (double) ((float) this.random.nextGaussian() * f3);
								this.world.addEntity(entityitem);
							}
						}
					}
					if (TrainCarts.breakCombinedCarts) {
						if (TrainCarts.spawnItemDrops) this.a(Block.CHEST.id, 1, 0.0F);
					} else {
						if (TrainCarts.spawnItemDrops) this.a(Material.STORAGE_MINECART.getId(), 1, 0.0F);
					}
				} else if (this.type == 2) {
					if (TrainCarts.breakCombinedCarts) {
						if (TrainCarts.spawnItemDrops) this.a(Block.FURNACE.id, 1, 0.0F);
					} else {
						if (TrainCarts.spawnItemDrops) this.a(Material.POWERED_MINECART.getId(), 1, 0.0F);
					}
				}
			}

			return true;
		} else {
			return true;
		}
	}

	/*
	 * Stores physics information (since functions are now pretty much scattered around)
	 */
	private MoveInfo moveinfo = new MoveInfo();
	private class MoveInfo {
		public boolean isRailed; //sets what post-velocity update to run
		public double prevX;
		public double prevY;
		public double prevZ;
		public float prevYaw;
		public float prevPitch;
		public int[][] moveMatrix;
		public int blockX;
		public int blockY;
		public int blockZ;
		public int raildata;
		public Vec3D vec3d;
		public boolean isLaunching;
		public Location getPrevLoc(World world) {
			return new Location(world.getWorld(), prevX, prevY, prevZ, prevYaw, prevPitch);
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
		moveinfo.prevX = this.locX;
		moveinfo.prevY = this.locY;
		moveinfo.prevZ = this.locZ;
		moveinfo.prevYaw = this.yaw;
		moveinfo.prevPitch = this.pitch;
		// CraftBukkit end

		if (this.l() > 0) {
			this.c(this.l() - 1);
		}

		if (this.getDamage() > 0) {
			this.setDamage(this.getDamage() - 1);
		}

		this.lastX = this.locX;
		this.lastY = this.locY;
		this.lastZ = this.locZ;
		this.wasOnMinecartTrack = this.isOnMinecartTrack;
		this.motY -= 0.04 * (1 / stepcount);
		moveinfo.blockX = MathHelper.floor(this.locX);
		moveinfo.blockY = MathHelper.floor(this.locY);
		moveinfo.blockZ = MathHelper.floor(this.locZ);

		//get the type of rails below
		int railtype = this.world.getTypeId(moveinfo.blockX, moveinfo.blockY - 1, moveinfo.blockZ);
		if (Util.isRails(railtype)) {
			moveinfo.isRailed = true;
			--moveinfo.blockY;
		} else {
			railtype = this.world.getTypeId(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ);
			moveinfo.isRailed = Util.isRails(railtype);
		}
		this.isOnMinecartTrack = moveinfo.isRailed ? BlockUtil.isRails(railtype) : false;
		
		moveinfo.isLaunching = false;

		//TrainCarts - prevent sloped movement if forces are ignored
		double slopedMotion = this.ignoreForces() ? 0 : 0.0078125; //forward movement on slopes

		if (moveinfo.isRailed) {
			moveinfo.vec3d = this.h(this.locX, this.locY, this.locZ);
			moveinfo.raildata = this.world.getData(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ);

			this.locY = (double) moveinfo.blockY;
			moveinfo.isLaunching = false;
			boolean isBraking = false;

			// TrainNote: Used to boost a Minecart on powered tracks
			if (railtype == Block.GOLDEN_RAIL.id && !ignoreForces()) {
				moveinfo.isLaunching = (moveinfo.raildata & 8) != 0;
				isBraking = !moveinfo.isLaunching;
			}
			//TrainNote end

			if (!this.isOnMinecartTrack) {
				//driving on top of powered plate: extra calculation needed
				org.bukkit.block.Block curr = this.getWorld().getBlockAt(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ);
				BlockFace dir = Util.getPlateDirection(curr);
				if (dir == BlockFace.SELF) {
					//set track direction based on direction of this cart (0 or 1)
					if (Math.abs(this.motX) > Math.abs(this.motZ)) {
						moveinfo.raildata = 1;
					} else {
						moveinfo.raildata = 0;
					}
				} else if (dir == BlockFace.SOUTH) {
					moveinfo.raildata = 1;
				} else if (dir == BlockFace.WEST) {
					moveinfo.raildata = 0;
				}
			} else {
				if (((BlockMinecartTrack) Block.byId[railtype]).h()) {
					moveinfo.raildata &= 7; //clear sloped state for non-slopable rails
				}
				if (moveinfo.raildata >= 2 && moveinfo.raildata <= 5) {
					this.locY = (double) (moveinfo.blockY + 1);
				}
			}

			//TrainNote: Used to move a minecart up or down sloped tracks
			if (moveinfo.raildata == 2) {
				if (this.motX <= 0 && this.group().getProperties().slowDown) {
					this.motX -= slopedMotion;
				}
			}

			if (moveinfo.raildata == 3) {
				if (this.motX >= 0 && this.group().getProperties().slowDown) {
					this.motX += slopedMotion;
				}
			}

			if (moveinfo.raildata == 4) {
				if (this.motZ >= 0 && this.group().getProperties().slowDown) {
					this.motZ += slopedMotion;
				}
			}

			if (moveinfo.raildata == 5) {
				if (this.motZ <= 0 && this.group().getProperties().slowDown) {
					this.motZ -= slopedMotion;
				}
			}
			//TrainNote end

			moveinfo.moveMatrix = matrix[moveinfo.raildata];

			//rail motion is calculated from the rails
			double railMotionX = (double) (moveinfo.moveMatrix[1][0] - moveinfo.moveMatrix[0][0]);
			double railMotionZ = (double) (moveinfo.moveMatrix[1][2] - moveinfo.moveMatrix[0][2]);		
			//reverse motion if needed
			if (this.motX * railMotionX + this.motZ * railMotionZ < 0) {
				railMotionX = -railMotionX;
				railMotionZ = -railMotionZ;
			}

			//rail motion is applied (railFactor is used to normalize the rail motion to current motion)
			double railFactor = MathUtil.normalize(railMotionX, railMotionZ, this.motX, this.motZ);
			this.motX = railFactor * railMotionX;
			this.motZ = railFactor * railMotionZ;

			//slows down minecarts on unpowered powered rails
			if (isBraking) {
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

			//location is updated to follow the tracks
			double oldRailX = (double) moveinfo.blockX + 0.5 + (double) moveinfo.moveMatrix[0][0] * 0.5;
			double oldRailZ = (double) moveinfo.blockZ + 0.5 + (double) moveinfo.moveMatrix[0][2] * 0.5;
			double newRailX = (double) moveinfo.blockX + 0.5 + (double) moveinfo.moveMatrix[1][0] * 0.5;
			double newRailZ = (double) moveinfo.blockZ + 0.5 + (double) moveinfo.moveMatrix[1][2] * 0.5;

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

			//finally update the position
			this.setPosition(this.locX, this.locY + (double) this.height, this.locZ);
		} else {
			if (this.onGround) {
				// CraftBukkit start
				Vector der = this.getDerailedVelocityMod();
				this.motX *= der.getX();
				this.motY *= der.getY();
				this.motZ *= der.getZ();
				// CraftBukkit start
			}
		}
		return true;
	}

	/*
	 * Executes the post-velocity and positioning updates
	 */
	@SuppressWarnings("unchecked")
	public void postUpdate(double speedFactor) throws MemberDeadException, GroupUnloadedException {
		this.validate();
		if (this.moveinfo == null) return; //pre-update is needed
		double motX = this.motX;
		double motZ = this.motZ;
		//Prevent NaN (you never know!)
		motX = MathUtil.fixNaN(motX);
		motZ = MathUtil.fixNaN(motZ);
		speedFactor = MathUtil.fixNaN(speedFactor, 1);
		if (speedFactor > 10) speedFactor = 10; //>10 is ridiculous!
		motX = MathUtil.limit(motX, this.maxSpeed);
		motZ = MathUtil.limit(motZ, this.maxSpeed);
		motX *= speedFactor;
		motZ *= speedFactor;
		if (moveinfo.isRailed) {
			this.move(motX, 0.0D, motZ);
			if (moveinfo.moveMatrix[0][1] != 0 && MathHelper.floor(this.locX) - moveinfo.blockX == moveinfo.moveMatrix[0][0] && MathHelper.floor(this.locZ) - moveinfo.blockZ == moveinfo.moveMatrix[0][2]) {
				this.setPosition(this.locX, this.locY + (double) moveinfo.moveMatrix[0][1], this.locZ);
			} else if (moveinfo.moveMatrix[1][1] != 0 && MathHelper.floor(this.locX) - moveinfo.blockX == moveinfo.moveMatrix[1][0] && MathHelper.floor(this.locZ) - moveinfo.blockZ == moveinfo.moveMatrix[1][2]) {
				this.setPosition(this.locX, this.locY + (double) moveinfo.moveMatrix[1][1], this.locZ);
			}

			// CraftBukkit
			//==================TrainCarts edited==============
			if (this.type == 2 && !ignoreForces()) {
				double fuelPower = MathUtil.length(this.b, this.c);
				if (fuelPower > 0.01) {
					this.b /= fuelPower;
					this.c /= fuelPower;
					double boost = 0.04 + TrainCarts.poweredCartBoost;

					this.motX *= 0.8;
					this.motY *= 0.0;
					this.motZ *= 0.8;
					this.motX += this.b * boost;
					this.motZ += this.c * boost;
				} else {
					if (this.group().getProperties().slowDown) {
						this.motX *= 0.9;
						this.motY *= 0.0;
						this.motZ *= 0.9;
					}
				}
			}
			if (this.group().getProperties().slowDown) {
				this.motX *= 0.997;
				this.motY *= 0.0;
				this.motZ *= 0.997;
			}
			//==================================================

			Vec3D vec3d1 = this.h(this.locX, this.locY, this.locZ);

			double motLength;

			//x and z motion slowed down on slopes
			if (vec3d1 != null && moveinfo.vec3d != null) {
				if (this.group().getProperties().slowDown) {
					motLength = this.getForce();
					if (motLength > 0) {
						double slopeSlowDown = (moveinfo.vec3d.b - vec3d1.b) * 0.05;
						slopeSlowDown /= motLength;
						slopeSlowDown += 1;
						this.motX *= slopeSlowDown;
						this.motZ *= slopeSlowDown;
					}
				}
				this.setPosition(this.locX, vec3d1.b, this.locZ);
			}

			//update motion based on changed location
			int newBlockX = MathHelper.floor(this.locX);
			int newBlockZ = MathHelper.floor(this.locZ);
			if (newBlockX != moveinfo.blockX || newBlockZ != moveinfo.blockZ) {
				motLength = this.getForce();
				this.motX = motLength * (double) (newBlockX - moveinfo.blockX);
				this.motZ = motLength * (double) (newBlockZ - moveinfo.blockZ);
			}

			//PushX and PushZ updated for Powered Minecarts
			if (this.type == 2) {
				motLength = MathUtil.length(this.b, this.c);
				if (motLength > 0.01 && this.motX * this.motX + this.motZ * this.motZ > 0.001) {
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
			if (moveinfo.isLaunching) {
				motLength = this.getForce();
				if (motLength > 0.01) {
					//simple motion boosting when already moving
					double launchFactor = 0.06D / motLength;
					this.motX += this.motX * launchFactor;
					this.motZ += this.motZ * launchFactor;
				} else if (moveinfo.raildata == 1) {
					//launch at x-axis
					if (this.world.e(moveinfo.blockX - 1, moveinfo.blockY, moveinfo.blockZ)) {
						this.motX = 0.02;
					} else if (this.world.e(moveinfo.blockX + 1, moveinfo.blockY, moveinfo.blockZ)) {
						this.motX = -0.02;
					}
				} else if (moveinfo.raildata == 0) {
					//launch at z-axis
					if (this.world.e(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ - 1)) {
						this.motZ = 0.02;
					} else if (this.world.e(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ + 1)) {
						this.motZ = -0.02;
					}
				}
			}
		} else {

			if (this.onGround) {
				// CraftBukkit start
				Vector der = this.getDerailedVelocityMod();
				this.motX *= der.getX();
				this.motY *= der.getY();
				this.motZ *= der.getZ();
				// CraftBukkit start
			}

			this.move(motX, this.motY, motZ);
			if (!this.onGround) {
				// CraftBukkit start
				Vector der = this.getFlyingVelocityMod();
				this.motX *= der.getX();
				this.motY *= der.getY();
				this.motZ *= der.getZ();
				// CraftBukkit start
			}
		}

		//Update yaw and pitch
		double movedX = this.lastX - this.locX;
		double movedY = this.lastY - this.locY;
		double movedZ = this.lastZ - this.locZ;
		if (moveinfo.isRailed && (this.wasOnMinecartTrack != this.isOnMinecartTrack)) {
			this.pitch = 0.0F; //prevent weird pitch angles on pressure plates
		} else if (MathUtil.lengthSquared(movedX, movedZ) > 0.001) {
			if (this.moveinfo.isRailed && this.isOnMinecartTrack) {
				this.pitch = -0.8F * MathUtil.getLookAtPitch(movedX, movedY, movedZ);
			} else if (this.member().isFlying()) {
				this.pitch = 0.7F * MathUtil.getLookAtPitch(movedX, movedY, movedZ);
			} else {
				this.pitch = 0;
			}
			this.pitch = MathUtil.limit(this.pitch, 60F);
			float newyaw = MathUtil.getLookAtYaw(movedX, movedZ);
			if (MathUtil.getAngleDifference(this.yaw, newyaw) > 170) {
				this.yaw = MathUtil.normalAngle(newyaw + 180);
			} else {
				this.yaw = newyaw;
			}
		} else {
			if (Math.abs(this.pitch) > 0.1) {
				this.pitch *= 0.1;
			} else {
				this.pitch = 0;
			}
		}
		this.c(this.yaw, this.pitch);

		// CraftBukkit start
		Location from = moveinfo.getPrevLoc(this.world);
		Location to = this.getLocation();
		Vehicle vehicle = (Vehicle) this.getBukkitEntity();

		CommonUtil.callEvent(new VehicleUpdateEvent(vehicle));

		if (!from.equals(to)) {
			CommonUtil.callEvent(new VehicleMoveEvent(vehicle, from, to));
		}
		// CraftBukkit end

		List<Entity> list = this.world.getEntities(this, this.boundingBox.grow(0.2, 0, 0.2));
		if (list != null && !list.isEmpty()) {
			for (Entity entity : list) {
				if (entity != this.passenger && entity.f_() && entity instanceof EntityMinecart) {
					entity.collide(this);
				}
			}
		}

		if (this.passenger != null && this.passenger.dead) {
			this.passenger.vehicle = null; // CraftBukkit
			this.passenger = null;
		}

		if (this.fuel > 0 && this.type == 2) {
			if (--this.fuel == 0) {
				//TrainCarts - Actions to be done when empty
				if (this.onCoalUsed()) {
					this.fuel += 3600; //Refill
				} else {
					this.b = this.c = 0.0D;
				}
			}
		}
		this.a(this.fuel > 0);
	}

	/*
	 * Overridden function used to let players interact with this minecart
	 * Changes: use my own fuel and changes direction of all attached carts
	 */
	@Override
	public boolean b(EntityHuman entityhuman) {
		if (this.type == 2) {
			ItemStack itemstack = entityhuman.inventory.getItemInHand();
			if (itemstack != null && itemstack.id == Item.COAL.id) {
				if (--itemstack.count == 0) {
					entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
				}
				this.fuel += 3600;
			}
			this.b = this.locX - entityhuman.locX;
			this.c = this.locZ - entityhuman.locZ;	
			BlockFace dir = FaceUtil.getDirection(this.b, this.c, false);
			if (dir == this.member().getDirection().getOppositeFace()) {
				this.group().reverse();
			}
			return true;
		} else {
			return super.b(entityhuman);
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
		if (this.bQ) {
			this.boundingBox.d(d0, d1, d2);
			this.locX = (this.boundingBox.a + this.boundingBox.d) / 2.0D;
			this.locY = this.boundingBox.b + (double) this.height - (double) this.bO;
			this.locZ = (this.boundingBox.c + this.boundingBox.f) / 2.0D;
		} else {
			this.bO *= 0.4F;
			double d3 = this.locX;
			double d4 = this.locZ;

			if (this.bC) {
				this.bC = false;
				d0 *= 0.25D;
				d1 *= 0.05000000074505806D;
				d2 *= 0.25D;
				this.motX = 0.0D;
				this.motY = 0.0D;
				this.motZ = 0.0D;
			}

			double d5 = d0;
			double d6 = d1;
			double d7 = d2;
			AxisAlignedBB axisalignedbb = this.boundingBox.clone();
			boolean flag = this.onGround && this.isSneaking();

			if (flag) {
				double d8;

				for (d8 = 0.05D; d0 != 0.0D && this.world.a(this, this.boundingBox.c(d0, -1.0D, 0.0D)).size() == 0; d5 = d0) {
					if (d0 < d8 && d0 >= -d8) {
						d0 = 0.0D;
					} else if (d0 > 0.0D) {
						d0 -= d8;
					} else {
						d0 += d8;
					}
				}

				for (; d2 != 0.0D && this.world.a(this, this.boundingBox.c(0.0D, -1.0D, d2)).size() == 0; d7 = d2) {
					if (d2 < d8 && d2 >= -d8) {
						d2 = 0.0D;
					} else if (d2 > 0.0D) {
						d2 -= d8;
					} else {
						d2 += d8;
					}
				}
			}

			List list = this.world.a(this, this.boundingBox.a(d0, d1, d2));

			//=========================TrainCarts Changes Start==============================
			filterCollisionList(list);
			//=========================TrainCarts Changes End==============================

			for (int i = 0; i < list.size(); ++i) {
				d1 = ((AxisAlignedBB) list.get(i)).b(this.boundingBox, d1);
			}

			this.boundingBox.d(0.0D, d1, 0.0D);
			if (!this.bD && d6 != d1) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			boolean flag1 = this.onGround || d6 != d1 && d6 < 0.0D;

			int j;

			for (j = 0; j < list.size(); ++j) {
				d0 = ((AxisAlignedBB) list.get(j)).a(this.boundingBox, d0);
			}

			this.boundingBox.d(d0, 0.0D, 0.0D);
			if (!this.bD && d5 != d0) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			for (j = 0; j < list.size(); ++j) {
				d2 = ((AxisAlignedBB) list.get(j)).c(this.boundingBox, d2);
			}

			this.boundingBox.d(0.0D, 0.0D, d2);
			if (!this.bD && d7 != d2) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			double d9;
			double d10;
			int k;

			if (this.bP > 0.0F && flag1 && (flag || this.bO < 0.05F) && (d5 != d0 || d7 != d2)) {
				d9 = d0;
				d10 = d1;
				double d11 = d2;

				d0 = d5;
				d1 = (double) this.bP;
				d2 = d7;
				AxisAlignedBB axisalignedbb1 = this.boundingBox.clone();

				this.boundingBox.b(axisalignedbb);
				list = this.world.a(this, this.boundingBox.a(d5, d1, d7));

				//=========================TrainCarts Changes Start==============================
				filterCollisionList(list);
				//=========================TrainCarts Changes End==============================

				for (k = 0; k < list.size(); ++k) {
					d1 = ((AxisAlignedBB) list.get(k)).b(this.boundingBox, d1);
				}

				this.boundingBox.d(0.0D, d1, 0.0D);
				if (!this.bD && d6 != d1) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				for (k = 0; k < list.size(); ++k) {
					d0 = ((AxisAlignedBB) list.get(k)).a(this.boundingBox, d0);
				}

				this.boundingBox.d(d0, 0.0D, 0.0D);
				if (!this.bD && d5 != d0) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				for (k = 0; k < list.size(); ++k) {
					d2 = ((AxisAlignedBB) list.get(k)).c(this.boundingBox, d2);
				}

				this.boundingBox.d(0.0D, 0.0D, d2);
				if (!this.bD && d7 != d2) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				if (!this.bD && d6 != d1) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				} else {
					d1 = (double) (-this.bP);

					for (k = 0; k < list.size(); ++k) {
						d1 = ((AxisAlignedBB) list.get(k)).b(this.boundingBox, d1);
					}

					this.boundingBox.d(0.0D, d1, 0.0D);
				}

				if (d9 * d9 + d11 * d11 >= d0 * d0 + d2 * d2) {
					d0 = d9;
					d1 = d10;
					d2 = d11;
					this.boundingBox.b(axisalignedbb1);
				} else {
					double d12 = this.boundingBox.b - (double) ((int) this.boundingBox.b);

					if (d12 > 0.0D) {
						this.bO = (float) ((double) this.bO + d12 + 0.01D);
					}
				}
			}

			this.locX = (this.boundingBox.a + this.boundingBox.d) / 2.0D;
			this.locY = this.boundingBox.b + (double) this.height - (double) this.bO;
			this.locZ = (this.boundingBox.c + this.boundingBox.f) / 2.0D;
			this.positionChanged = d5 != d0 || d7 != d2;
			this.bz = d6 != d1;
			this.onGround = d6 != d1 && d6 < 0.0D;
			this.bA = this.positionChanged || this.bz;
			this.a(d1, this.onGround);
			if (d5 != d0) {
				this.motX = 0.0D;
			}

			if (d6 != d1) {
				this.motY = 0.0D;
			}

			if (d7 != d2) {
				this.motZ = 0.0D;
			}

			d9 = this.locX - d3;
			d10 = this.locZ - d4;
			int l;
			int i1;
			int j1;

			// CraftBukkit start
			if ((this.positionChanged) && (this.getBukkitEntity() instanceof Vehicle)) {
				Vehicle vehicle = (Vehicle) this.getBukkitEntity();
				org.bukkit.block.Block block = this.world.getWorld().getBlockAt(MathHelper.floor(this.locX), MathHelper.floor(this.locY - 0.20000000298023224D - (double) this.height), MathHelper.floor(this.locZ));

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
				this.world.getServer().getPluginManager().callEvent(event);
			}
			// CraftBukkit end

			if (this.g_() && !flag && this.vehicle == null) {
				this.bJ = (float) ((double) this.bJ + (double) MathHelper.sqrt(d9 * d9 + d10 * d10) * 0.6D);
				l = MathHelper.floor(this.locX);
				i1 = MathHelper.floor(this.locY - 0.20000000298023224D - (double) this.height);
				j1 = MathHelper.floor(this.locZ);
				k = this.world.getTypeId(l, i1, j1);
				if (k == 0 && this.world.getTypeId(l, i1 - 1, j1) == Block.FENCE.id) {
					k = this.world.getTypeId(l, i1 - 1, j1);
				}

				if (this.bJ > (float) this.b && k > 0) {
					this.b = (int) this.bJ + 1;
					this.a(l, i1, j1, k);
					Block.byId[k].b(this.world, l, i1, j1, this);
				}
			}

			l = MathHelper.floor(this.boundingBox.a + 0.0010D);
			i1 = MathHelper.floor(this.boundingBox.b + 0.0010D);
			j1 = MathHelper.floor(this.boundingBox.c + 0.0010D);
			k = MathHelper.floor(this.boundingBox.d - 0.0010D);
			int k1 = MathHelper.floor(this.boundingBox.e - 0.0010D);
			int l1 = MathHelper.floor(this.boundingBox.f - 0.0010D);

			if (this.world.a(l, i1, j1, k, k1, l1)) {
				for (int i2 = l; i2 <= k; ++i2) {
					for (int j2 = i1; j2 <= k1; ++j2) {
						for (int k2 = j1; k2 <= l1; ++k2) {
							int l2 = this.world.getTypeId(i2, j2, k2);

							if (l2 > 0) {
								Block.byId[l2].a(this.world, i2, j2, k2, this);
							}
						}
					}
				}
			}

			boolean flag2 = this.aJ();

			if (this.world.d(this.boundingBox.shrink(0.0010D, 0.0010D, 0.0010D))) {
				this.burn(1);
				if (!flag2) {
					++this.fireTicks;
					// CraftBukkit start - not on fire yet
					if (this.fireTicks <= 0) { // only throw events on the first combust, otherwise it spams
						EntityCombustEvent event = new EntityCombustEvent(this.getBukkitEntity(), 8);
						this.world.getServer().getPluginManager().callEvent(event);

						if (!event.isCancelled()) {
							this.setOnFire(event.getDuration());
						}
					} else {
						// CraftBukkit end
						this.setOnFire(8);
					}
				}
			} else if (this.fireTicks <= 0) {
				this.fireTicks = -this.maxFireTicks;
			}

			if (flag2 && this.fireTicks > 0) {
				this.world.makeSound(this, "random.fizz", 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
				this.fireTicks = -this.maxFireTicks;
			}
		}
	}

	/*
	 * Returns if this entity is allowed to collide with another entity
	 */
	private boolean canCollide(Entity e) {
		if (this.member().isCollisionIgnored(e)) return false;
		if (e.dead) return false;
		if (this.dead) return false;
		if (this.group().isVelocityAction()) return false;
		if (e instanceof MinecartMember) {
			//colliding with a member in the group, or not?
			MinecartMember mm1 = this.member();
			MinecartMember mm2 = (MinecartMember) e;
			if (mm1.getGroup() == mm2.getGroup()) {
				//Same group, but do prevent penetration
				if (mm1.distance(mm2) > 0.5) {
					return false;
				}
			} else if (!mm1.getGroup().getProperties().trainCollision) {
				//Allows train collisions?
				return false;
			} else if (!mm2.getGroup().getProperties().trainCollision) {
				//Other train allows train collisions?
				return false;
			} else if (mm2.getGroup().isVelocityAction()) {
				//Is this train targeting?
				return false;
			}
		} else if (e instanceof EntityLiving && e.vehicle != null && e.vehicle instanceof EntityMinecart) {
			//Ignore passenger collisions
			return false;
		} else {
			//Use push-away?
			TrainProperties prop = this.group().getProperties();
			if (e instanceof EntityItem && this.member().getProperties().pickUp) {
				return false;
			} else if (prop.canPushAway(e.getBukkitEntity())) {
				this.member().pushSideways(e.getBukkitEntity());
				return false;
			}
			if (!prop.trainCollision) {
				//No collision is allowed? (Owners override)
				if (e instanceof EntityPlayer) {
					Player p = (Player) e.getBukkitEntity();
					if (!prop.isOwner(p)) {
						return false;
					}
				} else {
					return false;
				}
			} else if (this.type == 1 && EntityUtil.isMob(e.getBukkitEntity()) && this.member().getProperties().allowMobsEnter && this.passenger == null) {
				e.setPassengerOf(this);
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
			List<Entity> entityList = this.world.entityList;

			Iterator<AxisAlignedBB> iter = list.iterator();
			AxisAlignedBB a;
			while (iter.hasNext()) {
				a = iter.next();
				for (Entity e : entityList) {
					if (e.boundingBox == a) {
						if (!canCollide(e)) iter.remove();
						break;
					}
				}
			}
		} catch (ConcurrentModificationException ex) {
			TrainCarts.plugin.log(Level.WARNING, "Another plugin is interacting with the world entity list from another thread, please check your plugins!");
		}

	}

	public boolean canBeRidden() { return this.type == 0; }
	public boolean isStorageCart() { return this.type == 1; }
	public boolean isPoweredCart() { return this.type == 2; }
}
