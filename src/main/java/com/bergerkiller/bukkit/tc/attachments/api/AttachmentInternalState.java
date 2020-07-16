package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;

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
    }
}
