package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.PositionAnchorType;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;

public abstract class CartAttachment {
    public List<CartAttachment> children = new ArrayList<CartAttachment>(0);
    private PositionAnchorType anchor = PositionAnchorType.DEFAULT;
    private Map<String, Animation> animations = new HashMap<String, Animation>();
    private Animation currentAnimation = null;
    protected MinecartMemberNetwork controller = null;
    protected CartAttachment parent = null;
    protected ConfigurationNode config = null;
    public Matrix4x4 last_transform;
    public Matrix4x4 transform;
    public Matrix4x4 local_transform;
    public Vector3 position;
    public Vector3 rotation;

    public void onAttached() {
        this.anchor = PositionAnchorType.DEFAULT;
        this.position = new Vector3(0.0, 0.0, 0.0);
        this.rotation = new Vector3(0.0, 0.0, 0.0);
        if (this.config.isNode("position")) {
            ConfigurationNode positionNode = this.config.getNode("position");
            this.position.x = positionNode.get("posX", 0.0);
            this.position.y = positionNode.get("posY", 0.0);
            this.position.z = positionNode.get("posZ", 0.0);
            this.rotation.x = positionNode.get("rotX", 0.0);
            this.rotation.y = positionNode.get("rotY", 0.0);
            this.rotation.z = positionNode.get("rotZ", 0.0);
            this.anchor = positionNode.get("anchor", PositionAnchorType.DEFAULT);
        }
        this.local_transform = new Matrix4x4();
        this.local_transform.translate(this.position);
        this.local_transform.rotateYawPitchRoll(this.rotation);

        this.animations.clear();
        if (this.config.isNode("animations")) {
            ConfigurationNode animations = this.config.getNode("animations");
            for (ConfigurationNode animationConfig : animations.getNodes()) {
                Animation animation = Animation.loadFromConfig(animationConfig);
                this.animations.put(animation.getOptions().getName(), animation);
            }
        }
    }

    public void onDetached() {
        this.last_transform = null;
        this.transform = null;
        this.animations.clear();
    }

    /**
     * Gets the network controller that owns and manages this attachment
     * 
     * @return controller
     */
    public MinecartMemberNetwork getController() {
        return this.controller;
    }

    /**
     * Gets whether a particular Entity Id is in use by this attachment.
     * This is called when the player interacts to find out which entity was interacted with.
     * 
     * @param entityId to check
     * @return True if the entity id is part of this attachment
     */
    public boolean containsEntityId(int entityId) {
        return false;
    }

    /**
     * Gets an Entity Id of an Entity other entities can mount to mount this attachment.
     * Returns -1 if no mounting is possible.
     * 
     * @return Mountable entity Id, or -1 if not mountable
     */
    public int getMountEntityId() {
        return -1;
    }

    /**
     * Adds a new animation to this attachment
     * 
     * @param animation to add
     */
    public void addAnimation(Animation animation) {
        this.animations.put(animation.getOptions().getName(), animation);
    }

    /**
     * Starts a named animation for this attachment and all child attachments
     * 
     * @param name of the animation
     * @return True if an animation was found and started
     */
    public boolean playNamedAnimation(String name) {
        return this.playNamedAnimation(new AnimationOptions(name));
    }

    /**
     * Starts an animation for this attachment and all child attachments
     * 
     * @param options that specify the animation and animation configuration
     * @return True if an animation was found and started
     */
    public boolean playNamedAnimation(AnimationOptions options) {
        // Play animation defined in the attachment configuration
        if (this.playNamedAnimationRecursive(options)) {
            return true;
        }

        // Attempt playing a default animation
        Animation defaultAnimation = TCConfig.defaultAnimations.get(options.getName());
        if (defaultAnimation != null) {
            this.startAnimation(defaultAnimation.clone().applyOptions(options));
            return true;
        }

        return false;
    }

    private final boolean playNamedAnimationRecursive(AnimationOptions options) {
        boolean result = false;
        Animation animation = this.animations.get(options.getName());
        if (animation != null) {
            result = true;
            this.startAnimation(animation.clone().applyOptions(options));
        }
        for (CartAttachment child : this.children) {
            result |= child.playNamedAnimationRecursive(options);
        }
        return result;
    }

    /**
     * Stops any currently playing animation
     */
    public void stopAnimation() {
        this.currentAnimation = null;
    }

