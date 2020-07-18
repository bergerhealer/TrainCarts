package com.bergerkiller.bukkit.tc.attachments.helper;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentInternalState;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;

/**
 * Just some helper methods to keep the API clean
 */
public class HelperMethods {
    /**
     * Cyclical color wheel from which a color is picked by index of the
     * attachment relative to parent. Modulus 16.
     */
    private static final ChatColor[] GLOW_COLORS = new ChatColor[] {
            ChatColor.DARK_RED, ChatColor.DARK_GREEN, ChatColor.DARK_BLUE,
            ChatColor.DARK_AQUA, ChatColor.DARK_PURPLE, ChatColor.YELLOW,
            ChatColor.RED, ChatColor.GREEN, ChatColor.BLUE,
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GOLD,
            ChatColor.BLACK, ChatColor.DARK_GRAY, ChatColor.GRAY, ChatColor.WHITE
    };

    /**
     * Updates the positions of an attachment and all child attachments
     * 
     * @param attachment to update
     * @param transform of the attachment relative to which it should be updated
     */
    public static void updatePositions(Attachment startAttachment, Matrix4x4 transform) {
        UpdatePositionsState update_state = UpdatePositionsState.INSTANCE;
        try {
            // Refresh root attachment using the specified transform
            updatePositionsSingle(update_state, startAttachment, transform);

            // Add all children and begin processing those
            update_state.pendingUpdates.addAll(startAttachment.getChildren());

            // Update positions of all attachments in the list since previous index
            // Add their children to the list and then process all of those
            // Do this until the entire list is exhausted
            int startIndex = 0, endIndex;
            while (startIndex < (endIndex = update_state.pendingUpdates.size())) {
                for (int index = startIndex; index < endIndex; index++) {
                    Attachment attachment = update_state.pendingUpdates.get(index);
                    updatePositionsSingle(update_state, attachment, attachment.getParent().getTransform());
                    update_state.pendingUpdates.addAll(attachment.getChildren());
                }
                startIndex = endIndex;
            }

            // Handle changes in active mode last, as this causes spawning/despawning
            for (Map.Entry<Attachment, Boolean> activeChange : update_state.pendingActiveChanges) {
                activeChange.getKey().setActive(activeChange.getValue().booleanValue());
            }
        } finally {
            update_state.pendingUpdates.clear();
            update_state.pendingActiveChanges.clear();
        }
    }

    /*
     * Update logic for a single attachment only
     */
    private static void updatePositionsSingle(UpdatePositionsState update_state, Attachment attachment, Matrix4x4 transform) {
        AttachmentInternalState state = attachment.getInternalState();

        // Update last transform if one is available
        boolean hasLastTransform = (state.last_transform != null);
        if (state.curr_transform != null) {
            if (state.last_transform == null) {
                state.last_transform = state.curr_transform.clone();
            } else {
                state.last_transform.set(state.curr_transform);
            }
            hasLastTransform = true;
        }

        // Assign new transform to start from
        if (state.curr_transform == null) {
            state.curr_transform = transform.clone();
        } else {
            state.curr_transform.set(transform);
        }

        if (state.position.anchor.appliedLate()) {
            // First apply the local transformation, then transform using anchor
            state.curr_transform.multiply(state.position.transform);
            state.position.anchor.apply(attachment, state.curr_transform);
        } else {
            // First transform using anchor, then apply the local transformation
            state.position.anchor.apply(attachment, state.curr_transform);
            state.curr_transform.multiply(state.position.transform);
        }

        // Go to next animation when end of animation is reached (or no animation is playing)
        if (!state.nextAnimationQueue.isEmpty() && (state.currentAnimation == null || state.currentAnimation.hasReachedEnd())) {
            state.currentAnimation = state.nextAnimationQueue.remove(0);
            state.currentAnimation.start();
        }

        // If animation was reset (disabled), erase any previous animation state we had
        // If one is running, then refresh the last animation state to apply to this transform
        // If this method returns null, then we keep whatever state we were in prior
        if (state.currentAnimation == null) {
            state.lastAnimationState = null;
        } else if (!state.currentAnimation.hasReachedEnd()) {
            // TODO: Do we need dt here?
            double dt = ((CartAttachment) attachment).getController().getAnimationDeltaTime();
            AnimationNode animNode = state.currentAnimation.update(dt);
            if (animNode != null) {
                state.lastAnimationState = animNode;
            }
        }

        // Apply animation state from current or previous animation to the transformation
        if (state.lastAnimationState != null) {
            // Refresh active changes
            boolean active = state.lastAnimationState.isActive();
            state.lastAnimationState.apply(state.curr_transform);
            if (active != attachment.isActive()) {
                update_state.pendingActiveChanges.add(new AbstractMap.SimpleEntry<Attachment, Boolean>(attachment, active));
            }
        }

        // In case onTransformChanged requires this
        if (!hasLastTransform) {
            state.last_transform = state.curr_transform.clone();
        }

        // Update positions
        attachment.onTransformChanged(state.curr_transform);

        // Refresh
        if (!hasLastTransform) {
            state.last_transform = state.curr_transform.clone();
        }
    }

    // Singleton helper object
    private static final class UpdatePositionsState {
        public static final UpdatePositionsState INSTANCE = new UpdatePositionsState();
        public final ArrayList<Map.Entry<Attachment,Boolean>> pendingActiveChanges = new ArrayList<>();
        public final ArrayList<Attachment> pendingUpdates = new ArrayList<>();
    }

