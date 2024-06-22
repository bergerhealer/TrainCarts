package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.concurrent.CompletableFuture;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.tc.TCConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.attachments.ui.models.ResourcePackModelListing;

/**
 * Configures the behavior of a model listing dialog
 */
public final class DialogBuilder implements Cloneable {
    private static final ItemStack DEFAULT_NAMESPACE_ITEM = createDefaultNamespaceItem();
    private static final ItemStack DEFAULT_DIRECTORY_ITEM = createDefaultDirectoryItem();

    private final Plugin plugin;
    private final Player player;
    private final ResourcePackModelListing listing;
    private boolean creativeMenu = false;
    private boolean compactingEnabled = TCConfig.modelSearchCompactFolders;
    private String title = "Resource Pack Models";
    private String query = "";
    private String browsedLocation = "";
    private int browsedPage = 0;
    private boolean cancelOnRootRightClick = false;
    private ItemStack namespaceItem = DEFAULT_NAMESPACE_ITEM;
    private ItemStack directoryItem = DEFAULT_DIRECTORY_ITEM;

    public DialogBuilder(Plugin plugin, Player player, ResourcePackModelListing listing) {
        this.plugin = plugin;
        this.player = player;
        this.listing = listing;
    }

    /**
     * Gets the plugin that manages the dialog
     *
     * @return plugin owner
     */
    public Plugin plugin() {
        return plugin;
    }

    /**
     * Gets the Player to which the dialog will be displayed
     *
     * @return player
     */
    public Player player() {
        return player;
    }

    /**
     * Gets the resource pack model listing instance from which model information will be
     * read to display in the dialog.
     *
     * @return listing
     */
    public ResourcePackModelListing listing() {
        return listing;
    }

    /**
     * Sets the initial browsed location in the dialog when the dialog is first opened.
     * This can be used to restore a previous path and/or page that the player had open.
     * If the entered path and/or page is invalid, the dialog resets back to the root
     * location.
     *
     * @param path Initial path to the namespace or directory opened in the dialog.
     *             The Player can move back up to the parent directories.
     * @param page Initial opened page. 0 is the first page.
     * @return this
     */
    public DialogBuilder navigate(String path, int page) {
        browsedLocation = path;
        browsedPage = page;
        return this;
    }

    /**
     * Gets the initial/current path browsed in the dialog. If part of the dialog result,
     * this will be the path browsed by the Player when the dialog was closed or an item
     * was selected.
     *
     * @return Initial or current browsed path
     */
    public String getBrowsedPath() {
        return browsedLocation;
    }

    /**
     * Gets the initial/current page opened in the dialog. If part of the dialog result,
     * this will be the page that was selected by the Player when the dialog was closed or
     * an item was selected.
     *
     * @return Initial or current browsed page
     */
    public int getBrowsedPage() {
        return browsedPage;
    }

    /**
     * Makes the dialog into a creative menu. No item can be selected, but items can be taken
     * from the inventory as if it is a creative menu. If used, {@link #show()} will always only return
     * a cancelled result.
     *
     * @return this
     */
    public DialogBuilder asCreativeMenu() {
        creativeMenu = true;
        return this;
    }

    /**
     * Gets whether the dialog is displayed as a creative menu where the Player can take items
     * from it.
     *
     * @return True if the dialog is displayed as a creative menu
     */
    public boolean isCreativeMenu() {
        return creativeMenu;
    }

    /**
     * Sets whether to show the models inside a folder, instead of the folders,
     * if it contains too few models inside
     *
     * @param compact True to compact
     * @return this
     */
    public DialogBuilder setCompactingEnabled(boolean compact) {
        this.compactingEnabled = compact;
        return this;
    }

    /**
     * Gets whether to show the models inside a folder, instead of the folders,
     * if it contains too few models inside
     *
     * @return True if folders are automatically unpacked
     */
    public boolean isCompactingEnabled() {
        return compactingEnabled;
    }

