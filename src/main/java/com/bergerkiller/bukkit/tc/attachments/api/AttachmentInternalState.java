package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinTask;
import java.util.logging.Level;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;
import com.bergerkiller.bukkit.tc.attachments.helper.ActiveChangeHandler;

/**
 * All state information for an attachment that is available at all times,
 * no matter the implementation. Implementations of {@link Attachment}
 * should return the same unique mutable instance of AttachmentState
 * inside {@link Attachment#getState()}.<br>
 * <br>
 * The fields inside this class are for internal use only and should not
 * be used by implementations of the controller. Please use the appropriate
 * methods in the controller itself instead for future compatibility.
 */
public class AttachmentInternalState {
    /**
     * Manages that created the attachment and oversees it
     */
    protected AttachmentManager manager = null;

    /**
     * Parent of the attachment
     */
    protected Attachment parent = null;

    /**
     * Children of the attachment
     */
    protected List<Attachment> children = new ArrayList<Attachment>(1);

    /**
     * Mutable live configuration of an attachment. Is automatically populated
     * before onAttached() is called, returned by getConfig().
     */
    protected ConfigurationNode config = new ConfigurationNode();

    /**
     * Animations stored in the attachment, can be looked up by name
     */
    public Map<String, Animation> animations = new HashMap<String, Animation>();

    /**
     * Animation currently playing
     */
    public Animation currentAnimation = null;

    /**
     * Next animation in the queue to play once the current animation finishes
     */
    public List<Animation> nextAnimationQueue = Collections.emptyList();

    /**
     * Last animation state node applied to the attachment
     */
    public AnimationNode lastAnimationState = null;

    /**
     * Whether the attachment is active at this time.
     * Retrieved using {@link Attachment#isActive()}.
     * Updated using {@link Attachment#setActive(boolean)}.
     */
    protected boolean active = true;

    /**
     * Whether the attachment is focused at this time.
     * Retrieved using {@link Attachment#isFocused()}.
     * Updated using {@link Attachment#setFocused(boolean)}.
     */
    protected boolean focused = false;

    /**
     * Whether the attachment has been attached, either as a root,
     * or to parent attachment. Retrieved using {@link Attachment#isAttached()}.
     * Updated automatically when attaching and detaching.
     */
    public boolean attached = false;

    /**
     * Position information for the attachment. This is automatically
     * loaded before {@link Attachment#onAttached()} is called.
     */
    public ObjectPosition position = new ObjectPosition();

    /**
     * This task can be used to efficiently update the transform of this
     * attachment, and subsequently, of all child attachments.
     * Is automatically initialized when an update is first done
     * using a ForkJoinPool.
     */
    private UpdateTask transformUpdateTask;

    /**
     * Previous transform
     */
    public Matrix4x4 last_transform = null;

    /**
     * Current transform
     */
    public Matrix4x4 curr_transform = null;

    /**
     * Loads configuration that applies to all attachments into this internal state.
     * 
     * @param managerType The type of attachment manager that hosts the attachment
     * @param attachmentType The type of attachment being loaded
     * @param config Configuration to load
     */
    public void onLoad(Class<? extends AttachmentManager> managerType, AttachmentType attachmentType, ConfigurationNode config) {
        // Reset prior
        this.reset();

        // Store it
        this.config = config;

        // Position
        this.position.load(managerType, attachmentType, config.getNode("position"));

        // Animation list
        if (config.isNode("animations")) {
            ConfigurationNode animations = config.getNode("animations");
            for (ConfigurationNode animationConfig : animations.getNodes()) {
                Animation anim = Animation.loadFromConfig(animationConfig);
                if (anim != null) {
                    this.animations.put(anim.getOptions().getName(), anim);
                }
            }
        }
    }

    /**
     * Resets the state to the defaults
     */
    public void reset() {
        this.animations.clear();
        this.last_transform = null;
        this.curr_transform = null;
        this.transformUpdateTask = null;
    }

