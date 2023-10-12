package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.attachments.config.ObjectPosition;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;

/**
 * Controller object for attachments. This is added to the minecart
 * and is updated regularly.
 */
public interface Attachment extends AttachmentNameLookup.Supplier {

    /**
     * Gets controller internal state information. Should return the same instance
     * all the time. Internal use only.
     * 
     * @return state information
     */
    AttachmentInternalState getInternalState();

    /**
     * Gets the attachment manager instance that initialized this attachment
     * and oversees its interaction with the outside environment.
     * 
     * @return attachment manager
     */
    default AttachmentManager getManager() {
        return getInternalState().manager;
    }

    /**
     * Gets the configuration of this attachment. This is automatically populated
     * with the attachment's configuration before {@link #onAttached()} is called.
     * The configuration is mutable, and when properties of the attachment are
     * changed, they should be updated in the configuration before or
     * during {@link #onDetached()}.
     * 
     * @return configuration
     */
    default ConfigurationNode getConfig() {
        return getInternalState().config;
    }

    /**
     * Gets a Set of unique names that identify this attachment. External processes
     * can address this attachment by using one of these names.
     *
     * @return Set of attachment names
     */
    default Set<String> getNames() {
        return getInternalState().names;
    }

    /**
     * Gets the plugin that provided and created this attachment instance. Same as the
     * plugin returned by {@link AttachmentType#getPlugin()} but is efficiently cached.<br>
     * <br>
     * Can be overrided to cast and return the true plugin instance type. Can be used
     * by attachment implementations to access internal plugin state.
     *
     * @return plugin instance that provides this attachment
     */
    default Plugin getPlugin() {
        return getInternalState().plugin;
    }

    /**
     * Called when the controller is attached to the real world.
     * From this point onwards further initialization can be performed.
     */
    void onAttached();

    /**
     * Called when the controller is detached from the real world.
     * After this call all stored information in the controller is invalid,
     * until onAttached() is called again.
     */
    void onDetached();

    /**
     * Called when the attachment needs to (re)load the configuration.
     * This method is called after {@link #onAttached()} is called for the first time,
     * and when configuration is reloaded while the attachment remains attached.
     * A guarantee is made that the attachment will be attached when this method is called.
     *
     * @param config The configuration of the attachment
     */
    void onLoad(ConfigurationNode config);

    /**
     * Called before the attachment needs to reload the configuration. If this method
     * returns <i>true</i> (default) then it will perform a hot reload by calling
     * {@link #onLoad(ConfigurationNode)} with the new configuration. If it returns
     * <i>false</i> then no hot reloading will occur. Instead, it will recreate the
     * attachment and all child attachments with the new configuration.
     *
     * @param config The configuration of the attachment
     * @return <i>True</i> if reloading can proceed, <i>false</i> if the attachment
     *         and all child attachments should be recreated instead.
     */
    default boolean checkCanReload(ConfigurationNode config) {
        return true;
    }

    /**
     * Called right after the {@link #getTransform()} has been updated.
     * Additional transformations can be performed here on the transformation matrix,
     * and position information can be calculated. Called every tick.<br>
     * <br>
     * <b>Important: </b>this method may be called from other threads when
     * multi-threaded computation is used. Fields of this attachments can be safely
     * updated, but access to shared resources should be protected. It might
     * be better to access shared state, such as blocks or entities, inside
     * {@link #onTick()}.<br>
     * <br>
     * It is not recommended to send packets inside this method, because asynchronous
     * sending of packets is queued by the server. Doing so might introduce unneeded
     * overhead.
     * 
     * @param transform (same as {@link #getTransform()} but mutable)
     */
    void onTransformChanged(Matrix4x4 transform);

    /**
     * Called every tick to update the attachment.<br>
     * <br>
     * Before this method is called,
     * the {@link #getTransform()} information is updated. If the position of
     * the attachment needs to be changed, this should be done inside
     * {@link #onTransformChanged(Matrix4x4)} for better performance.
     */
    void onTick();

    /**
     * Called every now and then to refresh the position of the attachment.<br>
     * <br>
     * <b>Important: </b>this method is not called every tick, and may cause jitter
     * when used for updating something that changes every tick. If this is the case,
     * perform movement updates inside {@link #onTick()} instead.
     * 
     * @param absolute whether this is an absolute (synchronize) movement update.
     *        If this is true, the position information should be refreshed.
     */
    void onMove(boolean absolute);

