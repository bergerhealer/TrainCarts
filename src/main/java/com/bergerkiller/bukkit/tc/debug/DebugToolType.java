package com.bergerkiller.bukkit.tc.debug;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * Debug tool instance
 */
public interface DebugToolType {

    /**
     * Gets the identifier token stored inside the item to uniquely
     * identify this debug tool type
     *
     * @return identifier
     */
    String getIdentifier();

    /**
     * Gets the text displayed as a tooltip for the debug tool item
     *
     * @return title
     */
    String getTitle();

    /**
     * Gets a short description of this debug tool type. This is displayed
     * when the debug tool is hovered over as a lore.
     *
     * @return description
     */
    String getDescription();

    /**
     * Gets short instructions of how to use the debug tool item. This is
     * displayed as a message to the player when the item is given.
     *
     * @return instructions
     */
    String getInstructions();

    /**
     * Whether this tool type handles left-click interaction
     *
     * @return True if left-click is handled
     */
    default boolean handlesLeftClick() {
        return false;
    }

    /**
     * Called when a player interacts with a block
     *
     * @param trainCarts TrainCarts main plugin instance
     * @param player Player that interacted
     * @param clickedBlock Block that was interacted with
     * @param item The debug tool item player is holding while interacting
     * @param isRightClick Whether this is a right-click (true) or left-click (false)
     */
    void onBlockInteract(TrainCarts trainCarts, Player player, Block clickedBlock, ItemStack item, boolean isRightClick);

    /**
     * Gives this debug tool type as an item to a player
     *
     * @param player
     */
    default void giveToPlayer(Player player) {
        ItemStack item = ItemUtil.createItem(Material.STICK, 1);
        CommonTagCompound metadata = ItemUtil.getMetaTag(item, true);
        metadata.putValue("TrainCartsDebug", this.getIdentifier());
        saveMetadata(metadata);
        ItemUtil.setDisplayName(item, this.getTitle());
        ItemUtil.addLoreName(item, this.getDescription());

        // Update item in main hand, if it is a debug item
        if (DebugTool.updateToolItem(player, item)) {
            player.sendMessage(ChatColor.GREEN + "Debug tool updates to a " + this.getTitle());
            player.sendMessage(ChatColor.YELLOW + this.getDescription());
            return;
        }

        // Give new item
        player.getInventory().addItem(item);

        // Display a message to the player to explain what it is
        player.sendMessage(ChatColor.GREEN + "Given a " + this.getTitle());
        player.sendMessage(ChatColor.YELLOW + this.getDescription());
    }

    /**
     * Loads metadata stored in an item into this tool instance
     *
     * @param metadata Item Metadata
     */
    default void loadMetadata(CommonTagCompound metadata) {
    }

    /**
     * Saves metadata stored in this tool into the metadata of an item
     *
     * @param metadata Item Metadata
     * @see #giveToPlayer(Player)
     */
    default void saveMetadata(CommonTagCompound metadata) {
    }
}