    /**
     * Updates the transformation matrix of the attachment, updating
     * {@link #curr_transform} and assigning the previous transform
     * to {@link #last_transform}.
     *
     * @param attachment The attachment this internal state is of
     * @param initialTransform Initial transformation the attachment
     *                         is positioned relative to.
     * @param activeChangeHandler Handler for changing the active
     *                            state change of an attachment.
     *                            The handler should sync the active
     *                            state change after all updates are
     *                            done.
     */
    public void updateTransform(
            final Attachment attachment,
            final Matrix4x4 initialTransform,
            final ActiveChangeHandler activeChangeHandler
    ) {
        // Update last transform if one is available
        boolean hasLastTransform = (last_transform != null);
        if (curr_transform != null) {
            if (hasLastTransform) {
                last_transform.set(curr_transform);
            } else {
                last_transform = curr_transform.clone();
            }
            hasLastTransform = true;
        }

        // Assign new transform to start from
        if (curr_transform == null) {
            curr_transform = initialTransform.clone();
        } else {
            curr_transform.set(initialTransform);
        }

        if (position.anchor.appliedLate()) {
            // First apply the local transformation, then transform using anchor
            curr_transform.multiply(position.transform);
            position.anchor.apply(attachment, curr_transform);
        } else {
            // First transform using anchor, then apply the local transformation
            position.anchor.apply(attachment, curr_transform);
            curr_transform.multiply(position.transform);
        }

        // Go to next animation when end of animation is reached (or no animation is playing)
        if (!nextAnimationQueue.isEmpty() && (currentAnimation == null || currentAnimation.hasReachedEnd())) {
            currentAnimation = nextAnimationQueue.remove(0);
            currentAnimation.start();
        }

        // If animation was reset (disabled), erase any previous animation state we had
        // If one is running, then refresh the last animation state to apply to this transform
        // If this method returns null, then we keep whatever state we were in prior
        if (currentAnimation == null) {
            lastAnimationState = null;
        } else if (!currentAnimation.hasReachedEnd()) {
            // TODO: Do we need dt here?
            double dt = ((CartAttachment) attachment).getController().getAnimationDeltaTime();
            AnimationNode animNode = currentAnimation.update(dt);
            if (animNode != null) {
                lastAnimationState = animNode;
            }
        }

        // Apply animation state from current or previous animation to the transformation
        if (lastAnimationState != null) {
            // Refresh active changes
            boolean active = lastAnimationState.isActive();
            lastAnimationState.apply(curr_transform);
            if (active != attachment.isActive()) {
                activeChangeHandler.scheduleActiveChange(attachment, active);
            }
        }

        // In case onTransformChanged requires this
        if (!hasLastTransform) {
            last_transform = curr_transform.clone();
        }

        // Update positions
        try {
            attachment.onTransformChanged(curr_transform);
        } catch (Throwable t) {
            TrainCarts.plugin.getLogger().log(Level.SEVERE,
                    "Failed to execute onTransformChanged() on attachment " + attachment.getClass().getName(),
                    t);
        }

        // Refresh
        if (!hasLastTransform) {
            last_transform = curr_transform.clone();
        }
    }

    /**
     * Retrieves a {@link ForkJoinTask} which can be scheduled onto a
     * {@link ForkJoinPool} to recursively update the positions of this
     * attachment, and all child attachments.<br>
     * <br>
     * The returned task is cached, so changing the <i>initialTransformSupplier</i>
     * or <i>activeChangeHandler</i> is not possible unless a {@link #reset()}
     * is done first.
     *
     * @param attachment The attachment this internal state is of
     * @param initialTransform Initial transformation matrix for this
     *                         attachment.
     * @param activeChangeHandler Handler for changing the active
     *                            state change of an attachment.
     *                            The handler should sync the active
     *                            state change after all updates are
     *                            done.
     */
    public ForkJoinTask<Void> updateTransformRecurseAsync(
            final Attachment attachment,
            final Matrix4x4 initialTransform,
            final ActiveChangeHandler activeChangeHandler
    ) {
        if (this.transformUpdateTask instanceof UpdateRootAttachmentTask) {
            ((UpdateRootAttachmentTask) this.transformUpdateTask).initialTransform = initialTransform;
        } else {
            this.transformUpdateTask = new UpdateRootAttachmentTask(
                    attachment, initialTransform, activeChangeHandler);
        }
        this.transformUpdateTask.reinitialize();
        return this.transformUpdateTask;
    }

