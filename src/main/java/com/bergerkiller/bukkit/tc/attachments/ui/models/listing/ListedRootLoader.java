package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.bergerkiller.bukkit.common.utils.StringUtil;

/**
 * Stores important functions for initializing new listed entry hierarchies
 */
public class ListedRootLoader {
    protected ListedRoot root = new ListedRoot();

    protected void loadFromListing(ListedRoot listedRoot, String query) {
        // Figure out whether a namespace: or absolute directory structure is specified
        // If this doesn't match anything, it's a name search
        boolean isNameSearch = (StringUtil.firstIndexOf(query, '/', '\\', ':') == -1);

        // A single name without / and \ and not ending in : was specified
        // Try to match (=contains) all entries recursively
        // If a directory name matches, it and all sub-entries are included in the result
        // Don't match the namespace, that's dumb.
        if (!query.isEmpty() && isNameSearch) {
            for (ListedNamespace namespace : listedRoot.namespaces()) {
                for (ListedEntry e : namespace.matchChildrenNameContains(query)) {
                    e.assignToRoot(root);
                }
            }
            return;
        }

        // Loading of a directory hierarchy all at once. For paths, namespace is optional.
        // A single directory can be specified by querying name/ or /name

        List<String> parts = ListedEntry.tokenizePath(query);

        // Decide on the namespace to include. If no namespace was prefixed, do all of them.
        List<ListedNamespace> namespacesToCheck;
        if (!parts.isEmpty() && parts.get(0).endsWith(":")) {
            String namespace = parts.remove(0);
            ListedNamespace match = listedRoot.namespacesByName.get(namespace);
            if (match == null) {
                String namespaceLower = namespace.toLowerCase(Locale.ENGLISH);
                for (ListedNamespace n : listedRoot.namespaces()) {
                    if (n.nameLowerCase().equals(namespaceLower)) {
                        match = n;
                        break;
                    }
                }
            }
            if (match == null) {
                return;
            } else {
                namespacesToCheck = Collections.singletonList(match);
            }
        } else {
            namespacesToCheck = listedRoot.namespaces();
        }

        // Go by all matched namespaces and search the path query
        // Copy all matched elements into this listing's own root
        for (ListedNamespace namespace : namespacesToCheck) {
            for (ListedEntry e : namespace.matchWithPathPrefix(parts)) {
                e.assignToRoot(root);
            }
        }
    }
}
