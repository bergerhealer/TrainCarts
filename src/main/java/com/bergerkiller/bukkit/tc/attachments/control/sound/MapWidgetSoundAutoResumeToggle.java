package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetTooltip;

/**
 * Toggles between auto-resume mode being on or off.
 * Auto-resume sets whether the sound is automatically cut off, and a new sound played,
 * when the player switches between 1p and 3p. It also makes it play the sound when
 * a new player comes near. Only suitable for sounds that loop.
 */
public abstract class MapWidgetSoundAutoResumeToggle extends MapWidgetSoundButton {
    private boolean autoResume = false;
    private final MapTexture icon_disabled, icon_enabled;
    private final MapWidgetTooltip tooltip = new MapWidgetTooltip();

    public MapWidgetSoundAutoResumeToggle() {
        MapTexture icon = MapTexture.loadPluginResource(TrainCarts.plugin,
                "com/bergerkiller/bukkit/tc/textures/attachments/sound_autoresume.png");
        icon_disabled = icon.getView(0, 0, icon.getWidth()/2, icon.getHeight()).clone();
        icon_enabled = icon.getView(icon.getWidth()/2, 0, icon.getWidth()/2, icon.getHeight()).clone();
        updateTooltip();
        setSize(icon_enabled.getWidth(), icon_enabled.getHeight());
    }

    public abstract void onAutoResumeChanged(boolean autoResume);

    public MapWidgetSoundAutoResumeToggle setAutoResume(boolean autoResume) {
        if (this.autoResume != autoResume) {
            this.autoResume = autoResume;
            this.updateTooltip();
            this.invalidate();
        }
        return this;
    }

    public boolean isAutoResume() {
        return autoResume;
    }

    private void updateTooltip() {
        tooltip.setText(autoResume ? "auto-resume: ON" : "auto-resume: OFF");
    }

    @Override
    public void onClick() {
        setAutoResume(!isAutoResume());
        onAutoResumeChanged(isAutoResume());
    }

    @Override
    public void onFocus() {
        super.onFocus();
        addWidget(tooltip);
    }

    @Override
    public void onBlur() {
        super.onBlur();
        removeWidget(tooltip);
    }

    @Override
    public void onDraw() {
        super.onDraw();

        view.draw(autoResume ? icon_enabled : icon_disabled, 0, 0);
    }
}
