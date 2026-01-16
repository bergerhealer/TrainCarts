package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;

import java.util.List;

/**
 * Provides hastebin paste import/export functionality for widgets that display
 * train attachment animation frames. Used by the /train animation import/export
 * commands.
 */
public interface AnimationFramesImportExport {
    /**
     * Gets the name of the current displayed train attachments animation.
     * Returns <i>null</i> if no animation is currently selected/created.
     *
     * @return Animation Name
     */
    String getAnimationName();

    /**
     * Exports all current animation frames. If the user had selected multiple nodes,
     * exports only those. Otherwise exports all frames.
     *
     * @return List of animation nodes in order of the animation
     */
    List<AnimationNode> exportAnimationFrames();

    /**
     * Imports animation frames. If insert is true then the frames being imported
     * are added below the selected animation node(s). If the user had selected multiple
     * nodes and insert is false, then only that selection is replaced. Otherwise the
     * entire animation is replaced.
     *
     * @param frames Animation frames imported
     * @param insert True to insert the frames below the current selection, instead of
     *               replacing it
     */
    void importAnimationFrames(List<AnimationNode> frames, boolean insert);
}