    /**
     * Gets whether this attachment has been attached. This returns true if
     * {@link #onAttached()} was last called. It returns false if this was never
     * called, or {@link #onDetached()} was last called.
     * 
     * @return True if this attachment has been attached
     */
    default boolean isAttached() {
        return this.getInternalState().attached;
    }

    /**
     * Gets whether this attachment is focused. This returns true if {@link #onFocus()}
     * was last called. It returns false if this was never called, or {@link #onBlur()}
     * was last called.
     * 
     * @return True if this attachment receives focus
     */
    default boolean isFocused() {
        return this.getInternalState().focused;
    }

    /**
     * Sets whether this attachment is focused. This changes the {@link #isFocused()}
     * state, as well call the {@link #onFocus()} or {@link #onBlur()} callbacks as required.
     * 
     * @param focused state to set to
     */
    default void setFocused(boolean focused) {
        AttachmentInternalState state = this.getInternalState();
        if (state.focused != focused) {
            state.focused = focused;
            if (state.attached) {
                if (focused) {
                    onFocus();
                } else {
                    onBlur();
                }
            }
        }
    }

    /**
     * Called when an attachment is focused in the editor or by other means.
     * The attachment should try to change appearance to indicate it received focus.
     */
    default void onFocus() {
    }

    /**
     * Called when an attachment loses focus in the editor or by other means.
     * The attachment should reset the appearance changes that were performed by {@link #onFocus()}.
     */
    default void onBlur() {
    }

    /**
     * Called when the attachment {@link #isActive()} property changes as a result
     * of reconfiguration or animation being played. If a parent of this attachment
     * becomes inactive or active, it may also fire such events.
     * 
     * @param active The new active state
     */
    default void onActiveChanged(boolean active) {
    }

    /**
     * Makes this attachment visible to a viewer for the first time.
     * This is automatically called for you after {@link #onAttached()} is called,
     * and whenever a new viewer moves within range.<br>
     * <br>
     * Besides implementing this method, {@link #makeVisible(AttachmentViewer)}
     * can be implemented as well, which gives access to an AttachmentViewer.
     * It's more efficient to send packets using that viewer. If that method
     * is overrided, this method is no longer called.
     * 
     * @param viewer Player to make it visible to
     */
    void makeVisible(Player viewer);

    /**
     * Makes this attachment visible to a viewer for the first time.
     * This is automatically called for you after {@link #onAttached()} is called,
     * and whenever a new viewer moves within range.
     * 
     * @param viewer Attachment Viewer to make it visible to
     */
    default void makeVisible(AttachmentViewer viewer) {
        this.makeVisible(viewer.getPlayer());
    }

    /**
     * Makes this attachment invisible (despawns) for a viewer.
     * This is automatically called for you before {@link #onDetached()} is called,
     * and whenever a new viewer moves out of range.<br>
     * <br>
     * Besides implementing this method, {@link #makeHidden(AttachmentViewer)}
     * can be implemented as well, which gives access to an AttachmentViewer.
     * It's more efficient to send packets using that viewer. If that method
     * is overrided, this method is no longer called.
     * 
     * @param viewer Player to hide it from
     */
    void makeHidden(Player viewer);

    /**
     * Makes this attachment invisible (despawns) for a viewer.
     * This is automatically called for you before {@link #onDetached()} is called,
     * and whenever a new viewer moves out of range.
     * 
     * @param viewer Attachment Viewer to hide it from
     */
    default void makeHidden(AttachmentViewer viewer) {
        this.makeHidden(viewer.getPlayer());
    }

    /**
     * Gets the configured parent-relative object position of this attachment.
     * 
     * @return object position
     */
    default ObjectPosition getConfiguredPosition() {
        return this.getInternalState().position;
    }

    /**
     * Gets the transformation matrix that was applied to the entity
     * in the previous tick (update). See {@link #getTransform()}.
     * 
     * @return previous tick transformation matrix
     */
    default Matrix4x4 getPreviousTransform() {
        AttachmentInternalState state = this.getInternalState();
        return (state.last_transform == null) ? state.curr_transform : state.last_transform;
    }

    /**
     * Gets the transformation matrix that is currently applied to obtain the
     * absolute world-coordinates position and rotation of this attachment.
     * 
     * @return transformation matrix
     */
    default Matrix4x4 getTransform() {
        return this.getInternalState().curr_transform;
    }

