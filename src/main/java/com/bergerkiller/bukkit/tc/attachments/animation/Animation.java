package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * An animation consisting of key frame nodes with time-domain transformations.
 * Class can be inherited overriding {@link #update(dt)} returning a custom position for animations
 * controlled by external input.
 */
public class Animation implements Cloneable {
    private AnimationOptions _options;
    private final AnimationNode[] _nodes;
    private final Map<String, Scene> _scenes;
    private final Scene _entireAnimationScene;
    private Scene _currentScene;
    private double _time;
    private boolean _reachedEnd;

    protected Animation(Animation source) {
        this._options = source._options.clone();
        this._nodes = source._nodes;
        this._scenes = source._scenes;
        this._entireAnimationScene = source._entireAnimationScene;
        this._currentScene = source._currentScene;
        this._time = source._time;
        this._reachedEnd = source._reachedEnd;
    }

    public Animation(String name, String... nodes_config) {
        this(name, Arrays.asList(nodes_config));
    }

    public Animation(String name, List<String> nodes_config) {
        this(name, AnimationNode.parseAllFromStrings(nodes_config));
    }

    public Animation(String name, AnimationNode[] nodes) {
        this._options = new AnimationOptions(name);
        this._nodes = nodes;
        this._time = 0.0;
        this._reachedEnd = false;

        // Compute scenes mapping using the nodes
        this._scenes = new LinkedHashMap<>();
        {
            String lastSceneName = null;
            int lastSceneBegin = -1;
            double lastSceneDuration = 0.0;
            for (int i = 0; i < nodes.length; i++) {
                AnimationNode node = nodes[i];
                if (node.hasSceneMarker() && !node.getSceneMarker().equals(lastSceneName)) {
                    if (lastSceneName != null) {
                        this._scenes.put(lastSceneName, new Scene(lastSceneBegin, i-1, lastSceneDuration));
                    }

                    lastSceneName = node.getSceneMarker();
                    lastSceneDuration = node.getDuration();
                    lastSceneBegin = i;
                } else {
                    lastSceneDuration += node.getDuration();
                }
            }
            if (lastSceneName != null) {
                this._scenes.put(lastSceneName, new Scene(lastSceneBegin, nodes.length - 1, lastSceneDuration));
            }
        }

        // Calculate loop duration when playing the entire animation
        if (nodes.length > 0) {
            double total = 0.0;
            for (AnimationNode node : nodes) {
                total += node.getDuration();
            }
            this._entireAnimationScene = new Scene(0, nodes.length - 1, total);
        } else {
            this._entireAnimationScene = new Scene(0, 0, 0.0);
        }
        this._currentScene = this._entireAnimationScene;
    }

    /**
     * Gets all the options for this animation, which include the animation name.
     * The options are writable, although using {@link #apply(options)} is preferred.
     * 
     * @return animation options
     */
    public final AnimationOptions getOptions() {
        return this._options;
    }

    /**
     * Gets all the names of scenes defined for this animation
     *
     * @return set of animation scene names
     */
    public final Set<String> getSceneNames() {
        return Collections.unmodifiableSet(this._scenes.keySet());
    }

    /**
     * Sets all the options for this animation, which include the animation name.
     * This erases any options previously applied. The current animation moment is preserved
     * when setting these options, that is, the delay change is kept in mind.
     * 
     * @param options to set
     * @return this animation (for chained calls)
     */
    public Animation setOptions(AnimationOptions options) {
        double old_delay = this._options.getDelay();
        this._options = options;
        this._time -= (this._options.getDelay() - old_delay);
        this.updateScene(this.createScene(options));
        this._reachedEnd = false;
        return this;
    }

    /**
     * Gets whether the animation reached the end. When the loop option is set,
     * this end is never reached.
     * 
     * @return True if the end was reached
     */
    public boolean hasReachedEnd() {
        return this._reachedEnd;
    }

    /**
     * Updates the animation parameters while the animation is possibly still running. This
     * updates the speed, delay or looping option without causing a jump in the animation.
     * 
     * @param options to apply
     * @return this animation (for chained calls)
     */
    public Animation applyOptions(AnimationOptions options) {
        double old_delay = this._options.getDelay();
        this._options.apply(options);
        this._time -= (this._options.getDelay() - old_delay);
        this.updateScene(this.createScene(options));
        this._reachedEnd = false;
        return this;
    }

    /**
     * Resets the animation to the beginning, setting the running time to be
     * most appropriate for the animation options currently used. Use
     * {@link #applyOptions(options)} prior to starting to set these options.
     */
    public void start() {
        if (this._options.isReversed()) {
            this._time = this._currentScene.duration();
            if (this._nodes.length >= 1) {
                this._time -= this._nodes[this._currentScene.nodeEndIndex()].getDuration();
            }
        } else {
            this._time = 0.0;
        }
        this._time -= this._options.getDelay();
        this._reachedEnd = false;
    }

    /**
     * Gets whether this animation is the same as another animation.
     * When this is the case, the animation is played/resumed from the last time it played.
     * Can be overrided to disable this functionality for custom animations.
     * 
     * @param animation
     * @return True if the animations are the same
     */
    public boolean isSame(Animation animation) {
        return animation.getOptions().getName().equals(this.getOptions().getName());
    }

    /**
     * Gets the backing array of animation nodes
     * 
     * @return nodes
     */
    public AnimationNode[] getNodeArray() {
        return this._nodes;
    }

    /**
     * Gets the animation node at an index
     * 
     * @param index
     * @return node at this index
     */
    public AnimationNode getNode(int index) {
        return this._nodes[index];
    }

    /**
     * Gets the number of nodes in this animation
     * 
     * @return node count
     */
    public int getNodeCount() {
        return this._nodes.length;
    }

    @Override
    public Animation clone() {
        return new Animation(this);
    }

    /**
     * Updates this animation a single time step
     * 
     * @param dt - delta time in seconds since previous update
     * @return animation node, null if animation is disabled at this time
     */
    public AnimationNode update(double dt) {
        if (this._nodes.length == 0) {
            this._reachedEnd = true;
            return null; // animation missing
        }

        Scene scene = this._currentScene;
        boolean animationStarted = true;
        if (this._options.isLooped()) {
            // When animation is too short, always return node 0.
            if (this._currentScene.isSingleFrame()) {
                this._reachedEnd = true;
                return this._nodes[scene.nodeBeginIndex()];
            }

        } else {

            AnimationNode endNode = this._nodes[scene.nodeEndIndex()];
            double animEnd = scene.duration() - endNode.getDuration();

            // When not looped, check whether the animation finished playing fully,
            // or whether the animation is yet to start
            // Clamp time to the end-time when this happens (!)
            if (this._options.isReversed()) {
                if (this._time <= 0.0) {
                    this._time = 0.0;
                    this._reachedEnd = true;
                    return this._nodes[0];
                } else if (this._time > animEnd) {
                    animationStarted = false;
                }
            } else {
                if (this._time >= animEnd) {
                    this._time = animEnd;
                    this._reachedEnd = true;
                    return endNode;
                } else if (this._time < 0.0) {
                    animationStarted = false;
                }
            }
        }

        // Use time before the update to allow t=0 to display
        double curr_time = this._time;
        this._time += dt * this._options.getSpeed();

        // Not started yet
        if (!animationStarted) {
            return null;
        }

        // Looped:
        // Take modulo of time vs loop duration in order for it to loop around
        // This causes any sort of delay to act more like a phase shift
        if (this._options.isLooped()) {
            this._time = (this._time % scene.duration());
            if (this._time < 0.0) {
                this._time += scene.duration(); // nega
            }
        }

        // Only 1 node? Return that, no weird interpolation please.
        if (scene.nodeCount() == 1) {
            this._reachedEnd = true;
            return this._nodes[scene.nodeBeginIndex()];
        }

        // Interpolate to find the correct animation node
        for (int i = scene.nodeBeginIndex(); i <= scene.nodeEndIndex(); i++) {
            AnimationNode node = this._nodes[i];
            double duration = node.getDuration();
            if (curr_time > duration) {
                curr_time -= duration;
                continue;
            }

            int next_i = (i == scene.nodeEndIndex()) ? scene.nodeBeginIndex() : (i + 1);
            return AnimationNode.interpolate(this._nodes[i], this._nodes[next_i], curr_time/duration);
        }

        // Should never be reached
        return this._nodes[scene.nodeEndIndex()];
    }

    /**
     * Updates the scene being played while preserving the time moment
     * of playback.
     *
     * @param scene
     */
    private void updateScene(Scene scene) {
        // Start duration offset
        if (scene.nodeBeginIndex() < this._currentScene.nodeBeginIndex()) {
            for (int i = scene.nodeBeginIndex(); i < this._currentScene.nodeBeginIndex(); i++) {
                this._time += this._nodes[i].getDuration();
            }
        } else if (scene.nodeBeginIndex() > this._currentScene.nodeBeginIndex()) {
            for (int i = this._currentScene.nodeBeginIndex(); i < scene.nodeBeginIndex(); i++) {
                this._time -= this._nodes[i].getDuration();
            }
        }

        // Clamp if beyond range of animation
        if (this._time > scene.duration()) {
            this._time = scene.duration();
        }

        // Assign
        this._currentScene = scene;
    }

    /**
     * Uses the animation options to define the scene being played.
     *
     * @param options Options
     * @return Scene to play
     */
    private Scene createScene(AnimationOptions options) {
        // Don't bother.
        if (this._nodes.length == 0 || !options.hasSceneOption()) {
            return this._entireAnimationScene;
        }

        // Single scene
        if (options.isSingleScene()) {
            return this._scenes.getOrDefault(options.getSceneBegin(), this._entireAnimationScene);
        }

        // Range of scenes, potentially
        int beginIndex = 0;
        int endIndex = this._nodes.length - 1;
        if (options.getSceneBegin() != null) {
            beginIndex = this._scenes.getOrDefault(options.getSceneBegin(), this._entireAnimationScene)
                    .nodeBeginIndex();
        }
        if (options.getSceneEnd() != null) {
            endIndex = this._scenes.getOrDefault(options.getSceneEnd(), this._entireAnimationScene)
                    .nodeEndIndex();
        }
        double duration = 0.0;
        for (int n = beginIndex; n <= endIndex; n++) {
            duration += this._nodes[n].getDuration();
        }
        return new Scene(beginIndex, endIndex, duration);
    }

    /**
     * Saves this animation to a configuration
     * 
     * @param config
     */
    public void saveToConfig(ConfigurationNode config) {
        this.getOptions().saveToConfig(config);

        List<String> nodes_str = new ArrayList<String>(this._nodes.length);
        for (AnimationNode node : this._nodes) {
            nodes_str.add(node.serializeToString());
        }
        config.set("nodes", nodes_str);
    }

    /**
     * Saves this animation as a new node of a parent configuration.
     * The name of the node is taken from this animation.
     * 
     * @param parentConfig
     */
    public void saveToParentConfig(ConfigurationNode parentConfig) {
        saveToConfig(parentConfig.getNode(this.getOptions().getName()));
    }

    /**
     * Loads an animation from configuration
     * 
     * @param config
     * @return animation
     */
    public static Animation loadFromConfig(ConfigurationNode config) {
        String name = config.getName();
        List<String> nodes_str = config.getList("nodes", String.class);
        Animation animation = new Animation(name, nodes_str);
        animation.getOptions().loadFromConfig(config);
        return animation;
    }

    /**
     * A single range of an animation to play
     */
    public static final class Scene {
        private final int _nodeBegin;
        private final int _nodeEnd;
        private final double _duration;

        public Scene(int nodeBegin, int nodeEnd, double duration) {
            this._nodeBegin = nodeBegin;
            this._nodeEnd = nodeEnd;
            this._duration = duration;
        }

        public int nodeBeginIndex() {
            return this._nodeBegin;
        }

        public int nodeEndIndex() {
            return this._nodeEnd;
        }

        public int nodeCount() {
            return this._nodeEnd - this._nodeBegin + 1;
        }

        public double duration() {
            return this._duration;
        }

        /**
         * Whether this scene is only a single frame. In that case no animation
         * is being played, and just this one frame is updated.
         *
         * @return True if this is a single-frame animation
         */
        public boolean isSingleFrame() {
            return this._nodeBegin == this._nodeEnd || this._duration <= 1e-20;
        }
    }
}
