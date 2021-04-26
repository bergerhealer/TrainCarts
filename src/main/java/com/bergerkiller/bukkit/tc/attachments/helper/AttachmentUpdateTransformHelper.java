package com.bergerkiller.bukkit.tc.attachments.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

/**
 * Performs the per-tick update operations on trees of attachments.
 */
public abstract class AttachmentUpdateTransformHelper {
    protected final QueuedActiveChangeHandler activeChangeHandler;

    /**
     * Creates a single-threaded helper that processes all attachments on
     * the calling thread.
     *
     * @return single-threaded simple helper
     */
    public static AttachmentUpdateTransformHelper createSimple() {
        return new AttachmentUpdateHelperSingleThreaded();
    }

    /**
     * Creates a new AttachmentUpdateHelper
     *
     * @param parallelism The number of threads to process on.
     *                    To process on the main thread only,
     *                    specify 1. To automatically detect the
     *                    suitable parallelism based on the number
     *                    of CPU cores, use 0 or -1.
     */
    public static AttachmentUpdateTransformHelper create(int parallelism) {
        if (parallelism <= 0) {
            parallelism = Runtime.getRuntime().availableProcessors();
        }
        if (parallelism > 1) {
            return new AttachmentUpdateHelperMultiThreaded(parallelism);
        } else {
            return new AttachmentUpdateHelperSingleThreaded();
        }
    }

    protected AttachmentUpdateTransformHelper() {
        activeChangeHandler = new QueuedActiveChangeHandler();
    }

    /**
     * Same as {@link #start(Attachment, Supplier)} but calls {@link #finish()} right after
     *
     * @param attachment
     * @param initialTransform
     */
    public final void startAndFinish(Attachment attachment, Matrix4x4 initialTransform) {
        try {
            start(attachment, initialTransform);
        } finally {
            finish();
        }
    }

    /**
     * Schedules an attachment for updating the transformation
     *
     * @param attachment The attachment to update the transform of
     * @param initialTransform Initial transformation matrix relative
     *                         to which the attachment is placed.
     */
    public abstract void start(Attachment attachment, Matrix4x4 initialTransform);

    /**
     * Finishes processing all the tasks previously started using
     * {@link #start(Attachment, Supplier)}.
     */
    public abstract void finish();

    private static final class AttachmentUpdateHelperSingleThreaded extends AttachmentUpdateTransformHelper {
        private final ArrayList<Attachment> pendingUpdates = new ArrayList<>();

        @Override
        public void start(Attachment attachment, Matrix4x4 initialTransform) {
            // Refresh root attachment using the specified transform
            attachment.getInternalState().updateTransform(
                    attachment,
                    initialTransform,
                    activeChangeHandler);

            // Add all children
            pendingUpdates.addAll(attachment.getChildren());
        }

        @Override
        public void finish() {
            try {
                // Update positions of all attachments in the list since previous index
                // Add their children to the list and then process all of those
                // Do this until the entire list is exhausted
                int startIndex = 0, endIndex;
                while (startIndex < (endIndex = pendingUpdates.size())) {
                    for (int index = startIndex; index < endIndex; index++) {
                        Attachment attachment = pendingUpdates.get(index);
                        attachment.getInternalState().updateTransform(
                                attachment,
                                attachment.getParent().getTransform(),
                                activeChangeHandler);

                        pendingUpdates.addAll(attachment.getChildren());
                    }
                    startIndex = endIndex;
                }
            } finally {
                pendingUpdates.clear();
                activeChangeHandler.sync();
            }
        }
    }

    private static final class AttachmentUpdateHelperMultiThreaded extends AttachmentUpdateTransformHelper {
        private final List<ForkJoinTask<Void>> pendingTasks;
        private final ForkJoinPool pool;

        public AttachmentUpdateHelperMultiThreaded(int parallelism) {
            pendingTasks = new ArrayList<ForkJoinTask<Void>>();
            pool = new ForkJoinPool(parallelism, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, false);
        }

        @Override
        public void start(Attachment attachment, Matrix4x4 initialTransform) {
            // Create or retrieve the task used for updating
            ForkJoinTask<Void> task = attachment.getInternalState().updateTransformRecurseAsync(
                    attachment,
                    initialTransform,
                    activeChangeHandler);

            // Schedule it
            pendingTasks.add(task);
            pool.execute(task);
        }

        @Override
        public void finish() {
            try {
                // Wait for all tasks to finish. Do in reverse order for better performance.
                for (int i = pendingTasks.size() - 1; i >= 0; i--) {
                    pendingTasks.get(i).join();
                }
            } finally {
                pendingTasks.clear();
                activeChangeHandler.sync();
            }
        }
    }
}
