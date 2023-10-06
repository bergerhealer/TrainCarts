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
    SAME,
    /** One sound plays for the player in the cart, another for players not in the cart */
    CART,
    /** One sound plays for the player in the train, another for players not in the train */
    TRAIN,
    /** One sound plays for the player in a parented seat, another for players not in the parented seat */
    SEAT;

    private final MapTexture icon;

    SoundPerspectiveMode() {
        MapTexture tex = MapTexture.loadPluginResource(TrainCarts.plugin,
                "com/bergerkiller/bukkit/tc/textures/attachments/sound_perspectives.png");
        this.icon = tex.getView(tex.getHeight() * ordinal(), 0, tex.getHeight(), tex.getHeight()).clone();
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
