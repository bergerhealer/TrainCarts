package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

/**
 * An item model from the resource pack that should be listed
 */
public final class ListedItemModel extends ListedEntry {
    private static final boolean CAN_SHOW_ITEM_MODEL_LORE = Common.hasCapability("Common:CommonItemStack:ItemModel");

    private final String fullPath;
    private final String path;
    private final String name;
    private final String nameLowerCase;
    private final String credit;
    private final CommonItemStack bareItem;
    private final CommonItemStack item;

    public ListedItemModel(String fullPath, String path, String name, String credit, CommonItemStack item) {
        this.nestedItemCount = 1;
        this.fullPath = fullPath;
        this.path = path;
        this.name = name;
        this.nameLowerCase = name.toLowerCase(Locale.ENGLISH);
        this.credit = credit;
        this.bareItem = item;
        this.item = item.clone();
        this.initializeItem();
    }

    private ListedItemModel(ListedItemModel itemModel) {
        this.fullPath = itemModel.fullPath;
        this.path = itemModel.path;
        this.name = itemModel.name;
        this.nameLowerCase = itemModel.nameLowerCase;
        this.credit = itemModel.credit;
        this.bareItem = itemModel.bareItem;
        this.item = itemModel.item;
    }

    private void initializeItem() {
        item.hideAllAttributes();

        String origItemName = this.item.getDisplayNameMessage();
        item.setCustomNameMessage(ChatColor.AQUA + name);
        item.addLoreMessage(ChatColor.WHITE.toString() + ChatColor.ITALIC + fullPath);

        item.addLoreLine();

        {
            addLoreProperty(item, "Item", origItemName);
        }

        if (CAN_SHOW_ITEM_MODEL_LORE) {
            showItemModelInfo(item);
        }

        if (item.hasCustomModelData()) {
            addLoreProperty(item, "Custom model data", item.getCustomModelData());
        }

        if (item.isDamageSupported() && item.getDamage() != 0) {
            addLoreProperty(item, "Damage", item.getDamage());
        }

        if (item.isUnbreakable()) {
            addLoreProperty(item, "Unbreakable", true);
        }

        if (!credit.isEmpty()) {
            item.addLoreLine();
            item.addLoreMessage(ChatColor.DARK_BLUE + credit);
        }
    }

    private static void addLoreProperty(CommonItemStack item, String name, Object value) {
        item.addLoreMessage(ChatColor.DARK_GRAY + name + ": " + ChatColor.GRAY + value);
    }

    private static void showItemModelInfo(CommonItemStack item) {
        if (item.hasItemModel()) {
            addLoreProperty(item, "Item Model", item.getItemModel().toString());
        }
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

    /**
     * Gets Model credit information. This is a short sentence describing who
     * made the model.
     *
     * @return Model credit details. Empty String if not available.
     */
    public String credit() {
        return credit;
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
    public CommonItemStack item() {
        return item;
    }

    /**
     * Gets a bare item, without any decorative display name or lore information.
     * If it is not important to be able to identify the item, this can be used to
     * reduce wasted space in memory / configuration / disk.
     *
     * @return bare item
     */
    public CommonItemStack bareItem() {
        return bareItem;
    }

    @Override
    public CommonItemStack createIconItem(DialogBuilder options) {
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
