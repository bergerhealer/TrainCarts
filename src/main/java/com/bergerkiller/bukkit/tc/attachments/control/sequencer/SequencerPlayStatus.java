package com.bergerkiller.bukkit.tc.attachments.control.sequencer;

/**
 * The playing status of a sequencer
 */
public enum SequencerPlayStatus {
    /** Playing because playEffect() was called */
    PLAYING_MANUAL(true, false),
    /** Playing because an autoplay rule triggered it */
    PLAYING_AUTOMATIC(true, true),
    /** Not playing because stopEffect() was called */
    STOPPED_MANUAL(false, false),
    /** Not playing because an autoplay rule forced it back off */
    STOPPED_AUTOMATIC(false, true);

    private final boolean playing;
    private final boolean automatic;

    SequencerPlayStatus(boolean playing, boolean automatic) {
        this.playing = playing;
        this.automatic = automatic;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isAutomatic() {
        return automatic;
    }
}
