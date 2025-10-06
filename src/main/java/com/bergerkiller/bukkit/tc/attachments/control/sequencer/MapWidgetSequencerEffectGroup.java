package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentSelector;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentSelector;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionSingleConfigItem;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionConstant;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Each of the three start/loop/stop header + assigned effect loops.
 * Shows the category at the top, as well as the duration assigned to it.
 * Disables itself if a duration of 0 is specified, hiding assigned
 * effect loops in that case.
 */
public class MapWidgetSequencerEffectGroup extends MapWidget {
    protected static final byte BACKGROUND_COLOR = MapColorPalette.getColor(54, 81, 114);
    private static final NumberFormat DURATION_FORMAT = Util.createNumberFormat(1, 4);
    private static final ConfigurationNode EMPTY_CONFIG = new ConfigurationNode();
    private static final int TOP_HEADER_HEIGHT = 8;
    private final MapWidgetSequencerConfigurationMenu menu;
    private final SequencerMode mode;
    private final List<MapWidgetSequencerEffect> effects = new ArrayList<>();
    private Header header;
    private HeaderTitle headerTitle;
    private HeaderButton configureButton, addEffectButton;
    private EffectLoop.Time duration;

    public MapWidgetSequencerEffectGroup(MapWidgetSequencerConfigurationMenu menu, SequencerMode mode) {
        this.menu = menu;
        this.mode = mode;
        this.duration = EffectLoop.Time.seconds(readConfig().getOrDefault("duration", 0.0));
        this.setClipParent(true);
        this.updateBounds();

        for (ConfigurationNode effectConfig : readConfig().getNodeList("effects")) {
            addEffect(new MapWidgetSequencerEffect(effectConfig));
        }
    }

    protected ConfigurationNode readConfig() {
        ConfigurationNode config = menu.getConfig().getNodeIfExists(mode.configKey());
        return (config == null) ? EMPTY_CONFIG : config;
    }

    protected ConfigurationNode writeConfig() {
        return menu.getConfig().getNode(mode.configKey());
    }

    /**
     * Adds an effect to this group of effects
     *
     * @param effect Effect to add
     * @return this
     */
    public MapWidgetSequencerEffectGroup addEffect(MapWidgetSequencerEffect effect) {
        this.effects.add(effect);
        if (!effect.getConfig().hasParent()) {
            this.writeConfig().getNodeList("effects", false).add(effect.getConfig());
        }
        if (!this.duration.isZero()) {
            addEffectWidget(effect);
        }
        if (this.effects.size() == 1 && header != null) {
            header.invalidate();
        }
        this.updateBounds();
        return this;
    }

    /**
     * Removes a previously added effect from this group
     *
     * @param effect Effect to remove
     * @return this
     */
    public MapWidgetSequencerEffectGroup removeEffect(MapWidgetSequencerEffect effect) {
        int effectIndex = this.effects.indexOf(effect);
        if (effectIndex != -1) {
            boolean wasFocused = effect.isFocused();
            this.effects.remove(effectIndex);
            effect.getConfig().remove(); // Remove from effects node list

            if (!this.duration.isZero()) {
                this.removeWidget(effect);
                for (int i = 0; i < effects.size(); i++) {
                    effects.get(i).setBounds(0, TOP_HEADER_HEIGHT + ((MapWidgetSequencerEffect.HEIGHT - 1) * i),
                            getWidth(), MapWidgetSequencerEffect.HEIGHT);
                }
                this.updateBounds();
                if (wasFocused) {
                    if (effectIndex >= this.effects.size()) {
                        effectIndex = this.effects.size() - 1;
                    }
                    if (effectIndex != -1) {
                        this.effects.get(effectIndex).focus();
                    } else {
                        this.activate();
                    }
                }
            }
        }
        return this;
    }

    /**
     * Updates the duration set for this group of sequencer effects.
     * If set to 0.0 (or less), disables these effects.
     *
     * @param duration Duration in seconds
     * @return this
     */
    public MapWidgetSequencerEffectGroup setDuration(double duration) {
        return setDuration(EffectLoop.Time.seconds(Math.max(0.0, duration)));
    }