    /**
     * Adds a controller as a child of this one, which will have positions
     * relative to this one.
     * 
     * @param child controller to add
     */
    default void addChild(Attachment child) {
        this.getInternalState().children.add(child);
        child.getInternalState().parent = this;
    }

    /**
     * Adds a controller as a child of this one, which will have positions
     * relative to this one.
     *
     * @param index Index in the list of children of where to add the child
     * @param child controller to add
     */
    default void addChild(int index, Attachment child) {
        this.getInternalState().children.add(index, child);
        child.getInternalState().parent = this;
    }

    /**
     * Removes a controller that was added as a child of this one. The child will
     * be parented to null (becomes a new root).
     *
     * @param child Child to remove
     * @return True if removed
     */
    default boolean removeChild(Attachment child) {
        if (this.getInternalState().children.remove(child)) {
            child.getInternalState().parent = null;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Gets a list of children of this controller.
     * The order should stay unique when children are added.
     * 
     * @return children
     */
    default List<Attachment> getChildren() {
        return this.getInternalState().children;
    }

    /**
     * Obtains an immutable snapshot of the {@link AttachmentNameLookup name lookup} of a
     * subtree of attachments starting with this attachment. Internally caches the result until
     * this subtree changes. Identity comparison can be used to check whether this subtree
     * changed since a previous invocation.<br>
     * <br>
     * Is multi-thread safe.
     *
     * @return AttachmentNameLookup
     */
    @Override
    default AttachmentNameLookup getNameLookup() {
        return getManager().getNameLookup(this);
    }

    /**
     * Gets the parent controller of this controller, if one is available.
     * 
     * @return parent controller
     */
    default Attachment getParent() {
        return this.getInternalState().parent;
    }

    /**
     * Gets all the viewers of this attachment
     * 
     * @return viewers
     */
    Collection<Player> getViewers();

    /**
     * Gets the Attachment Viewers who can currently view this attachment
     *
     * @return Collection of attachment viewers
     */
    Collection<AttachmentViewer> getAttachmentViewers();

    /**
     * Gets whether this attachment is hidden ( {@link #makeHidden(Player)} ) when the
     * attachment or a parent attachment is inactive.
     * 
     * @return True if hidden when inactive, False if not
     */
    default boolean isHiddenWhenInactive() { return true; }

    /**
     * Gets whether or not this attachment is active.
     * See {@link #setActive(boolean)}.
     * 
     * @return True if active, False if not.
     */
    default boolean isActive() {
        return this.getInternalState().active;
    }

    /**
     * Sets whether this attachment is active. If active, it is displayed and updated
     * to the players. If it is inactive, the attachment and all child attachments are hidden.
     * 
     * @param active state
     */
    default void setActive(boolean active) {
        AttachmentInternalState state = this.getInternalState();
        if (state.active != active) {
            state.active = active;
            if (!HelperMethods.hasInactiveParent(this)) {
                HelperMethods.updateActiveRecursive(this, active, this.getViewers());
            }
        }
    }

    /**
     * Some attachment types, like entities, will have a default position relative
     * to it where passengers are normally seated. Implement this method to specify
     * exactly where a passenger sits on top of this attachment, if directly mounted.<br>
     * <br>
     * The input transform is the current position of this attachment. It should
     * be modified, such as with relative translations, to where the seat is positioned.<br>
     * <br>
     * Doing nothing in this method will effectively result in the seat being exactly
     * where this attachment is located.
     *
     * @param transform Transformation matrix to write the seat position changes to
     */
    default void applyPassengerSeatTransform(Matrix4x4 transform) {
    }

    /**
     * Adds a new animation to this attachment, which can then be played
     * again when specifying the name of the animation in the animation options.
     * 
     * @param animation to add
     */
    default void addAnimation(Animation animation) {
        this.getInternalState().animations.put(animation.getOptions().getName(), animation);
    }

    /**
     * Gets a list of animation names defined for this attachment
     *
     * @return unmodifiable list of registered animation names. Unsorted.
     */
    default List<String> getAnimationNames() {
        return Collections.unmodifiableList(new ArrayList<String>(this.getInternalState().animations.keySet()));
    }

    /**
     * Gets a set of animation scenes defined for an animation of this attachment
     *
     * @param animationName Name of the animation to get the scenes for
     * @return unmodifiable set of registered animation scenes. Sorted by play order.
     */
    default Set<String> getAnimationScenes(String animationName) {
        Animation animation = this.getInternalState().animations.get(animationName);
        return (animation == null) ? Collections.emptySet() : animation.getSceneNames();
    }

    /**
     * Gets a list of animation names defined for this attachment, or any of the child
     * attachments, recursively. The list only contains the unique animation names.
     *
     * @return unmodifiable list of registered animation names of this attachment,
     *         and all children recursively. Unsorted.
     */
    default List<String> getAnimationNamesRecursive() {
        HashSet<String> tmp = new LinkedHashSet<String>();
        HelperMethods.addAnimationNamesToSetRecursive(tmp, this);
        return Collections.unmodifiableList(new ArrayList<String>(tmp));
    }

    /**
     * Gets a set of scenes of an animation defined for this attachment, or any of
     * the child attachments, recursively. The set is ordered by order of occurance.
     *
     * @param animationName Name of the animation to get scenes for
     * @return Set of scenes
     */
    default Set<String> getAnimationScenesRecursive(String animationName) {
        HashSet<String> tmp = new LinkedHashSet<String>();
        HelperMethods.addAnimationScenesToSetRecursive(tmp, animationName, this);
        return Collections.unmodifiableSet(tmp);
    }

    /**
     * Gets a read-only collection of animations stored using {@link #addAnimation(Animation)}.
     * 
     * @return animations stored
     */
    default Collection<Animation> getAnimations() {
        return this.getInternalState().animations.values();
    }

    /**
     * Clears all animations stored for this attachment
     */
    default void clearAnimations() {
        this.getInternalState().animations.clear();
    }

    /**
     * Gets the animation that is currently playing.
     * Null if no animation is playing.
     * 
     * @return current animation
     */
    default Animation getCurrentAnimation() {
        return this.getInternalState().currentAnimation;
    }

    /**
     * Stops the playback of any animations playing or queued right now.
     */
    default void stopAnimation() {
        AttachmentInternalState state = this.getInternalState();
        state.currentAnimation = null;
        state.nextAnimationQueue = Collections.emptyList();
    }

    /**
     * Starts playing an animation for this attachment. The child attachments are not affected.
     * If the animation is already playing, this function does nothing. To force a reset,
     * call {@link #stopAnimation()} prior.
     * 
     * @param animation to start
     */
    default void startAnimation(Animation animation) {
        if (animation == null) {
            this.stopAnimation();
            return;
        }

        AttachmentInternalState state = this.getInternalState();
        if (state.currentAnimation == null || animation.getOptions().getReset()) {
            // Replace current animation
            state.currentAnimation = animation;
            state.currentAnimation.start();
            state.nextAnimationQueue = Collections.emptyList();
        } else if (animation.getOptions().getQueue()) {
            // Queue the next animation to be played after the current one
            if (state.nextAnimationQueue.isEmpty()) {
                state.nextAnimationQueue = new ArrayList<Animation>(1);
            }
            state.nextAnimationQueue.add(animation);
        } else if (state.currentAnimation.isSame(animation)) {
            // Play same animation with new options
            state.currentAnimation.setOptions(animation.getOptions().clone());
            state.nextAnimationQueue = Collections.emptyList();
        } else {
            // New animation, we must reset
            state.currentAnimation = animation;
            state.currentAnimation.start();
            state.nextAnimationQueue = Collections.emptyList();
        }
    }

    /**
     * Gets whether this attachment uses a particular Entity Id as part of
     * displaying its contents in the world. This is used to identify what
     * attachment was clicked by a player.
     * 
     * @param entityId
     * @return True if this attachment contains the entity Id
     */
    default boolean containsEntityId(int entityId) { return false; }

    /**
     * Starts a named animation for this attachment and all child attachments
     * recursively. If the animation exists in this attachments or one of its
     * nested children, then the animation will play there and this method
     * will return true.
     * 
     * @param name of the animation
     * @return True if an animation was found and started
     */
    default boolean playNamedAnimationRecursive(String name) {
        return this.playNamedAnimationRecursive(new AnimationOptions(name));
    }

    /**
     * Starts an animation for this attachment and all child attachments
     * recursively. If the animation exists in this attachments or one of its
     * nested children, then the animation will play there and this method
     * will return true.
     * 
     * @param options that specify the animation and animation configuration
     * @return True if an animation was found and started
     */
    default boolean playNamedAnimationRecursive(AnimationOptions options) {
        return HelperMethods.playAnimationRecursive(this, options);
    }

    /**
     * Starts an animation for this attachment. Only if the animation
     * exists for this attachment, will it be played.
     * 
     * @param name of the animation
     * @return True if an animation was found and started
     */
    default boolean playNamedAnimation(String name) {
        return this.playNamedAnimation(new AnimationOptions(name));
    }

    /**
     * Starts an animation for this attachment. Only if the animation
     * exists for this attachment, will it be played.
     * 
     * @param options that specify the animation and animation configuration
     * @return True if an animation was found and started
     */
    default boolean playNamedAnimation(AnimationOptions options) {
        return HelperMethods.playAnimation(this, options);
    }

    /**
     * Traverses down this tree of attachments based on the target
     * path indices specified. If the attachment at this path exists,
     * it is returned, otherwise this method returns <i>null</i>.
     * 
     * @param targetPath
     * @return attachment at targetPath, <i>null</i> if not found.
     */
    default Attachment findChild(int[] targetPath) {
        Attachment target = this;
        for (int index : targetPath) {
            List<Attachment> children = target.getChildren();
            if (index >= 0 && index < children.size()) {
                target = children.get(index);
            } else {
                return null;
            }
        }
        return target;
    }

    /**
     * Gets absolute path of this attachment relative to the root of the
     * attachment tree. This is an array of parent-to-child indices.
     *
     * @return path
     */
    default int[] getPath() {
        Attachment parent = this.getParent();
        if (parent == null) {
            return new int[0];
        } else {
            int[] result = parent.getPath();
            int len = result.length;
            result = Arrays.copyOf(result, len + 1);
            result[len] = parent.getChildren().indexOf(this);
            return result;
        }
    }

    /**
     * A type of attachment that produces some sort of effect. An effect can be
     * sound, particles or some other thing triggered through packets.<br>
     * <br>
     * This attachment controls:
     * <ul>
     *     <li>Where the effect occurs</li>
     *     <li>To who the effect is displayed</li>
     *     <li>Effect details, such as the audio channel and sound name for sound effects</li>
     * </ul>
     * An external process or attachment controls:
     * <ul>
     *     <li>When the effect is played</li>
     *     <li>How often the effect is played</li>
     *     <li>The playback speed of the effect</li>
     *     <li>The intensity of the effect - volume for sounds</li>
     * </ul>
     */
    interface EffectAttachment extends Attachment {

        /**
         * Plays the effect. Is possibly called asynchronously, so the implementation must ensure the
         * operation is thread-safe.
         *
         * @param options Options that change how the effect is played
         */
        void playEffect(EffectOptions options);

        /**
         * Stops playing this effect, if possible. Is possibly called asynchronously, so the implementation
         * must ensure the operation is thread-safe.
         */
        void stopEffect();

        /**
         * Options specified when playing an effect
         */
        class EffectOptions {
            /** Default options for an effect */
            public static final EffectOptions DEFAULT = new EffectOptions(1.0, 1.0);
            private final double intensity, speed;

            protected EffectOptions(double intensity, double speed) {
                this.intensity = intensity;
                this.speed = speed;
            }

            /**
             * The intensity of the effect. For sounds this means the volume, and for particle effects
             * it means the number of particles spawned, or something else like color changes.
             *
             * @return Intensity, where 0.0 plays nothing and 1.0 plays the default amount
             */
            public double intensity() {
                return intensity;
            }

            /**
             * Playback speed of the effect. For sounds this is pitch, for particles the rate at which
             * they move.
             *
             * @return Speed, where 1.0 is the default
             */
            public double speed() {
                return speed;
            }

            /**
             * Returns new Effect Options with the intensity changed
             *
             * @param newIntensity New intensity
             * @return New EffectOptions with intensity changed
             */
            public EffectOptions withIntensity(double newIntensity) {
                return new EffectOptions(newIntensity, this.speed);
            }

            /**
             * Returns new Effect Options with the speed changed
             *
             * @param newSpeed New speed
             * @return New EffectOptions with speed changed
             */
            public EffectOptions withSpeed(double newSpeed) {
                return new EffectOptions(this.intensity, newSpeed);
            }

            /**
             * Retrieves Effect Options for playing an effect at a certain intensity (sound volume)
             * and speed (sound pitch).
             *
             * @param intensity Intensity
             * @param speed Speed
             * @return EffectOptions
             */
            public static EffectOptions of(double intensity, double speed) {
                return new EffectOptions(intensity, speed);
            }
        }
    }
}
