package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.RunOnceTask;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.yaml.YamlChangeListener;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Tracks configuration changes and translates those into attachment
 * load, remove or creation listener callbacks. Does de-bouncing so many
 * changes translate into a single bulk change.
 */
public class AttachmentConfigTracker extends AttachmentConfigTrackerBase implements YamlChangeListener {
    private final ConfigurationNode completeConfig;
    private final SyncTask syncTask;
    private final Map<ConfigurationNode, TrackedAttachmentConfig> byConfig;
    private final List<AttachmentConfig.Change> pendingChanges;
    private TrackedAttachmentConfig root;

    /**
     * Initializes a new tracker that relies on an external trigger calling
     * {@link #sync()}. Primarily used for under test.
     *
     * @param completeConfig Complete Attachment configuration to track
     */
    public AttachmentConfigTracker(ConfigurationNode completeConfig) {
        this(completeConfig, null);
    }

    /**
     * Initializes a new tracker that can automatically execute {@link #sync()}
     * every tick when changes to the configuration occur.
     *
     * @param completeConfig Complete Attachment configuration to track
     * @param plugin Plugin to use to schedule an update task automatically
     *               calling the sync function. If null, doesn't do that.
     */
    public AttachmentConfigTracker(ConfigurationNode completeConfig, Plugin plugin) {
        super((plugin == null) ? Logger.getGlobal() : plugin.getLogger());
        this.completeConfig = completeConfig;
        this.syncTask = (plugin == null) ? null : new SyncTask(plugin);
        this.byConfig = new IdentityHashMap<>();
        this.pendingChanges = new ArrayList<>();
        this.root = null; // Not tracked until a listener is added
    }

    @Override
    protected void startTracking() {
        root = createNewConfig(null, completeConfig, 0);
        root.addToTracker();
        completeConfig.addChangeListener(this);
        pendingChanges.clear();
    }

    @Override
    protected void stopTracking() {
        completeConfig.removeChangeListener(this);
        pendingChanges.clear();
        root = null;
        if (syncTask != null) {
            syncTask.cancel();
        }
    }

    @Override
    protected AttachmentConfig getRoot() {
        return root;
    }

    /**
     * Gets the configuration that is being tracked by this tracker
     *
     * @return configuration
     */
    public ConfigurationNode getConfig() {
        return completeConfig;
    }

