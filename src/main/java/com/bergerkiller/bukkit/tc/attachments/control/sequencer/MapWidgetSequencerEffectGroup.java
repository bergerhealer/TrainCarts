package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.control.effect.EffectLoop;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNameSelector;
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
    private static final NumberFormat DURATION_FORMAT = Util.createNumberFormat(1, 4);
    private static final ConfigurationNode EMPTY_CONFIG = new ConfigurationNode();
    private static final int TOP_HEADER_HEIGHT = 8;
    private final MapWidgetSequencerEffectGroupList groupList;
    private final SequencerMode mode;
    private final List<MapWidgetSequencerEffect> effects = new ArrayList<>();
    private Header header;
    private HeaderTitle headerTitle;
    private HeaderButton configureButton, addEffectButton;
    private EffectLoop.Time duration;

    public MapWidgetSequencerEffectGroup(MapWidgetSequencerEffectGroupList groupList, SequencerMode mode) {
        this.groupList = groupList;
        this.mode = mode;
        this.duration = EffectLoop.Time.seconds(readConfig().getOrDefault("duration", 0.0));
        this.setClipParent(true);
        this.updateBounds();

        for (ConfigurationNode effectConfig : readConfig().getNodeList("effects")) {
            addEffect(new MapWidgetSequencerEffect(effectConfig));
        }
    }

    protected ConfigurationNode readConfig() {
        ConfigurationNode config = groupList.getConfig().getNodeIfExists(mode.configKey());
        return (config == null) ? EMPTY_CONFIG : config;
    }

    protected ConfigurationNode writeConfig() {
        return groupList.getConfig().getNode(mode.configKey());
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
            //TODO: Replace with getNodeList(path, false) when BKCommonLib 1.20.2-v3 or later is a hard-depend
            this.writeConfig().getList("effects").add(effect.getConfig());
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
        this.setBounds(0, getY(), groupList.getWidth(), newHeight);
        if (heightChanged) {
            groupList.recalculateContainerSize();
        }
    }

    @Override
    public void onAttached() {
        header = addWidget(new Header());
        header.setBounds(0, 0, getWidth(), 8);

        headerTitle = header.addWidget(new HeaderTitle());
        headerTitle.setBounds(0, 0, getWidth() - 43, 7);

        configureButton = header.addWidget(new HeaderButton(0, 19, 35, 7) {
            @Override
            public void onActivate() {
                groupList.addWidget(new ConfigureDialog());
            }
        });
        configureButton.setPosition(getWidth() - 43, 0);

        //TODO: Ugly indentation
        addEffectButton = header.addWidget(new HeaderButton(35, 19, 7, 7) {
            @Override
            public void onActivate() {
                // Ask what effect to target
                groupList.addWidget(new MapWidgetAttachmentNameSelector(groupList.getEffectNames()) {
                    @Override
                    public void onSelected(String effectName) {
                        // Ask what type of effect to add
                        groupList.addWidget(new MapWidgetSequencerTypeSelector() {
                            @Override
                            public void onSelected(SequencerType type) {
                                groupList.effectSelButtonIndex = 0;
                                addEffect((new MapWidgetSequencerEffect(type, effectName)).focusOnActivate());
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
            addWidget(new MapWidgetTransferFunctionSingleConfigItem(groupList.getTransferFunctionHost(), writeConfig(), "speed", () -> false) {
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
        private final byte BACKGROUND_COLOR = MapColorPalette.getColor(54, 81, 114);

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
        private final MapTexture defaultImage, focusedImage, disabledImage;

        public HeaderButton(int atlas_x, int atlas_y, int w, int h) {
            this.setFocusable(true);
            this.setClipParent(true);
            this.setSize(w, h);
            this.defaultImage = MapWidgetSequencerEffect.TEXTURE_ATLAS.getView(atlas_x, atlas_y, w, h).clone();
            this.focusedImage = MapWidgetSequencerEffect.TEXTURE_ATLAS.getView(atlas_x, atlas_y + h, w, h).clone();
            this.disabledImage = MapWidgetSequencerEffect.TEXTURE_ATLAS.getView(atlas_x, atlas_y + 2 * h, w, h).clone();
        }

        @Override
        public abstract void onActivate();

        @Override
        public void onDraw() {
            if (!isEnabled()) {
                view.draw(disabledImage, 0, 0);
            } else if (isFocused()) {
                view.draw(focusedImage, 0, 0);
            } else {
                view.draw(defaultImage, 0, 0);
            }
        }
    }
}
