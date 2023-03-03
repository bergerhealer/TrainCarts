package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.yaml.YamlChangeListener;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Tracks configuration changes and translates those into attachment
 * load, remove or creation operations. Does de-bouncing so many
 * changes translate into a single bulk change.
 */
public abstract class AttachmentConfigTracker implements YamlChangeListener {
    private final YamlLogic logic; //TODO: Remove when no longer needed
    private final Map<ConfigurationNode, TrackedAttachmentConfig> byConfig;
    private final List<Change> pendingChanges;
    private TrackedAttachmentConfig root;

    public AttachmentConfigTracker(ConfigurationNode config) {
        logic = YamlLogic.create();
        byConfig = new IdentityHashMap<>();
        pendingChanges = new ArrayList<>();
        root = new TrackedAttachmentConfig(config.getYamlPath(), null, config, 0);
        root.addToTracker();
    }

    public void start() {
        root.config.addChangeListener(this);
    }

    public void stop() {
        root.config.removeChangeListener(this);
    }

    public void sync() {
        // Old BKCommonLib had a bug in it of randomly dropping change listeners
        // with methods like setTo. This ensures we stay updated on changes when
        // this is detected. We notify a full remove and re-adding to ensure any
        // changes that occurred meanwhile get synchronized.
        if (!logic.isListening(root.config, this)) {
            pendingChanges.clear();
            root.swap(new TrackedAttachmentConfig(root.config.getYamlPath(), null, root.config, 0));
            root.config.addChangeListener(this);
        } else {
            // Normal sync
            root.sync();
        }

        // Notify all the changes we've gathered. Make sure all further changes are purged
        // as well if an error occurs. Caller should reset everything when that happens.
        try {
            pendingChanges.forEach(this::onChange);
        } finally {
            pendingChanges.clear();
        }
    }

    /**
     * Gets the configuration that is being tracked by this tracker
     *
     * @return configuration
     */
    public ConfigurationNode getConfig() {
        return root.config;
    }

    @Override
    public void onNodeChanged(YamlPath yamlPath) {
        // Legacy back-support
        if (!logic.areChangesRelative()) {
            yamlPath = getRelativePath(yamlPath);
        }

        if (yamlPath.name().equals("attachments")) {
            // The list of child attachments changed for an attachment
            // This requires a re-scan of all children
            YamlPath attachmentPath = yamlPath.parent();
            TrackedAttachmentConfig attachment = findAttachment(attachmentPath);
            if (attachment != null && !attachment.childrenRefreshNeeded) {
                attachment.childrenRefreshNeeded = true;
                attachment.markChanged();
            }
        } else {
            // Update the configuration for this attachment itself
            YamlPath attachmentPath = getAttachmentPath(yamlPath);
            TrackedAttachmentConfig attachment = findAttachment(attachmentPath);
            if (attachment != null && !attachment.loadNeeded) {
                attachment.loadNeeded = true;
                attachment.markChanged();
            }
        }
    }

    /**
     * Called when a change to an attachment was detected. Changes are notified
     * in a logical sequence. Can be overrided to handle multiple different
     * {@link ChangeType Change Types} at once.
     *
     * @param change Change that occurred
     */
    public void onChange(Change change) {
        change.changeType().callback.accept(this, change.attachment());
    }

    /**
     * Called when an attachment, and all its child attachments, need to be
     * added/created. The attachment child index can be used to as the location
     * in the list it should be added.
     *
     * @param attachment Attachment that was removed
     */
    public void onAttachmentAdded(AttachmentConfig attachment) {
    }

    /**
     * Called when an attachment, and all its child attachments, need to be
     * removed/destroyed.
     *
     * @param attachment Attachment that was removed
     */
    public void onAttachmentRemoved(AttachmentConfig attachment) {
    }

    /**
     * Called when an attachment configuration changed. The attachment should
     * have its configuration reloaded. Is not called if the attachment
     * type changed, in which case a remove-and-add is done.
     *
     * @param attachment Attachment whose configuration changed
     */
    public void onAttachmentChanged(AttachmentConfig attachment) {
    }

