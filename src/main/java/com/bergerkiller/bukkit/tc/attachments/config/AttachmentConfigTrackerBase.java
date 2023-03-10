package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedList;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.utils.ListCallbackCollector;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base implementation of both {@link AttachmentConfigTracker} and
 * {@link AttachmentConfigModelTracker} which provides the basic
 * attachment listener capabilities.
 */
public abstract class AttachmentConfigTrackerBase {
    private final ImplicitlySharedList<RemovableListener> listeners;
    private WeakRootReference cachedRoot;
    protected final Logger logger;

    public AttachmentConfigTrackerBase(Logger logger) {
        this.logger = logger;
        this.listeners = new ImplicitlySharedList<>();
        this.cachedRoot = new WeakRootReference();
    }

    /**
     * Starts tracking the configuration. Called after the first listener is added.
     */
    protected abstract void startTracking();

    /**
     * Stops tracking changes in the configuration. Called after the last listener
     * is removed.
     */
    protected abstract void stopTracking();

    /**
     * Creates a new {@link AttachmentConfig.RootReference}. The reference should become
     * invalid once the underlying configuration changes. The value is cached automatically.
     *
     * @return New root reference
     */
    protected abstract AttachmentConfig.RootReference createRootReference();

    /**
     * Collects all configuration changes that have occurred thus far and fires callbacks
     * on all previously registered listeners. If this tracker has no listeners, does
     * nothing. This is called automatically every tick when a plugin was specified in the
     * tracker's constructor.
     */
    public abstract void sync();

    /**
     * Gets a reference to a snapshot of the current root {@link AttachmentConfig}. This
     * reference becomes invalid automatically when the attachment configuration changes.
     * The entire attachment tree hierarchy can be navigated using this root attachment,
     * but it should no longer be used once invalid.
     *
     * @return Reference to the root {@link AttachmentConfig}
     */
    public final AttachmentConfig.RootReference getRoot() {
        AttachmentConfig.RootReference root = cachedRoot.getIfValid();
        if (root == null) {
            sync();
            root = createRootReference();
            cachedRoot = new WeakRootReference(root);
        }
        return root;
    }

    /**
     * Starts tracking changes to the configuration and notifies those changes
     * to the listener specified. Returns the current up-to-date attachment
     * configuration, calling {@link #sync()} up-front if needed. This method
     * can be called more than once for adding multiple listeners.<br>
     * <br>
     * The return attachment configuration can be used to set up the initial
     * state on the recipient end. The listener will not be called during
     * this method call. This attachment configuration should not be stored
     * for multiple ticks because it can internally go inconsistent with
     * the configuration until {@link #sync()} is called.
     *
     * @param listener Listener to notify of changes
     * @return Latest up-to-date attachment configuration. It and its children
     *         can be used to populate the initial tracked state.
     * @throws IllegalStateException If the listener was already added before
     */
    public AttachmentConfig startTracking(AttachmentConfigListener listener) {
        // Wrap it
        RemovableListener removableListener = new RemovableListener(listener);

        // Start tracking if this is the first listener
        if (listeners.isEmpty()) {
            startTracking();
            listeners.add(removableListener);
            AttachmentConfig.RootReference ref = createRootReference();
            cachedRoot.close();
            cachedRoot = new WeakRootReference(ref);
            return ref.get();
        } else if (listeners.contains(removableListener)) {
            throw new IllegalStateException("Listener already added");
        } else {
            sync();
            listeners.add(removableListener);
            return getRoot().get();
        }
    }

    /**
     * Stops tracking changes that happen to this configuration, removing the
     * listener as a recipient. If no more listeners exist, this tracker
     * will disable itself until {@link #startTracking(AttachmentConfigListener)} is
     * called again. If the listener was already un-tracked, does nothing.<br>
     * <br>
     * Changes that were still pending to be notified to this listener are no
     * longer notified. The removal is immediate.
     *
     * @param listener Listener to stop notifying of changes
     */
    public void stopTracking(AttachmentConfigListener listener) {
        for (Iterator<RemovableListener> iter = listeners.iterator(); iter.hasNext();) {
            RemovableListener rl = iter.next();
            if (rl.listener.equals(listener)) {
                // Mark for removal and remove from the list
                rl.removed = true;
                iter.remove();

                // If now empty, also stop tracking
                if (listeners.isEmpty()) {
                    stopTracking();
                }

                // Better to invalidate this one right away
                cachedRoot.close();
                cachedRoot = new WeakRootReference();

                break;
            }
        }
    }

    /**
     * Gets whether this tracker is actually tracking. This is only the case when at least
     * one listener was added using {@link #startTracking(AttachmentConfigListener)}
     *
     * @return True if tracking
     */
    public boolean isTracking() {
        return !listeners.isEmpty();
    }

    /**
     * Notifies all previously registered listeners of one or more changes
     *
     * @param changes Changes to notify
     */
    protected void notifyChanges(Collection<AttachmentConfig.Change> changes) {
        // Make a snapshot copy of all listeners that exist right now. Listener callbacks
        // could be adding/removing listeners (in an indirect way), and those must be
        // caught. Removed listeners are no longer called afterwards.
        try (ImplicitlySharedList<RemovableListener> listeners = this.listeners.clone()) {
            for (RemovableListener removableListener : listeners) {
                for (AttachmentConfig.Change change : changes) {
                    // If listener was removed, don't call it anymore
                    if (removableListener.removed) {
                        break;
                    }

                    // Notify
                    try {
                        removableListener.listener.onChange(change);
                    } catch (Throwable t) {
                        logger.log(Level.SEVERE, "Failed to notify an attachment was " + change.changeType(), t);
                    }
                }
            }
        }
    }

