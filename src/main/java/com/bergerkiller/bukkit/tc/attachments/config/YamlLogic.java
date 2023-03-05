package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.yaml.YamlChangeListener;
import com.bergerkiller.bukkit.common.config.yaml.YamlNodeAbstract;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.tc.TrainCarts;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Some features don't yet work or exist in BKCommonLib, and must be emulated if they
 * are missing. This interface abstracts that stuff out to support the older BKCommonLib.
 * Once BKCommonLib 1.19.3-v3 or later is a hard-dep this stuff can be removed.
 */
interface YamlLogic {
    YamlLogic INSTANCE = create();

    /**
     * Checks whether this tracker is still actually listening for changes to the
     * configuration. If not, returns false.
     *
     * @param config ConfigurationNode to check listeners of
     * @param tracker Tracker that is expected to be listening
     * @return true if still listening (or presumed to be)
     */
    boolean isListening(ConfigurationNode config, AttachmentConfigTracker tracker);

    /**
     * Obtains the configuration node at a relative path in another configuration node,
     * if it exists.
     *
     * @param node Node relative to which to search
     * @param path Relative YamlPath
     * @return node at this path, or null if missing
     */
    ConfigurationNode getNodeAtPathIfExists(ConfigurationNode node, YamlPath path);

    /**
     * Makes an absolute path relative to a root configuration. Does this always.
     *
     * @param rootPath Root yaml path relative to which changes are
     * @param path Input absolute path
     * @return relative path
     */
    YamlPath getRelativePath(YamlPath rootPath, YamlPath path);

    /**
     * Joins two paths together
     *
     * @param firstPath
     * @param secondPath
     * @return joined path
     */
    YamlPath join(YamlPath firstPath, YamlPath secondPath);

    /**
     * Gets whether change notifications with this version of BKCOmmonLib
     * are relative.
     *
     * @return True if relative, false if absolute and
     *         {@link #getRelativePath(YamlPath, YamlPath)} must be
     *         used to correct it.
     */
    boolean areChangesRelative();

    /**
     * Creates the most appropriate YamlLogic that can be used
     *
     * @return yaml logic
     */
    static YamlLogic create() {
        if (Common.hasCapability("Common:Yaml:BetterChangeListeners")) {
            return new YamlLogicLatest();
        } else {
            return new YamlLogicLegacy();
        }
    }

    class YamlLogicLegacy implements YamlLogic {
        private static class IsListeningLogic {
            private static IsListeningLogic INSTANCE = create();
            public final java.lang.reflect.Field entryField;
            public final java.lang.reflect.Field listenersField;
            public final Class<?> relativeListenerType;
            public final java.lang.reflect.Field relativeListenerField;

            public IsListeningLogic() throws Throwable {
                entryField = YamlNodeAbstract.class.getDeclaredField("_entry");
                entryField.setAccessible(true);
                listenersField = entryField.getType().getDeclaredField("listeners");
                listenersField.setAccessible(true);

                // Optional class in BKCL newer version. Is primarily for unit test.
                Class<?> relType;
                java.lang.reflect.Field relField;
                try {
                    relType = Class.forName("com.bergerkiller.bukkit.common.config.yaml.YamlChangeListenerRelative");
                    relField = relType.getDeclaredField("listener");
                    relField.setAccessible(true);
                } catch (Throwable t) {
                    relType = null;
                    relField = null;
                }
                relativeListenerType = relType;
                relativeListenerField = relField;
            }

            public boolean isListening(ConfigurationNode config, AttachmentConfigTracker tracker) {
                try {
                    Object entry = entryField.get(config);
                    YamlChangeListener[] listeners = (YamlChangeListener[]) listenersField.get(entry);
                    for (YamlChangeListener listener : listeners) {
                        if (relativeListenerType == listener.getClass()) {
                            listener = (YamlChangeListener) relativeListenerField.get(listener);
                        }
                        if (listener == tracker) {
                            return true;
                        }
                    }
                    return false;
                } catch (Throwable t) {
                    logListeningError(t);
                    INSTANCE = null;
                    return true; // Assume true I guess
                }
            }