    /**
     * Updates the duration set for this group of sequencer effects.
     * If set to 0, disables these effects.
     *
     * @param duration Time duration
     * @return this
     */
    public MapWidgetSequencerEffectGroup setDuration(EffectLoop.Time duration) {
        if (!this.duration.equals(duration)) {
            boolean effectsVisibleChanged = (this.duration.isZero() != duration.isZero());
            this.duration = duration;
            this.writeConfig().set("duration", duration.isZero()? null : duration.seconds);
            if (effectsVisibleChanged) {
                if (addEffectButton != null) {
                    addEffectButton.setEnabled(!duration.isZero());
                }

                // Remove (or re-add) all the effects
                for (MapWidgetSequencerEffect effect : this.effects) {
                    this.removeWidget(effect);
                }
                if (!duration.isZero()) {
                    this.effects.forEach(this::addEffectWidget);
                }

                if (header != null) {
                    header.invalidate();
                }

                updateBounds();
            }
            if (headerTitle != null) {
                headerTitle.invalidate();
            }
        }
        return this;
    }

    public EffectLoop.Time getDuration() {
        return duration;
    }

    private void addEffectWidget(MapWidgetSequencerEffect effect) {
        int index = this.effects.indexOf(effect);
        effect.setBounds(0, TOP_HEADER_HEIGHT + (MapWidgetSequencerEffect.HEIGHT - 1) * index,
                getWidth(), MapWidgetSequencerEffect.HEIGHT);
        this.addWidget(effect);
    }

    private void updateBounds() {
        int newHeight = TOP_HEADER_HEIGHT + ((duration.isZero() || effects.isEmpty())
                ? 0 : (effects.size() * (MapWidgetSequencerEffect.HEIGHT - 1) + 1));
        boolean heightChanged = (this.getHeight() != newHeight);
        this.setBounds(0, getY(), menu.getWidth(), newHeight);
        if (heightChanged) {
            menu.recalculateContainerSize();
        }
    }

    @Override
    public void onAttached() {
        header = addWidget(new Header());
        header.setBounds(0, 0, getWidth(), 8);

        headerTitle = header.addWidget(new HeaderTitle());
        headerTitle.setBounds(0, 0, getWidth() - 43, 7);

        configureButton = header.addWidget(new HeaderButton(MapWidgetSequencerEffect.HeaderIcon.CONFIGURE) {
            @Override
            public void onActivate() {
                display.playSound(SoundEffect.PISTON_EXTEND);
                menu.addWidget(new ConfigureDialog());
            }
        });
        configureButton.setPosition(getWidth() - 43, 0);

        //TODO: Ugly indentation
        addEffectButton = header.addWidget(new HeaderButton(MapWidgetSequencerEffect.HeaderIcon.ADD) {
            @Override
            public void onActivate() {
                // Ask what effect to target
                display.playSound(SoundEffect.PISTON_EXTEND);
                menu.addWidget(new MapWidgetAttachmentSelector<Attachment.EffectAttachment>(
                        AttachmentSelector.all(Attachment.EffectAttachment.class).excludingSelf()
                ) {
                    @Override
                    public List<String> getAttachmentNames(AttachmentSelector<Attachment.EffectAttachment> allSelector) {
                        return menu.getEffectNames(allSelector);
                    }

                    @Override
                    public void onSelected(AttachmentSelector<Attachment.EffectAttachment> effectSelector) {
                        // Ask what type of effect to add
                        menu.addWidget(new MapWidgetSequencerTypeSelector() {
                            @Override
                            public void onSelected(SequencerType type) {
                                menu.effectSelButtonIndex = 0;
                                addEffect((new MapWidgetSequencerEffect(type, effectSelector)).focusOnActivate());
                            }
                        });
                    }
                }.setTitle("Set Effect to play"));
            }
        });
        addEffectButton.setEnabled(!duration.isZero());
        addEffectButton.setPosition(getWidth() - 7, 0);
    }