    /**
     * Sets an alternative title to use for the dialog window
     *
     * @param title New title to use
     * @return this
     */
    public DialogBuilder title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Gets the title of the dialog window
     *
     * @return dialog window title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the item displayed in place of item model namespaces. Item may not be
     * null. Display name and lore descriptions are added automatically.
     *
     * @param item Item to use as a namespace icon
     * @return this
     */
    public DialogBuilder namespaceIconItem(ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("Item may not be null");
        }
        this.namespaceItem = item;
        return this;
    }

    /**
     * Gets the item displayed in place of item model directories
     *
     * @return namespace icon Item
     */
    public ItemStack getNamespaceIconItem() {
        return namespaceItem;
    }

    /**
     * Sets the item displayed in place of item model directories. Item may not be
     * null. Display name and lore descriptions are added automatically.
     *
     * @param item Item to use as a directory icon
     * @return this
     */
    public DialogBuilder directoryIconItem(ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("Item may not be null");
        }
        this.directoryItem = item;
        return this;
    }

    /**
     * Gets the item displayed in place of item model directories
     *
     * @return directory icon Item
     */
    public ItemStack getDirectoryIconItem() {
        return directoryItem;
    }

    /**
     * Sets it to cancel the dialog when it is right-clicked and the dialog
     * can't move back any further.
     *
     * @return this
     */
    public DialogBuilder cancelOnRootRightClick() {
        return cancelOnRootRightClick(true);
    }

    /**
     * Sets whether to cancel the dialog when it is right-clicked and the dialog
     * can't move back any further.
     *
     * @param cancel True to cancel the dialog when right-clicking at root
     * @return this
     */
    public DialogBuilder cancelOnRootRightClick(boolean cancel) {
        this.cancelOnRootRightClick = cancel;
        return this;
    }

    /**
     * Gets whether right-clicking the menu while at the root directory layer closes
     * the dialog.
     *
     * @return True if cancel on right clicking at root is enabled
     */
    public boolean isCancelOnRootRightClick() {
        return cancelOnRootRightClick;
    }

    /**
     * Sets an initial search query for when the dialog window is opened.
     * If set to non-empty, will only show item models that match the contents
     * of the query.
     *
     * @param query Search query
     * @return this
     */
    public DialogBuilder query(String query) {
        this.query = query;
        return this;
    }

    /**
     * Gets the search query that filters the item models displayed in the dialog
     *
     * @return search query
     */
    public String getQuery() {
        return query;
    }

    /**
     * Shows the item model listing dialog the Player. Any previous open dialogs are closed.
     *
     * @return a future completed when an item is selected or the dialog is closed. The result
     *         can contain a selected item (model) if successful.
     */
    public CompletableFuture<DialogResult> show() {
        return ResourcePackModelListing.showDialog(this);
    }

    /**
     * Clones the configured options of this dialog so that it can be modified and shown again.
     */
    @Override
    public DialogBuilder clone() {
        DialogBuilder clone = new DialogBuilder(this.plugin, this.player, this.listing);
        clone.browsedLocation = this.browsedLocation;
        clone.browsedPage = this.browsedPage;
        clone.creativeMenu = this.creativeMenu;
        clone.title = this.title;
        clone.query = this.query;
        clone.cancelOnRootRightClick = this.cancelOnRootRightClick;
        clone.namespaceItem = this.namespaceItem;
        clone.directoryItem = this.directoryItem;
        clone.compactingEnabled = this.compactingEnabled;
        return clone;
    }

    private static ItemStack createDefaultNamespaceItem() {
        return CommonItemStack.create(MaterialUtil.getFirst("NAME_TAG", "LEGACY_NAME_TAG"), 1)
                .hideAllAttributes()
                .toBukkit();
    }

    private static ItemStack createDefaultDirectoryItem() {
        return CommonItemStack.create(MaterialUtil.getFirst("CHEST", "LEGACY_CHEST"), 1)
                .hideAllAttributes()
                .toBukkit();
    }
}