    /**
     * Hides an attachment and all child attachments recursively.
     * This helper function calls {@link #makeHidden(Player)}.
     * 
     * @param root attachment to hide, only root attachment is permitted
     * @param active whether the attachment and parent attachments are active
     * @param viewer to hide it from
     */
    public static void makeHiddenRecursive(Attachment root, boolean active, Player viewer) {
        active &= root.isActive();
        for (Attachment child : root.getChildren()) {
            makeHiddenRecursive(child, active, viewer);
        }
        if (active || !root.isHiddenWhenInactive()) {
            root.makeHidden(viewer);
        }
    }

    /**
     * Makes an attachment and all child attachments visible recursively.
     * This helper function calls {@link #makeVisible(Player)}.
     * 
     * @param root attachment to make visible, only root attachment is permitted
     * @param active whether the attachment and parent attachments are active
     * @param viewer to make it disable to
     */
    public static void makeVisibleRecursive(Attachment root, boolean active, Player viewer) {
        active &= root.isActive();
        if (active || !root.isHiddenWhenInactive()) {
            root.makeVisible(viewer);
        }
        for (Attachment child : root.getChildren()) {
            makeVisibleRecursive(child, active, viewer);
        }
    }

    /**
     * Gets whether a parent higher up the tree is inactive, concluding therefore
     * that the attachment itself can not be active.
     * 
     * @param attachment
     * @return True if a parent is inactive
     */
    public static boolean hasInactiveParent(Attachment attachment) {
        Attachment parent = attachment.getParent();
        while (parent != null) {
            if (!parent.isActive()) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Updates the {@link Attachment#isActive()} for an attachment and all its children, making
     * it visible or hidden for viewers depending on the active change. Only call this method
     * when the active state of the attachment actually changed.
     * 
     * @param attachment
     * @param active state changed to
     * @param viewers of this attachment
     */
    public static void updateActiveRecursive(Attachment attachment, boolean active, Collection<Player> viewers) {
        if (attachment.isHiddenWhenInactive()) {
            if (active) {
                for (Player viewer : viewers) {
                    attachment.makeVisible(viewer);
                }
            } else {
                for (Player viewer : viewers) {
                    attachment.makeHidden(viewer);
                }
            }
            attachment.getInternalState().last_transform = null;
        }
        for (Attachment child : attachment.getChildren()) {
            if (child.isActive()) {
                updateActiveRecursive(child, active, viewers);
            }
        }
    }

    public static void perform_onTick(Attachment attachment) {
        attachment.onTick();
        for (Attachment child : attachment.getChildren()) {
            perform_onTick(child);
        }
    }

    public static void perform_onMove(Attachment attachment, boolean absolute) {
        attachment.onMove(absolute);
        for (Attachment child : attachment.getChildren()) {
            perform_onMove(child, absolute);
        }
    }

    public static void perform_onAttached(Attachment attachment) {
        attachment.getInternalState().attached = true;
        attachment.onAttached();
        attachment.onLoad(attachment.getConfig());
        if (attachment.isFocused()) {
            attachment.onFocus();
        }
        for (Attachment child : attachment.getChildren()) {
            perform_onAttached(child);
        }
    }

    public static void perform_onDetached(Attachment attachment) {
        for (Attachment child : attachment.getChildren()) {
            perform_onDetached(child);
        }
        attachment.onDetached();
        attachment.getInternalState().attached = false;
        attachment.getInternalState().reset();
    }

    public static Attachment findAttachmentWithEntityId(Attachment root, int entityId) {
        if (root.containsEntityId(entityId)) {
            return root;
        } else {
            for (Attachment child : root.getChildren()) {
                Attachment att = findAttachmentWithEntityId(child, entityId);
                if (att != null) {
                    return att;
                }
            }
            return null;
        }
    }

    public static boolean playAnimationRecursive(Attachment attachment, AnimationOptions options) {
        if (playStoredAnimationRecursive(attachment, options)) {
            return true;
        }

        Animation defaultAnimation = TCConfig.defaultAnimations.get(options.getName());
        if (defaultAnimation != null) {
            attachment.startAnimation(defaultAnimation.clone().applyOptions(options));
            return true;
        }

        return false;
    }

    public static boolean playAnimation(Attachment attachment, AnimationOptions options) {
        if (playStoredAnimation(attachment, options)) {
            return true;
        }

        Animation defaultAnimation = TCConfig.defaultAnimations.get(options.getName());
        if (defaultAnimation != null) {
            attachment.startAnimation(defaultAnimation.clone().applyOptions(options));
            return true;
        }

        return false;
    }

    public static void setFocusedRecursive(Attachment attachment, boolean focused) {
        attachment.setFocused(focused);
        for (Attachment child : attachment.getChildren()) {
            setFocusedRecursive(child, focused);
        }
    }

    /**
     * Uses the relative positioning information of an attachment to figure out an appropriate
     * glow color that will best differentiate it from other selections. If no proper
     * color can be selected, WHITE is returned as fallback.
     * 
     * @param attachment
     * @return glow color
     */
    public static ChatColor getFocusGlowColor(Attachment attachment) {
        while (true) {
            Attachment parent = attachment.getParent();
            if (parent == null) {
                return ChatColor.WHITE;
            } else if (parent.isFocused()) {
                attachment = parent;
            } else {
                return GLOW_COLORS[parent.getChildren().indexOf(attachment) & 0xF];
            }
        }
    }

    private static boolean playStoredAnimation(Attachment attachment, AnimationOptions options) {
        Animation anim = attachment.getInternalState().animations.get(options.getName());
        if (anim != null) {
            attachment.startAnimation(anim.clone().applyOptions(options));
            return true;
        }
        return false;
    }

    private static boolean playStoredAnimationRecursive(Attachment attachment, AnimationOptions options) {
        boolean found = playStoredAnimation(attachment, options);
        for (Attachment child : attachment.getChildren()) {
            found |= playStoredAnimationRecursive(child, options);
        }
        return found;
    }
}
