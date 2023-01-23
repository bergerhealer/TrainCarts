package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * An item model from the resource pack that should be listed
 */
public final class ListedItemModel extends ListedEntry {
    private final String fullPath;
    private final String path;
    private final String name;
    private final String nameLowerCase;
    private final ItemStack item;

    // Figure out the default HideFlags to put in the NBT
    private static final int DEFAULT_HIDE_FLAGS;
    static {
        ItemStack item = ItemUtil.createItem(MaterialUtil.getFirst("GLASS", "LEGACY_GLASS"), 1);
        ItemMeta meta = item.getItemMeta();
        for (ItemFlag flag : ItemFlag.values()) {
            if (flag.name().startsWith("HIDE_")) { // Probably is all of them
                meta.addItemFlags(flag);
            }
        }
        item.setItemMeta(meta);
        CommonTagCompound nbt = ItemUtil.getMetaTag(item, true);
        DEFAULT_HIDE_FLAGS = (nbt == null) ? 0 : nbt.getValue("HideFlags", 0);
    }

    public ListedItemModel(String fullPath, String path, String name, ItemStack item) {
        this.nestedItemCount = 1;
        this.fullPath = fullPath;
        this.path = path;
        this.name = name;
        this.nameLowerCase = name.toLowerCase(Locale.ENGLISH);
        this.item = ItemUtil.createItem(item);
    }

    private ListedItemModel(ListedItemModel itemModel) {
        this.fullPath = itemModel.fullPath;
        this.path = itemModel.path;
        this.name = itemModel.name;
        this.nameLowerCase = itemModel.nameLowerCase;
        this.item = itemModel.item;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void postInitialize() {
        CommonTagCompound nbt = ItemUtil.getMetaTag(this.item);

        // Hides everything except the lores we add
        nbt.putValue("HideFlags", DEFAULT_HIDE_FLAGS);

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

    @Override
    public ItemStack item() {
        return item;
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
        entry.isPostInitialized = true;
        entry.setParent(newParent);
        root.allListedItems.add(entry);
        return entry;
    }
}
