package com.bergerkiller.bukkit.tc.editor;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getFirst;

import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

public class TCMapControl {
    private static final Material FILLED_MAP_TYPE = getFirst("FILLED_MAP", "LEGACY_MAP");
    private static final Material EMPTY_MAP_TYPE = getFirst("MAP", "LEGACY_EMPTY_MAP");

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
        if (!isTCMapItem(item) || !player.isOnline()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        UUID uuid = ItemUtil.getMetaTag(item).getUUID("editor");
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack playerItem = inv.getItem(i);
            if (isTCMapItem(playerItem) && ItemUtil.getMetaTag(playerItem).getUUID("editor").equals(uuid)) {
                ItemStack newItem = playerItem.clone();
                if (opened) {
                    newItem.setType(FILLED_MAP_TYPE);
                } else {
                    newItem.setType(EMPTY_MAP_TYPE);
                }
                inv.setItem(i, newItem);
                return;
            }
        }
    }

    public static boolean isTCMapItem(ItemStack item) {
        if (item == null || (item.getType() != FILLED_MAP_TYPE && item.getType() != EMPTY_MAP_TYPE)) {
            return false;
        }

        // Check NBT tags for valid signature
        CommonTagCompound tag = ItemUtil.getMetaTag(item);
        if (tag == null) {
            return false;
        }

        return tag.getUUID("editor") != null && tag.getValue("plugin", "").equals("TrainCarts");
    }

    public static ItemStack createTCMapItem() {
        ItemStack item = MapDisplay.createMapItem(TCMapEditor.class);
        item.setType(EMPTY_MAP_TYPE);
        ItemUtil.setDisplayName(item, "TrainCarts Editor");
        ItemUtil.getMetaTag(item).putValue("plugin", "TrainCarts");
        ItemUtil.getMetaTag(item).putUUID("editor", UUID.randomUUID());
        item.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        return item;
    }
}