    /**
     * Starts playing an animation for this attachment. The child attachments are not affected.
     * If the animation is already playing, this function does nothing. To force a reset,
     * call {@link #stopAnimation()} prior.
     * 
     * @param animation
     */
    public void startAnimation(Animation animation) {
        if (this.currentAnimation == null || !this.currentAnimation.isSame(animation)) {
            this.currentAnimation = animation;
            this.currentAnimation.start();
        } else {
            this.currentAnimation.setOptions(animation.getOptions().clone());
        }
    }

    /**
     * Gets the animation that is currently playing.
     * Null if no animation is playing.
     * 
     * @return current animation
     */
    public Animation getCurrentAnimation() {
        return this.currentAnimation;
    }

    public abstract void makeVisible(Player viewer);

    public abstract void makeHidden(Player viewer);

    /**
     * Called right after the position transformation matrix is updated.
     * Relative positioning of the attachment should happen here.
     */
    public void onPositionUpdate() {
        this.transform.multiply(this.local_transform);
    }

    public abstract void onTick();
    
    public abstract void onMove(boolean absolute);

    public static CartAttachment findAttachment(CartAttachment root, int entityId) {
        if (root.containsEntityId(entityId)) {
            return root;
        } else {
            for (CartAttachment child : root.children) {
                CartAttachment att = findAttachment(child, entityId);
                if (att != null) {
                    return att;
                }
            }
            return null;
        }
    }

    public static void performTick(CartAttachment attachment) {
        attachment.onTick();
        for (CartAttachment child : attachment.children) {
            performTick(child);
        }
    }

    public static void performMovement(CartAttachment attachment, boolean absolute) {
        attachment.onMove(absolute);
        for (CartAttachment child : attachment.children) {
            performMovement(child, absolute);
        }
    }

    public static void updatePositions(CartAttachment attachment, Matrix4x4 transform) {
        attachment.last_transform = attachment.transform;

        // Update the transform based on the anchor setting
        switch (attachment.anchor) {
        case FRONT_WHEEL:
            attachment.transform = attachment.getController().getMember().getWheels().front().getAbsoluteTransform();
            break;
        case BACK_WHEEL:
            attachment.transform = attachment.getController().getMember().getWheels().back().getAbsoluteTransform();
            break;
        default:
            attachment.transform = transform.clone();
            break;
        }

        // Animations!
        if (attachment.currentAnimation != null) {
            double dt = attachment.getController().getAnimationDeltaTime();
            AnimationNode animNode = attachment.currentAnimation.update(dt);
            if (animNode != null) {
                animNode.apply(attachment.transform);
            }
        }

        attachment.onPositionUpdate();
        if (attachment.last_transform == null) {
            attachment.last_transform = attachment.transform.clone();
        }
        for (CartAttachment child : attachment.children) {
            updatePositions(child, attachment.transform);
        }
    }

    /**
     * De-initializes cart attachments after no viewers see the attachments anymore.
     * Done before attachments are radically changed.
     * 
     * @param attachment
     */
    public static void deinitialize(CartAttachment attachment) {
        for (CartAttachment child : attachment.children) {
            deinitialize(child);
        }
        attachment.onDetached();
    }

    /**
     * Loads a full cart attachment tree from configuration
     * 
     * @param controller
     * @param config
     * @return cart attachment root node
     */
    public static CartAttachment initialize(MinecartMemberNetwork controller, ConfigurationNode config) {
        CartAttachment attachment = loadAttachments(controller, config);
        attachment.attachAttachments();
        updatePositions(attachment, controller.getLiveTransform());
        return attachment;
    }

    /**
     * Called right after construction of this attachment.
     * Loads the children of this widget based on the attachments configuration option.
     */
    protected void onLoadChildren() {
        for (ConfigurationNode childNode : this.config.getNodeList("attachments")) {
            CartAttachment child = loadAttachments(controller, childNode);
            child.parent = this;
            this.children.add(child);
        }
    }

    private static CartAttachment loadAttachments(MinecartMemberNetwork controller, ConfigurationNode config) {
        CartAttachment attachment = config.get("type", CartAttachmentType.EMPTY).createAttachment();
        attachment.controller = controller;
        attachment.config = config;
        attachment.onLoadChildren();
        return attachment;
    }

    private void attachAttachments() {
        this.onAttached();
        for (CartAttachment child : this.children) {
            child.attachAttachments();
        }
    }

    protected Vector calcMotion() {
        Vector pos_old = this.last_transform.toVector();
        Vector pos_new = this.transform.toVector();
        return pos_new.subtract(pos_old);
    }

    /**
     * Gets the position of this attachment based on the last-applied transformation information.
     * 
     * @return position
     */
    public Vector getPosition() {
        return this.transform.toVector();
    }
}
