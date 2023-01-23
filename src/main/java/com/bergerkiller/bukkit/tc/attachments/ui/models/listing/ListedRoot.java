package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

/**
 * Root entry. Should not be displayed.
 */
public final class ListedRoot extends ListedEntry {
    final Map<String, ListedNamespace> namespacesByName;
    final List<ListedItemModel> allListedItems;

    public ListedRoot() {
        this.namespacesByName = new HashMap<>();
        this.allListedItems = new ArrayList<>();
    }

    private ListedRoot(ListedRoot root) {
        this.namespacesByName = new HashMap<>(root.namespacesByName.size());
        this.allListedItems = new ArrayList<>(root.allListedItems.size());
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public String nameLowerCase() {
        return "";
    }

    @Override
    public String fullPath() {
        return "";
    }

    @Override
    public int sortPriority() {
        return 0;
    }

    @Override
    public ItemStack createIconItem(DialogBuilder options) {
        return null; // Unused
    }

    @Override
    public ListedNamespace namespace() {
        return null;
    }

    /**
     * Gets a list of root namespaces in which custom models exist
     *
     * @return namespaces
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<ListedNamespace> namespaces() {
        return (List) this.children();
    }

    /**
     * Gets a flattened list of all item models contained below this root
     *
     * @return List of all item models
     */
    public List<ListedItemModel> itemModels() {
        return allListedItems;
    }

    @Override
    public String toString() {
        return "<ROOT>";
    }

    /**
     * Adds a new item at the path specified to this listed root. Any namespaces
     * and sub-directories are automatically created.
     *
     * @param path Path to the item model
     * @param item Item that will display the item model
     * @return Created Listed item model
     */
    public ListedItemModel addListedItem(String path, ItemStack item) {
        // Decode the path namespace and directory structure
        ListedNamespace namespace;
        ListedEntry containingEntry;
        String name;
        String pathWithoutNamespace;
        String fullPath;
        {
            int namespaceStart = path.indexOf(':');
            String namespaceName;
            if (namespaceStart == -1) {
                namespaceName = "minecraft:";
                pathWithoutNamespace = path;
                fullPath = namespaceName + path;
            } else {
                namespaceName = path.substring(0, namespaceStart + 1); // Includes :
                pathWithoutNamespace = path.substring(namespaceStart + 1);
                fullPath = path;
            }
            namespace = findOrCreateNamespace(namespaceName);

            int directoryPathEnd = pathWithoutNamespace.lastIndexOf('/');
            if (directoryPathEnd == -1) {
                containingEntry = namespace;
                name = pathWithoutNamespace;
            } else {
                String directoryPath = pathWithoutNamespace.substring(0, directoryPathEnd);
                containingEntry = namespace.findOrCreateDirectory(directoryPath);
                name = pathWithoutNamespace.substring(directoryPathEnd + 1);
            }
        }

        // Create listed item model, then register it in here
        ListedItemModel entry = new ListedItemModel(fullPath, pathWithoutNamespace, name, item);
        entry.setParent(containingEntry);
        allListedItems.add(entry);
        return entry;
    }

    @Override
    protected ListedRoot cloneSelf(ListedNamespace namespace) {
        if (namespace != null) {
            throw new IllegalArgumentException("Root entries cannot be in a namespace");
        }

        return new ListedRoot(this);
    }

    @Override
    protected ListedEntry findOrCreateInRoot(ListedRoot root) {
        return root; // Probably bad
    }

    protected ListedNamespace findOrCreateNamespace(String namespace) {
        ListedNamespace entry = namespacesByName.computeIfAbsent(namespace, ListedNamespace::new);
        if (entry.parent() == null) {
            entry.setParent(this);
        }
        return entry;
    }
}