package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

import com.bergerkiller.bukkit.common.map.MapTexture;

/**
 * One of three modes that sequencer effects are stored in.
 * Start advances to loop, then to stop. Then back to start.
 */
public enum SequencerMode {
    START("start", "start"),
    LOOP("loop", "loop"),
    STOP("stop", "stop");

    private final String title;
    private final String configKey;
    private final MapTexture icon;

    SequencerMode(String title, String configKey) {
        this.title = title;
        this.configKey=  configKey;
        this.icon = MapWidgetSequencerEffect.TEXTURE_ATLAS
                .getView(7 * ordinal(), 35, 7, 5).clone();
    }

    public String title() {
        return title;
    }

    public String configKey() {
        return configKey;
    }

    public MapTexture icon() {
        return icon;
    }

    public SequencerMode next() {
        switch (this) {
            case START:
            case LOOP:
                return LOOP;
            default:
                return START;
        }
    }
}
