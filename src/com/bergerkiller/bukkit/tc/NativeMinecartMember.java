package com.bergerkiller.bukkit.tc;

import java.util.List;

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

import com.bergerkiller.bukkit.tc.Utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;

import net.minecraft.server.AxisAlignedBB;
import net.minecraft.server.Block;
import net.minecraft.server.BlockMinecartTrack;
import net.minecraft.server.DamageSource;
import net.minecraft.server.Entity;
import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.MathHelper;
import net.minecraft.server.StepSound;
import net.minecraft.server.Vec3D;
import net.minecraft.server.World;
import net.minecraft.server.EntityMinecart;

@SuppressWarnings("rawtypes")
public class NativeMinecartMember extends EntityMinecart {
	/*
	 * Values taken over from source to use in the m_ function, see attached source links
	 */
	private boolean i;
    private static final int[][][] matrix = new int[][][] { 
			{ { 0, 0, -1 }, { 0, 0, 1 } }, { { -1, 0, 0 }, { 1, 0, 0 } },
			{ { -1, -1, 0 }, { 1, 0, 0 } }, { { -1, 0, 0 }, { 1, -1, 0 } },
			{ { 0, 0, -1 }, { 0, -1, 1 } }, { { 0, -1, -1 }, { 0, 0, 1 } },
			{ { 0, 0, 1 }, { 1, 0, 0 } }, { { 0, 0, 1 }, { -1, 0, 0 } },
			{ { 0, 0, -1 }, { -1, 0, 0 } }, { { 0, 0, -1 }, { 1, 0, 0 } } };
    
    private int k;
    private double l;
    private double m;
    private double n;
    private double o;
    private double p;
	    