    private void addChange(TrackedAttachmentConfig attachment, ChangeType changeType) {
        pendingChanges.add(new Change(attachment, changeType));
    }

    private YamlPath getRelativePath(YamlPath path) {
        return logic.getRelativePath(root.config.getYamlPath(), path);
    }

    /**
     * Attempts to locate an Attachment by its path. If the path was changed can
     * as fallback try to find it by its configuration. If the attachment does not
     * yet exist, returns null. It's expected to receive another notification
     * for the changed parent node so that it can refresh its children later.
     *
     * @param path Root-relative path to the attachment
     * @return existing attachment at this path, or null if it was not yet tracked
     */
    private TrackedAttachmentConfig findAttachment(YamlPath path) {
        // Note: removal of nodes is never notified with a path of the node itself
        // Rather, it is notified by a change of the parent node (children changed)
        // So if it does not exist, that's fine.
        ConfigurationNode nodeAtPath = logic.getNodeAtPathIfExists(root.config, path);
        if (nodeAtPath == null) {
            return null;
        }

        return this.byConfig.get(nodeAtPath);
    }

    /**
     * Walks backwards on a path until the root of an attachment
     * element is located.
     *
     * @param path Path in the configuration
     * @return attachment root path
     */
    private static YamlPath getAttachmentPath(YamlPath path) {
        while (!path.isRoot()) {
            YamlPath parent = path.parent();
            if (parent.name().equals("attachments")) {
                // Works for both 'attachments[1]' as well as 'attachments.1'
                break;
            } else {
                path = parent;
            }
        }
        return path;
    }

    /**
     * A single attachment configuration managed by this tracker. Attachments are
     * passed as part of change events, and should not be stored by the recipient
     * of these callbacks. Contains information about the attachment configuration
     * and its position in the attachment hierarchy, but nothing more.
     */
    public interface AttachmentConfig {

        /**
         * Gets the parent attachment of this attachment
         *
         * @return parent attachment, null if this is the root attachment
         */
        AttachmentConfig parent();

        /**
         * Gets the child index of this attachment relative to {@link #parent()}.
         * When an attachment is removed, this is the index relative to the parent
         * that should be removed. When an attachment is added, this is the index
         * where a new attachment should be inserted.
         *
         * @return parent-relative child index
         */
        int childIndex();

        /**
         * Gets a full sequence of parent-child indices that lead from the root
         * attachment to this current attachment. Changes to the root attachment
         * will return an empty array.
         *
         * @return child path
         */
        int[] childPath();

        /**
         * Gets a root-relative path to this attachment. To get an absolute path,
         * use {@link ConfigurationNode#getYamlPath()} instead.
         *
         * @return root-relative path to this attachment's Yaml configuration
         */
        YamlPath path();

        /**
         * Gets the current configuration node of this attachment.
         * Should not be used when handling attachment removal, as this
         * configuration might be out of date or contain information about
         * an entirely different attachment.
         *
         * @return attachment configuration
         */
        ConfigurationNode config();
    }

    private class TrackedAttachmentConfig implements AttachmentConfig {
        private final TrackedAttachmentConfig parent;
        private final List<TrackedAttachmentConfig> children;
        private YamlPath path;
        private final ConfigurationNode config;
        private final Object typeIdObj;
        private int childIndex;
        private boolean changed;
        private boolean loadNeeded;
        private boolean childrenRefreshNeeded;
        private boolean isAddedToTracker;

        private TrackedAttachmentConfig(YamlPath rootPath, TrackedAttachmentConfig parent, ConfigurationNode config, int childIndex) {
            this.parent = parent;
            this.children = new ArrayList<>();
            this.path = logic.getRelativePath(rootPath, config.getYamlPath());
            this.config = config;
            this.typeIdObj = config.get("type");
            this.childIndex = childIndex;
            this.changed = false;
            this.loadNeeded = false;
            this.childrenRefreshNeeded = false;
            this.isAddedToTracker = false;

            int index = -1;
            for (ConfigurationNode childNode : config.getNodeList("attachments")) {
                this.children.add(new TrackedAttachmentConfig(rootPath, this, childNode, ++index));
            }
        }

