package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * Shows a simple focusable blinking icon that can be clicked (onClick())
 */
public abstract class MapWidgetBlinkyButton extends MapWidget {
    private MapTexture icon = MapTexture.createEmpty(16, 16);
    private MapTexture icon_disabled = MapTexture.createEmpty(16, 16);
    private MapTexture icon_blink_a = MapTexture.createEmpty(16, 16);
    private MapTexture icon_blink_b = MapTexture.createEmpty(16, 16);
    private int blinkCtr = 0;
    private boolean isRepeatClicking = false;
    private boolean blinkMode = false;
    private boolean enableRepeatClicking = false;
    public final MapWidgetTooltip tooltip = new MapWidgetTooltip();

    public MapWidgetBlinkyButton() {
        this.setSize(16, 16);
        this.setFocusable(true);
    }

    public MapWidgetBlinkyButton setTooltip(String text) {
        this.tooltip.setText(text);
        return this;
    }

    public MapWidgetBlinkyButton setRepeatClickEnabled(boolean enabled) {
        this.enableRepeatClicking = enabled;
        return this;
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
        for (int x = 0; x < this.icon_disabled.getWidth(); x++) {
            for (int y = 0; y < this.icon_disabled.getHeight(); y++) {
                byte code = this.icon.readPixel(x, y);
                if (MapColorPalette.isTransparent(code)) {
                    this.icon_disabled.writePixel(x, y, code);
                } else {
                    java.awt.Color c = MapColorPalette.getRealColor(this.icon.readPixel(x, y));
                    int avg = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
                    this.icon_disabled.writePixel(x, y, MapColorPalette.getColor(avg, avg, avg));
                }
            }
        }
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
        if (!this.isEnabled()) {
            this.view.draw(this.icon_disabled, 0, 0);
        } else if (!this.isFocused()) {
            this.view.draw(this.icon, 0, 0);
        } else if (this.blinkMode) {
            this.view.draw(this.icon_blink_a, 0, 0);
        } else {
            this.view.draw(this.icon_blink_b, 0, 0);
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == MapPlayerInput.Key.ENTER) {
            if (!this.enableRepeatClicking || event.getRepeat() <= 1) {
                this.isRepeatClicking = false;
                this.activate();
            } else {
                if (!this.isRepeatClicking) {
                    this.isRepeatClicking = true;
                    display.playSound(SoundEffect.CLICK);
                    this.onClickHold();
                }
                this.onRepeatClick();
            }
        } else {
            super.onKeyPressed(event);
        }
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        super.onKeyReleased(event);
        if (event.getKey() == MapPlayerInput.Key.ENTER && this.isRepeatClicking) {
            this.isRepeatClicking = false;
            display.playSound(SoundEffect.CLICK_WOOD);
            this.onClickHoldRelease();
        }
    }

    @Override
    public void onActivate() {
        display.playSound(SoundEffect.EXTINGUISH);
        this.onClick();
    }

    @Override
    public void onFocus() {
        super.onFocus();

        this.addWidget(this.tooltip);

        // Click navigation sounds
        display.playSound(SoundEffect.CLICK_WOOD);
    }

    @Override
    public void onBlur() {
        super.onBlur();
        this.removeWidget(this.tooltip);
    }

    /**
     * Called once when the player activates the button
     */
    public abstract void onClick();

    /**
     * Called once when the player held down the button for a longer time
     */
    public void onClickHold() {
    }

    /**
     * Called once when the player releases the button after click-and-holding
     */
    public void onClickHoldRelease() {
    }

    /**
     * Called repeatedly while the player is holding down the button
     */
    public void onRepeatClick() {
    }
}