    public double getX() {
    	return this.lastX;
    }
    public double getY() {
    	return this.lastY;
    }
    public double getZ() {
    	return this.lastZ;
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
    
	private boolean ignoreForces() {
    	return group().hasTarget() || group().getProperties().isAtStation();
	}
	
	private MinecartMember member() {
		return (MinecartMember) this;
	}
	private MinecartGroup group() {
		return member().getGroup();
	}
	
	public NativeMinecartMember(World world, double d0, double d1, double d2, int i) {
		super(world, d0, d1, d2, i);
		this.lastX = this.locX;
		this.lastY = this.locY;
		this.lastZ = this.locZ;
		Location loc = new Location(this.world.getWorld(), this.locX, this.locY, this.locZ);
		this.yaw = BlockUtil.getRailsYaw(BlockUtil.getRails(loc));
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
            org.bukkit.entity.Entity passenger = (damagesource.e() == null) ? null : damagesource.e().getBukkitEntity();

            VehicleDamageEvent event = new VehicleDamageEvent(vehicle, passenger, i);
            this.world.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return true;
            }

            i = event.getDamage();
            // CraftBukkit end

            this.c = -this.c;
            this.b = 10;
            this.aq();
            this.damage += i * 10;
            if (this.damage > 40) {
                if (this.passenger != null) {
                    this.passenger.mount(this);
                }

                // CraftBukkit start
                VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, passenger);
                this.world.getServer().getPluginManager().callEvent(destroyEvent);

                if (destroyEvent.isCancelled()) {
                    this.damage = 40; // Maximize damage so this doesn't get triggered again right away
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
	 * Stores m_ information (since functions are now pretty much scattered around)
	 */
	private MoveInfo moveinfo;
	private class MoveInfo {
		public boolean isRailed; //sets what post-velocity update to run
        public double prevX;
        public double prevY;
        public double prevZ;
        public float prevYaw;
        public float prevPitch;
        public int[][] aint;
        public int i;
        public int j;
        public int k;
        public int i1;
        public Vec3D vec3d;
        public boolean flag;
        public boolean flag1;
        public boolean flag2;
	}
		
	/*
	 * Executes the pre-velocity and location updates
	 * Returns whether or not any velocity updates were done. (if the cart is NOT static)
	 */
	public boolean preUpdate() {
		if (this.dead) return false;
		moveinfo = new MoveInfo();
		
        // CraftBukkit start
		moveinfo.prevX = this.locX;
		moveinfo.prevY = this.locY;
		moveinfo.prevZ = this.locZ;
		moveinfo.prevYaw = this.yaw;
		moveinfo.prevPitch = this.pitch;
        // CraftBukkit end

        if (this.b > 0) {
            --this.b;
        }

        if (this.damage > 0) {
            --this.damage;
        }

        double d0;

        if (this.world.isStatic && this.k > 0) {
            if (this.k > 0) {
                double d1 = this.locX + (this.l - this.locX) / (double) this.k;
                double d2 = this.locY + (this.m - this.locY) / (double) this.k;
                double d3 = this.locZ + (this.n - this.locZ) / (double) this.k;

                for (d0 = this.o - (double) this.yaw; d0 < -180.0D; d0 += 360.0D) {
                    ;
                }

                while (d0 >= 180.0D) {
                    d0 -= 360.0D;
                }

                this.yaw = (float) ((double) this.yaw + d0 / (double) this.k);
                this.pitch = (float) ((double) this.pitch + (this.p - (double) this.pitch) / (double) this.k);
                --this.k;
                this.setPosition(d1, d2, d3);
                this.c(this.yaw, this.pitch);
            } else {
                this.setPosition(this.locX, this.locY, this.locZ);
                this.c(this.yaw, this.pitch);
            }
        } else {
            this.lastX = this.locX;
            this.lastY = this.locY;
            this.lastZ = this.locZ;
            this.motY -= 0.03999999910593033D;
            moveinfo.i = MathHelper.floor(this.locX);
            moveinfo.j = MathHelper.floor(this.locY);
            moveinfo.k = MathHelper.floor(this.locZ);

            if (BlockMinecartTrack.g(this.world, moveinfo.i, moveinfo.j - 1, moveinfo.k)) {
                --moveinfo.j;
            }

            // CraftBukkit
            moveinfo.flag = false;

            d0 = 0.0078125D;
            int l = this.world.getTypeId(moveinfo.i, moveinfo.j, moveinfo.k);
            
            moveinfo.isRailed = BlockMinecartTrack.c(l);
            if (moveinfo.isRailed) {
                moveinfo.vec3d = this.h(this.locX, this.locY, this.locZ);
                moveinfo.i1 = this.world.getData(moveinfo.i, moveinfo.j, moveinfo.k);

                this.locY = (double) moveinfo.j;
                moveinfo.flag1 = false;
                moveinfo.flag2 = false;

                // TrainNote: Used to boost a Minecart on powered tracks
                if (l == Block.GOLDEN_RAIL.id && !ignoreForces()) {
                	moveinfo.flag1 = (moveinfo.i1 & 8) != 0;
                	moveinfo.flag2 = !moveinfo.flag1;
                }

                if (((BlockMinecartTrack) Block.byId[l]).f()) {
                	moveinfo.i1 &= 7;
                }
                if (moveinfo.i1 >= 2 && moveinfo.i1 <= 5) {
                    this.locY = (double) (moveinfo.j + 1);
                }

            	if (moveinfo.i1 == 2) {
            		this.motX -= d0;
            	}
                 
                if (moveinfo.i1 == 3) {
                    this.motX += d0;
                }
                 
                if (moveinfo.i1 == 4) {
                    this.motZ += d0;
                }
                 
                if (moveinfo.i1 == 5) {
                    this.motZ -= d0;
                }
                //TrainNote end

				moveinfo.aint = matrix[moveinfo.i1];
                double d5 = (double) (moveinfo.aint[1][0] - moveinfo.aint[0][0]);
                double d6 = (double) (moveinfo.aint[1][2] - moveinfo.aint[0][2]);
                double d7 = Math.sqrt(d5 * d5 + d6 * d6);
                double d8 = this.motX * d5 + this.motZ * d6;

                if (d8 < 0.0D) {
                    d5 = -d5;
                    d6 = -d6;
                }

                double d9 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);

                this.motX = d9 * d5 / d7;
                this.motZ = d9 * d6 / d7;
                double d10;

                // TrainNote: Used to slow down a Minecart
                if (moveinfo.flag2) {
                    d10 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);
                    if (d10 < 0.03D) {
                        this.motX *= 0.0D;
                        this.motY *= 0.0D;
                        this.motZ *= 0.0D;
                    } else {
                        this.motX *= 0.5D;
                        this.motY *= 0.0D;
                        this.motZ *= 0.5D;
                    	if (this.motX < -100) this.motX = -100;
                    	if (this.motX > 100) this.motX = 100;
                    	if (this.motZ < -100) this.motZ = -100;
                    	if (this.motZ > 100) this.motZ = 100;
                    }
                }
                // TrainNote end
                
                d10 = 0.0D;
                double d11 = (double) moveinfo.i + 0.5D + (double) moveinfo.aint[0][0] * 0.5D;
                double d12 = (double) moveinfo.k + 0.5D + (double) moveinfo.aint[0][2] * 0.5D;
                double d13 = (double) moveinfo.i + 0.5D + (double) moveinfo.aint[1][0] * 0.5D;
                double d14 = (double) moveinfo.k + 0.5D + (double) moveinfo.aint[1][2] * 0.5D;

                d5 = d13 - d11;
                d6 = d14 - d12;
                double d15;
                double d16;
                double d17;

                if (d5 == 0.0D) {
                    this.locX = (double) moveinfo.i + 0.5D;
                    d10 = this.locZ - (double) moveinfo.k;
                } else if (d6 == 0.0D) {
                    this.locZ = (double) moveinfo.k + 0.5D;
                    d10 = this.locX - (double) moveinfo.i;
                } else {
                    d16 = this.locX - d11;
                    d15 = this.locZ - d12;
                    d17 = (d16 * d5 + d15 * d6) * 2.0D;
                    d10 = d17;
                }
                
                this.locX = d11 + d5 * d10;
                this.locZ = d12 + d6 * d10;
                this.setPosition(this.locX, this.locY + (double) this.height, this.locZ);
            } else {
                if (this.onGround) {
                    // CraftBukkit start
                    this.motX *= this.derailedX;
                    this.motY *= this.derailedY;
                    this.motZ *= this.derailedZ;
                    // CraftBukkit start
                }
            }
            return true;
        }
        return false;
    }
	
