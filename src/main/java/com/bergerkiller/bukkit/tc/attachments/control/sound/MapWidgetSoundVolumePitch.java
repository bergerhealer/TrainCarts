package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;

/**
 * Configures the volume and pitch of a Sound value.
 */
public abstract class MapWidgetSoundVolumePitch extends MapWidget {
    private static final int BOX_HEIGHT = 13;
    private static final int LEFT_TEXT_WIDTH = 12;
    private static final int RANDOM_ICON_WIDTH = 5;
    private final MapWidgetSoundNumberBox volumeBase = new MapWidgetSoundNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Sound Base Volume";
        }

        @Override
        public void onValueChanged(double newValue) {
            onChanged();
        }
    }.setDefaultValue(1.0);
    private final MapWidgetSoundNumberBox volumeRandom = new MapWidgetSoundNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Sound Random Volume";
        }

        @Override
        public void onValueChanged(double newValue) {
            onChanged();
        }
    }.setDefaultValue(0.0);
    private final MapWidgetSoundNumberBox speedBase = new MapWidgetSoundNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Sound Base Speed";
        }

        @Override
        public void onValueChanged(double newValue) {
            onChanged();
        }
    }.setDefaultValue(1.0);
    private final MapWidgetSoundNumberBox speedRandom = new MapWidgetSoundNumberBox() {
        @Override
        public String getAcceptedPropertyName() {
            return "Sound Random Speed";
        }

        @Override
        public void onValueChanged(double newValue) {
            onChanged();
        }
    }.setDefaultValue(0.0);

    /**
     * Called when a property of the sound volume or pitch changes
     */
    public abstract void onChanged();

    public MapWidgetSoundVolumePitch setInitialBaseVolume(double value) {
        volumeBase.setInitialValue(value);
        return this;
    }

    public MapWidgetSoundVolumePitch setInitialRandomVolume(double value) {
        volumeRandom.setInitialValue(value);
        return this;
    }

    public MapWidgetSoundVolumePitch setInitialBaseSpeed(double value) {
        speedBase.setInitialValue(value);
        return this;
    }

    public MapWidgetSoundVolumePitch setInitialRandomSpeed(double value) {
        speedRandom.setInitialValue(value);
        return this;
    }

    public double getBaseVolume() {
        return volumeBase.getValue();
    }

    public double getRandomVolume() {
        return volumeRandom.getValue();
    }

    public double getBaseSpeed() {
        return speedBase.getValue();
    }

    public double getRandomSpeed() {
        return speedRandom.getValue();
    }

    @Override
    public void onAttached() {
        onBoundsChanged();
        addWidget(volumeBase);
        addWidget(volumeRandom);
        addWidget(speedBase);
        addWidget(speedRandom);
    }

    private int calcBoxWidth() {
        return (getWidth() - LEFT_TEXT_WIDTH - RANDOM_ICON_WIDTH) / 2;
    }

    @Override
    public void onBoundsChanged() {
        int boxWidth = calcBoxWidth();
        int randomBoxX = LEFT_TEXT_WIDTH + boxWidth + RANDOM_ICON_WIDTH;
        volumeBase.setBounds(LEFT_TEXT_WIDTH, 0, boxWidth, BOX_HEIGHT);
        volumeRandom.setBounds(randomBoxX, 0, boxWidth, BOX_HEIGHT);
        speedBase.setBounds(LEFT_TEXT_WIDTH, getHeight() - BOX_HEIGHT, boxWidth, BOX_HEIGHT);
        speedRandom.setBounds(randomBoxX, getHeight() - BOX_HEIGHT, boxWidth, BOX_HEIGHT);
    }

    @Override
    public void onDraw() {
        int boxWidth = calcBoxWidth();

        // vol
        view.draw(MapFont.TINY, 0, 4, MapColorPalette.COLOR_RED, "Vol");
        // spd
        view.draw(MapFont.TINY, 0, getHeight() - 9, MapColorPalette.COLOR_RED, "Spd");
        // +- characters
        view.draw(MapFont.TINY, LEFT_TEXT_WIDTH + boxWidth + 1, 4, MapColorPalette.COLOR_RED, "\u00F1");
        view.draw(MapFont.TINY, LEFT_TEXT_WIDTH + boxWidth + 1, getHeight() - 9, MapColorPalette.COLOR_RED, "\u00F1");
    }
}