        @Override
        public AttachmentConfig parent() {
            return parent;
        }

        @Override
        public int childIndex() {
            return childIndex;
        }

        @Override
        public int[] childPath() {
            ArrayList<TrackedAttachmentConfig> parents = new ArrayList<>(10);
            for (TrackedAttachmentConfig a = this; a.parent != null; a = a.parent) {
                parents.add(a);
            }
            int[] path = new int[parents.size()];
            for (int i = 0, j = path.length - 1; j >= 0; --j, ++i) {
                path[i] = parents.get(j).childIndex;
            }
            return path;
        }

        @Override
        public YamlPath path() {
            return path;
        }

        @Override
        public ConfigurationNode config() {
            return config;
        }

        /**
         * Swaps this Attachment instance with another one, performing the required
         * book-keeping and change notifications.
         *
         * @param replacement Attachment that will replace this one.
         */
        private void swap(TrackedAttachmentConfig replacement) {
            addChange(this, ChangeType.REMOVED);
            if (parent == null) {
                byConfig.clear();
                this.markRemovedRecurse();
                root = replacement;
            } else if (parent.children.get(this.childIndex) != this) {
                throw new IllegalStateException("Self not found as child in parent");
            } else {
                this.removeFromTracker();
                parent.children.set(this.childIndex, replacement);
            }
            replacement.addToTracker();
            addChange(replacement, ChangeType.ADDED);
        }

        private void markRemovedRecurse() {
            childrenRefreshNeeded = false;
            isAddedToTracker = false;
            for (TrackedAttachmentConfig child : children) {
                child.markRemovedRecurse();
            }
        }

        private void sync() {
            // Run this at all times, because a change in path trickles down
            // into the path used for children, and so it must all be correct
            // from the bottom up.
            this.updatePath();

            if (changed) {
                changed = false;
                if (loadNeeded) {
                    loadNeeded = false;
                    handleLoad();
                }
                if (childrenRefreshNeeded) {
                    childrenRefreshNeeded = false;
                    updateChildren();
                }
                if (isAddedToTracker) {
                    for (TrackedAttachmentConfig child : children) {
                        child.sync();
                    }
                }
            }
        }

        private void updateChildren() {
            List<ConfigurationNode> currChildNodes = config.getNodeList("attachments");

            // Remove all children that are no longer parented to this attachment (=removed)
            // EfficientListContainsChecker is optimized for same-order lists
            {
                int childIndex = 0;
                EfficientListContainsChecker<ConfigurationNode> checker = new EfficientListContainsChecker<>(currChildNodes);
                for (Iterator<TrackedAttachmentConfig> iter = this.children.iterator(); iter.hasNext();) {
                    TrackedAttachmentConfig child = iter.next();
                    child.childIndex = childIndex;
                    if (checker.test(child.config)) {
                        childIndex++;
                    } else {
                        iter.remove();
                        child.removeFromTracker();
                        addChange(child, ChangeType.REMOVED);
                    }
                }
            }

            // Insert (new) attachments found in the configuration right now
            // Attachments with the wrong child index are removed and re-inserted
            {
                int childIndex = 0;
                for (ConfigurationNode childConfig : currChildNodes) {
                    if (childIndex < children.size()) {
                        // This child config exists at the same index already, nothing to do
                        TrackedAttachmentConfig attachment = children.get(childIndex);
                        if (attachment.config == childConfig) {
                            attachment.childIndex = childIndex;
                            childIndex++;
                            continue;
                        }

                        // Child is at the wrong List position. Remove from the old position.
                        for (int i = childIndex + 1; i < children.size(); i++) {
                            attachment = children.get(i);
                            if (attachment.config == childConfig) {
                                attachment.childIndex = i;
                                children.remove(i);
                                attachment.removeFromTracker();
                                addChange(attachment, ChangeType.REMOVED);
                                break;
                            }
                        }
                    }

                    // Add a new Attachment at this current child index
                    TrackedAttachmentConfig attachment = new TrackedAttachmentConfig(root.config.getYamlPath(), this, childConfig, childIndex);
                    children.add(childIndex, attachment);
                    attachment.addToTracker();
                    addChange(attachment, ChangeType.ADDED);
                    childIndex++;
                }

                // Excess children must be removed (shouldn't really happen...)
                while (childIndex < children.size()) {
                    TrackedAttachmentConfig attachment = children.remove(childIndex);
                    attachment.childIndex = childIndex;
                    attachment.removeFromTracker();
                    addChange(attachment, ChangeType.REMOVED);
                }
            }
        }

