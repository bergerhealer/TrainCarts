package com.bergerkiller.bukkit.tc.attachments.ui.models;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.MapResourcePack.ResourceType;
import com.bergerkiller.bukkit.common.map.util.Model.ModelOverride;
import com.bergerkiller.bukkit.common.map.util.ModelInfoLookup;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

import com.bergerkiller.bukkit.tc.attachments.ui.models.listing.*;

/**
 * Provides a listing of all the models available in a resource pack. The information can be
 * used to show a dialog to a Player which can be browsed.
 */
public class ResourcePackModelListing extends ListedRootLoader {
    private final Plugin plugin;
    private MapResourcePack resourcePack;

    /**
     * Initializes a new model listing. {@link #buildDialog(Player)} cannot be used
     * and a plugin must be specified there.
     */
    public ResourcePackModelListing() {
        this(null);
    }

    /**
     * Initializes a new model listing. The input plugin is only used for showing a dialog with
     * items to players.
     *
     * @param plugin
     */
    public ResourcePackModelListing(Plugin plugin) {
        this.plugin = plugin;
        this.resourcePack = null;
    }

    /**
     * Builds a new dialog to display this model listing inside of. The returned builder object can
     * be further configured, where {@link DialogBuilder#show()} will display the dialog to the Player.<br>
     * <br>
     * <b>A valid plugin must have been specified in the constructor of this listing</b>
     *
     * @param player Player to which to show the dialog
     * @return builder
     */
    public DialogBuilder buildDialog(Player player) {
        if (this.plugin == null) {
            throw new IllegalStateException("No plugin was specified on constructor, cannot show dialog");
        }
        return new DialogBuilder(this.plugin, player, this);
    }

    /**
     * Builds a new dialog to display this model listing inside of. The returned builder object can
     * be further configured, where {@link DialogBuilder#show()} will display the dialog to the Player.
     *
     * @param player Player to which to show the dialog
     * @param plugin Plugin that will be managing this dialog
     * @return builder
     */
    public DialogBuilder buildDialog(Player player, Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin is null");
        }
        return new DialogBuilder(plugin, player, this);
    }

    /**
     * Shows a navigatable dialog of item models to the player. This is a creative menu dialog,
     * so the player can take items from the menu and drag them in their own inventory.
     *
     * @param player Player to show a creative model listing dialog to
     */
    public void showCreativeDialog(Player player) {
        buildDialog(player).asCreativeMenu().show();
    }

    /**
     * Closes any open model listing dialog for the Player specified. Any consumers of the dialog
     * will be notified of the closure.
     *
     * @param player Player to close the dialog for
     */
    public static void closeDialog(Player player) {
        ResourcePackModelListingDialog.close(player);
    }

    /**
     * Closes all model listing dialogs for all Players on the server. Useful to be run on plugin
     * shutdown.
     */
    public static void closeAllDialogs() {
        ResourcePackModelListingDialog.closeAll();
    }

    /**
     * Closes all model listing dialogs for all Players on the server that were managed by the
     * plugin specified.
     *
     * @param plugin Plugin for which to close all dialogs
     */
    public static void closeAllDialogs(Plugin plugin) {
        ResourcePackModelListingDialog.closeAllByPlugin(plugin);
    }

    /**
     * Shows a model listing dialog that was pre-configured using {@link DialogBuilder}.
     * Please use {@link DialogBuilder#show()} instead.
     *
     * @param dialogOptions
     * @return future
     */
    public static CompletableFuture<DialogResult> showDialog(DialogBuilder dialogOptions) {
        return ResourcePackModelListingDialog.show(dialogOptions);
    }

    /**
     * Gets whether this model listing is empty. It will be empty if there is no resource pack set,
     * or the resource pack doesn't override any item models.
     *
     * @return True if empty
     */
    public boolean isEmpty() {
        return root.itemModels().isEmpty();
    }

    /**
     * Clears all listed items
     */
    public void clear() {
        root = new ListedRoot();
    }

    /**
     * Gets the root entry, which contains the namespace for which custom models exist
     *
     * @return root
     */
    public ListedRoot root() {
        return root;
    }

    /**
     * Gets whether the ItemStack specified is a bare item (item without title/details)
     * mapped to a model in this listing
     *
     * @param item Item to check
     * @return True if contained as an item model
     */
    public boolean isBareItem(ItemStack item) {
        return root.bareItemStacks().containsKey(item);
    }

    /**
     * Gets {@link ListedItemModel} metadata about a bare item contained inside this
     * model listing. Returns null if the input item is not one of the bare items
     * listed.
     *
     * @param bareItem Bare ItemStack
     * @return ListedItemModel of this item, or null if not listed
     * @see ListedRoot#bareItemStacks()
     */
    public ListedItemModel getBareItemModel(ItemStack bareItem) {
        return root.bareItemStacks().get(bareItem);
    }

    /**
     * Gets the {@link MapResourcePack} that was last loaded in using
     * {@link #load(MapResourcePack)}
     *
     * @return loaded resource pack
     */
    public MapResourcePack loadedResourcePack() {
        return resourcePack;
    }

    /**
     * Filters the contents of this resource pack model listing based on a search query.
     * If the query matches the pattern of a namespace and/or directory path, then only
     * that directory tree is displayed. Otherwise, all item models and directories whose name
     * includes the query (case-insensitive) is included.
     *
     * @param query Search query
     * @return filtered result
     */
    public ResourcePackModelListing filter(String query) {
        ResourcePackModelListing filteredListing = new ResourcePackModelListing(this.plugin);
        filteredListing.resourcePack = this.resourcePack;
        filteredListing.loadFromListing(this.root, query);
        return filteredListing;
    }

    /**
     * Initializes this model listing by parsing the files contained inside a loaded resource
     * pack
     *
     * @param resourcePack
     */
    public void load(MapResourcePack resourcePack) {
        clear();

        this.resourcePack = resourcePack;

        // Figure out all minecraft items that are overrided by the resource pack
        boolean logged = false;
        Set<String> allOverridedModels = new HashSet<String>();
        for (MapResourcePack p = resourcePack; p != null && p != MapResourcePack.VANILLA; p = p.getBase()) {
            if (!logged) {
                logged = true;
                logLoading("Loading resource pack item model lists");
            }
            allOverridedModels.addAll(p.listResources(ResourceType.MODELS, "item", false));
        }
        if (allOverridedModels.isEmpty()) {
            return;
        }

        // Figure out what ItemStack corresponds with each item model and register them all
        for (Material material : ItemUtil.getItemTypes()) {
            for (ItemStack item : ItemUtil.getItemVariants(material)) {
                String path = "item/" + ModelInfoLookup.lookupItemRenderOptions(item).lookupModelName();
                if (allOverridedModels.contains(path)) {
                    for (ModelOverride override : resourcePack.getModelInfo(path).getOverrides()) {
                        // Figure out what the 'credit' are for this model by reading the actual model
                        // No big deal if this fails.
                        String credit = resourcePack.getModelInfo(override.model).getCredit();

                        ItemStack modelItem = override.applyToItem(item);
                        root.addListedItem(override.model, modelItem, credit);
                    }
                }
            }
        }

        logLoading("Resource pack item model lists loaded");
    }

    private void logLoading(String message) {
        if (plugin != null) {
            plugin.getLogger().log(Level.INFO, "[Resource Pack Models] " + message);
        } else {
            System.out.println("[Resource Pack Models] " + message);
        }
    }
}
