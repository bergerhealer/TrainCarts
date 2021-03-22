package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Checks for the items in a train. This includes items in
 * chest minecarts and hopper minecarts.
 */
public class StatementTrainItems extends StatementItems {

    @Override
    public boolean match(String text) {
        return text.startsWith("items");
    }

    @Override
    public boolean matchArray(String text) {
        return text.equals("i");
    }

    @Override
    public Inventory getInventory(MinecartMember<?> member) {
        org.bukkit.entity.Entity entity = member.getEntity().getEntity();
        if (entity instanceof InventoryHolder) {
            return ((InventoryHolder) entity).getInventory();
        } else {
            return null;
        }
    }

    @Override
    public Inventory getInventory(MinecartGroup group) {
        return group.getInventory();
    }
}
