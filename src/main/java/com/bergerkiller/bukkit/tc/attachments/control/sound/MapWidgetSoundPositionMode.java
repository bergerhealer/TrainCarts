package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetTooltip;

/**
 * Toggles between the different {@link SoundPositionMode} values
 */
public abstract class MapWidgetSoundPositionMode extends MapWidgetSoundButton {
    private SoundPositionMode mode = SoundPositionMode.DEFAULT;
    private boolean isSamePerspective = false;
    public final MapWidgetTooltip tooltip = new MapWidgetTooltip().setText(mode.getTooltip());

    public MapWidgetSoundPositionMode() {
        setSize(mode.getIcon().getWidth(), mode.getIcon().getHeight());
    }

    public abstract void onModeChanged(SoundPositionMode newMode);

    public MapWidgetSoundPositionMode setIsSamePerspective(boolean isSamePerspective) {
        if (this.isSamePerspective != isSamePerspective) {
            this.isSamePerspective = isSamePerspective;
            if (mode.isAtPlayer1P() != mode.isAtPlayer3P()) {
                tooltip.setText(getDisplayedMode().getTooltip());
                this.invalidate();
            }
        }
        return this;
    }

    public MapWidgetSoundPositionMode setMode(boolean atPerson1P, boolean atPerson3P) {
        return setMode(SoundPositionMode.fromPerspectives(atPerson1P, atPerson3P));
    }

    public MapWidgetSoundPositionMode setMode(SoundPositionMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.tooltip.setText(getDisplayedMode().getTooltip());
            this.invalidate();
        }
        return this;
    }

    public SoundPositionMode getMode() {
        return mode;
    }

    @Override
    public void onClick() {
        SoundPositionMode[] values;
        if (isSamePerspective) {
            values = new SoundPositionMode[] { SoundPositionMode.DEFAULT, SoundPositionMode.AT_PLAYER };
        } else {
            values = SoundPositionMode.values();
        }
        mode = values[(getDisplayedMode().ordinal() + 1) % values.length];
        tooltip.setText(getDisplayedMode().getTooltip());
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

        view.draw(getDisplayedMode().getIcon(), 0, 0);
    }

    private SoundPositionMode getDisplayedMode() {
        if (isSamePerspective && mode.isAtPlayer1P() != mode.isAtPlayer3P()) {
            return mode.isAtPlayer1P() ? SoundPositionMode.AT_PLAYER : SoundPositionMode.DEFAULT;
        } else {
            return mode;
        }
    }

    /**
     * Changes where the sound is played: at the position of the sound attachment,
     * or right next to the Player. This can be set for 1p/3p separately.
     */
    public enum SoundPositionMode {
        DEFAULT("play at position", false, false),
        AT_PLAYER("play at player", true, true),
        FIRST_PERSON_AT_PLAYER("play 1p at player\nplay 3p at position", true, false),
        THIRD_PERSON_AT_PLAYER("play 1p at position\nplay 3p at player", false, true);

        private final MapTexture icon;
        private final String tooltip;
        private final boolean firstPersonAtPlayer, thirdPersonAtPlayer;

        SoundPositionMode(String tooltip, boolean firstPersonAtPlayer, boolean thirdPersonAtPlayer) {
            MapTexture tex = MapTexture.loadPluginResource(TrainCarts.plugin,
                    "com/bergerkiller/bukkit/tc/textures/attachments/sound_positions.png");
            this.icon = tex.getView(tex.getHeight() * ordinal(), 0, tex.getHeight(), tex.getHeight()).clone();
            this.tooltip = tooltip;
            this.firstPersonAtPlayer = firstPersonAtPlayer;
            this.thirdPersonAtPlayer = thirdPersonAtPlayer;
        }

        public String getTooltip() {
            return tooltip;
        }

        public MapTexture getIcon() {
            return icon;
        }

        public boolean isAtPlayer1P() {
            return firstPersonAtPlayer;
        }

        public boolean isAtPlayer3P() {
            return thirdPersonAtPlayer;
        }

        public static SoundPositionMode fromPerspectives(boolean atPlayer1P, boolean atPlayer3P) {
            if (atPlayer1P) {
                if (atPlayer3P) {
                    return AT_PLAYER;
                } else {
                    return FIRST_PERSON_AT_PLAYER;
                }
            } else if (atPlayer3P) {
                return THIRD_PERSON_AT_PLAYER;
            } else {
                return DEFAULT;
            }
        }
    }
}
