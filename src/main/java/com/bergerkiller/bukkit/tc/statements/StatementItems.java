package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.inventory.MergedInventory;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Generic base implementation for statements that evaluate inventory
 * views of carts or trains.
 */
public abstract class StatementItems extends Statement {

    /**
     * Gets the inventory of items of a given cart for this statement type
     *
     * @param member
     * @return inventory
     */
    public abstract Inventory getInventory(MinecartMember<?> member);

    /**
     * Gets the inventory of items of a given train for this statement type
     *
     * @param group
     * @return inventory
     */
    public abstract Inventory getInventory(MinecartGroup group);

    @Override
    public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
        Inventory inventory = getInventory(member);
        if (inventory == null) {
            inventory = new MergedInventory(); // simulate empty inventory
        }
        int count = ItemUtil.getItemCount(inventory, null, -1);
        return Util.evaluate(count, text);
    }

    @Override
    public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
        Inventory inventory = getInventory(group);
        if (inventory == null) {
            inventory = new MergedInventory(); // simulate empty inventory
        }
        int count = ItemUtil.getItemCount(inventory, null, -1);
        return Util.evaluate(count, text);
    }

    public boolean handleInventory(Inventory inv, String[] items) {
        if (inv == null) {
            return false;
        }

        for (String itemname : items) {
            int count;
            if (inv.getSize() == 0) {
                // Zero-slot inventory, no need to search anything
                count = 0;
            } else {
                // Search and count the items in the inventory
                int opidx = Util.getOperatorIndex(itemname);
                String itemnamefixed;
                if (opidx > 0) {
                    itemnamefixed = itemname.substring(0, opidx);
                } else {
                    itemnamefixed = itemname;
                }
                for (ItemParser parser : Util.getParsers(itemnamefixed)) {
                    count = ItemUtil.getItemCount(inv, parser.getType(), parser.getData());
                    if (opidx == -1) {
                        if (parser.hasAmount()) {
                            if (count >= parser.getAmount()) {
                                return true;
                            }
                        } else if (count > 0) {
                            return true;
                        }
                    } else if (Util.evaluate(count, itemname)) {
                        return true;
                    }
                }

                // Check for 'special' named items
                count = 0;
                for (ItemStack item : inv) {
                    if (item != null && ItemUtil.hasDisplayName(item) && ItemUtil.getDisplayName(item).equals(itemnamefixed)) {
                        count += item.getAmount();
                    }
                }
            }

            if (Util.evaluate(count, itemname)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleArray(MinecartMember<?> member, String[] items, SignActionEvent event) {
        return handleInventory(getInventory(member), items);
    }

    @Override
    public boolean handleArray(MinecartGroup group, String[] items, SignActionEvent event) {
        return handleInventory(getInventory(group), items);
    }
}