	/*
	 * Executes the post-velocity and positioning updates
	 */
	public void postUpdate(double speedFactor) {
		if (this.dead) return;
		double motX = this.motX;
		double motZ = this.motZ;
		//Prevent NaN (you never know!)
		if (Double.isNaN(motX)) motX = 0;
		if (Double.isNaN(motZ)) motZ = 0;
		if (Double.isNaN(speedFactor)) speedFactor = 1;
		if (speedFactor > 10) speedFactor = 10; //>10 is ridiculous!
		motX = Util.limit(motX, this.maxSpeed);
		motZ = Util.limit(motZ, this.maxSpeed);
        motX *= speedFactor;
        motZ *= speedFactor;
		if (moveinfo.isRailed) {
            this.move(motX, 0.0D, motZ);
            if (moveinfo.aint[0][1] != 0 && MathHelper.floor(this.locX) - moveinfo.i == moveinfo.aint[0][0] && MathHelper.floor(this.locZ) - moveinfo.k == moveinfo.aint[0][2]) {
                this.setPosition(this.locX, this.locY + (double) moveinfo.aint[0][1], this.locZ);
            } else if (moveinfo.aint[1][1] != 0 && MathHelper.floor(this.locX) - moveinfo.i == moveinfo.aint[1][0] && MathHelper.floor(this.locZ) - moveinfo.k == moveinfo.aint[1][2]) {
                this.setPosition(this.locX, this.locY + (double) moveinfo.aint[1][1], this.locZ);
            }

            // CraftBukkit
            //==================TrainCarts edited==============
            if (this.passenger != null) {
//                 this.motX *= 0.9599999785423279D;
//                 this.motY *= 0.0D;
//                 this.motZ *= 0.9599999785423279D;
            	if (this.group().getProperties().slowDown) {
                	this.motX *= 0.996999979019165D;
                	this.motY *= 0.0D;
                    this.motZ *= 0.996999979019165D;
            	}
            } else {
            	//Is in a group that is targetting?
                if (this.type == 2) {
                	if (!ignoreForces()) {
                        double d17 = (double) MathHelper.a(this.f * this.f + this.g * this.g);
                        if (d17 > 0.01D) {
                            moveinfo.flag = true;
                            this.f /= d17;
                            this.g /= d17;
                            double d18 = 0.04D;

                            this.motX *= 0.800000011920929D + TrainCarts.poweredCartBoost; //Traincarts edited
                            this.motY *= 0.0D;
                            this.motZ *= 0.800000011920929D + TrainCarts.poweredCartBoost; //Traincarts edited
                            this.motX += this.f * d18;
                            this.motZ += this.g * d18;
                        } else {
                            this.motX *= 0.8999999761581421D;
                            this.motY *= 0.0D;
                            this.motZ *= 0.8999999761581421D;
                        }
                	}
                }
                if (this.group().getProperties().slowDown) {
                    this.motX *= 0.996999979019165D;
                    this.motY *= 0.0D;
                    this.motZ *= 0.996999979019165D;
                }
            }
            //==================================================

            Vec3D vec3d1 = this.h(this.locX, this.locY, this.locZ);

            double d9;
            if (vec3d1 != null && moveinfo.vec3d != null) {
                double d19 = (moveinfo.vec3d.b - vec3d1.b) * 0.05D;

                d9 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);
                if (d9 > 0.0D) {
                    this.motX = this.motX / d9 * (d9 + d19);
                    this.motZ = this.motZ / d9 * (d9 + d19);
                }

                this.setPosition(this.locX, vec3d1.b, this.locZ);
            }

            int j1 = MathHelper.floor(this.locX);
            int k1 = MathHelper.floor(this.locZ);

            if (j1 != moveinfo.i || k1 != moveinfo.k) {
                d9 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);
                this.motX = d9 * (double) (j1 - moveinfo.i);
                this.motZ = d9 * (double) (k1 - moveinfo.k);
            }