    @Override
    public void sync() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        handleSync();
    }

    private void handleSync() {
        // Don't do anything when there's no listeners, or when we're already handling sync()
        if (!isTracking() || !pendingChanges.isEmpty()) {
            return;
        }

        try {
            // Old BKCommonLib had a bug in it of randomly dropping change listeners
            // with methods like setTo. This ensures we stay updated on changes when
            // this is detected. We notify a full remove and re-adding to ensure any
            // changes that occurred meanwhile get synchronized.
            if (!YamlLogic.INSTANCE.isListening(completeConfig, this)) {
                pendingChanges.clear();
                root.swap(createNewConfig(null, completeConfig, 0));
                completeConfig.addChangeListener(this);
            } else {
                // Normal sync
                root.sync();
            }

            // Notify all the changes we've gathered to all registered listeners
            notifyChanges(pendingChanges);
        } finally {
            pendingChanges.clear();
        }
    }

    @Override
    public void onNodeChanged(YamlPath yamlPath) {
        // Legacy back-support
        if (!YamlLogic.INSTANCE.areChangesRelative()) {
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

        // Schedule the task to start handling sync(). Only schedules once!
        // Make sure this doesn't happen while the plugin is disabled, which
        // might happen if configuration leaks beyond disabling.
        if (syncTask != null && syncTask.getPlugin().isEnabled()) {
            syncTask.start();
        }
    }

    private void addChange(AttachmentConfig.ChangeType changeType, TrackedAttachmentConfig attachment) {
        pendingChanges.add(new AttachmentConfig.Change(changeType, attachment));
    }

    private YamlPath getRelativePath(YamlPath path) {
        return YamlLogic.INSTANCE.getRelativePath(completeConfig.getYamlPath(), path);
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
        ConfigurationNode nodeAtPath = YamlLogic.INSTANCE.getNodeAtPathIfExists(completeConfig, path);
        return (nodeAtPath == null) ? null : this.byConfig.get(nodeAtPath);
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
                // Works for both 'attachments[1]' and 'attachments.1'
                break;
            } else {
                path = parent;
            }
        }
        return path;
    }

    private static String readAttachmentTypeId(ConfigurationNode config) {
        Object typeIdObj = config.get("type");
        return (typeIdObj == null) ? "EMPTY" : typeIdObj.toString();
    }

    private static String readModelName(ConfigurationNode config) {
        Object modelNameObj = config.get("modelName");
        return modelNameObj == null ? "" : modelNameObj.toString();
    }

    private TrackedAttachmentConfig createNewConfig(TrackedAttachmentConfig parent, ConfigurationNode config, int childIndex) {
        String typeId = readAttachmentTypeId(config);

        // Create a TrackedModelAttachmentConfig for MODEL attachments with a valid non-empty model name set
        if (typeId.equals(AttachmentType.MODEL_TYPE_ID)) {
            String modelName = readModelName(config);
            if (!modelName.isEmpty()) {
                return new TrackedModelAttachmentConfig(parent, config, typeId, modelName, childIndex);
            }
        }

        // Create a default TrackedAttachmentConfig otherwise
        return new TrackedAttachmentConfig(parent, config, typeId, childIndex);
    }

    private class TrackedAttachmentConfig implements AttachmentConfig {
        private final TrackedAttachmentConfig parent;
        private final List<TrackedAttachmentConfig> children;
        private YamlPath path;
        private final ConfigurationNode config;
        private final String typeId;
        private int childIndex;
        private boolean changed;
        private boolean loadNeeded;
        private boolean childrenRefreshNeeded;
        private boolean isAddedToTracker;

        private TrackedAttachmentConfig(TrackedAttachmentConfig parent, ConfigurationNode config, String typeId, int childIndex) {
            this.parent = parent;
            this.children = new ArrayList<>();
            this.path = getRelativePath(config.getYamlPath());
            this.config = config;
            this.typeId = typeId;
            this.childIndex = childIndex;
            this.changed = false;
            this.loadNeeded = false;
            this.childrenRefreshNeeded = false;
            this.isAddedToTracker = false;

            int index = -1;
            for (ConfigurationNode childNode : config.getNodeList("attachments")) {
                this.children.add(createNewConfig(this, childNode, ++index));
            }
        }

        @Override
        public AttachmentConfig parent() {
            return parent;
        }

        @Override
        public List<AttachmentConfig> children() {
            return Collections.unmodifiableList(children);
        }

        @Override
        public int childIndex() {
            return childIndex;
        }

        @Override
        public YamlPath path() {
            return path;
        }

        @Override
        public String typeId() {
            return typeId;
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
            addChange(ChangeType.REMOVED, this);
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
            addChange(ChangeType.ADDED, replacement);
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
                    if (handleLoad()) {
                        addChange(ChangeType.CHANGED, this);
                    } else {
                        this.swap(createNewConfig(this.parent, this.config, this.childIndex));
                    }
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
            List<ConfigurationNode> currChildNodes = this.config.getNodeList("attachments");

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
                        addChange(ChangeType.REMOVED, child);
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
                                addChange(ChangeType.REMOVED, attachment);
                                break;
                            }
                        }
                    }

                    // Add a new Attachment at this current child index
                    TrackedAttachmentConfig attachment = createNewConfig(this, childConfig, childIndex);
                    children.add(childIndex, attachment);
                    attachment.addToTracker();
                    addChange(ChangeType.ADDED, attachment);
                    childIndex++;
                }

                // Excess children must be removed (shouldn't really happen...)
                while (childIndex < children.size()) {
                    TrackedAttachmentConfig attachment = children.remove(childIndex);
                    attachment.childIndex = childIndex;
                    attachment.removeFromTracker();
                    addChange(ChangeType.REMOVED, attachment);
                }
            }
        }

        private void updatePath() {
            // Ignore ROOT, it's always ROOT
            if (parent != null) {
                this.path = getRelativePath(this.config.getYamlPath());
            }
        }

        /**
         * Handles when the attachment configuration was changed in some way.
         * Can decide to swap out this attachment for something else, in which
         * case false should be returned. If true is returned, then a
         * CHANGED notification is done keeping this attachment instance.
         *
         * @return True when to notify a CHANGED notification
         */
        protected boolean handleLoad() {
            // Recreate this attachment and all children when the attachment type changes
            if (!this.typeId.equals(readAttachmentTypeId(config))) {
                return false;
            }

            return true;
        }

        private void addToTracker() {
            byConfig.put(this.config, this);
            isAddedToTracker = true;
            for (TrackedAttachmentConfig child : children) {
                child.addToTracker();
            }
        }

        private void removeFromTracker() {
            isAddedToTracker = false;
            childrenRefreshNeeded = false;
            byConfig.remove(this.config, this);
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
     * TrackedAttachmentConfig for models, which tracks the model name stored in the
     * configuration. When the model name is changed, it re-creates the entire configuration.
     * If the model name is empty, creates a normal TrackedAttachmentConfig instead.
     * This feature is used by {@link AttachmentConfigModelTracker} to automatically
     * unpack models by name.
     */
    private class TrackedModelAttachmentConfig extends TrackedAttachmentConfig implements AttachmentConfig.Model {
        private final String modelName;

        private TrackedModelAttachmentConfig(TrackedAttachmentConfig parent, ConfigurationNode config, String typeId, String modelName, int childIndex) {
            super(parent, config, typeId, childIndex);
            this.modelName = modelName;
        }

        @Override
        protected boolean handleLoad() {
            if (!super.handleLoad()) {
                return false;
            }

            // If model name changes, recreate this attachment and its (extra) children
            if (!this.modelName.equals(readModelName(config()))) {
                return false;
            }

            return true;
        }

        @Override
        public String modelName() {
            return modelName;
        }
    }

    private class SyncTask extends RunOnceTask {

        public SyncTask(Plugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            handleSync();
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