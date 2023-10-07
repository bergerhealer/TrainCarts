package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetTooltip;

/**
 * Toggles between the different {@link SoundPerspectiveMode} values
 */
public abstract class MapWidgetSoundPerspectiveMode extends MapWidgetSoundButton {
    private SoundPerspectiveMode mode = SoundPerspectiveMode.SAME;
    public final MapWidgetTooltip tooltip = new MapWidgetTooltip().setText(mode.getTooltip());

    public MapWidgetSoundPerspectiveMode() {
        setSize(mode.getIcon().getWidth(), mode.getIcon().getHeight());
    }

    public abstract void onModeChanged(SoundPerspectiveMode newMode);

    public MapWidgetSoundPerspectiveMode setMode(SoundPerspectiveMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.tooltip.setText(mode.getTooltip());
            this.invalidate();
        }
        return this;
    }

    public SoundPerspectiveMode getMode() {
        return mode;
    }

    @Override
    public void onClick() {
        SoundPerspectiveMode[] values = SoundPerspectiveMode.values();
        mode = values[(mode.ordinal() + 1) % values.length];
        tooltip.setText(mode.getTooltip());
        onModeChanged(mode);
        invalidate();
    }

    @Override
    public void onFocus() {
        super.onFocus();
        this.addWidget(this.tooltip);
    }

    @Override
    public void onBlur() {
        super.onBlur();
        this.removeWidget(this.tooltip);
    }

    @Override
    public void onDraw() {
        super.onDraw();

        view.draw(mode.getIcon(), 0, 0);
    }
}
