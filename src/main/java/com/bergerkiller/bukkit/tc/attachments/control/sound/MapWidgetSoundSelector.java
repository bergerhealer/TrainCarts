package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutCustomSoundEffectHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutStopSoundHandle;
import org.bukkit.entity.Player;

/**
 * Selects the sound name and play category for a sound. When the sound name is selected,
 * previews/plays the sound, and when de-focussing stops the sound instantly.
 * To the left it can show an icon: 1P, 3P, both or none.
 */
public abstract class MapWidgetSoundSelector extends MapWidget {
    private final MapWidgetSoundNameSelector name = new MapWidgetSoundNameSelector() {
        @Override
        public void onSoundChanged(ResourceKey<SoundEffect> sound) {
            playSound(display);
            MapWidgetSoundSelector.this.onSoundChanged(sound);
        }

        @Override
        public void onFocus() {
            playSound(display);
        }

        @Override
        public void onBlur() {
            stopSound(display);
        }

        @Override
        public void onDetached() {
            super.onDetached();
            stopSound(display);
        }
    };
    private final MapWidgetSoundCategorySelector category = new MapWidgetSoundCategorySelector() {
        @Override
        public void onCategoryChanged(String categoryName) {
            MapWidgetSoundSelector.this.onCategoryChanged(categoryName);
        }
    };
    private Mode mode = Mode.FIRST_PERSPECTIVE;
    private ResourceKey<SoundEffect> lastPreviewedSound = null;
    private String lastPreviewedCategory = "master";

    public abstract void onSoundChanged(ResourceKey<SoundEffect> sound);
    public abstract void onCategoryChanged(String categoryName);

    public MapWidgetSoundSelector setMode(Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.invalidate();
        }
        return this;
    }

    public MapWidgetSoundSelector setSound(ResourceKey<SoundEffect> sound) {
        name.setSound(sound);
        return this;
    }

    public ResourceKey<SoundEffect> getSound() {
        return name.getSound();
    }

    public MapWidgetSoundSelector setCategory(String categoryName) {
        category.setCategory(categoryName);
        return this;
    }

    public String getCategory() {
        return category.getCategory();
    }

    @Override
    public void onAttached() {
        onBoundsChanged();
        addWidget(name);
        addWidget(category);
    }

    @Override
    public void onBoundsChanged() {
        int modeSpace = 7;
        name.setBounds(modeSpace, 0, getWidth() - category.getWidth() - 1 - modeSpace, getHeight());
        category.setBounds(name.getX() + name.getWidth() + 1, 0, category.getWidth(), getHeight());
    }

    @Override
    public void onDraw() {
        switch (mode) {
            case FIRST_PERSPECTIVE:
                draw1p(0, 3, MapColorPalette.COLOR_RED);
                break;
            case THIRD_PERSPECTIVE:
                draw3p(0, 3, MapColorPalette.COLOR_RED);
                break;
            case ALL_PERSPECTIVE:
                draw1p(0, 0, MapColorPalette.COLOR_RED);
                draw3p(0, 6, MapColorPalette.COLOR_RED);
                break;
        }
    }

    // here because 1 is 3-pixels wide, and we can draw it as 2 to save space
    private void draw1p(int x, int y, byte color) {
        view.drawPixel(x, y + 1, color);
        view.drawLine(x + 1, y, x + 1, y + 4, color);
        view.draw(MapFont.TINY, x + 3, y, color, "p");
    }

    // here because 3 is 3-pixels wide, and we can draw it as 2 to save space
    private void draw3p(int x, int y, byte color) {
        view.drawPixel(x, y, color);
        view.drawPixel(x, y + 2, color);
        view.drawPixel(x, y + 4, color);
        view.drawLine(x + 1, y, x + 1, y + 4, color);
        view.draw(MapFont.TINY, x + 3, y, color, "p");
    }

    private void playSound(MapDisplay display) {
        stopSound(display);
        lastPreviewedSound = getSound();
        lastPreviewedCategory = getCategory();
        if (lastPreviewedSound != null) {
            for (Player player : display.getOwners()) {
                PacketPlayOutCustomSoundEffectHandle packet = PacketPlayOutCustomSoundEffectHandle.createNew(
                        lastPreviewedSound, lastPreviewedCategory, player.getLocation(), 1.0f, 1.0f);
                PacketUtil.sendPacket(player, packet);
            }
        }
    }

    private void stopSound(MapDisplay display) {
        if (Common.hasCapability("Common:Sound:StopSoundPacket")) {
            stopSoundImpl(display);
        }
    }

    private void stopSoundImpl(MapDisplay display) {
        if (lastPreviewedSound != null) {
            PacketPlayOutStopSoundHandle packet = PacketPlayOutStopSoundHandle.createNew(lastPreviewedSound, lastPreviewedCategory);
            for (Player player : display.getOwners()) {
                PacketUtil.sendPacket(player, packet);
            }
            lastPreviewedSound = null;
        }
    }

    public enum Mode {
        NONE,
        FIRST_PERSPECTIVE,
        THIRD_PERSPECTIVE,
        ALL_PERSPECTIVE
    }
}
