package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.controller.Tickable;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.control.effect.ScheduledEffectLoop;
import com.bergerkiller.bukkit.tc.attachments.control.sequencer.MapWidgetSequencerEffectGroupList;
import com.bergerkiller.bukkit.tc.attachments.control.sequencer.SequencerMode;
import com.bergerkiller.bukkit.tc.attachments.control.sequencer.SequencerType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionBoolean;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionConstant;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionRegistry;
import com.bergerkiller.bukkit.tc.controller.functions.inputs.TransferFunctionInput;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plays a sequence of effects in a loop. Supports a mechanism for a pre-loop start and post-loop stop
 * sequence, as well as using the {@link TransferFunction} API to automate various parameters.
 */
public class CartAttachmentSequencer extends CartAttachment implements Attachment.EffectAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "SEQUENCER";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/sequencer.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentSequencer();
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            final TransferFunctionHost host = new TransferFunctionHost() {
                @Override
                public TransferFunctionRegistry getRegistry() {
                    return TransferFunction.getRegistry();
                }

                @Override
                public TransferFunctionInput.ReferencedSource registerInputSource(TransferFunctionInput.ReferencedSource source) {
                    // While editing it is not too important where the information comes from
                    return source;
                }

                @Override
                public boolean isSequencer() {
                    return true;
                }

                @Override
                public MinecartMember<?> getMember() {
                    return null;
                }

                @Override
                public Attachment getAttachment() {
                    return null;
                }

                @Override
                public TrainCarts getTrainCarts() {
                    return TrainCarts.plugin;
                }
            };

            tab.addWidget(new MapWidgetSequencerEffectGroupList() {
                @Override
                public ConfigurationNode getConfig() {
                    return attachment.getConfig();
                }

                @Override
                public List<String> getEffectNames() {
                    return attachment.getAttachmentConfig().liveAttachmentsOfType(CartAttachmentSequencer.class).stream()
                            .flatMap(s -> s.findAllEffectAttachments().stream())
                            .flatMap(e -> e.getNames().stream())
                            .sorted()
                            .distinct()
                            .collect(Collectors.toList());
                }

                @Override
                public TransferFunctionHost getTransferFunctionHost() {
                    return host;
                }

                @Override
                public Attachment.EffectSink createEffectSink(String name) {
                    List<AttachmentNameLookup.NameGroup<EffectAttachment>> nameGroups = new ArrayList<>();
                    for (CartAttachmentSequencer sequencer : attachment.getAttachmentsOfType(CartAttachmentSequencer.class)) {
                        nameGroups.add(AttachmentNameLookup.NameGroup.of(sequencer, name, EffectAttachment.class));
                    }
                    return EffectSink.combineEffects(nameGroups);
                }
            }).setBounds(-5, 0, 110, 70);
        }
    };

    private static final int STATE_NOT_PLAYING = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_STOP_REQUESTED = 2;

    private Attachment rootParent = this;
    private final SequencerTransferFunctionHost functionHost = new SequencerTransferFunctionHost();
    private final EnumMap<SequencerMode, SequencerGroup> sequencerGroups;
    private SequencerGroup currentGroup;
    private final AtomicInteger playState = new AtomicInteger(STATE_NOT_PLAYING);
    private EffectOptions playOptions = EffectOptions.DEFAULT;

    public CartAttachmentSequencer() {
        sequencerGroups = new EnumMap<>(SequencerMode.class);
        for (SequencerMode mode : SequencerMode.values()) {
            sequencerGroups.put(mode, new SequencerGroup(this, mode));
        }
        currentGroup = sequencerGroups.get(SequencerMode.START);
    }

    @Override
    public void onAttached() {
        Attachment rootParent = this;
        while (rootParent.getParent() != null) {
            rootParent = rootParent.getParent();
        }
        this.rootParent = rootParent;
    }

    @Override
    public void onLoad(ConfigurationNode config) {
        for (SequencerMode mode : SequencerMode.values()) {
            sequencerGroups.get(mode).load(config.getNodeIfExists(mode.configKey()));
        }
    }

    /**
     * Gets the current play options of last {@link #playEffect(EffectOptions)} invocation
     *
     * @return Play Options
     */
    public EffectOptions getCurrentPlayOptions() {
        return playOptions;
    }

    /**
     * Gets how far along playback has progressed. 0.0 is the beginning, 1.0 is the end.
     *
     * @return Play progression
     */
    public double getProgression() {
        return Math.min(1.0, (double) currentGroup.nanosElapsed / (double) currentGroup.duration.nanos);
    }

    @Override
    public void playEffect(EffectOptions options) {
        playOptions = options;
        int prevState = playState.getAndSet(STATE_PLAYING);
        if (prevState == STATE_NOT_PLAYING) {
            // Schedule an EffectLoop to be played, updating this attachment
            TrainCarts.plugin.getEffectLoopPlayerController().createPlayer().play(new EffectLoop() {
                @Override
                public boolean advance(Time dt, Time duration, boolean loop) {
                    // If a stop was requested, process the stop sequence
                    // Once all groups have played, clean up the effect loop
                    int currState = playState.get();
                    if (currState == STATE_NOT_PLAYING) {
                        return false; // Bug?
                    } else if (currState == STATE_STOP_REQUESTED) {
                        while (true) {
                            // Advance current group. Interrupt non-STOP groups.
                            dt = currentGroup.advance(dt, currentGroup.mode() != SequencerMode.STOP);
                            if (dt.isZero()) {
                                return true; // Not done yet with this group
                            }

                            // Advance to next group. If this is the STOP group, shut down the effect loop
                            // It's possible that during this time play() was called again, in which case it restarts
                            if (currentGroup.mode() == SequencerMode.STOP) {
                                return !playState.compareAndSet(STATE_STOP_REQUESTED, STATE_NOT_PLAYING);
                            }

                            // Next group (start/loop -> stop)
                            currentGroup = sequencerGroups.get(SequencerMode.STOP);
                            currentGroup.resetToBeginning();
                        }
                    }

                    // Playing state
                    while (true) {
                        // Advance current group. Interrupt STOP groups.
                        dt = currentGroup.advance(dt, currentGroup.mode() == SequencerMode.STOP);
                        if (dt.isZero()) {
                            return true; // Not done yet with this group (or is LOOP)
                        }

                        // Go from STOP -> START, and from START -> LOOP
                        if (currentGroup.mode() == SequencerMode.STOP) {
                            currentGroup = sequencerGroups.get(SequencerMode.START);
                        } else {
                            currentGroup = sequencerGroups.get(SequencerMode.LOOP);
                        }
                        currentGroup.resetToBeginning();
                    }
                }
            });
        }
    }

    @Override
    public void stopEffect() {
        playState.set(STATE_STOP_REQUESTED);
    }

    @Override
    public void makeVisible(Player viewer) {
    }

    @Override
    public void makeHidden(Player viewer) {
    }

    @Override
    public void onTick() {
        functionHost.sources.removeIf(s -> {
            if (s.hasRecipients()) {
                if (!s.isTickedDuringPlay()) {
                    s.onTick();
                }
                return false;
            } else {
                functionHost.onSourceRemoved(s);
                return true; // Remove
            }
        });
        sequencerGroups.values().forEach(SequencerGroup::onTick);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        functionHost.sources.forEach(s -> s.onTransform(transform));
    }

    @Override
    public void onMove(boolean absolute) {
    }

    private AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> findEffectAttachments(String name) {
        if (name.isEmpty()) {
            return AttachmentNameLookup.NameGroup.none();
        } else {
            //TODO: Option for all of cart / attachments below sequencer
            return AttachmentNameLookup.NameGroup.of(rootParent, name, EffectAttachment.class);
        }
    }

    private List<EffectAttachment> findAllEffectAttachments() {
        //TODO: Option for all of cart / attachments below sequencer
        List<EffectAttachment> result = new ArrayList<>();
        fillChildEffectAttachments(result, rootParent);
        return result;
    }

    private void fillChildEffectAttachments(List<EffectAttachment> result, Attachment root) {
        if (root instanceof EffectAttachment && root != this) {
            result.add((EffectAttachment) root);
        }
        for (Attachment child : root.getChildren()) {
            fillChildEffectAttachments(result, child);
        }
    }

    public class SequencerTransferFunctionHost implements TransferFunctionHost {
        private final List<TransferFunctionInput.ReferencedSource> sources = new ArrayList<>();
        private List<TransferFunctionInput.ReferencedSource> sourcesTickedDuringPlay = Collections.emptyList();

        @Override
        public TrainCarts getTrainCarts() {
            return TrainCarts.plugin;
        }

        @Override
        public TransferFunctionRegistry getRegistry() {
            return TransferFunction.getRegistry();
        }

        public void tickPlaySources() {
            sourcesTickedDuringPlay.forEach(TransferFunctionInput.ReferencedSource::onTick);
        }

        public void onSourceRemoved(TransferFunctionInput.ReferencedSource source) {
            int idx = sourcesTickedDuringPlay.indexOf(source);
            if (idx != -1) {
                List<TransferFunctionInput.ReferencedSource> newList = new ArrayList<>(sourcesTickedDuringPlay);
                newList.remove(idx);
                sourcesTickedDuringPlay = newList;
            }
        }

        @Override
        public TransferFunctionInput.ReferencedSource registerInputSource(TransferFunctionInput.ReferencedSource source) {
            int index = sources.indexOf(source);
            if (index == -1) {
                sources.add(source);
                if (source.isTickedDuringPlay()) {
                    List<TransferFunctionInput.ReferencedSource> newList = new ArrayList<>(sourcesTickedDuringPlay);
                    newList.add(source);
                    sourcesTickedDuringPlay = newList;
                }
                return source;
            } else {
                return sources.get(index);
            }
        }

        @Override
        public boolean isSequencer() {
            return true;
        }

        @Override
        public Attachment getAttachment() {
            return CartAttachmentSequencer.this;
        }

        @Override
        public MinecartMember<?> getMember() {
            return CartAttachmentSequencer.this.getMember();
        }
    }

    /**
     * A single group category of effects played simultaneously.
     * One of start/loop/stop modes.
     */
    public static class SequencerGroup implements Tickable {
        private final ConfigLoadedValue<TransferFunction> speedFunction = new ConfigLoadedValue<>(TransferFunctionConstant.of(1.0));
        private final CartAttachmentSequencer sequencer;
        private final SequencerMode mode;
        private EffectLoop.Time duration = EffectLoop.Time.ZERO;
        private long nanosElapsed = 0;
        private boolean interruptPlay = false;
        private final Map<ConfigurationNode, SequencerEffect> effectsByConfig = new IdentityHashMap<>();
        private List<SequencerEffect> effects = Collections.emptyList();

        public SequencerGroup(CartAttachmentSequencer sequencer, SequencerMode mode) {
            this.sequencer = sequencer;
            this.mode = mode;
        }

        public SequencerMode mode() {
            return mode;
        }

        public void load(ConfigurationNode config) {
            // Reset if there is no configuration
            if (config == null || config.isEmpty()) {
                speedFunction.reset();
                duration = EffectLoop.Time.ZERO;
                interruptPlay = false;
                nanosElapsed = 0;
                effects.forEach(SequencerEffect::onRemoved);
                effects = Collections.emptyList();
                effectsByConfig.clear();
                return;
            }

            // Speed
            speedFunction.load(config.getNodeIfExists("speed"), sequencer.functionHost::loadFunction);

            // Duration
            duration = EffectLoop.Time.seconds(Math.max(0.0, config.getOrDefault("duration", 0.0)));

            // Interrupt Playback
            interruptPlay = config.getOrDefault("interrupt", false);

            // Effects
            List<ConfigurationNode> effectConfigs;
            if (duration.isZero() || (effectConfigs = config.getNodeList("effects")).isEmpty()) {
                effects = Collections.emptyList();
            } else {
                List<SequencerEffect> newEffects = new ArrayList<>(effectConfigs.size());
                for (ConfigurationNode effectConfig : effectConfigs) {
                    // Find an already existing sequencer effect that uses the same config block instance
                    // This makes reloading more efficient if there's no changes.
                    // If not found, create a new one.
                    SequencerEffect effect = effectsByConfig.remove(effectConfig);
                    if (effect == null) {
                        effect = new SequencerEffect();
                    }

                    // Load it
                    effect.load(sequencer, effectConfig);

                    // Add
                    newEffects.add(effect);
                }

                // Items remaining in the effectsByConfig mapping have been removed
                effectsByConfig.values().forEach(SequencerEffect::onRemoved);
                effectsByConfig.clear();

                // Store in by-config mapping
                for (int i = 0; i < newEffects.size(); i++) {
                    effectsByConfig.put(effectConfigs.get(i), newEffects.get(i));
                }

                // Store loaded result
                effects = newEffects;
            }
        }

        @Override
        public void onTick() {
            effects.forEach(SequencerEffect::onTick);
        }

        /**
         * Advances this SequencerGroup. Returns time zero if this is still being played.
         * If non-zero is returned, it indicates the next sequencer group should be played
         * and this one is done.
         *
         * @param dt Delta time to advance
         * @param stopRequested Whether stopping was requested. Stops looping if looped,
         *                      returns instantly if interrupt play is enabled.
         * @return Amount of delta time remaining to be played. If non-zero, it indicates
         *         this sequencer group has finished playing.
         */
        public EffectLoop.Time advance(EffectLoop.Time dt, boolean stopRequested) {
            long durationNanos = duration.nanos;

            // If stopping is instant, return dt to move on
            if (stopRequested && interruptPlay) {
                return dt;
            }

            // If duration is zero, simply do nothing
            // In the case of loop, pretend to be doing things while actually running a no-op
            // If looping and stop is requested, get out of this catatonic state immediately
            if (durationNanos == 0L) {
                return (stopRequested || mode != SequencerMode.LOOP) ? dt : EffectLoop.Time.ZERO;
            }

            // Update transfer functions
            sequencer.functionHost.tickPlaySources();
            double speed = speedFunction.get().map(0.0);
            effects.forEach(SequencerEffect::updateEffectLoop);

            // If speed is zero or less, playback is paused. Simply return.
            if (speed <= 1e-6) {
                return EffectLoop.Time.ZERO;
            }

            // Adjust dt based on playback speed
            EffectLoop.Time dt_adjusted = (speed == 1.0) ? dt : dt.multiply(speed);
            long prev_time_nanos = this.nanosElapsed;
            long curr_time_nanos = prev_time_nanos + dt_adjusted.nanos;

            // Advance up until the loop point
            if (curr_time_nanos <= durationNanos) {
                // Have not reached the end duration yet, so continue playing it
                advanceAllEffects(prev_time_nanos, curr_time_nanos);
                return EffectLoop.Time.ZERO;
            }

            // Loop is active, play the final bit of the sequence and loop back around
            // Because it has looped it will continue playing indefinitely until somebody
            // higher up the chain aborts playback.
            if (mode == SequencerMode.LOOP && !stopRequested) {
                long remainder = curr_time_nanos - durationNanos;
                if (remainder >= durationNanos) {
                    remainder %= durationNanos;
                }
                advanceAllEffects(prev_time_nanos, durationNanos);
                advanceAllEffects(0, remainder);
                return EffectLoop.Time.ZERO;
            }

            // Reached the end of playback. Play any remainder amount of time.
            advanceAllEffects(prev_time_nanos, durationNanos);

            // Compute the delta time remaining that hasn't been played
            // Should be at least 1 so that we're sure this effect loop isn't played again
            return EffectLoop.Time.nanos(Math.max(1, (long) ((curr_time_nanos - durationNanos) / speed)));
        }

        private void advanceAllEffects(long prevNanos, long currNanos) {
            effects.forEach(e -> e.effectLoop.get().advance(prevNanos, currNanos));
            nanosElapsed = currNanos;
        }

        public void resetToBeginning() {
            nanosElapsed = 0;
        }
    }

    /**
     * A single effect part of a group
     */
    public static class SequencerEffect implements EffectSink, Tickable {
        private AttachmentNameLookup.NameGroup<Attachment.EffectAttachment> effectAttachments = AttachmentNameLookup.NameGroup.none();
        private final ConfigLoadedValue<TransferFunction> activeFunction = new ConfigLoadedValue<>(TransferFunctionBoolean.TRUE);
        private final ConfigLoadedValue<TransferFunction> volumeFunction = new ConfigLoadedValue<>(TransferFunctionConstant.of(1.0));
        private final ConfigLoadedValue<TransferFunction> pitchFunction = new ConfigLoadedValue<>(TransferFunctionConstant.of(1.0));
        private final ConfigLoadedValue<ScheduledEffectLoop> effectLoop = new ConfigLoadedValue<>(ScheduledEffectLoop.NONE);
        private SequencerType sequencerType = null;
        private boolean active;
        private double volume, pitch;

        public SequencerEffect() {
        }

        public void onRemoved() {

        }

        @Override
        public void onTick() {
            effectAttachments.sync();
        }

        public void updateEffectLoop() {
            active = activeFunction.get().map(0.0) != 0.0;
            volume = volumeFunction.get().map(0.0);
            pitch = pitchFunction.get().map(0.0);
        }

        @Override
        public void playEffect(EffectOptions options) {
            if (active) {
                final EffectOptions adjusted = (volume != 1.0 || pitch != 1.0)
                        ? options.multiply(volume, pitch) : options;
                effectAttachments.forEach(e -> e.playEffect(adjusted));
            }
        }

        @Override
        public void stopEffect() {
            if (active) {
                effectAttachments.forEach(EffectAttachment::stopEffect);
            }
        }

        public void load(CartAttachmentSequencer sequencer, ConfigurationNode config) {
            // Effect name to play
            effectAttachments = sequencer.findEffectAttachments(config.getOrDefault("effect", ""));

            // Active
            activeFunction.load(config.getNodeIfExists("active"), sequencer.functionHost::loadFunction);

            // Volume
            volumeFunction.load(config.getNodeIfExists("volume"), sequencer.functionHost::loadFunction);

            // Pitch
            pitchFunction.load(config.getNodeIfExists("pitch"), sequencer.functionHost::loadFunction);

            // Sequencer Type + Load configuration for it into an EffectLoop
            {
                SequencerType newType = SequencerType.byName(config.getOrDefault("type", ""));
                if (sequencerType != newType) {
                    sequencerType = newType;
                    effectLoop.forceLoad(config.getNodeIfExists("config"), c -> sequencerType.createEffectLoop(c, this));
                } else {
                    effectLoop.load(config.getNodeIfExists("config"), c -> sequencerType.createEffectLoop(c, this));
                }
            }
        }
    }

    /**
     * Loads a Value from a Configuration block. Tracks the previously decoded value,
     * so that future changes don't reload it when it hasn't changed.
     *
     * @param <T> Value type
     */
    private static class ConfigLoadedValue<T> {
        private final T defaultValue;
        private ConfigurationNode previousConfig = null;
        private T value;

        public ConfigLoadedValue(T defaultValue) {
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        public void reset() {
            previousConfig = null;
            value = defaultValue;
        }

        public void forceLoad(ConfigurationNode config, Function<ConfigurationNode, T> loader) {
            if (config != null) {
                previousConfig = config.clone();
                value = loader.apply(config);
            } else {
                reset();
            }
        }

        public void load(ConfigurationNode config, Function<ConfigurationNode, T> loader) {
            if (previousConfig == null) {
                if (config != null) {
                    forceLoad(config, loader);
                }
            } else if (config == null || !previousConfig.equals(config)) {
                forceLoad(config, loader);
            }
        }
    }
}
