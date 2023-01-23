package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.common.utils.StringUtil;

/**
 * Stores important functions for initializing new listed entry hierarchies
 */
public class ListedRootLoader {
    protected ListedRoot root = new ListedRoot();

    protected void loadFromListing(ListedRoot listedRoot, String query) {
        // Figure out whether a namespace: or absolute directory structure is specified
        int firstPartEnd = StringUtil.firstIndexOf(query, '/', '\\', ':');

        // A single name without / and \ and not ending in : was specified
        // Try to match (=contains) all entries recursively
        // If a directory name matches, it and all sub-entries are included in the result
        // Don't match the namespace, that's dumb.
        if (!query.isEmpty() && firstPartEnd == -1) {
            for (ListedNamespace namespace : listedRoot.namespaces()) {
                for (ListedEntry e : namespace.matchChildrenNameContains(query)) {
                    e.assignToRoot(root);
                }
            }
            return;
        }

        // Loading of a directory hierarchy all at once. For paths, namespace is optional.
        // A single directory can be specified by querying name/ or /name

        // For proper parsing to work it's important that the first : has a / appended after it
        if (query.charAt(firstPartEnd) == ':' && query.length() >= firstPartEnd) {
            query = query.substring(0, firstPartEnd+1) + "/" + query.substring(firstPartEnd+1);
        }

        // Tokenize by / and \ characters
        List<String> parts = Arrays.stream(query.split("/|\\\\"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));

        // Decide on the namespce to include. If no namespace was prefixed, do all of them.
        List<ListedNamespace> namespacesToCheck;
        if (!parts.isEmpty() && parts.get(0).endsWith(":")) {
            ListedNamespace match = listedRoot.namespacesByName.get(parts.remove(0));
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
