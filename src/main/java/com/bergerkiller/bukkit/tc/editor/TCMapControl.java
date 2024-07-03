package com.bergerkiller.bukkit.tc.editor;

import java.util.UUID;

import com.bergerkiller.bukkit.common.inventory.CommonItemMaterials;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;

public class TCMapControl {

    /**
     * Updates a TC Map Sign Editor map item in the inventory of a player.
     * When opened is false, an unopened map item will be added. This map item
     * can be opened again by clicking on a sign or rails that has signs. When
     * it is opened elsewhere, the last-clicked sign or rails is remembered.<br>
     * <br>
     * When opened is true, the map will be opened and player control locked.
     * 
     * @param player to update the map item for
     * @param opened whether the map item is opened and controlled
     */
    public static void updateMapItem(Player player, boolean opened) {
        PlayerInventory inv = player.getInventory();
        ItemStack heldItem = inv.getItem(inv.getHeldItemSlot());

        // Check if the item the player currently has selected is our item
        // If not, find the slot that has it
        if (!isTCMapItem(heldItem)) {
            return; //todo
        }

        updateMapItem(player, heldItem, opened);
    }

    public static void updateMapItem(Player player, ItemStack item, boolean opened) {
        updateMapItem(player, CommonItemStack.of(item), opened);
    }

    public static void updateMapItem(Player player, CommonItemStack item, boolean opened) {
        if (!isTCMapItem(item) || !player.isValid()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        UUID uuid = item.getCustomData().getUUID("editor");
        for (int i = 0; i < inv.getSize(); i++) {
            CommonItemStack playerItem = CommonItemStack.of(inv.getItem(i));
            if (isTCMapItem(playerItem) && playerItem.getCustomData().getUUID("editor").equals(uuid)) {
                CommonItemStack newItem = playerItem.clone();
                if (opened) {
                    newItem.setType(CommonItemMaterials.FILLED_MAP);
                } else {
                    newItem.setType(CommonItemMaterials.EMPTY_MAP);
                }
                inv.setItem(i, newItem.toBukkit());
                return;
            }
        }
    }

    public static boolean isTCMapItem(ItemStack item) {
        return isTCMapItem(CommonItemStack.of(item));
    }

    public static boolean isTCMapItem(CommonItemStack item) {
        if (item == null || (!item.isType(CommonItemMaterials.FILLED_MAP) && item.isType(CommonItemMaterials.EMPTY_MAP))) {
            return false;
        }

        // Check NBT tags for valid signature
        CommonTagCompound tag = item.getCustomData();
        if (tag == null) {
            return false;
        }

        return tag.getUUID("editor") != null && tag.getValue("plugin", "").equals("TrainCarts");
    }

    public static ItemStack createTCMapItem() {
        CommonItemStack item = CommonItemStack.of(MapDisplay.createMapItem(TCMapEditor.class));
        item.setType(CommonItemMaterials.EMPTY_MAP);
        item.setCustomNameMessage("TrainCarts Editor");
        item.updateCustomData(tag -> {
            tag.putValue("plugin", "TrainCarts");
            tag.putUUID("editor", UUID.randomUUID());
        });
        item.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        return item.toBukkit();
    }
}
