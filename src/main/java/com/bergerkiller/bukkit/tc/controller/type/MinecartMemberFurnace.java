package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartFurnace;
import com.bergerkiller.bukkit.common.utils.*;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.common.wrappers.InteractionResult;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.PoweredCartSoundLoop;
import com.bergerkiller.bukkit.tc.events.MemberCoalUsedEvent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class MinecartMemberFurnace extends MinecartMember<CommonMinecartFurnace> {
    private int fuelCheckCounter = 0;
    private boolean isPushingForwards = true; // Whether pushing forwards, or backwards, relative to orientation

    @Override
    public void onAttached() {
        super.onAttached();
        this.soundLoop = new PoweredCartSoundLoop(this);

        Vector fwd = this.getOrientationForward();
        Vector push;
        if (Math.abs(fwd.getY()) > Math.max(Math.abs(fwd.getX()), Math.abs(fwd.getZ()))) {
            // Vertical
            push = new Vector(0.0, entity.getPushX(), 0.0);
        } else {
            // Horizontal
            push = new Vector(entity.getPushX(), 0.0, entity.getPushZ());
        }
        this.isPushingForwards = fwd.dot(push) >= 0.0;
    }

    // Only needed for saving/restoring, otherwise unused!
    private void updatePushXZ() {
        Vector fwd = this.getOrientationForward();
        if (!this.isPushingForwards) {
            fwd.multiply(-1.0);
        }
        if (Math.abs(fwd.getY()) > Math.max(Math.abs(fwd.getX()), Math.abs(fwd.getZ()))) {
            // Vertical
            entity.setPushX(fwd.getY() >= 0.0 ? 1.0 : -1.0);
            entity.setPushZ(0.0);
        } else {
            // Horizontal
            fwd.setY(0.0);
            if (fwd.lengthSquared() > 1e-10) {
                fwd.multiply(MathUtil.getNormalizationFactorLS(fwd.lengthSquared()));
                entity.setPushX(fwd.getX());
                entity.setPushZ(fwd.getZ());
            }
        }
    }

    @Override
    public InteractionResult onInteractBy(HumanEntity human, HumanHand hand) {
        if (!this.isInteractable()) {
            return InteractionResult.PASS;
        }

        ItemStack itemstack = HumanHand.getHeldItem(human, hand);
        if (itemstack != null && itemstack.getType() == Material.COAL) {
            ItemUtil.subtractAmount(itemstack, 1);
            HumanHand.setHeldItem(human, hand, itemstack);
            addFuelTicks(CommonMinecartFurnace.COAL_FUEL);
        }

        // Get forward vector of the human's head
        Location humanEye = human.getEyeLocation();
        Vector eyeFwd = MathUtil.getDirection(humanEye.getYaw(), humanEye.getPitch());
        this.isPushingForwards = (this.getOrientationForward().dot(eyeFwd) >= 0.0);
        this.updatePushXZ();
        return InteractionResult.CONSUME;
    }

    public void addFuelTicks(int fuelTicks) {
        int newFuelTicks = entity.getFuelTicks() + fuelTicks;
        if (newFuelTicks <= 0) {
            newFuelTicks = 0;
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
    public void onPhysicsPostMove() throws MemberMissingException, GroupUnloadedException {
        super.onPhysicsPostMove();

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
            if (fuelCheckCounter++ % 20 == 0 && TCConfig.useCoalFromStorageCart && this.getCoalFromNeighbours()) {
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
    public void onPhysicsPreMove() {
        super.onPhysicsPreMove();
        if (!this.isDerailed()) {
            // Velocity boost is applied
            if (isMovementControlled()) {
                // Station or launcher sign is launching us
                // Make sure to set the orientation forwards to match
                Vector fwd = FaceUtil.faceToVector(this.getDirection());
                double dot = this.getOrientationForward().dot(fwd);
                if (dot < -0.0001 || dot > 0.0001) {
                    this.isPushingForwards = (dot > 0.0);
                }
            } else {
                if (entity.hasFuel()) {
                    Vector dir = this.getOrientationForward();
                    if (!this.isPushingForwards) {
                        dir.multiply(-1.0);
                    }
                    dir.multiply(0.04 + TCConfig.poweredCartBoost);

                    entity.vel.multiply(0.8);
                    entity.vel.add(dir);
                } else if (this.getGroup().getProperties().isSlowingDown(SlowdownMode.FRICTION)) {
                    entity.vel.multiply(0.98);
                }
            }

            // Persistence
            this.updatePushXZ();
        }
    }

    @Override
    public void onItemSet(int index, ItemStack item) {
        super.onItemSet(index, item);
        // Mark the Entity as changed
        onPropertiesChanged();
    }

    @Override
    public void onTrainSaved(ConfigurationNode data) {
        if (this.getEntity().getFuelTicks() > 0) {
            data.set("fuel", this.entity.getFuelTicks());
        }
    }

    @Override
    public void onTrainSpawned(ConfigurationNode data) {
        if (data.contains("fuel")) {
            this.entity.setFuelTicks(data.get("fuel", 0));
        } else {
            this.entity.setFuelTicks(0);
        }
        this.entity.setSmoking(this.entity.getFuelTicks() > 0);
    }
}
