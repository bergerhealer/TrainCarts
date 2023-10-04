package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;

import java.util.Locale;

/**
 * Displays a sound name, and if too long to fit, scrolls it right to left
 * to show all of it. Allows changing it by entering it into an anvil menu.
 */
abstract class MapWidgetSoundNameSelector extends MapWidgetSoundElement implements SetValueTarget {
    private static final int SCROLL_DELAY = 20;
    private static final int SCROLL_HOLD = 80;
    private static final int SCROLL_STEP = 5;
    private ResourceKey<SoundEffect> sound = null;
    private int namespaceWidth = -1;
    private int soundFullWidth = -1;
    private int scrollOffset = 0;
    private int scrollDelayCtr = 0;
    private int scrollHoldCtr = 0;

    private final MapWidgetSubmitText submitText = addWidget(new MapWidgetSubmitText() {
        @Override
        public void onAccept(String text) {
            onSoundAccepted(text);
        }
    }).setDescription("Set Sound Name");

    public MapWidgetSoundNameSelector() {
        this.setSize(60, 11);
        this.setRetainChildWidgets(true);
    }

    public abstract void onSoundChanged(ResourceKey<SoundEffect> sound);

    public MapWidgetSoundNameSelector setSound(ResourceKey<SoundEffect> sound) {
        if (!LogicUtil.bothNullOrEqual(this.sound, sound)) {
            this.sound = sound;
            this.namespaceWidth = -1;
            this.soundFullWidth = -1;
            this.scrollOffset = 0;
            this.scrollDelayCtr = 0;
            this.scrollHoldCtr = 0;
            this.invalidate();
        }
        return this;
    }

    public ResourceKey<SoundEffect> getSound() {
        return sound;
    }

    @Override
    public void onDraw() {
        super.onDraw();

        // Draw the text itself - namespace in red, path in white
        MapCanvas textArea = view.getView(2, 2, getWidth() - 4, getHeight() - 3);
        if (sound != null) {
            byte namespaceColor = MapColorPalette.getColor(255, 160, 160);
            byte pathColor = MapColorPalette.COLOR_WHITE;
            calcWidths();
            textArea.draw(MapFont.MINECRAFT, -scrollOffset, 0, namespaceColor, sound.getName().getNamespace() + ":");
            textArea.draw(MapFont.MINECRAFT, namespaceWidth - scrollOffset, 0, pathColor, sound.getName().getName());
        } else {
            textArea.draw(MapFont.MINECRAFT, 0, 0, MapColorPalette.COLOR_RED, "<No Sound>");
        }
    }

    @Override
    public void onTick() {
        calcWidths();
        int overflow = soundFullWidth - (getWidth() - 4);
        if (overflow > 0) {
            if (++scrollDelayCtr > SCROLL_DELAY) {
                int newOffset = Math.min(overflow, scrollOffset + SCROLL_STEP);
                if (scrollOffset != newOffset) {
                    scrollOffset = newOffset;
                    invalidate();
                } else if (++scrollHoldCtr > SCROLL_HOLD) {
                    scrollDelayCtr = 0;
                    scrollHoldCtr = 0;
                    scrollOffset = 0;
                    invalidate();
                }
            }
        }
    }

    @Override
    public void onActivate() {
        // This fires again when the input text dialog is closed, so check for that
        // In that case don't activate this widget again, but focus it instead
        // That way we don't keep re-opening the anvil menu for the player
        if (submitText.isActivated()) {
            this.focus();
        } else {
            submitText.activate();
        }
    }

    private void calcWidths() {
        if (namespaceWidth != -1 && soundFullWidth != -1) {
            return;
        }
        if (sound == null) {
            namespaceWidth = 0;
            soundFullWidth = 0;
        } else {
            namespaceWidth = (int) view.calcFontSize(MapFont.MINECRAFT, sound.getName().getNamespace() + ":").getWidth();
            soundFullWidth = namespaceWidth + (int) view.calcFontSize(MapFont.MINECRAFT, sound.getName().getName()).getWidth();
        }
    }

    private void onSoundAccepted(String soundName) {
        // Parse as text. If empty, set to no sound
        soundName = soundName.trim().toLowerCase(Locale.ENGLISH).replace(' ', '_');
        if (soundName.isEmpty()) {
            setSound(null);
        } else {
            setSound(SoundEffect.fromName(soundName));
        }
        onSoundChanged(getSound());
    }

    @Override
    public String getAcceptedPropertyName() {
        return "Sound Name";
    }

    @Override
    public boolean acceptTextValue(String value) {
        onSoundAccepted(value);
        return true;
    }
}