    private class ConfigureDialog extends MapWidgetMenu {

        public ConfigureDialog() {
            setPositionAbsolute(true);
            setBounds(14, 30, 100, 78);
            setBackgroundColor(MapColorPalette.getColor(72, 108, 152));
            labelColor = MapColorPalette.COLOR_BLACK;
        }

        @Override
        public void onAttached() {
            addLabel(5, 6, "Duration (s):");
            addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    setRange(0.0, 100000.0);
                    setIncrement(0.01);
                    setInitialValue(duration.seconds);
                    setTextOverride(duration.isZero() ? "Off" : null);
                    super.onAttached();
                }

                @Override
                public void onValueChanged() {
                    setDuration(getValue());
                    setTextOverride(getValue() > 0.0 ? null : "Off");
                }
            }).setBounds(5, 13, 66, 11);

            addLabel(5,  27, "Playback Speed:");
            addWidget(new MapWidgetTransferFunctionSingleConfigItem(menu.getTransferFunctionHost(), writeConfig(), "speed", () -> false) {
                @Override
                public TransferFunction createDefault() {
                    return TransferFunctionConstant.of(1.0);
                }
            }).setBounds(5, 34, getWidth() - 10, MapWidgetTransferFunctionItem.HEIGHT);

            addLabel(5, 53, "Interrupt Play:");
            addWidget(new MapWidgetButton() {
                @Override
                public void onAttached() {
                    updateText();
                    super.onAttached();
                }

                @Override
                public void onActivate() {
                    writeConfig().set("interrupt", !readConfig().getOrDefault("interrupt", false));
                    updateText();
                }

                private void updateText() {
                    setText(readConfig().getOrDefault("interrupt", false) ? "Yes" : "No");
                }
            }).setBounds(5, 60, 52, 12);

            super.onAttached();
        }
    }

    private class Header extends MapWidget {
        public Header() {
            this.setClipParent(true);
        }

        @Override
        public void onDraw() {
            if (duration.isZero() || effects.isEmpty()) {
                // No effects configured, show it as an isolated bubble
                view.fillRectangle(2, 0, getWidth() - 2, getHeight() - 1, BACKGROUND_COLOR);
                view.drawLine(1, 1, 1, getHeight() - 3, BACKGROUND_COLOR);
            } else {
                // Fill with the background color, but take out a notch top-left to give it a "tab" appearance
                view.fillRectangle(2, 0, getWidth() - 2, getHeight(), BACKGROUND_COLOR);
                view.drawLine(1, 1, 1, getHeight() - 1, BACKGROUND_COLOR);
                view.drawPixel(0, getHeight() - 1, BACKGROUND_COLOR);
            }
        }
    }

    private class HeaderTitle extends MapWidget {

        public HeaderTitle() {
            this.setClipParent(true);
        }

        @Override
        public void onDraw() {
            byte textColor = duration.isZero()
                    ? MapColorPalette.getColor(72, 108, 152)
                    : MapColorPalette.getColor(213, 201, 140);
            view.draw(mode.icon(), 2, 1, textColor);
            view.draw(MapFont.TINY, 11, 1, textColor, mode.title());
            view.draw(MapFont.TINY, 37, 1, textColor,
                    duration.isZero() ? "[OFF]"
                                      : (DURATION_FORMAT.format(duration.seconds) + "s"));
        }
    }

    /**
     * The configure / add effect buttons
     */
    private static abstract class HeaderButton extends MapWidget {
        private final MapWidgetSequencerEffect.HeaderIcon icon;

        public HeaderButton(MapWidgetSequencerEffect.HeaderIcon icon) {
            this.setFocusable(true);
            this.setClipParent(true);
            this.setSize(icon.getWidth(), icon.getHeight());
            this.icon = icon;
        }

        @Override
        public abstract void onActivate();

        @Override
        public void onDraw() {
            view.draw(icon.getIcon(isEnabled(), isFocused()), 0, 0);
        }
    }
}
