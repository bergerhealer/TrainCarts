package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.sound.MapWidgetSoundAutoResumeToggle;
import com.bergerkiller.bukkit.tc.attachments.control.sound.MapWidgetSoundPerspectiveMode;
import com.bergerkiller.bukkit.tc.attachments.control.sound.MapWidgetSoundPlayStop;
import com.bergerkiller.bukkit.tc.attachments.control.sound.MapWidgetSoundPositionMode;
import com.bergerkiller.bukkit.tc.attachments.control.sound.MapWidgetSoundSelector;
import com.bergerkiller.bukkit.tc.attachments.control.sound.MapWidgetSoundVolumePitch;
import com.bergerkiller.bukkit.tc.attachments.control.sound.SoundPerspectiveMode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutCustomSoundEffectHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutStopSoundHandle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays a sound when activated using animations or continuously as part of
 * a sound loop.
 */
public class CartAttachmentSound extends CartAttachment implements Attachment.EffectAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "SOUND";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/sound.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentSound();
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            final MapWidgetSoundSelector soundSelector = tab.addWidget(new MapWidgetSoundSelector() {
                @Override
                public void onAttached() {
                    setMode(attachment.getConfig().getOrDefault("perspectiveMode", SoundPerspectiveMode.SAME).getSoundMode());
                    setSoundPath(attachment.getConfig().getOrDefault("sound.key", String.class, null));
                    setCategory(attachment.getConfig().getOrDefault("sound.category", "master"));
                    super.onAttached();
                }

                @Override
                public void onSoundChanged(ResourceKey<SoundEffect> sound) {
                    attachment.getConfig().set("sound.key", (sound == null) ? null : sound.getPath());
                }

                @Override
                public void onCategoryChanged(String categoryName) {
                    attachment.getConfig().set("sound.category", categoryName);
                }
            }.setMode(MapWidgetSoundSelector.Mode.FIRST_PERSPECTIVE));
            soundSelector.setBounds(2, 18, 102, 11);

            final MapWidgetSoundSelector soundSelectorAlt = tab.addWidget(new MapWidgetSoundSelector() {
                @Override
                public void onAttached() {
                    setMode(attachment.getConfig().getOrDefault("perspectiveMode", SoundPerspectiveMode.SAME).getSoundAltMode());
                    setSoundPath(attachment.getConfig().getOrDefault("soundAlt.key", String.class, null));
                    setCategory(attachment.getConfig().getOrDefault("soundAlt.category", "master"));
                    super.onAttached();
                }

                @Override
                public void onSoundChanged(ResourceKey<SoundEffect> sound) {
                    attachment.getConfig().set("soundAlt.key", (sound == null) ? null : sound.getPath());
                }

                @Override
                public void onCategoryChanged(String categoryName) {
                    attachment.getConfig().set("soundAlt.category", categoryName);
                }
            }.setMode(MapWidgetSoundSelector.Mode.THIRD_PERSPECTIVE));
            soundSelectorAlt.setBounds(2, 32, 102, 11);

            tab.addWidget(new MapWidgetSoundPerspectiveMode() {
                @Override
                public void onAttached() {
                    setMode(attachment.getConfig().getOrDefault("perspectiveMode", SoundPerspectiveMode.SAME));
                    super.onAttached();
                }

                @Override
                public void onModeChanged(SoundPerspectiveMode newMode) {
                    attachment.getConfig().set("perspectiveMode", newMode);
                    soundSelector.setMode(newMode.getSoundMode());
                    soundSelectorAlt.setMode(newMode.getSoundAltMode());
                    for (MapWidget widget : tab.getWidgets()) {
                        if (widget instanceof MapWidgetSoundPositionMode) {
                            ((MapWidgetSoundPositionMode) widget).setIsSamePerspective(
                                    newMode == SoundPerspectiveMode.SAME);
                        }
                    }
                }
            }.setPosition(9, 3));

            tab.addWidget(new MapWidgetSoundAutoResumeToggle() {
                @Override
                public void onAttached() {
                    setAutoResume(attachment.getConfig().getOrDefault("autoResume", false));
                    super.onAttached();
                }

                @Override
                public void onAutoResumeChanged(boolean autoResume) {
                    attachment.getConfig().set("autoResume", autoResume);
                }
            }.setPosition(21, 3));

            tab.addWidget(new MapWidgetSoundPositionMode() {
                @Override
                public void onAttached() {
                    SoundPerspectiveMode perspective = attachment.getConfig().getOrDefault("perspectiveMode", SoundPerspectiveMode.SAME);
                    setIsSamePerspective(perspective == SoundPerspectiveMode.SAME);
                    setMode(attachment.getConfig().getOrDefault("sound.atPlayer", false),
                            attachment.getConfig().getOrDefault("soundAlt.atPlayer", false));
                    super.onAttached();
                }

                @Override
                public void onModeChanged(SoundPositionMode newMode) {
                    attachment.getConfig().set("sound.atPlayer", newMode.isAtPlayer1P());
                    attachment.getConfig().set("soundAlt.atPlayer", newMode.isAtPlayer3P());
                }
            }.setPosition(33, 3));

            tab.addWidget(new MapWidgetSoundPlayStop() {
                @Override
                public void onPlay() {
                    attachment.getAttachmentsOfType(CartAttachmentSound.class)
                            .forEach(a -> a.playEffect(EffectOptions.DEFAULT));
                }

                @Override
                public void onStop() {
                    attachment.getAttachmentsOfType(CartAttachmentSound.class)
                            .forEach(CartAttachmentSound::stopEffect);
                }
            }.setBounds(81, 3, 24, 11));

            tab.addWidget(new MapWidgetSoundVolumePitch() {
                @Override
                public void onAttached() {
                    setInitialBaseVolume(attachment.getConfig().getOrDefault("volume.base", 1.0f));
                    setInitialRandomVolume(attachment.getConfig().getOrDefault("volume.random", 0.0f));
                    setInitialBaseSpeed(attachment.getConfig().getOrDefault("pitch.base", 1.0f));
                    setInitialRandomSpeed(attachment.getConfig().getOrDefault("pitch.random", 0.0f));
                    super.onAttached();
                }

                @Override
                public void onChanged() {
                    if (getBaseVolume() == 1.0 && getRandomVolume() == 0.0) {
                        attachment.getConfig().remove("volume");
                    } else {
                        attachment.getConfig().set("volume.base", getBaseVolume());
                        attachment.getConfig().set("volume.random", getRandomVolume());
                    }

                    if (getBaseSpeed() == 1.0 && getRandomSpeed() == 0.0) {
                        attachment.getConfig().remove("pitch");
                    } else {
                        attachment.getConfig().set("pitch.base", getBaseSpeed());
                        attachment.getConfig().set("pitch.random", getRandomSpeed());
                    }
                }
            }).setBounds(-3, 47, 107, 30);
        }
    };

    private final SoundListeners listeners = new SoundListeners();
    private SoundConfiguration sound = SoundConfiguration.NO_CONFIG;

    @Override
    public void onLoad(ConfigurationNode config) {
        SoundConfiguration newSound = new SoundConfiguration(config);
        boolean refreshSounds = !SoundConfiguration.isSameSounds(sound, newSound);
        sound = newSound;
        listeners.updateListeners(this, sound, refreshSounds);
    }

    @Override
    public void makeVisible(Player viewer) {
        makeVisible(getManager().asAttachmentViewer(viewer));
    }

    @Override
    public void makeHidden(Player viewer) {
        makeHidden(getManager().asAttachmentViewer(viewer));
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        listeners.addListener(this, sound, viewer);
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        listeners.removeListener(this, sound, viewer);
    }

    @Override
    public void playEffect(EffectOptions effectOptions) {
        listeners.play(sound.createVolumePitch(effectOptions));
    }

    @Override
    public void stopEffect() {
        listeners.stop();
    }

    @Override
    public void onTick() {
        listeners.updateListeners(this, sound, false);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        listeners.updateLoc(transform, getManager().getWorld());
    }

    @Override
    public void onMove(boolean absolute) {
    }

    /**
     * This class stores all sound properties in an immutable fashion.
     * As playEffect() can be called asynchronously, this avoids issues that way.
     */
    private static class SoundConfiguration {
        public static final SoundConfiguration NO_CONFIG = new SoundConfiguration(new ConfigurationNode());
        public final SoundType sound;
        public final SoundType soundAlt;
        public final SoundPerspectiveMode perspectiveMode;
        public final boolean autoResume;
        public final VariableFloatRange volume, pitch;

        public SoundConfiguration(ConfigurationNode config) {
            sound = new SoundType(config.getNodeIfExists("sound"));
            soundAlt = new SoundType(config.getNodeIfExists("soundAlt"));
            if (SoundType.isSameSound(sound, soundAlt)) {
                perspectiveMode = SoundPerspectiveMode.SAME;
            } else {
                perspectiveMode = config.getOrDefault("perspectiveMode", SoundPerspectiveMode.SAME);
            }
            autoResume = config.getOrDefault("autoResume", false);
            volume = VariableFloatRange.decode(config.getNodeIfExists("volume"));
            pitch = VariableFloatRange.decode(config.getNodeIfExists("pitch"));
        }

        public SoundType sound(boolean isAlt) {
            return isAlt ? soundAlt : sound;
        }

        public VolumePitch createVolumePitch(EffectOptions effectOptions) {
            return new VolumePitch((float) (effectOptions.volume() * volume.next()),
                                   (float) (effectOptions.speed() * pitch.next()));
        }

        public static boolean isSameSounds(SoundConfiguration a, SoundConfiguration b) {
            return SoundType.isSameSound(a.sound, b.sound) &&
                   SoundType.isSameSound(a.soundAlt, b.soundAlt);
        }
    }

    private static class SoundType {
        private static final Random RANDOM_SEED_SOURCE = new Random();
        private static final boolean CAN_STOP_SOUND = Common.hasCapability("Common:Sound:StopSoundPacket");
        public final ResourceKey<SoundEffect> key;
        public final String category;
        public final boolean atPlayer;

        public SoundType(ConfigurationNode config) {
            if (config != null) {
                String keyPath = config.getOrDefault("key", String.class, null);
                this.key = (keyPath == null) ? null : SoundEffect.fromName(keyPath);
                this.category = config.getOrDefault("category", "master");
                this.atPlayer = config.getOrDefault("atPlayer", false);
            } else {
                this.key = null;
                this.category = "master";
                this.atPlayer = false;
            }
        }

        public boolean exists() {
            return key != null;
        }

        public void play(AttachmentViewer viewer, Location location, VolumePitch volumePitch) {
            if (key != null) {
                Location at = atPlayer ? viewer.getPlayer().getLocation() : location;

                // Note: must use this method because of a multithreading bug in BKCommonLibs location constructor
                //       can be removed when BKCommonLib 1.20.2-v2 or later is a hard-depend
                viewer.send(PacketPlayOutCustomSoundEffectHandle.createNew(key, category,
                        at.getX(), at.getY(), at.getZ(),
                        volumePitch.volume, volumePitch.pitch,
                        RANDOM_SEED_SOURCE.nextLong()));
            }
        }

        public void stop(AttachmentViewer viewer) {
            if (key != null && CAN_STOP_SOUND) {
                stopImpl(viewer);
            }
        }

        private void stopImpl(AttachmentViewer viewer) {
            viewer.send(PacketPlayOutStopSoundHandle.createNew(key, category));
        }

        public static boolean isSameSound(SoundType a, SoundType b) {
            return LogicUtil.bothNullOrEqual(a.key, b.key)
                    && a.category.equals(b.category)
                    && a.atPlayer == b.atPlayer;
        }
    }

    private static class SoundListener {
        public final AttachmentViewer viewer;
        public final AtomicBoolean isResumingPlay;
        public boolean isAlt;
        public SoundType sound;

        public SoundListener(AttachmentViewer viewer, boolean isAlt, SoundType sound) {
            this.viewer = viewer;
            this.isResumingPlay = new AtomicBoolean(false);
            this.isAlt = isAlt;
            this.sound = sound;
        }

        public void stop() {
            isResumingPlay.set(false);
            sound.stop(viewer);
        }

        public void play(Location location, VolumePitch volumePitch) {
            SoundType sound = this.sound;
            if (isResumingPlay.compareAndSet(true, false)) {
                sound.stop(viewer);
            }
            sound.play(viewer, location, volumePitch);
        }

        public void playResume(Location location, VolumePitch volumePitch) {
            isResumingPlay.set(true);
            sound.play(viewer, location, volumePitch);
        }
    }

    /**
     * Tracks a (synchronized) list of players that listen to sounds
     */
    private static class SoundListeners {
        private final List<SoundListener> listeners = new ArrayList<>();
        private VolumePitch lastVolumePitch = VolumePitch.SILENT;
        private Location loc = null;

        public synchronized void play(VolumePitch volumePitch) {
            lastVolumePitch = volumePitch;
            if (volumePitch.silent) {
                return;
            }
            Location loc = this.loc;
            if (loc != null) {
                for (SoundListener listener : listeners) {
                    listener.play(loc, volumePitch);
                }
            }
        }

        public synchronized void stop() {
            lastVolumePitch = VolumePitch.SILENT;
            listeners.forEach(SoundListener::stop);
        }

        public synchronized void addListener(CartAttachmentSound sound, SoundConfiguration config, AttachmentViewer viewer) {
            // Check not already contained
            for (SoundListener listener : listeners) {
                if (listener.viewer.equals(viewer)) {
                    return;
                }
            }

            // Detect alt mode
            boolean isAlt = detectIsAlt(sound, config, viewer);
            listeners.add(new SoundListener(viewer, isAlt, config.sound(isAlt)));
        }

        public synchronized void removeListener(CartAttachmentSound sound, SoundConfiguration config, AttachmentViewer viewer) {
            for (Iterator<SoundListener> iter = listeners.iterator(); iter.hasNext();) {
                SoundListener listener = iter.next();
                if (viewer.equals(listener.viewer)) {
                    iter.remove();
                    if (config.autoResume) {
                        listener.stop();
                    }
                    return;
                }
            }
        }

        public void updateLoc(Matrix4x4 transform, World world) {
            loc = transform.toLocation(world);
        }

        public void updateListeners(CartAttachmentSound sound, SoundConfiguration config, boolean forceRefreshSounds) {
            for (SoundListener listener : listeners) {
                boolean isAlt = detectIsAlt(sound, config, listener.viewer);
                if (forceRefreshSounds || isAlt != listener.isAlt) {
                    synchronized (this) {
                        if (config.autoResume) {
                            listener.stop();
                        }
                        listener.isAlt = isAlt;
                        listener.sound = config.sound(isAlt);

                        Location loc;
                        if (config.autoResume && !lastVolumePitch.silent && (loc = this.loc) != null) {
                            listener.playResume(loc, lastVolumePitch);
                        }
                    }
                }
            }
        }

        private boolean detectIsAlt(CartAttachmentSound sound, SoundConfiguration config, AttachmentViewer viewer) {
            if (config.perspectiveMode == SoundPerspectiveMode.SAME) {
                return false;
            }

            // Check viewer is part of a member at all
            MinecartMember<?> member = MinecartMemberStore.getFromEntity(viewer.getPlayer().getVehicle());
            if (member == null) {
                return true;
            }

            switch (config.perspectiveMode) {
                case CART:
                    return member != sound.getMember();
                case TRAIN:
                    return member.getGroup() != sound.getMember().getGroup();
                case SEAT:
                    if (member == sound.getMember()) {
                        // Check seat attachment parents of this attachment if it has this player
                        for (Attachment a = sound.getParent(); a != null; a = a.getParent()) {
                            if (a instanceof CartAttachmentSeat && ((CartAttachmentSeat) a).getEntity() == viewer.getPlayer()) {
                                return false; // Not alt, is 1p
                            }
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private static class VolumePitch {
        public static final VolumePitch SILENT = new VolumePitch(0.0f, 1.0f);
        public final float volume;
        public final float pitch;
        public final boolean silent;

        public VolumePitch(float volume, float pitch) {
            this.volume = volume;
            this.silent = volume < 1e-4f;
            this.pitch = pitch;
        }
    }

    @FunctionalInterface
    private interface VariableFloatRange {
        VariableFloatRange DEFAULT = () -> 1.0f;

        float next();

        static VariableFloatRange decode(ConfigurationNode node) {
            return (node == null) ? DEFAULT : get(node.getOrDefault("base", 1.0f),
                                                  node.getOrDefault("random", 0.0f));
        }

        static VariableFloatRange get(float base, float random) {
            if (random < 1e-4f) {
                return (Math.abs(base - 1.0f) < 1e-4f) ? DEFAULT : () -> base;
            } else {
                return new RandomFloat(base, random);
            }
        }
    }

    private static class RandomFloat implements VariableFloatRange {
        private final Random random = new Random();
        private final float base, mult;

        public RandomFloat(float base, float mult) {
            this.base = base - mult;
            this.mult = 2.0f * mult;
        }

        @Override
        public float next() {
            return base + random.nextFloat(mult);
        }
    }
}
