package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentSelector;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.control.effect.MidiChartDialog;
import com.bergerkiller.bukkit.tc.attachments.control.effect.MidiScheduledEffectLoop;
import com.bergerkiller.bukkit.tc.attachments.control.effect.ScheduledEffectLoop;
import com.bergerkiller.bukkit.tc.attachments.control.effect.SimpleScheduledEffectLoop;
import com.bergerkiller.bukkit.tc.attachments.control.effect.SimpleScheduledEffectLoopDialog;
import com.bergerkiller.bukkit.tc.attachments.control.effect.midi.MidiChart;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A type of sequencer that can play an effect. These types can be selected
 * when assigning one to the start/loop/stop categories.
 */
public abstract class SequencerType {
    private static Map<String, SequencerType> types = new HashMap<>();
    private final String name;
    private final MapTexture iconDefault, iconFocus;

    // Default TrainCarts types
    public static final SequencerType SIMPLE = register(new SequencerType("Simple", MapWidgetSequencerEffect.Icon.SIMPLE) {
        @Override
        public void openConfigurationDialog(OpenDialogArguments args) {
            args.parent.addWidget(new SimpleScheduledEffectLoopDialog(args.config));
        }

        @Override
        public ScheduledEffectLoop createEffectLoop(ConfigurationNode config, Attachment.EffectSink effectSink) {
            SimpleScheduledEffectLoop effectLoop = new SimpleScheduledEffectLoop();
            effectLoop.setEffectSink(effectSink);
            effectLoop.setDelay(EffectLoop.Time.seconds(config.getOrDefault("delay", 0.0)));
            return effectLoop;
        }
    });
    public static final SequencerType MIDI = register(new SequencerType("MIDI", MapWidgetSequencerEffect.Icon.MIDI) {
        @Override
        public void openConfigurationDialog(final OpenDialogArguments args) {
            MidiChartDialog dialog = new MidiChartDialog() {
                @Override
                public void onChartChanged(MidiChart chart) {
                    args.config.setTo(chart.toYaml());
                }

                @Override
                public Attachment.EffectSink getEffectSink() {
                    return args.effectSink;
                }
            };
            dialog.setChart(MidiChart.fromYaml(args.config));
            dialog.setDuration(args.duration);
            args.parent.addWidget(dialog);
        }

        @Override
        public ScheduledEffectLoop createEffectLoop(ConfigurationNode config, Attachment.EffectSink effectSink) {
            MidiScheduledEffectLoop effectLoop = new MidiScheduledEffectLoop();
            effectLoop.setEffectSink(effectSink);
            effectLoop.setChart(MidiChart.fromYaml(config));
            return effectLoop;
        }
    });

    public SequencerType(String name, MapWidgetSequencerEffect.Icon icon) {
        this(name, icon.image(false), icon.image(true));
    }

    public SequencerType(String name, MapTexture iconDefault, MapTexture iconFocus) {
        this.name = name;
        this.iconDefault = iconDefault;
        this.iconFocus = iconFocus;
    }

    /**
     * This is called when the player activates the sequencer type specific configuration dialog
     *
     * @param args All arguments available for opening the dialog
     */
    public abstract void openConfigurationDialog(OpenDialogArguments args);

    /**
     * Creates a new ScheduledEffectLoop instance of this type of sequencer, loading it from the configuration
     * specified. The effects are played through the effect sink specified.
     *
     * @param config Configuration for this sequencer type
     * @param effectSink Effect Sink to control (instruments)
     * @return new EffectLoop
     */
    public abstract ScheduledEffectLoop createEffectLoop(ConfigurationNode config, Attachment.EffectSink effectSink);

    public String name() {
        return name;
    }

    public MapTexture icon(boolean focused) {
        return focused ? iconFocus : iconDefault;
    }

    public ConfigurationNode createConfig(AttachmentSelector<Attachment.EffectAttachment> effectSelector) {
        ConfigurationNode config = new ConfigurationNode();
        config.set("type", name());
        effectSelector.writeToConfig(config, "effect");
        config.getNode("config");
        return config;
    }

    public static List<SequencerType> all() {
        return types.values().stream()
                .sorted(Comparator.comparing(SequencerType::name))
                .collect(Collectors.toList());
    }

    public static SequencerType fromConfig(ConfigurationNode config) {
        String typeName = config.getOrDefault("type", String.class, null);
        if (typeName != null) {
            SequencerType type = byName(typeName);
            if (type != null) {
                return type;
            }
        }

        return SIMPLE; // Fallback
    }

    /**
     * Gets a registered SequencerType by its {@link #name()}.
     * Returns null if not found.
     *
     * @param name Name
     * @return SequencerType, or null if not found
     */
    public static SequencerType byName(String name) {
        SequencerType type = types.get(name);
        if (type == null) {
            type = types.get(name.toUpperCase(Locale.ENGLISH));
        }
        return type;
    }

    public static <T extends SequencerType> T register(T type) {
        types.put(type.name().toUpperCase(Locale.ENGLISH), type);
        return type;
    }

    public static void unregister(SequencerType type) {
        types.remove(type.name().toUpperCase(Locale.ENGLISH), type);
    }

    public static final class OpenDialogArguments {
        /** Parent widget on which the dialog should be added to open it */
        public final MapWidget parent;
        /** Configuration of this sequencer type */
        public final ConfigurationNode config;
        /** Total duration set for the effect to play */
        public final EffectLoop.Time duration;
        /** Effect Sink used for previewing the effect */
        public final Attachment.EffectSink effectSink;

        public OpenDialogArguments(
                final MapWidget parent,
                final ConfigurationNode config,
                final EffectLoop.Time duration,
                final Attachment.EffectSink effectSink
        ) {
            this.parent = parent;
            this.config = config;
            this.duration = duration;
            this.effectSink = effectSink;
        }
    }
}