        private void updatePath() {
            // Ignore ROOT, it's always ROOT
            if (parent != null) {
                this.path = getRelativePath(this.config.getYamlPath());
            }
        }

        private void handleLoad() {
            // Recreate this attachment and all children when the attachment type changes
            if (!LogicUtil.bothNullOrEqual(this.typeIdObj, config.get("type"))) {
                this.swap(new TrackedAttachmentConfig(root.config.getYamlPath(), this.parent, this.config, this.childIndex));
                return;
            }

            // Calls onLoad on the attachment
            addChange(this, ChangeType.CHANGED);
        }

        private void addToTracker() {
            byConfig.put(config, this);
            isAddedToTracker = true;
            for (TrackedAttachmentConfig child : children) {
                child.addToTracker();
            }
        }

        private void removeFromTracker() {
            isAddedToTracker = false;
            childrenRefreshNeeded = false;
            byConfig.remove(config, this);
            for (TrackedAttachmentConfig child : children) {
                child.removeFromTracker();
            }
        }

        private void markChanged() {
            TrackedAttachmentConfig att = this;
            while (att != null && !att.changed) {
                att.changed = true;
                att = att.parent;
            }
        }
    }

    /**
     * A single attachment Change notification
     */
    public static final class Change {
        private final AttachmentConfig attachment;
        private final ChangeType changeType;

        public Change(AttachmentConfig attachment, ChangeType changeType) {
            this.attachment = attachment;
            this.changeType = changeType;
        }

        /**
         * Gets the attachment that was removed, created or changed.
         * Use {@link AttachmentConfig#childIndex()} to figure out in your own
         * representation what or where to remove/create/find the attachment.
         *
         * @return attachment
         */
        public AttachmentConfig attachment() {
            return attachment;
        }

        /**
         * Gets the type of change that occurred
         *
         * @return change type
         */
        public ChangeType changeType() {
            return changeType;
        }

        @Override
        public String toString() {
            return "{" + changeType.name() + " " + attachment.path() + "}";
        }
    }

    /**
     * A type of change that occurred to an attachment
     */
    public enum ChangeType {
        /** The attachment and all its children were added */
        ADDED(AttachmentConfigTracker::onAttachmentAdded),
        /** The attachment and all its children were removed */
        REMOVED(AttachmentConfigTracker::onAttachmentRemoved),
        /** The attachment configuration changed and needs to be re-loaded */
        CHANGED(AttachmentConfigTracker::onAttachmentChanged);

        private final BiConsumer<AttachmentConfigTracker, AttachmentConfig> callback;

        ChangeType(BiConsumer<AttachmentConfigTracker, AttachmentConfig> callback) {
            this.callback = callback;
        }
    }

    /**
     * Behaves identical to {@link List#contains(Object)} but iterates from the
     * position of the last successful test, instead of index 0. This makes it
     * slightly faster when the contains checks visit the same sequence of
     * values as the input list.
     *
     * @param <T> Value type
     */
    private static class EfficientListContainsChecker<T> implements Predicate<T> {
        private final List<T> list;
        private int currentIndex;

        private EfficientListContainsChecker(List<T> list) {
            this.list = list;
            this.currentIndex = 0;
        }

        @Override
        public boolean test(T t) {
            int size = list.size();
            if (size == 0) {
                return false;
            }

            // Try to search from the current index forwards until we loop around
            // If found, future searches will resume from the found index
            int i = this.currentIndex;
            while (true) {
                if (list.get(i) == t) {
                    this.currentIndex = (i + 1) % size;
                    return true;
                } else {
                    ++i;
                    i %= size;
                    if (i == this.currentIndex) {
                        // Looped around, element was not found
                        return false;
                    }
                }
            }
        }
    }
}