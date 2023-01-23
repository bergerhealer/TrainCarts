package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

/**
 * An item model from the resource pack that should be listed
 */
public final class ListedItemModel extends ListedEntry {
    private final String fullPath;
    private final String path;
    private final String name;
    private final String nameLowerCase;
    private final ItemStack bareItem;
    private final ItemStack item;

    public ListedItemModel(String fullPath, String path, String name, ItemStack item) {
        this.nestedItemCount = 1;
        this.fullPath = fullPath;
        this.path = path;
        this.name = name;
        this.nameLowerCase = name.toLowerCase(Locale.ENGLISH);
        this.bareItem = item;
        this.item = ItemUtil.createItem(item);
        this.initializeItem();
    }

    private ListedItemModel(ListedItemModel itemModel) {
        this.fullPath = itemModel.fullPath;
        this.path = itemModel.path;
        this.name = itemModel.name;
        this.nameLowerCase = itemModel.nameLowerCase;
        this.bareItem = itemModel.bareItem;
        this.item = itemModel.item;
    }

    @SuppressWarnings("deprecation")
    private void initializeItem() {
        ListedRootLoader.hideItemAttributes(this.item);

        CommonTagCompound nbt = ItemUtil.getMetaTag(this.item);

        String itemName = ItemUtil.getDisplayName(this.item);
        ItemUtil.setDisplayName(this.item, ChatColor.AQUA + name);
        ItemUtil.addLoreName(this.item, ChatColor.WHITE.toString() + ChatColor.ITALIC + fullPath);

        addLoreSpacer(this.item);

        {
            addLoreProperty(this.item, "Item", itemName);
        }

        {
            int cmd = nbt.containsKey("CustomModelData") ? nbt.getValue("CustomModelData", 0) : 0;
            if (cmd != 0) {
                addLoreProperty(this.item, "Custom model data", cmd);
            }
        }

        {
            short damage = this.item.getDurability();
            if (damage != 0) {
                addLoreProperty(this.item, "Damage", damage);
            }
        }

        if (nbt.containsKey("Unbreakable") && nbt.getValue("Unbreakable", false)) {
            addLoreProperty(this.item, "Unbreakable", true);
        }
    }

    private static void addLoreSpacer(ItemStack item) {
        ItemUtil.addLoreName(item, "");
    }

    private static void addLoreProperty(ItemStack item, String name, Object value) {
        ItemUtil.addLoreName(item, ChatColor.DARK_GRAY + name + ": " + ChatColor.GRAY + value);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String nameLowerCase() {
        return nameLowerCase;
    }

    /**
     * Gets the path of this item model, excluding the namespace prefix
     *
     * @return path
     */
    public String path() {
        return path;
    }

    @Override
    public String fullPath() {
        return fullPath;
    }

    @Override
    public int sortPriority() {
        return 3;
    }

    @Override
    public ListedNamespace namespace() {
        return parent().namespace();
    }

    /**
     * Gets the ItemStack that represents this item model. If put in an inventory or armorstand slot,
     * the model will be displayed.
     *
     * @return item
     */
    public ItemStack item() {
        return item;
    }

    /**
     * Gets a bare item, without any decorative display name or lore information.
     * If it is not important to be able to identify the item, this can be used to
     * reduce wasted space in memory / configuration / disk.
     *
     * @return bare item
     */
    public ItemStack bareItem() {
        return bareItem;
    }

    @Override
    public ItemStack createIconItem(DialogBuilder options) {
        return item.clone();
    }

    @Override
    public List<ListedItemModel> explode() {
        return Collections.singletonList(this);
    }

    @Override
    protected void fillItems(List<ListedItemModel> items) {
        items.add(this);
    }

    @Override
    public String toString() {
        return "Item name=" + name + " path=" + fullPath + " item=" + item;
    }

    @Override
    protected ListedEntry cloneSelf(ListedNamespace namespace) {
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace is required");
        }

        return new ListedItemModel(this);
    }

    @Override
    protected ListedItemModel findOrCreateInRoot(ListedRoot root) {
        // Find the directory or namespace in which this item model resides
        ListedEntry newParent = this.parent().findOrCreateInRoot(root);
        // Copy self and assign it to this parent
        ListedItemModel entry = new ListedItemModel(this);
        entry.setParent(newParent);
        root.allListedItems.add(entry);
        return entry;
    }
}
