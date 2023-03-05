package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The configuration of a single attachment. Stores the configuration of the attachment
 * itself, as well as information about its position in the attachment hierarchy. Child
 * attachments are also made available, but should only be used when handling
 * {@link ChangeType#ADDED}
 */
public interface AttachmentConfig {
    /**
     * Gets the parent attachment of this attachment
     *
     * @return parent attachment, null if this is the root attachment
     */
    AttachmentConfig parent();

    /**
     * Gets the child attachment configurations parented to this
     * attachment. This should only be used on the returned configuration of
     * {@link AttachmentConfigTracker#startTracking(AttachmentConfigListener)
     * AttachmentConfigTracker.startTracking()}
     * or when handling {@link ChangeType#ADDED ChangeType.ADDED} events.
     *
     * @return List of child attachment configurations
     */
    List<AttachmentConfig> children();

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
    default int[] childPath() {
        ArrayList<AttachmentConfig> parents = new ArrayList<>(10);
        for (AttachmentConfig a = this; a.parent() != null; a = a.parent()) {
            parents.add(a);
        }
        int[] path = new int[parents.size()];
        for (int i = 0, j = path.length - 1; j >= 0; --j, ++i) {
            path[i] = parents.get(j).childIndex();
        }
        return path;
    }

    /**
     * Gets a root-relative path to this attachment. To get an absolute path,
     * use {@link ConfigurationNode#getYamlPath()} instead.
     *
     * @return root-relative path to this attachment's Yaml configuration
     */
    YamlPath path();

    /**
     * Gets the attachment type identifier. This might be out of sync with
     * {@link #config()} when handling {@link ChangeType#REMOVED}. In that
     * case, this will return the attachment type id that was removed, not
     * the type that might replace it.
     *
     * @return Attachment type identifier
     */
    String typeId();

    /**
     * Gets the current configuration node of this attachment.
     * Should not be used when handling attachment removal, as this
     * configuration might be out of date or contain information about
     * an entirely different attachment. This configuration is only
     * suitable for loading the configuration of this attachment,
     * not for inspecting parent or child attachments.
     *
     * @return attachment configuration
     */
    ConfigurationNode config();

    /**
     * Attachment Configuration for a Model Attachment that has a valid model name
     * defined. Model name will not change and will always be non-empty. If changes
     * happen a new Model configuration is created, and if empty, a normal
     * AttachmentConfig is created instead.
     */
    interface Model extends AttachmentConfig {

        /**
         * Gets the name of the model this model attachment uses. Invisibly, the
         * attachments of this model configuration are mixed in.
         *
         * @return model name. Is never empty.
         */
        String modelName();
    }

    /**
     * A single attachment Change notification
     */
    final class Change {
        private final ChangeType changeType;
        private final AttachmentConfig attachment;

        public Change(ChangeType changeType, AttachmentConfig attachment) {
            this.changeType = changeType;
            this.attachment = attachment;
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

        /**
         * Calls the appropriate callback in the {@link AttachmentConfigListener}
         *
         * @param listener Listener to call the right callback on
         */
        void callListener(AttachmentConfigListener listener) {
            changeType.callback.accept(listener, attachment);
        }

        @Override
        public String toString() {
            return "{" + changeType.name() + " " + attachment.path() + "}";
        }
    }

    /**
     * A type of change that occurred to an attachment
     */
    enum ChangeType {
        /** The attachment and all its children were added */
        ADDED(AttachmentConfigListener::onAttachmentAdded),
        /** The attachment and all its children were removed */
        REMOVED(AttachmentConfigListener::onAttachmentRemoved),
        /**
         * The attachment configuration changed and needs to be re-loaded.
         * The {@link AttachmentConfig} instance will not have changed
         * since previous events.
         */
        CHANGED(AttachmentConfigListener::onAttachmentChanged);

        private final BiConsumer<AttachmentConfigListener, AttachmentConfig> callback;

        ChangeType(BiConsumer<AttachmentConfigListener, AttachmentConfig> callback) {
            this.callback = callback;
        }
    }
}
