package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * An animation consisting of key frame nodes with time-domain transformations.
 * Class can be inherited overriding {@link #update(dt)} returning a custom position for animations
 * controlled by external input.
 */
public class Animation implements Cloneable {
    private AnimationOptions _options;
    private final AnimationNode[] _nodes;
    private final double _loopDuration;
    private double _time;
    private boolean _reachedEnd;

    protected Animation(Animation source) {
        this._options = source._options.clone();
        this._nodes = source._nodes;
        this._loopDuration = source._loopDuration;
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

        // Calculate loop duration from the nodes
        {
            double total = 0.0;
            for (AnimationNode node : nodes) {
                total += node.getDuration();
            }
            this._loopDuration = total;
        }
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
            this._time = this._loopDuration;
            if (this._nodes.length >= 1) {
                this._time -= this._nodes[this._nodes.length - 1].getDuration();
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

        boolean animationStarted = true;
        if (this._options.isLooped()) {
            // When animation is too short, always return node 0.
            if (this._nodes.length == 1 || this._loopDuration <= 1e-20) {
                this._reachedEnd = true;
                return this._nodes[0];
            }

        } else {

            AnimationNode endNode = this._nodes[this._nodes.length - 1];
            double animEnd = this._loopDuration - endNode.getDuration();

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
            this._time = (this._time % this._loopDuration);
            if (this._time < 0.0) {
                this._time += this._loopDuration; // nega
            }
        }

        // Only 1 node? Return that, no weird interpolation please.
        if (this._nodes.length == 1) {
            this._reachedEnd = true;
            return this._nodes[0];
        }

        // Interpolate to find the correct animation node
        int nodes_cnt = this._nodes.length;
        for (int i = 0; i < nodes_cnt; i++) {
            AnimationNode node = this._nodes[i];
            double duration = node.getDuration();
            if (curr_time > duration) {
                curr_time -= duration;
                continue;
            }

            int next_i = i + 1;
            if (next_i == nodes_cnt) {
                next_i = 0;
            }
            return AnimationNode.interpolate(this._nodes[i], this._nodes[next_i], curr_time/duration);
        }

        // Should never be reached
        return this._nodes[nodes_cnt - 1];
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
}
