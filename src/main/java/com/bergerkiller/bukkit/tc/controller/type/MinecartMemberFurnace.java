package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartFurnace;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.PoweredCartSoundLoop;
import com.bergerkiller.bukkit.tc.events.MemberCoalUsedEvent;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MinecartMemberFurnace extends MinecartMember<CommonMinecartFurnace> {
    private boolean wasOnVertical = false;
    private int fuelCheckCounter = 0;

    @Override
    public void onAttached() {
        super.onAttached();
        this.soundLoop = new PoweredCartSoundLoop(this);
        //double pushX = entity.getPushX();
        //double pushZ = entity.getPushZ();
    }

    @Override
    public boolean onInteractBy(HumanEntity human, HumanHand hand) {
        if (!this.isInteractable()) {
            return true;
        }

        ItemStack itemstack = HumanHand.getHeldItem(human, hand);
        if (itemstack != null && itemstack.getType() == Material.COAL) {
            ItemUtil.subtractAmount(itemstack, 1);
            HumanHand.setHeldItem(human, hand, itemstack);
            addFuelTicks(CommonMinecartFurnace.COAL_FUEL);
        }

        if (this.isOnVertical()) {
            // When on vertical rail we only use PushX for up/down momentum
            boolean isCartAbove = (entity.loc.getY() - EntityUtil.getLocY(human)) > 0.0;
            boolean isCartUpward = this.getDirection() == BlockFace.UP;
            entity.setPushX((isCartAbove == isCartUpward) ? 1.0 : -1.0);
            this.wasOnVertical = true;
        } else {
            // Otherwise, use the position of the human vs minecart for the push vector
            double dx = entity.loc.getX() - EntityUtil.getLocX(human);
            double dz = entity.loc.getZ() - EntityUtil.getLocZ(human);
            entity.setPushX(dx);
            entity.setPushZ(dz);
            this.wasOnVertical = false;
        }

        /*
        if (this.isOnVertical()) {
            boolean isCartAbove = (entity.loc.getY() - EntityUtil.getLocY(human)) > 0.0;
            boolean isCartUpward = this.getDirection() == BlockFace.UP;
            this.isPushingReverse = (isCartAbove != isCartUpward);
        } else {
            BlockFace dir = this.getDirection();
            this.isPushingReverse = !MathUtil.isHeadingTo(dir, new Vector(entity.loc.getX() - EntityUtil.getLocX(human), 0.0, entity.loc.getZ() - EntityUtil.getLocZ(human)));
        }
        */

        /*if (this.isMoving()) {
            if (this.pushDirection == this.getDirection().getOppositeFace()) {
                this.getGroup().reverse();
                // Prevent push direction being inverted
                this.pushDirection = this.pushDirection.getOppositeFace();
            }
        }*/
        return true;
    }

    public BlockFace updatePushDirection() {
        BlockFace direction = this.getDirection();
        if (FaceUtil.isVertical(direction)) {
            if (!wasOnVertical) {
                wasOnVertical = true;
                entity.setPushX(direction.getModY());
            }
            return entity.getPushX() > 0.0 ? BlockFace.UP : BlockFace.DOWN;
        }

        BlockFace last = FaceUtil.getDirection(entity.getPushX(), entity.getPushZ(), true);
        if (!wasOnVertical && FaceUtil.getFaceYawDifference(last, direction) > 90) {
            direction = direction.getOppositeFace();
        }

        entity.setPushX(direction.getModX());
        entity.setPushZ(direction.getModZ());
        wasOnVertical = false;

        return direction;
    }

    public void addFuelTicks(int fuelTicks) {
        boolean hadFuel = entity.hasFuel();
        int newFuelTicks = entity.getFuelTicks() + fuelTicks;
        if (newFuelTicks <= 0) {
            newFuelTicks = 0;
        } else if (!hadFuel) {
            if (this.isOnVertical()) {
                entity.setPushX(this.getDirection().getModY());
                wasOnVertical = true;
            } else {
                entity.setPushX(this.getDirection().getModX());
                entity.setPushZ(this.getDirection().getModZ());
                wasOnVertical = false;
            }
        }
        entity.setFuelTicks(newFuelTicks);
    }

    /**
     * Checks if new coal can be used
     *
     * @return True if new coal can be put into the powered minecart, False if not
     */
    public boolean onCoalUsed() {
        MemberCoalUsedEvent event = MemberCoalUsedEvent.call(this);
        if (event.useCoal()) {
            return this.getCoalFromNeighbours();
        }
        return event.refill();
    }

    public boolean getCoalFromNeighbours() {
        for (MinecartMember<?> mm : this.getNeightbours()) {
            //Is it a storage minecart?
            if (mm instanceof MinecartMemberChest) {
                //has coal?
                Inventory inv = ((MinecartMemberChest) mm).getEntity().getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (LogicUtil.nullOrEmpty(item) || item.getType() != Material.COAL) {
                        continue;
                    }
                    ItemUtil.subtractAmount(item, 1);
                    inv.setItem(i, item);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
        super.onPhysicsPostMove(speedFactor);

        // Fuel update routines
        if (entity.hasFuel()) {
            entity.addFuelTicks(-1);
            if (!entity.hasFuel()) {
                //TrainCarts - Actions to be done when empty
                if (this.onCoalUsed()) {
                    this.addFuelTicks(CommonMinecartFurnace.COAL_FUEL); //Refill
                }
            }
        }
        // Put coal into cart if needed
        if (!entity.hasFuel()) {
            if (fuelCheckCounter++ % 20 == 0 && TrainCarts.useCoalFromStorageCart && this.getCoalFromNeighbours()) {
                this.addFuelTicks(CommonMinecartFurnace.COAL_FUEL);
            }
        } else {
            this.fuelCheckCounter = 0;
        }
        if (!entity.hasFuel()) {
            entity.setFuelTicks(0);
        }
        entity.setSmoking(entity.hasFuel());
    }

    @Override
    public void doPostMoveLogic() {
        super.doPostMoveLogic();
        if (!this.isDerailed()) {
            // Update pushing direction
            /*if (this.pushDirection != 0) {
                BlockFace dir = this.getDirection();
                if (this.isOnVertical()) {
                    if (dir != this.pushDirection.getOppositeFace()) {
                        this.pushDirection = dir;
                    }
                } else {
                    if (FaceUtil.isVertical(this.pushDirection) || FaceUtil.getFaceYawDifference(dir, this.pushDirection) <= 45) {
                        this.pushDirection = dir;
                    }
                }
            }*/

            // Velocity boost is applied
            if (!isMovementControlled()) {
                if (entity.hasFuel()) {
                    BlockFace pd = updatePushDirection();

                    double boost = 0.04 + TrainCarts.poweredCartBoost;
                    entity.vel.multiply(0.8);
                    entity.vel.x.add(boost * FaceUtil.cos(pd));
                    entity.vel.y.add((boost + 0.04) * pd.getModY());
                    entity.vel.z.add(boost * FaceUtil.sin(pd));
                } else if (this.getGroup().getProperties().isSlowingDown()) {
                    entity.vel.multiply(0.98);
                }
            }
        }
    }

    @Override
    public void onItemSet(int index, ItemStack item) {
        super.onItemSet(index, item);
        // Mark the Entity as changed
        onPropertiesChanged();
    }
}
