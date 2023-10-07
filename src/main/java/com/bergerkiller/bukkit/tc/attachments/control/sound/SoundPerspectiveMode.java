package com.bergerkiller.bukkit.tc.attachments.control.sound;

import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * A mode in which a sound attachment controls to who what sounds play.
 * This makes a difference between sounds played "inside" (1p) and "outside"
 * (3p) and what this means.
 */
public enum SoundPerspectiveMode {
    /** The one and same sound plays no matter whether a player is inside or outside */
    SAME("play same sound for\nall perspectives"),
    /** One sound plays for the player in the cart, another for players not in the cart */
    CART("1p cart passengers\n3p outside cart"),
    /** One sound plays for the player in the train, another for players not in the train */
    TRAIN("1p train passengers\n3p outside train"),
    /** One sound plays for the player in a parented seat, another for players not in the parented seat */
    SEAT("1p seat passenger\n3p outside seat");

    private final MapTexture icon;
    private final String tooltip;

    SoundPerspectiveMode(String tooltip) {
        MapTexture tex = MapTexture.loadPluginResource(TrainCarts.plugin,
                "com/bergerkiller/bukkit/tc/textures/attachments/sound_perspectives.png");
        this.icon = tex.getView(tex.getHeight() * ordinal(), 0, tex.getHeight(), tex.getHeight()).clone();
        this.tooltip = tooltip;
    }

    public String getTooltip() {
        return tooltip;
    }

    public MapTexture getIcon() {
        return icon;
    }

    public MapWidgetSoundSelector.Mode getSoundMode() {
        return (this == SAME) ? MapWidgetSoundSelector.Mode.ALL_PERSPECTIVE
                              : MapWidgetSoundSelector.Mode.FIRST_PERSPECTIVE;
    }

    public MapWidgetSoundSelector.Mode getSoundAltMode() {
        return (this == SAME) ? MapWidgetSoundSelector.Mode.NONE
                              : MapWidgetSoundSelector.Mode.THIRD_PERSPECTIVE;
    }
}