    private static abstract class UpdateTask extends ForkJoinTask<Void> {
        private static final long serialVersionUID = 2077912465035575092L;
        public final Attachment attachment;
        public final ActiveChangeHandler activeChangeHandler;

        public UpdateTask(
                final Attachment attachment,
                final ActiveChangeHandler activeChangeHandler
        ) {
            this.attachment = attachment;
            this.activeChangeHandler = activeChangeHandler;
        }

        @Override
        public final Void getRawResult() {
            return null;
        }

        @Override
        protected final void setRawResult(Void value) {
        }

        @Override
        protected final boolean exec() {
            performUpdates();
            return true;
        }

        /**
         * Performs the transformation updates for this attachment
         * right now on the calling thread.
         */
        public abstract void performUpdates();

        /**
         * Updates the child attachments of this attachment.
         * If more than 2 children exist, child-1 children are
         * forked onto other threads.
         *
         * @param state Internal state
         */
        protected final void updateChildren(AttachmentInternalState state) {
            final int nrOfChildren = state.children.size();
            if (nrOfChildren == 0) {
                return;
            }

            // Fork further tasks if more than 2 children exist
            // Do in reverse order, same way ForkJoinTask invokeAll reference
            // implementation does it. Is important for performance!
            //
            // The last child we execute inside this method to avoid an extra
            // unneeded fork().
            int i = nrOfChildren - 1;
            while (true) {
                Attachment child = state.children.get(i);
                AttachmentInternalState childState = child.getInternalState();
                if (childState.transformUpdateTask == null) {
                    childState.transformUpdateTask = new UpdateRelativeToParentTask(
                            child, this.activeChangeHandler);
                }

                if (i == 0) {
                    childState.transformUpdateTask.performUpdates();
                    break;
                } else {
                    childState.transformUpdateTask.reinitialize();
                    childState.transformUpdateTask.fork();
                    --i;
                }
            }

            // Wait until the forked children are done processing
            for (i = 1; i < nrOfChildren; i++) {
                state.children.get(i).getInternalState().transformUpdateTask.join();
            }
        }
    }

    private static final class UpdateRelativeToParentTask extends UpdateTask {
        private static final long serialVersionUID = 8242564088493119093L;

        public UpdateRelativeToParentTask(
                final Attachment attachment,
                final ActiveChangeHandler activeChangeHandler
        ) {
            super(attachment, activeChangeHandler);
        }

        @Override
        public void performUpdates() {
            final AttachmentInternalState state = this.attachment.getInternalState();
            final Matrix4x4 initialTransform;
            try {
                initialTransform = state.parent.getTransform();
            } catch (NullPointerException ex) {
                if (state.parent == null) {
                    throw new IllegalStateException("Attachment has no parent");
                } else {
                    throw ex;
                }
            }

            state.updateTransform(this.attachment, initialTransform, this.activeChangeHandler);

            updateChildren(state);
        }
    }

    private static final class UpdateRootAttachmentTask extends UpdateTask {
        private static final long serialVersionUID = -758729101023975440L;
        public Matrix4x4 initialTransform;

        public UpdateRootAttachmentTask(
                final Attachment attachment,
                final Matrix4x4 initialTransform,
                final ActiveChangeHandler activeChangeHandler
        ) {
            super(attachment, activeChangeHandler);
            this.initialTransform = initialTransform;
        }

        @Override
        public void performUpdates() {
            final AttachmentInternalState state = this.attachment.getInternalState();

            state.updateTransform(this.attachment, this.initialTransform, this.activeChangeHandler);

            updateChildren(state);
        }
    }
}
