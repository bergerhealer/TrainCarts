package com.bergerkiller.bukkit.tc.controller.type;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartHopper;
import com.bergerkiller.bukkit.tc.exception.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.exception.MemberMissingException;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MinecartMemberHopper extends MinecartMember<CommonMinecartHopper> {

    @Override
    public void onAttached() {
        super.onAttached();
    }

    @Override
    public void onActivatorUpdate(boolean activated) {
        boolean activateSucking = !activated;
        if (entity.isSuckingItems() != activateSucking) {
            entity.setSuckingItems(activateSucking);
        }
    }

    @Override
    public void onPhysicsPostMove() throws MemberMissingException, GroupUnloadedException {
        super.onPhysicsPostMove();
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
    public void onBlockChange(Block from, Block to) {
        super.onBlockChange(from, to);
        entity.setSuckingCooldown(0);
    }

    @Override
    public void onItemSet(int index, ItemStack item) {
        super.onItemSet(index, item);
        // Mark the Entity as changed
        onPropertiesChanged();
    }

    @Override
    public void onTrainSaved(ConfigurationNode data) {
        Util.saveInventoryToConfig(entity.getInventory(), data);
    }

    @Override
    public void onTrainSpawned(ConfigurationNode data) {
        Util.loadInventoryFromConfig(entity.getInventory(), data);
    }
}
