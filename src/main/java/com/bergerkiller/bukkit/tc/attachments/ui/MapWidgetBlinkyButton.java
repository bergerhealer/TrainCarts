package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.CommonSounds;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * Shows a simple focusable blinking icon that can be clicked (onClick())
 */
public abstract class MapWidgetBlinkyButton extends MapWidget {
    private MapTexture icon = MapTexture.createEmpty(16, 16);
    private MapTexture icon_blink_a = MapTexture.createEmpty(16, 16);
    private MapTexture icon_blink_b = MapTexture.createEmpty(16, 16);
    private int blinkCtr = 0;
    boolean blinkMode = false;

    public MapWidgetBlinkyButton() {
        this.setSize(16, 16);
        this.setFocusable(true);
    }

    public MapWidgetBlinkyButton setIcon(String filename) {
        return this.setIcon(MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/" + filename));
    }

    public MapWidgetBlinkyButton setIcon(MapTexture icon) {
        this.icon = icon;
        this.icon_blink_a.clear();
        this.icon_blink_a.setBlendMode(MapBlendMode.NONE);
        this.icon_blink_a.draw(this.icon, 0, 0);
        this.icon_blink_a.setBlendMode(MapBlendMode.SUBTRACT);
        this.icon_blink_a.fill(MapColorPalette.getColor(20, 20, 64));
        this.icon_blink_b.clear();
        this.icon_blink_b.setBlendMode(MapBlendMode.NONE);
        this.icon_blink_b.draw(this.icon, 0, 0);
        this.icon_blink_b.setBlendMode(MapBlendMode.ADD);
        this.icon_blink_b.fill(MapColorPalette.getColor(80, 80, 0));
        this.setSize(icon.getWidth(), icon.getHeight());
        this.invalidate();
        return this;
    }

    public MapTexture getIcon() {
        return this.icon;
    }

    private void setBlink(boolean mode) {
        if (this.blinkMode != mode) {
            this.blinkMode = mode;
            this.invalidate();
        }
    }

    @Override
    public void onFocus() {
        // Click navigation sounds
        display.playSound(CommonSounds.CLICK_WOOD);
    }

    @Override
    public void onTick() {
        if (this.isFocused()) {
            if (blinkCtr-- == 0) {
                blinkCtr = 5;
                setBlink(!this.blinkMode);
            }
        } else {
            setBlink(false);
            blinkCtr = 0;
        }
    }

    @Override
    public void onDraw() {
        if (!this.isFocused()) {
            this.view.draw(this.icon, 0, 0);
        } else if (this.blinkMode) {
            this.view.draw(this.icon_blink_a, 0, 0);
        } else {
            this.view.draw(this.icon_blink_b, 0, 0);
        }
    }

    @Override
    public void onActivate() {
        display.playSound(CommonSounds.EXTINGUISH);
        this.onClick();
    }

    public abstract void onClick();
}
