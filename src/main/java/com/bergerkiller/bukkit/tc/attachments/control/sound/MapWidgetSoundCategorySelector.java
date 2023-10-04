package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.TrainCarts;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Selects a sound category from one of a select channels minecraft supports.
 */
abstract class MapWidgetSoundCategorySelector extends MapWidgetSoundElement {
    private SoundCategory category = SoundCategory.MASTER;
    private final ArrowWidget upArrow = new ArrowWidget(-90);
    private final ArrowWidget downArrow = new ArrowWidget(90);
    private final ToolTipWidget tooltip = new ToolTipWidget();

    public MapWidgetSoundCategorySelector() {
        this.setSize(11, 11);
        this.tooltip.setText(category.getId());
    }

    public abstract void onCategoryChanged(String categoryName);

    public MapWidgetSoundCategorySelector setCategory(String categoryName) {
        return setCategory(SoundCategory.byId(categoryName));
    }

    private MapWidgetSoundCategorySelector setCategory(SoundCategory newCategory) {
        if (category != newCategory) {
            category = newCategory;
            tooltip.setText(newCategory.getId());
            updateArrowsEnabled();
            invalidate();
        }
        return this;
    }

    public String getCategory() {
        return category.getId();
    }

    @Override
    public void onDraw() {
        super.onDraw();
        view.draw(category.getIcon(isFocused()), 0, 0);
    }

    @Override
    public void onActivate() {
        super.onActivate();

        // When activated show an up/down arrow that allows the player to change the selection
        removeWidget(upArrow);
        removeWidget(downArrow);
        updateArrowsEnabled();
        upArrow.setPressed(false);
        downArrow.setPressed(false);
        addWidget(upArrow.setPosition(0, -upArrow.getHeight() - 1));
        addWidget(downArrow.setPosition(0, getHeight() + 1));
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();

        removeWidget(upArrow);
        removeWidget(downArrow);
        if (!isFocused()) {
            removeWidget(tooltip);
        }
    }

    @Override
    public void onFocus() {
        removeWidget(tooltip);
        tooltip.setPosition(-tooltip.getWidth() - 1, 1);
        addWidget(tooltip);
    }

    @Override
    public void onBlur() {
        if (!isActivated()) {
            removeWidget(tooltip);
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (this.isActivated()) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                upArrow.setPressed(true);
                if (category.hasPrev()) {
                    setCategory(category.getPrev());
                    onCategoryChanged(getCategory());
                }
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                downArrow.setPressed(true);
                if (category.hasNext()) {
                    setCategory(category.getNext());
                    onCategoryChanged(getCategory());
                }
            } else {
                upArrow.setPressed(false);
                downArrow.setPressed(false);
                this.deactivate();
            }
        } else {
            super.onKeyPressed(event);
        }
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        if (this.isActivated()) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                upArrow.setPressed(false);
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                downArrow.setPressed(false);
            } else {
                super.onKeyReleased(event);
            }
        } else {
            super.onKeyReleased(event);
        }
    }

    private void updateArrowsEnabled() {
        upArrow.setEnabled(category.hasPrev());
        downArrow.setEnabled(category.hasNext());
    }

    /**
     * Shows a tooltip text left of the selector button
     */
    private static class ToolTipWidget extends MapWidget {
        private String text = "";

        public ToolTipWidget() {
            this.setDepthOffset(2);
        }

        public ToolTipWidget setText(String text) {
            if (!this.text.equals(text)) {
                this.text = text;
                if (display != null) {
                    calcSize();
                }
                invalidate();
            }
            return this;
        }

        @Override
        public void onAttached() {
            calcSize();
        }

        @Override
        public void onDraw() {
            view.fill(MapColorPalette.COLOR_BLACK);
            view.draw(MapFont.MINECRAFT, 2, 1, MapColorPalette.COLOR_WHITE, text);
        }

        private void calcSize() {
            java.awt.Dimension dim = view.calcFontSize(MapFont.MINECRAFT, text);
            int tw = (int) dim.getWidth() + 2;
            int th = (int) dim.getHeight() + 1;
            this.setBounds(getX() + getWidth() - tw, getY(), tw, th);
        }
    }

    /**
     * The up and down arrow to change category, shown when activated
     */
    private static class ArrowWidget extends MapWidget {
        private final MapTexture icon_disabled;
        private final MapTexture icon_enabled;
        private final MapTexture icon_pressed;
        private boolean pressed = false;

        public ArrowWidget(int angle) {
            MapTexture icon = MapTexture.loadPluginResource(TrainCarts.plugin,
                    "com/bergerkiller/bukkit/tc/textures/attachments/arrow.png");
            icon_disabled = MapTexture.rotate(icon.getView(0, 0, 6, 11), angle);
            icon_enabled = MapTexture.rotate(icon.getView(6, 0, 6, 11), angle);
            icon_pressed = MapTexture.rotate(icon.getView(12, 0, 6, 11), angle);
            this.setSize(icon_enabled.getWidth(), icon_enabled.getHeight());
        }

        public ArrowWidget setPressed(boolean pressed) {
            if (this.pressed != pressed) {
                this.pressed = pressed;
                invalidate();
            }
            return this;
        }

        @Override
        public void onDraw() {
            if (!this.isEnabled()) {
                view.draw(icon_disabled, 0, 0);
            } else if (pressed) {
                view.draw(icon_pressed, 0, 0);
            } else {
                view.draw(icon_enabled, 0, 0);
            }
        }
    }

    private enum SoundCategory {
        MASTER("master"),
        MUSIC("music"),
        RECORD("record"),
        WEATHER("weather"),
        BLOCK("block"),
        HOSTILE("hostile"),
        NEUTRAL("neutral"),
        PLAYER("player"),
        AMBIENT("ambient"),
        VOICE("voice");

        private final String id;
        private final MapTexture icon;
        private final MapTexture icon_focused;
        private SoundCategory prev, next;

        private static final Map<String, SoundCategory> byId = new HashMap<>();
        static {
            SoundCategory prev = null;
            for (SoundCategory cat : SoundCategory.values()) {
                byId.put(cat.getId(), cat);
                cat.prev = prev;
                if (prev != null) {
                    prev.next = cat;
                }
                prev = cat;
            }
        }

        public static SoundCategory byId(String id) {
            return byId.getOrDefault(id.toLowerCase(Locale.ENGLISH), MASTER);
        }

        SoundCategory(String id) {
            this.id = id;
            this.icon = MapTexture.loadPluginResource(TrainCarts.plugin,
                    "com/bergerkiller/bukkit/tc/textures/attachments/sound_categories.png")
                    .getView(ordinal() * 11, 0, 11, 11).clone();
            this.icon_focused = this.icon.clone();
            this.icon_focused.setBlendMode(MapBlendMode.ADD);
            this.icon_focused.fill(MapColorPalette.getColor(80, 80, 0));
            this.icon_focused.setBlendMode(MapBlendMode.NONE);
        }

        public String getId() {
            return id;
        }

        public MapTexture getIcon(boolean focused) {
            return focused ? icon_focused : icon;
        }

        public boolean hasPrev() {
            return prev != null;
        }

        public SoundCategory getPrev() {
            return prev;
        }

        public boolean hasNext() {
            return next != null;
        }

        public SoundCategory getNext() {
            return next;
        }
    }
}
