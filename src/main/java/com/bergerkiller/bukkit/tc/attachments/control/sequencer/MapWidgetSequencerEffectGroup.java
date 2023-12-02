package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.Util;

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
    private static final int TOP_HEADER_HEIGHT = 8;
    private final MapWidgetSequencerScroller scroller;
    private final Mode mode;
    private final List<MapWidgetSequencerEffect> effects = new ArrayList<>();
    private HeaderTitle headerTitle;
    private HeaderButton configureButton, addEffectButton;
    private double duration = 1.0;

    public MapWidgetSequencerEffectGroup(MapWidgetSequencerScroller scroller, Mode mode) {
        this.scroller = scroller;
        this.mode = mode;
        this.setClipParent(true);
        this.updateBounds();
    }

    /**
     * Adds an effect to this group of effects
     *
     * @param effect Effect to add
     * @return this
     */
    public MapWidgetSequencerEffectGroup addEffect(MapWidgetSequencerEffect effect) {
        this.effects.add(effect);
        if (this.duration > 0.0) {
            addEffectWidget(effect);
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
            effect.getConfig().remove(); // Remove from node list

            if (this.duration > 0.0) {
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
     * @param duration
     * @return this
     */
    public MapWidgetSequencerEffectGroup setDuration(double duration) {
        if (this.duration != duration) {
            boolean effectsVisibleChanged = (this.duration <= 0.0) != (duration <= 0.0);
            this.duration = duration;
            if (effectsVisibleChanged) {
                if (addEffectButton != null) {
                    addEffectButton.setEnabled(duration > 0.0);
                }

                // Remove (or re-add) all the effects
                for (MapWidgetSequencerEffect effect : this.effects) {
                    this.removeWidget(effect);
                }
                if (duration > 0.0) {
                    this.effects.forEach(this::addEffectWidget);
                }

                updateBounds();
            }
            if (headerTitle != null) {
                headerTitle.invalidate();
            }
        }
        return this;
    }

    private void addEffectWidget(MapWidgetSequencerEffect effect) {
        int index = this.effects.indexOf(effect);
        effect.setBounds(0, TOP_HEADER_HEIGHT + (MapWidgetSequencerEffect.HEIGHT - 1) * index,
                getWidth(), MapWidgetSequencerEffect.HEIGHT);
        this.addWidget(effect);
    }

    private void updateBounds() {
        int newHeight = TOP_HEADER_HEIGHT + ((duration <= 0.0 || effects.isEmpty())
                ? 0 : (effects.size() * (MapWidgetSequencerEffect.HEIGHT - 1) + 1));
        boolean heightChanged = (this.getHeight() != newHeight);
        this.setBounds(0, getY(), scroller.getWidth(), newHeight);
        if (heightChanged) {
            scroller.recalculateContainerSize();
        }
    }

    @Override
    public void onAttached() {
        headerTitle = addWidget(new HeaderTitle());
        headerTitle.setBounds(0, 0, getWidth() - 43, 7);

        configureButton = addWidget(new HeaderButton(0, 19, 35, 7) {
            @Override
            public void onActivate() {

            }
        });
        configureButton.setPosition(getWidth() - 43, 0);

        addEffectButton = addWidget(new HeaderButton(35, 19, 7, 7) {
            @Override
            public void onActivate() {
                // Ask what effect name and type of effect to add
                // TODO: Implement this

                // Reset focus index to 0
                scroller.effectSelButtonIndex = 0;
                addEffect(new MapWidgetSequencerEffect());
            }
        });
        addEffectButton.setEnabled(duration > 0.0);
        addEffectButton.setPosition(getWidth() - 7, 0);
    }

    private class HeaderTitle extends MapWidget {

        public HeaderTitle() {
            this.setClipParent(true);
        }

        @Override
        public void onDraw() {
            if (duration > 0.0) {
                view.draw(mode.icon(), 3, 1, MapColorPalette.COLOR_RED);
                view.draw(MapFont.TINY, 12, 1, MapColorPalette.COLOR_RED,
                        mode.title() + "  " + DURATION_FORMAT.format(duration) + "s");
            } else {
                byte color = MapColorPalette.getColor(128, 128, 128);
                view.draw(mode.icon(), 3, 1, color);
                view.draw(MapFont.TINY, 12, 1, color,
                        mode.title() + " [OFF]");
            }
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

    /**
     * One of three modes that sequencer effects are stored
     */
    public enum Mode {
        START("start"),
        LOOP("loop"),
        STOP("stop");

        private final String title;
        private final MapTexture icon;

        Mode(String title) {
            this.title = title;
            this.icon = MapWidgetSequencerEffect.TEXTURE_ATLAS
                    .getView(7 * ordinal(), 14, 7, 5).clone();
        }

        public String title() {
            return title;
        }

        public MapTexture icon() {
            return icon;
        }
    }
}
