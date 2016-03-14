package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartFurnace;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.PoweredCartSoundLoop;
import com.bergerkiller.bukkit.tc.events.MemberCoalUsedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class MinecartMemberFurnace extends MinecartMember<CommonMinecartFurnace> {
    private int pushDirection = 0;
    private int fuelCheckCounter = 0;

    @Override
    public void onAttached() {
        super.onAttached();
        this.soundLoop = new PoweredCartSoundLoop(this);
        double pushX = entity.getPushX();
        double pushZ = entity.getPushZ();
    }

    @Override
    public boolean onInteractBy(HumanEntity human) {
        if (!this.isInteractable()) {
            return true;
        }
        ItemStack itemstack = human.getItemInHand();
        if (itemstack != null && itemstack.getType() == Material.COAL) {
            ItemUtil.subtractAmount(itemstack, 1);
            human.setItemInHand(itemstack);
            addFuelTicks(CommonMinecartFurnace.COAL_FUEL);
        }
        if (this.isOnVertical()) {
            boolean isCartAbove = (entity.loc.getY() - EntityUtil.getLocY(human)) > 0.0;
            boolean isCartUpward = this.getDirection() == BlockFace.UP;
            this.pushDirection = (isCartAbove == isCartUpward) ? 1 : -1;
        } else {
            BlockFace dir = FaceUtil.getRailsCartDirection(this.getRailDirection());
            if (MathUtil.isHeadingTo(dir, new Vector(entity.loc.getX() - EntityUtil.getLocX(human), 0.0, entity.loc.getZ() - EntityUtil.getLocZ(human)))) {
                this.pushDirection = -1;
            } else {
                this.pushDirection = 1;
            }
        }
        /*if (this.isMoving()) {
            if (this.pushDirection == this.getDirection().getOppositeFace()) {
                this.getGroup().reverse();
                // Prevent push direction being inverted
                this.pushDirection = this.pushDirection.getOppositeFace();
            }
        }*/
        return true;
    }

    public void addFuelTicks(int fuelTicks) {
        int newFuelTicks = entity.getFuelTicks() + fuelTicks;
        if (newFuelTicks <= 0) {
            newFuelTicks = 0;
            this.pushDirection = 0;
        } else if (this.pushDirection == 0) {
            this.pushDirection = 1;
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
    public void reverse() {
        super.reverse();
        this.pushDirection = -1 * pushDirection;
    }

    @Override
    public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
        super.onPhysicsPostMove(speedFactor);
        BlockFace pd = getDirection();
        if (pushDirection == -1) pd = pd.getOppositeFace();
        else if (pushDirection == 0) pd = BlockFace.SELF;
        // Fuel update routines
        if (entity.hasFuel()) {
            entity.addFuelTicks(-1);
            entity.setPushX(pd.getModX());
            entity.setPushZ(pd.getModZ());
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
            this.pushDirection = 0;
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
                if (this.pushDirection != 0) {
                    BlockFace pd = getDirection();
                    if (pushDirection == -1) pd = pd.getOppositeFace();
                    else if (pushDirection == 0) pd = BlockFace.SELF;

                    double boost = 0.04 + TrainCarts.poweredCartBoost;
                    entity.vel.multiply(0.8);
                    entity.vel.x.add(boost * FaceUtil.cos(pd));
                    entity.vel.y.add((boost + 0.04) * pd.getModY());
                    entity.vel.z.add(boost * FaceUtil.sin(pd));
                } else if (this.getGroup().getProperties().isSlowingDown()) {
                    entity.vel.multiply(0.9);
                }
            }
        }
    }
}
