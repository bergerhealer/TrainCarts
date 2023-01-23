package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.utils.ItemUtil;

/**
 * A root namespace, such as 'minecraft'
 */
public final class ListedNamespace extends ListedEntry {
    private final String name;
    private final String nameLowerCase;
    final Map<String, ListedDirectory> directories;

    public ListedNamespace(String namespace) {
        this.name = namespace; // Note: namespace should always end with :
        this.nameLowerCase = namespace.toLowerCase(Locale.ENGLISH);
        this.directories = new HashMap<>();
    }

    private ListedNamespace(ListedNamespace namespace) {
        this.name = namespace.name;
        this.nameLowerCase = namespace.nameLowerCase;
        this.directories = new HashMap<>(namespace.directories.size());
    }

    protected ListedDirectory findOrCreateDirectory(String path) {
        ListedDirectory entry = initDirectory(path);

        // Find parent directories as well
        if (entry.parent() == null) {
            ListedDirectory d = entry;
            while (true) {
                int d_path_end = d.path().lastIndexOf('/');
                if (d_path_end == -1) {
                    // namespace is the parent
                    d.setParent(this);
                    break;
                }

                // find or create this directory as well, then continue
                String parent_dir_path = d.path().substring(0, d_path_end);
                ListedDirectory dp = initDirectory(parent_dir_path);
                d.setParent(dp);
                if (dp.parent() != null) {
                    break;
                }

                // Recurse down to root
                d = dp;
            }
        }

        return entry;
    }

    private ListedDirectory initDirectory(String path) {
        return directories.computeIfAbsent(path, p -> new ListedDirectory(this, p));
    }

    /**
     * Gets the namespace name represented by this listed namespace entry
     *
     * @return namespace
     */
    @Override
    public String name() {
        return name;
    }

    @Override
    public String nameLowerCase() {
        return nameLowerCase;
    }

    @Override
    public String fullPath() {
        return name;
    }

    @Override
    public int sortPriority() {
        return 1;
    }

    @Override
    public ListedNamespace namespace() {
        return this;
    }

    @Override
    public ItemStack createIconItem(DialogBuilder options) {
        ItemStack item = options.getNamespaceIconItem().clone();
        ItemUtil.setDisplayName(item, ChatColor.YELLOW + this.name);
        ItemUtil.addLoreName(item, "");
        ItemUtil.addLoreName(item, ChatColor.DARK_GRAY + "Namespace");
        ItemUtil.addLoreName(item, ChatColor.DARK_GRAY +
                "< " + ChatColor.GRAY + this.nestedItemCount + ChatColor.DARK_GRAY + " Item models >");
        return item;
    }

    @Override
    public String toString() {
        return "Namespace: " + name;
    }

    @Override
    protected ListedNamespace cloneSelf(ListedNamespace namespace) {
        if (namespace != null) {
            throw new IllegalArgumentException("Namespace entries cannot be in a namespace");
        }

        return new ListedNamespace(this);
    }

    @Override
    protected ListedEntry findOrCreateInRoot(ListedRoot root) {
        return root.findOrCreateNamespace(this.name);
    }
}