            double d20;
            
            //TrainNote: PushX and PushZ updated for Powered Minecarts
            if (this.type == 2) {
                d20 = (double) MathHelper.a(this.f * this.f + this.g * this.g);
                if (d20 > 0.01D && this.motX * this.motX + this.motZ * this.motZ > 0.0010D) {
                    this.f /= d20;
                    this.g /= d20;
                    if (this.f * this.motX + this.g * this.motZ < 0.0D) {
                        this.f = 0.0D;
                        this.g = 0.0D;
                    } else {
                        this.f = this.motX;
                        this.g = this.motZ;
                    }
                }
            }
            //TrainNote end
            
            if (moveinfo.flag1) {
                d20 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);
                if (d20 > 0.01D) {
                    double d21 = 0.06D;
                    this.motX += this.motX / d20 * d21;
                    this.motZ += this.motZ / d20 * d21;
                } else if (moveinfo.i1 == 1) {
                    if (this.world.e(moveinfo.i - 1, moveinfo.j, moveinfo.k)) {
                        this.motX = 0.02D;
                    } else if (this.world.e(moveinfo.i + 1, moveinfo.j, moveinfo.k)) {
                        this.motX = -0.02D;
                    }
                } else if (moveinfo.i1 == 0) {
                    if (this.world.e(moveinfo.i, moveinfo.j, moveinfo.k - 1)) {
                        this.motZ = 0.02D;
                    } else if (this.world.e(moveinfo.i, moveinfo.j, moveinfo.k + 1)) {
                        this.motZ = -0.02D;
                    }
                }
            }
        } else {

            if (this.onGround) {
                // CraftBukkit start
                motX *= this.derailedX;
                motY *= this.derailedY;
                motZ *= this.derailedZ;
                // CraftBukkit start
            }

            this.move(motX, this.motY, motZ);
            if (!this.onGround) {
                // CraftBukkit start
                motX *= this.flyingX;
                motY *= this.flyingY;
                motZ *= this.flyingZ;
                // CraftBukkit start
            }
        }
		
		
		
		this.pitch = 0.0F;
		double d22 = this.lastX - this.locX;
		double d23 = this.lastZ - this.locZ;

		if (d22 * d22 + d23 * d23 > 0.0010D) {
			this.yaw = (float) (Math.atan2(d23, d22) * 180.0D / 3.141592653589793D);
			if (this.i) {
				this.yaw += 180.0F;
			}
		}

		double d24;

		for (d24 = (double) (this.yaw - this.lastYaw); d24 >= 180.0D; d24 -= 360.0D) {
			;
		}

		while (d24 < -180.0D) {
			d24 += 360.0D;
		}

		if (d24 < -170.0D || d24 >= 170.0D) {
			this.yaw += 180.0F;
			this.i = !this.i;
		}

		this.c(this.yaw, this.pitch);

		// CraftBukkit start
		org.bukkit.World bworld = this.world.getWorld();
		Location from = new Location(bworld, moveinfo.prevX, moveinfo.prevY,
				moveinfo.prevZ, moveinfo.prevYaw, moveinfo.prevPitch);
		Location to = new Location(bworld, this.locX, this.locY, this.locZ,
				this.yaw, this.pitch);
		Vehicle vehicle = (Vehicle) this.getBukkitEntity();

		this.world.getServer().getPluginManager()
				.callEvent(new VehicleUpdateEvent(vehicle));

		if (!from.equals(to)) {
			this.world.getServer().getPluginManager()
					.callEvent(new VehicleMoveEvent(vehicle, from, to));
		}
		// CraftBukkit end

		List list = this.world.b((Entity) this, this.boundingBox.b(
				0.20000000298023224D, 0.0D, 0.20000000298023224D));
		
		if (list != null && list.size() > 0) {
			for (int l1 = 0; l1 < list.size(); ++l1) {
				Entity entity = (Entity) list.get(l1);
				if (entity != this.passenger && entity.g()
						&& entity instanceof EntityMinecart) {
					entity.collide(this);
				}
			}
		}

		if (this.passenger != null && this.passenger.dead) {
			this.passenger.vehicle = null; // CraftBukkit
			this.passenger = null;
		}

		if (moveinfo.flag && this.random.nextInt(4) == 0) {
			--this.e;
			if (this.e < 0) {
				//TrainCarts - Actions to be done when empty
				if (this.onCoalUsed()) {
					this.e = 1200; //Refill
				} else {
					this.f = this.g = 0.0D;
				}
			}

			this.world.a("largesmoke", this.locX, this.locY + 0.8D, this.locZ,
					0.0D, 0.0D, 0.0D);
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
    public void move(double d0, double d1, double d2) {
        if (this.bJ) {
            this.boundingBox.d(d0, d1, d2);
            this.locX = (this.boundingBox.a + this.boundingBox.d) / 2.0D;
            this.locY = this.boundingBox.b + (double) this.height - (double) this.bH;
            this.locZ = (this.boundingBox.c + this.boundingBox.f) / 2.0D;
        } else {
            this.bH *= 0.4F;
            double d3 = this.locX;
            double d4 = this.locZ;

            if (this.bv) {
                this.bv = false;
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

                for (d8 = 0.05D; d0 != 0.0D && this.world.getEntities(this, this.boundingBox.c(d0, -1.0D, 0.0D)).size() == 0; d5 = d0) {
                    if (d0 < d8 && d0 >= -d8) {
                        d0 = 0.0D;
                    } else if (d0 > 0.0D) {
                        d0 -= d8;
                    } else {
                        d0 += d8;
                    }
                }

                for (; d2 != 0.0D && this.world.getEntities(this, this.boundingBox.c(0.0D, -1.0D, d2)).size() == 0; d7 = d2) {
                    if (d2 < d8 && d2 >= -d8) {
                        d2 = 0.0D;
                    } else if (d2 > 0.0D) {
                        d2 -= d8;
                    } else {
                        d2 += d8;
                    }
                }
            }

            List list = this.world.getEntities(this, this.boundingBox.a(d0, d1, d2));

            //=========================TrainCarts Changes Start==============================
            filterCollisionList(list);
            //=========================TrainCarts Changes End==============================
            
            for (int i = 0; i < list.size(); ++i) {
                d1 = ((AxisAlignedBB) list.get(i)).b(this.boundingBox, d1);
            }

            this.boundingBox.d(0.0D, d1, 0.0D);
            if (!this.bw && d6 != d1) {
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
            if (!this.bw && d5 != d0) {
                d2 = 0.0D;
                d1 = 0.0D;
                d0 = 0.0D;
            }

            for (j = 0; j < list.size(); ++j) {
                d2 = ((AxisAlignedBB) list.get(j)).c(this.boundingBox, d2);
            }

            this.boundingBox.d(0.0D, 0.0D, d2);
            if (!this.bw && d7 != d2) {
                d2 = 0.0D;
                d1 = 0.0D;
                d0 = 0.0D;
            }

            double d9;
            double d10;
            int k;

            if (this.bI > 0.0F && flag1 && (flag || this.bH < 0.05F) && (d5 != d0 || d7 != d2)) {
                d9 = d0;
                d10 = d1;
                double d11 = d2;

                d0 = d5;
                d1 = (double) this.bI;
                d2 = d7;
                AxisAlignedBB axisalignedbb1 = this.boundingBox.clone();

                this.boundingBox.b(axisalignedbb);
                list = this.world.getEntities(this, this.boundingBox.a(d5, d1, d7));

                //=========================TrainCarts Changes Start==============================
                filterCollisionList(list);
                //=========================TrainCarts Changes End==============================
                
                for (k = 0; k < list.size(); ++k) {
                    d1 = ((AxisAlignedBB) list.get(k)).b(this.boundingBox, d1);
                }

                this.boundingBox.d(0.0D, d1, 0.0D);
                if (!this.bw && d6 != d1) {
                    d2 = 0.0D;
                    d1 = 0.0D;
                    d0 = 0.0D;
                }

                for (k = 0; k < list.size(); ++k) {
                    d0 = ((AxisAlignedBB) list.get(k)).a(this.boundingBox, d0);
                }

                this.boundingBox.d(d0, 0.0D, 0.0D);
                if (!this.bw && d5 != d0) {
                    d2 = 0.0D;
                    d1 = 0.0D;
                    d0 = 0.0D;
                }

                for (k = 0; k < list.size(); ++k) {
                    d2 = ((AxisAlignedBB) list.get(k)).c(this.boundingBox, d2);
                }

                this.boundingBox.d(0.0D, 0.0D, d2);
                if (!this.bw && d7 != d2) {
                    d2 = 0.0D;
                    d1 = 0.0D;
                    d0 = 0.0D;
                }

                if (!this.bw && d6 != d1) {
                    d2 = 0.0D;
                    d1 = 0.0D;
                    d0 = 0.0D;
                } else {
                    d1 = (double) (-this.bI);

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
                        this.bH = (float) ((double) this.bH + d12 + 0.01D);
                    }
                }
            }

            this.locX = (this.boundingBox.a + this.boundingBox.d) / 2.0D;
            this.locY = this.boundingBox.b + (double) this.height - (double) this.bH;
            this.locZ = (this.boundingBox.c + this.boundingBox.f) / 2.0D;
            this.positionChanged = d5 != d0 || d7 != d2;
            this.bs = d6 != d1;
            this.onGround = d6 != d1 && d6 < 0.0D;
            this.bt = this.positionChanged || this.bs;
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

            if (this.e_() && !flag && this.vehicle == null) {
                this.bC = (float) ((double) this.bC + (double) MathHelper.a(d9 * d9 + d10 * d10) * 0.6D);
                l = MathHelper.floor(this.locX);
                i1 = MathHelper.floor(this.locY - 0.20000000298023224D - (double) this.height);
                j1 = MathHelper.floor(this.locZ);
                k = this.world.getTypeId(l, i1, j1);
                if (this.world.getTypeId(l, i1 - 1, j1) == Block.FENCE.id) {
                    k = this.world.getTypeId(l, i1 - 1, j1);
                }

                if (this.bC > (float) this.b && k > 0) {
                    this.b = (int) this.bC + 1;
                    StepSound stepsound = Block.byId[k].stepSound;

                    if (this.world.getTypeId(l, i1 + 1, j1) == Block.SNOW.id) {
                        stepsound = Block.SNOW.stepSound;
                        this.world.makeSound(this, stepsound.getName(), stepsound.getVolume1() * 0.15F, stepsound.getVolume2());
                    } else if (!Block.byId[k].material.isLiquid()) {
                        this.world.makeSound(this, stepsound.getName(), stepsound.getVolume1() * 0.15F, stepsound.getVolume2());
                    }

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

            boolean flag2 = this.an();

            if (this.world.d(this.boundingBox.shrink(0.0010D, 0.0010D, 0.0010D))) {
                this.burn(1);
                if (!flag2) {
                    ++this.fireTicks;
                    // CraftBukkit start - not on fire yet
                    if (this.fireTicks <= 0) {
                        EntityCombustEvent event = new EntityCombustEvent(this.getBukkitEntity());
                        this.world.getServer().getPluginManager().callEvent(event);

                        if (!event.isCancelled()) {
                            this.fireTicks = 300;
                        }
                    } else {
                        // CraftBukkit end - reset fire level back to max
                        this.fireTicks = 300;
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
		if (e.dead) return false;
		if (this.dead) return false;
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
			}
		} else if (e instanceof EntityLiving && e.vehicle != null && e.vehicle instanceof EntityMinecart) {
			//Ignore passenger collisions
			return false;
		} else {
			//Use push-away?
			TrainProperties prop = this.group().getProperties();
			if (prop.canPushAway(e.getBukkitEntity())) {
				this.member().push(e.getBukkitEntity());
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
			} else if (EntityUtil.isMob(e.getBukkitEntity()) && prop.allowMobsEnter && this.passenger == null) {
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
	private void filterCollisionList(List list) {		
        int ri = 0;
        
        Entity[] entityList = (Entity[]) this.world.entityList.toArray(new Entity[0]);

        while (ri < list.size()) {
        	AxisAlignedBB a = (AxisAlignedBB) list.get(ri);
        	boolean remove = false;
        	for (Entity e : entityList) {
        		if (e.boundingBox == a) {
        			remove = !canCollide(e);
        		}
        		if (remove) break;
        	}
        	if (remove) {
        		list.remove(ri);
        	} else {
        		ri++;
        	}
        }
	}

}
