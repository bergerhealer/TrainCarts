package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.common.inventory.InventoryBaseImpl;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public class StatementPlayerHand extends StatementItems {

    @Override
    public boolean match(String text) {
        return text.startsWith("playerhand");
    }

    @Override
    public boolean matchArray(String text) {
        return text.equals("ph");
    }

    private void addItems(MinecartMember<?> member, ArrayList<ItemStack> itemsList) {
        for (Player player : member.getEntity().getPlayerPassengers()) {
            ItemStack item1 = HumanHand.getItemInMainHand(player);
            ItemStack item2 = HumanHand.getItemInOffHand(player);
            if (!LogicUtil.nullOrEmpty(item1)) itemsList.add(item1);
            if (!LogicUtil.nullOrEmpty(item2)) itemsList.add(item2);
        }
    }

    @Override
    public Inventory getInventory(MinecartMember<?> member) {
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        addItems(member, items);
        return new InventoryBaseImpl(items, false);
    }

    @Override
    public Inventory getInventory(MinecartGroup group) {
        ArrayList<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
        for (MinecartMember<?> member : group) {
            addItems(member, items);
        }
        return new InventoryBaseImpl(items, false);
    }
}
