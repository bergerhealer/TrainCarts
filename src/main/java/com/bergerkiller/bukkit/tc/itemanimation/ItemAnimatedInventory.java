package com.bergerkiller.bukkit.tc.itemanimation;

import com.bergerkiller.bukkit.common.inventory.InventoryBase;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation.Target;
import com.bergerkiller.bukkit.tc.utils.GroundItemsInventory;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Redirects calls to a base inventory, while showing item animations during item transfers
 */
public class ItemAnimatedInventory extends InventoryBase {
    private final Inventory source;
    private final ItemStack[] original;
    private final Target other;
    private final Target self;

    public ItemAnimatedInventory(Inventory inventory, Target self, Target other) {
        this.other = other;
        this.self = self;
        this.source = inventory;
        this.original = ItemUtil.getClonedContents(inventory);
    }

    public static Inventory convert(Inventory inventory, Target self, Target other) {
        return new ItemAnimatedInventory(inventory, self, other);
    }

    @Override
    public void setItem(int index, ItemStack newitem) {
        ItemStack olditem = this.original[index];
        this.source.setItem(index, newitem);
        Target selfTarget = this.getSelfAt(index);
        this.original[index] = ItemUtil.cloneItem(newitem);
        if (olditem == null) {
            if (newitem != null) {
                ItemAnimation.start(other, selfTarget, newitem);
            }
        } else {
            if (newitem == null) {
                ItemAnimation.start(selfTarget, other, olditem);
            } else {
                //same type?
                if (ItemUtil.equalsIgnoreAmount(olditem, newitem)) {
                    // Obtain an item stack (trans) to do an animation with
                    // Switch between self and other based on changed amount
                    ItemStack trans = ItemUtil.cloneItem(newitem);
                    int newAmount = trans.getAmount() - olditem.getAmount();
                    if (newAmount > 0) {
                        trans.setAmount(newAmount);
                        ItemAnimation.start(other, selfTarget, trans);
                    } else if (newAmount < 0) {
                        trans.setAmount(-newAmount);
                        ItemAnimation.start(selfTarget, other, trans);
                    }
                } else {
                    //swap
                    ItemAnimation.start(selfTarget, other, olditem);
                    ItemAnimation.start(other, selfTarget, newitem);
                }
            }
        }
    }

    public Target getSelfAt(int index) {
        if (this.source instanceof GroundItemsInventory) {
            Item entity = ((GroundItemsInventory) this.source).getEntity(index);
            return Target.forEntity(entity);
        }
        return self;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.source.getItem(index);
    }

    @Override
    public int getSize() {
        return this.source.getSize();
    }
}