    /**
     * Notifies a single change to all listeners
     *
     * @param changeType Type of change
     * @param attachment Attachment that changed
     */
    protected void notifyChange(AttachmentConfig.ChangeType changeType, AttachmentConfig attachment) {
        notifyChanges(Collections.singleton(new AttachmentConfig.Change(changeType, attachment)));
    }

    /**
     * Asks all registered listeners to perform an action on their live managed Attachments, if any.
     * Listeners that don't manage attachments should do nothing.
     *
     * @param attachment Attachment configuration for which attachments should run an action
     * @param action Action to run
     */
    protected void runAttachmentAction(AttachmentConfig attachment, Consumer<Attachment> action) {
        if (listeners.isEmpty()) {
            return;
        }

        try (ImplicitlySharedList<RemovableListener> listeners = this.listeners.clone()) {
            for (RemovableListener removableListener : listeners) {
                // If listener was removed, don't call it anymore
                if (removableListener.removed) {
                    continue;
                }

                // Forward the call to this listener
                try {
                    removableListener.listener.onAttachmentAction(attachment, action);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Failed to run attachment action", t);
                }
            }
        }
    }

    /**
     * Figures out all live {@link Attachment} instances that use an attachment configuration,
     * and runs an action on them. This runs sync and can only be used from the main thread.<br>
     * <br>
     * A series of parent-to-child indices can be specified that refer to the attachment
     * configuration to retrieve and run actions on. If this tracker has no listeners,
     * runs nothing.
     *
     * @param childPath Parent-to-child indices
     * @param action Action to run for all live attachments using the configuration
     * @see AttachmentConfig#runAction(Consumer)
     */
    public void runAction(int[] childPath, Consumer<Attachment> action) {
        if (!listeners.isEmpty()) {
            sync();
            if (!listeners.isEmpty()) {
                // Grab root attachment, find the attachment at this path, and run the action
                AttachmentConfig config = getRoot().get().child(childPath);
                if (config != null) {
                    config.runAction(action);
                }
            }
        }
    }

    /**
     * Figures out all live {@link Attachment} instances that use an attachment configuration,
     * and runs an action on them. This runs sync and can only be used from the main thread.<br>
     * <br>
     * A YAML path relative to the tracked configuration root is used to select the attachment
     * to run the action on.
     *
     * @param relativePath Relative YAML path of the configuration node whose attachments
     *                     to run an action on
     * @param action Action to run for all live attachments using the configuration
     * @see AttachmentConfig#runAction(Consumer)
     */
    public void runAction(YamlPath relativePath, Consumer<Attachment> action) {
        if (!listeners.isEmpty()) {
            sync();
            if (!listeners.isEmpty()) {
                // Grab root attachment, find the attachment at this path, and run the action
                AttachmentConfig config = getRoot().get().child(relativePath);
                if (config != null) {
                    config.runAction(action);
                }
            }
        }
    }

    /**
     * Finds all live {@link Attachment} instances that use the attachment configuration
     *
     * @param childPath Parent-to-child indices
     * @return Unmodifiable List of live Attachments
     * @see #runAction(int[], Consumer)
     */
    public List<Attachment> liveAttachments(int[] childPath) {
        ListCallbackCollector<Attachment> collector = new ListCallbackCollector<>();
        runAction(childPath, collector);
        return collector.result();
    }

    /**
     * Finds all live {@link Attachment} instances that use the attachment configuration
     *
     * @param relativePath Relative YAML path of the configuration node whose attachments
     *                     to return
     * @return Unmodifiable List of live Attachments
     * @see #runAction(YamlPath, Consumer)
     */
    public List<Attachment> liveAttachments(YamlPath relativePath) {
        ListCallbackCollector<Attachment> collector = new ListCallbackCollector<>();
        runAction(relativePath, collector);
        return collector.result();
    }

    private static class RemovableListener {
        public final AttachmentConfigListener listener;
        public boolean removed;

        public RemovableListener(AttachmentConfigListener listener) {
            this.listener = listener;
            this.removed = false;
        }

        @Override
        public boolean equals(Object o) {
            return this.listener.equals(((RemovableListener) o).listener);
        }
    }

    /**
     * Stores a weak RootReference, but manages its valid checker separately so that it
     * properly cleans up these checkers when swapping out the references.
     */
    private static class WeakRootReference {
        private final WeakReference<AttachmentConfig.RootReference> reference;
        private final AttachmentConfig.RootReference.ValidChecker checker;

        public WeakRootReference() {
            this.reference = LogicUtil.nullWeakReference();
            this.checker = () -> false;
        }

        public WeakRootReference(AttachmentConfig.RootReference ref) {
            this.reference = new WeakReference<>(ref);
            this.checker = ref.getValidChecker();
        }

        public AttachmentConfig.RootReference getIfValid() {
            AttachmentConfig.RootReference ref = reference.get();
            if (ref == null) {
                this.checker.close();
                return null;
            } else {
                return ref.valid() ? ref : null;
            }
        }

        public void close() {
            AttachmentConfig.RootReference ref = reference.get();
            if (ref != null) {
                ref.invalidate(); // also closes checker
            } else {
                this.checker.close();
            }
        }
    }
}
