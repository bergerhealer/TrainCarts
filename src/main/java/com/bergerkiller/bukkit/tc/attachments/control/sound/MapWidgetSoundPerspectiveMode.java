package com.bergerkiller.bukkit.tc.attachments.control.sound;

/**
 * Toggles between the different {@link SoundPerspectiveMode} values
 */
public abstract class MapWidgetSoundPerspectiveMode extends MapWidgetSoundElement {
    private SoundPerspectiveMode mode = SoundPerspectiveMode.SAME;

    public MapWidgetSoundPerspectiveMode() {
        setSize(mode.getIcon().getWidth(), mode.getIcon().getHeight());
    }

    public abstract void onModeChanged(SoundPerspectiveMode newMode);

    public MapWidgetSoundPerspectiveMode setMode(SoundPerspectiveMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.invalidate();
        }
        return this;
    }

    public SoundPerspectiveMode getMode() {
        return mode;
    }

    @Override
    public void onActivate() {
        SoundPerspectiveMode[] values = SoundPerspectiveMode.values();
        mode = values[(mode.ordinal() + 1) % values.length];
        onModeChanged(mode);
        invalidate();
    }

    @Override
    public void onDraw() {
        super.onDraw();

        view.draw(mode.getIcon(), 0, 0);
    }
}