            public static IsListeningLogic create() {
                try {
                    return new IsListeningLogic();
                } catch (Throwable t) {
                    logListeningError(t);
                    return null;
                }
            }

            private static void logListeningError(Throwable t) {
                if (TrainCarts.plugin != null) {
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to detect isListening() - update bkcL!", t);
                } else {
                    Logger.getGlobal().log(Level.SEVERE, "Failed to detect isListening() - update bkcL!", t);
                }
            }
        }

        @Override
        public boolean isListening(ConfigurationNode config, AttachmentConfigTracker tracker) {
            IsListeningLogic logic = IsListeningLogic.INSTANCE;
            return logic == null || logic.isListening(config, tracker);
        }

        @Override
        public ConfigurationNode getNodeAtPathIfExists(ConfigurationNode node, YamlPath path) {
            String pathStr = path.toString();
            if (node.isNode(pathStr)) {
                return node.getNode(pathStr);
            } else {
                return null;
            }
        }

        // Ported from BKCL I guess. Meh.
        private static YamlPath makeRelative(YamlPath path, YamlPath absolutePath) {
            // Number of elements this path is deeper than the absolute path
            int depthDiff = path.depth() - absolutePath.depth();
            if (depthDiff < 0) {
                return null; // Impossible
            } else if (depthDiff == 0) {
                return path.equals(absolutePath) ? YamlPath.ROOT : null;
            }

            // Find the common parent which must be equal to the absolute path
            // Collect all nodes in-between
            YamlPath[] relativeParts = new YamlPath[depthDiff];
            YamlPath commonParent = path;
            do {
                relativeParts[--depthDiff] = commonParent;
                commonParent = commonParent.parent();
            } while (depthDiff > 0);
            if (!commonParent.equals(absolutePath)) {
                return null;
            }

            // Okay! Create a new path with just the elements beyond commonParent
            YamlPath result = YamlPath.ROOT;
            for (YamlPath relativePart : relativeParts) {
                result = result.childWithName(relativePart);
            }
            return result;
        }

        @Override
        public YamlPath getRelativePath(YamlPath rootPath, YamlPath path) {
            YamlPath relative = makeRelative(path, rootPath);
            if (relative == null) {
                throw new IllegalStateException("Path is not part of root: " + path);
            }
            return relative;
        }

        @Override
        public boolean areChangesRelative() {
            return false;
        }

        @Override
        public YamlPath join(YamlPath firstPath, YamlPath secondPath) {
            if (firstPath.isRoot()) {
                return secondPath;
            } else if (secondPath.isRoot()) {
                return firstPath;
            }

            // Convert second path into path parts
            // This avoids having to do nasty slow recursion to reverse-iterate
            int depth = secondPath.depth();
            YamlPath[] parts = new YamlPath[depth];
            {
                YamlPath p = secondPath;
                while (--depth >= 0) {
                    parts[depth] = p;
                    p = p.parent();
                }
            }

            // Make a new path
            YamlPath result = firstPath;
            for (YamlPath p : parts) {
                result = result.childWithName(p);
            }
            return result;
        }
    }

    class YamlLogicLatest implements YamlLogic {
        @Override
        public boolean isListening(ConfigurationNode config, AttachmentConfigTracker tracker) {
            // No-op. No bugs here, it'll always be listening
            return true;
        }

        @Override
        public ConfigurationNode getNodeAtPathIfExists(ConfigurationNode node, YamlPath path) {
            if (node.isNode(path)) {
                return node.getNode(path);
            } else {
                return null;
            }
        }

        @Override
        public YamlPath getRelativePath(YamlPath rootPath, YamlPath path) {
            YamlPath relative = path.makeRelative(rootPath);
            if (relative == null) {
                throw new IllegalStateException("Path is not part of root: " + path);
            }
            return relative;
        }

        @Override
        public boolean areChangesRelative() {
            return true;
        }

        @Override
        public YamlPath join(YamlPath firstPath, YamlPath secondPath) {
            return YamlPath.join(firstPath, secondPath);
        }
    }
}
