package com.bergerkiller.bukkit.tc.controller.type;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecartHopper;
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
    public void onTick() {
        super.onTick();

        if (entity.isDead() || !entity.isSuckingItems()) {
            return;
        }

        // ALL THIS CODE IS BROKEN BECAUSE VANILLA MINECRAFT BLOCK CHANGED HANDLING IS KAPUT
        // https://github.com/bergerhealer/TrainCarts/issues/398#issuecomment-713913866
        /*
        // Decrease sucking cooldown until 0 is reached
        int cooldown = entity.getSuckingCooldown();
        if (cooldown > 0) {
            entity.setSuckingCooldown(cooldown - 1);
        } else if (entity.suckItems()) {
            entity.setSuckingCooldown(4);
            entity.update();
        }
        */

        // Just suck in items every tick. Whatever.
        entity.setSuckingCooldown(0);
        entity.suckItems();
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
        super.onTrainSaved(data);
        Util.saveInventoryToConfig(entity.getInventory(), data);
    }

    @Override
    public void onTrainSpawned(ConfigurationNode data) {
        super.onTrainSpawned(data);
        Util.loadInventoryFromConfig(entity.getInventory(), data);
    }
}
