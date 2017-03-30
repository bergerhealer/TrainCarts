package com.bergerkiller.bukkit.tc.controller.type;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartHopper;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MinecartMemberHopper extends MinecartMember<CommonMinecartHopper> {

    @Override
    public void onAttached() {
        super.onAttached();
    }

    @Override
    public void onActivatorUpdate(boolean activated) {
        if (entity.isSuckingItems() != activated) {
            entity.setSuckingItems(activated);
        }
    }

    @Override
    public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
        super.onPhysicsPostMove(speedFactor);
        if (entity.isDead() || !entity.isSuckingItems()) {
            return;
        }
        entity.setSuckingCooldown(entity.getSuckingCooldown() - 1);
        if (entity.getSuckingCooldown() <= 0) {
            entity.setSuckingCooldown(0);
            if (entity.suckItems()) {
                entity.setSuckingCooldown(4);
                entity.update();
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
